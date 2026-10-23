package we.frozen.recruiting.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

/**
 * Holds the currently composed recruit message and sends it to a target player.
 *
 * <p>Sending goes through {@link ClientPlayNetworkHandler#sendChatCommand(String)}, which expects the
 * command <i>without</i> a leading slash. This produces a {@code CommandExecutionC2SPacket} that
 * the server handles identically to a player typing {@code /tell ...}, so it is not affected by
 * chat plugins that intercept the chat-message packet path.
 */
public final class RecruitMessageStore {
	private static String message = null;

	private RecruitMessageStore() {
	}

	public static String get() {
		return message;
	}

	public static boolean isArmed() {
		return message != null && !message.isBlank();
	}

	public static void set(String newMessage) {
		String trimmed = newMessage == null ? null : newMessage.trim();
		message = (trimmed == null || trimmed.isEmpty()) ? null : trimmed;
	}

	public static void clear() {
		message = null;
	}

	/**
	 * Sends the armed message to {@code targetName} as {@code /tell <target> <message>}.
	 *
	 * @return true when the command was dispatched to the server
	 */
	public static boolean sendTo(String targetName) {
		if (!isArmed()) {
			return false;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (targetName == null || targetName.isBlank()) {
			ChatFeedback.error(client, "frozen_recruiting.msg.unnamed");
			return false;
		}

		ClientPlayNetworkHandler handler = client.getNetworkHandler();
		if (handler == null) {
			ChatFeedback.error(client, "frozen_recruiting.msg.not_server");
			return false;
		}

		// No leading slash: sendChatCommand builds the CommandExecutionC2SPacket itself.
		handler.sendChatCommand("tell " + targetName + " " + message);
		SentLog.recordSent(targetName);
		ChatFeedback.success(client, "frozen_recruiting.msg.sent", targetName);
		return true;
	}
}