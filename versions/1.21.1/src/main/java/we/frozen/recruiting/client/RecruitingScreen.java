package we.frozen.recruiting.client;

import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import we.frozen.recruiting.cosmetic.CosmeticCatalog;
import we.frozen.recruiting.cosmetic.CosmeticCommands;
import we.frozen.recruiting.cosmetic.CosmeticDef;
import we.frozen.recruiting.cosmetic.CosmeticService;
import we.frozen.recruiting.cosmetic.CosmeticCatalog;
import we.frozen.recruiting.cosmetic.CosmeticCommands;
import we.frozen.recruiting.cosmetic.CosmeticDef;
import we.frozen.recruiting.cosmetic.CosmeticService;
import org.lwjgl.glfw.GLFW;

/**
 * Frozen Recruiting message composer, styled after the MobMastery / TargetMaster panels: a dark
 * panel with a header bar, a tab strip, and an inset input box.
 *
 * <p>Two tabs: <b>Compose</b> holds the message you will send by right-clicking a player, and
 * <b>Sent</b> lists everyone you have messaged with their last reply, letting you reply again
 * without leaving the menu.
 */
public class RecruitingScreen extends Screen {
	public static final String DISCORD_URL = "https://discord.gg/RMgaNmQVau";

	private static final int PANEL_WIDTH = 340;
	private static final int PANEL_HEIGHT = 210;

	private static final int COL_BG_DARK = 0xE0101018;
	private static final int COL_BG_PANEL = 0xF214141F;
	private static final int COL_BG_HEADER = 0xFF0A0A14;
	private static final int COL_BORDER_LIGHT = 0xFF3A3D55;
	private static final int COL_BORDER = 0xFF2A2D40;
	private static final int COL_ACCENT_CYAN = 0xFF70E0FF;
	private static final int COL_TEXT_PRIMARY = 0xFFEDEFF7;
	private static final int COL_TEXT_SECONDARY = 0xFFA0A4B8;
	private static final int COL_TEXT_MUTED = 0xFF6B7080;
	private static final int COL_HOVER = 0xFF222638;
	private static final int COL_UNREAD = 0xFFFF6B6B;

	private static final int HEADER_HEIGHT = 22;
	private static final int TAB_HEIGHT = 18;
	private static final int FOOTER_HEIGHT = 22;
	private static final int PADDING = 14;
	private static final int FIELD_HEIGHT = 20;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 6;

	/** Sent-tab row layout. */
	private static final int ROW_HEIGHT = 34;
	private static final int ROW_GAP = 4;
	private static final int REPLY_FIELD_HEIGHT = 16;
	private static final int REPLY_BUTTON_WIDTH = 46;

	private enum Tab {
		COMPOSE(0),
		SENT(1),
		COSMETICS(2);

		final int index;

		Tab(int index) {
			this.index = index;
		}

		static Tab of(int index) {
			if (index == 1) return SENT;
			if (index == 2) return COSMETICS;
			return COMPOSE;
		}
	}

	private final Screen parent;

	private Tab tab = Tab.COMPOSE;
	private List<SentLog.Contact> contacts = List.of();
	private int scrollOffset;

	/** Inline reply field per visible row; sized to the visible window only. */
	private final java.util.Map<Integer, TextFieldWidget> replyFields = new java.util.HashMap<>();

	private TextFieldWidget messageField;

	// Cosmetics tab state
	private List<String> ownedCosmetics = List.of();
	private int cosmeticScrollOffset;
	private int selectedCosmetic = -1;
	private TextFieldWidget redeemField;
	private TextFieldWidget mintPlayerField;
	private TextFieldWidget mintCosmeticField;
	private boolean isAdmin;

	private int panelLeft;
	private int panelTop;

	public RecruitingScreen(Screen parent) {
		super(Text.translatable("frozen_recruiting.gui.title"));
		this.parent = parent;
	}

	private int contentLeft() {
		return panelLeft + PADDING;
	}

	private int contentWidth() {
		return PANEL_WIDTH - PADDING * 2;
	}

