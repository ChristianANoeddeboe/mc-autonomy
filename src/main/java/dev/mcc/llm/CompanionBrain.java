package dev.mcc.llm;

import dev.mcc.entity.CompanionEntity;

/**
 * Phase 2 stub — full planner arrives in Phase 6. Holds the entity reference
 * and exposes the state so status can report llm state. tick() is a no-op that
 * lets the deterministic layer run independently.
 */
public final class CompanionBrain {
	public enum LlmState { CONNECTED, DEGRADED, OFFLINE, MOCK, OFF }

	private final CompanionEntity entity;
	private LlmState state = LlmState.OFF;

	public CompanionBrain(CompanionEntity entity) {
		this.entity = entity;
	}

	public LlmState state() { return state; }
	public void setState(LlmState s) { this.state = s; }

	public void tick(CompanionEntity e) {
		// Phase 6: drain async results, schedule requests.
	}

	public CompanionEntity entity() { return entity; }
}