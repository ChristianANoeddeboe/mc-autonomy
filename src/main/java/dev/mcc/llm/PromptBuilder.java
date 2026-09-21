package dev.mcc.llm;

import java.util.Map;

/**
 * Pure prompt interpolation: substitutes {slot} placeholders from a map.
 * Personality lives ONLY in the personality.txt file; this just fills slots.
 */
public final class PromptBuilder {
	private PromptBuilder() {}

	/** Replaces every {key} present in the map; leaves unknown slots literal. */
	public static String apply(String template, Map<String, String> slots) {
		String result = template;
		for (Map.Entry<String, String> e : slots.entrySet()) {
			result = result.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
		}
		return result;
	}

	/** Convenience: build a small map from the given slots. */
	public static Map<String, String> slots(String... kv) {
		java.util.Map<String, String> m = new java.util.HashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
		return m;
	}
}