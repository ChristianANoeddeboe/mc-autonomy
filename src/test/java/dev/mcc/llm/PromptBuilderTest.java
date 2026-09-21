package dev.mcc.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 6: prompt slot interpolation. Cold. */
class PromptBuilderTest {

	@Test void replacesKnownSlots() {
		String out = PromptBuilder.apply("You are {name}. Health {health}/{maxHealth}.", PromptBuilder.slots("name","Companion","health","18","maxHealth","20"));
		assertEquals("You are Companion. Health 18/20.", out);
	}

	@Test void leavesUnknownSlotsLiteral() {
		String out = PromptBuilder.apply("gap {unknown_slot} end", PromptBuilder.slots("name","X"));
		assertEquals("gap {unknown_slot} end", out);
	}

	@Test void nullSlotBecomesEmpty() {
		String out = PromptBuilder.apply("a{goal}b", PromptBuilder.slots("goal", null));
		assertEquals("ab", out);
	}

	@Test void repeatedReplacement() {
		String out = PromptBuilder.apply("{name} and {name}", PromptBuilder.slots("name","Bob"));
		assertEquals("Bob and Bob", out);
	}
}