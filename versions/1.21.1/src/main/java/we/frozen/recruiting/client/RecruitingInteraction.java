package we.frozen.recruiting.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

/**
 * Wires up the Frozen Recruiting keybind and the right-click-to-send interaction.
 *
 * <h2>Why this works on servers with right-click ping plugins</h2>
 * Fabric injects {@link UseEntityCallback} into the client interaction method as a
 * <em>cancellable</em> {@code @Inject}. When a listener returns anything other than
 * {@link ActionResult#PASS}, Fabric skips the packet send entirely and cancels the rest of the
 * vanilla interaction.
 *
 * <p>The original version of this mod returned {@code PASS}, which told Fabric to carry on with
 * the normal interaction. The vanilla interact packet was then sent to the server, the server-side
 * ping plugin saw it, and both actions fired at once. Returning {@link ActionResult#SUCCESS} here
 * suppresses that packet, so the server never sees the vanilla right-click and never runs its ping
 * handler.
 */
public final class RecruitingInteraction {
	private static KeyBinding openMenuKey;

	/**
	 * Set when a right-click on a player was handled by this mod, so the held shield can be
	 * dropped on the next tick instead of sitting there blocking the view.
	 */
	private static boolean dropShieldNextTick = false;

	private RecruitingInteraction() {
	}

	public static void register() {
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.frozen-recruiting.open_gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_G,
				"key.categories.frozen-recruiting"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.wasPressed()) {
				openMenuKey.setPressed(false);
				openMenuClient(client);
			}

			// The vanilla "use item" path is what raises a shield. It runs after the
			// interaction we already handled, so drop the item use here to make sure the
			// shield never ends up blocking the screen.
			if (dropShieldNextTick) {
				dropShieldNextTick = false;

				if (client.player != null && client.player.isUsingItem()) {
					client.player.stopUsingItem();
				}
			}
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient()) {
				return ActionResult.PASS;
			}

			MinecraftClient client = MinecraftClient.getInstance();

			if (!(entity instanceof PlayerEntity target)) {
				return ActionResult.PASS;
			}

			// Shift + right-click keeps the vanilla interaction alive so servers that use
			// right-click for ping keep working.
			if (isSneaking(client, player)) {
				dropShieldNextTick = true;
				return ActionResult.PASS;
			}

			if (!RecruitMessageStore.isArmed()) {
				ChatFeedback.error(client, "frozen_recruiting.msg.empty");
				// Still cancel: otherwise the interact packet goes out and any server-side
				// right-click handler fires while the player only asked for a message send.
				dropShieldNextTick = true;
				return ActionResult.SUCCESS;
			}

			RecruitMessageStore.sendTo(target.getGameProfile().getName());
			dropShieldNextTick = true;
			return ActionResult.SUCCESS;
		});
	}

	private static boolean isSneaking(MinecraftClient client, PlayerEntity player) {
		if (client.getWindow() == null) {
			return false;
		}

		// Honour a sneak keybind as well as the vanilla sneak state.
		long handle = client.getWindow().getHandle();
		return player.isSneaking() || InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_LEFT_SHIFT)
				|| InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	private static void openMenuClient(MinecraftClient client) {
		if (client.player == null) {
			return;
		}

		client.setScreen(new RecruitingScreen(null));
	}
}