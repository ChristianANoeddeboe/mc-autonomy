package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;
import com.example.companion.util.ScanUtils;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * GATHER_WOOD — locate, navigate to, and chop logs until the inventory holds
 * at least {@code targetCount} logs.
 *
 * <p>State machine: SCANNING → NAVIGATING → CHOPPING → (back to SCANNING)
 * <p>Complete when: log count in inventory ≥ targetCount.
 * <p>Fails when: no logs found after {@value MAX_SCAN_ATTEMPTS} scan cycles.
 */
public class GatherWoodGoal implements CompanionGoal {

    private static final int DEFAULT_TARGET  = 8;
    private static final int SCAN_RADIUS     = 24;
    private static final double CHOP_REACH   = 3.0;
    private static final double WALK_SPEED   = 1.0;
    private static final int MAX_SCAN_ATTEMPTS = 4;
    private static final int NAV_TIMEOUT_TICKS = 20 * 30; // 30 s

    private enum State { SCANNING, NAVIGATING, CHOPPING }

    private final CompanionEntity entity;
    private int targetCount = DEFAULT_TARGET;
    private boolean failed = false;
    private boolean complete = false;

    private State state = State.SCANNING;
    private BlockPos targetLog = null;
    private int scanAttempts = 0;
    private int navTicks = 0;

    public GatherWoodGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false;
        state = State.SCANNING; targetLog = null;
        scanAttempts = 0; navTicks = 0;
        if (params.containsKey("count")) {
            targetCount = ((Number) params.get("count")).intValue();
        }
        CompanionMod.LOGGER.info("GatherWoodGoal started — target={}", targetCount);
    }

    @Override
    public void tick() {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return;

        // Check completion first
        int logsHeld = ScanUtils.countInInventory(entity,
                s -> Block_isLog(s));
        if (logsHeld >= targetCount) {
            complete = true;
            entity.getNavigation().stop();
            CompanionMod.LOGGER.info("GatherWoodGoal: collected {} logs", logsHeld);
            return;
        }

        // Collect any dropped logs within arm's reach
        ScanUtils.collectNearbyItems(entity, 2.0);

        switch (state) {
            case SCANNING -> {
                targetLog = ScanUtils.findNearestBlock(entity, SCAN_RADIUS,
                        blockState -> blockState.isIn(BlockTags.LOGS));
                if (targetLog == null) {
                    scanAttempts++;
                    CompanionMod.LOGGER.debug("GatherWoodGoal: no logs found (attempt {})", scanAttempts);
                    if (scanAttempts >= MAX_SCAN_ATTEMPTS) {
                        failed = true;
                    }
                } else {
                    navTicks = 0;
                    state = State.NAVIGATING;
                    entity.getNavigation().startMovingTo(
                            targetLog.getX(), targetLog.getY(), targetLog.getZ(), WALK_SPEED);
                }
            }
            case NAVIGATING -> {
                navTicks++;
                if (navTicks > NAV_TIMEOUT_TICKS) {
                    CompanionMod.LOGGER.debug("GatherWoodGoal: navigation timed out, re-scanning");
                    state = State.SCANNING;
                    return;
                }
                // Re-verify block still exists (another player may have chopped it)
                if (!entity.getWorld().getBlockState(targetLog).isIn(BlockTags.LOGS)) {
                    state = State.SCANNING;
                    return;
                }
                if (ScanUtils.isReachable(entity, targetLog, CHOP_REACH)) {
                    entity.getNavigation().stop();
                    state = State.CHOPPING;
                }
            }
            case CHOPPING -> {
                if (!entity.getWorld().getBlockState(targetLog).isIn(BlockTags.LOGS)) {
                    // Already broken (or was never there) — collect drops and rescan
                    ScanUtils.collectNearbyItems(entity, 3.0);
                    state = State.SCANNING;
                    return;
                }
                sw.breakBlock(targetLog, true, entity);
                ScanUtils.collectNearbyItems(entity, 3.0);
                state = State.SCANNING;
            }
        }
    }

    /** True if the item is a log (checks the block form of the item). */
    private boolean Block_isLog(net.minecraft.item.ItemStack stack) {
        net.minecraft.block.Block b = net.minecraft.block.Block.getBlockFromItem(stack.getItem());
        return b.getDefaultState().isIn(BlockTags.LOGS);
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "No trees found within " + SCAN_RADIUS + " blocks"; }

    @Override
    public void reset() {
        failed = false; complete = false; state = State.SCANNING;
        targetLog = null; scanAttempts = 0; navTicks = 0;
        entity.getNavigation().stop();
    }
}
