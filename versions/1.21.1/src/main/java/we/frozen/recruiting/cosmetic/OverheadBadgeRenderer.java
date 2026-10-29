package we.frozen.recruiting.cosmetic;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * Renders an overhead badge above any player whose cosmetic is known to the configured
 * {@link CosmeticProvider}. Uses the pre-1.21.10 rendering API (TextRenderer.draw directly).
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

		Camera camera = ctx.camera();
		Vec3d camPos = camera.getPos();
		TextRenderer textRenderer = client.textRenderer;
		net.minecraft.client.util.math.MatrixStack matrixStack = ctx.matrixStack();
		net.minecraft.client.render.VertexConsumerProvider consumers = ctx.consumers();

		for (PlayerEntity player : client.world.getPlayers()) {
			CosmeticProfile profile = provider.getProfile(player.getUuid());
			if (profile == null || profile.equippedCosmetic == null || profile.equippedCosmetic.isBlank()) {
				continue;
			}

			CosmeticDef cosmetic = CosmeticCatalog.get(profile.equippedCosmetic);
			if (cosmetic == null) {
				continue;
			}

			Vec3d pos = player.getPos().add(0, player.getHeight() + 0.5, 0);

			double dx = pos.x - camPos.x;
			double dy = pos.y - camPos.y;
			double dz = pos.z - camPos.z;

			double distSq = dx * dx + dy * dy + dz * dz;
			if (distSq > 64 * 64) {
				continue;
			}

			String text = cosmetic.rarity().glyph + " " + cosmetic.rarity().displayName;
			int color = cosmetic.rarity().color | 0xFF000000;
			int width = textRenderer.getWidth(text);

			matrixStack.push();
			matrixStack.translate(dx, dy, dz);
			matrixStack.multiply(camera.getRotation());
			matrixStack.scale(-0.025f, -0.025f, 0.025f);

			org.joml.Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
			textRenderer.draw(text, -width / 2f, 0, color, false, positionMatrix, consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0x00F000F0);

			matrixStack.pop();
		}
	}
}
