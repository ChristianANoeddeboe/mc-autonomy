package dev.mcc.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registered via fabric-command-api-v2.
 * Phase 0: every handler is a stub that answers "[MCC] stub" so the command
 * surface exists and the headless smoke console can exercise it.
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
		root.then(Commands.literal("status").executes(MccCommands::stub));
		root.then(Commands.literal("inventory").executes(MccCommands::stub));
		root.then(sayOp());
		root.then(Commands.literal("despawn").executes(MccCommands::stub));
		root.then(llmOp());
		root.then(debugOp());

		return root;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> spawnOp() {
		// mc-companion spawn [x y z]
		LiteralArgumentBuilder<CommandSourceStack> spawn = Commands.literal("spawn");
		spawn.executes(MccCommands::stub);
		spawn.then(Commands.argument("x", DoubleArgumentType.doubleArg())
			.then(Commands.argument("y", DoubleArgumentType.doubleArg())
				.then(Commands.argument("z", DoubleArgumentType.doubleArg())
					.executes(MccCommands::stub))));
		return spawn;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> sayOp() {
		return Commands.literal("say")
			.then(Commands.argument("text", StringArgumentType.greedyString())
				.executes(MccCommands::stub));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> llmOp() {
		LiteralArgumentBuilder<CommandSourceStack> llm = Commands.literal("llm");
		llm.then(Commands.argument("mode", StringArgumentType.string())
			.executes(MccCommands::stub));
		return llm;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> debugOp() {
		LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug");
		// debug give <item> <count>
		debug.then(Commands.literal("give")
			.then(Commands.argument("item", StringArgumentType.string())
				.then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
					.executes(MccCommands::stub))));
		// debug goto <x> <y> <z>
		debug.then(Commands.literal("goto")
			.then(Commands.argument("x", DoubleArgumentType.doubleArg())
				.then(Commands.argument("y", DoubleArgumentType.doubleArg())
					.then(Commands.argument("z", DoubleArgumentType.doubleArg())
						.executes(MccCommands::stub)))));
		// debug goal <TYPE> ...  (later phases)
		debug.then(Commands.literal("goal")
			.then(Commands.argument("spec", StringArgumentType.greedyString())
				.executes(MccCommands::stub)));
		// debug health <amount>
		debug.then(Commands.literal("health")
			.then(Commands.argument("amount", DoubleArgumentType.doubleArg())
				.executes(MccCommands::stub)));
		return debug;
	}

	private static int stub(CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSuccess(() -> Component.literal("[MCC] stub"), false);
		return 1;
	}
}