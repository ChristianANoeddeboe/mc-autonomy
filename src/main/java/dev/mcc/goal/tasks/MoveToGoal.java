package dev.mcc.goal.tasks;

import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Navigate to a fixed BlockPos. DONE on arrival, FAILED on no-path. */
public final class MoveToGoal implements GoalTask {
	private final GoalSpec spec;
	private final BlockPos target;
	private final double reachDist;

	public MoveToGoal(GoalSpec spec, CompanionEntity entity) {
		this.spec = spec;
		this.target = new BlockPos(spec.intParam("x", 0), spec.intParam("y", 0), spec.intParam("z", 0));
		this.reachDist = spec.doubleParam("reach", CompanionConfig.get().nav.reachDist);
	}

	@Override public void start(CompanionEntity e) {
		e.navigator().goTo(target, reachDist);
	}

	@Override public TaskStatus tick(CompanionEntity e) {
		return switch (e.navigator().tick()) {
			case ARRIVED -> TaskStatus.DONE;
			case NO_PATH -> TaskStatus.FAILED;
			default -> TaskStatus.RUNNING;
		};
	}

	@Override public void stop(CompanionEntity e) {
		e.navigator().stop();
	}

	@Override public String progress() {
		return "MOVE_TO " + target;
	}

	@Override public GoalSpec spec() { return spec; }
}