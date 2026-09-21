package dev.mcc.llm;

import dev.mcc.config.CompanionConfig;
import java.util.concurrent.CompletableFuture;

/**
 * Scripted mock for headless/deterministic tests. Matches the payload's
 * intent/trigger against rules in mock_script.json:
 *   {"rules":[{"whenIntent":"GIVE_REQUEST","respond":{decision json}},
 *             {"whenTrigger":"PERIODIC","respond":{...}},
 *             {"default":{...}}],
 *    "failNext": n, "delayMs": m}
 * Also used by CompanionBrainTest via a RecordingLlmClient wrapper.
 */
public final class MockLlmClient implements LlmClient {
	private final com.google.gson.JsonObject script;
	private final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
	private final long delayMs;

	public MockLlmClient(CompanionConfig.LlmGroup cfg) {
		com.google.gson.JsonObject parsed = new com.google.gson.JsonObject();
		try {
			parsed = com.google.gson.JsonParser.parseString(
				java.nio.file.Files.readString(java.nio.file.Path.of("config/mc-companion/" + cfg.mockScript)))
				.getAsJsonObject();
		} catch (Exception e) {
			dev.mcc.MccMod.LOGGER.warn("[MCC][mock] could not load {} : {}", cfg.mockScript, e.toString());
		}
		this.script = parsed;
		this.delayMs = parsed.has("delayMs") ? parsed.get("delayMs").getAsLong() : 0;
	}

	/** Construct directly from an inline script (tests). */
	private MockLlmClient(com.google.gson.JsonObject script, long delayMs) {
		this.script = script; this.delayMs = delayMs;
	}

	public static MockLlmClient of(String json) {
		long delayMs = 0;
		com.google.gson.JsonObject parsed;
		try {
			parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
			if (parsed.has("delayMs")) delayMs = parsed.get("delayMs").getAsLong();
		} catch (Exception e) {
			parsed = new com.google.gson.JsonObject();
		}
		return new MockLlmClient(parsed, delayMs);
	}

	@Override public CompletableFuture<String> complete(String system, String user) {
		int n = calls.incrementAndGet();
		// failNext window
		if (script.has("failNext") && n <= script.get("failNext").getAsInt()) {
			return CompletableFuture.failedFuture(new IllegalStateException("mock-fail"));
		}
		String respond = matchRule(user);
		if (respond == null) return CompletableFuture.completedFuture(null);
		return CompletableFuture.supplyAsync(() -> respond,
			CompletableFuture.delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS));
	}

	@Override public CompletableFuture<Boolean> probe() {
		return CompletableFuture.completedFuture(calls.get() >= (script.has("failNext") ? script.get("failNext").getAsInt() : 0));
	}

	@Override public void shutdown() {}

	int callCount() { return calls.get(); }

	private String matchRule(String user) {
		try {
			com.google.gson.JsonObject payload = com.google.gson.JsonParser.parseString(extractPayload(user)).getAsJsonObject();
			String intent = payload.has("lastMessage") && payload.getAsJsonObject("lastMessage").has("intent")
				? payload.getAsJsonObject("lastMessage").getAsJsonObject("intent").getAsJsonObject().get("type").getAsString()
				: "";
			String trigger = payload.has("trigger") ? payload.get("trigger").getAsString() : "";
			if (script.has("rules")) {
				for (var r : script.getAsJsonArray("rules")) {
					com.google.gson.JsonObject rule = r.getAsJsonObject();
					if (rule.has("whenIntent") && rule.get("whenIntent").getAsString().equals(intent)) {
						return rule.get("respond").toString();
					}
					if (rule.has("whenTrigger") && rule.get("whenTrigger").getAsString().equals(trigger)) {
						return rule.get("respond").toString();
					}
				}
			}
			if (script.has("default")) return script.get("default").toString();
		} catch (Exception e) {
			dev.mcc.MccMod.LOGGER.warn("[MCC][mock] rule match error: {}", e.toString());
		}
		return null;
	}

	private static String extractPayload(String user) {
		// payload embedded as the last JSON object in the prompt; find first { after "PAYLOAD"
		int marker = user.indexOf("PAYLOAD");
		int s = user.indexOf('{', marker >= 0 ? marker : 0);
		int e = user.lastIndexOf('}');
		return (s >= 0 && e > s) ? user.substring(s, e + 1) : "{}";
	}
}