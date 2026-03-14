package com.example.companion.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.Block;
import net.minecraft.registry.tag.BlockTags;

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
        root.addProperty("entity_id", entity.getUuidAsString());
        root.addProperty("tick", (int) entity.getWorld().getTime());
        root.add("world", buildWorldInfo(entity, radius));
        return root;
    }

    // ------------------------------------------------------------------
    // World info
    // ------------------------------------------------------------------

    private static JsonObject buildWorldInfo(CompanionEntity entity, int radius) {
        World world = entity.getWorld();
        JsonObject info = new JsonObject();

        long tod = world.getTimeOfDay() % 24000;
        info.addProperty("time_of_day", timeLabel(tod));
        info.addProperty("weather", weatherLabel(world));
        info.addProperty("biome", biomeName(world, entity.getBlockPos()));
        info.addProperty("light_level", world.getLightLevel(entity.getBlockPos()));

        info.add("nearby_blocks",   scanBlocks(entity, radius));
        info.add("nearby_entities", scanEntities(entity, radius));
        info.add("companion",       buildCompanionState(entity));

        String lastMsg = entity.getLastPlayerMessage();
        if (lastMsg != null) info.addProperty("last_player_message", lastMsg);
        else                 info.add("last_player_message", JsonNull.INSTANCE);

        info.addProperty("current_goal", entity.getActiveGoalType().name());

        String failReason = entity.getGoalFailedReason();
        if (failReason != null) info.addProperty("goal_failed_reason", failReason);
        else                    info.add("goal_failed_reason", JsonNull.INSTANCE);

        return info;
    }

    // ------------------------------------------------------------------
    // Companion state
    // ------------------------------------------------------------------

    private static JsonObject buildCompanionState(CompanionEntity entity) {
        JsonObject cs = new JsonObject();
        cs.addProperty("health", entity.getHealth());
        cs.addProperty("hunger", entity.getHunger());

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
        // Aggregate identical items into "item x count" strings
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : entity.getCarriedInventory()) {
            if (stack.isEmpty()) continue;
            String name = Registries.ITEM.getId(stack.getItem()).getPath();
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
        World world = entity.getWorld();
        BlockPos origin = entity.getBlockPos();

        // Collect interesting blocks, grouped by type to avoid payload bloat
        Map<String, List<int[]>> grouped = new LinkedHashMap<>();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) continue;
                    if (!isInteresting(state)) continue;

                    String name = Registries.BLOCK.getId(state.getBlock()).getPath();
                    grouped.computeIfAbsent(name, k -> new ArrayList<>())
                           .add(new int[]{dx, dy, dz});
                }
            }
        }

        // Flatten: for each type keep only the nearest occurrence, cap total entries
        JsonArray arr = new JsonArray();
        outer:
        for (Map.Entry<String, List<int[]>> entry : grouped.entrySet()) {
            if (arr.size() >= MAX_BLOCK_ENTRIES) break;
            // Nearest relative position
            int[] nearest = entry.getValue().stream()
                    .min(Comparator.comparingInt(r -> r[0]*r[0] + r[1]*r[1] + r[2]*r[2]))
                    .orElse(null);
            if (nearest == null) continue;

            JsonObject obj = new JsonObject();
            obj.addProperty("type", entry.getKey());
            obj.addProperty("count", entry.getValue().size());
            JsonArray rel = new JsonArray();
            rel.add(nearest[0]); rel.add(nearest[1]); rel.add(nearest[2]);
            obj.add("nearest_relative", rel);
            arr.add(obj);
        }
        return arr;
    }

    private static boolean isInteresting(BlockState state) {
        if (state.isIn(BlockTags.LOGS))         return true;
        if (state.isIn(BlockTags.COAL_ORES))    return true;
        if (state.isIn(BlockTags.IRON_ORES))    return true;
        if (state.isIn(BlockTags.GOLD_ORES))    return true;
        if (state.isIn(BlockTags.DIAMOND_ORES)) return true;
        if (state.isIn(BlockTags.EMERALD_ORES)) return true;
        if (state.isIn(BlockTags.REDSTONE_ORES))return true;
        if (state.isIn(BlockTags.LAPIS_ORES))   return true;
        if (state.isIn(BlockTags.COPPER_ORES))  return true;
        if (state.isIn(BlockTags.BEDS))         return true;
        if (state.isIn(BlockTags.DOORS))        return true;
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
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
        World world = entity.getWorld();
        List<Entity> nearby = world.getOtherEntities(entity,
                entity.getBoundingBox().expand(radius));

        JsonArray arr = new JsonArray();
        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity living)) continue;
            JsonObject obj = new JsonObject();
            obj.addProperty("type", e.getType().getUntranslatedName());
            obj.addProperty("distance", Math.round(entity.distanceTo(e) * 10) / 10.0);
            boolean hostile = (e instanceof MobEntity mob)
                    && mob.getTarget() != null;
            obj.addProperty("hostile", hostile);
            arr.add(obj);
        }
        return arr;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String biomeName(World world, BlockPos pos) {
        RegistryEntry<Biome> entry = world.getBiome(pos);
        return entry.getKey()
                .map(k -> k.getValue().getPath())
                .orElse("unknown");
    }

    private static String timeLabel(long tod) {
        if (tod < 1000 || tod >= 23000) return "dawn";
        if (tod < 12000)                return "day";
        if (tod < 13000)                return "dusk";
        return "night";
    }

    private static String weatherLabel(World world) {
        if (world.isThundering()) return "thunder";
        if (world.isRaining())    return "rain";
        return "clear";
    }
}
