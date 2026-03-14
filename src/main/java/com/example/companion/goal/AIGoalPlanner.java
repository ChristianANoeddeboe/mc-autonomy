package com.example.companion.goal;

import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.entity.WorldPerception;
import com.example.companion.net.SidecarClient;
import com.google.gson.JsonObject;

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
 *   <li>The periodic fallback timer fires (every {@value FALLBACK_INTERVAL_TICKS} ticks)</li>
 * </ul>
 *
 * <p>Requests are non-blocking; the response is applied on the next safe tick.
 */
public class AIGoalPlanner {

    /** 60 seconds × 20 ticks/s */
    private static final int FALLBACK_INTERVAL_TICKS = 20 * 60;

    private final CompanionEntity entity;
    private final SidecarClient sidecarClient;

    private int ticksSinceLastRequest = 0;
    private final AtomicBoolean requestPending = new AtomicBoolean(false);

    /** Countdown for a scheduled one-shot trigger; -1 = none pending. */
    private int scheduledTriggerIn = -1;

    // Queued result from async sidecar callback
    private volatile GoalType pendingGoalType = null;
    private volatile Map<String, Object> pendingParams = null;

    public AIGoalPlanner(CompanionEntity entity) {
        this.entity = entity;
        this.sidecarClient = new SidecarClient();
    }

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
        triggerRequest("goal-complete");
    }

    public void onGoalFailed(String reason) {
        triggerRequest("goal-failed: " + reason);
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
        // Apply queued result from async sidecar response
        if (pendingGoalType != null) {
            GoalType type = pendingGoalType;
            Map<String, Object> params = pendingParams;
            pendingGoalType = null;
            pendingParams = null;
            entity.setGoal(type, params != null ? params : Map.of());
        }

        // Scheduled one-shot trigger (e.g. initial spawn request)
        if (scheduledTriggerIn > 0) {
            scheduledTriggerIn--;
            if (scheduledTriggerIn == 0) {
                scheduledTriggerIn = -1;
                triggerRequest("scheduled");
                return;
            }
        }

        ticksSinceLastRequest++;
        if (ticksSinceLastRequest >= FALLBACK_INTERVAL_TICKS) {
            triggerRequest("periodic-fallback");
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void triggerRequest(String reason) {
        if (!requestPending.compareAndSet(false, true)) {
            CompanionMod.LOGGER.debug("AIGoalPlanner: request already in flight, skipping ({})", reason);
            return;
        }

        ticksSinceLastRequest = 0;
        CompanionMod.LOGGER.info("AIGoalPlanner: sending sidecar request [{}]", reason);

        JsonObject worldState = WorldPerception.serialize(entity);

        sidecarClient.postDecideAsync(worldState, response -> {
            requestPending.set(false);
            if (response == null) {
                CompanionMod.LOGGER.warn("AIGoalPlanner: null response from sidecar, keeping current goal");
                return;
            }
            try {
                String goalStr = response.get("goal").getAsString();
                GoalType type = GoalType.fromString(goalStr);
                CompanionMod.LOGGER.info("AIGoalPlanner: received goal={} reason={}",
                        type, response.has("reason") ? response.get("reason").getAsString() : "?");
                // Queue for safe application on next tick
                pendingGoalType = type;
                pendingParams = Map.of();
            } catch (Exception e) {
                CompanionMod.LOGGER.error("AIGoalPlanner: failed to parse sidecar response", e);
            }
        });
    }
}
