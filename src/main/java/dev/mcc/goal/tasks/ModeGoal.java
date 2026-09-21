package dev.mcc.goal.tasks;

import dev.mcc.entity.BehaviourMode;
import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;

/**
 * Mode setter: FOLLOW / STAY / GUARD. start() switches the entity's behaviour
 * mode and completes immediately; the ongoing mode behaviour runs in mode()
 * within ModeBehaviour (executor-level, Phase 4).
 */
public final class ModeGoal implements GoalTask {
	private final GoalSpec spec;
	private final BehaviourMode mode;
	private boolean done;

	public ModeGoal(GoalSpec spec, CompanionEntity e) {
		this.spec = spec;
		this.mode = switch (spec.type()) {
			case STAY -> BehaviourMode.STAY;
			case GUARD -> BehaviourMode.GUARD;
			default -> BehaviourMode.FOLLOW;
		};
	}

	@Override public void start(CompanionEntity e) {
		e.setMode(mode);
		if (mode == BehaviourMode.STAY || mode == BehaviourMode.GUARD) {
			e.setAnchor(e.blockPosition());
		}
		done = true;
	}

	@Override public TaskStatus tick(CompanionEntity e) {
		return done ? TaskStatus.DONE : TaskStatus.RUNNING;
	}

	@Override public void stop(CompanionEntity e) {}
	@Override public String progress() { return "mode=" + mode; }
	@Override public GoalSpec spec() { return spec; }
}