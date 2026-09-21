package dev.mcc.llm;

import dev.mcc.goal.GoalType;
import dev.mcc.llm.DecisionValidator.Context;
import dev.mcc.llm.DecisionValidator.Resolver;
import dev.mcc.llm.DecisionValidator.Validated;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 6: pure decision-JSON validation. Cold — no MC classes. */
class DecisionValidatorTest {

	// resolver that knows a handful of ids
	static final Resolver R = new Resolver() {
		@Override public boolean itemExists(String id) { return Set.of("minecraft:oak_log","minecraft:stick","minecraft:torch","minecraft:raw_iron").contains(id); }
		@Override public boolean blockExists(String id) { return Set.of("minecraft:oak_log","minecraft:iron_ore").contains(id); }
		@Override public boolean tagExists(String id) { return false; }
	};

	private static Validated v(String llm) { return DecisionValidator.validate(llm, Context.of(R)).orElseThrow(); }

	@Test void validJsonStrippedAndParsed() {
		Validated vd = v("```json\n{\"say\":\"ok\",\"plan\":[{\"type\":\"GATHER\",\"params\":{\"block\":\"minecraft:oak_log\",\"count\":5}}]}\n```");
		assertEquals("ok", vd.say());
		assertEquals(1, vd.plan().size());
		assertEquals(GoalType.GATHER, vd.plan().get(0).type());
		assertEquals(5, vd.plan().get(0).intParam("count", 0));
	}

	@Test void clampsNegativeCount() {
		Validated vd = v("{\"say\":\"\",\"plan\":[{\"type\":\"GIVE\",\"params\":{\"item\":\"minecraft:oak_log\",\"count\":-5}}]}");
		assertEquals(1, vd.plan().size());
		assertEquals(1, vd.plan().get(0).intParam("count", 0));
	}

	@Test void clampsTooLargeCountToCap() {
		Validated vd = v("{\"say\":\"\",\"plan\":[{\"type\":\"GIVE\",\"params\":{\"item\":\"minecraft:oak_log\",\"count\":995}}]}");
		assertEquals(64, vd.plan().get(0).intParam("count", 0));
	}

	@Test void unknownTypeDropped() {
		Validated vd = v("{\"plan\":[{\"type\":\"TEAPARTY\",\"params\":{}},{\"type\":\"WAIT\",\"params\":{\"ticks\":50}}]}");
		assertEquals(1, vd.plan().size());
		assertEquals(GoalType.WAIT, vd.plan().get(0).type());
	}

	@Test void twelveElementPlanTruncatedToMax() {
		// 10 WAITs -> cap at 4 (maxPlanLength)
		StringBuilder sb = new StringBuilder("{\"plan\":[");
		for (int i = 0; i < 10; i++) sb.append("{\"type\":\"WAIT\",\"params\":{\"ticks\":50}},");
		sb.setLength(sb.length() - 1);
		sb.append("]}");
		Validated vd = v(sb.toString());
		assertEquals(4, vd.plan().size());
	}

	@Test void declineEnforcedFirst() {
		Validated vd = v("{\"plan\":[{\"type\":\"GATHER\",\"params\":{\"block\":\"minecraft:oak_log\",\"count\":1}},{\"type\":\"DECLINE\",\"params\":{\"reason\":\"night\"}}]}");
		// decline not first -> the non-decline goals stand, decline dropped
		assertEquals(1, vd.plan().size());
		assertEquals(GoalType.GATHER, vd.plan().get(0).type());
	}

	@Test void emptyPlanAndSayReturnsEmpty() {
		assertTrue(DecisionValidator.validate("{\"say\":\"\",\"plan\":[]}", Context.of(R)).isEmpty());
		assertTrue(DecisionValidator.validate("just prose no json", Context.of(R)).isEmpty());
		assertTrue(DecisionValidator.validate("", Context.of(R)).isEmpty());
	}

	@Test void proseBeforeJsonStripped() {
		Validated vd = DecisionValidator.validate("Hmm, let me think. {\"say\":\"hi\",\"plan\":[{\"type\":\"FOLLOW\"}]} trailing words", Context.of(R)).orElseThrow();
		assertEquals("hi", vd.say());
	}

	@Test void unknownItemGivesWarningAndDrops() {
		Validated vd = DecisionValidator.validate("{\"plan\":[{\"type\":\"GIVE\",\"params\":{\"item\":\"minecraft:netherite_sword\",\"count\":1}}]}", Context.of(R)).orElseThrow();
		assertEquals(0, vd.plan().size());
		assertTrue(vd.warnings().stream().anyMatch(w -> w.contains("unknown")));
	}

	@Test void missingRequiredParamDrops() {
		Validated vd = DecisionValidator.validate("{\"plan\":[{\"type\":\"GATHER\",\"params\":{\"count\":3}}]}", Context.of(R)).orElseThrow();
		assertEquals(0, vd.plan().size());
	}
}