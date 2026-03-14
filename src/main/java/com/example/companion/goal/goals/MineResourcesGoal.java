package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.ScanUtils;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.Map;
import java.util.function.Predicate;

/**
 * MINE_RESOURCES — descend into the earth and mine a target ore type.
 *
 * <p>Complete when: any item matching the target ore appears in inventory.
 * <p>Fails when: search times out or navigation repeatedly stalls.
 *
 * <p>The companion digs a 1×2 staircase down while scanning each level for the
 * target ore. When found it navigates to and breaks the ore block.
 */
public class MineResourcesGoal implements CompanionGoal {

    private static final int    SCAN_RADIUS          = 12;
    private static final double ORE_REACH            = 2.5;
    private static final double WALK_SPEED           = 1.0;
    private static final int    SEARCH_TIMEOUT_TICKS = 20 * 120; // 2 min
    private static final int    NAV_TIMEOUT_TICKS    = 20 * 30;
    /** Target Y level (below iron ore depth for iron, diamond level for diamonds). */
    private static final int    TARGET_Y_IRON        = 16;
    private static final int    TARGET_Y_DIAMOND      = -58;

    private enum State { DESCENDING, SCANNING, NAVIGATING_TO_ORE, MINING, COLLECTING }

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;

    private State state = State.SCANNING;
    private Predicate<BlockState> orePredicate;
    private Predicate<net.minecraft.item.ItemStack> itemPredicate;
    private String targetName = "iron";
    private int targetY = TARGET_Y_IRON;

    private BlockPos targetOre = null;
    private int ticksElapsed = 0;
    private int navTicks = 0;
    private int descentSteps = 0;

    public MineResourcesGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false; state = State.SCANNING;
        targetOre = null; ticksElapsed = 0; navTicks = 0; descentSteps = 0;

        targetName = (String) params.getOrDefault("target", "iron");
        setupTarget(targetName);
        CompanionMod.LOGGER.info("MineResourcesGoal started — target={} targetY={}", targetName, targetY);
    }

    private void setupTarget(String name) {
        switch (name.toLowerCase()) {
            case "gold"     -> { orePredicate = s -> s.isIn(BlockTags.GOLD_ORES);
                                 targetY = 20; }
            case "diamond"  -> { orePredicate = s -> s.isIn(BlockTags.DIAMOND_ORES);
                                 targetY = TARGET_Y_DIAMOND; }
            case "coal"     -> { orePredicate = s -> s.isIn(BlockTags.COAL_ORES);
                                 targetY = 64; }
            case "redstone" -> { orePredicate = s -> s.isIn(BlockTags.REDSTONE_ORES);
                                 targetY = -40; }
            case "lapis"    -> { orePredicate = s -> s.isIn(BlockTags.LAPIS_ORES);
                                 targetY = 0; }
            case "copper"   -> { orePredicate = s -> s.isIn(BlockTags.COPPER_ORES);
                                 targetY = 48; }
            case "emerald"  -> { orePredicate = s -> s.isIn(BlockTags.EMERALD_ORES);
                                 targetY = 100; }
            default         -> { orePredicate = s -> s.isIn(BlockTags.IRON_ORES);
                                 targetY = TARGET_Y_IRON; }
        }
        String ore = targetName + "_ore";
        itemPredicate = s -> ScanUtils.itemName(s).contains(targetName + "_ore")
                || ScanUtils.itemName(s).contains("raw_" + targetName);
    }

    @Override
    public void tick() {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return;

        ticksElapsed++;
        if (ticksElapsed >= SEARCH_TIMEOUT_TICKS) {
            failed = true;
            return;
        }

        // Completion check
        if (ScanUtils.countInInventory(entity, itemPredicate) > 0) {
            complete = true;
            entity.getNavigation().stop();
            CompanionMod.LOGGER.info("MineResourcesGoal: {} collected", targetName);
            return;
        }

        ScanUtils.collectNearbyItems(entity, 2.0);

        switch (state) {
            case SCANNING -> {
                targetOre = ScanUtils.findNearestBlock(entity, SCAN_RADIUS, orePredicate);
                if (targetOre != null) {
                    navTicks = 0;
                    state = State.NAVIGATING_TO_ORE;
                    entity.getNavigation().startMovingTo(
                            targetOre.getX(), targetOre.getY(), targetOre.getZ(), WALK_SPEED);
                } else {
                    state = State.DESCENDING;
                }
            }
            case DESCENDING -> {
                int currentY = entity.getBlockY();
                if (currentY <= targetY + 5) {
                    // At target depth — switch to scanning
                    state = State.SCANNING;
                    return;
                }
                // Dig one block down (break block at feet and one below)
                BlockPos feet = entity.getBlockPos();
                BlockPos below = feet.down();
                if (!sw.getBlockState(below).isAir()) {
                    sw.breakBlock(below, false, entity);
                }
                if (!sw.getBlockState(feet).isAir()) {
                    sw.breakBlock(feet, false, entity);
                }
                // Step diagonally: move south + down each cycle
                entity.getNavigation().startMovingTo(
                        entity.getX(), currentY - 1, entity.getZ() + 1, WALK_SPEED);
                descentSteps++;
                if (descentSteps % 8 == 0) state = State.SCANNING; // scan every 8 steps
            }
            case NAVIGATING_TO_ORE -> {
                navTicks++;
                if (navTicks > NAV_TIMEOUT_TICKS || targetOre == null) {
                    state = State.SCANNING; return;
                }
                if (!entity.getWorld().getBlockState(targetOre).isIn(getOreTag())) {
                    state = State.SCANNING; return;
                }
                if (ScanUtils.isReachable(entity, targetOre, ORE_REACH)) {
                    entity.getNavigation().stop();
                    state = State.MINING;
                }
            }
            case MINING -> {
                if (targetOre == null || !entity.getWorld().getBlockState(targetOre).isIn(getOreTag())) {
                    state = State.COLLECTING; return;
                }
                sw.breakBlock(targetOre, true, entity);
                ScanUtils.collectNearbyItems(entity, 3.0);
                state = State.SCANNING; // scan for more ore
            }
            case COLLECTING -> {
                ScanUtils.collectNearbyItems(entity, 3.0);
                state = State.SCANNING;
            }
        }
    }

    /** Needed for state checks in NAVIGATING_TO_ORE — uses the configured predicate via tag. */
    private TagKey<Block> getOreTag() {
        return switch (targetName.toLowerCase()) {
            case "gold"     -> BlockTags.GOLD_ORES;
            case "diamond"  -> BlockTags.DIAMOND_ORES;
            case "coal"     -> BlockTags.COAL_ORES;
            case "redstone" -> BlockTags.REDSTONE_ORES;
            case "lapis"    -> BlockTags.LAPIS_ORES;
            case "copper"   -> BlockTags.COPPER_ORES;
            case "emerald"  -> BlockTags.EMERALD_ORES;
            default         -> BlockTags.IRON_ORES;
        };
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Could not find " + targetName + " ore"; }

    @Override
    public void reset() {
        failed = false; complete = false; state = State.SCANNING;
        targetOre = null; ticksElapsed = 0; navTicks = 0; descentSteps = 0;
        entity.getNavigation().stop();
    }
}
