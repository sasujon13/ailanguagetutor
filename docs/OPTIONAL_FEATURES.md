# Optional features (non-blockers)

These are **not required** for daily dev, Android debug builds, Home AI (Ollama + NLLB), or the 243-pack Cloud API. Enable when you need voice I/O, Play Store billing, or OpenVINO instead of Ollama.

---

## Mode 3 Balanced polish — **implemented**

| Mode | Path | Pipeline |
|------|------|----------|
| 2 Fast Translation | `POST /translate` | NLLB (short phrases → Qwen when Ollama available) |
| 3 Balanced | `POST /translate` | NLLB → **Qwen 7B polish** |
| 4 Lightweight / OCR | `POST /clean-ocr` | Mistral 7B |

---

## Grammar on tap (Reader) — **implemented**

Settings → **Grammar detail level** (also quick chips on Reader):

| Level | On word tap |
|-------|-------------|
| **Word grammar** | Offline pack meanings + AI grammar for the word in its sentence |
| **Sentence grammar** | Meanings + full sentence structure |
| **Paragraph grammar** | Meanings + paragraph-wide patterns |

- **Meanings:** always from SQLite pack (≤3), instant — never AI.
- **Grammar:** batch AI via Home AI `/ask` or cloud fallback; cached in `ai_cache`; Pro/Plus.
- **Listen:** 🔊 on word and on grammar block (teen tutor voice).

**Prefetch (ajax-style):** Debounced background warm-up via `AiPrefetchCoordinator` (Pro/Plus, active packs only):

| Trigger | Debounce | What warms |
|---------|----------|------------|
| Reader open / grammar depth change | ~900 ms | `POST /prefetch-ai` on Home AI + local `ai_cache` |
| Practice typed input (≥60 chars) | ~1.5 s | Same, lighter targets |

One Home AI call per warm: grammar items (3 words / 3 sentences / 1 paragraph) **plus** the first ~480 chars of explain **or** translate (per your AI intent setting). Results are written to local Room `ai_cache` in one pass — no duplicate grammar fetches. Local cache trimmed to ~200 entries after batch writes.

- **Word mode:** first 3 distinct words in the text  
- **Sentence mode:** first 3 sentences  
- **Paragraph mode:** first paragraph  

Only runs for **active language packs** you selected (Languages tab, max 3). Grammar language = document language; learner notes use your other active pack(s).

---

## Grammar learning book — **implemented**

Bottom nav **Grammar** opens a chapter-style grammar book per active downloaded language (max 3 chips, top-right).

| Item | Detail |
|------|--------|
| API | `POST /grammar-book` on Home AI (`:8787`) |
| Android | `GrammarBookRepository` caches JSON in Room `ai_cache` |
| Switch language | Immediate reload for that language's book |
| Pro/Plus | Full AI-generated book; Free gets outline stub |
| Offline | Shows last cached book for that language |

Restart Home AI after pulling server changes: `server\v2\scripts\run-dev.ps1 -Restart`

---

## Cache housekeeping

Home AI L3 cache: `server/v2/data/ai_cache.db` (gitignored).

| Task | Command |
|------|---------|
| Clear stale translations | `cd server\v2; .\scripts\clear-cache.ps1` |
| Restart after clear | `.\scripts\run-dev.ps1 -Restart` |
| Full stack check | `.\scripts\verify-stack.ps1` (repo root) |

Clear cache after NLLB/translation logic changes so old entries are not served from SQLite.

**Pack download verify** — use `curl.exe` (bare `Invoke-WebRequest` hangs on binary ZIP):

```powershell
curl.exe -s -o NUL -w "status=%{http_code} size=%{size_download}" https://cheradip.com/ailt/api/languages/en/file
```

---

## Whisper STT (speech-to-text) — stub

| Item | Detail |
|------|--------|
| Endpoint | `POST /stt` on Home AI (`:8787`) |
| Status | Returns `text: null` until Whisper weights are loaded |
| Android | Practice “Say” mode; `InputSource.VOICE` on Home AI requests |
| Weights | `server/v2/models/stt/` via `setup_models.ps1` |

**Enable (OpenVINO path):**

```powershell
cd D:\VSCode\android\ailanguagetutor\server\v2
.\scripts\setup_models.ps1
# Wire inference in app/backends/ when ready (V2-2)
```

