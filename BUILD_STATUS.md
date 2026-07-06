# Build Status — AI Language Tutor v2.0.0

Updated June 2026 after 243-pack rollout, Ollama + NLLB Home AI, and cache/translation polish.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| **0** | Scaffold | ✅ Done |
| **1** | Scanner | ✅ CameraX + gallery + multi-page |
| **2** | OCR | ✅ ML Kit + WordMapBuilder |
| **3** | Reader | ✅ Tappable text + word tap |
| **4** | Dictionary | ✅ SQLite pack reader + PackInstaller |
| **5** | Word loop + teen voice | ✅ Save to study list + VoicePreference DataStore |
| **6** | Language packs + 243 catalog | ✅ Download + max 3 active |
| **7** | Translation | ✅ OfflineTranslationEngine + reader UI |
| **8** | Practice Modes Hub | ✅ Calibration + modes + SyncedTextDisplay |
| **9** | Activity journal | ✅ learning_activities + FTS search |
| **10** | Onboarding & settings | ✅ Multi-step onboarding |
| **11** | Auth & profile | ✅ API + local fallback + OTP stubs |
| **12** | Trial & billing | ✅ Device trial + NavInterceptor + paywall |
| **13** | AI layer | ✅ AIManager batch-only + ai_cache |
| **14** | Polish & QA | 🔄 Unit tests + release checklist |

## Release APK (Cloudflare — your PC)

- **`API_BASE_URL`** → `https://cheradip.com/ailt/api/` (`bcheradip/ailt_api/`)
- **`HOME_AI_BASE_URL`** → `https://ai.cheradip.com` (`server/v2/`)

See **`docs/DEV_LOCAL_SETUP.md`**. App API server: **`docs/BCHERADIP_SERVER.md`** → `D:\VSCode\cheradip\bcheradip\ailt_api`

**Android Studio:** **`docs/ANDROID_STUDIO_SETUP.md`** — run configs, variants, emulator vs device.

## Admin password (local dev)

Set in **`local.env.properties`** (gitignored) — copy from **`local.env.properties.example`**.

Or run `scripts/dev-env.ps1` before Gradle. **Never commit passwords.**

## Build (clean)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd D:\VSCode\android\ailanguagetutor
.\gradlew.bat clean :app:assembleDebug :app:assembleRelease
```

**minSdk 26** (Android 8.0) — required for CameraX, ML Kit OCR, Play Billing, modern permissions.

**APK:** `app\build\outputs\apk\debug\app-debug.apk`

## Language packs (243 SQLite ZIPs)

| Item | Status |
|------|--------|
| `tools/pack-builder/` CLI | ✅ Builds all 243 packs |
| `bcheradip/ailt_api/packs/{code}/v1.zip` | ✅ sync via pack-builder |
| Cloud API `/api/ailt/languages/list` | ✅ Serves metadata + `/file` download |
| Android `PackInstaller` + SQLite reader | ✅ Unzip + query |

Rebuild packs:

```powershell
.\gradlew.bat :tools:pack-builder:run --args="build-all --version 1.0.0"
# or: .\tools\pack-builder\scripts\build-all-packs.ps1
```

## Home AI models (Ollama + NLLB)

| Model | Tag / path | Modes |
|-------|------------|-------|
| Qwen 7B | `qwen2.5:7b-instruct-q4_K_M` | 1 Smart Tutor, 3 Balanced polish |
| Qwen 14B | `qwen2.5:14b-instruct-q4_K_M` | 5 High Accuracy (Plus) |
| Mistral 7B | `mistral:7b-instruct-q4_K_M` | 4 Lightweight / OCR |
| Llama 3 8B | `llama3:8b-instruct-q4_K_M` | LLM fallback chain |
| NLLB-600M | `server/v2/models/hf/translation/nllb-600m/` | 2 Fast Translation, 3 base |

Pull scripts: `server/v2/scripts/pull-ollama-models.ps1`, `pull-nllb-model.ps1`

**Translation quality:** Short phrases (≤6 words) route to Qwen when Ollama is available; suspicious NLLB output triggers LLM fallback. Mode 3 runs NLLB + Qwen polish.

**Cache:** L3 SQLite at `server/v2/data/ai_cache.db` (gitignored). Clear with `server/v2/scripts/clear-cache.ps1`.

## Scan enhance (levels 0–7)

| Item | Status |
|------|--------|
| Android Clean / AI Clean UI | ✅ Levels 0–7, L1/L7 compare, recommendation |
| Live scan boundary (CameraX) | ✅ `LiveScanCaptureScreen` |
| Home AI `/scan-analyze`, `/scan-enhance` | ✅ `server/v2/` on **Windows** + Cloudflare |
| ONNX models | ✅ u2net, yolov8, realesrgan via `setup_scan_models.ps1` |
| Dewarp L5+ | ✅ OpenCV (no DewarpNet) |

Setup: `server/v2/scripts/setup_scan_models.ps1` · Health: `scan_models` on `https://ai.cheradip.com/health`

