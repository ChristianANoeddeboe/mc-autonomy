package com.example.companion.entity;

import com.example.companion.CompanionMod;
import com.example.companion.behavior.ReactiveBehaviorController;
import com.example.companion.goal.AIGoalPlanner;
import com.example.companion.goal.GoalExecutor;
import com.example.companion.goal.GoalType;
import com.example.companion.util.ScanUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
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

    /** Radius for narrating goal changes to nearby players. */
    private static final double NARRATION_RADIUS = 32.0;

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
    private boolean initialRequestScheduled = false;

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

        // Fire the first sidecar request 1 second after spawn so the entity
        // picks a real goal immediately rather than waiting the 60 s fallback.
        if (!initialRequestScheduled) {
            goalPlanner.scheduleRequest(20);
            initialRequestScheduled = true;
        }

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
    // Item pickup — auto-collect dropped items when walking over them
    // ------------------------------------------------------------------

    @Override
    public boolean canPickUpLoot() {
        return true;
    }

    @Override
    protected void loot(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getStack().copy();
        if (ScanUtils.addToInventory(this, stack)) {
            itemEntity.discard();
            CompanionMod.LOGGER.debug("CompanionEntity picked up {}", ScanUtils.itemName(stack));
        }
    }

    // ------------------------------------------------------------------
    // Goal management
    // ------------------------------------------------------------------

    public void setGoal(GoalType type, Map<String, Object> params) {
        goalFailedReason = null;
        goalExecutor.setGoal(type, params);
        narrateGoal(type);
        CompanionMod.LOGGER.info("CompanionEntity: goal set to {}", type);
    }

    public void reportGoalComplete() {
        goalPlanner.onGoalComplete();
    }

    public void reportGoalFailed(String reason) {
        goalFailedReason = reason;
        goalPlanner.onGoalFailed(reason);
    }

    /**
     * Broadcast the new goal as a chat message to nearby players.
     * The message is prefixed with "[Companion]" to distinguish it from player chat.
     */
    private void narrateGoal(GoalType type) {
        if (!(getWorld() instanceof ServerWorld sw)) return;
        String readable = type.name().replace('_', ' ').toLowerCase();
        Text msg = Text.literal("[Companion] Now: " + readable);
        Box box = getBoundingBox().expand(NARRATION_RADIUS);
        List<PlayerEntity> nearby = sw.getEntitiesByClass(PlayerEntity.class, box, p -> true);
        for (PlayerEntity player : nearby) {
            player.sendMessage(msg, false);
        }
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

    public GoalType getActiveGoalType()   { return goalExecutor.getActiveType(); }
    public String   getLastPlayerMessage() { return lastPlayerMessage; }
    public String   getGoalFailedReason()  { return goalFailedReason; }
    public ActionExecutor getActions()     { return actions; }
    public GoalExecutor   getGoalExecutor(){ return goalExecutor; }

    public int  getHunger()          { return hunger; }
    public void setHunger(int value) { hunger = Math.max(0, Math.min(20, value)); }

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
