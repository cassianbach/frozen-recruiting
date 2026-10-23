## Frozen Recruiting 1.21.1 / 1.21.11

Write a recruit message in a MobMastery-style GUI, then **right-click a player** to send it
as `/tell`.

### Downloads

| Minecraft | Fabric Loader | Fabric API | File |
|---|---|---|---|
| 1.21.11 | 0.19.5+ | 0.141.6+1.21.11 | `frozen-recruiting-1.21.11.jar` |
| 1.21.1 | 0.16.14+ | 0.116.17+1.21.1 | `frozen-recruiting-1.21.1.jar` |

Both need [Fabric API](https://modrinth.com/mod/fabric-api) — the mod depends on
`fabric-api`, so it will not load in vanilla.

### Usage

1. Press **G** to open the menu (rebind in *Options → Controls → Frozen Recruiting*).
2. Type your message.
3. Press **Save** or `Enter`.
4. **Right-click a player** to send it.

**Shift + right-click** falls through to the normal interaction, so servers that use
right-click for ping keep working.

### Sent tab

The menu has two tabs:

- **Compose** — write the message you are about to send.
- **Sent** — everyone you have messaged, with their last reply, an unread badge, and an
  inline reply field so you can `/tell` them again without leaving the menu.

History is stored in `config/frozen-recruiting.json`.

### Reply notifications

When someone replies, a toast appears in the top-right with a short click sound, so a reply
is not missed while you are busy.

Reply detection works by matching the rendered chat text, because Fabric's client receive
event discards the sender UUID before it fires. The matcher supports the formats seen in
practice:

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
```

### Why it works on ping servers

The usual approach registers a Fabric `UseEntityCallback` and returns
`ActionResult.PASS`. `PASS` means *"continue the vanilla interaction"*, so the interact
packet still reaches the server, the server-side ping plugin sees it, and you get the tell
and the ping at once.

Frozen Recruiting returns `ActionResult.SUCCESS`. Fabric injects that callback as a
cancellable `@Inject`, so a non-`PASS` result skips the packet send entirely — the server
never sees the vanilla right-click, so its ping plugin never fires.

Delivery uses `ClientPlayNetworkHandler#sendChatCommand`, producing a
`CommandExecutionC2SPacket` byte-identical to typing `/tell` yourself.

### Building

Needs **JDK 21**. Set the target Minecraft version in `gradle.properties`, or override with
the `MC_VERSION` environment variable:

```powershell
$env:MC_VERSION = '1.21.1';  gradle build
```

Use the environment variable rather than `-Pmc_version` — Gradle parses
`-Pmc_version=1.21.11` as a version-number and mangles it into `1`.

The built jar is copied to `lib/`.

MIT licensed.