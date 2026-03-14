package com.example.companion.entity;

import com.example.companion.CompanionMod;
import com.example.companion.behavior.ReactiveBehaviorController;
import com.example.companion.goal.AIGoalPlanner;
import com.example.companion.goal.GoalExecutor;
import com.example.companion.goal.GoalType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The companion entity.
 *
 * <p>Extends {@link PathAwareEntity} so vanilla pathfinding works out of the box.
 *
 * <p>Tick order each server tick:
 * <pre>
 *   ReactiveBehaviorController  — always-on, can pause the goal
 *   GoalExecutor                — drives the active CompanionGoal FSM
 *   AIGoalPlanner               — queues / applies goals from the sidecar
 * </pre>
 */
public class CompanionEntity extends PathAwareEntity {

    /** 36-slot inventory (matches a standard player hotbar + inventory). */
    private static final int INVENTORY_SIZE = 36;

    /** Hunger drains 1 point every this many ticks (80 ticks = 4 s). */
    private static final int HUNGER_DRAIN_INTERVAL = 80;

    private final ReactiveBehaviorController reactiveBehaviors;
    private final GoalExecutor goalExecutor;
    private final AIGoalPlanner goalPlanner;
    private final ActionExecutor actions;

    private final SimpleInventory inventory = new SimpleInventory(INVENTORY_SIZE);

    /** Hunger level 0-20 (mirrors the player hunger mechanic). */
    private int hunger = 20;
    private int hungerDrainTick = 0;

    private String lastPlayerMessage = null;
    private String goalFailedReason = null;

    public CompanionEntity(EntityType<? extends CompanionEntity> entityType, World world) {
        super(entityType, world);
        this.reactiveBehaviors = new ReactiveBehaviorController(this);
        this.goalExecutor      = new GoalExecutor(this);
        this.goalPlanner       = new AIGoalPlanner(this);
        this.actions           = new ActionExecutor(this);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        tickHunger();

        reactiveBehaviors.tick();
        goalExecutor.tick();
        goalPlanner.tick();
    }

    private void tickHunger() {
        if (++hungerDrainTick >= HUNGER_DRAIN_INTERVAL) {
            hungerDrainTick = 0;
            if (hunger > 0) hunger--;
        }
    }

    // ------------------------------------------------------------------
    // Goal management
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
        CompanionMod.LOGGER.info("CompanionEntity: message from {}: {}", playerName, message);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public GoalType getActiveGoalType()  { return goalExecutor.getActiveType(); }
    public String   getLastPlayerMessage(){ return lastPlayerMessage; }
    public String   getGoalFailedReason() { return goalFailedReason; }
    public ActionExecutor getActions()    { return actions; }
    public GoalExecutor   getGoalExecutor(){ return goalExecutor; }

    public int  getHunger()         { return hunger; }
    public void setHunger(int value){ hunger = Math.max(0, Math.min(20, value)); }

    public SimpleInventory getCarriedInventory() { return inventory; }

    /** Convenience: return all non-empty stacks as a list. */
    public List<ItemStack> getNonEmptyStacks() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack s = inventory.getStack(i);
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    /** Restore hunger (e.g. after eating). Clamped to 0-20. */
    public void restoreHunger(int amount) {
        setHunger(hunger + amount);
    }
}