	private int tabsTop() {
		return panelTop + HEADER_HEIGHT + 4;
	}

	private int bodyTop() {
		return tabsTop() + TAB_HEIGHT + 10;
	}

	private int fieldTop() {
		return bodyTop() + 22;
	}

	private int footerTop() {
		return panelTop + PANEL_HEIGHT - FOOTER_HEIGHT - 6;
	}

	@Override
	protected void init() {
		panelLeft = (this.width - PANEL_WIDTH) / 2;
		panelTop = (this.height - PANEL_HEIGHT) / 2;

		contacts = SentLog.list();
		ownedCosmetics = CosmeticService.get().ownedCosmetics();
		scrollOffset = 0;
		cosmeticScrollOffset = 0;
		replyFields.clear();

		MinecraftClient client = MinecraftClient.getInstance();
		isAdmin = client != null && client.player != null
				&& "chaosfloh2011".equalsIgnoreCase(client.player.getName().getString());

		if (tab == Tab.SENT) {
			initSentTab();
		} else if (tab == Tab.COSMETICS) {
			initCosmeticsTab();
		} else {
			initComposeTab();
		}
	}

	private void initComposeTab() {
		this.messageField = new TextFieldWidget(
				this.textRenderer, contentLeft(), fieldTop(), contentWidth(), FIELD_HEIGHT, Text.empty());
		this.messageField.setMaxLength(256);
		this.messageField.setPlaceholder(Text.translatable("frozen_recruiting.gui.message_placeholder"));
		this.messageField.setDrawsBackground(false);
		this.messageField.setEditableColor(COL_TEXT_PRIMARY);
		this.messageField.setUneditableColor(COL_TEXT_MUTED);
		this.messageField.setText(RecruitMessageStore.get() == null ? "" : RecruitMessageStore.get());
		this.messageField.setCursorToEnd(false);
		this.addDrawableChild(this.messageField);
		this.setInitialFocus(this.messageField);

		int buttonTop = footerTop();
		int buttonWidth = (contentWidth() - BUTTON_GAP * 2) / 3;

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.send"), b -> onStore())
				.dimensions(contentLeft(), buttonTop, buttonWidth, BUTTON_HEIGHT)
				.build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.clear"), b -> onClear())
				.dimensions(contentLeft() + buttonWidth + BUTTON_GAP, buttonTop, buttonWidth, BUTTON_HEIGHT)
				.build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.done"), b -> close())
				.dimensions(contentLeft() + (buttonWidth + BUTTON_GAP) * 2, buttonTop, buttonWidth, BUTTON_HEIGHT)
				.build());
	}

	private void initSentTab() {
		SentLog.markAllRead();

		// One inline reply field per visible row.
		int visible = visibleRowCount();
		for (int i = 0; i < visible; i++) {
			int index = scrollOffset + i;
			if (index < 0 || index >= contacts.size()) {
				continue;
			}

			SentLog.Contact c = contacts.get(index);
			int rowTop = rowTop(i);
			int fieldX = rowLeft() + 8;
			int fieldW = rowWidth() - 16 - REPLY_BUTTON_WIDTH - 4;
			if (fieldW < 20) {
				continue;
			}

			TextFieldWidget field = new TextFieldWidget(this.textRenderer, fieldX,
					rowTop + ROW_HEIGHT - REPLY_FIELD_HEIGHT - 3, fieldW, REPLY_FIELD_HEIGHT, Text.empty());
			field.setMaxLength(200);
			field.setDrawsBackground(false);
			field.setEditableColor(COL_TEXT_PRIMARY);
			field.setUneditableColor(COL_TEXT_MUTED);
			field.setPlaceholder(Text.literal("/tell " + c.name + " ..."));
			replyFields.put(index, field);
			this.addDrawableChild(field);

			int buttonX = fieldX + fieldW + 4;
			this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.reply"), b -> sendReply(c, field))
					.dimensions(buttonX, rowTop + ROW_HEIGHT - REPLY_FIELD_HEIGHT - 3,
							REPLY_BUTTON_WIDTH, REPLY_FIELD_HEIGHT)
					.build());
		}

		int buttonTop = footerTop();
		int buttonWidth = (contentWidth() - BUTTON_GAP) / 2;

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.refresh"), b -> switchTab(tab))
				.dimensions(contentLeft(), buttonTop, buttonWidth, BUTTON_HEIGHT)
				.build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.clear_history"), b -> {
					SentLog.clearAll();
					ReplyPopup.clear();
					clearAndInit();
				})
				.dimensions(contentLeft() + buttonWidth + BUTTON_GAP, buttonTop, buttonWidth, BUTTON_HEIGHT)
				.build());
	}

	private void initCosmeticsTab() {
		ownedCosmetics = CosmeticService.get().ownedCosmetics();
		selectedCosmetic = -1;

		int listTop = bodyTop() + 4;
		int listHeight = footerTop() - 12 - listTop;
		int visible = Math.max(1, listHeight / 14);

		// Cosmetics list (scrollable)
		// We'll draw the list in drawCosmeticsList, and handle clicks in mouseClicked.

		// Redeem code field
		int fieldTop = footerTop() - 48;
		redeemField = new TextFieldWidget(this.textRenderer, contentLeft(), fieldTop, contentWidth() - 70, 18, Text.empty());
		redeemField.setMaxLength(512);
		redeemField.setPlaceholder(Text.translatable("frozen_recruiting.gui.redeem_placeholder"));
		redeemField.setDrawsBackground(false);
		redeemField.setEditableColor(COL_TEXT_PRIMARY);
		redeemField.setUneditableColor(COL_TEXT_MUTED);
		this.addDrawableChild(redeemField);

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.redeem"), b -> {
			String code = redeemField.getText().trim();
			if (!code.isEmpty()) {
				CosmeticCommands.redeemCodeFromGui(code);
				redeemField.setText("");
				clearAndInit();
			}
		}).dimensions(contentLeft() + contentWidth() - 66, fieldTop, 66, 18).build());

		// Scan button
		int scanTop = fieldTop + 22;
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.scan"), b -> {
			CosmeticService.get().scanCodesFile();
			clearAndInit();
		}).dimensions(contentLeft(), scanTop, (contentWidth() - BUTTON_GAP) / 2, BUTTON_HEIGHT).build());

		// Admin section
		if (isAdmin) {
			int adminTop = scanTop + BUTTON_HEIGHT + 4;
			this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.keygen"), b -> {
				CosmeticService.get(); // ensure initialized
				CosmeticCommands.keygenFromGui();
				clearAndInit();
			}).dimensions(contentLeft() + (contentWidth() - BUTTON_GAP) / 2 + BUTTON_GAP, scanTop, (contentWidth() - BUTTON_GAP) / 2, BUTTON_HEIGHT).build());

			mintPlayerField = new TextFieldWidget(this.textRenderer, contentLeft(), adminTop + 2, (contentWidth() - BUTTON_GAP) / 2 - 4, 16, Text.empty());
			mintPlayerField.setMaxLength(32);
			mintPlayerField.setPlaceholder(Text.translatable("frozen_recruiting.gui.mint_player"));
			mintPlayerField.setDrawsBackground(false);
			mintPlayerField.setEditableColor(COL_TEXT_PRIMARY);
			this.addDrawableChild(mintPlayerField);

			mintCosmeticField = new TextFieldWidget(this.textRenderer, contentLeft() + (contentWidth() - BUTTON_GAP) / 2 + 4, adminTop + 2, (contentWidth() - BUTTON_GAP) / 2 - 4, 16, Text.empty());
			mintCosmeticField.setMaxLength(32);
			mintCosmeticField.setPlaceholder(Text.translatable("frozen_recruiting.gui.mint_cosmetic"));
			mintCosmeticField.setDrawsBackground(false);
			mintCosmeticField.setEditableColor(COL_TEXT_PRIMARY);
			this.addDrawableChild(mintCosmeticField);

			this.addDrawableChild(ButtonWidget.builder(Text.translatable("frozen_recruiting.gui.mint"), b -> {
				String player = mintPlayerField.getText().trim();
				String cosmetic = mintCosmeticField.getText().trim();
				if (!player.isEmpty() && !cosmetic.isEmpty()) {
					CosmeticCommands.mintCodeFromGui(player, cosmetic);
					mintPlayerField.setText("");
					mintCosmeticField.setText("");
					clearAndInit();
				}
			}).dimensions(contentLeft(), adminTop + 22, contentWidth(), BUTTON_HEIGHT).build());
		}
	}

	private void drawCosmeticsList(DrawContext context, int mouseX, int mouseY) {
		int listTop = bodyTop() + 4;
		int listHeight = (isAdmin ? 50 : 80);
		int rowH = 14;

		context.drawText(this.textRenderer, Text.translatable("frozen_recruiting.gui.your_cosmetics"),
				contentLeft(), listTop - 12, COL_ACCENT_CYAN, false);

		context.fill(contentLeft(), listTop, contentLeft() + contentWidth(), listTop + listHeight, COL_BG_HEADER);
		drawBorder(context, contentLeft(), listTop, contentWidth(), listHeight, COL_BORDER);

		if (ownedCosmetics.isEmpty()) {
			context.drawText(this.textRenderer, Text.translatable("frozen_recruiting.gui.no_cosmetics"),
					contentLeft() + 6, listTop + 6, COL_TEXT_MUTED, false);
			return;
		}

		for (int i = 0; i < ownedCosmetics.size(); i++) {
			int y = listTop + i * rowH;
			if (y + rowH > listTop + listHeight) break;

			String id = ownedCosmetics.get(i);
			CosmeticDef def = CosmeticCatalog.get(id);
			if (def == null) continue;

			boolean selected = i == selectedCosmetic;
			boolean hovered = mouseX >= contentLeft() && mouseX < contentLeft() + contentWidth()
					&& mouseY >= y && mouseY < y + rowH;

			int bg = selected ? COL_HOVER : (hovered ? 0xFF1B3157 : COL_BG_HEADER);
			context.fill(contentLeft() + 1, y, contentLeft() + contentWidth() - 1, y + rowH - 1, bg);

			String label = def.rarity().glyph + " " + def.displayName() + " (" + def.rarity().displayName + ")";
			if (id.equals(CosmeticService.get().equippedId())) {
				label = label + " [Equipped]";
			}
			context.drawText(this.textRenderer, this.textRenderer.trimToWidth(label, contentWidth() - 8),
					contentLeft() + 6, y + 3, selected ? 0xFFFFFFFF : COL_TEXT_PRIMARY, false);
		}

		// Equip/Unequip buttons
		int btnTop = listTop + listHeight + 4;
		int btnW = (contentWidth() - BUTTON_GAP) / 2;
		// (buttons are added in initCosmeticsTab)
	}

	private int visibleRowCount() {
		int available = footerTop() - 8 - bodyTop();
		return Math.max(1, (available + ROW_GAP) / (ROW_HEIGHT + ROW_GAP));
	}

	private int rowLeft() {
		return contentLeft();
	}

	private int rowWidth() {
		return contentWidth();
	}

	private int rowTop(int visibleIndex) {
		return bodyTop() + visibleIndex * (ROW_HEIGHT + ROW_GAP);
	}

	private void sendReply(SentLog.Contact contact, TextFieldWidget field) {
		String text = field.getText().trim();
		if (text.isEmpty()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.getNetworkHandler() == null) {
			ChatFeedback.error(client, "frozen_recruiting.msg.not_server");
			return;
		}

		client.getNetworkHandler().sendChatCommand("tell " + contact.name + " " + text);
		SentLog.recordSent(contact.name);
		ChatFeedback.success(client, "frozen_recruiting.msg.sent", contact.name);
		field.setText("");
	}

	private void switchTab(Tab next) {
		if (this.messageField != null) {
			RecruitMessageStore.set(this.messageField.getText());
			this.messageField = null;
		}

		this.tab = next;
		replyFields.clear();
		clearAndInit();
	}

	private void onClear() {
		if (this.messageField != null) {
			this.messageField.setText("");
			this.messageField.setCursorToEnd(false);
		}

		RecruitMessageStore.clear();
		ChatFeedback.info(MinecraftClient.getInstance(), "frozen_recruiting.msg.cleared");
	}

	private void onStore() {
		MinecraftClient client = MinecraftClient.getInstance();
		RecruitMessageStore.set(currentTypedText());

		if (!RecruitMessageStore.isArmed()) {
			ChatFeedback.error(client, "frozen_recruiting.msg.empty");
			return;
		}

		ChatFeedback.info(client, "frozen_recruiting.msg.set");
		close();
	}

	private void openDiscord() {
		try {
			Util.getOperatingSystem().open(Util.validateUri(DISCORD_URL));
			ChatFeedback.info(MinecraftClient.getInstance(), "frozen_recruiting.msg.discord_opened");
		} catch (Exception e) {
			ChatFeedback.error(MinecraftClient.getInstance(), "frozen_recruiting.msg.discord_failed");
		}
	}

	private String currentTypedText() {
		return this.messageField == null ? "" : this.messageField.getText();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		drawFrame(context);
		drawHeader(context, mouseX, mouseY);
		drawTabs(context, mouseX, mouseY);

		if (tab == Tab.SENT) {
			drawSentList(context, mouseX, mouseY);
		} else if (tab == Tab.COSMETICS) {
			drawCosmeticsList(context, mouseX, mouseY);
		} else {
			drawFieldChrome(context);
		}

		// Widgets (fields, buttons) render on top of the panel chrome.
		super.render(context, mouseX, mouseY, delta);
	}

	private void drawFrame(DrawContext context) {
		context.fill(panelLeft - 2, panelTop + 2, panelLeft + PANEL_WIDTH + 3, panelTop + PANEL_HEIGHT + 3, COL_BG_DARK);
		context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, COL_BG_PANEL);
		context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + HEADER_HEIGHT, COL_BG_HEADER);
		context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 1, COL_ACCENT_CYAN);
		drawBorder(context, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, COL_BORDER_LIGHT);
		context.fill(panelLeft, panelTop + HEADER_HEIGHT - 1, panelLeft + PANEL_WIDTH, panelTop + HEADER_HEIGHT, COL_BORDER);
		context.fill(panelLeft, footerTop() - 5, panelLeft + PANEL_WIDTH, footerTop() - 4, COL_BORDER);
	}

	private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x, y, x + w, y + 1, color);
		context.fill(x, y + h - 1, x + w, y + h, color);
		context.fill(x, y, x + 1, y + h, color);
		context.fill(x + w - 1, y, x + w, y + h, color);
	}

	private void drawHeader(DrawContext context, int mouseX, int mouseY) {
		int baseline = panelTop + (HEADER_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
		context.drawText(this.textRenderer, this.title, contentLeft(), baseline, COL_TEXT_PRIMARY, false);

		Text label = Text.translatable("frozen_recruiting.gui.discord");
		int labelWidth = this.textRenderer.getWidth(label);
		int buttonHeight = 14;
		int buttonTop = panelTop + (HEADER_HEIGHT - buttonHeight) / 2;
		int buttonRight = panelLeft + PANEL_WIDTH - PADDING;
		int buttonLeft = buttonRight - labelWidth - 8;

		boolean hovered = mouseX >= buttonLeft && mouseX < buttonRight
				&& mouseY >= buttonTop && mouseY < buttonTop + buttonHeight;

		context.fill(buttonLeft, buttonTop, buttonRight, buttonTop + buttonHeight, hovered ? COL_HOVER : COL_BORDER);
		drawBorder(context, buttonLeft, buttonTop, labelWidth + 8, buttonHeight, COL_ACCENT_CYAN);
		context.drawText(this.textRenderer, label, buttonLeft + 4, buttonTop + 3, COL_ACCENT_CYAN, false);
	}

	private void drawTabs(DrawContext context, int mouseX, int mouseY) {
		int tabLeft = contentLeft();
		int y = tabsTop();

		int widthEach = (contentWidth() - 12) / 3;

		for (Tab t : Tab.values()) {
			int x = tabLeft + t.index * (widthEach + 6);
			boolean active = t == tab;
			boolean hovered = mouseX >= x && mouseX < x + widthEach && mouseY >= y && mouseY < y + TAB_HEIGHT;

			context.fill(x, y, x + widthEach, y + TAB_HEIGHT, active ? COL_BG_HEADER : (hovered ? COL_HOVER : COL_BG_PANEL));
			context.fill(x, y + TAB_HEIGHT - 1, x + widthEach, y + TAB_HEIGHT, active ? COL_ACCENT_CYAN : COL_BORDER);
			context.fill(x, y, x + 1, y + TAB_HEIGHT, COL_BORDER);
			context.fill(x + widthEach - 1, y, x + widthEach, y + TAB_HEIGHT, COL_BORDER);

			String label = t == Tab.COMPOSE
					? Text.translatable("frozen_recruiting.gui.tab_compose").getString()
					: Text.translatable("frozen_recruiting.gui.tab_sent").getString();

			int unread = t == Tab.SENT ? SentLog.totalUnread() : 0;
			if (unread > 0) {
				label = label + " (" + unread + ")";
			}

			int textY = y + (TAB_HEIGHT - this.textRenderer.fontHeight) / 2 + 1;
			context.drawCenteredTextWithShadow(this.textRenderer, label,
					x + widthEach / 2, textY, active ? COL_ACCENT_CYAN : COL_TEXT_SECONDARY);
		}
	}

	private void drawFieldChrome(DrawContext context) {
		int left = contentLeft();
		int width = contentWidth();
		int top = fieldTop();
		int bottom = top + FIELD_HEIGHT;

		context.drawText(this.textRenderer, Text.translatable("frozen_recruiting.gui.message_label"),
				left, top - 13, COL_ACCENT_CYAN, false);

		int typed = currentTypedText().length();
		String counter = typed + "/256";
		context.drawText(this.textRenderer, counter,
				left + width - this.textRenderer.getWidth(counter), top - 13, COL_TEXT_MUTED, false);

		context.fill(left, top, left + width, bottom, COL_BG_HEADER);
		drawBorder(context, left, top, width, FIELD_HEIGHT, COL_BORDER);

		if (this.messageField != null && this.messageField.isFocused()) {
			context.fill(left, bottom - 1, left + width, bottom, COL_ACCENT_CYAN);
		}

		if (typed == 0) {
			String placeholder = Text.translatable("frozen_recruiting.gui.message_placeholder").getString();
			context.drawText(this.textRenderer, this.textRenderer.trimToWidth(placeholder, width - 10),
					left + 6, top + 6, COL_TEXT_MUTED, false);
		}

		context.drawText(this.textRenderer, Text.translatable("frozen_recruiting.gui.hint"),
				left, bottom + 6, COL_TEXT_SECONDARY, false);
	}

	private void drawSentList(DrawContext context, int mouseX, int mouseY) {
		int visible = visibleRowCount();

		if (contacts.isEmpty()) {
			String empty = Text.translatable("frozen_recruiting.gui.sent_empty").getString();
			context.drawText(this.textRenderer, empty, rowLeft() + 4, bodyTop() + 6, COL_TEXT_MUTED, false);
			return;
		}

		for (int i = 0; i < visible; i++) {
			int index = scrollOffset + i;
			if (index < 0 || index >= contacts.size()) {
				break;
			}

			SentLog.Contact c = contacts.get(index);
			int top = rowTop(i);
			int left = rowLeft();
			int width = rowWidth();

			boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + ROW_HEIGHT;
			context.fill(left, top, left + width, top + ROW_HEIGHT, hovered ? COL_HOVER : COL_BG_HEADER);
			drawBorder(context, left, top, width, ROW_HEIGHT, COL_BORDER);

			// Unread marker.
			if (c.unread > 0) {
				context.fill(left + 1, top + 1, left + 4, top + ROW_HEIGHT - 1, COL_UNREAD);
			}

			int textX = left + 8;
			int nameY = top + 4;
			context.drawText(this.textRenderer, this.textRenderer.trimToWidth(c.name, width - 90),
					textX, nameY, COL_TEXT_PRIMARY, false);

			if (c.unread > 0) {
				String badge = c.unread + " new";
				context.drawText(this.textRenderer, badge,
						left + width - 8 - this.textRenderer.getWidth(badge), nameY, COL_UNREAD, false);
			}

			String when = SentLog.formatTime(Math.max(c.lastReplyAt, c.lastSentAt));
			context.drawText(this.textRenderer, when,
					left + width - 8 - this.textRenderer.getWidth(when), nameY, COL_TEXT_MUTED, false);

			String reply = c.lastReply == null || c.lastReply.isBlank()
					? Text.translatable("frozen_recruiting.gui.sent_no_reply").getString()
					: "\"" + c.lastReplyPreview() + "\"";
			int replyColor = (c.lastReply == null || c.lastReply.isBlank()) ? COL_TEXT_MUTED : COL_TEXT_SECONDARY;
			context.drawText(this.textRenderer, this.textRenderer.trimToWidth(reply, width - 16),
					textX, nameY + 10, replyColor, false);

			// Underline for the inline reply field.
			int fieldX = textX;
			int fieldW = width - 16 - REPLY_BUTTON_WIDTH - 4;
			context.fill(fieldX, top + ROW_HEIGHT - 5, fieldX + fieldW, top + ROW_HEIGHT - 4, COL_BORDER);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int mx = (int) mouseX;
		int my = (int) mouseY;

		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			// Discord button.
			Text label = Text.translatable("frozen_recruiting.gui.discord");
			int labelWidth = this.textRenderer.getWidth(label);
			int buttonHeight = 14;
			int buttonTop = panelTop + (HEADER_HEIGHT - buttonHeight) / 2;
			int buttonLeft = panelLeft + PANEL_WIDTH - PADDING - labelWidth - 8;

			if (mx >= buttonLeft && mx < buttonLeft + labelWidth + 8
					&& my >= buttonTop && my < buttonTop + buttonHeight) {
				openDiscord();
				return true;
			}

			// Tab strip.
			int widthEach = (contentWidth() - 12) / 3;
			int tabTop = tabsTop();

			for (Tab t : Tab.values()) {
				int x = contentLeft() + t.index * (widthEach + 6);
				if (mx >= x && mx < x + widthEach && my >= tabTop && my < tabTop + TAB_HEIGHT) {
					switchTab(t);
					return true;
				}
			}

			// Cosmetics list click.
			if (tab == Tab.COSMETICS) {
				int listTop = bodyTop() + 4;
				int listHeight = isAdmin ? 50 : 80;
				int rowH = 14;
				for (int i = 0; i < ownedCosmetics.size(); i++) {
					int y = listTop + i * rowH;
					if (y + rowH > listTop + listHeight) break;
					if (mx >= contentLeft() && mx < contentLeft() + contentWidth()
							&& my >= y && my < y + rowH) {
						selectedCosmetic = i;
						clearAndInit();
						return true;
					}
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (tab == Tab.SENT && !contacts.isEmpty()) {
			int maxScroll = Math.max(0, contacts.size() - visibleRowCount());

			if (verticalAmount > 0 && scrollOffset > 0) {
				scrollOffset--;
				clearAndInit();
				return true;
			}

			if (verticalAmount < 0 && scrollOffset < maxScroll) {
				scrollOffset++;
				clearAndInit();
				return true;
			}
		}

		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}

		if (tab == Tab.COMPOSE && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			onStore();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		if (this.messageField != null) {
			RecruitMessageStore.set(this.messageField.getText());
		}

		if (this.parent != null) {
			MinecraftClient.getInstance().setScreen(this.parent);
		} else {
			MinecraftClient.getInstance().setScreen(null);
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Intentionally empty: 1.21.1's default applies a full-screen blur post-process.
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}