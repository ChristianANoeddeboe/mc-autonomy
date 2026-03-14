package com.example.companion.goal;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.goal.goals.*;

import java.util.EnumMap;
import java.util.Map;

/**
 * Owns the currently-active {@link CompanionGoal} and ticks it every game tick.
 * When a goal completes or fails, it notifies {@link CompanionEntity} so the
 * {@link AIGoalPlanner} can request a new decision from the sidecar.
 */
public class GoalExecutor {

    private final CompanionEntity entity;
    private final Map<GoalType, CompanionGoal> goalRegistry = new EnumMap<>(GoalType.class);

    private GoalType activeType = GoalType.IDLE;
    private CompanionGoal activeGoal;
    private boolean paused = false;

    public GoalExecutor(CompanionEntity entity) {
        this.entity = entity;
        registerGoals();
        activeGoal = goalRegistry.get(GoalType.IDLE);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void setGoal(GoalType type, Map<String, Object> params) {
        if (activeGoal != null) {
            activeGoal.reset();
        }
        activeType = type;
        activeGoal = goalRegistry.getOrDefault(type, goalRegistry.get(GoalType.IDLE));
        paused = false;
        CompanionMod.LOGGER.info("GoalExecutor: starting goal {}", type);
        activeGoal.start(params);
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    public GoalType getActiveType() {
        return activeType;
    }

    /** Called every game tick by {@link CompanionEntity}. */
    public void tick() {
        if (paused || activeGoal == null) return;

        activeGoal.tick();

        if (activeGoal.isComplete()) {
            CompanionMod.LOGGER.info("GoalExecutor: goal {} complete", activeType);
            entity.reportGoalComplete();
        } else if (activeGoal.isFailed()) {
            CompanionMod.LOGGER.warn("GoalExecutor: goal {} failed — {}", activeType, activeGoal.failureReason());
            entity.reportGoalFailed(activeGoal.failureReason());
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void registerGoals() {
        goalRegistry.put(GoalType.IDLE,          new IdleGoal(entity));
        goalRegistry.put(GoalType.GATHER_WOOD,   new GatherWoodGoal(entity));
        goalRegistry.put(GoalType.FIND_FOOD,     new FindFoodGoal(entity));
        goalRegistry.put(GoalType.BUILD_SHELTER, new BuildShelterGoal(entity));
        goalRegistry.put(GoalType.MINE_RESOURCES,new MineResourcesGoal(entity));
        goalRegistry.put(GoalType.EXPLORE,       new ExploreGoal(entity));
        goalRegistry.put(GoalType.FOLLOW_PLAYER, new FollowPlayerGoal(entity));
        goalRegistry.put(GoalType.CRAFT_ITEM,    new CraftItemGoal(entity));
        goalRegistry.put(GoalType.SLEEP,         new SleepGoal(entity));
    }
}
