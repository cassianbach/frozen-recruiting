package we.frozen.recruiting.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Frozen Recruiting message composer, styled after the MobMastery / TargetMaster panels:
 * a dark panel with a header bar, an inset input box and a footer of accent buttons.
 *
 * <p>There is deliberately no player list: the target is whichever player you right-click in the
 * world, so this screen only needs to hold the message text.
 */
public class RecruitingScreen extends Screen {
	public static final String DISCORD_URL = "https://discord.gg/RMgaNmQVau";

	private static final int PANEL_WIDTH = 320;
	private static final int PANEL_HEIGHT = 160;
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

	private static final int HEADER_HEIGHT = 22;
	private static final int FOOTER_HEIGHT = 22;
	private static final int PADDING = 14;
	private static final int FIELD_HEIGHT = 20;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 6;

	private final Screen parent;

	private TextFieldWidget messageField;

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

	private int fieldTop() {
		return panelTop + HEADER_HEIGHT + 22;
	}

	private int footerTop() {
		return panelTop + PANEL_HEIGHT - FOOTER_HEIGHT - 6;
	}

	@Override
	protected void init() {
		panelLeft = (this.width - PANEL_WIDTH) / 2;
		panelTop = (this.height - PANEL_HEIGHT) / 2;

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
		drawFieldChrome(context);

		// Widgets (text field, buttons) render on top of the panel chrome.
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		// Empty: 1.21.1's default would apply a full-screen blur post-process.
	}

	private void drawFrame(DrawContext context) {
		// Soft drop shadow.
		context.fill(panelLeft - 2, panelTop + 2, panelLeft + PANEL_WIDTH + 3, panelTop + PANEL_HEIGHT + 3, COL_BG_DARK);

		// Panel body.
		context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, COL_BG_PANEL);

		// Header.
		context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + HEADER_HEIGHT, COL_BG_HEADER);

		// Subtle cyan accent along the very top of the panel.
		context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + 1, COL_ACCENT_CYAN);

		// Outer border.
		drawBorder(context, panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, COL_BORDER_LIGHT);

		// Divider under the header.
		context.fill(panelLeft, panelTop + HEADER_HEIGHT - 1, panelLeft + PANEL_WIDTH, panelTop + HEADER_HEIGHT, COL_BORDER);

		// Divider above the footer.
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

		// Right-aligned Discord invite button in the header.
		Text label = Text.translatable("frozen_recruiting.gui.discord");
		int labelWidth = this.textRenderer.getWidth(label);
		int buttonHeight = 14;
		int buttonTop = panelTop + (HEADER_HEIGHT - buttonHeight) / 2;
		int buttonRight = panelLeft + PANEL_WIDTH - PADDING;
		int buttonLeft = buttonRight - labelWidth - 8;

		boolean hovered = mouseX >= buttonLeft && mouseX < buttonRight
				&& mouseY >= buttonTop && mouseY < buttonTop + buttonHeight;

		context.fill(buttonLeft, buttonTop, buttonRight, buttonTop + buttonHeight,
				hovered ? COL_HOVER : COL_BORDER);
		drawBorder(context, buttonLeft, buttonTop, labelWidth + 8, buttonHeight, COL_ACCENT_CYAN);
		context.drawText(this.textRenderer, label, buttonLeft + 4, buttonTop + 3, COL_ACCENT_CYAN, false);
	}

	private void drawFieldChrome(DrawContext context) {
		int left = contentLeft();
		int width = contentWidth();
		int top = fieldTop();
		int bottom = top + FIELD_HEIGHT;

		// Section label above the input.
		context.drawText(this.textRenderer, Text.translatable("frozen_recruiting.gui.message_label"),
				left, top - 13, COL_ACCENT_CYAN, false);

		// Character counter (right aligned on the label row).
		int typed = currentTypedText().length();
		String counter = typed + "/256";
		context.drawText(this.textRenderer, counter,
				left + width - this.textRenderer.getWidth(counter), top - 13, COL_TEXT_MUTED, false);

		// Inset input box.
		context.fill(left, top, left + width, bottom, COL_BG_HEADER);
		drawBorder(context, left, top, width, FIELD_HEIGHT, COL_BORDER);

		// Focused highlight.
		if (this.messageField != null && this.messageField.isFocused()) {
			context.fill(left, bottom - 1, left + width, bottom, COL_ACCENT_CYAN);
		}

		// Placeholder (the widget draws no text of its own).
		if (typed == 0) {
			String placeholder = Text.translatable("frozen_recruiting.gui.message_placeholder").getString();
			context.drawText(this.textRenderer, this.textRenderer.trimToWidth(placeholder, width - 10),
					left + 6, top + 6, COL_TEXT_MUTED, false);
		}

		// Hint line under the input.
		context.drawText(this.textRenderer, Text.translatable("frozen_recruiting.gui.hint"),
				left, bottom + 6, COL_TEXT_SECONDARY, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			Text label = Text.translatable("frozen_recruiting.gui.discord");
			int labelWidth = this.textRenderer.getWidth(label);
			int buttonHeight = 14;
			int buttonTop = panelTop + (HEADER_HEIGHT - buttonHeight) / 2;
			int buttonRight = panelLeft + PANEL_WIDTH - PADDING;
			int buttonLeft = buttonRight - labelWidth - 8;

			if (mouseX >= buttonLeft && mouseX < buttonRight
					&& mouseY >= buttonTop && mouseY < buttonTop + buttonHeight) {
				openDiscord();
				return true;
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			onStore();
			return true;
		}

		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}

		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		RecruitMessageStore.set(currentTypedText());

		if (this.parent != null) {
			MinecraftClient.getInstance().setScreen(this.parent);
		} else {
			MinecraftClient.getInstance().setScreen(null);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}