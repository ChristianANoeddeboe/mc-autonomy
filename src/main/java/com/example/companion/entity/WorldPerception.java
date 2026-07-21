package com.example.companion.entity;

import com.example.companion.goal.GoalType;
import com.example.companion.objective.ObjectiveTracker;
import com.example.companion.plan.CompanionPlan;
import com.example.companion.plan.PlanStep;
import com.example.companion.util.ScanUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Holder;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Scans the world around the companion and serialises it to JSON for the sidecar.
 *
 * <p>Scan radius is configurable; defaults to {@value DEFAULT_RADIUS} blocks.
 */
public final class WorldPerception {

    public static final int DEFAULT_RADIUS = 16;
    private static final int MAX_BLOCK_ENTRIES = 32;

    private WorldPerception() {}

    public static JsonObject serialize(CompanionEntity entity) {
        return serialize(entity, com.example.companion.CompanionConfig.get().scanRadius);
    }

    public static JsonObject serialize(CompanionEntity entity, int radius) {
        JsonObject root = new JsonObject();
        root.addProperty("entity_id", entity.getStringUUID());
        root.addProperty("tick", (int) entity.level().getGameTime());
        root.add("world", buildWorldInfo(entity, radius));
        return root;
    }

    // ------------------------------------------------------------------
    // World info
    // ------------------------------------------------------------------

    private static JsonObject buildWorldInfo(CompanionEntity entity, int radius) {
        Level world = entity.level();
        JsonObject info = new JsonObject();

        long tod = world.getDayTime() % 24000;
        info.addProperty("time_of_day", timeLabel(tod));
        info.addProperty("weather", weatherLabel(world));
        info.addProperty("biome", biomeName(world, entity.blockPosition()));
        info.addProperty("light_level", world.getMaxLocalRawBrightness(entity.blockPosition()));

        info.add("nearby_blocks",   scanBlocks(entity, radius));
        info.add("nearby_entities", scanEntities(entity, radius));
        info.add("companion",       buildCompanionState(entity));

        String lastMsg = entity.getLastPlayerMessage();
        info.addProperty("last_player_message", lastMsg != null ? lastMsg : "");

        info.add("available_goals", buildAvailableGoals());
        info.addProperty("current_goal", entity.getActiveGoalType().name());
        info.add("current_plan", buildPlanInfo(entity));
        info.add("objective", ObjectiveTracker.serialize(entity));

        String failReason = entity.getGoalFailedReason();
        info.addProperty("goal_failed_reason", failReason != null ? failReason : "");

        return info;
    }

    // ------------------------------------------------------------------
    // Companion state
    // ------------------------------------------------------------------

    private static JsonObject buildCompanionState(CompanionEntity entity) {
        JsonObject cs = new JsonObject();
        cs.addProperty("health", entity.getHealth());
        if (com.example.companion.CompanionConfig.get().hungerEnabled) {
            cs.addProperty("hunger", entity.getHunger());
        }

        JsonArray pos = new JsonArray();
        pos.add(Math.round(entity.getX() * 10) / 10.0);
        pos.add(Math.round(entity.getY() * 10) / 10.0);
        pos.add(Math.round(entity.getZ() * 10) / 10.0);
        cs.add("position", pos);

        cs.add("inventory", serializeInventory(entity));
        return cs;
    }

