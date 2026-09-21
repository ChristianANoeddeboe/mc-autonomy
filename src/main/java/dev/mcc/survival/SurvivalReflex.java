package dev.mcc.survival;

/**
 * Pure survival decision — no MC dependency beyond a plain snapshot, so it's
 * unit-testable cold. Produces the highest-priority reflex action or none.
 */
public final class SurvivalReflex {

	public enum ReflexAction { FLEE, EAT, EXTINGUISH, FIGHT }

	/** Immutable world snapshot the evaluator needs. */
	public record Snapshot(
		double health, double maxHealth,
		int hunger, boolean hasFood,
		double nearestHostileDist, int hostileCount,
		boolean isNight, boolean onFire, boolean inWater,
		long lastHurtAge,
		// combat
		double fightHealthAbove, boolean inGuardMode,
		double threatRadius, double fleeRadius, double fleeHealthBelow) {

		public static Builder builder() { return new Builder(); }

		public static final class Builder {
			double health, maxHealth = 20;
			int hunger = 20; boolean hasFood;
			double nearestHostileDist = Double.MAX_VALUE; int hostileCount;
			boolean isNight, onFire, inWater;
			long lastHurtAge = Long.MAX_VALUE;
			double fightHealthAbove = 14, threatRadius = 12, fleeRadius = 8, fleeHealthBelow = 10;
			boolean inGuardMode;

			public Builder health(double v) { this.health = v; return this; }
			public Builder maxHealth(double v) { this.maxHealth = v; return this; }
			public Builder hunger(int v) { this.hunger = v; return this; }
			public Builder hasFood(boolean v) { this.hasFood = v; return this; }
			public Builder nearestHostileDist(double v) { this.nearestHostileDist = v; return this; }
			public Builder hostileCount(int v) { this.hostileCount = v; return this; }
			public Builder isNight(boolean v) { this.isNight = v; return this; }
			public Builder onFire(boolean v) { this.onFire = v; return this; }
			public Builder inWater(boolean v) { this.inWater = v; return this; }
			public Builder lastHurtAge(long v) { this.lastHurtAge = v; return this; }
			public Builder fightHealthAbove(double v) { this.fightHealthAbove = v; return this; }
			public Builder inGuardMode(boolean v) { this.inGuardMode = v; return this; }
			public Builder threatRadius(double v) { this.threatRadius = v; return this; }
			public Builder fleeRadius(double v) { this.fleeRadius = v; return this; }
			public Builder fleeHealthBelow(double v) { this.fleeHealthBelow = v; return this; }
			public Snapshot build() { return new Snapshot(health, maxHealth, hunger, hasFood, nearestHostileDist,
				hostileCount, isNight, onFire, inWater, lastHurtAge, fightHealthAbove, inGuardMode, threatRadius, fleeRadius, fleeHealthBelow); }
		}
	}

	private SurvivalReflex() {}

	/**
	 * Reflex priority: FLEE (danger) > EXTINGUISH (fire) > EAT (hungry+food) >
	 * FIGHT (defensive, guard mode) > none.
	 */
	public static java.util.Optional<ReflexAction> evaluate(Snapshot s) {
		// fire is always an emergency
		if (s.onFire()) return java.util.Optional.of(ReflexAction.EXTINGUISH);

		// fleeing: low HP + hostile close, OR overwhelmed by numbers
		boolean threatened = s.nearestHostileDist() <= s.fleeRadius() || s.hostileCount() >= 3;
		boolean lowHp = s.health() < s.fleeHealthBelow();
		if (threatened && (lowHp || s.hostileCount() >= 3)) {
			return java.util.Optional.of(ReflexAction.FLEE);
		}

		// FIGHT defensively: in guard mode and a hostile is inside threat radius
		if (s.inGuardMode() && s.nearestHostileDist() <= s.threatRadius() && s.health() >= s.fightHealthAbove()) {
			return java.util.Optional.of(ReflexAction.FIGHT);
		}

		// eat when hungry and food is on hand
		if (s.hunger() < 12 && s.hasFood()) {
			return java.util.Optional.of(ReflexAction.EAT);
		}

		return java.util.Optional.empty();
	}
}