package dev.mcc.goal.tasks;

import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import dev.mcc.goal.GoalSpec;
import dev.mcc.goal.GoalTask;
import dev.mcc.goal.TaskStatus;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;

/**
 * Shelter: find a bed within range and sleep until day; else wait at a high
 * light spot. Never fails hard — WAIT fallback.
 */
public final class ShelterGoal implements GoalTask {
	private final GoalSpec spec;
	private BlockPos bed;
	private boolean declared;

	public ShelterGoal(GoalSpec spec, CompanionEntity entity) {
		this.spec = spec;
	}

	@Override public void start(CompanionEntity e) {}

	@Override public TaskStatus tick(CompanionEntity e) {
		Level level = e.level();
		CompanionConfig.SleepGroup s = CompanionConfig.get().sleep;
		// 1.21.11 has no Level.isNight(); derive from day time against config.
		long dayTime = level.getDayTime() % 24000;
		boolean night = dayTime < s.nightStart || dayTime > s.nightEnd;

		if (!night) {
			dev.mcc.MccMod.LOGGER.info("[MCC][shelter] day reached — DONE");
			return TaskStatus.DONE;
		}

		if (bed == null) {
			Optional<BlockPos> found = findBed(level, e.blockPosition(), s.bedRadius);
			if (found.isPresent()) {
				bed = found.get();
			}
		}
		if (bed != null) {
			if (distTo(e, bed) > 4) {
				e.navigator().goTo(bed, 1.0);
			} else if (!declared) {
				e.startSleeping(bed);
				declared = true;
				dev.mcc.MccMod.LOGGER.info("[MCC][shelter] sleeping at {}", bed);
			}
		} else if (!declared) {
			e.navigator().stop();
			declared = true;
			dev.mcc.MccMod.LOGGER.info("[MCC][shelter] no bed — waiting out the night");
		}
		return TaskStatus.RUNNING;
	}

	private Optional<BlockPos> findBed(Level level, BlockPos center, double radius) {
		int r = (int) Math.ceil(radius);
		for (int dx = -r; dx <= r; dx++)
			for (int dy = -3; dy <= 3; dy++)
				for (int dz = -r; dz <= r; dz++) {
					BlockPos p = center.offset(dx, dy, dz);
					if (level.getBlockState(p).is(BlockTags.BEDS)) return Optional.of(p);
				}
		return Optional.empty();
	}

	@Override public void stop(CompanionEntity e) { e.navigator().stop(); }
	@Override public String progress() { return "SHELTER bed=" + bed; }
	@Override public GoalSpec spec() { return spec; }

	private double distTo(CompanionEntity e, BlockPos p) {
		return e.position().distanceTo(new net.minecraft.world.phys.Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
	}
}