package dev.mcc.goal.tasks;

import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;
import dev.mcc.goal.GoalType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/**
 * Reflexive flee: run away from the nearest hostile. Entered via preempt();
 * completes when no hostile is within range or a target is picked away.
 */
public final class FleeTask implements GoalTask {
	private final GoalSpec spec = new GoalSpec(GoalType.FLEE, java.util.Map.of(), "reflex");
	private int safeTicks;

	@Override public void start(CompanionEntity e) {
		Vec3 away = DefaultRandomPos.getPosAway(e, 12, 7, nearestHostile(e));
		if (away != null) {
			e.navigator().goTo(net.minecraft.core.BlockPos.containing(away), 1.5);
		}
	}

	@Override public TaskStatus tick(CompanionEntity e) {
		Vec3 hostile = nearestHostile(e);
		if (hostile == null) {
			safeTicks++;
			if (safeTicks > 40) return TaskStatus.DONE;
			e.navigator().stop();
			return TaskStatus.RUNNING;
		}
		safeTicks = 0;
		double d = e.position().distanceTo(hostile);
		if (d > 16) return TaskStatus.DONE;
		return TaskStatus.RUNNING;
	}

	@Override public void stop(CompanionEntity e) { e.navigator().stop(); }
	@Override public String progress() { return "FLEE"; }
	@Override public GoalSpec spec() { return spec; }

	private Vec3 nearestHostile(CompanionEntity e) {
		ServerLevel level = (ServerLevel) e.level();
		java.util.Optional<net.minecraft.world.entity.monster.Monster> nearest = level.getEntitiesOfClass(
				net.minecraft.world.entity.monster.Monster.class,
				e.getBoundingBox().inflate(24),
				m -> m.isAlive() && !m.isRemoved())
			.stream().min(java.util.Comparator.comparingDouble(m -> e.position().distanceToSqr(m.position())));
		return nearest.map(net.minecraft.world.entity.Entity::position).orElse(null);
	}
}