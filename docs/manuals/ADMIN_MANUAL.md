# AI Language Tutor — Administrator Manual

This guide is for **admin users** operating the Cheradip backend consoles, promo codes, and AI infrastructure. Examples use the live admin UI in the Android app and the cloud API.

---

## 1. Admin access

### Log in as admin

1. Open the app → **Profile → Login** (or Settings if already signed in elsewhere).
2. Use your admin account email and password registered on the cloud API.
3. **Local dev fallback:** If the server is unreachable, login with the seeded admin email and password from `local.env.properties` (`ADMIN_SEED_PASSWORD`). Role is set to `admin`.

After login, Profile shows **Role: Admin**. Settings and the ☰ menu expose **Admin Console** and **AI API Dashboard**.

### Who sees which manuals

| User | User Manual | Admin Manual | Developer Manual |
|------|-------------|--------------|------------------|
| Guest / Free / Pro / Plus | Yes | No | No |
| Admin | Yes | Yes | Yes |

Open **Profile → User Manual** to reach all guides available to your role.

---

## 2. Admin Console overview

**Navigation:** Settings → Admin Console, or ☰ → Admin Console.

The console has tabs:

1. **Promo codes** — create and manage discount codes
2. **AI providers** — cloud LLM pool health and routing (via App API)

**AI API Dashboard** (separate screen): Home AI server stats, cache, GPU, rate limits.

---

## 3. Promo codes (with examples)

Promo codes live in the cloud database table `promo_codes`. Admins manage them from the **Promo codes** tab.

### Create a 50% off code

1. Admin Console → Promo codes tab.
2. Enter code: `LAUNCH50`
3. Discount percent: `50`
4. Paywall slot: `2` (which price card the code applies to)
5. Optional: enable **Auto-apply** for new users
6. Tap **Create**

**Expected result:** Message "Saved to promo_codes: LAUNCH50". Code appears in the list with Active = true.

### Deactivate a code

1. Find the row (e.g. `LAUNCH50`).
2. Toggle **Active** off.
3. Tap **Update**.

Users entering the code at checkout will no longer receive the discount.

### Example: referral campaign code

| Field | Value |
|-------|-------|
| Code | `FRIEND20` |
| Discount | 20 |
| Paywall slot | 1 |
| Auto-apply | false |

Share `FRIEND20` in marketing; track redemptions via billing logs on the server.

---

## 4. AI providers dashboard (cloud pool)

**Navigation:** Admin Console → AI providers tab, or ☰ → AI API Dashboard (Home AI section on second tab).

### Cloud providers (App API)

Shows each configured LLM provider:

- Name, tier (`free` / `paid`), health status
- Enable/disable toggle
- Routing mode (round-robin, priority)

**Example operation — disable exhausted free provider:**

1. Open AI providers tab.
2. Find provider with health **exhausted**.
3. Turn **Enabled** off.
4. Paid tier providers continue serving Pro/Plus users.

### When to use cloud vs home AI

| Scenario | Action |
|----------|--------|
| Home PC offline | Ensure cloud providers enabled; users fall back automatically |
| Cost spike | Disable expensive paid providers temporarily |
| Provider API key expired | Health shows error; rotate key in `server/cloud-api/.env` and restart |

---

## 5. Home AI dashboard (server/v2)

**Navigation:** ☰ → AI API Dashboard → Home AI tab.

Requires `HOME_AI_BASE_URL` reachable (tunnel or LAN).

### Metrics shown

- Routes total / by intent (translation, OCR, answer)
- Cache hit rate (L1)
- Rate limit allowed/rejected
- GPU status, active LLM model, loaded models
- Inference success/fallback counts

### Example: verify Mode 5 (14B) is Plus-only

1. Check dashboard while a Plus user uses High Accuracy mode — **active_llm** may show `qwen2.5-14b-int4` or Ollama equivalent.
2. Pro user on mode 1–4 should never load 14B — active model stays 7B or Mistral.

If Pro users incorrectly get 14B, review `server/v2/app/services/model_selector.py` and restart Home AI.

---

## 6. Daily operations checklist

### Morning startup (self-hosted)

```powershell
cd server\v2
.\scripts\run-dev.ps1

cd server\cloud-api
.\scripts\run-dev.ps1

cloudflared tunnel run cheradip-ailt   # if not installed as service
```

Run `.\scripts\verify-stack.ps1` — all health checks green.

### Monitor

1. Home AI `/health` and `/admin/stats` (via dashboard)
2. Cloud API `/api/ailt/health`
3. Admin Console → provider health
4. Rate limit rejected count — spike may mean increase limits in `rate_limit.py`

### Restart Home AI after model changes

```powershell
cd server\v2
.\scripts\run-dev.ps1 -Restart
```

---

## 7. User & billing support

### Verify subscription (server)

Billing verify endpoint: `POST /billing/verify` with Play purchase token.  
Tier stored: `pro` or `plus`. App maps to `AccessState.PRO_ACTIVE` or `PLUS_ACTIVE`.

**Example:** User paid Plus but app shows Pro — ask them to tap Restore purchases on Paywall; check cloud DB `subscriptions` table for their email.

### Trial expired

`AccessState.TRIAL_EXPIRED` → paywall on launch. Offline language packs still work; AI and scanner require subscription.

### Guest AI limit

Guests get 99 AI calls (`GuestAiUsageRepository`). After limit, login gate appears. Reset is per-install (DataStore counter).

---

## 8. Content & language packs

Language packs served from cloud API (`/packs/...`). Admin scripts:

```powershell
cd server\cloud-api
python scripts/build_language_packs.py
```

Upload new pack versions through your deployment process; app downloads on Languages tab.

---

## 9. Security notes

- Admin APIs require `role=admin` session on the server — UI gating is not sufficient alone.
- Never commit `.env`, `local.env.properties`, or API keys.
- Rotate `ADMIN_SEED_PASSWORD` in production builds.
- Cloudflare tunnel credentials stay in `%USERPROFILE%\.cloudflared\`.

---

## 10. Troubleshooting

| Issue | Admin action |
|-------|----------------|
| Promo code not applying | Confirm Active=true, correct paywall slot, code spelling |
| All AI failing | Check tunnel + both servers; verify-stack.ps1 |
| High rate-limit errors | Adjust `pro_per_hour` / `plus_per_hour` in `rate_limit.py` |
| 14B OOM on GPU | Only one large LLM loaded; ensure 7B unloaded before 14B |
| Admin menu missing | Confirm login role is exactly `admin` |

---

## 11. API endpoints (reference)

| Endpoint | Service | Purpose |
|----------|---------|---------|
| `GET /health` | both | Liveness |
| `GET /admin/stats` | v2 | Home AI metrics |
| `GET /admin/promo-codes` | cloud | List promos |
| `POST /admin/promo-codes` | cloud | Create promo |
| `PATCH /admin/promo-codes/{code}` | cloud | Update promo |
| `GET /admin/ai/providers` | cloud | Provider list |

Full API docs: run cloud-api locally → OpenAPI at `/docs`.

---

*Cheradip AI Language Tutor · Administrator Manual · v2.0.0*
