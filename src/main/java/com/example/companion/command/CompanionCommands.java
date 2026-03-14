package com.example.companion.command;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.GoalType;
import com.example.companion.objective.Objective;
import com.example.companion.objective.ObjectiveTracker;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

/**
 * Registers the {@code /companion} command tree for in-game control and testing.
 *
 * <pre>
 *   /companion spawn               — spawn a companion at the player's feet
 *   /companion status              — show current goal, health, hunger, objective
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
                        CommandManager.literal("companion")
                                .requires(src -> src.hasPermissionLevel(2))

                                .then(CommandManager.literal("spawn")
                                        .executes(CompanionCommands::spawnCompanion))

                                .then(CommandManager.literal("status")
                                        .executes(CompanionCommands::showStatus))

                                .then(CommandManager.literal("goal")
                                        .then(CommandManager.argument("type", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (GoalType t : GoalType.values())
                                                        builder.suggest(t.name().toLowerCase());
                                                    return builder.buildFuture();
                                                })
                                                .executes(CompanionCommands::setGoal)))

                                .then(CommandManager.literal("remove")
                                        .executes(CompanionCommands::removeAll))

                                .then(CommandManager.literal("objective")
                                        .then(CommandManager.literal("set")
                                                .then(CommandManager.argument("objective", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            for (Objective o : Objective.values())
                                                                builder.suggest(o.name().toLowerCase());
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(CompanionCommands::setObjective)))
                                        .then(CommandManager.literal("status")
                                                .executes(CompanionCommands::objectiveStatus))
                                        .then(CommandManager.literal("list")
                                                .executes(CompanionCommands::objectiveList)))
                ));
    }

    // ------------------------------------------------------------------
    // Handlers
    // ------------------------------------------------------------------

    private static int spawnCompanion(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayerOrThrow();
        } catch (Exception e) {
            src.sendError(Text.literal("Must be run by a player."));
            return 0;
        }

        ServerWorld world = player.getServerWorld();
        CompanionEntity companion = CompanionMod.COMPANION_ENTITY_TYPE.create(world);
        if (companion == null) {
            src.sendError(Text.literal("Failed to create companion entity."));
            return 0;
        }

        companion.setPosition(player.getX(), player.getY(), player.getZ());
        companion.setYaw(player.getYaw());
        world.spawnEntity(companion);

        src.sendFeedback(() -> Text.literal(
                "Spawned companion [" + companion.getUuidAsString().substring(0, 8) + "]"), false);
        CompanionMod.LOGGER.info("Spawned CompanionEntity at {}", player.getBlockPos());
        return 1;
    }

    private static int showStatus(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();

        List<CompanionEntity> companions = world.getEntitiesByClass(
                CompanionEntity.class, src.getEntityAnchor().positionAt(src).expand(128), e -> true);

        if (companions.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No companions found within 128 blocks."), false);
            return 0;
        }

        for (CompanionEntity c : companions) {
            String id    = c.getUuidAsString().substring(0, 8);
            String goal  = c.getActiveGoalType().name();
            float  hp    = c.getHealth();
            int    hunger = c.getHunger();
            src.sendFeedback(() -> Text.literal(
                    String.format("[%s] goal=%-16s  hp=%.1f  hunger=%d", id, goal, hp, hunger)), false);
        }
        return companions.size();
    }

    private static int setGoal(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        String typeStr = StringArgumentType.getString(ctx, "type");
        GoalType type = GoalType.fromString(typeStr);

        ServerWorld world = src.getWorld();
        List<CompanionEntity> companions = world.getEntitiesByClass(
                CompanionEntity.class, src.getEntityAnchor().positionAt(src).expand(128), e -> true);

        if (companions.isEmpty()) {
            src.sendError(Text.literal("No companions found within 128 blocks."));
            return 0;
        }

        for (CompanionEntity c : companions) {
            c.setGoal(type, Map.of());
        }
        final GoalType finalType = type;
        src.sendFeedback(() -> Text.literal(
                "Set goal to " + finalType + " on " + companions.size() + " companion(s)."), false);
        return companions.size();
    }

    private static int removeAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();

        List<CompanionEntity> companions = world.getEntitiesByClass(
                CompanionEntity.class, src.getEntityAnchor().positionAt(src).expand(128), e -> true);

        companions.forEach(CompanionEntity::discard);
        src.sendFeedback(() -> Text.literal("Removed " + companions.size() + " companion(s)."), false);
        return companions.size();
    }

    // ------------------------------------------------------------------
    // Objective handlers
    // ------------------------------------------------------------------

    private static int setObjective(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        String objStr = StringArgumentType.getString(ctx, "objective");
        Objective obj = Objective.fromString(objStr);

        ServerWorld world = src.getWorld();
        List<CompanionEntity> companions = world.getEntitiesByClass(
                CompanionEntity.class, src.getEntityAnchor().positionAt(src).expand(128), e -> true);

        if (companions.isEmpty()) {
            src.sendError(Text.literal("No companions found within 128 blocks."));
            return 0;
        }
        for (CompanionEntity c : companions) {
            c.setObjective(obj);
        }
        final Objective finalObj = obj;
        src.sendFeedback(() -> Text.literal(
                "Objective set to [" + finalObj.name() + "] — " + finalObj.displayName
                + " on " + companions.size() + " companion(s)."), false);
        return companions.size();
    }

    private static int objectiveStatus(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerWorld world = src.getWorld();

        List<CompanionEntity> companions = world.getEntitiesByClass(
                CompanionEntity.class, src.getEntityAnchor().positionAt(src).expand(128), e -> true);

        if (companions.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No companions found within 128 blocks."), false);
            return 0;
        }

        for (CompanionEntity c : companions) {
            String id = c.getUuidAsString().substring(0, 8);
            com.google.gson.JsonObject obj = ObjectiveTracker.serialize(c);
            String type      = obj.get("type").getAsString();
            String milestone = obj.get("milestone").getAsString();
            boolean complete = obj.get("complete").getAsBoolean();

            src.sendFeedback(() -> Text.literal(
                    "[" + id + "] Objective: " + type + (complete ? " ✓" : "")), false);
            src.sendFeedback(() -> Text.literal("  Milestone: " + milestone), false);

            obj.getAsJsonArray("progress").forEach(e ->
                    src.sendFeedback(() -> Text.literal("    " + e.getAsString()), false));
        }
        return companions.size();
    }

    private static int objectiveList(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        src.sendFeedback(() -> Text.literal("Available objectives:"), false);
        for (Objective o : Objective.values()) {
            src.sendFeedback(() -> Text.literal(
                    "  " + o.name().toLowerCase() + " — " + o.displayName), false);
        }
        return Objective.values().length;
    }
}
