package dev.mcc.entity;

import dev.mcc.config.CompanionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.mcc.entity.HungerTracker.TickResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure hunger model — no MC classes involved beyond config, so this runs cold.
 * Confirms decay cadence, want-to-eat signal, starve damage and heal ticks.
 */
class HungerTrackerTest {

	@BeforeEach
	void fastHunger() {
		// speed up decay for a deterministic test: decay every 20 ticks, eat below 12
		CompanionConfig cf = CompanionConfig.get();
		cf.hunger.decayIntervalTicks = 20;
		cf.hunger.eatBelow = 12;
		cf.hunger.starveDamageIntervalTicks = 10;
		cf.hunger.healAboveHunger = 17;
		cf.hunger.healIntervalTicks = 5;
	}

	@Test
	void decaysEveryInterval() {
		HungerTracker h = new HungerTracker();
		assertEquals(20, h.get());
		assertEquals(TickResult.NONE, h.tick(1, false));
		assertEquals(TickResult.NONE, h.tick(2, false));
		assertEquals(20, h.get());
		h.tick(1, false); // counter at 19 -> still none
		for (int i = 0; i < 18; i++) h.tick(1, false);
		assertEquals(19, h.get()); // decayed once at tick 20
	}

	@Test
	void wantsToEatWhenBelowThresholdAndHasFood() {
		HungerTracker h = new HungerTracker();
		h.set(10);
		assertEquals(TickResult.WANT_EAT, h.tick(1, true));
		// but no food available -> no signal
		h.set(10);
		assertEquals(TickResult.NONE, h.tick(1, false));
	}

	@Test
	void starvesWhenEmpty() {
		HungerTracker h = new HungerTracker();
		h.set(0);
		// 9 ticks below the 10-tick starve interval -> no damage yet
		for (int i = 0; i < 9; i++) {
			assertEquals(TickResult.NONE, h.tick(1, false));
		}
		// the 10th consecutive tick at 0 -> starve
		assertEquals(TickResult.STARVE_DAMAGE, h.tick(1, false));
	}

	@Test
	void healsWhenWellFed() {
		HungerTracker h = new HungerTracker();
		h.set(18); // > healAboveHunger
		for (int i = 0; i < 4; i++) {
			assertEquals(TickResult.NONE, h.tick(1, false));
		}
		assertEquals(TickResult.HEAL, h.tick(1, false));
	}

	@Test
	void feedIncreasesHungerClampedToMax() {
		HungerTracker h = new HungerTracker();
		h.feed(5);
		assertEquals(20, h.get());
		h.set(5);
		h.feed(3);
		assertEquals(8, h.get());
	}

	@Test
	void clampsBounds() {
		HungerTracker h = new HungerTracker();
		h.set(999);
		assertEquals(20, h.get());
		h.set(-3);
		assertEquals(0, h.get());
	}
}