package we.frozen.recruiting.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Persistent record of everyone you have sent a recruit message to, plus their last reply.
 *
 * <p>Stored as JSON in {@code config/frozen-recruiting.json}. Writes are debounced by the caller
 * and always go through a temp file so a crash mid-write cannot corrupt the existing log.
 */
public final class SentLog {
	/** Keep the history bounded so the file and the Sent tab cannot grow without limit. */
	private static final int MAX_CONTACTS = 100;

	/** One player's history. */
	public static final class Contact {
		public String name = "";
		public long lastSentAt = 0L;
		public long lastReplyAt = 0L;
		public String lastReply = "";
		public int unread = 0;

		public Contact() {
		}

		public Contact(String name) {
			this.name = name;
		}

		public String lastReplyPreview() {
			if (lastReply == null || lastReply.isBlank()) {
				return "";
			}

			return lastReply.length() <= 60 ? lastReply : lastReply.substring(0, 57) + "...";
		}
	}

	private static final Map<String, Contact> CONTACTS = new LinkedHashMap<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean loaded = false;

	private SentLog() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("frozen-recruiting.json");
	}

	private static String key(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	/** Loads the log from disk once. Safe to call from anywhere. */
	public static synchronized void load() {
		if (loaded) {
			return;
		}

		loaded = true;
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			List<Contact> loaded_ = GSON.fromJson(reader, new TypeToken<List<Contact>>() { }.getType());
			if (loaded_ != null) {
				for (Contact c : loaded_) {
					if (c != null && c.name != null && !c.name.isBlank()) {
						CONTACTS.put(key(c.name), c);
					}
				}
			}
		} catch (Exception e) {
			// A corrupt log must never prevent the game from loading the mod.
			CONTACTS.clear();
		}
	}

	/** Writes the log to disk atomically. */
	public static synchronized void save() {
		Path path = file();

		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

			try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(new ArrayList<>(CONTACTS.values()), writer);
			}

			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			// Persistence is best-effort; never crash the client over it.
		}
	}

	/** Records that we just sent a message to {@code name}. */
	public static synchronized void recordSent(String name) {
		if (name == null || name.isBlank()) {
			return;
		}

		load();
		Contact c = CONTACTS.get(key(name));
		if (c == null) {
			c = new Contact(name.trim());
			CONTACTS.put(key(name.trim().toLowerCase(Locale.ROOT)), c);
		}

		c.name = name.trim();
		c.lastSentAt = System.currentTimeMillis();
		save();
	}

	/**
	 * Records a reply received from {@code name}.
	 *
	 * @return true when the sender was already a tracked contact
	 */
	public static synchronized boolean recordReply(String name, String reply) {
		if (name == null || name.isBlank()) {
			return false;
		}

		load();
		Contact c = CONTACTS.get(key(name));
		if (c == null) {
			return false;
		}

		c.lastReply = reply == null ? "" : reply.trim();
		c.lastReplyAt = System.currentTimeMillis();
		c.unread++;
		save();
		return true;
	}

	/** Formats a timestamp as a compact relative time, e.g. "now", "5m", "3h", "2d". */
	public static String formatTime(long millis) {
		if (millis <= 0L) {
			return "";
		}

		long delta = System.currentTimeMillis() - millis;
		if (delta < 0) {
			return "now";
		}

		long seconds = delta / 1000L;
		if (seconds < 60) {
			return "now";
		}

		long minutes = seconds / 60L;
		if (minutes < 60) {
			return minutes + "m";
		}

		long hours = minutes / 60L;
		if (hours < 24) {
			return hours + "h";
		}

		long days = hours / 24L;
		if (days < 7) {
			return days + "d";
		}

		return (days / 7) + "w";
	}

	/** Contacts ordered by most recent activity (reply first, then send). */
	public static synchronized List<Contact> list() {
		load();
		List<Contact> out = new ArrayList<>(CONTACTS.values());
		out.sort(Comparator.comparingLong((Contact c) -> Math.max(c.lastReplyAt, c.lastSentAt)).reversed());
		return out;
	}

	public static synchronized int totalUnread() {
		load();
		int n = 0;
		for (Contact c : CONTACTS.values()) {
			n += c.unread;
		}

		return n;
	}

	/**
	 * Given a possibly decorated sender name like {@code "[MVP] Steve"}, finds the matching
	 * tracked contact name, or null if there is no plausible match.
	 *
	 * <p>This is deliberately conservative: rather than stripping brackets and hoping (which
	 * would turn a player named "Bob" in team "[Team Red]" into a guess), we only accept a
	 * candidate if it exactly matches someone we have actually messaged.
	 */
	public static synchronized String resolveDecoratedName(String decorated) {
		load();
		if (decorated == null || decorated.isBlank()) {
			return null;
		}

		String trimmed = decorated.trim();

		// Exact match first.
		Contact exact = CONTACTS.get(key(trimmed));
		if (exact != null) {
			return exact.name;
		}

		// Try removing a leading bracketed decoration: "[MVP] Steve" -> "Steve".
		int close = trimmed.indexOf(']');
		if (trimmed.startsWith("[") && close > 0) {
			String candidate = trimmed.substring(close + 1).trim();
			Contact c = CONTACTS.get(key(candidate));
			if (c != null) {
				return c.name;
			}
		}

		// Try removing leading chat arrows: ">> Steve" -> "Steve".
		String arrows = trimmed;
		while (arrows.startsWith(">")) {
			arrows = arrows.substring(1).trim();
		}

		Contact a = CONTACTS.get(key(arrows));
		if (a != null) {
			return a.name;
		}

		return null;
	}

	public static synchronized void markRead(Contact contact) {
		if (contact != null && contact.unread != 0) {
			contact.unread = 0;
			save();
		}
	}

	public static synchronized void markAllRead() {
		load();
		for (Contact c : CONTACTS.values()) {
			c.unread = 0;
		}

		save();
	}

	public static synchronized void clearAll() {
		CONTACTS.clear();
		save();
	}
}