    private static JsonArray serializeInventory(CompanionEntity entity) {
        JsonArray arr = new JsonArray();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < entity.getCarriedInventory().getContainerSize(); i++) {
            ItemStack stack = entity.getCarriedInventory().getItem(i);
            if (stack.isEmpty()) continue;
            String name = ScanUtils.itemName(stack);
            counts.merge(name, stack.getCount(), Integer::sum);
        }
        counts.forEach((name, count) ->
                arr.add(count == 1 ? name : name + " x" + count));
        return arr;
    }

    // ------------------------------------------------------------------
    // Block scan
    // ------------------------------------------------------------------

    private static JsonArray scanBlocks(CompanionEntity entity, int radius) {
        Level world = entity.level();
        BlockPos origin = entity.blockPosition();

        Map<String, List<int[]>> grouped = new LinkedHashMap<>();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) continue;
                    if (!isInteresting(state)) continue;

                    String name = ScanUtils.blockName(state);
                    grouped.computeIfAbsent(name, k -> new ArrayList<>())
                           .add(new int[]{dx, dy, dz});
                }
            }
        }

        JsonArray arr = new JsonArray();
        for (Map.Entry<String, List<int[]>> entry : grouped.entrySet()) {
            if (arr.size() >= MAX_BLOCK_ENTRIES) break;
            int[] nearest = entry.getValue().stream()
                    .min(Comparator.comparingInt(r -> r[0]*r[0] + r[1]*r[1] + r[2]*r[2]))
                    .orElse(null);
            if (nearest == null) continue;

            JsonObject obj = new JsonObject();
            obj.addProperty("type", entry.getKey());
            obj.addProperty("count", entry.getValue().size());
            JsonArray rel = new JsonArray();
            rel.add(nearest[0]); rel.add(nearest[1]); rel.add(nearest[2]);
            obj.add("relative", rel);
            arr.add(obj);
        }
        return arr;
    }

    private static boolean isInteresting(BlockState state) {
        if (state.is(BlockTags.LOGS))         return true;
        if (state.is(BlockTags.COAL_ORES))    return true;
        if (state.is(BlockTags.IRON_ORES))    return true;
        if (state.is(BlockTags.GOLD_ORES))    return true;
        if (state.is(BlockTags.DIAMOND_ORES)) return true;
        if (state.is(BlockTags.EMERALD_ORES)) return true;
        if (state.is(BlockTags.REDSTONE_ORES))return true;
        if (state.is(BlockTags.LAPIS_ORES))   return true;
        if (state.is(BlockTags.COPPER_ORES))  return true;
        if (state.is(BlockTags.BEDS))         return true;
        if (state.is(BlockTags.DOORS))        return true;
        String path = ScanUtils.blockName(state);
        return MISC_INTERESTING.contains(path);
    }

    private static final Set<String> MISC_INTERESTING = Set.of(
            "crafting_table", "furnace", "chest", "trapped_chest",
            "water", "lava",
            "wheat", "carrots", "potatoes", "beetroots",
            "melon", "pumpkin", "sweet_berry_bush",
            "gravel", "sand",
            "cobblestone", "stone"
    );

    // ------------------------------------------------------------------
    // Entity scan
    // ------------------------------------------------------------------

    private static JsonArray scanEntities(CompanionEntity entity, int radius) {
        Level world = entity.level();
        AABB box = entity.getBoundingBox().inflate(radius);
        List<Entity> nearby = world.getEntities(entity, box);

        JsonArray arr = new JsonArray();
        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity)) continue;
            JsonObject obj = new JsonObject();
            obj.addProperty("type", e.getType().getDescriptionId());
            obj.addProperty("distance", Math.round(entity.distanceTo(e) * 10) / 10.0);
            boolean hostile = (e instanceof Mob mob) && mob.getTarget() != null;
            obj.addProperty("hostile", hostile);
            arr.add(obj);
        }
        return arr;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static JsonObject buildPlanInfo(CompanionEntity entity) {
        CompanionPlan plan = entity.getCurrentPlan();
        JsonObject obj = new JsonObject();
        if (plan == null || plan.isComplete()) {
            obj.addProperty("active", false);
            return obj;
        }
        obj.addProperty("active",      true);
        obj.addProperty("plan_id",     plan.getPlanId());
        obj.addProperty("name",        plan.getName());
        obj.addProperty("step",        plan.getCurrentStepIndex() + 1);
        obj.addProperty("total_steps", plan.getStepCount());
        PlanStep current = plan.currentStep();
        if (current != null) {
            obj.addProperty("current_step_goal", current.goal().name());
            if (!current.note().isEmpty()) obj.addProperty("current_step_note", current.note());
        }
        return obj;
    }

    private static JsonArray buildAvailableGoals() {
        com.example.companion.CompanionConfig cfg = com.example.companion.CompanionConfig.get();
        JsonArray arr = new JsonArray();
        for (GoalType type : GoalType.values()) {
            if (type == GoalType.IDLE) continue;
            if (type == GoalType.FIND_FOOD && !cfg.hungerEnabled) continue;
            arr.add(type.name());
        }
        return arr;
    }

    private static String biomeName(Level world, BlockPos pos) {
        return world.getBiome(pos).unwrapKey()
                .<String>map(k -> k.identifier().getPath())
                .orElse("unknown");
    }

    private static String timeLabel(long tod) {
        if (tod < 1000 || tod >= 23000) return "dawn";
        if (tod < 12000)                return "day";
        if (tod < 13000)                return "dusk";
        return "night";
    }

    private static String weatherLabel(Level world) {
        if (world.isThundering()) return "thunder";
        if (world.isRaining())    return "rain";
        return "clear";
    }
}
