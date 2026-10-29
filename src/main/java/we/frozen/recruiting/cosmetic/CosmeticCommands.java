package we.frozen.recruiting.cosmetic;
import java.util.List;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import we.frozen.recruiting.client.TextHelper;
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
								.then(ClientCommandManager.literal("codes")
								.then(ClientCommandManager.argument("codes", StringArgumentType.greedyString())
								.executes(CosmeticCommands::redeemCodes)))
								.then(ClientCommandManager.literal("mint")
										.then(ClientCommandManager.argument("player", StringArgumentType.word())
												.then(ClientCommandManager.argument("cosmetic", StringArgumentType.word())
														.executes(CosmeticCommands::mintCode))))
								.then(ClientCommandManager.literal("scan").executes(CosmeticCommands::scanFiles))
								.then(ClientCommandManager.literal("keyinfo").executes(CosmeticCommands::keyInfo))
								.then(ClientCommandManager.literal("keygen").executes(CosmeticCommands::keygen)))));
	}
	private static int listOwn(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		CosmeticService service = CosmeticService.get();
		if (!service.isAvailable()) {
			source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7fNo cosmetics configured."));
			return 0;
		}
		List<String> owned = service.ownedCosmetics();
		if (owned.isEmpty()) {
			source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7fYou own no cosmetics. Redeem a code with "
					+ "\u00a77/frozenrecruiting cosmetic code <code>\u00a7f."));
			return 0;
		}
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a77your cosmetics:"));
		for (String id : owned) {
			CosmeticDef def = CosmeticCatalog.get(id);
			if (def == null) {
				continue;
			}
			String marker = id.equals(service.equippedId()) ? " \u00a7a[Equipped]" : "";
			source.sendFeedback(TextHelper.parse("  \u00a7f" + def.rarity().glyph + " " + def.displayName()
					+ " \u00a77(" + def.rarity().displayName + ")" + marker));
		}
		return owned.size();
	}
	private static int equip(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String id = StringArgumentType.getString(ctx, "id");
		CosmeticService service = CosmeticService.get();
		if (service.equip(id)) {
			CosmeticDef def = CosmeticCatalog.get(id);
			source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7aEquipped: \u00a7f"
					+ (def == null ? id : def.displayName()) + " \u00a77(" + (def == null ? "?" : def.rarity().displayName) + ")"));
		} else {
			source.sendError(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7cYou do not own \"" + id + "\"."));
		}
		return 1;
	}
	private static int unequip(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		CosmeticService.get().unequip();
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7aBadge unequipped."));
		return 1;
	}
	private static int redeemCode(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String code = StringArgumentType.getString(ctx, "code");
		CosmeticService service = CosmeticService.get();
		CodeCodec.VerifyResult result = CodeCodec.verify(code);
		if (!result.ok()) {
			source.sendError(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7c" + result.reason()));
			return 0;
		}
		// Reject codes already redeemed by this client.
		if (service.alreadyRedeemed(code)) {
			source.sendError(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7cThis code has already been redeemed."));
			return 0;
		}
		service.grant(result.cosmeticId(), code);
		service.equip(result.cosmeticId());
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7aRedeemed: \u00a7f" + result.cosmeticId()
				+ " \u00a77(" + result.rarity().displayName + ")"));
		// Push to the worker so other mod-users can see it.
		java.util.UUID uuid = CosmeticStorage.localUuid();
		if (uuid != null) {
			CosmeticProvider provider = service.provider();
			if (provider instanceof CloudflareCosmeticProvider cf) {
				cf.register(uuid, result.cosmeticId(), result.rarity(),
						MinecraftClient.getInstance().player.getName().getString());
			}
		}
		return 1;
	}
	private static int redeemCodes(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		String allCodes = StringArgumentType.getString(ctx, "codes");
		int redeemed = 0;
		int failed = 0;
		StringBuilder sb = new StringBuilder();
		// Split on whitespace (spaces, newlines, tabs).
		for (String code : allCodes.split("\\s+")) {
			code = code.trim();
			if (code.isEmpty()) {
				continue;
			}
			CosmeticService service = CosmeticService.get();
			CodeCodec.VerifyResult result = CodeCodec.verify(code);
			if (!result.ok()) {
				failed++;
				continue;
			}
			if (service.alreadyRedeemed(code)) {
				failed++;
				continue;
			}
			service.grant(result.cosmeticId(), code);
			service.equip(result.cosmeticId());
			redeemed++;
			// Push to the worker.
			java.util.UUID uuid = CosmeticStorage.localUuid();
			if (uuid != null) {
				CosmeticProvider provider = service.provider();
				if (provider instanceof CloudflareCosmeticProvider cf) {
					cf.register(uuid, result.cosmeticId(), result.rarity(),
							MinecraftClient.getInstance().player.getName().getString());
				}
			}
		}
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7fRedeemed " + redeemed + " code(s)."));
		if (failed > 0) {
			source.sendFeedback(TextHelper.parse("\u00a77" + failed + " code(s) were invalid or already redeemed."));
		}
		return redeemed;
	}
	private static int scanFiles(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		int redeemed = CosmeticService.get().scanCodesFile();
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7fScanned codes file: "
				+ redeemed + " new code(s) redeemed."));
		return redeemed;
	}
	private static int keyInfo(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7fPublic key: "
				+ (CodeCodec.isAvailable() ? CodeCodec.publicKeyFingerprint() : "\u00a7cnot configured")));
		source.sendFeedback(TextHelper.parse("\u00a77Config dir: " + FabricLoader.getInstance().getConfigDir().toAbsolutePath()));
		return 1;
	}
	private static int mintCode(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		MinecraftClient client = MinecraftClient.getInstance();
		// Only the admin account can mint codes.
		if (client == null || client.player == null
				|| !"chaosfloh2011".equalsIgnoreCase(client.player.getName().getString())) {
			source.sendError(TextHelper.parse("\u00a7cYou are not authorized to mint codes."));
			return 0;
		}
		String player = StringArgumentType.getString(ctx, "player");
		String cosmeticId = StringArgumentType.getString(ctx, "cosmetic");
		CosmeticDef def = CosmeticCatalog.get(cosmeticId);
		if (def == null) {
			source.sendError(TextHelper.parse("\u00a7cUnknown cosmetic: " + cosmeticId));
			source.sendFeedback(TextHelper.parse("\u00a77Run \u00a7f/frozenrecruiting cosmetic list \u00a77to see available cosmetics."));
			return 0;
		}
		// Read the private key from an external file (never in the jar).
		java.nio.file.Path keyFile = FabricLoader.getInstance().getConfigDir()
				.resolve("frozen-recruiting").resolve("private.key");
		if (!java.nio.file.Files.exists(keyFile)) {
			source.sendError(TextHelper.parse("\u00a7cPrivate key not found at: " + keyFile));
			source.sendFeedback(TextHelper.parse("\u00a77Place your private.key file there. Generate one with:"));
			source.sendFeedback(TextHelper.parse("\u00a77java Codegen.java keygen --out <dir>"));
			return 0;
		}
		try {
			String privB64 = java.nio.file.Files.readString(keyFile).trim();
			byte[] privBytes = java.util.Base64.getDecoder().decode(privB64);
			java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(privBytes);
			java.security.PrivateKey privateKey = java.security.KeyFactory.getInstance("Ed25519").generatePrivate(spec);
			long issuedAt = System.currentTimeMillis();
			String payload = player + "|" + cosmeticId + "|" + def.rarity().name() + "|" + issuedAt;
			byte[] payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
			signer.initSign(privateKey);
			signer.update(payloadBytes);
			byte[] signature = signer.sign();
			String code = "FR1."
					+ java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes) + "."
					+ java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
			source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7aMinted code for \u00a7f" + player + "\u00a7a:"));
			source.sendFeedback(Text.literal(code));
			source.sendFeedback(TextHelper.parse("\u00a77Cosmetic: \u00a7f" + def.displayName() + " \u00a77(" + def.rarity().displayName + ")"));
			// Copy to clipboard so you don't have to select it by hand.
			try {
				MinecraftClient.getInstance().keyboard.setClipboard(code);
				source.sendFeedback(TextHelper.parse("\u00a7aCopied to clipboard!"));
			} catch (Exception ignored) {
			}
			// Also append to a log file for easy copy-paste later.
			try {
				java.nio.file.Path logFile = FabricLoader.getInstance().getConfigDir()
						.resolve("frozen-recruiting").resolve("minted-codes.txt");
				java.nio.file.Files.createDirectories(logFile.getParent());
				String entry = player + " | " + cosmeticId + " | " + code + "\n";
				java.nio.file.Files.writeString(logFile, entry, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
				source.sendFeedback(TextHelper.parse("\u00a77Also saved to: " + logFile));
			} catch (Exception ignored) {
			}
			source.sendFeedback(TextHelper.parse("\u00a77Give this code to " + player + ". They run: /frozenrecruiting cosmetic code " + code));
			return 1;
		} catch (Exception e) {
			source.sendError(TextHelper.parse("\u00a7cFailed to mint code: " + e.getMessage()));
			return 0;
		}
	}
	private static int keygen(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		MinecraftClient client = MinecraftClient.getInstance();
		// Only the admin account can generate keys.
		if (client == null || client.player == null
				|| !"chaosfloh2011".equalsIgnoreCase(client.player.getName().getString())) {
			source.sendError(TextHelper.parse("\u00a7cYou are not authorized to generate keys."));
			return 0;
		}
		try {
			java.security.KeyPairGenerator kp = java.security.KeyPairGenerator.getInstance("Ed25519");
			java.security.KeyPair pair = kp.generateKeyPair();
			String privB64 = java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
			String pubB64 = java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
			java.nio.file.Path dir = FabricLoader.getInstance().getConfigDir().resolve("frozen-recruiting");
			java.nio.file.Files.createDirectories(dir);
			java.nio.file.Path privFile = dir.resolve("private.key");
			java.nio.file.Path pubFile = dir.resolve("cosmetic-public-key.txt");
			java.nio.file.Files.writeString(privFile, privB64);
			java.nio.file.Files.writeString(pubFile, pubB64);
			// Lock down the private key where the filesystem supports POSIX permissions.
			try {
				java.nio.file.Files.setPosixFilePermissions(privFile,
						java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
								java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
			} catch (UnsupportedOperationException ignored) {
			}
			source.sendFeedback(TextHelper.parse("\u00a7b[Frozen Recruiting] \u00a7aKey pair generated!"));
			source.sendFeedback(TextHelper.parse("\u00a77Private key: \u00a7f" + privFile));
			source.sendFeedback(TextHelper.parse("\u00a77Public key : \u00a7f" + pubFile));
			source.sendFeedback(TextHelper.parse("\u00a77You can now mint codes with:"));
			source.sendFeedback(TextHelper.parse("\u00a77/frozenrecruiting cosmetic mint <player> <cosmetic>"));
			return 1;
		} catch (Exception e) {
			source.sendError(TextHelper.parse("\u00a7cFailed to generate key: " + e.getMessage()));
			return 0;
		}
	}
}
