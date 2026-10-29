package we.frozen.recruiting.cosmetic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/**
 * High-level coordinator for the cosmetic system. Owns the provider, the local profile, and the
 * set of redeemed codes. Singleton via {@link #get()}.
 */
public final class CosmeticService {
	private static CosmeticService INSTANCE;

	private final CosmeticStorage storage;
	private final CosmeticProvider provider;
	private final Set<String> redeemedCodes = ConcurrentHashMap.newKeySet();

	public CosmeticService() {
		this.storage = new CosmeticStorage();
		this.storage.load();

		// Swap the provider here to enable cross-client visibility.
		CloudflareCosmeticProvider cf = CloudflareCosmeticProvider.fromConfig();
		this.provider = (cf != null && cf.isAvailable()) ? cf : new LocalCosmeticProvider();

		// Load the redeemed-code set from the profile's owned list (codes are tracked separately).
		loadRedeemedCodes();
	}

	private void loadRedeemedCodes() {
		// Best-effort: codes are tracked in a separate file.
		try {
			Path p = FabricLoader.getInstance().getConfigDir()
					.resolve("frozen-recruiting").resolve("redeemed-codes.txt");

			if (Files.exists(p)) {
				for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
					String trimmed = line.trim();
					if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
						redeemedCodes.add(trimmed);
					}
				}
			}
		} catch (Exception ignored) {
		}
	}

	private void saveRedeemedCodes() {
		try {
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("frozen-recruiting");
			Files.createDirectories(dir);
			Path p = dir.resolve("redeemed-codes.txt");
			Files.write(p, redeemedCodes, StandardCharsets.UTF_8);
		} catch (Exception ignored) {
		}
	}

	public static synchronized CosmeticService get() {
		if (INSTANCE == null) {
			INSTANCE = new CosmeticService();
		}

		return INSTANCE;
	}

	public CosmeticProvider provider() {
		return provider;
	}

	public CosmeticStorage storage() {
		return storage;
	}

	public boolean isAvailable() {
		return true; // local provider is always available
	}

	public List<String> ownedCosmetics() {
		return new ArrayList<>(storage.get().ownedCosmetics);
	}

	public String equippedId() {
		return storage.get().equippedCosmetic;
	}

	public void grant(String cosmeticId, String code) {
		storage.grant(cosmeticId);
		if (code != null && !code.isBlank()) {
			redeemedCodes.add(code.trim());
			saveRedeemedCodes();
		}
	}

	public boolean equip(String cosmeticId) {
		return storage.equip(cosmeticId);
	}

	public void unequip() {
		storage.unequip();
	}

	public boolean alreadyRedeemed(String code) {
		return code != null && redeemedCodes.contains(code.trim());
	}

	/**
	 * Scans {@code <gameDir>/config/frozen-recruiting/codes.txt} (and the game dir root's
	 * {@code codes.txt}) for codes, redeeming each valid one. Returns the number redeemed.
	 */
	public int scanCodesFile() {
		int redeemed = 0;
		Path[] candidates = new Path[] {
				FabricLoader.getInstance().getConfigDir().resolve("frozen-recruiting").resolve("codes.txt"),
				FabricLoader.getInstance().getGameDir().resolve("codes.txt"),
				FabricLoader.getInstance().getGameDir().resolve("frozen-recruiting-codes.txt")
		};

		for (Path p : candidates) {
			if (!Files.exists(p)) {
				continue;
			}

			try {
				for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
					String trimmed = line.trim();
					if (trimmed.isEmpty() || trimmed.startsWith("#")) {
						continue;
					}

					CodeCodec.VerifyResult result = CodeCodec.verify(trimmed);
					if (!result.ok()) {
						continue;
					}

					if (alreadyRedeemed(trimmed)) {
						continue;
					}

					grant(result.cosmeticId(), trimmed);
					equip(result.cosmeticId());
					redeemed++;
				}
			} catch (Exception ignored) {
			}
		}

		return redeemed;
	}
}