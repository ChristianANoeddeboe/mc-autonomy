package com.example.companion.entity;

import com.example.companion.CompanionMod;
import com.example.companion.behavior.ReactiveBehaviorController;
import com.example.companion.goal.GoalExecutor;
import com.example.companion.goal.GoalType;
import com.example.companion.objective.Objective;
import com.example.companion.plan.CompanionPlan;
import com.example.companion.plan.PlanExecutor;
import com.example.companion.util.ScanUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The companion entity.
 *
 * <p>Extends {@link PathfinderMob} so vanilla pathfinding works out of the box.
 *
 * <p>Tick order each server tick:
 * <pre>
 *   ReactiveBehaviorController  — always-on, can pause the goal
 *   GoalExecutor                — drives the active CompanionGoal FSM
 *   PlanExecutor                — advances plan steps, requests plans from sidecar
 * </pre>
 */
public class CompanionEntity extends PathfinderMob {

    /** 36-slot inventory (matches a standard player hotbar + inventory). */
    private static final int INVENTORY_SIZE = 36;

    /** Hunger drains 1 point every this many ticks (80 ticks = 4 s). */
    private static final int HUNGER_DRAIN_INTERVAL = 80;

    /** Radius for narrating goal changes to nearby players. */
    private static final double NARRATION_RADIUS = 32.0;

    private final ReactiveBehaviorController reactiveBehaviors;
    private final GoalExecutor goalExecutor;
    private final PlanExecutor planExecutor;

