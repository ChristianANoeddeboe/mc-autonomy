package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.BlockBreaker;
import com.example.companion.util.ScanUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

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
    private final BlockBreaker    breaker = new BlockBreaker();
    private boolean failed = false;
    private boolean complete = false;

    private State state = State.SCANNING;
    private Predicate<BlockState> orePredicate;
    private Predicate<net.minecraft.world.item.ItemStack> itemPredicate;
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
        if (entity.level() instanceof ServerLevel sw) breaker.clear(entity.getId(), sw);

        targetName = (String) params.getOrDefault("target", "iron");
        setupTarget(targetName);

        // Hard guard — fail immediately so the planner knows to craft a pickaxe first
        boolean hasPickaxe = entity.getNonEmptyStacks().stream()
                .anyMatch(s -> s.is(ItemTags.PICKAXES));
        if (!hasPickaxe) {
            failed = true;
            CompanionMod.LOGGER.warn("MineResourcesGoal: no pickaxe in inventory — failing so planner can craft one");
            return;
        }

        CompanionMod.LOGGER.info("MineResourcesGoal started — target={} targetY={}", targetName, targetY);
    }

    private void setupTarget(String name) {
        switch (name.toLowerCase()) {
            case "gold"     -> { orePredicate = s -> s.is(BlockTags.GOLD_ORES);
                                 targetY = 20; }
            case "diamond"  -> { orePredicate = s -> s.is(BlockTags.DIAMOND_ORES);
                                 targetY = TARGET_Y_DIAMOND; }
            case "coal"     -> { orePredicate = s -> s.is(BlockTags.COAL_ORES);
                                 targetY = 64; }
            case "redstone" -> { orePredicate = s -> s.is(BlockTags.REDSTONE_ORES);
                                 targetY = -40; }
            case "lapis"    -> { orePredicate = s -> s.is(BlockTags.LAPIS_ORES);
                                 targetY = 0; }
            case "copper"   -> { orePredicate = s -> s.is(BlockTags.COPPER_ORES);
                                 targetY = 48; }
            case "emerald"  -> { orePredicate = s -> s.is(BlockTags.EMERALD_ORES);
                                 targetY = 100; }
            default         -> { orePredicate = s -> s.is(BlockTags.IRON_ORES);
                                 targetY = TARGET_Y_IRON; }
        }
        itemPredicate = s -> ScanUtils.itemName(s).contains(targetName + "_ore")
                || ScanUtils.itemName(s).contains("raw_" + targetName);
    }

    @Override
    public void tick() {
        if (!(entity.level() instanceof ServerLevel sw)) return;

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
                    int depthDelta = entity.getBlockY() - targetOre.getY();
                    if (depthDelta > 4) {
                        // Ore is deeper than pathfinding can reach — dig down first
                        state = State.DESCENDING;
                    } else {
                        navTicks = 0;
                        state = State.NAVIGATING_TO_ORE;
                        entity.getNavigation().moveTo(
                                targetOre.getX(), targetOre.getY(), targetOre.getZ(), WALK_SPEED);
                    }
                } else {
                    state = State.DESCENDING;
                }
            }
            case DESCENDING -> {
                int currentY = entity.getBlockY();
                if (currentY <= targetY + 5) {
                    state = State.SCANNING;
                    return;
                }
                // Dig straight down — break the floor block with timing, then fall naturally
                entity.getNavigation().stop();
                BlockPos floor = entity.blockPosition().below();
                ItemStack tool = BlockBreaker.bestToolFor(entity.getNonEmptyStacks(), sw.getBlockState(floor));
                boolean broke = breaker.tick(entity.getId(), sw, floor, tool, entity);
                if (broke) {
                    // Also clear the block 2 below to create a 2-tall shaft
                    BlockPos deeper = floor.below();
                    ItemStack tool2 = BlockBreaker.bestToolFor(entity.getNonEmptyStacks(), sw.getBlockState(deeper));
                    breaker.tick(entity.getId(), sw, deeper, tool2, entity);
                    descentSteps++;
                    if (descentSteps % 8 == 0) state = State.SCANNING;
                }
            }
            case NAVIGATING_TO_ORE -> {
                navTicks++;
                if (navTicks > NAV_TIMEOUT_TICKS || targetOre == null) {
                    state = State.DESCENDING; return;
                }
                if (!entity.level().getBlockState(targetOre).is(getOreTag())) {
                    state = State.SCANNING; return;
                }
                // Pathfinding gave up quickly → can't route there, dig down instead
                if (navTicks > 5 && entity.getNavigation().isDone()) {
                    state = State.DESCENDING; return;
                }
                if (ScanUtils.isReachable(entity, targetOre, ORE_REACH)) {
                    entity.getNavigation().stop();
                    state = State.MINING;
                }
            }
            case MINING -> {
                if (targetOre == null || !entity.level().getBlockState(targetOre).is(getOreTag())) {
                    breaker.clear(entity.getId(), sw);
                    state = State.COLLECTING; return;
                }
                ItemStack tool = BlockBreaker.bestToolFor(entity.getNonEmptyStacks(), sw.getBlockState(targetOre));
                boolean broke = breaker.tick(entity.getId(), sw, targetOre, tool, entity);
                if (broke) {
                    ScanUtils.collectNearbyItems(entity, 3.0);
                    state = State.SCANNING;
                }
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
    @Override public String failureReason() {
        boolean hasPickaxe = entity.getNonEmptyStacks().stream().anyMatch(s -> s.is(ItemTags.PICKAXES));
        return hasPickaxe ? "Could not find " + targetName + " ore"
                          : "No pickaxe in inventory — craft one before mining";
    }

    @Override
    public void reset() {
        failed = false; complete = false; state = State.SCANNING;
        targetOre = null; ticksElapsed = 0; navTicks = 0; descentSteps = 0;
        if (entity.level() instanceof ServerLevel sw) breaker.clear(entity.getId(), sw);
        entity.getNavigation().stop();
    }
}
