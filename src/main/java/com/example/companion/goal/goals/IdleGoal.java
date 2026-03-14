package com.example.companion.goal.goals;

import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;

import java.util.Map;

/**
 * IDLE — does nothing.  Complete condition: next planner tick.
 * This goal never self-completes; the planner fires a new request after the
 * fallback timer, at which point a real goal is assigned.
 */
public class IdleGoal implements CompanionGoal {

    private final CompanionEntity entity;

    public IdleGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override public void start(Map<String, Object> params) {}
    @Override public void tick() {}
    @Override public boolean isComplete() { return false; }
    @Override public boolean isFailed()   { return false; }
    @Override public String failureReason() { return null; }
    @Override public void reset() {}
}
