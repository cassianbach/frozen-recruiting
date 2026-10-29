package we.frozen.recruiting.client;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Converts legacy section-sign colour codes ({@code §a}, {@code §l}, {@code §r}, ...) into
 * properly styled {@link Text} components. Modern Minecraft does not interpret {@code §} codes
 * inside {@link Text#literal(String)}, so every place that previously used them must go through
 * this helper instead.
 */
public final class TextHelper {
	private TextHelper() {
	}

	public static MutableText parse(String input) {
		if (input == null || input.isEmpty()) {
			return Text.empty();
		}

		MutableText result = Text.empty();
		StringBuilder current = new StringBuilder();
		Formatting currentColor = null;
		boolean bold = false, italic = false, underline = false, strikethrough = false, obfuscated = false;

		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c == '§' && i + 1 < input.length()) {
				// Flush accumulated text with the current style.
				if (current.length() > 0) {
					MutableText segment = Text.literal(current.toString());
					if (currentColor != null) {
						segment = segment.formatted(currentColor);
					}
					if (bold) {
						segment = segment.formatted(Formatting.BOLD);
					}
					if (italic) {
						segment = segment.formatted(Formatting.ITALIC);
					}
					if (underline) {
						segment = segment.formatted(Formatting.UNDERLINE);
					}
					if (strikethrough) {
						segment = segment.formatted(Formatting.STRIKETHROUGH);
					}
					if (obfuscated) {
						segment = segment.formatted(Formatting.OBFUSCATED);
					}
					result.append(segment);
					current.setLength(0);
				}

				char code = input.charAt(++i);
				Formatting format = Formatting.byCode(code);
				if (format != null) {
					if (format.isColor()) {
						currentColor = format;
						bold = italic = underline = strikethrough = obfuscated = false;
					} else if (format == Formatting.RESET) {
						currentColor = null;
						bold = italic = underline = strikethrough = obfuscated = false;
					} else if (format == Formatting.BOLD) {
						bold = true;
					} else if (format == Formatting.ITALIC) {
						italic = true;
					} else if (format == Formatting.UNDERLINE) {
						underline = true;
					} else if (format == Formatting.STRIKETHROUGH) {
						strikethrough = true;
					} else if (format == Formatting.OBFUSCATED) {
						obfuscated = true;
					}
				}
			} else {
				current.append(c);
			}
		}

		// Flush remaining text.
		if (current.length() > 0) {
			MutableText segment = Text.literal(current.toString());
			if (currentColor != null) {
				segment = segment.formatted(currentColor);
			}
			if (bold) {
				segment = segment.formatted(Formatting.BOLD);
			}
			if (italic) {
				segment = segment.formatted(Formatting.ITALIC);
			}
			if (underline) {
				segment = segment.formatted(Formatting.UNDERLINE);
			}
			if (strikethrough) {
				segment = segment.formatted(Formatting.STRIKETHROUGH);
			}
			if (obfuscated) {
				segment = segment.formatted(Formatting.OBFUSCATED);
			}
			result.append(segment);
		}

		return result;
	}
}