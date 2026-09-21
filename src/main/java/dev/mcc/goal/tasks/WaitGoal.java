package dev.mcc.goal.tasks;

import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;

/** Idle for a fixed number of ticks. ModeBehaviour still runs meanwhile. */
public final class WaitGoal implements GoalTask {
	private final GoalSpec spec;
	private int remaining;

	public WaitGoal(GoalSpec spec) {
		this.spec = spec;
		this.remaining = spec.intParam("ticks", 100);
	}

	@Override public void start(CompanionEntity e) {}
	@Override public TaskStatus tick(CompanionEntity e) {
		return --remaining <= 0 ? TaskStatus.DONE : TaskStatus.RUNNING;
	}
	@Override public void stop(CompanionEntity e) {}
	@Override public String progress() { return "WAIT remaining=" + remaining; }
	@Override public GoalSpec spec() { return spec; }
}