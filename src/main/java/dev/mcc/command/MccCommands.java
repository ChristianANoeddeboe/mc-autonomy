package dev.mcc.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.mcc.CompanionManager;
import dev.mcc.CompanionRegistry;
import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalType;
import dev.mcc.llm.CompanionBrain;
import dev.mcc.owner.ConsoleOwner;
import dev.mcc.owner.PlayerOwner;
import dev.mcc.owner.Owner;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * mc-companion <spawn|status|inventory|say|despawn|llm|debug>.
 * Phase 2 implements spawn/status/inventory/despawn/debug give/goto/health.
 */
public final class MccCommands {
	private MccCommands() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(build());
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> build() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mc-companion");

		root.then(spawnOp());
		root.then(Commands.literal("status").executes(MccCommands::status));
		root.then(Commands.literal("inventory").executes(MccCommands::inventory));
		root.then(sayOp());
		root.then(Commands.literal("despawn").executes(MccCommands::despawn));
		root.then(llmOp());
		root.then(debugOp());

		return root;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> spawnOp() {
		LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
		spawn.executes(MccCommands::spawnHere);
		spawn.then(Commands.argument("x", DoubleArgumentType.doubleArg())
			.then(Commands.argument("y", DoubleArgumentType.doubleArg())
				.then(Commands.argument("z", DoubleArgumentType.doubleArg())
					.executes(MccCommands::spawnAt))));
		return spawn;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> sayOp() {
		return Commands.literal("say")
			.then(Commands.argument("text", StringArgumentType.greedyString())
				.executes(MccCommands::say));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> llmOp() {
		LiteralArgumentBuilder<CommandSourceStack> llm = Commands.literal("llm");
		llm.then(Commands.argument("mode", StringArgumentType.string()).executes(MccCommands::llm));
		return llm;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> debugOp() {
		LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug");
		debug.then(Commands.literal("give")
			.then(Commands.argument("item", StringArgumentType.string())
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
					.executes(MccCommands::debugGive))));
		debug.then(Commands.literal("goto")
			.then(Commands.argument("x", DoubleArgumentType.doubleArg())
				.then(Commands.argument("y", DoubleArgumentType.doubleArg())
					.then(Commands.argument("z", DoubleArgumentType.doubleArg())
						.executes(MccCommands::debugGoto)))));
		debug.then(Commands.literal("goal")
			.then(Commands.argument("type", StringArgumentType.string())
				.then(Commands.argument("params", StringArgumentType.greedyString())
					.executes(MccCommands::debugGoal))));
		debug.then(Commands.literal("health")
			.then(Commands.argument("amount", DoubleArgumentType.doubleArg())
				.executes(MccCommands::debugHealth)));
		return debug;
	}

	// ---- handlers -----------------------------------------------------------

	private static ServerLevel level(CommandContext<CommandSourceStack> ctx) {
		return ctx.getSource().getLevel();
	}

	/** Owner to bind a freshly-spawned companion: player if from a player, else console. */
	private static Owner ownerFor(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer player = src.getPlayer();
		if (player != null) {
			return new PlayerOwner(player);
		}
		return consoleOwner(ctx);
	}

	private static Owner consoleOwner(CommandContext<CommandSourceStack> ctx) {
		Vec3 anchor = new Vec3(0, -60, 0);
		if (ctx.getSource().getEntity() != null) {
			anchor = ctx.getSource().getEntity().position();
		}
		return new ConsoleOwner(ctx.getSource().getServer(), CompanionManager.CONSOLE_ID, "console", anchor);
	}

	private static int spawnHere(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = level(ctx);
		Owner owner = ownerFor(ctx);
		Vec3 p = owner.pos();
		CompanionManager.spawn(level, owner, p.x(), p.y(), p.z());
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] spawn"), true);
		return 1;
	}

	private static int spawnAt(CommandContext<CommandSourceStack> ctx) {
		double x = DoubleArgumentType.getDouble(ctx, "x");
		double y = DoubleArgumentType.getDouble(ctx, "y");
		double z = DoubleArgumentType.getDouble(ctx, "z");
		ServerLevel level = level(ctx);
		Owner owner = ownerFor(ctx);
		// when spawning at explicit coords from a player, anchor the console owner at those coords
		if (owner instanceof ConsoleOwner) {
			owner = new ConsoleOwner(ctx.getSource().getServer(), CompanionManager.CONSOLE_ID, "console", new Vec3(x, y, z));
		}
		CompanionManager.spawn(level, owner, x, y, z);
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] spawn"), true);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = level(ctx);
		companionFor(ctx).ifPresentOrElse(c -> {
			String llm = c.brain().state().toString();
			String goal = c.executor().current().map(t -> t.spec().type().toString()).orElse("none");
			int queue = c.executor().queue().size();
			String inv = c.inventory() != null ? (c.inventory().filledSlots() + "/" + c.inventory().size()) : "0/0";
			String msg = String.format("[MCC][status] goal=%s queue=%d health=%.0f/%.0f hunger=%d inv=%s mode=%s llm=%s",
				goal, queue, c.getHealth(), c.getMaxHealth(), c.hunger().get(), inv, c.mode(), llm);
			ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
			dev.mcc.MccMod.LOGGER.info(msg);
		}, () -> ctx.getSource().sendFailure(Component.literal("[MCC] no companion for this source")));
		return 1;
	}

