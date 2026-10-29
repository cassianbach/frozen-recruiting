# Frozen Recruiting

A Minecraft **Fabric** client mod. Write a recruit message in a simple and sleek
GUI, then **right-click a player** to send it to them as `/tell`.

---

## Downloads

| Minecraft | Fabric Loader | Fabric API | File |
|---|---|---|---|
| **1.21.11** | 0.19.5+ | 0.141.6+1.21.11 | `frozen-recruiting-1.21.11.jar` |
| **1.21.1** | 0.16.14+ | 0.116.17+1.21.1 | `frozen-recruiting-1.21.1.jar` |

Grab the jars from the [Releases page](https://github.com/cassianbach/frozen-recruiting/releases).

Requires **Fabric API** â€” it depends on `fabric-api`, so vanilla will not load it.

---

## How to use

1. Press **G** to open the menu. Rebind under *Options â†’ Controls â†’ Frozen Recruiting*.
2. Type your message.
3. Press **Save** (or `Enter`).
4. **Right-click any player** â€” the message is sent to them.

**Shift + right-click** falls through to the normal interaction, so servers that use
right-click for ping keep working.

---

## Sent tab and reply notifications

The menu has two tabs:

- **Compose** â€” write the message you are about to send.
- **Sent** â€” everyone you have messaged, with their last reply, an unread badge, and an
  inline reply field so you can `/tell` them again without leaving the menu.

History persists to `config/frozen-recruiting.json`.

When someone replies, a toast appears in the top-right with a short click sound so the reply
is not missed.

### How replies are detected

Fabric's client receive event discards the sender UUID before it fires â€” the only signal
left is the rendered chat text. The matcher therefore supports the formats seen in practice:

| Format | Used by |
|---|---|
| `[Eviority_ -> You] hey` | Stray |
| `Steve whispers to you: hey` | Vanilla |
| `Steve whispered to you: hey` | past-tense variants |
| `Steve tells you: hey` | some plugins |
| `-> Steve: hey` | compact plugin formats |

Only replies from players you have actually messaged are picked up, so unrelated chat does
not trigger a popup.

### Commands

```
/frozenrecruiting            summary
/frozenrecruiting list       all contacts
/frozenrecruiting unread     only contacts with unread replies
/frozenrecruiting markread   clear all unread badges
/frozenrecruiting clear      delete all history
/frozenrecruiting reply <player> <text>

/frozenrecruiting cosmetic list
/frozenrecruiting cosmetic equip <id>
/frozenrecruiting cosmetic unequip
/frozenrecruiting cosmetic code <code>
/frozenrecruiting cosmetic scan
/frozenrecruiting cosmetic keyinfo
```

---

### Cosmetics

A client-side cosmetic system with rarity tiers. Cosmetics are obtainable **only via codes**
that you (the admin) mint.

**Rarity tiers:** Common (grey) â†’ Uncommon (green) â†’ Rare (blue) â†’ Epic (purple) â†’ Legendary (gold).

**How it works:**
1. Run /frozenrecruiting cosmetic keygen in-game (only works for the admin account).
2. Mint codes: /frozenrecruiting cosmetic mint <player> <cosmetic> (e.g. /frozenrecruiting cosmetic mint Steve legendary_crown)
3. The player redeems the code: /frozenrecruiting cosmetic code <code> (or drop it in codes.txt)
4. A badge appears above their head.

**Redemption paths:**
- In-game: `/frozenrecruiting cosmetic code <code>`
- File: drop codes into `<gameDir>/config/frozen-recruiting/codes.txt` (one per line), then
  `/frozenrecruiting cosmetic scan`

**Code security:** Codes are Ed25519-signed. The private key never ships in the mod jar, so
only you can mint valid codes. Codes are bound to the player name, so they can't be stolen.

**Cloudflare Worker (cross-client visibility):** The `worker/` directory contains a Cloudflare
Worker that stores UUID->cosmetic mappings. Deploy it to your Cloudflare account and set the
URL in `config/frozen-recruiting/cosmetic-api-url.txt`. Without it, cosmetics are self-only.

---

## Why it works on ping servers

The common way to do this (including the original version of this mod) registers a
Fabric `UseEntityCallback` and returns `ActionResult.PASS`. `PASS` means *"carry on with the
vanilla interaction"*, so the vanilla interact packet still gets sent to the server. A
server-side ping plugin sees that packet and runs its handler â€” so you get the tell **and**
the ping at the same time.

Frozen Recruiting returns `ActionResult.SUCCESS` instead. Fabric injects that callback as a
**cancellable** `@Inject`, so returning anything other than `PASS` skips the packet send
entirely. The server never sees the vanilla right-click, so a server ping plugin never
fires.

Message delivery uses `ClientPlayNetworkHandler#sendChatCommand`, which builds a
`CommandExecutionC2SPacket` â€” byte-identical to you typing `/tell` yourself. That means chat
plugins that only intercept the chat-message packet path do not interfere.

---

## Building from source

Requires **JDK 21**. The project targets a specific Minecraft version, set in
`gradle.properties` (or override with the `MC_VERSION` environment variable).

```powershell
# default (whatever mc_version says in gradle.properties)
gradle build

# build a specific Minecraft version
$env:MC_VERSION = '1.21.1';   gradle build
$env:MC_VERSION = '1.21.11';  gradle build
```

The jar lands in `lib/frozen-recruiting-<mc version>.jar` (and in `build/libs/`).

> Use the `MC_VERSION` environment variable rather than `-Pmc_version`. Gradle parses
> `-Pmc_version=1.21.11` as a version-number and mangles it into `1`, which fails the build.

### Layout

```
src/main/java/                     shared code (identical on both versions)
versions/1.21.11/src/main/java/    sources that differ on 1.21.11
versions/1.21.1/src/main/java/     sources that differ on 1.21.1
tools/Codegen.java                 standalone signer (run with `java Codegen.java`)
worker/index.js                    Cloudflare Worker for cross-client visibility
```

Only two classes differ per version:

- `RecruitingScreen` â€” the GUI
- `RecruitingInteraction` â€” keybind + right-click handling
- `OverheadBadgeRenderer` â€” rendering (uses different APIs per version)

They differ because of genuine API breaks:

| | 1.21.1 | 1.21.11 |
|---|---|---|
| `GameProfile` | `getName()` | `name()` (it is a record in authlib 7.x) |
| `KeyBinding` category | `String` | `KeyBinding.Category` object |
| `Screen.keyPressed` | `(int, int, int)` | `(KeyInput)` |
| `InputUtil.isKeyPressed` | `(long handle, int)` | `(Window, int)` |
| `Screen.renderBackground` | applies a full-screen blur post-process | no longer called |
| `WorldRenderEvents` | `v1` package | `v1.world` package |
| `Entity.getPos()` | exists | renamed to `getEntityPos()` |

---

## License

MIT â€” see [LICENSE](LICENSE).