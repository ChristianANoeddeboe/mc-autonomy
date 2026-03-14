package com.example.companion.goal;

import com.example.companion.CompanionConfig;
import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.entity.WorldPerception;
import com.example.companion.net.SidecarClient;
import com.google.gson.JsonObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Slow-loop planner that decides which {@link GoalType} the companion pursues next.
 *
 * <p>Fires a request to the Python sidecar when:
 * <ul>
 *   <li>The current goal reports complete or failed</li>
 *   <li>A player addresses the companion by name in chat</li>
 *   <li>A significant event occurs (health spike, night begins)</li>
 *   <li>The periodic fallback timer fires (configurable, default 60 s)</li>
 * </ul>
 *
 * <h3>Failure recovery</h3>
 * On the <em>first</em> consecutive failure of a goal type the planner applies
 * a hardcoded recovery goal immediately (no sidecar round-trip). On repeated
 * failures it escalates to an AI request with failure context.
 *
 * <p>Requests are non-blocking; the response is applied on the next safe tick.
 */
public class AIGoalPlanner {

    // ------------------------------------------------------------------
    // Recovery strategy table
    // ------------------------------------------------------------------

    /** Immediate fallback goal tried on first failure before asking the AI. */
    private static final Map<GoalType, GoalType> RECOVERY_MAP = new EnumMap<>(GoalType.class);
    static {
        RECOVERY_MAP.put(GoalType.GATHER_WOOD,    GoalType.EXPLORE);        // find trees elsewhere
        RECOVERY_MAP.put(GoalType.FIND_FOOD,      GoalType.EXPLORE);        // explore for food
        RECOVERY_MAP.put(GoalType.BUILD_SHELTER,  GoalType.GATHER_WOOD);    // get materials first
        RECOVERY_MAP.put(GoalType.MINE_RESOURCES, GoalType.EXPLORE);        // better mining spot
        RECOVERY_MAP.put(GoalType.CRAFT_ITEM,     GoalType.GATHER_WOOD);    // gather raw materials
        RECOVERY_MAP.put(GoalType.SLEEP,          GoalType.IDLE);           // no bed — rest
        RECOVERY_MAP.put(GoalType.EXPLORE,        GoalType.IDLE);           // couldn't explore
        RECOVERY_MAP.put(GoalType.FOLLOW_PLAYER,  GoalType.IDLE);           // player unreachable
    }

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    private final CompanionEntity entity;
    private final SidecarClient sidecarClient;

    private int ticksSinceLastRequest = 0;
    private final AtomicBoolean requestPending = new AtomicBoolean(false);

    /** Countdown for a scheduled one-shot trigger; -1 = none pending. */
    private int scheduledTriggerIn = -1;

    /** Number of consecutive failures per goal type (reset on success or AI replan). */
    private final Map<GoalType, Integer> consecutiveFailures = new EnumMap<>(GoalType.class);

    // Queued result from async sidecar callback — applied on next tick
    private volatile GoalType pendingGoalType = null;
    private volatile Map<String, Object> pendingParams = null;

    public AIGoalPlanner(CompanionEntity entity) {
        this.entity = entity;
        this.sidecarClient = new SidecarClient();
    }

    // ------------------------------------------------------------------
    // Scheduled trigger
    // ------------------------------------------------------------------

    /**
     * Schedule a sidecar request to fire in {@code delayTicks} ticks.
     * If a request is already scheduled the closer deadline wins.
     */
    public void scheduleRequest(int delayTicks) {
        if (scheduledTriggerIn < 0 || delayTicks < scheduledTriggerIn) {
            scheduledTriggerIn = Math.max(1, delayTicks);
        }
    }

    // ------------------------------------------------------------------
    // Public trigger API
    // ------------------------------------------------------------------

    public void onGoalComplete() {
        consecutiveFailures.remove(entity.getActiveGoalType());
        triggerRequest("goal-complete");
    }

    public void onGoalFailed(String reason) {
        GoalType failed = entity.getActiveGoalType();
        int failures = consecutiveFailures.merge(failed, 1, Integer::sum);

        GoalType recovery = RECOVERY_MAP.get(failed);
        if (failures == 1 && recovery != null) {
            // First failure — apply recovery immediately, no round-trip
            CompanionMod.LOGGER.info(
                    "AIGoalPlanner: {} failed (first time), applying recovery goal {} before asking AI",
                    failed, recovery);
            // The recovery goal's own complete/fail callback will trigger the next AI request
            entity.setGoal(recovery, Map.of());
        } else {
            // Repeated failure or no recovery — escalate to AI with context
            consecutiveFailures.put(failed, 0); // reset counter after AI involvement
            triggerRequest("goal-failed(" + failures + "x): " + reason);
        }
    }

    public void onPlayerMessage(String message) {
        triggerRequest("player-message: " + message);
    }

    public void onSignificantEvent(String description) {
        triggerRequest("event: " + description);
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /** Must be called every game tick from {@link CompanionEntity#tick()}. */
    public void tick() {
        // Apply queued sidecar response
        if (pendingGoalType != null) {
            GoalType type = pendingGoalType;
            Map<String, Object> params = pendingParams;
            pendingGoalType = null;
            pendingParams = null;
            entity.setGoal(type, params != null ? params : Map.of());
        }

        // Scheduled one-shot trigger
        if (scheduledTriggerIn > 0) {
            scheduledTriggerIn--;
            if (scheduledTriggerIn == 0) {
                scheduledTriggerIn = -1;
                triggerRequest("scheduled");
                return;
            }
        }

        // Periodic fallback
        int fallbackTicks = CompanionConfig.get().decisionIntervalSeconds * 20;
        ticksSinceLastRequest++;
        if (ticksSinceLastRequest >= fallbackTicks) {
            triggerRequest("periodic-fallback");
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void triggerRequest(String reason) {
        if (!requestPending.compareAndSet(false, true)) {
            CompanionMod.LOGGER.debug("AIGoalPlanner: request in flight, skipping ({})", reason);
            return;
        }

        ticksSinceLastRequest = 0;
        CompanionMod.LOGGER.info("AIGoalPlanner: sending sidecar request [{}]", reason);

        JsonObject worldState = WorldPerception.serialize(entity);

        sidecarClient.postDecideAsync(worldState, response -> {
            requestPending.set(false);
            if (response == null) {
                CompanionMod.LOGGER.warn("AIGoalPlanner: null sidecar response, keeping current goal");
                return;
            }
            try {
                String goalStr = response.get("goal").getAsString();
                GoalType type = GoalType.fromString(goalStr);
                CompanionMod.LOGGER.info("AIGoalPlanner: received goal={} reason={}",
                        type, response.has("reason") ? response.get("reason").getAsString() : "?");
                pendingGoalType = type;
                pendingParams = Map.of();
            } catch (Exception e) {
                CompanionMod.LOGGER.error("AIGoalPlanner: failed to parse sidecar response", e);
            }
        });
    }
}
