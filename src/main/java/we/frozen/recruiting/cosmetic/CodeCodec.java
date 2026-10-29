package we.frozen.recruiting.cosmetic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

/**
 * Encodes and decodes cosmetic codes.
 *
 * <p>A code looks like {@code FR1.<base64url payload>.<base64url signature>} where the payload
 * is {@code playerName|cosmeticId|rarity|issuedAt} and the signature is an Ed25519 signature over
 * the payload bytes, verified against the public key shipped with the mod.
 *
 * <p>The signer tool (kept separate, holding the private key) mints codes; this class only
 * verifies them, so the mod jar never contains the signing key.
 */
public final class CodeCodec {
	private static final String PREFIX = "FR1";
	private static final String SEPARATOR = ".";

	private CodeCodec() {
	}

	/** Result of attempting to parse and verify a code. */
	public record VerifyResult(boolean ok, String reason, String playerName, String cosmeticId,
	                           Rarity rarity, long issuedAt) {
		public static VerifyResult fail(String reason) {
			return new VerifyResult(false, reason, null, null, null, 0L);
		}

		public static VerifyResult ok(String playerName, String cosmeticId, Rarity rarity, long issuedAt) {
			return new VerifyResult(true, "ok", playerName, cosmeticId, rarity, issuedAt);
		}
	}

	/**
	 * Verifies a code. Returns a result describing success or the failure reason.
	 *
	 * <p>The verification requires the public key to be configured; if it is not, codes cannot be
	 * redeemed at all.
	 */
	public static VerifyResult verify(String code) {
		if (code == null || code.isBlank()) {
			return VerifyResult.fail("empty code");
		}

		code = code.trim();
		String[] parts = code.split("\\.");
		if (parts.length != 3 || !parts[0].equals(PREFIX)) {
			return VerifyResult.fail("invalid format (expected FR1.<payload>.<signature>)");
		}

		PublicKey publicKey = loadPublicKey();
		if (publicKey == null) {
			return VerifyResult.fail("no public key configured â€” cannot verify codes");
		}

		byte[] payloadBytes;
		byte[] signatureBytes;

		try {
			payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
			signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
		} catch (IllegalArgumentException e) {
			return VerifyResult.fail("invalid base64");
		}

		// Verify the signature before trusting any field in the payload.
		try {
			Signature verifier = Signature.getInstance("Ed25519");
			verifier.initVerify(publicKey);
			verifier.update(payloadBytes);
			if (!verifier.verify(signatureBytes)) {
				return VerifyResult.fail("signature verification failed");
			}
		} catch (Exception e) {
			return VerifyResult.fail("signature error: " + e.getMessage());
		}

		// Payload is "playerName|cosmeticId|rarity|issuedAt".
		String payload = new String(payloadBytes, StandardCharsets.UTF_8);
		String[] fields = payload.split("\\|", -1);
		if (fields.length != 4) {
			return VerifyResult.fail("invalid payload");
		}

		String playerName = fields[0];
		String cosmeticId = fields[1];
		Rarity rarity = Rarity.fromName(fields[2]);
		long issuedAt;

		try {
			issuedAt = Long.parseLong(fields[3]);
		} catch (NumberFormatException e) {
			return VerifyResult.fail("invalid issuedAt");
		}

		if (playerName.isBlank() || cosmeticId.isBlank() || rarity == null) {
			return VerifyResult.fail("invalid fields");
		}

		if (!CosmeticCatalog.exists(cosmeticId)) {
			return VerifyResult.fail("unknown cosmetic id: " + cosmeticId);
		}

		// The cosmetic's own rarity must match the code's rarity.
		CosmeticDef def = CosmeticCatalog.get(cosmeticId);
		if (def.rarity() != rarity) {
			return VerifyResult.fail("rarity mismatch (code says " + rarity + ", cosmetic is " + def.rarity() + ")");
		}

		// Bind the code to the receiving player.
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.player != null) {
			String localName = client.player.getName().getString();
			if (localName != null && !localName.isBlank() && !playerName.equalsIgnoreCase(localName)) {
				return VerifyResult.fail("this code is for " + playerName + ", not " + localName);
			}
		}

		return VerifyResult.ok(playerName, cosmeticId, rarity, issuedAt);
	}

	/**
	 * Loads the public key from the override file in the config dir, or falls back to the key
	 * embedded in the mod jar. Returns null if neither is available (code redemption disabled).
	 */
	public static PublicKey loadPublicKey() {
		// Override file takes precedence (allows rotation without rebuilding the mod).
		try {
			Path override = FabricLoader.getInstance().getConfigDir()
					.resolve("frozen-recruiting").resolve("cosmetic-public-key.txt");

			if (java.nio.file.Files.exists(override)) {
				String b64 = java.nio.file.Files.readString(override).trim();
				if (!b64.isEmpty()) {
					byte[] decoded = Base64.getDecoder().decode(b64);
					X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
					return KeyFactory.getInstance("Ed25519").generatePublic(spec);
				}
			}
		} catch (Exception ignored) {
		}

		// Fall back to the embedded key in the mod jar.
		try {
			var resource = CodeCodec.class.getResourceAsStream("/assets/frozen-recruiting/cosmetic-public-key.txt");
			if (resource != null) {
				String b64 = new String(resource.readAllBytes(), StandardCharsets.UTF_8).trim();
				if (!b64.isEmpty()) {
					byte[] decoded = Base64.getDecoder().decode(b64);
					X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
					return KeyFactory.getInstance("Ed25519").generatePublic(spec);
				}
			}
		} catch (Exception ignored) {
		}

		return null;
	}

	/**
	 * Returns true if code redemption is available (public key is configured).
	 */
	public static boolean isAvailable() {
		return loadPublicKey() != null;
	}

	/**
	 * Returns a short fingerprint of the active public key, for display in the keyinfo command.
	 */
	public static String publicKeyFingerprint() {
		PublicKey pk = loadPublicKey();
		if (pk == null) {
			return "not configured";
		}

		try {
			byte[] encoded = pk.getEncoded();
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(encoded);
			StringBuilder sb = new StringBuilder();

			for (int i = 0; i < 6 && i < hash.length; i++) {
				sb.append(String.format("%02x", hash[i]));
			}

			return sb.toString();
		} catch (Exception e) {
			return "error";
		}
	}
}