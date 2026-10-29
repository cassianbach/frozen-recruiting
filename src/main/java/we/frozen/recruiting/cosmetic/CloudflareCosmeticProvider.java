package we.frozen.recruiting.cosmetic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Cosmetic provider that queries a Cloudflare Worker for cross-client cosmetic data.
 *
 * <p>Optimized for large servers: caches aggressively, only fetches once per player per TTL
 * window, and serves stale data while refreshing in the background.
 */
public final class CloudflareCosmeticProvider implements CosmeticProvider {
	private static final Gson GSON = new Gson();

	/** Positive cache (player has a cosmetic): 1 hour. */
	private static final long POSITIVE_TTL_MILLIS = 3_600_000L; // 1 hour
	/** Negative cache (player has no cosmetic): 2 hours. */
	private static final long NEGATIVE_TTL_MILLIS = 7_200_000L; // 2 hours

	private final String baseUrl;
	private final HttpClient client;
	private final ConcurrentHashMap<UUID, CachedProfile> cache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, AtomicBoolean> fetching = new ConcurrentHashMap<>();

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

		// Stale-while-revalidate: serve stale data immediately, fetch in background.
		// Only trigger one fetch per player at a time (fetch guard).
		if (cached == null || cached.isExpired()) {
			AtomicBoolean flag = fetching.computeIfAbsent(playerUuid, k -> new AtomicBoolean(false));
			if (flag.compareAndSet(false, true)) {
				fetchAsync(playerUuid, flag);
			}
		}

		// Return stale data while fetching (null if never fetched).
		return cached == null ? null : cached.profile;
	}

	@Override
	public boolean isAvailable() {
		return !baseUrl.isEmpty();
	}

	@Override
	public void refresh() {
		cache.clear();
		fetching.clear();
	}

	/**
	 * Registers a cosmetic for the local player with the worker. Call after redeeming a code.
	 */
	public void register(UUID playerUuid, String cosmeticId, Rarity rarity, String playerName) {
		if (playerUuid == null || baseUrl.isEmpty()) {
			return;
		}

		com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
		payload.addProperty("uuid", playerUuid.toString());
		payload.addProperty("cosmetic", cosmeticId);
		payload.addProperty("rarity", rarity.name());
		payload.addProperty("name", playerName);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/register"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(new com.google.gson.Gson().toJson(payload)))
				.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(r -> {
					if (r.statusCode() == 200) {
						cache.put(playerUuid, new CachedProfile(
								buildProfile(playerName, cosmeticId), System.currentTimeMillis(), true));
					}
				});
	}

	private void fetchAsync(UUID playerUuid, AtomicBoolean flag) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/lookup?uuid=" + playerUuid))
				.GET()
				.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(r -> {
					try {
						if (r.statusCode() == 200) {
							com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(r.body(), com.google.gson.JsonObject.class);
							String cosmetic = obj.has("cosmetic") ? obj.get("cosmetic").getAsString() : null;
							String name = obj.has("name") ? obj.get("name").getAsString() : null;
							CosmeticProfile profile = buildProfile(name, cosmetic);
							cache.put(playerUuid, new CachedProfile(profile, System.currentTimeMillis(), cosmetic != null));
						} else {
							// 404 = no cosmetic. Cache as negative.
							cache.put(playerUuid, new CachedProfile(null, System.currentTimeMillis(), false));
						}
					} catch (Exception ignored) {
					} finally {
						flag.set(false);
					}
				})
				.exceptionally(e -> {
					flag.set(false);
					return null;
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
		final boolean hasCosmetic;

		CachedProfile(CosmeticProfile profile, long fetchedAt, boolean hasCosmetic) {
			this.profile = profile;
			this.fetchedAt = fetchedAt;
			this.hasCosmetic = hasCosmetic;
		}

		boolean isExpired() {
			long ttl = hasCosmetic ? POSITIVE_TTL_MILLIS : NEGATIVE_TTL_MILLIS;
			return System.currentTimeMillis() - fetchedAt > ttl;
		}
	}
}