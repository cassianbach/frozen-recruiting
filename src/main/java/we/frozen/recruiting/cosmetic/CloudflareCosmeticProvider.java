package we.frozen.recruiting.cosmetic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Cosmetic provider that queries a Cloudflare Worker for cross-client cosmetic data.
 *
 * <p>Enabled only when {@code config/frozen-recruiting/cosmetic-api-url.txt} exists and contains
 * a valid URL. Otherwise, {@link #isAvailable()} returns false and the renderer falls back to
 * local-only cosmetics.
 *
 * <p>Results are cached with a TTL to avoid spamming the worker. Network calls are made on a
 * background thread; the render thread only ever reads the cache.
 */
public final class CloudflareCosmeticProvider implements CosmeticProvider {
	private static final Gson GSON = new Gson();
	private static final long CACHE_TTL_MILLIS = 300_000L; // 5 minutes

	private final String baseUrl;
	private final HttpClient client;
	private final ConcurrentHashMap<UUID, CachedProfile> cache = new ConcurrentHashMap<>();

	public CloudflareCosmeticProvider(String baseUrl) {
		this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
		this.client = HttpClient.newHttpClient();
	}

	public static CloudflareCosmeticProvider fromConfig() {
		try {
			var path = FabricLoader.getInstance().getConfigDir()
					.resolve("frozen-recruiting").resolve("cosmetic-api-url.txt");

			if (java.nio.file.Files.exists(path)) {
				String url = java.nio.file.Files.readString(path).trim();
				if (!url.isEmpty()) {
					return new CloudflareCosmeticProvider(url);
				}
			}
		} catch (Exception ignored) {
		}

		return null;
	}

	@Override
	public CosmeticProfile getProfile(UUID playerUuid) {
		if (playerUuid == null || baseUrl.isEmpty()) {
			return null;
		}

		CachedProfile cached = cache.get(playerUuid);
		if (cached != null && !cached.isExpired()) {
			return cached.profile;
		}

		// Trigger an async fetch if not cached or expired.
		if (cached == null || cached.isExpired()) {
			fetchAsync(playerUuid);
		}

		return cached == null ? null : cached.profile;
	}

	@Override
	public boolean isAvailable() {
		return !baseUrl.isEmpty();
	}

	@Override
	public void refresh() {
		cache.clear();
	}

	/**
	 * Registers a cosmetic for the local player with the worker. Call after redeeming a code.
	 */
	public void register(UUID playerUuid, String cosmeticId, Rarity rarity, String playerName) {
		if (playerUuid == null || baseUrl.isEmpty()) {
			return;
		}

		JsonObject payload = new JsonObject();
		payload.addProperty("uuid", playerUuid.toString());
		payload.addProperty("cosmetic", cosmeticId);
		payload.addProperty("rarity", rarity.name());
		payload.addProperty("name", playerName);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/register"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
				.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(r -> {
					if (r.statusCode() == 200) {
						cache.put(playerUuid, new CachedProfile(
								buildProfile(playerName, cosmeticId), System.currentTimeMillis()));
					}
				});
	}

	private void fetchAsync(UUID playerUuid) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/lookup?uuid=" + playerUuid))
				.GET()
				.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(r -> {
					if (r.statusCode() == 200) {
						try {
							JsonObject obj = GSON.fromJson(r.body(), JsonObject.class);
							String cosmetic = obj.has("cosmetic") ? obj.get("cosmetic").getAsString() : null;
							String name = obj.has("name") ? obj.get("name").getAsString() : null;
							CosmeticProfile profile = buildProfile(name, cosmetic);
							cache.put(playerUuid, new CachedProfile(profile, System.currentTimeMillis()));
						} catch (Exception ignored) {
						}
					}
				});
	}

	private static CosmeticProfile buildProfile(String playerName, String cosmeticId) {
		CosmeticProfile profile = new CosmeticProfile(playerName == null ? "" : playerName);
		if (cosmeticId != null && !cosmeticId.isBlank()) {
			profile.grant(cosmeticId);
			profile.equip(cosmeticId);
		}

		return profile;
	}

	private static final class CachedProfile {
		final CosmeticProfile profile;
		final long fetchedAt;

		CachedProfile(CosmeticProfile profile, long fetchedAt) {
			this.profile = profile;
			this.fetchedAt = fetchedAt;
		}

		boolean isExpired() {
			return System.currentTimeMillis() - fetchedAt > CACHE_TTL_MILLIS;
		}
	}
}