package we.frozen.recruiting.cosmetic;

import java.util.List;

/**
 * The catalogue of obtainable cosmetics. New cosmetics are added here — the code system references
 * cosmetic ids from this catalogue, so a code for an unknown id is rejected at redemption time.
 */
public final class CosmeticCatalog {
	private CosmeticCatalog() {
	}

	public static final List<CosmeticDef> COSMETICS = List.of(
			new CosmeticDef("common_circle", "Common Circle", Rarity.COMMON, "A simple grey marker."),
			new CosmeticDef("uncommon_diamond", "Uncommon Diamond", Rarity.UNCOMMON, "A green diamond."),
			new CosmeticDef("uncommon_envoy", "Envoy", Rarity.UNCOMMON, "A green envoy badge."),
			new CosmeticDef("rare_sparkle", "Rare Sparkle", Rarity.RARE, "A blue sparkle."),
			new CosmeticDef("rare_pathfinder", "Pathfinder", Rarity.RARE, "A blue pathfinder badge."),
			new CosmeticDef("epic_star", "Epic Star", Rarity.EPIC, "A purple star."),
			new CosmeticDef("epic_warden", "Warden", Rarity.EPIC, "A purple warden badge."),
			new CosmeticDef("legendary_crown", "Legendary Crown", Rarity.LEGENDARY, "A gold crown."),
			new CosmeticDef("legendary_frozen", "Frozen Elite", Rarity.LEGENDARY, "A gold frozen elite badge."),
			new CosmeticDef("mythic_frost", "Mythic Frost", Rarity.MYTHIC, "A cyan snowflake of mythic power.")
	);

	public static CosmeticDef get(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}

		for (CosmeticDef c : COSMETICS) {
			if (c.id().equals(id)) {
				return c;
			}
		}

		return null;
	}

	public static boolean exists(String id) {
		return get(id) != null;
	}
}