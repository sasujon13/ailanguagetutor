# 🛡 AI Language Tutor — Administrator Guide

This guide is for **admin users** operating promo codes, AI infrastructure, platform reports, and daily ops. Examples use the Android app admin UI and cloud API.

---

## 1. 🔐 Admin access

### Log in as admin

1. Open the app → **Profile → Login**.
2. Use your admin email and password registered on the cloud API.
3. **Local dev fallback:** If the server is unreachable, login with the seeded admin email and `ADMIN_SEED_PASSWORD` from `local.env.properties`. Role is set to `admin`.

After login, Profile shows **Role: Admin**.

### Where admin tools appear

- ⚙️ **Settings → Admin** section
- 👤 **Profile → Admin** section (Reports)
- ☰ **Menu** → Admin Console · AI API Dashboard

### Manual access

- 👤 **Guest / Pro / Plus** — User Guide only
- 🛡 **Admin** — User Guide + Administrator Guide + Developer Guide

Open **Profile → User Manual** to pick a guide.

---

## 2. 🖥 Admin screens overview

- **Admin Console** — Settings → Admin Console — Promo codes CRUD
- **AI API Dashboard** — Settings → AI API status — Cloud providers + Home AI URL/stats
- **Reports** — Settings or Profile → Reports — Platform metrics + live Home AI usage

---

## 3. 📊 Reports (platform dashboard)

**Navigation:** Settings → **Reports** or Profile → **Reports** (admin only).

Tap **Refresh** to reload cloud and Home AI data.

### ☁️ Cloud metrics (requires admin login + cloud API)

**Users**

- Total registered · regular vs admin · email verified
- New users (last 7 days · last 30 days)

**Subscriptions**

- Active Pro · Active Plus · total active

**Engagement**

- Learning activities synced
- Device trials registered
- Guest AI uses (total across devices)
- Active promo codes · pending referral withdrawals · total referral balance

**Cloud AI API**

- API requests today · routing mode
- Per provider: requests, tier, health, quota %

### 🖥 Home AI (self-hosted PC)

- Server URL · online/offline status
- Inference backend · GPU available
- Active model · queue depth · cache hit rate
- Rate limit: allowed vs rejected
- **API requests by task** (ask, translate, OCR cleanup, …)
- **AI engine models used** — count per model (Qwen 7B, NLLB, etc.) since last server restart
- Resident models in memory · inference fallback count

**Example:** After a busy day, open Reports → Home AI → **Models used** to see whether NLLB or Qwen handled most translation requests.

### API endpoint

```
GET /api/ailt/admin/reports
Authorization: Bearer <admin session token>
```

Implemented in `server/cloud-api/app/routers/admin.py`.

---

## 4. 🎟 Promo codes

**Navigation:** Admin Console → **Promo codes** tab.

Promo codes live in cloud DB table `promo_codes`.

### Create a 50% off code

1. Enter code: `LAUNCH50`
2. Discount percent: `50`
3. Paywall slot: `2` (which price card the code applies to)
4. Optional: **Auto-apply** for new users
5. Tap **Create**

**Expected:** Message "Saved to promo_codes: LAUNCH50". Code appears with Active = true.

### Deactivate a code

1. Find the row (e.g. `LAUNCH50`).
2. Toggle **Active** off.
3. Tap **Update**.

### Example campaign code

- Code: `FRIEND20` · Discount: 20 · Paywall slot: 1 · Auto-apply: false

---

## 5. 🤖 Cloud AI providers

**Navigation:** Admin Console → **AI providers** tab (or AI API Dashboard).

Shows each configured LLM provider:

- Name · tier (`free` / `paid`) · health status
- Enable/disable toggle
- Requests today · quota used %
- Routing mode

### Disable exhausted free provider

1. Find provider with health **exhausted**.
2. Turn **Enabled** off.
3. Paid tier providers continue serving Pro/Plus users.

### Cloud vs Home AI

- **Home PC offline** — Ensure cloud providers enabled; app falls back automatically.
- **Cost spike** — Disable expensive paid providers temporarily.
- **API key expired** — Health shows error; rotate key in `server/cloud-api/.env` and restart.

---

## 6. 🖥 Home AI dashboard

**Navigation:** Admin Console → **Home AI** tab (AI API Dashboard).

Requires `HOME_AI_BASE_URL` reachable (tunnel or LAN). You can override URL for testing.

### Metrics

- Backend · GPU · model loaded · queue depth
- Cache hit rate (L1 / L2 / L3)
- Routes total · rate limit allowed/rejected
- Resident models

