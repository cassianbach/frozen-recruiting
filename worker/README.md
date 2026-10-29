# Cloudflare Worker for cross-client cosmetic visibility

This directory contains a Cloudflare Worker that stores UUID-to-cosmetic mappings, allowing
players using Frozen Recruiting to see each other's cosmetics on any server.

## Setup

1. Install Wrangler: `npm install -g wrangler`
2. Create a KV namespace: `wrangler kv namespace create COSMETICS_KV`
3. Add the namespace ID to `wrangler.toml` (create it if needed):
   ```toml
   name = "frozen-recruiting"
   main = "index.js"
   compatibility_date = "2024-01-01"

   [[kv_namespaces]]
   binding = "COSMETICS_KV"
   id = "<your-namespace-id>"
   ```
4. Deploy: `wrangler deploy`
5. Note the worker URL (e.g. `https://frozen-recruiting.your-account.workers.dev`)

## Mod configuration

Create a file at `<gameDir>/config/frozen-recruiting/cosmetic-api-url.txt` containing the
worker URL. The mod will query it for cosmetic data.

## Endpoints

- `POST /register {uuid, code}` — verifies the code and stores the mapping
- `GET /lookup?uuids=uuid1,uuid2,...` — returns cosmetic data for each UUID
- `GET /health` — health check

## Notes

- The worker holds the public key and verifies code signatures, so malicious clients
  cannot register fake cosmetics.
- Codes are single-use: the worker tracks redeemed codes in KV.
- The free tier (100k reads/day, 1k writes/day) is plenty for a small server.