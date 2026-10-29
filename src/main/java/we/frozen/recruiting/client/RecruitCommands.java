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
			source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7fNo contacts yet. Send someone a message first."));
			return 1;
		}

		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7f" + contacts.size()
				+ " contact(s), " + SentLog.totalUnread() + " unread."));
		source.sendFeedback(TextHelper.parse("\u00a77/frozenrecruiting list \u00a78- \u00a77all contacts"));
		source.sendFeedback(TextHelper.parse("\u00a77/frozenrecruiting unread \u00a78- \u00a77only unread"));
		source.sendFeedback(TextHelper.parse("\u00a77/frozenrecruiting reply <player> <text>"));
		source.sendFeedback(TextHelper.parse("\u00a77/frozenrecruiting markread \u00a78- \u00a77clear unread badges"));
		source.sendFeedback(TextHelper.parse("\u00a77/frozenrecruiting clear \u00a78- \u00a77delete all history"));
		return 1;
	}

	private static int listAll(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		List<SentLog.Contact> contacts = SentLog.list();

		if (contacts.isEmpty()) {
			source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7fNo contacts yet."));
			return 0;
		}

		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a77sent to " + contacts.size() + ":"));
		for (SentLog.Contact c : contacts) {
			source.sendFeedback(TextHelper.parse(formatContact(c)));
		}

		return contacts.size();
	}

	private static int showUnread(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		boolean any = false;

		for (SentLog.Contact c : SentLog.list()) {
			if (c.unread > 0) {
				source.sendFeedback(TextHelper.parse(formatContact(c)));
				any = true;
			}
		}

		if (!any) {
			source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7fNo unread replies."));
			return 0;
		}

		return 1;
	}

	private static String formatContact(SentLog.Contact c) {
		StringBuilder sb = new StringBuilder("\u00a77 - \u00a7f");
		sb.append(c.name);

		if (c.unread > 0) {
			sb.append(" \u00a7c(").append(c.unread).append(" unread)");
		}

		if (c.lastReply != null && !c.lastReply.isBlank()) {
			sb.append(" \u00a78| \u00a77\"").append(c.lastReplyPreview()).append("\"");
		}

		return sb.toString();
	}

	private static int markAllRead(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		SentLog.markAllRead();
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7aAll replies marked as read."));
		return 1;
	}

	private static int clearAll(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		SentLog.clearAll();
		ReplyPopup.clear();
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7aSent history cleared."));
		return 1;
	}

	private static int sendReply(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String player = StringArgumentType.getString(ctx, "player");
		String message = StringArgumentType.getString(ctx, "message");

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() == null) {
			source.sendError(TextHelper.parse("Not connected to a server."));
			return 0;
		}

		client.getNetworkHandler().sendChatCommand("tell " + player + " " + message);
		SentLog.recordSent(player);
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7aSent to \u00a7f" + player + "\u00a7a."));
		return 1;
	}
}