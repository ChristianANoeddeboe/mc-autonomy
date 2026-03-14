package com.example.companion.net;

import com.example.companion.CompanionMod;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Async HTTP client that communicates with the Python sidecar.
 *
 * <p>Sends a non-blocking POST to {@code http://localhost:8765/decide} with the
 * serialised world state, then calls the provided callback with the parsed
 * {@link JsonObject} response (or {@code null} on error).
 *
 * <p>Timeout behaviour:
 * <ul>
 *   <li>Response timeout: {@value TIMEOUT_SECONDS} seconds — logs warning, keeps current goal</li>
 *   <li>Connection refused: logs error, entity enters IDLE fallback</li>
 * </ul>
 */
public class SidecarClient {

    private static final String SIDECAR_URL = System.getProperty("companion.sidecarUrl",
            "http://localhost:8765/decide");
    private static final int TIMEOUT_SECONDS = 5;

    private static final Gson GSON = new Gson();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    /**
     * Fire-and-forget POST; {@code callback} is invoked on the common fork-join pool thread.
     *
     * @param worldState serialised world state JSON
     * @param callback   receives the parsed {@link JsonObject} or {@code null} on failure
     */
    public void postDecideAsync(JsonObject worldState, Consumer<JsonObject> callback) {
        String body = GSON.toJson(worldState);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SIDECAR_URL))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        CompanionMod.LOGGER.warn("Sidecar returned HTTP {}", response.statusCode());
                        return null;
                    }
                    try {
                        return JsonParser.parseString(response.body()).getAsJsonObject();
                    } catch (Exception e) {
                        CompanionMod.LOGGER.error("Failed to parse sidecar response: {}", e.getMessage());
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    if (isConnectionRefused(ex)) {
                        CompanionMod.LOGGER.error("Sidecar unreachable — entering IDLE fallback. Start the sidecar with: uvicorn main:app --port 8765");
                    } else {
                        CompanionMod.LOGGER.warn("Sidecar request failed: {}", ex.getMessage());
                    }
                    return null;
                })
                .thenAccept(callback);
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
