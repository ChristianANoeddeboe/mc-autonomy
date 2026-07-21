package com.example.companion.goal.goals;

import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;

import java.util.Map;

/**
 * IDLE — does nothing.
 *
 * <p>Never self-completes.  {@code PlanExecutor.tick()} treats a plan parked on
 * an IDLE step as replannable, so the fallback timer fires a new plan request
 * and a real goal gets assigned.
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
