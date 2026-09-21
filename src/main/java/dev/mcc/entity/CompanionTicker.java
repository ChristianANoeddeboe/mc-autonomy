package dev.mcc.entity;

import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.HungerTracker.TickResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Runs every server tick (from CompanionEntity.tick). Order:
 * 1) hunger (apply starvation/heal/eat), 2) executor goals, 3) ongoing mode
 * behaviour, 4) brain drain. ItemPickup and SurvivalReflex land in later phases.
 */
public final class CompanionTicker {
	private CompanionTicker() {}

	public static void tick(CompanionEntity e) {
		CompanionConfig cfg = CompanionConfig.get();
		if (!e.isAlive() || e.level().isClientSide()) return;

		if (e.tickCount % 100 == 0) {
			dev.mcc.MccMod.LOGGER.info("[MCC][alive] t={} pos={} hp={} alive={} removed={}",
				e.tickCount, e.blockPosition(), e.getHealth(), e.isAlive(), e.isRemoved());
		}

		ServerLevel level = (ServerLevel) e.level();

		// 1) hunger
		boolean hasFood = e.inventory().bestFood().isPresent();
		TickResult h = e.hunger().tick(e.tickCount, hasFood);
		switch (h) {
			case STARVE_DAMAGE -> e.hurtServer(level, e.damageSources().starve(), (float) cfg.hunger.starveDamage);
			case HEAL -> e.heal(1f);
			case WANT_EAT -> eat(e);
			default -> {}
		}

		// 2) executor
		e.executor().tick();

		// 3) ongoing mode behaviour (FOLLOW/STAY/GUARD drift when no goal running)
		modeBehaviour(e);

		// 4) brain
		e.brain().tick(e);
	}

	private static void eat(CompanionEntity e) {
		e.inventory().bestFood().ifPresent(food -> {
			FoodProperties fp = food.get(DataComponents.FOOD);
			int nutrition = fp != null ? fp.nutrition() : 1;
			e.inventory().remove(food.getItem(), 1);
			e.hunger().feed(nutrition);
			e.heal(nutrition * 0.1f);
			dev.mcc.MccMod.LOGGER.info("[MCC][eat] {} hunger={}", food.getItem(), e.hunger().get());
		});
	}

	private static void modeBehaviour(CompanionEntity e) {
		// Only drift when no explicit goal is running.
		if (e.executor().current().isPresent() || !e.executor().queue().isEmpty()) return;
		CompanionConfig.NavGroup n = CompanionConfig.get().nav;
		BehaviourMode mode = e.mode();

		if (mode == BehaviourMode.FOLLOW) {
			e.owner().flatMap(o -> o.player()).ifPresent(p -> {
				double d = e.position().distanceTo(p.position());
				if (d > n.followMaxDist) {
					e.navigator().follow(p);
				} else if (d < n.followMinDist) {
					e.navigator().stop();
				}
			});
		} else if ((mode == BehaviourMode.STAY || mode == BehaviourMode.GUARD) && e.anchor().isPresent()) {
			BlockPos anchor = e.anchor().get();
			double d = e.position().distanceTo(new Vec3(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5));
			if (d > 2.0) {
				e.navigator().goTo(anchor, 1.0);
			} else {
				e.navigator().stop();
			}
		}
	}
}