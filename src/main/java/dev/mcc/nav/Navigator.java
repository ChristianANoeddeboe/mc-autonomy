package dev.mcc.nav;

import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.Vec3;

/**
 * Wraps the entity's PathNavigation with a deterministic state machine:
 * MOVING / ARRIVED / STUCK / NO_PATH. Fallback teleport when navigation is
 * impossible, per config. The GoalExecutor drives this; the FSM/LLM never touch
 * PathNavigation directly.
 */
public final class Navigator {
	public enum NavStatus { MOVING, ARRIVED, STUCK, NO_PATH }

	private final CompanionEntity entity;
	private final PathNavigation nav;
	private final CompanionConfig.NavGroup cfg;

	private BlockPos target;
	private double reachDist;
	private int stuckTicks;
	private int repaths;
	private int ageTicks;
	private BlockPos lastPos;
	private int noProgressTicks;

	public Navigator(CompanionEntity entity) {
		this.entity = entity;
		this.nav = entity.getNavigation();
		this.cfg = CompanionConfig.get().nav;
	}

	public void goTo(BlockPos target, double reachDist) {
		this.target = target;
		this.reachDist = reachDist;
		this.stuckTicks = 0;
		this.repaths = 0;
		this.ageTicks = 0;
		this.noProgressTicks = 0;
		this.lastPos = entity.blockPosition();
		nav.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed());
	}

	public void follow(Entity target) {
		this.target = target.blockPosition();
		this.reachDist = cfg.followMinDist;
		this.ageTicks = 0;
		this.stuckTicks = 0;
		nav.moveTo(target, speed());
	}

	public void stop() {
		nav.stop();
	}

	public NavStatus tick() {
		ageTicks++;
		if (target == null) return NavStatus.ARRIVED;

		// reached? compute squared distance manually (BlockPos has no distSqr in Mojang)
		BlockPos here = entity.blockPosition();
		if (distSqr(here, target) <= reachDist * reachDist) {
			nav.stop();
			return NavStatus.ARRIVED;
		}

		// timeout
		if (ageTicks > cfg.goalTimeoutTicks) {
			return teleportFallback();
		}

		// stuck / no progress
		if (here.equals(lastPos)) {
			noProgressTicks++;
		} else {
			noProgressTicks = 0;
			lastPos = here;
		}
		if (noProgressTicks >= cfg.stuckTicks) {
			nav.stop();
			stuckTicks++;
			if (stuckTicks > cfg.maxRepaths) {
				return teleportFallback();
			}
			// jittered repath
			BlockPos jittered = target.offset(rnd(1), 0, rnd(1));
			nav.moveTo(jittered.getX() + 0.5, jittered.getY(), jittered.getZ() + 0.5, speed());
			noProgressTicks = 0;
			return NavStatus.STUCK;
		}

		return nav.isDone() ? NavStatus.MOVING : NavStatus.MOVING;
	}

	private double speed() { return CompanionConfig.get().entity.moveSpeed; }

	private int rnd(int bound) {
		return entity.getRandom().nextInt(2 * bound + 1) - bound;
	}

	private static long distSqr(BlockPos a, BlockPos b) {
		long dx = a.getX() - b.getX();
		long dy = a.getY() - b.getY();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	private NavStatus teleportFallback() {
		nav.stop();
		if (cfg.allowTeleport && this.pos().distanceTo(center(target)) <= cfg.teleportMaxDist) {
			entity.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
			dev.mcc.MccMod.LOGGER.info("[MCC][nav] teleport-fallback to {}", target);
			return NavStatus.ARRIVED;
		}
		return NavStatus.NO_PATH;
	}

	private Vec3 center(BlockPos p) {
		return new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
	}

	private Vec3 pos() { return entity.position(); }
}