package we.frozen.recruiting.cosmetic;

/**
 * A cosmetic definition. The rarity is part of the definition so that a code granting a specific
 * cosmetic also carries a specific rarity — there is no way to self-assign a cosmetic with a
 * different rarity than the code specifies.
 */
public record CosmeticDef(String id, String displayName, Rarity rarity, String description) {
	public CosmeticDef {
		if (id == null || id.isBlank()) {
			throw new IllegalArgumentException("cosmetic id must not be blank");
		}

		if (displayName == null || displayName.isBlank()) {
			displayName = id;
		}

		if (rarity == null) {
			rarity = Rarity.COMMON;
		}

		if (description == null) {
			description = "";
		}
	}

	/** Convenience constructor with empty description. */
	public CosmeticDef(String id, String displayName, Rarity rarity) {
		this(id, displayName, rarity, "");
	}
}