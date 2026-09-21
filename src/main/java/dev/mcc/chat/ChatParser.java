package dev.mcc.chat;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure intent parser — no MC classes, fully unit-testable cold. Given a
 * stripped (non-addressed) command string it decides the IntentType and extracts
 * item/count/extra. First matching rule wins (case-insensitive).
 *
 * This is the *advisory* intent fed to the LLM and the *authoritative* intent
 * in OFFLINE mode.
 */
public final class ChatParser {
	public enum IntentType {
		GIVE_REQUEST, GATHER_REQUEST, CRAFT_REQUEST,
		FOLLOW, STAY, GUARD, COME, OFFER, STATUS_QUERY, STOP, CONVERSATION
	}

	public record Intent(IntentType type, String itemName, int count, String extra) {
		public static Intent of(IntentType type) { return new Intent(type, "", 1, ""); }
		public static Intent conversation() { return new Intent(IntentType.CONVERSATION, "", 1, ""); }
	}

	private static final Pattern COUNT = Pattern.compile("^(\\d+|an|a|some)\\b", Pattern.CASE_INSENSITIVE);

	// (\\d+|an|a|some) count group, default 1; "some" -> 8
	private static final Pattern GIVE = Pattern.compile("^(?:can|could)?\\s*(give|hand|pass|toss|drop|bring)\\s*(?:me)?\\s*(\\d+|an|a|some)?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern GIVE_ALT = Pattern.compile("^(?:can|could)(?:\\s+i|\\s+you)?\\s+(?:have|give me)\\s*(\\d+|an|a|some)?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
		private static final Pattern GATHER = Pattern.compile("^(mine|gather|collect|chop|dig|get|fetch)\\s*(?:me|us)?\\s*(\\d+|an|a|some)?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
		private static final Pattern CRAFT = Pattern.compile("^(make me|build me|make|craft)\\s*(?:me)?\\s*(\\d+|an|a|some)?\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern FOLLOW = Pattern.compile("^(follow(?: me)?|come with me)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern COME = Pattern.compile("^(come(?: here)?|come to me)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern STAY = Pattern.compile("^(stay|wait|stop here|hold position)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern GUARD = Pattern.compile("^(guard|protect|defend)(?: here| me)?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern STOP = Pattern.compile("^(stop|cancel|never\\s*mind)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern OFFER = Pattern.compile("^(here|take this|i (?:have|brought|dropped)|for you)", Pattern.CASE_INSENSITIVE);
	private static final Pattern STATUS = Pattern.compile("^(what are you doing|status|how are you|what do you have)$", Pattern.CASE_INSENSITIVE);

	private ChatParser() {}

	/** True if the raw chat message is addressed to the companion (prefix or name:). */
	public static boolean isAddressed(String raw) {
		String lower = raw.trim().toLowerCase(Locale.ROOT);
		for (String prefix : dev.mcc.config.CompanionConfig.get().chat.prefixes) {
			if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) return true;
		}
		String name = dev.mcc.config.CompanionConfig.get().chat.name;
		return !name.isEmpty() && lower.startsWith(name.toLowerCase(Locale.ROOT) + ":");
	}

	/** Strip the addressing prefix/name from a raw message. */
	public static String strip(String raw) {
		String s = raw.trim();
		String lower = s.toLowerCase(Locale.ROOT);
		for (String prefix : dev.mcc.config.CompanionConfig.get().chat.prefixes) {
			if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
				return s.substring(prefix.length()).trim();
			}
		}
		String name = dev.mcc.config.CompanionConfig.get().chat.name;
		if (!name.isEmpty() && lower.startsWith(name.toLowerCase(Locale.ROOT) + ":")) {
			return s.substring(name.length() + 1).trim();
		}
		return s;
	}

	public static Intent parse(String text) {
		String t = normalize(text);

		// give me 5 oak logs / hand me some bread
		Matcher give = GIVE.matcher(t);
		if (give.matches()) {
			String item = give.group(3);
			if (item == null || item.isEmpty()) {
				Matcher alt = GIVE_ALT.matcher(t);
				if (alt.matches()) {
					return intent(IntentType.GIVE_REQUEST, alt.group(2), count(alt.group(1)));
				}
				return Intent.conversation();
			}
			return intent(IntentType.GIVE_REQUEST, item, count(give.group(2)));
		}
		if (GIVE_ALT.matcher(t).matches()) {
			Matcher alt = GIVE_ALT.matcher(t);
			alt.matches();
			return intent(IntentType.GIVE_REQUEST, alt.group(2), count(alt.group(1)));
		}

		Matcher gather = GATHER.matcher(t);
		if (gather.matches()) {
			return intent(IntentType.GATHER_REQUEST, gather.group(3), count(gather.group(2)));
		}

		Matcher craft = CRAFT.matcher(t);
		if (craft.matches()) {
			return intent(IntentType.CRAFT_REQUEST, craft.group(3), count(craft.group(2)));
		}

		if (FOLLOW.matcher(t).matches()) return Intent.of(IntentType.FOLLOW);
		if (COME.matcher(t).matches()) return Intent.of(IntentType.COME);
		if (STAY.matcher(t).matches()) return Intent.of(IntentType.STAY);
		if (GUARD.matcher(t).matches()) return Intent.of(IntentType.GUARD);
		if (STOP.matcher(t).matches()) return Intent.of(IntentType.STOP);
		// OFFER is a prefix (no trailing anchor); find() instead of matches().
		if (OFFER.matcher(t).find()) return Intent.of(IntentType.OFFER);
		if (STATUS.matcher(t).matches()) return Intent.of(IntentType.STATUS_QUERY);

		return Intent.conversation();
	}

	/** Trim punctuation, collapse whitespace, lowercase for regex robustness. */
	static String normalize(String s) {
		return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static Intent intent(IntentType type, String item, int count) {
		return new Intent(type, normalizeItem(item), count, "");
	}

	private static int count(String raw) {
		if (raw == null || raw.isEmpty()) return 1;
		String r = raw.trim().toLowerCase(Locale.ROOT);
		if (r.equals("some")) return 8;
		try {
			return Math.max(1, Integer.parseInt(r));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	/** Normalize a bare item phrase to an id-shaped string: strip trailing s, space->_, use lower. */
	public static String normalizeItem(String phrase) {
		if (phrase == null) return "";
		String p = phrase.trim().toLowerCase(Locale.ROOT);
		p = p.replaceAll("\\s+", "_");
		if (p.endsWith("s") && !p.endsWith("ss") && !p.endsWith("us")) {
			p = p.substring(0, p.length() - 1);
		}
		return p;
	}
}