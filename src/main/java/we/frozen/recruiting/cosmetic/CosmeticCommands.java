package we.frozen.recruiting.cosmetic;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * {@code /frozenrecruiting cosmetic ...} subcommands for managing cosmetics.
 */
public final class CosmeticCommands {
	private CosmeticCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				dispatcher.register(ClientCommandManager.literal("frozenrecruiting")
						.then(ClientCommandManager.literal("cosmetic")
								.executes(CosmeticCommands::listOwn)
								.then(ClientCommandManager.literal("list").executes(CosmeticCommands::listOwn))
								.then(ClientCommandManager.literal("equip")
										.then(ClientCommandManager.argument("id", StringArgumentType.word())
												.executes(CosmeticCommands::equip)))
								.then(ClientCommandManager.literal("unequip").executes(CosmeticCommands::unequip))
								.then(ClientCommandManager.literal("code")
										.then(ClientCommandManager.argument("code", StringArgumentType.greedyString())
												.executes(CosmeticCommands::redeemCode)))
								.then(ClientCommandManager.literal("scan").executes(CosmeticCommands::scanFiles))
								.then(ClientCommandManager.literal("keyinfo").executes(CosmeticCommands::keyInfo)))));
	}

	private static int listOwn(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		CosmeticService service = CosmeticService.get();

		if (!service.isAvailable()) {
			source.sendFeedback(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§fNo cosmetics configured."));
			return 0;
		}

		List<String> owned = service.ownedCosmetics();
		if (owned.isEmpty()) {
			source.sendFeedback(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§fYou own no cosmetics. Redeem a code with "
					+ "Ã‚Â§7/frozenrecruiting cosmetic code <code>Ã‚Â§f."));
			return 0;
		}

		source.sendFeedback(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§7your cosmetics:"));
		for (String id : owned) {
			CosmeticDef def = CosmeticCatalog.get(id);
			if (def == null) {
				continue;
			}

			String marker = id.equals(service.equippedId()) ? " Ã‚Â§a[Equipped]" : "";
			source.sendFeedback(Text.literal("  Ã‚Â§f" + def.rarity().glyph + " " + def.displayName()
					+ " Ã‚Â§7(" + def.rarity().displayName + ")" + marker));
		}

		return owned.size();
	}

	private static int equip(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String id = StringArgumentType.getString(ctx, "id");

		CosmeticService service = CosmeticService.get();
		if (service.equip(id)) {
			CosmeticDef def = CosmeticCatalog.get(id);
			source.sendFeedback(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§aEquipped: Ã‚Â§f"
					+ (def == null ? id : def.displayName()) + " Ã‚Â§7(" + (def == null ? "?" : def.rarity().displayName) + ")"));
		} else {
			source.sendError(Text.literal("You do not own \"" + id + "\"."));
		}

		return 1;
	}

	private static int unequip(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		CosmeticService.get().unequip();
		source.sendFeedback(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§aBadge unequipped."));
		return 1;
	}

	private static int redeemCode(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String code = StringArgumentType.getString(ctx, "code");

		CosmeticService service = CosmeticService.get();
		CodeCodec.VerifyResult result = CodeCodec.verify(code);

		if (!result.ok()) {
			source.sendError(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§c" + result.reason()));
			return 0;
		}

		// Reject codes already redeemed by this client.
		if (service.alreadyRedeemed(code)) {
			source.sendError(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§cThis code has already been redeemed."));
			return 0;
		}

		service.grant(result.cosmeticId(), code);
		service.equip(result.cosmeticId());

		source.sendFeedback(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§aRedeemed: Ã‚Â§f" + result.cosmeticId()
				+ " Ã‚Â§7(" + result.rarity().displayName + ")"));

		// Push to the worker so other mod-users can see it.
		UUID uuid = CosmeticStorage.localUuid();
		if (uuid != null) {
			CosmeticProvider provider = service.provider();
			if (provider instanceof CloudflareCosmeticProvider cf) {
				cf.register(uuid, result.cosmeticId(), result.rarity(),
						MinecraftClient.getInstance().player.getName().getString());
			}
		}

		return 1;
	}

	private static int scanFiles(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		int redeemed = CosmeticService.get().scanCodesFile();
		source.sendFeedback(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§fScanned codes file: "
				+ redeemed + " new code(s) redeemed."));
		return redeemed;
	}

	private static int keyInfo(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		source.sendFeedback(Text.literal("Ã‚Â§b[Frozen Recruiting] Ã‚Â§fPublic key: "
				+ (CodeCodec.isAvailable() ? CodeCodec.publicKeyFingerprint() : "Ã‚Â§cnot configured")));
		source.sendFeedback(Text.literal("Ã‚Â§7Config dir: " + FabricLoader.getInstance().getConfigDir().toAbsolutePath()));
		return 1;
	}
}