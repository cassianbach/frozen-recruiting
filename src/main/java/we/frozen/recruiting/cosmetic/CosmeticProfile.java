package we.frozen.recruiting.cosmetic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A player's owned cosmetics and currently equipped badge. Persisted as JSON.
 *
 * <p>The profile is keyed by player name so it survives name changes gracefully (the code is
 * name-bound, so a rename simply means the old profile is abandoned — acceptable for a cosmetic
 * system where codes are re-issuable).
 */
public class CosmeticProfile {
	public String playerName = "";
	public List<String> ownedCosmetics = new ArrayList<>();
	public String equippedCosmetic = null;

	public CosmeticProfile() {
	}

	public CosmeticProfile(String playerName) {
		this.playerName = playerName == null ? "" : playerName;
	}

	public boolean owns(String cosmeticId) {
		return ownedCosmetics != null && ownedCosmetics.contains(cosmeticId);
	}

	public void grant(String cosmeticId) {
		if (cosmeticId == null || cosmeticId.isBlank()) {
			return;
		}

		if (ownedCosmetics == null) {
			ownedCosmetics = new ArrayList<>();
		}

		if (!ownedCosmetics.contains(cosmeticId)) {
			ownedCosmetics.add(cosmeticId);
		}
	}

	public void equip(String cosmeticId) {
		if (cosmeticId == null || cosmeticId.isBlank() || !owns(cosmeticId)) {
			return;
		}

		equippedCosmetic = cosmeticId;
	}

	public void unequip() {
		equippedCosmetic = null;
	}

	public CosmeticDef equippedDef() {
		if (equippedCosmetic == null || equippedCosmetic.isBlank()) {
			return null;
		}

		return CosmeticCatalog.get(equippedCosmetic);
	}

	/** Removes duplicate entries while preserving order. */
	public void normalize() {
		if (ownedCosmetics == null) {
			ownedCosmetics = new ArrayList<>();
			return;
		}

		Set<String> seen = new LinkedHashSet<>(ownedCosmetics);
		ownedCosmetics = new ArrayList<>(seen);
	}
}