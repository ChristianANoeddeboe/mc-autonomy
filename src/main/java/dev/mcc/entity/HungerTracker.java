package dev.mcc.entity;

import dev.mcc.config.CompanionConfig;

/**
 * Pure hunger model, owned by a CompanionEntity. No MC dependencies beyond
 * config — fully unit-testable cold. Decay, starve and heal ticks are computed
 * here; the caller (CompanionTicker) applies the health/inventory effects.
 */
public final class HungerTracker {
	public enum TickResult { NONE, WANT_EAT, STARVE_DAMAGE, HEAL }

	private final CompanionConfig.HungerGroup cfg;
	private int value;
	private int decayTickCounter;
	private int starveTickCounter;
	private int healTickCounter;

	public HungerTracker() {
		this.cfg = CompanionConfig.get().hunger;
		this.value = cfg.max;
	}

	public int get() { return value; }
	public void set(int v) { value = Math.max(0, Math.min(cfg.max, v)); }

	public void feed(int nutrition) {
		value = Math.max(0, Math.min(cfg.max, value + nutrition));
	}

	/**
	 * Advance one server tick.
	 * @param hasFood whether the entity has edible inventory — passed in so this
	 *                class stays inventory-free.
	 */
	public TickResult tick(long tick, boolean hasFood) {
		TickResult result = TickResult.NONE;

		// decay
		decayTickCounter++;
		if (decayTickCounter >= cfg.decayIntervalTicks) {
			decayTickCounter = 0;
			set(value - 1);
		}

		// starve damage
		if (value <= 0) {
			starveTickCounter++;
			if (starveTickCounter >= cfg.starveDamageIntervalTicks) {
				starveTickCounter = 0;
				result = TickResult.STARVE_DAMAGE;
			}
			return result;
		}

		// want-to-eat signal
		if (value < cfg.eatBelow && hasFood) {
			result = TickResult.WANT_EAT;
		}

		// heal when well fed
		if (value > cfg.healAboveHunger) {
			healTickCounter++;
			if (healTickCounter >= cfg.healIntervalTicks) {
				healTickCounter = 0;
				result = TickResult.HEAL;
			}
		} else {
			healTickCounter = 0;
		}
		return result;
	}
}