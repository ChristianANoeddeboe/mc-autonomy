package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;

import java.util.Map;

/**
 * MINE_RESOURCES — dig to find and collect a target ore/resource.
 *
 * <p>Complete when: target resource collected.
 * <p>Fails when: stuck, lost, or no ore found after extended search.
 *
 * <p>TODO (Phase 4): cave navigation, ore detection, staircase digging.
 */
public class MineResourcesGoal implements CompanionGoal {

    private final CompanionEntity entity;
    private boolean failed = false;
    private boolean complete = false;
    private String target = "iron_ore";

    public MineResourcesGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        failed = false; complete = false;
        target = (String) params.getOrDefault("target", "iron_ore");
        CompanionMod.LOGGER.info("MineResourcesGoal started — target={}", target);
    }

    @Override public void tick() { /* TODO */ }
    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "Could not find " + target; }
    @Override public void reset() { failed = false; complete = false; }
}
