package dev.mcc.chat;

import dev.mcc.chat.ChatParser.Intent;
import dev.mcc.chat.ChatParser.IntentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 3 gate: chat intent parsing. Pure Java — no MC boot. */
class ChatParserTest {

	@BeforeAll
	static void defaults() {
		dev.mcc.config.CompanionConfig cf = dev.mcc.config.CompanionConfig.get();
		cf.chat.prefixes = new String[]{"companion:", "comp:"};
		cf.chat.name = "Companion";
	}

	private static Intent parse(String s) { return ChatParser.parse(s); }
	private static void assertIntent(IntentType t, String item, int count, String phrase) {
		Intent i = parse(phrase);
		assertEquals(t, i.type(), "type for '" + phrase + "'");
		assertEquals(item, i.itemName(), "item for '" + phrase + "'");
		assertEquals(count, i.count(), "count for '" + phrase + "'");
	}

	@Test void giveRequests() {
		assertIntent(IntentType.GIVE_REQUEST, "oak_log", 5, "give me 5 oak logs");
		assertIntent(IntentType.GIVE_REQUEST, "bread", 1, "hand me a bread");
		assertIntent(IntentType.GIVE_REQUEST, "stick", 8, "give me some sticks");
		assertIntent(IntentType.GIVE_REQUEST, "bone", 1, "drop me a bone");
		assertIntent(IntentType.GIVE_REQUEST, "sword", 1, "pass me a sword");
	}

	@Test void giveAlternativeForms() {
		assertIntent(IntentType.GIVE_REQUEST, "raw_iron", 1, "can i have raw iron");
		assertIntent(IntentType.GIVE_REQUEST, "oak_log", 1, "can you give me an oak log");
		assertIntent(IntentType.GIVE_REQUEST, "diamond", 1, "could you give me a diamond");
	}

	@Test void gatherRequests() {
		assertIntent(IntentType.GATHER_REQUEST, "iron_ore", 10, "mine me 10 iron ore");
		assertIntent(IntentType.GATHER_REQUEST, "oak_log", 8, "gather some oak logs");
		assertIntent(IntentType.GATHER_REQUEST, "coal", 8, "dig me some coal");
		assertIntent(IntentType.GATHER_REQUEST, "iron_ore", 1, "fetch iron ore");
		assertIntent(IntentType.GATHER_REQUEST, "gold_ore", 3, "chop me 3 gold ore");
		assertIntent(IntentType.GATHER_REQUEST, "diamond_ore", 1, "collect a diamond ore");
	}

	@Test void craftRequests() {
		// items are normalized (trailing-s stripped, spaces->underscore); the
		// alias/registry resolution (planks->oak_planks) happens in ItemResolver
		assertIntent(IntentType.CRAFT_REQUEST, "oak_plank", 4, "make me 4 oak planks");
		assertIntent(IntentType.CRAFT_REQUEST, "stick", 1, "craft me a stick");
		assertIntent(IntentType.CRAFT_REQUEST, "torch", 1, "make a torch");
	}

	@Test void behaviourIntents() {
		assertEquals(IntentType.FOLLOW, parse("follow me").type());
		assertEquals(IntentType.FOLLOW, parse("come with me").type());
		assertEquals(IntentType.COME, parse("come here").type());
		assertEquals(IntentType.COME, parse("come to me").type());
		assertEquals(IntentType.STAY, parse("stay").type());
		assertEquals(IntentType.STAY, parse("wait").type());
		assertEquals(IntentType.STAY, parse("hold position").type());
		assertEquals(IntentType.GUARD, parse("guard here").type());
		assertEquals(IntentType.GUARD, parse("protect me").type());
		assertEquals(IntentType.STOP, parse("stop").type());
		assertEquals(IntentType.STOP, parse("never mind").type());
	}

	@Test void offerAndStatus() {
		assertEquals(IntentType.OFFER, parse("here").type());
		assertEquals(IntentType.OFFER, parse("take this bread").type());
		assertEquals(IntentType.OFFER, parse("i brought you some food").type());
		assertEquals(IntentType.STATUS_QUERY, parse("status").type());
		assertEquals(IntentType.STATUS_QUERY, parse("what are you doing").type());
		assertEquals(IntentType.STATUS_QUERY, parse("what do you have").type());
	}

	@Test void conversationFallback() {
		assertEquals(IntentType.CONVERSATION, parse("hello there").type());
		assertEquals(IntentType.CONVERSATION, parse("tell me a story").type());
		assertEquals(IntentType.CONVERSATION, parse("are you ok").type());
	}

	// ---- addressing ---------------------------------------------------------

	@Test void addressing() {
		assertTrue(ChatParser.isAddressed("companion: give me 5 oak logs"));
		assertTrue(ChatParser.isAddressed("comp: hello"));
		assertTrue(ChatParser.isAddressed("Companion: hey"));
		assertFalse(ChatParser.isAddressed("give me wood"));
		assertFalse(ChatParser.isAddressed("just chatting"));
	}

	@Test void stripping() {
		assertEquals("give me 5 oak logs", ChatParser.strip("companion: give me 5 oak logs"));
		assertEquals("hello", ChatParser.strip("Comp: hello"));
		// not addressed -> returns input unchanged
		assertEquals("hi", ChatParser.strip("hi"));
	}

	@Test void itemNormalization() {
		assertEquals("oak_log", ChatParser.normalizeItem("oak logs"));
		assertEquals("oak_log", ChatParser.normalizeItem("Oak Logs"));
		assertEquals("iron_ore", ChatParser.normalizeItem("iron ore"));
		assertEquals("glass", ChatParser.normalizeItem("glass")); // s preserved (ends ss? no—use ss guard)
		assertEquals("bonus", ChatParser.normalizeItem("bonus")); // ends us, preserved
	}

	@Test void caseInsensitive() {
		assertTrue(ChatParser.isAddressed("COMPANION: STATUS"));
		assertEquals(IntentType.STATUS_QUERY, parse("STATUS").type());
	}
}