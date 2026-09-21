package dev.mcc.llm;

import com.google.gson.JsonObject;
import dev.mcc.MccMod;
import dev.mcc.chat.ChatParser.Intent;
import dev.mcc.config.CompanionConfig;
import dev.mcc.entity.CompanionEntity;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The planner. Holds the LlmClient, builds payloads via WorldStateBuilder,
 * validates replies via DecisionValidator, applies valid plans to the executor.
 *
 * Async: LlmClient futures complete on the HttpClient executor; results are
 * pushed onto a ConcurrentLinkedQueue and only drained inside tick() on the
 * server thread — game objects are never touched off-thread.
 *
 * Modes: CONNECTED (real/online), DEGRADED (failing, fallback used immediately),
 * OFFLINE (probe-only + fallback), MOCK (scripted), OFF (disabled: still emits
 * fallback so the companion isn't a statue).
 *
 * The deterministic fallback (FallbackPlanner) runs on any PLAYER_MESSAGE when
 * not CONNECTED, so the companion acts in degraded/offline without a model.
 */
public final class CompanionBrain {
	public enum LlmState { CONNECTED, DEGRADED, OFFLINE, MOCK, OFF }

	private final CompanionEntity entity;
	private final CompanionConfig.LlmGroup cfg;
	private LlmClient client;
	private LlmState state = LlmState.OFF;
	private final ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();
	private CompletableFuture<String> inFlight;
	private CompletableFuture<Boolean> probeInFlight;
	private int failures;
	private long nextProbeTick;
	private long lastRequestTick;
	private Intent pendingIntent;

	public CompanionBrain(CompanionEntity entity) {
		this.entity = entity;
		this.cfg = CompanionConfig.get().llm;
		this.client = createClient();
	}

	LlmClient createClient() {
		return switch (cfg.mode) {
			case "mock" -> MockLlmClient.of(mockScriptDefault());
			case "http" -> new HttpLlmClient(cfg);
			default -> new OffLlmClient();
		};
	}

	private static String mockScriptDefault() {
		return """
			{"rules":[
			  {"whenIntent":"GIVE_REQUEST","respond":{"say":"On it.","plan":[{"type":"GATHER","params":{"block":"minecraft:oak_log","count":3}},{"type":"GIVE","params":{"item":"minecraft:oak_log","count":3}}]}},
			  {"whenIntent":"GATHER_REQUEST","respond":{"say":"Gathering.","plan":[{"type":"GATHER","params":{"block":"minecraft:oak_log","count":3}},{"type":"GIVE","params":{"item":"minecraft:oak_log","count":3}}]}},
			  {"whenIntent":"CRAFT_REQUEST","respond":{"say":"Making it.","plan":[{"type":"CRAFT","params":{"item":"minecraft:oak_planks","count":4}}]}},
			  {"default":{"say":"Okay.","plan":[{"type":"FOLLOW"}]}}
			]}""";
	}

	public LlmState state() { return state; }
	public void setState(LlmState s) { this.state = s; }

	/** Switch the client mode at runtime (llm command): on/http/mock/off. */
	public void setMode(String mode) {
		this.client.shutdown();
		this.client = switch (mode) {
			case "mock" -> MockLlmClient.of(mockScriptDefault());
			case "off" -> new OffLlmClient();
			default -> new HttpLlmClient(cfg);
		};
		this.state = switch (mode) {
			case "mock" -> LlmState.MOCK;
			case "off" -> LlmState.OFF;
			default -> LlmState.CONNECTED;
		};
		this.failures = 0;
		MccMod.LOGGER.info("[MCC][llm] mode={} state={}", mode, this.state);
	}
	public CompletableFuture<String> inFlightFuture() { return inFlight; }
	public Intent pendingIntent() { return pendingIntent; }

	/** Entry point for an addressed player/console message. */
	public void onPlayerMessage(dev.mcc.owner.Owner sendTo, String text) {
		pendingIntent = dev.mcc.chat.ChatParser.parse(text);
		MccMod.LOGGER.info("[MCC][brain] onPlayerMessage intent={} state={} tick={}",
			pendingIntent.type(), state, entity.tickCount);
		// In non-CONNECTED modes, apply the fallback immediately.
		if (state != LlmState.CONNECTED && state != LlmState.MOCK) {
			applyFallback(pendingIntent);
		}
		requestIfIdle();
	}

	public void onGoalDone() { requestIfIdle(); }
	public void onDangerEnded() {
		if (entity.mode() != dev.mcc.entity.BehaviourMode.FOLLOW) return;
		requestIfIdle();
	}

	public void tick(CompanionEntity e) {
		// 1) drain completions (server thread)
		String raw;
		while ((raw = results.poll()) != null) {
			apply(raw);
		}
		// 2) detect dead in-flight (handled via future callbacks pushing null)
		// 3) periodic
		if (state != LlmState.OFF && e.tickCount % cfg.periodicTicks == 0) {
			requestIfIdle();
		}
	}

	/** Apply a raw LLM reply: validate and replace the executor queue. */
	void apply(String raw) {
		if (raw == null || raw.isEmpty()) {
			fail();
			return;
		}
		DecisionValidator.Validated v = DecisionValidator.validate(raw, DecisionValidator.Context.of(resolver())).orElse(null);
		if (v == null) { fail(); return; }
		// success resets the failure counter
		failures = 0;
		if (state == LlmState.DEGRADED || state == LlmState.OFFLINE) {
			state = LlmState.CONNECTED;
			MccMod.LOGGER.info("[MCC][llm] CONNECTED");
		}
		applyValidated(v);
	}

	/** Push a validated plan into the executor and speak. */
	void applyValidated(DecisionValidator.Validated v) {
		boolean declineOnly = !v.plan().isEmpty() && v.plan().get(0).type() == dev.mcc.goal.GoalType.DECLINE;
		if (declineOnly) {
			entity.executor().clear();
			entity.executor().push(v.plan().get(0));
			MccMod.LOGGER.info("[MCC][decline] reason={}", v.plan().get(0).strParam("reason", ""));
		} else if (!v.plan().isEmpty()) {
			entity.executor().replaceQueue(v.plan());
		}
		if (!v.say().isEmpty()) {
			entity.say(v.say());
		}
		List<String> warns = v.warnings();
		if (!warns.isEmpty()) MccMod.LOGGER.info("[MCC][llm] warnings={}", warns);
	}

	/** Deterministic fallback plan (degraded/offline). */
	void applyFallback(Intent intent) {
		var have = entity.inventory().summary();
		java.util.Map<String, Integer> haveIds = new java.util.HashMap<>();
		java.util.Map<String, Boolean> canGather = new java.util.HashMap<>();
		for (var en : have.entrySet()) {
			haveIds.put(WorldStateBuilder.itemId(en.getKey()), en.getValue());
			canGather.put(WorldStateBuilder.itemId(en.getKey()), true);
		}
		List<dev.mcc.goal.GoalSpec> plan = FallbackPlanner.plan(intent, haveIds, canGather, isNight(), List.of());
		if (!plan.isEmpty()) {
			entity.executor().replaceQueue(plan);
			MccMod.LOGGER.info("[MCC][fallback] plan={}", plan.stream().map(s -> s.type().toString()).toList());
		}
	}

	private boolean isNight() {
		CompanionConfig c = CompanionConfig.get();
		long dayTime = entity.level().getDayTime() % 24000;
		return dayTime < c.sleep.nightStart || dayTime > c.sleep.nightEnd;
	}

	private void requestIfIdle() {
		if (state == LlmState.OFF) return;
		long now = entity.tickCount;
		if (now - lastRequestTick < (cfg != null ? cfg.minIntervalTicks : 60)) {
			MccMod.LOGGER.info("[MCC][brain] request gate: rate-lim (tick {} last {} min {})",
				now, lastRequestTick, cfg != null ? cfg.minIntervalTicks : 60);
			return;
		}
		lastRequestTick = now;

		if (state == LlmState.OFFLINE) {
			probe();
			return;
		}
		if (inFlight != null && !inFlight.isDone()) return;

		JsonObject payload = WorldStateBuilder.build(entity, "PLAYER_MESSAGE", pendingIntent);
		MccMod.LOGGER.info("[MCC][brain] sending payload {} bytes", payload.toString().length());
		inFlight = client.complete(systemPrompt(), "PAYLOAD " + payload);
		inFlight.thenAccept(raw -> {
			if (raw != null) results.add(raw);
		});
		MccMod.LOGGER.info("[MCC][llm] request trigger=PLAYER_MESSAGE");
	}

	private void probe() {
		if (state != LlmState.OFFLINE) return;
		if (entity.tickCount < nextProbeTick) return;
		nextProbeTick = entity.tickCount + (cfg != null ? cfg.probeIntervalTicks : 200);
		probeInFlight = client.probe();
		probeInFlight.thenAccept(ok -> {
			if (ok != null && ok) {
				failures = 0;
				state = LlmState.CONNECTED;
				MccMod.LOGGER.info("[MCC][llm] CONNECTED (probe)");
			}
		});
	}

	private void fail() {
		failures++;
		if (state == LlmState.CONNECTED || state == LlmState.MOCK) {
			if (cfg != null && failures >= cfg.degradedAfterFailures) {
				state = LlmState.DEGRADED;
				MccMod.LOGGER.info("[MCC][llm] DEGRADED ({} failures)", failures);
			}
			if (cfg != null && failures >= cfg.offlineAfterFailures) {
				state = LlmState.OFFLINE;
				MccMod.LOGGER.info("[MCC][llm] OFFLINE");
			}
		} else if (state == LlmState.DEGRADED && cfg != null && failures >= cfg.offlineAfterFailures) {
			state = LlmState.OFFLINE;
			MccMod.LOGGER.info("[MCC][llm] OFFLINE");
		}
	}

	private DecisionValidator.Resolver resolver() {
		return new DecisionValidator.Resolver() {
			@Override public boolean itemExists(String id) {
				return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.tryParse(id)).isPresent();
			}
			@Override public boolean blockExists(String id) {
				return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.tryParse(id)).isPresent();
			}
			@Override public boolean tagExists(String id) {
				return false; // tags validated live in GatherGoal anyway
			}
		};
	}

	private String systemPrompt() {
		return "You are " + dev.mcc.config.CompanionConfig.get().chat.name
			+ ", a loyal companion. Return ONLY JSON {\"say\":\"...\",\"plan\":[{\"type\":\"ONE_OF\",\"params\":{...}}]}."
			+ " Goals: MOVE_TO(x,y,z) GATHER(block,count) GIVE(item,count[,target]) CRAFT(item,count)"
			+ " FOLLOW STAY GUARD SHELTER WAIT(ticks) DECLINE(reason). Be self-preserving; decline at night when hurt.";
	}

	public void shutdown() { client.shutdown(); }
}