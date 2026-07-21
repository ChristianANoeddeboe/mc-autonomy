package com.example.companion.plan;

import com.example.companion.CompanionConfig;
import com.example.companion.CompanionMod;
import com.example.companion.entity.CompanionEntity;
import com.example.companion.entity.WorldPerception;
import com.example.companion.goal.GoalType;
import com.example.companion.net.SidecarClient;
import com.google.gson.JsonObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates multi-step plan execution.
 *
 * <p>Replaces the single-goal {@code AIGoalPlanner} with a plan-aware loop:
 * <ol>
 *   <li>No plan → ask the sidecar for one.</li>
 *   <li>Plan received → log all steps, start executing step 0.</li>
 *   <li>Step completes → notify sidecar async, advance to next step.</li>
 *   <li>Step fails once → retry same step.</li>
 *   <li>Step fails twice → ask sidecar for a revised plan.</li>
 *   <li>All steps done → ask sidecar for the next plan.</li>
 * </ol>
 *
 * <p>The sidecar is only called when the plan changes, not every tick.
 */
public class PlanExecutor {

    private static final int MAX_STEP_RETRIES = 2;

    private final CompanionEntity entity;
    private final SidecarClient   sidecarClient;

    private CompanionPlan          currentPlan   = null;
    private volatile CompanionPlan pendingPlan   = null;

    private int                ticksSinceLastRequest = 0;
    private final AtomicBoolean requestPending       = new AtomicBoolean(false);

    public PlanExecutor(CompanionEntity entity) {
        this.entity        = entity;
        this.sidecarClient = new SidecarClient();
    }

    // ------------------------------------------------------------------
    // Tick — called every server tick by CompanionEntity
    // ------------------------------------------------------------------

    public void tick() {
        // Apply a plan received from the async sidecar callback
        if (pendingPlan != null) {
            CompanionPlan plan = pendingPlan;
            pendingPlan = null;
            applyPlan(plan);
            return;
        }

        ticksSinceLastRequest++;

        // No plan (or plan complete) → request one after the configured interval
        if (currentPlan == null || currentPlan.isComplete()) {
            int fallbackTicks = CompanionConfig.get().decisionIntervalSeconds * 20;
            if (ticksSinceLastRequest >= fallbackTicks) {
                requestPlan("no-plan");
            }
        }
    }

    // ------------------------------------------------------------------
    // Step outcome callbacks (called by CompanionEntity)
    // ------------------------------------------------------------------

    public void onStepComplete() {
        if (currentPlan == null || currentPlan.isComplete()) return;

        PlanStep step = currentPlan.currentStep();
        CompanionMod.LOGGER.info("PlanExecutor: step {}/{} ({}) complete — plan='{}'",
                currentPlan.getCurrentStepIndex() + 1,
                currentPlan.getStepCount(),
                step.goal(),
                currentPlan.getName());

        // Notify sidecar so it can record the accomplishment (fire-and-forget)
        notifyStepComplete(currentPlan, step);

        if (currentPlan.advance()) {
            startCurrentStep();
        } else {
            CompanionMod.LOGGER.info("PlanExecutor: plan '{}' finished — requesting next",
                    currentPlan.getName());
            currentPlan = null;
            requestPlan("plan-complete");
        }
    }

    public void onStepFailed(GoalType failedType, String reason) {
        if (currentPlan == null) return;

        int failures = currentPlan.incrementFailures();
        CompanionMod.LOGGER.warn("PlanExecutor: step {} failed ({} time(s)) — {}",
                failedType, failures, reason);

        if (failures < MAX_STEP_RETRIES) {
            CompanionMod.LOGGER.info("PlanExecutor: retrying step {}", failedType);
            startCurrentStep();
        } else {
            CompanionMod.LOGGER.warn("PlanExecutor: {} failed {} times — requesting replan",
                    failedType, failures);
            currentPlan = null;
            requestPlan("step-failed(" + failures + "x): " + reason);
        }
    }

    /** Called when the player sends a chat message — cancels current plan and replans. */
    public void onPlayerMessage(String message) {
        currentPlan = null;
        requestPlan("player-message: " + message);
    }

    /** Cancel the active plan and immediately request a new one (e.g. after objective changes). */
    public void cancelAndReplan(String reason) {
        currentPlan = null;
        requestPlan(reason);
    }

    /** Fire an early request in {@code delayTicks} ticks (used when no plan is active). */
    public void scheduleRequest(int delayTicks) {
        int fallback = CompanionConfig.get().decisionIntervalSeconds * 20;
        ticksSinceLastRequest = Math.max(ticksSinceLastRequest, fallback - Math.max(1, delayTicks));
    }

    // ------------------------------------------------------------------
    // Plan lifecycle
    // ------------------------------------------------------------------

    private void applyPlan(CompanionPlan plan) {
        if (plan.getStepCount() == 0) {
            CompanionMod.LOGGER.warn("PlanExecutor: received empty plan — staying idle");
            return;
        }
        currentPlan = plan;
        CompanionMod.LOGGER.info("PlanExecutor: starting plan '{}' ({} steps):",
                plan.getName(), plan.getStepCount());
        for (int i = 0; i < plan.getStepCount(); i++) {
            PlanStep s = plan.getSteps().get(i);
            CompanionMod.LOGGER.info("  {}. {} — {}", i + 1, s.goal(), s.note());
        }
        startCurrentStep();
    }

    private void startCurrentStep() {
        if (currentPlan == null || currentPlan.isComplete()) return;
        PlanStep step = currentPlan.currentStep();
        currentPlan.resetFailures();
        entity.setGoal(step.goal(), step.params());
    }

    // ------------------------------------------------------------------
    // Sidecar communication
    // ------------------------------------------------------------------

    private void requestPlan(String reason) {
        if (!requestPending.compareAndSet(false, true)) {
            CompanionMod.LOGGER.debug("PlanExecutor: request already in flight ({})", reason);
            return;
        }
        ticksSinceLastRequest = 0;
        CompanionMod.LOGGER.info("PlanExecutor: requesting new plan [{}]", reason);

        JsonObject worldState = WorldPerception.serialize(entity);
        sidecarClient.postPlanAsync(worldState, plan -> {
            requestPending.set(false);
            if (plan != null) {
                pendingPlan = plan;
            } else {
                CompanionMod.LOGGER.warn("PlanExecutor: null plan response — will retry");
            }
        });
    }

    private void notifyStepComplete(CompanionPlan plan, PlanStep step) {
        JsonObject body = new JsonObject();
        body.addProperty("plan_id",    plan.getPlanId());
        body.addProperty("step_index", plan.getCurrentStepIndex());
        body.addProperty("goal",       step.goal().name());
        body.addProperty("note",       step.note());
        body.add("world_state", WorldPerception.serialize(entity));
        sidecarClient.postStepCompleteAsync(body);
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public CompanionPlan getCurrentPlan() { return currentPlan; }

    /** Restore a persisted plan on server reload (skips the intro log). */
    public void restorePlan(CompanionPlan plan) {
        if (plan != null && !plan.isComplete()) {
            currentPlan = plan;
            CompanionMod.LOGGER.info("PlanExecutor: restored plan '{}' at step {}/{}",
                    plan.getName(),
                    plan.getCurrentStepIndex() + 1,
                    plan.getStepCount());
            startCurrentStep();
        }
    }
}
