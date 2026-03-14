package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;

import java.util.Map;

/**
 * SLEEP — find a bed and sleep until morning.
 *
 * <p>Complete when: morning arrives (time ~0).
 * <p>Fails when: no bed is found or the area is unsafe.
 *
 * <p>TODO (Phase 4): bed scan, navigation, sleep action.
 */
public class SleepGoal implements CompanionGoal {

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;

    public SleepGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false;
        CompanionMod.LOGGER.info("SleepGoal started");
    }

    @Override
    public void tick() {
        long timeOfDay = entity.getWorld().getTimeOfDay() % 24000;
        if (timeOfDay < 500 || timeOfDay > 23500) { // morning
            complete = true;
        }
        // TODO: locate bed, navigate, trigger sleep
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "No bed found or area is unsafe"; }
    @Override public void reset() { failed = false; complete = false; }
}
