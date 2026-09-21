package dev.mcc.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7: pins the exit of the payload/validator wiring that the mock relies
 * on — the nested lastMessage.intent shape and the mock's intent extraction.
 * Cold (pure string/JSON manipulation).
 */
class WorldStateSchemaTest {

	@Test void mockExtractsIntentFromPayload() {
		// simulate what WorldStateBuilder produces for a GIVE_REQUEST
		String payload = "{\"trigger\":\"PLAYER_MESSAGE\",\"lastMessage\":{\"intent\":{\"type\":\"GIVE_REQUEST\",\"item\":\"oak_log\",\"count\":5}}}";
		MockLlmClient client = MockLlmClient.of("{\"rules\":[{\"whenIntent\":\"GIVE_REQUEST\",\"respond\":{\"say\":\"ok\",\"plan\":[]}}],\"default\":{\"say\":\"dflt\",\"plan\":[]}}");
		// complete() resolves via the mock rule matcher — String user contains "PAYLOAD <json>"
		String reply = client.complete("sys", "PAYLOAD " + payload).join();
		JsonObject r = JsonParser.parseString(reply).getAsJsonObject();
		assertEquals("ok", r.get("say").getAsString());
	}

	@Test void mockDefaultWhenIntentUnknown() {
		MockLlmClient client = MockLlmClient.of("{\"default\":{\"say\":\"dflt\",\"plan\":[]}}");
		String payload = "{\"trigger\":\"PLAYER_MESSAGE\",\"lastMessage\":{\"intent\":{\"type\":\"CONVERSATION\",\"item\":\"\",\"count\":1}}}";
		String reply = client.complete("sys", "PAYLOAD " + payload).join();
		assertEquals("dflt", JsonParser.parseString(reply).getAsJsonObject().get("say").getAsString());
	}

	@Test void validatorAcceptsNestedIntentPayloadShape() {
		// the plan's decision JSON — validator must accept GATHER+GIVE
		DecisionValidator.Resolver r = new DecisionValidator.Resolver() {
			public boolean itemExists(String id) { return id.equals("minecraft:oak_log"); }
			public boolean blockExists(String id) { return id.equals("minecraft:oak_log"); }
			public boolean tagExists(String id) { return false; }
		};
		String llm = "{\"say\":\"On it\",\"plan\":[{\"type\":\"GATHER\",\"params\":{\"block\":\"minecraft:oak_log\",\"count\":5}},{\"type\":\"GIVE\",\"params\":{\"item\":\"minecraft:oak_log\",\"count\":5}}]}";
		var v = DecisionValidator.validate(llm, DecisionValidator.Context.of(r)).orElseThrow();
		assertEquals(2, v.plan().size());
		assertEquals(dev.mcc.goal.GoalType.GATHER, v.plan().get(0).type());
		assertEquals(dev.mcc.goal.GoalType.GIVE, v.plan().get(1).type());
	}
}