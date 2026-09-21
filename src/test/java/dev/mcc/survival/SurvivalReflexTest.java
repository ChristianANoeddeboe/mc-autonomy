package dev.mcc.survival;

import dev.mcc.survival.SurvivalReflex.ReflexAction;
import dev.mcc.survival.SurvivalReflex.Snapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 5: pure reflex decision matrix — no game classes, runs cold. */
class SurvivalReflexTest {

	private static Snapshot base() {
		return Snapshot.builder().health(20).maxHealth(20).hunger(16).hasFood(false)
			.nearestHostileDist(Double.MAX_VALUE).hostileCount(0)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
	}

	private static Optional<ReflexAction> ev(Snapshot s) { return SurvivalReflex.evaluate(s); }

	@Test void noReflexWhenSafe() {
		assertTrue(ev(base()).isEmpty());
	}

	@Test void fleeWhenLowHpAndHostileClose() {
		Snapshot s = base();
		s = Snapshot.builder().health(6).hunger(16).nearestHostileDist(4).hostileCount(1)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
		assertEquals(ReflexAction.FLEE, ev(s).orElseThrow());
	}

	@Test void fleeWhenOverwhelmedByNumbers() {
		Snapshot s = base();
		s = Snapshot.builder().health(20).hunger(16).nearestHostileDist(6).hostileCount(4)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
		assertEquals(ReflexAction.FLEE, ev(s).orElseThrow());
	}

	@Test void noFleeWhenHealthyDespiteHostile() {
		Snapshot s = base();
		s = Snapshot.builder().health(20).hunger(16).nearestHostileDist(4).hostileCount(1)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
		assertNotEquals(ReflexAction.FLEE, ev(s).orElse(null));
	}

	@Test void extinguishFireIsPriority() {
		Snapshot s = base();
		s = Snapshot.builder().health(5).hunger(16).hasFood(true).onFire(true)
			.nearestHostileDist(3).hostileCount(2)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
		assertEquals(ReflexAction.EXTINGUISH, ev(s).orElseThrow());
	}

	@Test void eatWhenHungryAndFoodAvailable() {
		Snapshot s = base();
		s = Snapshot.builder().health(20).hunger(6).hasFood(true)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
		assertEquals(ReflexAction.EAT, ev(s).orElseThrow());
	}

	@Test void noEatWhenNotHungry() {
		Snapshot s = base();
		s = Snapshot.builder().health(20).hunger(16).hasFood(true)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
		assertNotEquals(ReflexAction.EAT, ev(s).orElse(null));
	}

	@Test void fightWhenGuardModeAndHostileInRange() {
		Snapshot s = base();
		s = Snapshot.builder().health(18).hunger(16).inGuardMode(true)
			.nearestHostileDist(9).hostileCount(1)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
		assertEquals(ReflexAction.FIGHT, ev(s).orElseThrow());
	}

	@Test void noFightWhenGuardButLowHp() {
		Snapshot s = base();
		s = Snapshot.builder().health(5).hunger(16).inGuardMode(true)
			.nearestHostileDist(5).hostileCount(1)
			.fleeRadius(8).fleeHealthBelow(10).fightHealthAbove(14).threatRadius(12).build();
		assertEquals(ReflexAction.FLEE, ev(s).orElseThrow()); // lowHP + in radius -> flee
	}
}