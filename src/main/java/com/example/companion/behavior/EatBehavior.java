package com.example.companion.behavior;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;

/**
 * Reactive behavior: consume food from inventory when hunger is low.
 *
 * <p>Trigger: hunger ≤ {@value HUNGER_THRESHOLD} AND food item in inventory.
 * <p>Action: eat the highest-saturation food available.
 *
 * <p>TODO (Phase 2): track hunger stat; search inventory for food items.
 */
public class EatBehavior {

    private static final int HUNGER_THRESHOLD = 8;

    private final CompanionEntity entity;

    public EatBehavior(CompanionEntity entity) {
        this.entity = entity;
    }

    /** @return true if this behavior is active */
    public boolean tick() {
        // TODO: check entity hunger and inventory for food items
        return false;
    }
}
