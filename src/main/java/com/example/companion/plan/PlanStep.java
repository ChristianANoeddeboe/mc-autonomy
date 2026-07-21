package com.example.companion.plan;

import com.example.companion.goal.GoalType;

import java.util.Map;

/**
 * A single step inside a {@link CompanionPlan}.
 *
 * @param goal   Goal to execute.
 * @param params Parameters forwarded to the goal's {@code start()} method.
 * @param note   Human-readable description produced by the AI (used for logging).
 */
public record PlanStep(GoalType goal, Map<String, Object> params, String note) {

    /** Convenience constructor for steps with no parameters. */
    public PlanStep(GoalType goal, String note) {
        this(goal, Map.of(), note);
    }
}
