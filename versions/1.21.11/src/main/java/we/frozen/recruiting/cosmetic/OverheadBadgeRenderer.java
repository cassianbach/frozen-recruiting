package we.frozen.recruiting.cosmetic;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Renders an overhead badge above any player whose cosmetic is known to the configured
 * {@link CosmeticProvider}. Works for the local player (self-only) today and for everyone once a
 * relay provider is plugged in.
 */
public final class OverheadBadgeRenderer {
	private static CosmeticProvider provider;

	private OverheadBadgeRenderer() {
	}

	public static void register(CosmeticProvider p) {
		provider = p;
		WorldRenderEvents.AFTER_ENTITIES.register(ctx -> render(ctx));
	}

	public static void setProvider(CosmeticProvider p) {
		provider = p;
	}

	private static void render(WorldRenderContext ctx) {
		if (provider == null || !provider.isAvailable()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null || client.player == null) {
			return;
		}

		Camera camera = ctx.gameRenderer().getCamera();
		Vec3d camPos = camera.getCameraPos();
		TextRenderer textRenderer = client.textRenderer;

		for (PlayerEntity player : client.world.getPlayers()) {
			Vec3d pos = player.getEntityPos().add(0, player.getHeight() + 0.5, 0);

			// Distance culling: skip players too far away (avoids querying the worker).
			double distSq = pos.squaredDistanceTo(camPos);
			if (distSq > 64 * 64) {
				continue;
			}

			CosmeticProfile profile = provider.getProfile(player.getUuid());
			if (profile == null || profile.equippedCosmetic == null || profile.equippedCosmetic.isBlank()) {
				continue;
			}

			CosmeticDef cosmetic = CosmeticCatalog.get(profile.equippedCosmetic);
			if (cosmetic == null) {
				continue;
			}

			String text = cosmetic.rarity().glyph + " " + cosmetic.rarity().displayName;
			int color = cosmetic.rarity().color | 0xFF000000;

			// Build a CameraRenderState for the submitLabel call.
			net.minecraft.client.render.state.CameraRenderState cameraState = new net.minecraft.client.render.state.CameraRenderState();
			cameraState.pos = camPos;
			cameraState.orientation = camera.getRotation();
			cameraState.initialized = true;
			cameraState.blockPos = camera.getBlockPos();

			ctx.commandQueue().submitLabel(
					ctx.matrices(),
					pos,
					color,
					Text.literal(text),
					true, // seeThrough
					0x00F000F0, // light
					64.0, // maxDistance
					cameraState);
		}
	}
}