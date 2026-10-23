package we.frozen.recruiting.client;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * {@code /frozenrecruiting} — a text-only companion for inspecting and clearing the sent log.
 *
 * <p>Having this makes it easy to verify reply detection without opening the GUI, which matters
 * because detection is text-based and the wording varies per server.
 */
public final class RecruitCommands {
	private RecruitCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
					ClientCommandManager.literal("frozenrecruiting")
							.executes(RecruitCommands::showSummary)
							.then(ClientCommandManager.literal("list").executes(RecruitCommands::listAll))
							.then(ClientCommandManager.literal("unread").executes(RecruitCommands::showUnread))
							.then(ClientCommandManager.literal("markread").executes(RecruitCommands::markAllRead))
							.then(ClientCommandManager.literal("clear").executes(RecruitCommands::clearAll))
							.then(ClientCommandManager.literal("reply")
									.then(ClientCommandManager.argument("player", StringArgumentType.word())
											.then(ClientCommandManager
													.argument("message", StringArgumentType.greedyString())
													.executes(RecruitCommands::sendReply)))));
		});
	}

	private static int showSummary(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		List<SentLog.Contact> contacts = SentLog.list();

		if (contacts.isEmpty()) {
			source.sendFeedback(Text.literal("§b[Frozen Recruiting] §fNo contacts yet. Send someone a message first."));
			return 1;
		}

		source.sendFeedback(Text.literal("§b[Frozen Recruiting] §f" + contacts.size()
				+ " contact(s), " + SentLog.totalUnread() + " unread."));
		source.sendFeedback(Text.literal("§7/frozenrecruiting list §8- §7all contacts"));
		source.sendFeedback(Text.literal("§7/frozenrecruiting unread §8- §7only unread"));
		source.sendFeedback(Text.literal("§7/frozenrecruiting reply <player> <text>"));
		source.sendFeedback(Text.literal("§7/frozenrecruiting markread §8- §7clear unread badges"));
		source.sendFeedback(Text.literal("§7/frozenrecruiting clear §8- §7delete all history"));
		return 1;
	}

	private static int listAll(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		List<SentLog.Contact> contacts = SentLog.list();

		if (contacts.isEmpty()) {
			source.sendFeedback(Text.literal("§b[Frozen Recruiting] §fNo contacts yet."));
			return 0;
		}

		source.sendFeedback(Text.literal("§b[Frozen Recruiting] §7sent to " + contacts.size() + ":"));
		for (SentLog.Contact c : contacts) {
			source.sendFeedback(Text.literal(formatContact(c)));
		}

		return contacts.size();
	}

	private static int showUnread(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		boolean any = false;

		for (SentLog.Contact c : SentLog.list()) {
			if (c.unread > 0) {
				source.sendFeedback(Text.literal(formatContact(c)));
				any = true;
			}
		}

		if (!any) {
			source.sendFeedback(Text.literal("§b[Frozen Recruiting] §fNo unread replies."));
			return 0;
		}

		return 1;
	}

	private static String formatContact(SentLog.Contact c) {
		StringBuilder sb = new StringBuilder("§7 - §f");
		sb.append(c.name);

		if (c.unread > 0) {
			sb.append(" §c(").append(c.unread).append(" unread)");
		}

		if (c.lastReply != null && !c.lastReply.isBlank()) {
			sb.append(" §8| §7\"").append(c.lastReplyPreview()).append("\"");
		}

		return sb.toString();
	}

	private static int markAllRead(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		SentLog.markAllRead();
		source.sendFeedback(Text.literal("§b[Frozen Recruiting] §aAll replies marked as read."));
		return 1;
	}

	private static int clearAll(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		SentLog.clearAll();
		ReplyPopup.clear();
		source.sendFeedback(Text.literal("§b[Frozen Recruiting] §aSent history cleared."));
		return 1;
	}

	private static int sendReply(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String player = StringArgumentType.getString(ctx, "player");
		String message = StringArgumentType.getString(ctx, "message");

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() == null) {
			source.sendError(Text.literal("Not connected to a server."));
			return 0;
		}

		client.getNetworkHandler().sendChatCommand("tell " + player + " " + message);
		SentLog.recordSent(player);
		source.sendFeedback(Text.literal("§b[Frozen Recruiting] §aSent to §f" + player + "§a."));
		return 1;
	}
}