	private static int inventory(CommandContext<CommandSourceStack> ctx) {
		companionFor(ctx).ifPresentOrElse(c -> {
			StringBuilder sb = new StringBuilder("[MCC][inv]");
			if (c.inventory() == null) {
				ctx.getSource().sendSuccess(() -> Component.literal("[MCC][inv] (empty)"), false);
				return;
			}
			net.minecraft.world.SimpleContainer sc = c.inventory().raw();
			for (int i = 0; i < sc.getContainerSize(); i++) {
				ItemStack st = sc.getItem(i);
				if (!st.isEmpty()) {
					sb.append(" ").append(resolveItemId(st)).append("=").append(st.getCount());
				}
			}
			String msg = sb.toString();
			ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
			dev.mcc.MccMod.LOGGER.info(msg);
		}, () -> ctx.getSource().sendFailure(Component.literal("[MCC] no companion")));
		return 1;
	}

	private static int say(CommandContext<CommandSourceStack> ctx) {
		String text = StringArgumentType.getString(ctx, "text");
		companionFor(ctx).ifPresentOrElse(c -> c.brain().entity().say(text),
			() -> ctx.getSource().sendFailure(Component.literal("[MCC] no companion")));
		return 1;
	}

	private static int despawn(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = level(ctx);
		UUID ownerId = resolveOwnerId(ctx);
		if (ownerId != null) {
			CompanionManager.despawn(level, ownerId);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] despawn"), true);
		return 1;
	}

	private static int llm(CommandContext<CommandSourceStack> ctx) {
		String mode = StringArgumentType.getString(ctx, "mode");
		CompanionBrain.LlmState st = switch (mode) {
			case "on" -> CompanionBrain.LlmState.CONNECTED;
			case "mock" -> CompanionBrain.LlmState.MOCK;
			case "off" -> CompanionBrain.LlmState.OFF;
			default -> null;
		};
		if (st == null) {
			ctx.getSource().sendFailure(Component.literal("[MCC] llm mode: on|mock|off"));
			return 0;
		}
		companionFor(ctx).ifPresent(c -> c.brain().setState(st));
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] llm=" + mode), false);
		return 1;
	}

	private static int debugGive(CommandContext<CommandSourceStack> ctx) {
		String itemId = StringArgumentType.getString(ctx, "item");
		int count = IntegerArgumentType.getInteger(ctx, "count");
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(itemId));
		if (item.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal("[MCC] unknown item: " + itemId));
			return 0;
		}
		companionFor(ctx).ifPresent(c -> {
			ItemStack stack = new ItemStack(item.get(), count);
			c.inventory().add(stack);
			dev.mcc.MccMod.LOGGER.info("[MCC][debug] give {} x{}", itemId, count);
		});
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] debug give"), true);
		return 1;
	}

	private static int debugGoto(CommandContext<CommandSourceStack> ctx) {
		double x = DoubleArgumentType.getDouble(ctx, "x");
		double y = DoubleArgumentType.getDouble(ctx, "y");
		double z = DoubleArgumentType.getDouble(ctx, "z");
		companionFor(ctx).ifPresent(c -> {
			GoalSpec move = new GoalSpec(GoalType.MOVE_TO,
				java.util.Map.of("x", (int) x, "y", (int) y, "z", (int) z), "debug");
			c.executor().push(move);
			dev.mcc.MccMod.LOGGER.info("[MCC][debug] goto {},{},{}", (int) x, (int) y, (int) z);
		});
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] debug goto"), true);
		return 1;
	}

	private static int debugGoal(CommandContext<CommandSourceStack> ctx) {
		String type = StringArgumentType.getString(ctx, "type");
		String params = StringArgumentType.getString(ctx, "params");
		GoalType gt;
		try {
			gt = GoalType.valueOf(type.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("[MCC] unknown goal type " + type));
			return 0;
		}
		GoalSpec spec = new GoalSpec(gt, java.util.Map.of(), params);
		companionFor(ctx).ifPresent(c -> c.executor().push(spec));
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] debug goal " + type), true);
		return 1;
	}

	private static int debugHealth(CommandContext<CommandSourceStack> ctx) {
		double amount = DoubleArgumentType.getDouble(ctx, "amount");
		companionFor(ctx).ifPresent(c -> {
			c.setHealth((float) amount);
			dev.mcc.MccMod.LOGGER.info("[MCC][debug] health -> {}", amount);
		});
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] debug health"), true);
		return 1;
	}

	// ---- helpers ------------------------------------------------------------

	private static Optional<CompanionEntity> companionFor(CommandContext<CommandSourceStack> ctx) {
		UUID ownerId = resolveOwnerId(ctx);
		if (ownerId == null) return Optional.empty();
		return CompanionManager.forOwner(level(ctx), ownerId);
	}

	private static UUID resolveOwnerId(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) return player.getUUID();
		// console: fall back to the first tracked companion
		return CompanionManager.byOwnerIds().stream().findFirst().orElse(null);
	}

	private static String resolveItemId(ItemStack st) {
		Identifier id = BuiltInRegistries.ITEM.getKey(st.getItem());
		return id != null ? id.toString() : st.getItem().toString();
	}
}