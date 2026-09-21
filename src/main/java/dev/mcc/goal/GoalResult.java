package dev.mcc.goal;

/** Immutable record of a finished goal, surfaced to the Brain for re-planning. */
public record GoalResult(GoalSpec spec, TaskStatus status, String reason) {}