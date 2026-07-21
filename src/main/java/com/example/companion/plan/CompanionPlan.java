package com.example.companion.plan;

import com.example.companion.goal.GoalType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered sequence of {@link PlanStep}s produced by the AI sidecar.
 *
 * <p>Tracks which step is currently executing and how many consecutive
 * failures that step has accumulated so {@link PlanExecutor} can decide
 * when to retry vs escalate to the AI.
 */
public class CompanionPlan {

    private final String        planId;
    private final String        name;
    private final List<PlanStep> steps;

    private int currentStepIndex = 0;
    private int stepFailureCount = 0;

    public CompanionPlan(String planId, String name, List<PlanStep> steps) {
        this.planId = planId;
        this.name   = name;
        this.steps  = List.copyOf(steps);
    }

    // ------------------------------------------------------------------
    // Step navigation
    // ------------------------------------------------------------------

    /** Advance to the next step. @return true if there are more steps, false if plan is done. */
    public boolean advance() {
        currentStepIndex++;
        stepFailureCount = 0;
        return !isComplete();
    }

    /** @return the currently active step, or null if the plan is complete. */
    public PlanStep currentStep() {
        return isComplete() ? null : steps.get(currentStepIndex);
    }

    public boolean isComplete() {
        return currentStepIndex >= steps.size();
    }

    // ------------------------------------------------------------------
    // Failure tracking
    // ------------------------------------------------------------------

    public int  incrementFailures() { return ++stepFailureCount; }
    public void resetFailures()     { stepFailureCount = 0; }
    public int  getFailureCount()   { return stepFailureCount; }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public String        getPlanId()            { return planId; }
    public String        getName()              { return name; }
    public int           getCurrentStepIndex()  { return currentStepIndex; }
    public int           getStepCount()         { return steps.size(); }
    public List<PlanStep> getSteps()            { return steps; }

    // ------------------------------------------------------------------
    // Serialisation (for persistence across server restarts)
    // ------------------------------------------------------------------

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("plan_id",           planId);
        obj.addProperty("name",              name);
        obj.addProperty("current_step_index", currentStepIndex);

        JsonArray stepsArr = new JsonArray();
        for (PlanStep step : steps) {
            JsonObject stepObj = new JsonObject();
            stepObj.addProperty("goal", step.goal().name());
            stepObj.addProperty("note", step.note());

            JsonObject paramsObj = new JsonObject();
            for (Map.Entry<String, Object> e : step.params().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Number n)  paramsObj.addProperty(e.getKey(), n.doubleValue());
                else if (v instanceof Boolean b) paramsObj.addProperty(e.getKey(), b);
                else paramsObj.addProperty(e.getKey(), v.toString());
            }
            stepObj.add("params", paramsObj);
            stepsArr.add(stepObj);
        }
        obj.add("steps", stepsArr);
        return obj;
    }

    public static CompanionPlan fromJson(JsonObject obj) {
        String planId = obj.get("plan_id").getAsString();
        String name   = obj.get("name").getAsString();
        int stepIndex = obj.has("current_step_index") ? obj.get("current_step_index").getAsInt() : 0;

        List<PlanStep> steps = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("steps")) {
            JsonObject stepObj = el.getAsJsonObject();
            GoalType goal = GoalType.fromString(stepObj.get("goal").getAsString());
            String note   = stepObj.has("note") ? stepObj.get("note").getAsString() : "";

            Map<String, Object> params = new HashMap<>();
            if (stepObj.has("params")) {
                for (Map.Entry<String, JsonElement> e : stepObj.getAsJsonObject("params").entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        JsonPrimitive p = e.getValue().getAsJsonPrimitive();
                        if (p.isNumber())       params.put(e.getKey(), p.getAsDouble());
                        else if (p.isBoolean()) params.put(e.getKey(), p.getAsBoolean());
                        else                    params.put(e.getKey(), p.getAsString());
                    }
                }
            }
            steps.add(new PlanStep(goal, params, note));
        }

        CompanionPlan plan = new CompanionPlan(planId, name, steps);
        // Clamp to steps.size() (not size-1) so a plan persisted at completion
        // stays complete on restore instead of re-running its final step.
        plan.currentStepIndex = Math.max(0, Math.min(stepIndex, steps.size()));
        return plan;
    }
}
