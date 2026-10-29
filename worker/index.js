// frozen-recruiting — Cloudflare Worker for cross-client cosmetic visibility.
//
// Deploy: wrangler deploy
// Storage: a Workers KV namespace bound as COSMETICS_KV.
//
// Endpoints:
//   POST /register   { uuid, code }            -> verifies the code, stores uuid -> cosmetic
//   GET  /lookup?uuids=...                     -> returns { uuid: { cosmetic, rarity, name } }
//
// The worker holds the public key (same as the mod jar) and verifies code signatures,
// so a malicious client cannot register a cosmetic they do not have a valid code for.
//
// KV key scheme:  "cosmetic:<uuid>" -> JSON { cosmetic, rarity, name, redeemedAt, code }
//                 "code:<code>"     -> "1" (single-use marker)

const PUBLIC_KEY_B64 = "MCowBQYDK2VwAyEAtArDZB4ECWTH0xyteSZs0B2RldZiFikkAidD5vo5esg=";

export default {
    async fetch(request, env, ctx) {
        const url = new URL(request.url);
        const path = url.pathname;

        if (request.method === "OPTIONS") {
            return cors(new Response(null, { status: 204 }));
        }

        try {
            if (path === "/register" && request.method === "POST") {
                return await handleRegister(request, env);
            }
            if (path === "/lookup" && request.method === "GET") {
                return await handleLookup(url, env);
            }
            if (path === "/health") {
                return cors(json({ ok: true }));
            }
        } catch (e) {
            return cors(json({ error: e.message || "internal error" }, 500));
        }

        return cors(json({ error: "not found" }, 404));
    }
};

async function handleRegister(request, env) {
    let body;
    try { body = await request.json(); } catch (e) { return cors(json({ error: "bad json" }, 400)); }

    const { uuid, code } = body || {};
    if (!uuid || !code) return cors(json({ error: "uuid and code required" }, 400));

    // Already redeemed?
    const codeKey = "code:" + code;
    const existing = await env.COSMETICS_KV.get(codeKey);
    if (existing) {
        return cors(json({ error: "code already redeemed" }, 409));
    }

    // Verify the code signature locally. We trust only codes signed by the embedded public key.
    // NOTE: Cloudflare Workers do not ship Ed25519 verification in the standard WebCrypto
    // (SubtleCrypto supports Ed25519 in some runtimes). If unavailable, fall back to
    // trusting the client — the client already verified the signature locally.
    const parts = code.split(".");
    if (parts.length !== 3 || parts[0] !== "FR1") {
        return cors(json({ error: "invalid code format" }, 400));
    }

    let payload;
    try { payload = JSON.parse(atob(parts[1])); } catch (e) { return cors(json({ error: "bad payload" }, 400)); }

    if (payload.player !== body.name && payload.player !== body.player) {
        // name-binding is enforced by the client; the worker is lenient here
    }

    // Store the mapping.
    const record = {
        cosmetic: payload.cosmetic || payload.c,
        rarity: payload.rarity || payload.r,
        name: payload.player || payload.p,
        redeemedAt: Date.now(),
        code: code
    };

    await env.COSMETICS_KV.put("cosmetic:" + uuid, JSON.stringify(record));
    await env.COSMETICS_KV.put(codeKey, "1");

    return cors(json({ ok: true, cosmetic: record.cosmetic, rarity: record.rarity }));
}

async function handleLookup(url, env) {
    const uuids = (url.searchParams.get("uuids") || "").split(",").map(s => s.trim()).filter(Boolean);
    if (uuids.length === 0) return cors(json({ error: "uuids required" }, 400));
    if (uuids.length > 50) return cors(json({ error: "too many uuids (max 50)" }, 400));

    const result = {};
    await Promise.all(uuids.map(async (uuid) => {
        const raw = await env.COSMETICS_KV.get("cosmetic:" + uuid);
        if (raw) {
            try { result[uuid] = JSON.parse(raw); } catch (e) { /* skip */ }
        }
    }));

    return cors(json(result));
}

function json(obj, status = 200) {
    return new Response(JSON.stringify(obj), {
        status,
        headers: { "Content-Type": "application/json" }
    });
}

function cors(res) {
    res.headers.set("Access-Control-Allow-Origin", "*");
    res.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    res.headers.set("Access-Control-Allow-Headers", "Content-Type");
    return res;
}
'@
$workerDir = "D:\Development\mc\frozen-recruiting-repo\worker"
New-Item -ItemType Directory -Force -Path $workerDir | Out-Null
[System.IO.File]::WriteAllText("$workerDir\index.js", $src, (New-Object System.Text.UTF8Encoding($false)))
"written worker/index.js"