package we.frozen.recruiting.cosmetic;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Rarity tiers for cosmetics. Colour and glyph are baked into the tier so that a "legendary"
 * badge is instantly recognisable regardless of which cosmetic it belongs to.
 *
 * <p>Glyphs were chosen from codepoints that are present in Minecraft's bundled unifont data
 * (verified against the asset index) so they render as real characters, not blank boxes.
 */
public enum Rarity {
	COMMON(0x808080, Formatting.GRAY, "○", "Common"),
	UNCOMMON(0x55FF55, Formatting.GREEN, "◆", "Uncommon"),
	RARE(0x5555FF, Formatting.BLUE, "✦", "Rare"),
	EPIC(0xAA00AA, Formatting.DARK_PURPLE, "★", "Epic"),
	LEGENDARY(0xFFAA00, Formatting.GOLD, "♛", "Legendary");

	public final int color;
	public final Formatting formatting;
	public final String glyph;
	public final String displayName;

	Rarity(int color, Formatting formatting, String glyph, String displayName) {
		this.color = color;
		this.formatting = formatting;
		this.glyph = glyph;
		this.displayName = displayName;
	}

	public Text formattedName() {
		return Text.literal(displayName).formatted(formatting);
	}

	public static Rarity fromName(String name) {
		if (name == null) {
			return null;
		}

		for (Rarity r : values()) {
			if (r.name().equalsIgnoreCase(name)) {
				return r;
			}
		}

		return null;
	}
}