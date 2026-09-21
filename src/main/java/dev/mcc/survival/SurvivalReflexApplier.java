package dev.mcc.survival;

import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import dev.mcc.entity.BehaviourMode;
import dev.mcc.goal.tasks.EatTask;
import dev.mcc.goal.tasks.FleeTask;
import dev.mcc.survival.SurvivalReflex.ReflexAction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;

/**
 * Reflex applier: builds the world snapshot, evaluates the reflex, and when a
 * reflex fires, preempts the executor (urgent goal runs then the interrupted
 * task resumes). Always outranks LLM goals; never calls the Brain.
 */
public final class SurvivalReflexApplier {
	private SurvivalReflexApplier() {}

	public static void tick(CompanionEntity e) {
		CompanionConfig.CombatGroup c = CompanionConfig.get().combat;
		ServerLevel level = (ServerLevel) e.level();

		// gather hostiles
		java.util.List<Monster> hostiles = level.getEntitiesOfClass(
			Monster.class, e.getBoundingBox().inflate(c.fleeRadius),
			m -> m.isAlive() && !m.isRemoved());
		double nearest = hostiles.stream()
			.mapToDouble(m -> e.position().distanceTo(m.position()))
			.min().orElse(Double.MAX_VALUE);

		SurvivalReflex.Snapshot snap = SurvivalReflex.Snapshot.builder()
			.health(e.getHealth())
			.maxHealth(e.getMaxHealth())
			.hunger(e.hunger().get())
			.hasFood(e.inventory().bestFood().isPresent())
			.nearestHostileDist(nearest)
			.hostileCount(hostiles.size())
			.onFire(e.isOnFire())
			.fightHealthAbove(c.fightHealthAbove)
			.fleeRadius(c.fleeRadius)
			.fleeHealthBelow(c.fleeHealthBelow)
			.threatRadius(c.threatRadius)
			.inGuardMode(e.mode() == BehaviourMode.GUARD)
			.lastHurtAge(e.tickCount - e.lastHurtTick())
			.build();

		SurvivalReflex.evaluate(snap).ifPresent(action -> apply(e, action));
	}

	private static void apply(CompanionEntity e, ReflexAction action) {
		boolean alreadyReflexing = e.executor().current()
			.map(t -> switch (t.spec().type()) {
				case FLEE, ATTACK -> action == ReflexAction.FLEE || action == ReflexAction.FIGHT;
				case EAT -> action == ReflexAction.EAT;
				default -> false;
			}).orElse(false);
		if (alreadyReflexing) return;

		switch (action) {
			case FLEE -> {
				dev.mcc.MccMod.LOGGER.info("[MCC][reflex] FLEE");
				e.executor().preempt(new FleeTask());
			}
			case EAT -> {
				dev.mcc.MccMod.LOGGER.info("[MCC][reflex] EAT");
				e.executor().preempt(new EatTask());
			}
			case EXTINGUISH -> {
				if (e.getHealth() <= 0) return;
				dev.mcc.MccMod.LOGGER.info("[MCC][reflex] EXTINGUISH fire");
			}
			case FIGHT -> {
				dev.mcc.MccMod.LOGGER.info("[MCC][reflex] FIGHT (guard)");
			}
		}
	}
}