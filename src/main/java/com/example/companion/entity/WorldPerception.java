package com.example.companion.entity;

import com.example.companion.goal.GoalType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Scans the world around the companion and serialises it to JSON for the sidecar.
 *
 * <p>Scan radius is configurable; defaults to {@value DEFAULT_RADIUS} blocks.
 */
public final class WorldPerception {

    public static final int DEFAULT_RADIUS = 16;

    // Maximum nearby-blocks entries sent to avoid huge payloads
    private static final int MAX_BLOCKS = 32;

    private WorldPerception() {}

    /**
     * Build and return a {@link JsonObject} matching the WorldState schema
     * defined in the design document.
     */
    public static JsonObject serialize(CompanionEntity entity) {
        return serialize(entity, DEFAULT_RADIUS);
    }

    public static JsonObject serialize(CompanionEntity entity, int radius) {
        JsonObject root = new JsonObject();
        root.addProperty("entity_id", entity.getUuidAsString());
        root.addProperty("tick", (int) entity.getWorld().getTime());

        root.add("world", buildWorldInfo(entity, radius));
        return root;
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static JsonObject buildWorldInfo(CompanionEntity entity, int radius) {
        World world = entity.getWorld();
        JsonObject info = new JsonObject();

        long tod = world.getTimeOfDay() % 24000;
        info.addProperty("time_of_day", timeLabel(tod));
        info.addProperty("weather", weatherLabel(world));
        info.addProperty("biome", "unknown"); // TODO: resolve biome registry name
        info.addProperty("light_level", world.getLightLevel(entity.getBlockPos()));

        info.add("nearby_blocks",  scanBlocks(entity, radius));
        info.add("nearby_entities", scanEntities(entity, radius));
        info.add("companion", buildCompanionState(entity));

        info.addProperty("last_player_message", entity.getLastPlayerMessage());
        info.addProperty("current_goal", entity.getActiveGoalType().name());
        String failReason = entity.getGoalFailedReason();
        if (failReason != null) {
            info.addProperty("goal_failed_reason", failReason);
        } else {
            info.add("goal_failed_reason", null);
        }

        return info;
    }

    private static JsonObject buildCompanionState(CompanionEntity entity) {
        JsonObject cs = new JsonObject();
        cs.addProperty("health", entity.getHealth());
        cs.addProperty("hunger", 20); // TODO: track hunger on entity

        JsonArray pos = new JsonArray();
        pos.add(entity.getX());
        pos.add(entity.getY());
        pos.add(entity.getZ());
        cs.add("position", pos);

        cs.add("inventory", new JsonArray()); // TODO: serialize inventory
        return cs;
    }

    private static JsonArray scanBlocks(CompanionEntity entity, int radius) {
        // TODO (Phase 2): scan BlockPos cube, filter interesting block types, sort by distance
        return new JsonArray();
    }

    private static JsonArray scanEntities(CompanionEntity entity, int radius) {
        World world = entity.getWorld();
        Box box = entity.getBoundingBox().expand(radius);
        List<Entity> nearby = world.getOtherEntities(entity, box);

        JsonArray arr = new JsonArray();
        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity living)) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("type", e.getType().getUntranslatedName());
            entry.addProperty("distance", (float) entity.distanceTo(e));
            entry.addProperty("hostile", e instanceof MobEntity mob && mob.getTarget() != null);
            arr.add(entry);
        }
        return arr;
    }

    private static String timeLabel(long tod) {
        if (tod < 1000 || tod >= 23000) return "dawn";
        if (tod < 12000) return "day";
        if (tod < 13000) return "dusk";
        return "night";
    }

    private static String weatherLabel(World world) {
        if (world.isThundering()) return "thunder";
        if (world.isRaining()) return "rain";
        return "clear";
    }
}
