package we.frozen.recruiting.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Transient top-right toasts that announce incoming replies, with a short click sound.
 *
 * <p>Timing uses {@link System#nanoTime()} rather than {@code RenderTickCounter}, whose accessor
 * names differ between 1.21.1 and 1.21.11. Fade state is timestamp-based so it behaves the same
 * on both versions and does not depend on tick rate.
 */
public final class ReplyPopup {
	private static final int MAX_VISIBLE = 3;
	private static final int TOAST_WIDTH = 220;
	private static final int TOAST_HEIGHT = 44;
	private static final int MARGIN = 6;
	private static final long FADE_OUT_MILLIS = 900L;

	private static final int BG = 0xF20B1526;
	private static final int BORDER = 0xFF3A3D55;
	private static final int ACCENT = 0xFF70E0FF;
	private static final int TITLE = 0xFFEDEFF7;
	private static final int BODY = 0xFFA0A4B8;

	private static final class Entry {
		final String name;
		final String body;
		final long createdAt;
		long soundPlayedAt;

		Entry(String name, String body) {
			this.name = name;
			this.body = body;
			this.createdAt = System.currentTimeMillis();
			this.soundPlayedAt = 0L;
		}

		int elapsedMillis() {
			return (int) (System.currentTimeMillis() - createdAt);
		}

		float alpha() {
			int t = elapsedMillis();
			if (t < 140) {
				// Slide/fade in.
				return Math.min(1f, t / 140f);
			}

			int fadeStart = 4200;
			if (t >= fadeStart + FADE_OUT_MILLIS) {
				return 0f;
			}

			if (t >= fadeStart) {
				return 1f - ((t - fadeStart) / (float) FADE_OUT_MILLIS);
			}

			return 1f;
		}
	}

	private static final Deque<Entry> QUEUE = new ArrayDeque<>();

	private ReplyPopup() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
	}

	public static synchronized void enqueue(String name, String body) {
		QUEUE.addLast(new Entry(name, body));

		// Keep the backlog bounded; drop the oldest so a burst cannot grow forever.
		while (QUEUE.size() > MAX_VISIBLE) {
			QUEUE.pollFirst();
		}
	}

	public static synchronized boolean hasVisible() {
		return !QUEUE.isEmpty();
	}

	public static synchronized void clear() {
		QUEUE.clear();
	}

	private static void render(DrawContext context) {
		if (QUEUE.isEmpty()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null) {
			return;
		}

		playSound(client);

		List<Entry> entries;
		synchronized (ReplyPopup.class) {
			entries = new ArrayList<>(QUEUE);
		}

		int screenWidth = context.getScaledWindowWidth();
		int right = screenWidth - MARGIN;
		int y = MARGIN;

		for (Entry entry : entries) {
			float alpha = entry.alpha();
			if (alpha <= 0f) {
				removeIfDead(entry);
				continue;
			}

			int left = right - TOAST_WIDTH;

			context.fill(left, y, right, y + TOAST_HEIGHT, BG);
			context.fill(left, y, right, y + 1, ACCENT);
			context.fill(left, y + TOAST_HEIGHT - 1, right, y + TOAST_HEIGHT, BORDER);

			Text title = Text.literal(entry.name + " replied");
			int titleY = y + 7;

			// Shrink the body line so it never overflows the toast.
			int maxWidth = TOAST_WIDTH - 16;
			String body = entry.body.length() <= 60
					? entry.body
					: entry.body.substring(0, 57) + "...";
			body = client.textRenderer.trimToWidth(body, maxWidth);

			context.drawText(client.textRenderer, title, left + 8, titleY, TITLE, false);
			context.drawText(client.textRenderer, body, left + 8, titleY + 12, BODY, false);

			y += TOAST_HEIGHT + 4;
		}
	}

	private static void playSound(MinecraftClient client) {
		if (client.getSoundManager() == null) {
			return;
		}

		Entry target = null;
		synchronized (ReplyPopup.class) {
			for (Entry e : QUEUE) {
				if (e.soundPlayedAt == 0L) {
					target = e;
					break;
				}
			}

			if (target != null) {
				target.soundPlayedAt = System.currentTimeMillis();
			}
		}

		if (target == null) {
			return;
		}

		try {
			// UI_BUTTON_CLICK is a RegistryEntry.Reference<SoundEvent> on 1.21.x, so the
			// RegistryEntry overload is required.
			client.getSoundManager()
					.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
		} catch (Throwable ignored) {
			// A missing sound must never break the HUD.
		}
	}

	private static void removeIfDead(Entry entry) {
		synchronized (ReplyPopup.class) {
			QUEUE.remove(entry);
		}
	}
}