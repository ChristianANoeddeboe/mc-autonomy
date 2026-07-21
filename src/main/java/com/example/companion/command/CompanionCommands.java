package com.example.companion.command;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.entity.WorldPerception;
import com.example.companion.goal.GoalType;
import com.example.companion.objective.Objective;
import com.example.companion.objective.ObjectiveTracker;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Registers the {@code /companion} command tree for in-game control and testing.
 *
 * <pre>
 *   /companion spawn               — spawn a companion at the player's feet
 *   /companion status              — show current goal, health, hunger, objective
 *   /companion debug               — full state dump to chat + world perception to log
 *   /companion goal <type>         — manually set the active goal (for testing)
 *   /companion objective set <obj> — set the long-term objective
 *   /companion objective status    — show objective progress
 *   /companion objective list      — list all available objectives
 *   /companion remove              — remove all companion entities in the world
 * </pre>
 */
public final class CompanionCommands {

    private CompanionCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        Commands.literal("companion")
                                .requires(src -> src.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))

                                .then(Commands.literal("spawn")
                                        .executes(CompanionCommands::spawnCompanion))

                                .then(Commands.literal("status")
                                        .executes(CompanionCommands::showStatus))

                                .then(Commands.literal("goal")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (GoalType t : GoalType.values())
                                                        builder.suggest(t.name().toLowerCase());
                                                    return builder.buildFuture();
                                                })
                                                .executes(CompanionCommands::setGoal)))

                                .then(Commands.literal("debug")
                                        .executes(CompanionCommands::debugStatus))

                                .then(Commands.literal("remove")
                                        .executes(CompanionCommands::removeAll))

                                .then(Commands.literal("objective")
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("objective", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            for (Objective o : Objective.values())
                                                                builder.suggest(o.name().toLowerCase());
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(CompanionCommands::setObjective)))
                                        .then(Commands.literal("status")
                                                .executes(CompanionCommands::objectiveStatus))
                                        .then(Commands.literal("list")
                                                .executes(CompanionCommands::objectiveList)))
                ));
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private static int spawnCompanion(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Must be run by a player."));
            return 0;
        }

        ServerLevel world = (ServerLevel) player.level();
        CompanionEntity companion = new CompanionEntity(CompanionMod.COMPANION_ENTITY_TYPE, world);

        companion.setPos(player.getX(), player.getY(), player.getZ());
        companion.setYRot(player.getYRot());
        world.addFreshEntity(companion);

        src.sendSuccess(() -> Component.literal(
                "Spawned companion [" + companion.getStringUUID().substring(0, 8) + "]"), false);
        CompanionMod.LOGGER.info("Spawned CompanionEntity at {}", player.blockPosition());
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();

        List<CompanionEntity> companions = world.getEntitiesOfClass(
                CompanionEntity.class, searchBox(src), e -> true);

        if (companions.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No companions found within 128 blocks."), false);
            return 0;
        }

        for (CompanionEntity c : companions) {
            String id    = c.getStringUUID().substring(0, 8);
            String goal  = c.getActiveGoalType().name();
            float  hp    = c.getHealth();
            int    hunger = c.getHunger();
            src.sendSuccess(() -> Component.literal(
                    String.format("[%s] goal=%-16s  hp=%.1f  hunger=%d", id, goal, hp, hunger)), false);
        }
        return companions.size();
    }

    private static int debugStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();

        List<CompanionEntity> companions = world.getEntitiesOfClass(
                CompanionEntity.class, searchBox(src), e -> true);

        if (companions.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No companions found within 128 blocks."), false);
            return 0;
        }

        for (CompanionEntity c : companions) {
            String id = c.getStringUUID().substring(0, 8);

            // --- header ---
            src.sendSuccess(() -> Component.literal("=== Companion [" + id + "] ==="), false);

            // --- goal ---
            String goalStatus = c.getGoalExecutor().getActiveGoalStatus();
            src.sendSuccess(() -> Component.literal(
                    "  Goal:      " + c.getActiveGoalType().name() + " (" + goalStatus + ")"), false);

            // --- failure reason ---
            String fail = c.getGoalFailedReason();
            if (fail != null && !fail.isEmpty())
                src.sendSuccess(() -> Component.literal("  Last fail: " + fail), false);

            // --- vitals + position ---
            src.sendSuccess(() -> Component.literal(String.format(
                    "  HP: %.1f  Hunger: %d  Pos: (%d, %d, %d)",
                    c.getHealth(), c.getHunger(),
                    c.blockPosition().getX(),
                    c.blockPosition().getY(),
                    c.blockPosition().getZ())), false);

            // --- objective ---
            com.google.gson.JsonObject obj = ObjectiveTracker.serialize(c);
            src.sendSuccess(() -> Component.literal(
                    "  Objective: " + obj.get("type").getAsString()
                    + " — " + obj.get("milestone").getAsString()), false);

            // --- inventory ---
            List<net.minecraft.world.item.ItemStack> stacks = c.getNonEmptyStacks();
            if (stacks.isEmpty()) {
                src.sendSuccess(() -> Component.literal("  Inventory: (empty)"), false);
            } else {
                src.sendSuccess(() -> Component.literal("  Inventory: " +
                        stacks.stream()
                              .map(s -> com.example.companion.util.ScanUtils.itemName(s) + " x" + s.getCount())
                              .collect(java.util.stream.Collectors.joining(", "))), false);
            }

            // --- last player message ---
            String msg = c.getLastPlayerMessage();
            if (msg != null && !msg.isEmpty())
                src.sendSuccess(() -> Component.literal("  Last msg:  " + msg), false);

            // --- dump full world state to server log ---
            CompanionMod.LOGGER.info("[debug] WorldPerception for [{}]:\n{}",
                    id, WorldPerception.serialize(c));
        }
        return companions.size();
    }

    private static int setGoal(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String typeStr = StringArgumentType.getString(ctx, "type");
        GoalType type = GoalType.fromString(typeStr);

        ServerLevel world = src.getLevel();
        List<CompanionEntity> companions = world.getEntitiesOfClass(
                CompanionEntity.class, searchBox(src), e -> true);

        if (companions.isEmpty()) {
            src.sendFailure(Component.literal("No companions found within 128 blocks."));
            return 0;
        }

        for (CompanionEntity c : companions) {
            c.setGoal(type, Map.of());
        }
        final GoalType finalType = type;
        src.sendSuccess(() -> Component.literal(
                "Set goal to " + finalType + " on " + companions.size() + " companion(s)."), false);
        return companions.size();
    }

    private static int removeAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();

        List<CompanionEntity> companions = world.getEntitiesOfClass(
                CompanionEntity.class, searchBox(src), e -> true);

        companions.forEach(CompanionEntity::discard);
        src.sendSuccess(() -> Component.literal("Removed " + companions.size() + " companion(s)."), false);
        return companions.size();
    }

    // ------------------------------------------------------------------
    // Objective handlers
    // ------------------------------------------------------------------

    private static int setObjective(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String objStr = StringArgumentType.getString(ctx, "objective");
        Objective obj = Objective.fromString(objStr);

        ServerLevel world = src.getLevel();
        List<CompanionEntity> companions = world.getEntitiesOfClass(
                CompanionEntity.class, searchBox(src), e -> true);

        if (companions.isEmpty()) {
            src.sendFailure(Component.literal("No companions found within 128 blocks."));
            return 0;
        }
        for (CompanionEntity c : companions) {
            c.setObjective(obj);
        }
        final Objective finalObj = obj;
        src.sendSuccess(() -> Component.literal(
                "Objective set to [" + finalObj.name() + "] — " + finalObj.displayName
                + " on " + companions.size() + " companion(s)."), false);
        return companions.size();
    }

    private static int objectiveStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel world = src.getLevel();

        List<CompanionEntity> companions = world.getEntitiesOfClass(
                CompanionEntity.class, searchBox(src), e -> true);

        if (companions.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No companions found within 128 blocks."), false);
            return 0;
        }

        for (CompanionEntity c : companions) {
            String id = c.getStringUUID().substring(0, 8);
            com.google.gson.JsonObject obj = ObjectiveTracker.serialize(c);
            String type      = obj.get("type").getAsString();
            String milestone = obj.get("milestone").getAsString();
            boolean complete = obj.get("complete").getAsBoolean();

            src.sendSuccess(() -> Component.literal(
                    "[" + id + "] Objective: " + type + (complete ? " ✓" : "")), false);
            src.sendSuccess(() -> Component.literal("  Milestone: " + milestone), false);

            obj.getAsJsonArray("progress").forEach(e ->
                    src.sendSuccess(() -> Component.literal("    " + e.getAsString()), false));
        }
        return companions.size();
    }

    private static int objectiveList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("Available objectives:"), false);
        for (Objective o : Objective.values()) {
            src.sendSuccess(() -> Component.literal(
                    "  " + o.name().toLowerCase() + " — " + o.displayName), false);
        }
        return Objective.values().length;
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private static AABB searchBox(CommandSourceStack src) {
        Vec3 pos = src.getPosition();
        return new AABB(pos.x - 128, pos.y - 128, pos.z - 128,
                        pos.x + 128, pos.y + 128, pos.z + 128);
    }
}
