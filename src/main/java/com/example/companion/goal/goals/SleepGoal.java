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
 * SLEEP — find a bed, navigate to it, and sleep until morning.
 *
 * <p>Complete when: morning arrives (time of day in [23000, 24000) ∪ [0, 1000)).
 * <p>Fails when: no bed found within {@value SCAN_RADIUS} blocks, or the area
 * is not safe enough to sleep.
 */
public class SleepGoal implements CompanionGoal {

    private static final int    SCAN_RADIUS       = 32;
    private static final double BED_REACH         = 2.5;
    private static final double WALK_SPEED        = 1.0;
    private static final int    NAV_TIMEOUT_TICKS = 20 * 30;

    private enum State { SCANNING, NAVIGATING, SLEEPING }

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;

    private State state = State.SCANNING;
    private BlockPos bedPos = null;
    private int navTicks = 0;

    public SleepGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false; state = State.SCANNING;
        bedPos = null; navTicks = 0;
        CompanionMod.LOGGER.info("SleepGoal started");
    }

    @Override
    public void tick() {
        // Morning check — complete regardless of sleep state
        long tod = entity.getWorld().getTimeOfDay() % 24000;
        if (tod < 1000 || tod > 23000) {
            if (entity.isSleeping()) entity.wakeUp();
            complete = true;
            entity.getNavigation().stop();
            return;
        }

        if (!(entity.getWorld() instanceof ServerWorld sw)) return;

        switch (state) {
            case SCANNING -> {
                bedPos = ScanUtils.findNearestBlock(entity, SCAN_RADIUS,
                        blockState -> blockState.isIn(BlockTags.BEDS));
                if (bedPos == null) {
                    CompanionMod.LOGGER.info("SleepGoal: no bed found — failing");
                    failed = true;
                } else {
                    navTicks = 0;
                    state = State.NAVIGATING;
                    entity.getNavigation().startMovingTo(
                            bedPos.getX(), bedPos.getY(), bedPos.getZ(), WALK_SPEED);
                }
            }
            case NAVIGATING -> {
                navTicks++;
                if (navTicks > NAV_TIMEOUT_TICKS) {
                    CompanionMod.LOGGER.warn("SleepGoal: could not reach bed — failing");
                    failed = true;
                    return;
                }
                if (!entity.getWorld().getBlockState(bedPos).isIn(BlockTags.BEDS)) {
                    state = State.SCANNING; return; // bed removed
                }
                if (ScanUtils.isReachable(entity, bedPos, BED_REACH)) {
                    entity.getNavigation().stop();
                    entity.sleep(bedPos);
                    state = State.SLEEPING;
                    CompanionMod.LOGGER.info("SleepGoal: sleeping at {}", bedPos);
                }
            }
            case SLEEPING -> {
                // Remain here until morning check above triggers completion
                if (!entity.isSleeping()) {
                    // Woken up externally (explosion, mob, etc.) — rescan
                    state = State.SCANNING;
                }
            }
        }
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "No bed found or could not reach it"; }

    @Override
    public void reset() {
        if (entity.isSleeping()) entity.wakeUp();
        failed = false; complete = false; state = State.SCANNING;
        bedPos = null; navTicks = 0;
        entity.getNavigation().stop();
    }
}
