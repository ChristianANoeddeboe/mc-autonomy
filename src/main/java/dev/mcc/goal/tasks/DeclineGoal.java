package dev.mcc.goal.tasks;

import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;
import dev.mcc.goal.GoalType;

/**
 * Structurally honours a decline: completes immediately with no action — the
 * requested goal is simply never created. The spoken reason is emitted by the
 * Brain via Speaker. Logs [MCC][decline] with the reason.
 */
public final class DeclineGoal implements GoalTask {
	private final GoalSpec spec;

	public DeclineGoal(GoalSpec spec) { this.spec = spec; }

	@Override public void start(CompanionEntity e) {
		String reason = spec.strParam("reason", "");
		dev.mcc.MccMod.LOGGER.info("[MCC][decline] reason={}", reason);
	}

	@Override public TaskStatus tick(CompanionEntity e) { return TaskStatus.DONE; }
	@Override public void stop(CompanionEntity e) {}
	@Override public String progress() { return "DECLINE " + spec.strParam("reason", ""); }
	@Override public GoalSpec spec() { return spec; }
}