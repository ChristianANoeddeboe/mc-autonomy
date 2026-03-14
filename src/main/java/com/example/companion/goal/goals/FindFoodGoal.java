package com.example.companion.goal.goals;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.CompanionGoal;

import java.util.Map;

/**
 * FIND_FOOD — obtain food items or restore hunger by eating.
 *
 * <p>Complete when: food item obtained OR hunger is restored.
 * <p>Fails when: no food source found after {@code SEARCH_TIMEOUT_TICKS}.
 *
 * <p>TODO (Phase 4): scan for crops/animals, navigate, harvest, eat.
 */
public class FindFoodGoal implements CompanionGoal {

    private static final int SEARCH_TIMEOUT_TICKS = 20 * 60;

    private final CompanionEntity entity;
    private int ticksElapsed = 0;
    private boolean failed = false;
    private boolean complete = false;

    public FindFoodGoal(CompanionEntity entity) {
        this.entity = entity;
    }

    @Override
    public void start(Map<String, Object> params) {
        ticksElapsed = 0; failed = false; complete = false;
        CompanionMod.LOGGER.info("FindFoodGoal started");
    }

    @Override
    public void tick() {
        ticksElapsed++;
        // TODO: locate food source, navigate, collect, eat if hungry
        if (ticksElapsed >= SEARCH_TIMEOUT_TICKS) failed = true;
    }

    @Override public boolean isComplete()   { return complete; }
    @Override public boolean isFailed()     { return failed; }
    @Override public String failureReason() { return "No food source found"; }
    @Override public void reset() { ticksElapsed = 0; failed = false; complete = false; }
}
