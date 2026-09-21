package dev.mcc.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import dev.mcc.config.CompanionConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 gate: defaults serialize and round-trip cleanly; out-of-range values
 * clamp to their documented bounds. Pure POJO — no game boot (CompanionConfig.load()
 * requires FabricLoader, which is not available in a cold JUnit run).
 */
class CompanionConfigTest {

	private static Gson gson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}

	@Test
	void defaultsRoundTrip() {
		CompanionConfig a = new CompanionConfig();
		String json = gson().toJson(a);
		CompanionConfig b = gson().fromJson(json, CompanionConfig.class);

		assertEquals(a.entity.maxHealth, b.entity.maxHealth, 1e-6);
		assertEquals(a.entity.moveSpeed, b.entity.moveSpeed, 1e-6);
		assertEquals(a.hunger.max, b.hunger.max);
		assertEquals(a.hunger.decayIntervalTicks, b.hunger.decayIntervalTicks);
		assertEquals(a.nav.allowTeleport, b.nav.allowTeleport);
		assertEquals(a.mine.speedMultiplier, b.mine.speedMultiplier, 1e-6);
		assertEquals(a.combat.avoidTypes.length, b.combat.avoidTypes.length);
		assertEquals(a.combat.avoidTypes[0], b.combat.avoidTypes[0]);
	}

	/** Absent fields in a JSON file pick up defaults; no NPE. */
	@Test
	void emptyJsonYieldsDefaults() {
		CompanionConfig c = gson().fromJson("{}", CompanionConfig.class);
		assertEquals(20, c.entity.maxHealth);
		assertEquals("http", c.llm.mode);
		assertEquals(36, c.items.inventorySize);
	}

	@Test
	void clampsOutOfRange() {
		CompanionConfig c = gson().fromJson("""
			{
			  "entity": {"maxHealth": 5000, "moveSpeed": 99.0, "followRange": 3, "maxFallDistance": -4},
			  "hunger": {"max": -5, "eatBelow": 999, "starveDamage": 500},
			  "nav": {"reachDist": -2, "followMaxDist": 1, "leashDist": 0.5},
			  "mine": {"scanRadius": 999, "reachDist": 100, "minBreakTicks": -3, "maxBreakTicks": 2},
			  "items": {"inventorySize": 9999, "pickupRadius": -1},
			  "combat": {"fleeHealthBelow": 200, "disengageHealthFraction": 0.9, "maxHostilesToFight": -2},
			  "llm": {"mode": "bogus", "timeoutMs": -5, "maxPlanLength": 50, "backoffMaxMs": 10},
			  "chat": {"prefixes": [], "name": ""}
			}
			""", CompanionConfig.class);
		c.clamp();

		// entity
		assertEquals(100, c.entity.maxHealth);
		assertEquals(1.0, c.entity.moveSpeed);
		assertEquals(16, c.entity.followRange);
		assertEquals(1, c.entity.maxFallDistance);

		// hunger
		assertEquals(1, c.hunger.max);
		assertTrue(c.hunger.eatBelow <= c.hunger.max);
		assertEquals(20, c.hunger.starveDamage);

		// nav
		assertEquals(0.5, c.nav.reachDist);
		assertTrue(c.nav.followMaxDist >= c.nav.followMinDist);
		assertEquals(c.nav.followMaxDist, c.nav.leashDist);

		// mine
		assertEquals(64, c.mine.scanRadius);
		assertEquals(16, c.mine.reachDist);
		assertTrue(c.mine.maxBreakTicks >= c.mine.minBreakTicks);

		// items
		assertEquals(128, c.items.inventorySize);
		assertEquals(0.5, c.items.pickupRadius);

		// combat
		assertEquals(100, c.combat.fleeHealthBelow);
		assertTrue(c.combat.disengageHealthFraction <= c.combat.minHealthFractionToFight);
		assertEquals(1, c.combat.maxHostilesToFight);

		// llm
		assertEquals("http", c.llm.mode);
		assertEquals(250, c.llm.timeoutMs);
		assertEquals(10, c.llm.maxPlanLength);
		assertTrue(c.llm.backoffMaxMs >= c.llm.backoffBaseMs);

		// chat
		assertEquals(2, c.chat.prefixes.length);
		assertEquals("Companion", c.chat.name);
	}
}