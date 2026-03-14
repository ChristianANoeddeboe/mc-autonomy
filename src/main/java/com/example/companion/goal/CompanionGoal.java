package com.example.companion.goal;

import java.util.Map;

/**
 * Interface every high-level companion goal must implement.
 * Each goal is a small state machine driven by {@link GoalExecutor}.
 */
public interface CompanionGoal {

    /** Called once when this goal becomes active. */
    void start(Map<String, Object> params);

    /** Called every game tick while this goal is active. */
    void tick();

    /** @return true when the goal has been successfully completed. */
    boolean isComplete();

    /** @return true when the goal has failed and cannot continue. */
    boolean isFailed();

    /** Human-readable reason returned to {@link AIGoalPlanner} on failure. */
    String failureReason();

    /** Reset internal state so the goal can be reused. */
    void reset();
}
