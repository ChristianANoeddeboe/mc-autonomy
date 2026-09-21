package dev.mcc.llm;

import dev.mcc.config.CompanionConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Real HTTP client speaking POST /v1/chat/completions (Ollama, LM Studio,
 * vLLM, OpenAI all accept it). JSON via Gson on Minecraft's classpath.
 */
public final class HttpLlmClient implements LlmClient {
	private final HttpClient client;
	private final CompanionConfig.LlmGroup cfg;

	public HttpLlmClient(CompanionConfig.LlmGroup cfg) {
		this.cfg = cfg;
		this.client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(Math.max(500, cfg.timeoutMs)))
			.build();
	}

	@Override public CompletableFuture<String> complete(String system, String user) {
		com.google.gson.JsonObject body = new com.google.gson.JsonObject();
		body.addProperty("model", cfg.model);
		com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
		messages.add(msg("system", system));
		messages.add(msg("user", user));
		body.add("messages", messages);
		body.addProperty("temperature", 0.4);
		com.google.gson.JsonObject responseFormat = new com.google.gson.JsonObject();
		responseFormat.addProperty("type", "json_object");
		body.add("response_format", responseFormat);

		java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest
			.newBuilder(URI.create(stripSlash(cfg.baseUrl) + "/v1/chat/completions"))
			.header("Content-Type", "application/json")
			.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()));
		if (cfg.apiKey != null && !cfg.apiKey.isEmpty()) {
			builder.header("Authorization", "Bearer " + cfg.apiKey);
		}

		return client.sendAsync(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString())
			.thenApply(HttpLlmClient::parse)
			.exceptionally(e -> {
				dev.mcc.MccMod.LOGGER.info("[MCC][llm] error: {}", e.toString());
				return null;
			});
	}

	@Override public CompletableFuture<Boolean> probe() {
		java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest
			.newBuilder(URI.create(stripSlash(cfg.baseUrl) + "/v1/models"))
			.GET();
		return client.sendAsync(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString())
			.thenApply(r -> r.statusCode() >= 200 && r.statusCode() < 300);
	}

	@Override public void shutdown() {
		// Java 21 HttpClient.close(); fire and forget (no closeAsync in this build).
		try {
			client.close();
		} catch (Exception e) {
			dev.mcc.MccMod.LOGGER.warn("[MCC][llm] close: {}", e.toString());
		}
	}

	private static com.google.gson.JsonObject msg(String role, String content) {
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("role", role);
		o.addProperty("content", content);
		return o;
	}

	private static String parse(java.net.http.HttpResponse<String> resp) {
		if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
			dev.mcc.MccMod.LOGGER.info("[MCC][llm] HTTP {} : {}", resp.statusCode(), resp.body());
			return null;
		}
		try {
			com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
			return root.getAsJsonArray("choices").get(0).getAsJsonObject()
				.get("message").getAsJsonObject().get("content").getAsString();
		} catch (Exception e) {
			dev.mcc.MccMod.LOGGER.warn("[MCC][llm] parse error: {}", e.toString());
			return null;
		}
	}

	private static String stripSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}