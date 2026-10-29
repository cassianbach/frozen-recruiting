package we.frozen.recruiting.client;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

/**
 * Watches incoming chat for private replies to messages sent by this mod.
 *
 * <h2>Why this parses text</h2>
 * Fabric's {@link ClientReceiveMessageEvents.GAME} hands us a fully formatted {@link Text} and a
 * boolean. The sender UUID that exists on {@code ChatMessageS2CPacket#sender()} has already been
 * discarded by the time the event fires, so there is no structured way to ask "who sent this?".
 * The only signal available is the rendered string, so replies are matched by pattern.
 */
public final class ReplyWatcher {
	/**
	 * Private-message prefixes, tried in order.
	 *
	 * <p>Stray (and several other server networks) render a private message as
	 * {@code "[Eviority_ -> You] hey join us"}. Vanilla uses {@code "%s whispers to you: %s"} and
	 * other plugins use variants like {@code "%s tells you: %s"}. All of them are tried so the
	 * detector works across the servers the user plays on.
	 *
	 * <p>Group 1 is the sender name, group 2 is the message body.
	 */
	private static final Pattern[] WHISPER_PATTERNS = {
			// Stray: "[Eviority_ -> You] hey join us"
			Pattern.compile("^\\[\\s*(.+?)\\s*->\\s*You\\s*\\]\\s*(.*)$", Pattern.CASE_INSENSITIVE),
			// Vanilla and friends: "Steve whispers to you: hey" / "Steve whispered to you: hey"
			Pattern.compile("^(.+?)\\s+whisper(?:s|ed|ing)?\\s+to\\s+you:?\\s+(.*)$", Pattern.CASE_INSENSITIVE),
			// Some server plugins: "Steve tells you: hey"
			Pattern.compile("^(.+?)\\s+tells?\\s+you:?\\s+(.*)$", Pattern.CASE_INSENSITIVE),
			// Compact plugin formatting: "-> Steve: hello"
			Pattern.compile("^\\[?->\\]?\\s*(.+?):\\s+(.*)$", Pattern.CASE_INSENSITIVE),
	};

	/** Legacy section colour code, e.g. {@code \u00a7a}. */
	private static final char SECTION_SIGN = '\u00a7';

	private ReplyWatcher() {
	}

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message));
	}

	private static void onMessage(Text message) {
		if (message == null) {
			return;
		}

		// Overlay messages are system/actionbar text, never player whispers.
		String plain = stripFormatting(message.getString()).trim();
		if (plain.isEmpty()) {
			return;
		}

		Match match = parse(plain);
		if (match == null) {
			return;
		}

		String name = match.name().trim();
		String body = match.body().trim();

		if (name.isEmpty()) {
			return;
		}

		// Only surface replies from people we actually messaged; this keeps unrelated
		// player-to-player chat from polluting the popup queue.
		if (!SentLog.recordReply(name, body)) {
			// Some servers prepend a rank, e.g. "[MVP] Steve whispers to you: hi". Try the
			// decorated name against the tracked list before giving up, rather than
			// stripping brackets blindly (which would mangle names like "Team Red").
			String resolved = SentLog.resolveDecoratedName(name);
			if (resolved == null || !SentLog.recordReply(resolved, body)) {
				return;
			}

			name = resolved;
		}

		ReplyPopup.enqueue(name, body);
	}

	/** Parsed sender and body from a chat line. */
	public record Match(String name, String body) {
	}

	/** Attempts to read a private-message reply out of a plain chat line. */
	public static Match parse(String plain) {
		if (plain == null || plain.isEmpty()) {
			return null;
		}

		for (Pattern pattern : WHISPER_PATTERNS) {
			Matcher m = pattern.matcher(plain);
			if (m.matches()) {
				String name = m.group(1).trim();
				String body = m.group(2);

				// Strip decoration some servers add, e.g. "[MVP] Steve".
				name = stripDecorations(name);
				if (!name.isEmpty() && !body.isBlank()) {
					return new Match(name, body.trim());
				}
			}
		}

		return null;
	}

	/** Removes {@code \u00a7x} colour codes so formatting cannot break the match. */
	public static String stripFormatting(String input) {
		if (input == null || input.indexOf(SECTION_SIGN) < 0) {
			return input == null ? "" : input;
		}

		StringBuilder sb = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == SECTION_SIGN && i + 1 < input.length()) {
				// Skip the section sign and the following format character.
				i++;
				continue;
			}

			sb.append(c);
		}

		return sb.toString();
	}

	/** Trims a trailing/leading bracket decoration only if it leaves a name we recognise. */
	public static String stripDecorations(String name) {
		String out = name == null ? "" : name.trim();

		// ">> Steve" or "> Steve" style chat arrows.
		while (out.startsWith(">")) {
			out = out.substring(1).trim();
		}

		return out.trim();
	}
}