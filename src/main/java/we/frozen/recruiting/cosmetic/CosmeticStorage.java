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
import net.minecraft.client.MinecraftClient;

/**
 * Loads and saves the local player's cosmetic profile. Single instance per client.
 */
public final class CosmeticStorage {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private CosmeticProfile profile;

	public CosmeticStorage() {
		this.file = FabricLoader.getInstance().getConfigDir()
				.resolve("frozen-recruiting").resolve("cosmetics.json");
	}

	public CosmeticProfile get() {
		if (profile == null) {
			load();
		}

		return profile;
	}

	public void load() {
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
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			profile.playerName = client.player.getName().getString();
		}

		profile.normalize();
	}

	public void save() {
		if (profile == null) {
			return;
		}

		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(profile, w);
			}

			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
		}
	}

	/**
	 * Grants a cosmetic to the local profile (if not already owned) and persists.
	 */
	public void grant(String cosmeticId) {
		get().grant(cosmeticId);
		save();
	}

	public boolean equip(String cosmeticId) {
		if (!get().owns(cosmeticId)) {
			return false;
		}

		get().equip(cosmeticId);
		save();
		return true;
	}

	public void unequip() {
		get().unequip();
		save();
	}

	/**
	 * Returns the local player's UUID, or null if not in-game.
	 */
	public static UUID localUuid() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return null;
		}

		return client.player.getUuid();
	}
}