package dev.mcc.goal;

import dev.mcc.entity.CompanionEntity;

/**
 * A runnable goal. Deterministic; no LLM. Implementations use the Navigator to
 * move and report progress.
 */
public interface GoalTask {
	void start(CompanionEntity e);
	TaskStatus tick(CompanionEntity e);
	void stop(CompanionEntity e);
	String progress();
	GoalSpec spec();
}