package we.frozen.recruiting.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import we.frozen.recruiting.cosmetic.CosmeticCatalog;
import we.frozen.recruiting.cosmetic.CosmeticDef;
import we.frozen.recruiting.cosmetic.CosmeticProfile;
import we.frozen.recruiting.cosmetic.CosmeticProvider;
import we.frozen.recruiting.cosmetic.CosmeticService;

/**
 * Appends a cosmetic badge to each player's name in the tab list.
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {
	@Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
	private void frozenRecruiting$appendCosmeticBadge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
		Text original = cir.getReturnValue();
		if (original == null || entry == null) {
			return;
		}

		UUID uuid = gameProfileId(entry);
		if (uuid == null) {
			return;
		}

		CosmeticProvider provider = CosmeticService.get().provider();
		if (provider == null) {
			return;
		}

		CosmeticProfile profile = provider.getProfile(uuid);
		if (profile == null || profile.equippedCosmetic == null || profile.equippedCosmetic.isBlank()) {
			return;
		}

		CosmeticDef def = CosmeticCatalog.get(profile.equippedCosmetic);
		if (def == null) {
			return;
		}

		MutableText badge = Text.literal(" " + def.rarity().glyph).formatted(def.rarity().formatting);
		cir.setReturnValue(original.copy().append(badge));
	}

	/**
	 * Gets the player's UUID from the entry's GameProfile, handling both authlib 6.x (getId())
	 * and 7.x (id() record accessor).
	 */
	private static UUID gameProfileId(PlayerListEntry entry) {
		try {
			Object profile = entry.getProfile();
			// Try record accessor first (authlib 7.x / 1.21.11+)
			try {
				return (UUID) profile.getClass().getMethod("id").invoke(profile);
			} catch (NoSuchMethodException e) {
				// Fall back to getter (authlib 6.x / 1.21.1)
				return (UUID) profile.getClass().getMethod("getId").invoke(profile);
			}
		} catch (Exception e) {
			return null;
		}
	}
}