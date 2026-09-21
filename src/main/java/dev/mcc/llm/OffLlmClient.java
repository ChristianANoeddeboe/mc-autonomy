package dev.mcc.llm;

import dev.mcc.config.CompanionConfig;
import java.util.concurrent.CompletableFuture;

/** Client that fails immediately — llm mode "off". */
public final class OffLlmClient implements LlmClient {
	@Override public CompletableFuture<String> complete(String system, String user) {
		return CompletableFuture.failedFuture(new IllegalStateException("llm off"));
	}
	@Override public CompletableFuture<Boolean> probe() {
		return CompletableFuture.completedFuture(false);
	}
	@Override public void shutdown() {}
}