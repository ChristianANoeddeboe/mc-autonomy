package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;

/**
 * Reactive behavior: sprint away from threats when health is critically low.
 *
 * <p>Trigger: health ≤ {@value HEALTH_THRESHOLD} (4 hearts = 8 HP).
 * <p>Action: sprint in the direction away from the nearest hostile entity or general retreat.
 *
 * <p>TODO (Phase 2): implement flee pathfinding direction.
 */
public class FleeBehavior {

    private static final float HEALTH_THRESHOLD = 8.0f;

    private final CompanionEntity entity;

    public FleeBehavior(CompanionEntity entity) {
        this.entity = entity;
    }

    /** @return true if this behavior is active */
    public boolean tick() {
        if (entity.getHealth() > HEALTH_THRESHOLD) return false;

        CompanionMod.LOGGER.debug("FleeBehavior: health critical ({} HP) — fleeing", entity.getHealth());
        entity.setSprinting(true);
        // TODO: navigate away from threat
        return true;
    }
}
