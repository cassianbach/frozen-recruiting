package we.frozen.recruiting.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Small helper for pushing short status lines into the client chat. */
public final class ChatFeedback {
	private static final String PREFIX = "§b[§3Frozen§b]§r ";

	private ChatFeedback() {
	}

	public static void success(MinecraftClient client, String key, Object... args) {
		send(client, "§a" + Text.translatable(key, args).getString());
	}

	public static void error(MinecraftClient client, String key, Object... args) {
		send(client, "§c" + Text.translatable(key, args).getString());
	}

	public static void info(MinecraftClient client, String key, Object... args) {
		send(client, Text.translatable(key, args).getString());
	}

	private static void send(MinecraftClient client, String message) {
		if (client == null || client.inGameHud == null) {
			return;
		}

		client.inGameHud.getChatHud().addMessage(Text.literal(PREFIX + message));
	}
}