package we.frozen.recruiting.cosmetic;

import java.util.UUID;

/**
 * Source of cosmetic information for a player. The renderer and UI only ever talk to this
 * interface, so the local-only implementation can later be swapped for a server-relay or
 * cloud-backend implementation without touching the rendering code.
 */
public interface CosmeticProvider {
	/**
	 * Returns the cosmetic profile for the given player, or {@code null} if none is known.
	 *
	 * <p>May return {@code null} while a remote provider is still loading; callers must treat
	 * {@code null} as "no cosmetic" and never throw.
	 */
	CosmeticProfile getProfile(UUID playerUuid);

	/** Whether this provider can currently answer queries (i.e. is it online / configured). */
	boolean isAvailable();

	/** Asks the provider to refresh its cached data. Safe to call from the render thread. */
	void refresh();
}