**Current dev workaround:** Android uses on-device speech recognition where available; Home AI STT endpoint is optional for server-side Whisper.

**Check stub:**

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8787/stt" -Method POST -ContentType "application/json" -Body '{"audio_base64":"","language_code":"en"}'
```

---

## Piper TTS (text-to-speech) — stub

| Item | Detail |
|------|--------|
| Endpoint | `POST /tts` on Home AI (`:8787`) |
| Status | Returns `audio_base64: null` until Piper voices are installed |
| Android | Practice “Listen” / teen tutor voice prefs (on-device TTS fallback) |
| Weights | `server/v2/models/tts/` via `setup_models.ps1` |

**Enable:** same as STT — full model setup downloads Piper assets under `models/tts/`.

**Check stub:**

```powershell
Invoke-RestMethod -Uri "http://127.0.0.1:8787/tts" -Method POST -ContentType "application/json" -Body '{"text":"Hello","voice":"en"}'
```

---

## Ollama vs OpenVINO (optional backend switch)

**Default (current):** `INFERENCE_BACKEND=ollama` in `server/v2/.env` — Qwen, Mistral, Llama via Ollama; NLLB via Transformers on CPU.

**Optional:** OpenVINO quants on Intel Arc:

```powershell
cd server\v2
.\scripts\setup_models.ps1
# Sets INFERENCE_BACKEND=openvino in .env when created from .env.example
```

Pull Ollama tags separately: `.\scripts\pull-ollama-models.ps1`, NLLB: `.\scripts\pull-nllb-model.ps1`.

---

## Play Console billing — client ready, Play products required

Billing **does not work** on sideload-only APKs. You need Play internal testing + signed release/AAB.

### Product IDs (must match Play Console)

| SKU | Tier | Actual price |
|-----|------|--------------|
| `cheradip_alt_pro_monthly` | Pro | $2/mo |
| `cheradip_alt_pro_yearly` | Pro | $20/yr |
| `cheradip_alt_plus_monthly` | Plus | $5/mo |
| `cheradip_alt_plus_yearly` | Plus | $50/yr |

Defined in `core/billing/.../PlayProductIds.kt`. Legacy `cheradip_alt_premium_*` maps to Pro.

### Play Console checklist

1. Create the **4 auto-renewing subscriptions** with exact IDs above.
2. Add your Google account as a **license tester**.
3. Upload **AAB** to **internal testing** (`assembleRelease` + signing).
4. Install from the **Play internal test link** (not sideload).
5. Test: Paywall → Google Play purchase → app calls `POST /api/ailt/billing/verify`.

### Server side

`bcheradip/ailt_api/app/routers/billing.py` — stores entitlement in MySQL (Play API verification stub until Google Play Developer API is wired with service account).

**Env:** no extra keys required for the local stub; production needs Play service account JSON on the server.

---

## App UI language — **implemented**

| Item | Detail |
|------|--------|
| Picker | **Settings → App language** (searchable dropdown) |
| Onboarding | Pick app language on first install — UI translates immediately via Home AI |
| Default | English (US) |
| Order | English → device region → A→Z in language lists |
| Translation | Home AI `POST /translate-strings` + local cache; ailt_api `X-Language` middleware |
| Scope | Bottom nav, Home, Settings, Languages, Onboarding strings (expand `AppStrings.kt` for more screens) |

Removed: top-right overlay flag picker (was unstable in Compose dropdown).

---

## Other optional items

| Feature | Status | Notes |
|---------|--------|-------|
| WhatsApp OTP delivery | Stub | Auth UI works; wire SMS/WhatsApp provider in bcheradip/ailt_api |
| ailt_api pytest | Not installed | Add when you want API regression tests |
| Tablet two-pane layout | Later | Spec only |
| OpenVINO-only LLM | Optional | Use when Ollama is not installed |

---

## Quick reference

| Doc | Purpose |
|-----|---------|
| `BUILD_STATUS.md` | Phase checklist + what works now |
| `docs/DEV_LOCAL_SETUP.md` | Tunnel + daily startup |
| `helper.txt` | Local cheat sheet (gitignored) |
| `server/v2/docs/ROUTING_AND_CACHE.md` | Mode router + L1/L2/L3 cache |
