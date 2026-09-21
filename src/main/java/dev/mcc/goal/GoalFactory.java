package dev.mcc.goal;

import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.tasks.MoveToGoal;
import dev.mcc.goal.tasks.ModeGoal;
import dev.mcc.goal.tasks.WaitGoal;

/** Maps a GoalSpec to a concrete GoalTask. */
public final class GoalFactory {
	private GoalFactory() {}

	public static GoalTask create(GoalSpec spec, CompanionEntity e) {
		return switch (spec.type()) {
			case MOVE_TO -> new MoveToGoal(spec, e);
			case FOLLOW, STAY, GUARD -> new ModeGoal(spec, e);
			case WAIT -> new WaitGoal(spec);
			default -> new UnimplementedGoal(spec);
		};
	}

	/** Placeholder until the owning phase lands; never silently succeeds. */
	public static final class UnimplementedGoal implements GoalTask {
		private final GoalSpec spec;
		public UnimplementedGoal(GoalSpec spec) { this.spec = spec; }
		@Override public void start(CompanionEntity e) {}
		@Override public TaskStatus tick(CompanionEntity e) { return TaskStatus.FAILED; }
		@Override public void stop(CompanionEntity e) {}
		@Override public String progress() { return "unimplemented:" + spec.type(); }
		@Override public GoalSpec spec() { return spec; }
	}
}