### Verify Mode 5 (14B) is Plus-only

1. Plus user on **High Accuracy** — active model may show Qwen 14B or Ollama equivalent.
2. Pro user on modes 1–4 should stay on 7B class models.

If Pro users get 14B, review `server/v2/app/services/model_selector.py` and restart Home AI.

### Model usage tracking

Home AI inference engine counts **models_used** per request (resets on server restart). Full breakdown also appears in **Reports → Home AI**.

---

## 7. ✉️ Email & SMTP (local dev)

Auth emails (OTP recovery, password update, email change) use SMTP.

### Startup order (same PC)

```powershell
# 1 — Local SMTP (port 1025)
cd server\mail
.\run-dev-smtp.ps1

# 2 — Cloud API (reads SMTP_* from .env)
cd server\cloud-api
.\scripts\run-dev.ps1
```

- Inbox files land in `server/mail/inbox/*.eml`
- Sender: `admin@ailanguagetutor.com`
- Config: `server/cloud-api/.env` → `SMTP_HOST`, `SMTP_PORT`, etc.

### Trusted device OTP skip

Registration stores `registered_device_id`. Recovery, email change, and password update may **skip OTP** when the request comes from that device.

---

## 8. 📋 Daily operations checklist

### Morning startup (self-hosted)

```powershell
cd server\mail
.\run-dev-smtp.ps1          # if testing auth emails locally

cd server\v2
.\scripts\run-dev.ps1

cd server\cloud-api
.\scripts\run-dev.ps1

cloudflared tunnel run cheradip-ailt   # if not installed as service
```

Run `.\scripts\verify-stack.ps1` — all health checks green.

### Monitor

1. **Reports** screen in app (users, API usage, models)
2. Home AI `/health` and `/admin/status`
3. Cloud API `/api/ailt/health`
4. Admin Console → provider health
5. Rate limit rejected count — spike → adjust `server/v2/app/services/rate_limit.py`

### Restart Home AI after model changes

```powershell
cd server\v2
.\scripts\run-dev.ps1 -Restart
```

---

## 9. 👥 User & billing support

### Verify subscription

`POST /billing/verify` with Play purchase token. Tier stored: `pro` or `plus`.

**User paid Plus but app shows Pro** — Ask them to tap **Restore purchases** on Paywall; check cloud DB `subscriptions` table.

### Trial expired

Offline language packs still work. AI and scanner require subscription.

### Guest AI limit

Guests get **99 AI calls**. After limit, login gate appears. Counter is per-install (DataStore + optional cloud sync).

---

## 10. 🌍 Language packs

Served from cloud API. Build/update:

```powershell
cd server\cloud-api
python scripts/build_language_packs.py
```

Deploy new versions; app downloads on **Languages** tab.

---

## 11. 🔒 Security notes

- **Reports API** requires admin session (`require_admin` dependency).
- Other admin routes should also enforce role on server — do not rely on UI hiding alone.
- Never commit `.env`, `local.env.properties`, or API keys.
- Rotate `ADMIN_SEED_PASSWORD` in production builds.
- Cloudflare tunnel credentials stay in `%USERPROFILE%\.cloudflared\`.

---

## 12. 🔧 Troubleshooting

- **Promo code not applying** — Confirm Active=true, correct paywall slot, spelling.
- **All AI failing** — Check tunnel + both servers; run verify-stack.ps1.
- **Reports empty / 403** — Log in as admin; ensure cloud API reachable with valid session.
- **Home AI offline in Reports** — Check HOME_AI_BASE_URL; start server/v2.
- **High rate-limit errors** — Adjust `pro_per_hour` / `plus_per_hour` in rate_limit.py.
- **14B OOM on GPU** — Only one large LLM loaded at a time.
- **Admin menu missing** — Login role must be exactly `admin`.
- **OTP emails not arriving (dev)** — Start SMTP server; read `server/mail/inbox/`.

---

## 13. 📡 API reference

**Home AI (v2)**

- GET /health — Liveness
- GET /admin/status — Home AI metrics, models_used, routes_by_intent

**Cloud API**

- GET /admin/reports — Platform dashboard (admin session required)
- GET /admin/promo-codes — List promos
- POST /admin/promo-codes — Create promo
- PATCH /admin/promo-codes/{code} — Update promo
- GET /admin/ai/providers — Provider list

Cloud API OpenAPI: run locally → `/docs`.

---

*Cheradip AI Language Tutor · Administrator Guide · v2.1.0*
