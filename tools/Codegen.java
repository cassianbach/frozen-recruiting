import java.nio.file.*;
import java.security.*;
import java.util.Base64;
import java.util.Set;

public class Codegen {
    private static final String PREFIX = "FR1";

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String cmd = args[0].toLowerCase();
        switch (cmd) {
            case "keygen" -> keygen(args);
            case "mint" -> mint(args);
            case "list" -> listCosmetics();
            default -> {
                System.out.println("Unknown command: " + cmd);
                printUsage();
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java Codegen.java keygen --out <dir>");
        System.out.println("  java Codegen.java mint  --key <private.key> --player <name> --cosmetic <id> [--rarity <tier>]");
        System.out.println("  java Codegen.java list");
    }

    private static void keygen(String[] args) throws Exception {
        String outDir = "keys";
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--out") && i + 1 < args.length) {
                outDir = args[++i];
            }
        }

        Files.createDirectories(Paths.get(outDir));

        KeyPairGenerator kp = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = kp.generateKeyPair();

        String privB64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pubB64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        Files.writeString(Paths.get(outDir, "private.key"), privB64);
        Files.writeString(Paths.get(outDir, "public.key"), pubB64);

        try {
            Files.setPosixFilePermissions(Paths.get(outDir, "private.key"),
                    Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
        }

        System.out.println("=== Key pair generated ===");
        System.out.println("Private key: " + Paths.get(outDir, "private.key").toAbsolutePath());
        System.out.println("Public key : " + Paths.get(outDir, "public.key").toAbsolutePath());
        System.out.println();
        System.out.println("Copy the public key into the mod's resource file:");
        System.out.println("  src/main/resources/assets/frozen-recruiting/cosmetic-public-key.txt");
        System.out.println();
        System.out.println(pubB64);
    }

    private static void mint(String[] args) throws Exception {
        String keyPath = null;
        String player = null;
        String cosmetic = null;
        String rarity = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--key" -> keyPath = args[++i];
                case "--player" -> player = args[++i];
                case "--cosmetic" -> cosmetic = args[++i];
                case "--rarity" -> rarity = args[++i];
            }
        }

        if (keyPath == null || player == null || cosmetic == null) {
            System.out.println("Missing required args. Need --key, --player, --cosmetic.");
            printUsage();
            return;
        }

        String privB64 = Files.readString(Paths.get(keyPath)).trim();
        byte[] privBytes = Base64.getDecoder().decode(privB64);

        java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(privBytes);
        java.security.PrivateKey privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(spec);

        String resolvedRarity = rarity != null ? rarity.toUpperCase() : rarityFor(cosmetic);
        if (resolvedRarity == null) {
            System.out.println("Unknown cosmetic: " + cosmetic + ". Run `java Codegen.java list`.");
            return;
        }

        long issuedAt = System.currentTimeMillis();

        String payload = player + "|" + cosmetic + "|" + resolvedRarity + "|" + issuedAt;
        byte[] payloadBytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(payloadBytes);
        byte[] signature = signer.sign();

        String code = PREFIX + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        System.out.println("=== Cosmetic code ===");
        System.out.println(code);
        System.out.println();
        System.out.println("Player    : " + player);
        System.out.println("Cosmetic  : " + cosmetic);
        System.out.println("Rarity    : " + resolvedRarity);
        System.out.println("Issued at : " + issuedAt);
        System.out.println();
        System.out.println("Give this code to " + player + ". They run: /frozenrecruiting cosmetic code " + code);
    }

    private static String rarityFor(String cosmeticId) {
        return switch (cosmeticId) {
            case "common_circle" -> "COMMON";
            case "uncommon_diamond", "uncommon_envoy" -> "UNCOMMON";
            case "rare_sparkle", "rare_pathfinder" -> "RARE";
            case "epic_star", "epic_warden" -> "EPIC";
            case "legendary_crown", "legendary_frozen" -> "LEGENDARY";
            default -> null;
        };
    }

    private static void listCosmetics() {
        System.out.println("Available cosmetics:");
        System.out.println("  common_circle       COMMON     grey circle");
        System.out.println("  uncommon_diamond    UNCOMMON   green diamond");
        System.out.println("  uncommon_envoy      UNCOMMON   green envoy");
        System.out.println("  rare_sparkle        RARE       blue sparkle");
        System.out.println("  rare_pathfinder     RARE       blue pathfinder");
        System.out.println("  epic_star           EPIC       purple star");
        System.out.println("  epic_warden         EPIC       purple warden");
        System.out.println("  legendary_crown     LEGENDARY  gold crown");
        System.out.println("  legendary_frozen    LEGENDARY  gold frozen elite");
    }
}