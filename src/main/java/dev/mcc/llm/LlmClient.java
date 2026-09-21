package dev.mcc.llm;

import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-compatible chat client abstraction. Future-based so calls run on the
 * HttpClient executor; results are drained by CompanionBrain.tick on the server
 * thread (never touch game objects off-thread).
 */
public interface LlmClient {
	/** Send system+user, return the model's raw text. */
	CompletableFuture<String> complete(String system, String user);
	/** Liveness probe (GET /v1/models). */
	CompletableFuture<Boolean> probe();
	/** Release resources (HTTP client close, etc.). */
	void shutdown();
}