    /**
     * Inventory container.
     *
     * <p>Deliberately <em>not</em> synced via {@code setChanged()}: goals and
     * behaviors mutate live {@link ItemStack} references obtained from
     * {@link #getNonEmptyStacks()} (e.g. {@code stack.shrink(n)}), which never
     * notifies the container. State is instead written to the attachment on the
     * periodic sync in {@link #tick()}.
     */
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);

    /** Hunger level 0-20 (mirrors the player hunger mechanic). */
    private int hunger = 20;
    private int hungerDrainTick = 0;

    /** Set to true after the first tick loads state from the Fabric attachment. */
    private boolean dataLoaded = false;

    private String  lastPlayerMessage     = null;
    private String  goalFailedReason      = null;
    private boolean initialPlanScheduled  = false;

    /** The companion's long-term objective, used to bias AI plan selection. */
    private Objective objective = Objective.NONE;

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    public CompanionEntity(EntityType<? extends CompanionEntity> entityType, Level world) {
        super(entityType, world);
        this.reactiveBehaviors = new ReactiveBehaviorController(this);
        this.goalExecutor      = new GoalExecutor(this);
        this.planExecutor      = new PlanExecutor(this);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) return;

        if (!dataLoaded) {
            dataLoaded = true;
            CompanionPersistentData data = getAttachedOrCreate(CompanionPersistentData.ATTACHMENT);
            hunger    = data.getHunger();
            objective = Objective.fromString(data.getObjective());
            inventory.clearContent();
            for (CompanionPersistentData.SlottedItem si : data.getInventoryItems()) {
                if (si.slot() >= 0 && si.slot() < inventory.getContainerSize()) {
                    inventory.setItem(si.slot(), si.stack().copy());
                }
            }
            // Restore persisted plan so the companion picks up where it left off
            String planJson = data.getCurrentPlanJson();
            if (planJson != null && !planJson.isEmpty()) {
                try {
                    CompanionPlan restored = CompanionPlan.fromJson(
                            com.google.gson.JsonParser.parseString(planJson).getAsJsonObject());
                    planExecutor.restorePlan(restored);
                    initialPlanScheduled = true; // plan already in progress
                } catch (Exception e) {
                    CompanionMod.LOGGER.warn("Could not restore persisted plan: {}", e.getMessage());
                }
            }
        }

        // Request the first plan shortly after spawn
        if (!initialPlanScheduled) {
            planExecutor.scheduleRequest(20);
            initialPlanScheduled = true;
        }

        tickHunger();
        tickWaterEscape();

        reactiveBehaviors.tick();
        goalExecutor.tick();
        planExecutor.tick();

        // Persist mutable state every second (20 ticks) so restarts are low-loss
        if (level().getGameTime() % 20 == 0) {
            persistCurrentPlan();
            syncInventoryToAttachment();
        }
    }

    private void persistCurrentPlan() {
        CompanionPlan plan = planExecutor.getCurrentPlan();
        CompanionPersistentData data = getAttachedOrCreate(CompanionPersistentData.ATTACHMENT);
        if (plan != null && !plan.isComplete()) {
            data.setCurrentPlanJson(plan.toJson().toString());
        } else {
            data.setCurrentPlanJson("");
        }
    }

    private void tickWaterEscape() {
        if (isInWater()) {
            // Swim upward to avoid drowning — PathfinderMob has no built-in float goal
            setDeltaMovement(getDeltaMovement().add(0, 0.08, 0));
            getNavigation().stop();
        }
    }

    private void tickHunger() {
        if (!com.example.companion.CompanionConfig.get().hungerEnabled) return;
        if (++hungerDrainTick >= HUNGER_DRAIN_INTERVAL) {
            hungerDrainTick = 0;
            if (hunger > 0) setHunger(hunger - 1);
        }
    }

    // ------------------------------------------------------------------
    // Persistence — Fabric AttachmentType handles NBT serialization.
    // hunger/objective write-through via setters; inventory and plan are
    // persisted on the periodic sync in tick().
    // ------------------------------------------------------------------

    private void syncInventoryToAttachment() {
        CompanionPersistentData data = getAttachedOrCreate(CompanionPersistentData.ATTACHMENT);
        List<CompanionPersistentData.SlottedItem> items = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) items.add(new CompanionPersistentData.SlottedItem(i, stack));
        }
        data.setInventoryItems(items);
    }

    @Override
    public float getPathfindingMalus(PathType pathType) {
        if (pathType == PathType.WATER || pathType == PathType.WATER_BORDER) {
            return 8.0f;
        }
        return super.getPathfindingMalus(pathType);
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack stack) {
        return true;
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem().copy();
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
        planExecutor.onStepComplete();
    }

    public void reportGoalFailed(GoalType failedType, String reason) {
        goalFailedReason = reason;
        planExecutor.onStepFailed(failedType, reason);
    }

    private void narrateGoal(GoalType type) {
        if (!com.example.companion.CompanionConfig.get().narrationEnabled) return;
        if (!(level() instanceof ServerLevel sw)) return;
        String readable = type.name().replace('_', ' ').toLowerCase();
        Component msg = Component.literal("[Companion] Now: " + readable);
        AABB box = getBoundingBox().inflate(NARRATION_RADIUS);
        List<ServerPlayer> nearby = sw.getEntitiesOfClass(ServerPlayer.class, box, p -> true);
        for (ServerPlayer player : nearby) {
            player.sendSystemMessage(msg);
        }
    }

    // ------------------------------------------------------------------
    // Chat integration
    // ------------------------------------------------------------------

    public void onPlayerMessage(String playerName, String message) {
        lastPlayerMessage = message;
        planExecutor.onPlayerMessage(message);
        CompanionMod.LOGGER.info("CompanionEntity: message from {}: {}", playerName, message);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public Objective getObjective()            { return objective; }
    public void      setObjective(Objective o) {
        this.objective = o;
        if (level() instanceof ServerLevel) {
            getAttachedOrCreate(CompanionPersistentData.ATTACHMENT).setObjective(o.name());
        }
        CompanionMod.LOGGER.info("CompanionEntity: objective set to {}", o.name());
        planExecutor.cancelAndReplan("objective-changed: " + o.name());
    }

    public GoalType       getActiveGoalType()    { return goalExecutor.getActiveType(); }
    public CompanionPlan  getCurrentPlan()        { return planExecutor.getCurrentPlan(); }
    public String         getLastPlayerMessage()  { return lastPlayerMessage; }
    public String         getGoalFailedReason()   { return goalFailedReason; }
    public GoalExecutor   getGoalExecutor()       { return goalExecutor; }
    public PlanExecutor   getPlanExecutor()       { return planExecutor; }

    public int  getHunger()          { return hunger; }
    public void setHunger(int value) {
        hunger = Math.max(0, Math.min(20, value));
        if (level() instanceof ServerLevel) {
            getAttachedOrCreate(CompanionPersistentData.ATTACHMENT).setHunger(hunger);
        }
    }

    public SimpleContainer getCarriedInventory() { return inventory; }

    public List<ItemStack> getNonEmptyStacks() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) result.add(s);
        }
        return result;
    }

    public void restoreHunger(int amount) {
        setHunger(hunger + amount);
    }
}
