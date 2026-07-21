package com.example.companion.net;

import com.example.companion.CompanionMod;
import com.example.companion.goal.GoalType;
import com.example.companion.plan.CompanionPlan;
import com.example.companion.plan.PlanStep;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Async HTTP client for the Python sidecar.
 *
 * <p>Endpoints used:
 * <ul>
 *   <li>{@code POST /plan}          — send world state, receive a {@link CompanionPlan}</li>
 *   <li>{@code POST /step_complete} — notify sidecar a step finished (fire-and-forget)</li>
 * </ul>
 */
public class SidecarClient {

    private static final int TIMEOUT_SECONDS       = 60;
    private static final int STEP_NOTIFY_TIMEOUT_S = 10;

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    // ------------------------------------------------------------------
    // URL helpers
    // ------------------------------------------------------------------

    private String baseUrl() {
        return "http://"
                + com.example.companion.CompanionConfig.get().sidecarHost + ":"
                + com.example.companion.CompanionConfig.get().sidecarPort;
    }

    // ------------------------------------------------------------------
    // /plan
    // ------------------------------------------------------------------

    /**
     * POST world state to {@code /plan}; callback receives a parsed
     * {@link CompanionPlan} or {@code null} on failure.
     */
    public void postPlanAsync(JsonObject worldState, Consumer<CompanionPlan> callback) {
        String body = GSON.toJson(worldState);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/plan"))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        CompanionMod.LOGGER.warn("Sidecar /plan returned HTTP {} — body: {}",
                                response.statusCode(), response.body());
                        return null;
                    }
                    try {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        return parsePlan(json);
                    } catch (Exception e) {
                        CompanionMod.LOGGER.error("Failed to parse /plan response: {}", e.getMessage());
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    if (isConnectionRefused(ex)) {
                        CompanionMod.LOGGER.error(
                                "Sidecar unreachable — start it with: uvicorn main:app --port 8765");
                    } else {
                        CompanionMod.LOGGER.warn("Sidecar /plan request failed: {}", ex.getMessage());
                    }
                    return null;
                })
                .thenAccept(callback);
    }

    // ------------------------------------------------------------------
    // /step_complete  (fire-and-forget)
    // ------------------------------------------------------------------

    /** Notify the sidecar that a plan step completed so it can update its knowledge. */
    public void postStepCompleteAsync(JsonObject body) {
        String bodyStr = GSON.toJson(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/step_complete"))
                .timeout(Duration.ofSeconds(STEP_NOTIFY_TIMEOUT_S))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyStr))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .exceptionally(ex -> {
                    CompanionMod.LOGGER.debug("Step-complete notification failed: {}", ex.getMessage());
                    return null;
                });
        // We deliberately do not chain thenAccept — true fire-and-forget.
    }

    // ------------------------------------------------------------------
    // Parsing helpers
    // ------------------------------------------------------------------

    private static CompanionPlan parsePlan(JsonObject json) {
        String planId = json.has("plan_id") ? json.get("plan_id").getAsString() : "unknown";
        String name   = json.has("name")    ? json.get("name").getAsString()    : "unnamed";

        JsonArray stepsJson = json.has("steps") ? json.getAsJsonArray("steps") : new JsonArray();
        List<PlanStep> steps = new ArrayList<>();

        for (JsonElement el : stepsJson) {
            JsonObject stepObj = el.getAsJsonObject();
            GoalType goal = GoalType.fromString(
                    stepObj.has("goal") ? stepObj.get("goal").getAsString() : "IDLE");
            String note   = stepObj.has("note") ? stepObj.get("note").getAsString() : "";

            Map<String, Object> params = new HashMap<>();
            if (stepObj.has("params") && stepObj.get("params").isJsonObject()) {
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

        return new CompanionPlan(planId, name, steps);
    }

    private static boolean isConnectionRefused(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.net.ConnectException) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
