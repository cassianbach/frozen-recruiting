package we.frozen.recruiting.cosmetic;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Cosmetic provider backed by the local config file. Knows only about the local player, so the
 * badge is self-only unless a relay provider is swapped in.
 */
public final class LocalCosmeticProvider implements CosmeticProvider {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private CosmeticProfile profile;

	public LocalCosmeticProvider() {
		this.file = FabricLoader.getInstance().getConfigDir()
				.resolve("frozen-recruiting").resolve("cosmetics.json");
	}

	@Override
	public CosmeticProfile getProfile(UUID playerUuid) {
		if (playerUuid == null) {
			return null;
		}

				net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return null;
		}

		if (!playerUuid.equals(client.player.getUuid())) {
			return null;
		}

		ensureLoaded();
		return profile;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public void refresh() {
		profile = null;
		load();
	}

	public void load() {
		if (profile != null) {
			return;
		}

		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					profile = GSON.fromJson(r, CosmeticProfile.class);
				}
			}
		} catch (Exception e) {
			profile = null;
		}

		if (profile == null) {
			profile = new CosmeticProfile();
		}

		// Keep the stored name in sync with the current player.
		net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			profile.playerName = client.player.getName().getString();
		}

		profile.normalize();
	}

	public void save() {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(profile, w);
			}

			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ignored) {
		}
	}

	private void ensureLoaded() {
		if (profile == null) {
			load();
		}
	}

	/** Grants a cosmetic to the local profile and persists. */
	public void grant(String cosmeticId) {
		ensureLoaded();
		profile.grant(cosmeticId);
		save();
	}

	/** Equips a cosmetic the player owns. */
	public boolean equip(String cosmeticId) {
		ensureLoaded();

		if (!profile.owns(cosmeticId)) {
			return false;
		}

		profile.equip(cosmeticId);
		save();
		return true;
	}

	public void unequip() {
		ensureLoaded();
		profile.unequip();
		save();
	}
}