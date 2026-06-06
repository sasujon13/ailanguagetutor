# Cheradip AI Language Tutor API

**Self-hosted from this repo** — exposed via Cloudflare Tunnel:

**`https://ailt.cheradip.com/api/ailt/`**

Implementation: [`cloud-api/`](cloud-api/) (local dev + production until you add a custom domain path).

Run locally:

```powershell
cd server\cloud-api
python scripts\build_language_packs.py   # tier-1 JSON packs (en, fr, es, de, bn, hi, ar, ja, pt, zh)
.\scripts\run-dev.ps1
```

Or one-click: `cd server; .\scripts\setup-all.ps1`

**Language packs:** stored in `server/cloud-api/packs/{code}/v1.json`, synced to MySQL on startup, served at `GET /languages/{code}/file`.

**Cloud AI:** set `GEMINI_API_KEY` / `OPENAI_API_KEY` / `OPENROUTER_API_KEY` in `.env` for live LLM responses on `/ai/*`.

The Android app calls these endpoints. Stubs work offline; extend `server/cloud-api/` as you build features.

## Services

| Service | Method | Path | Purpose |
|---------|--------|------|---------|
| Auth | POST | `auth/login` | Email/WhatsApp login |
| Auth | POST | `auth/register` | Request OTP |
| Auth | POST | `auth/verify-email` | Verify email OTP |
| Auth | POST | `auth/verify-whatsapp` | Verify WhatsApp OTP |
| Device | POST | `device/register` | Trial registration + fingerprint |
| Billing | POST | `billing/verify` | Play purchase verification |
| Promo | GET | `promo/paywall-config` | Slot1/slot2 visibility; hide all if no active discounts |
| Promo | POST | `promo/validate` | Max 2 codes: LAUNCH50 (slot1) + manual (slot2) |
| Referral | GET | `referral/policy` | Referrer buyer discount % + commission % |
| Referral | GET | `referral/balance` | Referrer credits |
| Languages | GET | `languages/list` | Pack catalog |
| Languages | GET | `languages/{code}/download` | Pack download metadata |
| Admin | GET | `admin/promo-codes` | List promo codes |
| Admin | POST | `admin/promo-codes` | Create promo (name + discount % + flags) |
| Admin | PATCH | `admin/promo-codes/{id}` | Edit discount % (**0 = expired**) |
| Admin | PATCH | `admin/referral-policy` | Referrer buyer discount + commission % |
| **Admin** | **GET** | **`admin/ai/providers`** | **AI provider health & quotas (admin)** |
| **Admin** | **PATCH** | **`admin/ai/routing`** | **Set routing mode (random free / paid fallback)** |
| **Admin** | **PATCH** | **`admin/ai/providers/{id}`** | **Enable/disable a provider** |
| AI | POST | `ai/activity-metadata` | Batch activity titles |
| AI | POST | `ai/explain-paragraph` | Paragraph explanation (batch only) |

Promo config: [`promo-codes.example.json`](promo-codes.example.json) — LAUNCH50, WELCOME10, COMEBACK20; **Pro actual $2/mo**; discount **0** → *Promo code is expired. No discount available.*

API keys **never** go in the Android APK. Configure providers on the server using `server/ai-providers.example.json` as a template.

### Supported providers (typical free tiers)

| ID | Provider | Notes |
|----|----------|--------|
| `gemini` | Google Gemini | Good default for metadata batch jobs |
| `openai` | OpenAI GPT-4o mini | Free/low-cost tier |
| `claude` | Anthropic Claude Haiku | Free tier where available |
| `groq` | Groq (Llama) | High free RPM |
| `openrouter` | OpenRouter | One key, many models; default `google/gemini-2.0-flash-exp:free` |
| `mistral` | Mistral | Smaller free quota |
| `openai_paid` | GPT-4o paid | Enable when free pool exhausted |
| `claude_paid` | Claude Sonnet paid | Premium fallback |
| `openrouter_paid` | OpenRouter paid | Default `anthropic/claude-3.5-sonnet` via same API key |

### Routing modes (`PATCH /admin/ai/routing`)

| Mode | Behavior |
|------|----------|
| `random_free` | Pick randomly among enabled **free** providers with `health != exhausted`; **retries another provider on the same request if one API call errors** |
| `random_all` | Random among all enabled providers |
| `priority_fallback` | Try free pool randomly; on failure/quota, use paid |
| `paid_only` | Skip free tier entirely |

Each AI response should include `"provider_used": "gemini"` so the app can show which backend served the request.

### Admin dashboard (app)

Log in as admin → **Settings → AI API status** (or Admin console → **AI providers** tab):

- Per-provider quota bars and health (healthy / degraded / exhausted)
- Toggle routing mode and paid auto-fallback
- Enable/disable individual providers
- Alert when free tier is not enough → enable paid keys in server env

## Admin bootstrap

See `admin.seed.example.json`. Password is **never** in the APK — use `ADMIN_SEED_PASSWORD` in server env or `local.env.properties` for local Android dev only.

- Email: `sashafik.me@gmail.com`
- WhatsApp: `+8801722710298`

## Environment variables (server)

```bash
GEMINI_API_KEY=...
OPENAI_API_KEY=...
ANTHROPIC_API_KEY=...
GROQ_API_KEY=...
MISTRAL_API_KEY=...
# Paid fallbacks (optional)
OPENAI_PAID_API_KEY=...
ANTHROPIC_PAID_API_KEY=...
ADMIN_SEED_PASSWORD=...
```

## Version 2.0.0 — local home AI (future)

When upgrading to v2, the **primary AI engine runs on your personal PC** (`server/v2/` FastAPI).

| Feature | Detail |
|---------|--------|
| **Tiers** | **Free** · **Pro** $2/mo actual · **Plus** $5/mo actual |
| **Pro modes** | 1–4 (pick 1–3; Mode 4 auto OCR) |
| **Plus modes** | 1–5 (pick 1–3 or 5; Mode 4 auto OCR) |
| **Translation** | NLLB (not LLM-only) |
| **Promos** | LAUNCH50 auto 50%; admin adds/edits code name + discount % |
| **Routing** | Task classifier + complexity + model selector |
| **Cache** | L1 RAM → L2 Redis → L3 SQLite |
| **GPU** | Intel Arc + OpenVINO |

| Config | Purpose |
|--------|---------|
| `HOME_AI_BASE_URL` | Android admin → your PC |
| `server/v2/` | FastAPI inference |
| `server/v2/docs/ROUTING_AND_CACHE.md` | Router + cache implementation guide |
| `server/ai-modes.example.json` | Mode + tier config |

Full spec: `ailanguagetutor.md` → **Version 2.0.0** (revision 1.4.3).
