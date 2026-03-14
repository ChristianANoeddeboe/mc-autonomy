package com.example.companion.entity;

import com.example.companion.CompanionMod;
import com.example.companion.behavior.ReactiveBehaviorController;
import com.example.companion.goal.AIGoalPlanner;
import com.example.companion.goal.GoalExecutor;
import com.example.companion.goal.GoalType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

import java.util.Map;

/**
 * The companion entity.
 *
 * <p>Extends {@link PathAwareEntity} so vanilla pathfinding works out of the box.
 *
 * <p>Architecture:
 * <pre>
 *   tick()
 *     ├── ReactiveBehaviorController  (always-on, overrides goal if needed)
 *     ├── GoalExecutor                (drives current CompanionGoal FSM)
 *     └── AIGoalPlanner               (queues / applies new goals from sidecar)
 * </pre>
 */
public class CompanionEntity extends PathAwareEntity {

    private final ReactiveBehaviorController reactiveBehaviors;
    private final GoalExecutor goalExecutor;
    private final AIGoalPlanner goalPlanner;
    private final ActionExecutor actions;

    private String lastPlayerMessage = null;
    private String goalFailedReason = null;

    public CompanionEntity(EntityType<? extends CompanionEntity> entityType, World world) {
        super(entityType, world);
        this.reactiveBehaviors = new ReactiveBehaviorController(this);
        this.goalExecutor = new GoalExecutor(this);
        this.goalPlanner = new AIGoalPlanner(this);
        this.actions = new ActionExecutor(this);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        reactiveBehaviors.tick();
        goalExecutor.tick();
        goalPlanner.tick();
    }

    // ------------------------------------------------------------------
    // Goal management (called by GoalExecutor / AIGoalPlanner)
    // ------------------------------------------------------------------

    public void setGoal(GoalType type, Map<String, Object> params) {
        goalFailedReason = null;
        goalExecutor.setGoal(type, params);
        CompanionMod.LOGGER.info("CompanionEntity: goal set to {}", type);
    }

    public void reportGoalComplete() {
        goalPlanner.onGoalComplete();
    }

    public void reportGoalFailed(String reason) {
        goalFailedReason = reason;
        goalPlanner.onGoalFailed(reason);
    }

    // ------------------------------------------------------------------
    // Chat integration
    // ------------------------------------------------------------------

    public void onPlayerMessage(String playerName, String message) {
        lastPlayerMessage = message;
        goalPlanner.onPlayerMessage(message);
        CompanionMod.LOGGER.info("CompanionEntity: received message from {}: {}", playerName, message);
    }

    // ------------------------------------------------------------------
    // Accessors used by WorldPerception / AIGoalPlanner
    // ------------------------------------------------------------------

    public GoalType getActiveGoalType() {
        return goalExecutor.getActiveType();
    }

    public String getLastPlayerMessage() {
        return lastPlayerMessage;
    }

    public String getGoalFailedReason() {
        return goalFailedReason;
    }

    public ActionExecutor getActions() {
        return actions;
    }
}
