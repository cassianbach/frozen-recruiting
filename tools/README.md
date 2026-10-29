# Frozen Recruiting — Cosmetic Code Signer

A tiny standalone tool for minting cosmetic codes. The private key never ships in the mod jar.

## Usage

### Generate a keypair (once)

```
java Codegen.java keygen --out ./keys
```

This writes `keys/private.key` and `keys/public.key`. Keep `private.key` secret — it is the
only thing that can mint valid codes.

### Embed the public key

Copy the contents of `keys/public.key` into the mod's resource file:

```
src/main/resources/assets/frozen-recruiting/cosmetic-public-key.txt
```

Then rebuild the mod. From then on, the mod verifies codes against your public key.

### Mint a code

```
java Codegen.java mint --key ./keys/private.key --player Steve --cosmetic legendary_crown
```

Optional: `--rarity LEGENDARY` (defaults to the cosmetic's rarity).

### Available cosmetics

```
java Codegen.java list
```

### Security note

The private key is the only thing that can mint codes. Anyone who obtains it can forge
legendary codes. Keep it offline and never commit it. The mod jar contains only the public
key, so a determined user could still extract it and forge codes for their own account — but
they cannot forge codes for other players (codes are bound to the player name).