package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;

import java.util.Map;

/**
 * GATHER_WOOD — locate and chop nearby oak/birch/spruce logs.
 *
 * <p>Complete when: N logs collected (default 8).
 * <p>Fails when: no trees found within search radius after {@code SEARCH_TIMEOUT_TICKS}.
 *
 * <p>TODO (Phase 4): implement block scan, navigation, and chop action.
 */
public class GatherWoodGoal implements CompanionGoal {

    private static final int SEARCH_TIMEOUT_TICKS = 20 * 60; // 60 s

    private final CompanionEntity entity;
    private int ticksElapsed = 0;
    private boolean failed = false;
    private boolean complete = false;

    public GatherWoodGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        ticksElapsed = 0;
        failed = false;
        complete = false;
        CompanionMod.LOGGER.info("GatherWoodGoal started");
        // TODO: read optional "count" param
    }

    @Override
    public void tick() {
        ticksElapsed++;
        // TODO: scan for logs, navigate, break blocks, track inventory count
        if (ticksElapsed >= SEARCH_TIMEOUT_TICKS) {
            failed = true;
        }
    }

    @Override public boolean isComplete()     { return complete; }
    @Override public boolean isFailed()       { return failed; }
    @Override public String failureReason()   { return "No trees found within search radius"; }
    @Override public void reset() { ticksElapsed = 0; failed = false; complete = false; }
}
