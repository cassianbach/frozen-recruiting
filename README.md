# Frozen Recruiting

A Minecraft **Fabric** client mod. Write a recruit message in a MobMastery / TargetMaster-style
GUI, then **right-click a player** to send it to them as `/tell`.

Works on servers that use right-click for **ping**.

---

## Downloads

| Minecraft | Loader | Fabric API | Jar |
|---|---|---|---|
| **1.21.11** | 0.19.5+ | 0.141.6+1.21.11 | `frozen-recruiting-1.21.11.jar` |
| **1.21.1** | 0.16.14+ | 0.116.17+1.21.1 | `frozen-recruiting-1.21.1.jar` |

Grab the jars from the [Releases page](https://github.com/cassianbach/frozen-recruiting/releases).

Requires **Fabric API** — it depends on `fabric-api`, so vanilla will not load it.

---

## How to use

1. Press **G** to open the menu. Rebind under *Options → Controls → Frozen Recruiting*.
2. Type your message.
3. Press **Save** (or `Enter`).
4. **Right-click any player** — the message is sent to them.

**Shift + right-click** falls through to the normal interaction, so servers that use
right-click for ping keep working.

---

## Why it works on ping servers

The common way to do this (including the original version of this mod) registers a
Fabric `UseEntityCallback` and returns `ActionResult.PASS`. `PASS` means *"carry on with the
vanilla interaction"*, so the vanilla interact packet still gets sent to the server. A
server-side ping plugin sees that packet and runs its handler — so you get the tell **and**
the ping at the same time.

Frozen Recruiting returns `ActionResult.SUCCESS` instead. Fabric injects that callback as a
**cancellable** `@Inject`, so returning anything other than `PASS` skips the packet send
entirely. The server never sees the vanilla right-click, so a server ping plugin never
fires.

Message delivery uses `ClientPlayNetworkHandler#sendChatCommand`, which builds a
`CommandExecutionC2SPacket` — byte-identical to you typing `/tell` yourself. That means chat
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

The jar lands in `build/libs/frozen-recruiting-<mc version>.jar`.

> Use the `MC_VERSION` environment variable rather than `-Pmc_version`. Gradle parses
> `-Pmc_version=1.21.11` as a version-number and mangles it into `1`, which fails the build.

### Layout

```
src/main/java/                     shared code (identical on both versions)
versions/1.21.11/src/main/java/    sources that differ on 1.21.11
versions/1.21.1/src/main/java/     sources that differ on 1.21.1
```

Only two classes differ per version:

- `RecruitingScreen` — the GUI
- `RecruitingInteraction` — keybind + right-click handling

They differ because of genuine API breaks:

| | 1.21.1 | 1.21.11 |
|---|---|---|
| `GameProfile` | `getName()` | `name()` (it is a record in authlib 7.x) |
| `KeyBinding` category | `String` | `KeyBinding.Category` object |
| `Screen.keyPressed` | `(int, int, int)` | `(KeyInput)` |
| `InputUtil.isKeyPressed` | `(long handle, int)` | `(Window, int)` |
| `Screen.renderBackground` | applies a full-screen blur post-process | no longer called |

The last one is why 1.21.1 overrides `renderBackground` with a no-op — otherwise the whole
panel renders blurred and out of focus.

---

## License

MIT — see [LICENSE](LICENSE).