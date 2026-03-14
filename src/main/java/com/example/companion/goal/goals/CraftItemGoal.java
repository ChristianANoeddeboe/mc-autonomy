package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;

import java.util.Map;

/**
 * CRAFT_ITEM — craft a specific item from materials in the companion's inventory.
 *
 * <p>Complete when: the target item appears in inventory.
 * <p>Fails when: required materials are missing.
 *
 * <p>TODO (Phase 4): recipe lookup, crafting-table navigation, inventory crafting.
 */
public class CraftItemGoal implements CompanionGoal {

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;
    private String targetItem = "wooden_pickaxe";

    public CraftItemGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false;
        targetItem = (String) params.getOrDefault("item", "wooden_pickaxe");
        CompanionMod.LOGGER.info("CraftItemGoal started — item={}", targetItem);
    }

    @Override public void tick() { /* TODO */ }
    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Missing materials to craft " + targetItem; }
    @Override public void reset() { failed = false; complete = false; }
}
