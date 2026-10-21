package we.frozen.recruiting.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Wires up the Frozen Recruiting keybind and the right-click-to-send interaction.
 *
 * <h2>Why this works on servers with right-click ping plugins</h2>
 * Fabric injects {@link UseEntityCallback} into
 * {@code ClientPlayerInteractionManager#interactEntityAtLocation} as a <em>cancellable</em>
 * {@code @Inject} on the interaction call. When a listener returns anything other than
 * {@link ActionResult#PASS}, Fabric skips the packet send entirely and cancels the rest of the
 * vanilla interaction.
 *
 * <p>The previous version of this mod returned {@code PASS}, which told Fabric to carry on with
 * the normal interaction. The vanilla interact packet was then sent to the server, the server-side
 * ping plugin saw it, and both actions fired at once. Returning {@link ActionResult#SUCCESS} here
 * suppresses that packet, so the server never sees the vanilla right-click and never runs its ping
 * handler.
 */
public final class RecruitingInteraction {
	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(FrozenIds.MOD_ID, "category"));

	private static KeyBinding openMenuKey;

	private RecruitingInteraction() {
	}

	public static void register() {
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.frozen-recruiting.open_gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.wasPressed()) {
				openMenuKey.setPressed(false);
				openMenuClient(client);
			}
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient()) {
				return ActionResult.PASS;
			}

			MinecraftClient client = MinecraftClient.getInstance();

			// Shift + right-click keeps the vanilla interaction alive so servers that use
			// right-click for ping keep working.
			if (isSneaking(client, player)) {
				return ActionResult.PASS;
			}

			if (!(entity instanceof PlayerEntity target)) {
				return ActionResult.PASS;
			}

			if (!RecruitMessageStore.isArmed()) {
				ChatFeedback.error(client, "frozen_recruiting.msg.empty");
				// Still cancel: otherwise the interact packet goes out and any server-side
				// right-click handler fires while the player only asked for a message send.
				return ActionResult.SUCCESS;
			}

			RecruitMessageStore.sendTo(target.getGameProfile().name());
			return ActionResult.SUCCESS;
		});
	}

	private static boolean isSneaking(MinecraftClient client, PlayerEntity player) {
		if (client.getWindow() == null) {
			return false;
		}

		// Honour a sneak keybind as well as the vanilla sneak state.
		return player.isSneaking() || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	private static void openMenuClient(MinecraftClient client) {
		if (client.player == null) {
			return;
		}

		client.setScreen(new RecruitingScreen(null));
	}
}