package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;

import java.util.Map;

/**
 * BUILD_SHELTER — construct a minimal enclosed space with a door.
 *
 * <p>Complete when: enclosed structure with door exists.
 * <p>Fails when: insufficient materials and night has arrived.
 *
 * <p>TODO (Phase 4): material check, site selection, block placement sequence.
 */
public class BuildShelterGoal implements CompanionGoal {

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;

    public BuildShelterGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false;
        CompanionMod.LOGGER.info("BuildShelterGoal started");
    }

    @Override public void tick() { /* TODO */ }
    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Insufficient materials and night arrived"; }
    @Override public void reset() { failed = false; complete = false; }
}