Web manual: [cheradip.com/ailt](https://cheradip.com/ailt) — rebuild `bcheradip/ailt/scripts/build-manual.ps1`

## Verify stack

```powershell
.\scripts\verify-stack.ps1
```

Or manually:

```powershell
curl.exe https://ai.cheradip.com/health
curl.exe https://cheradip.com/ailt/api/health
# Pack download — use curl (Invoke-WebRequest hangs without -OutFile):
curl.exe -s -o NUL -w "status=%{http_code} size=%{size_download}" https://cheradip.com/ailt/api/languages/en/file
```

## What works now

1. **Onboarding** — app language (default English US) + pick 1–3 study packs from 243 SQLite ZIPs + voice + download
2. **Scan → OCR → Reader** with tap-to-define, grammar on tap (Word/Sentence/Paragraph), save word, offline translation badge
3. **Languages** tab — download any of 243 packs; toggle **Active** switch (max 3 active)
4. **Practice hub** — 20-word calibration, Say/Write/Scan modes
5. **Learning journal** — documents + activity search (FTS)
6. **Paywall** — Pro/Plus tiers, two promo slots from API, referral, trial gate
7. **Admin login** — seeded admin + env password
8. **Cloud API (MySQL)** — auth, billing, promo, referral, languages, admin, AI fallback
9. **Home AI v2** — tunnel `ai.cheradip.com`, 30s timeout → cloud fallback
10. **Reader AI** — scan → UnifiedTextPipeline (home AI or cloud)
11. **Practice typed AI** — Type → Answer/Translation via mode settings; debounced background prefetch
12. **Grammar prefetch** — `AiPrefetchCoordinator` + `POST /prefetch-ai` (Pro/Plus, active packs); local `ai_cache` trim ~200 entries

---

## Verified (June 2026)

| Check | Result |
|-------|--------|
| `:app:assembleDebug` | ✅ |
| Home AI + Cloud API + 243 packs | ✅ `verify-stack.ps1` |
| Server pytest (`server/v2/tests/`) | ✅ 22 passed |
| Git vs `origin/main` | ✅ up to date (local feature work uncommitted) |

---

## Version 2.0.0 (spec revision 1.4.3)

See **`docs/V2-0-AUDIT.md`** and **`ailanguagetutor.md` → Version 2.0.0**.

| Phase | Name | Status |
|-------|------|--------|
| V2-0 | v1 audit + tier/mode enums | ✅ |
| V2-1 | FastAPI + `/ai/modes` | ✅ |
| V2-2 | Router + model setup + CI | ✅ |
| V2-3 | Mode picker + OCR auto Mode 4 | ✅ |
| V2-4 | Unified input | ✅ |
| V2-5 | HomeAiService + tier | ✅ |
| V2-6 | Router + L1/L2/L3 cache | ✅ |
| V2-7 | Pro + Plus Paywall (2 promo fields) | ✅ |
| V2-8 | Admin metrics + rate limits | ✅ |
| V2-9 | Release 2.0.0 | ✅ |

**Run home server:** `server/v2/scripts/run-dev.ps1` → http://localhost:8787/health

**One-click OpenVINO models (optional):** `server/v2/scripts/setup_models.ps1`

**Docs:** `docs/OPTIONAL_FEATURES.md` · `server/v2/docs/ROUTING_AND_CACHE.md` · `server/README.md`

---

## Optional / not yet implemented (non-blockers)

Full enable steps: **`docs/OPTIONAL_FEATURES.md`**

| Feature | Status | How to enable |
|---------|--------|---------------|
| **Grammar on tap + prefetch** | ✅ Implemented | Word/Sentence/Paragraph; `POST /prefetch-ai`; see `OPTIONAL_FEATURES.md` |
| **Mode 3 Balanced polish** | ✅ Implemented | `POST /translate` with `ai_engine_mode: 3` |
| **Whisper STT** | Stub (`POST /stt`) | `setup_models.ps1` + backend wiring |
| **Piper TTS** | Stub (`POST /tts`) | `setup_models.ps1` + backend wiring |
| **OpenVINO LLM path** | Optional | `setup_models.ps1` + `INFERENCE_BACKEND=openvino` |
| **Play Console billing** | Client ready; needs Play products | 4 SKUs + internal testing track |
| **Cache housekeeping** | ✅ Scripts | `clear-cache.ps1`, `verify-stack.ps1` |
| **WhatsApp OTP** | Stub delivery | Wire provider in bcheradip/ailt_api |
| **ailt_api pytest** | Not installed | Optional test harness |
| **Tablet two-pane layout** | Later | — |

### Play Console billing (when ready)

Create auto-renewing subscriptions:

- `cheradip_alt_pro_monthly` ($2/mo)
- `cheradip_alt_pro_yearly` ($20/yr)
- `cheradip_alt_plus_monthly` ($5/mo)
- `cheradip_alt_plus_yearly` ($50/yr)

Add license testers, upload AAB to internal testing. Flow: Paywall → Google Play → `POST /billing/verify` → Pro/Plus unlock. Billing does not work on sideload-only APKs without Play Store.
