package we.frozen.recruiting.cosmetic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Cosmetic provider that queries a Cloudflare Worker for cross-client cosmetic data.
 *
 * <p>Optimized for large servers:
 * <ul>
 *   <li>Batch lookups: accumulates UUIDs and sends them in one request (up to 50 per batch)</li>
 *   <li>Long TTL: 1 hour positive, 2 hours negative</li>
 *   <li>Fetch guard: only one fetch per UUID at a time</li>
 *   <li>Stale-while-revalidate: serves stale data while refreshing in background</li>
 * </ul>
 */
public final class CloudflareCosmeticProvider implements CosmeticProvider {
	private static final Gson GSON = new Gson();

	/** Positive cache (player has a cosmetic): 1 hour. */
	private static final long POSITIVE_TTL_MILLIS = 3_600_000L; // 1 hour
	/** Negative cache (player has no cosmetic): 2 hours. */
	private static final long NEGATIVE_TTL_MILLIS = 7_200_000L; // 2 hours
	/** Max UUIDs per batch request. */
	private static final int BATCH_SIZE = 50;
	/** Delay before flushing the pending batch (ms). */
	private static final long BATCH_DELAY_MILLIS = 100L;

	private final String baseUrl;
	private final HttpClient client;
	private final ConcurrentHashMap<UUID, CachedProfile> cache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, AtomicBoolean> fetching = new ConcurrentHashMap<>();
	private final ConcurrentLinkedQueue<UUID> pendingBatch = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean batchScheduled = new AtomicBoolean(false);

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

		// Stale-while-revalidate: serve stale data immediately, batch-fetch in background.
		AtomicBoolean flag = fetching.computeIfAbsent(playerUuid, k -> new AtomicBoolean(false));
		if (flag.compareAndSet(false, true)) {
			pendingBatch.offer(playerUuid);
			scheduleBatch();
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
		pendingBatch.clear();
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

	private void scheduleBatch() {
		if (batchScheduled.compareAndSet(false, true)) {
			// Schedule a delayed batch flush to collect more UUIDs.
			new Thread(() -> {
				try {
					Thread.sleep(BATCH_DELAY_MILLIS);
				} catch (InterruptedException ignored) {
				}

				flushBatch();
				batchScheduled.set(false);
			}, "frozen-recruiting-batch").start();
		}
	}

	private void flushBatch() {
		// Collect all pending UUIDs (up to BATCH_SIZE).
		List<UUID> batch = new ArrayList<>();
		UUID uuid;
		while ((uuid = pendingBatch.poll()) != null && batch.size() < BATCH_SIZE) {
			batch.add(uuid);
		}

		if (batch.isEmpty()) {
			return;
		}

		// Build the batch URL.
		StringBuilder sb = new StringBuilder(baseUrl + "/lookup?uuids=");
		for (int i = 0; i < batch.size(); i++) {
			if (i > 0) sb.append(",");
			sb.append(batch.get(i));
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(sb.toString()))
				.GET()
				.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenAccept(r -> {
					try {
						if (r.statusCode() == 200) {
							com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(r.body(), com.google.gson.JsonObject.class);
							for (UUID u : batch) {
								String key = u.toString();
								if (obj.has(key) && !obj.get(key).isJsonNull()) {
									com.google.gson.JsonObject entry = obj.getAsJsonObject(key);
									String cosmetic = entry.has("cosmetic") ? entry.get("cosmetic").getAsString() : null;
									String name = entry.has("name") ? entry.get("name").getAsString() : null;
									cache.put(u, new CachedProfile(buildProfile(name, cosmetic), System.currentTimeMillis(), true));
								} else {
									// 404 = no cosmetic. Cache as negative.
									cache.put(u, new CachedProfile(null, System.currentTimeMillis(), false));
								}
							}
						} else {
							// Error — cache all as negative to avoid hammering.
							for (UUID u : batch) {
								cache.put(u, new CachedProfile(null, System.currentTimeMillis(), false));
							}
						}
					} catch (Exception ignored) {
					} finally {
						for (UUID u : batch) {
							AtomicBoolean flag = fetching.get(u);
							if (flag != null) flag.set(false);
						}
					}
				})
				.exceptionally(e -> {
					for (UUID u : batch) {
						AtomicBoolean flag = fetching.get(u);
						if (flag != null) flag.set(false);
					}
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