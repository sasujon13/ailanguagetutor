# Build Status — AI Language Tutor v2.0.0

Updated after v2.0.0 release gate (June 2026).

## Phases

| Phase | Name | Status |
|-------|------|--------|
| **0** | Scaffold | ✅ Done |
| **1** | Scanner | ✅ CameraX + gallery + multi-page |
| **2** | OCR | ✅ ML Kit + WordMapBuilder |
| **3** | Reader | ✅ Tappable text + word tap |
| **4** | Dictionary | ✅ Sample pack JSON + PackDatabaseConnector |
| **5** | Word loop + teen voice | ✅ Save to study list + VoicePreference DataStore |
| **6** | Language packs + 243 catalog | ✅ Download + max 3 active |
| **7** | Translation | ✅ OfflineTranslationEngine + reader UI |
| **8** | Practice Modes Hub | ✅ Calibration + modes + SyncedTextDisplay |
| **9** | Activity journal | ✅ learning_activities + FTS search |
| **10** | Onboarding & settings | ✅ Multi-step onboarding |
| **11** | Auth & profile | ✅ API + local fallback + OTP stubs |
| **12** | Trial & billing | ✅ Device trial + NavInterceptor + paywall |
| **13** | AI layer | ✅ AIManager batch-only + ai_cache |
| **14** | Polish & QA | 🔄 Unit test + release checklist doc |

## Release APK (Cloudflare — your PC)

- **`API_BASE_URL`** → `https://ailt.cheradip.com/api/ailt/` (`server/cloud-api/`)
- **`HOME_AI_BASE_URL`** → `https://ai.cheradip.com` (`server/v2/`)

See **`docs/DEV_LOCAL_SETUP.md`**. No separate cheradip.com project required.

**Android Studio:** **`docs/ANDROID_STUDIO_SETUP.md`** — run configs, variants, emulator vs device.

## Admin password (local dev)

Set in **`local.env.properties`** (gitignored):

```
ADMIN_SEED_PASSWORD=Sa@2271029867890
```

Or run `scripts/dev-env.ps1` before Gradle. **Not committed to git.**

## Build (clean)

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd D:\VSCode\android\ailanguagetutor
.\gradlew.bat clean :app:assembleDebug :app:assembleRelease
```

**minSdk 26** (Android 8.0) — required for CameraX, ML Kit OCR, Play Billing, modern permissions.

**APK:** `app\build\outputs\apk\debug\app-debug.apk`

## UI (June 2026 polish)

- Shared **`CheradipUiKit`**: scroll screens, icon grids, dropdowns, input channel bar
- Teal brand theme + rounded shapes (16dp cards)
- Language packs: API download + searchable/filter dropdown on Languages tab
- Cloud API: 10 tier-1 packs served at `/languages/{code}/file`

## Still optional / later

- `tools/pack-builder/` CLI (243 full SQLite packs)
- Play Billing server-side Google verify
- Full STT / WhatsApp OTP delivery
- Tablet two-pane layout

## What works now

1. Multi-step **onboarding** (languages, voice, pack download)
2. **Scan → OCR → Reader** with tap-to-define, save word, offline translation badge
3. **Languages** tab — download packs (max 3 active)
4. **Practice hub** — 20-word calibration, Say/Write/Scan modes
5. **Learning journal** — documents + activity search (FTS)
6. **Paywall** — Pro/Plus tiers, two promo slots from API, referral, trial gate
7. **Admin login** — email `sashafik.me@gmail.com` + env password
8. **Cloud API (MySQL)** — auth, billing, promo, referral, languages, admin, AI fallback
9. **Home AI v2** — tunnel `ai.cheradip.com`, 30s timeout → cloud fallback
10. **Reader AI** — scan → UnifiedTextPipeline (home AI or cloud)
11. **Practice typed AI** — Type → Answer/Translation via mode settings

---

## Version 2.0.0 (in progress — spec revision 1.4.3)

See **`docs/V2-0-AUDIT.md`** and **`ailanguagetutor.md` → Version 2.0.0**.

| Phase | Name | Status |
|-------|------|--------|
| V2-0 | v1 audit + tier/mode enums | ✅ `core/model/AiV2Models.kt`, `docs/V2-0-AUDIT.md` |
| V2-1 | FastAPI + `/ai/modes` | ✅ `server/v2/` runnable scaffold |
| V2-2 | Router + model setup + CI | ✅ `MODEL_ROUTER`, `setup_models`, Docker, GitHub Actions |
| V2-3 | Mode picker + OCR auto Mode 4 | ✅ `ModeSelectionScreen`, `PlusUpgradeSheet`, `Routes.MODE_SELECTION`, `resolveAiEngineMode` |
| V2-4 | Unified input | ✅ `UnifiedTextPipeline`; Reader AI panel; Practice typed input |
| V2-5 | HomeAiService + tier | ✅ `translateParagraph`, intent routing, `X-Device-Id` header |
| V2-6 | Router + L1/L2/L3 cache | ✅ `cache_l2.py`, `cache_l3.py`, promotion, tests |
| V2-7 | Pro + Plus Paywall (2 promo fields) | ✅ `GET promo/paywall-config`, Pro/Plus plans, 2 slots |
| V2-8 | Admin metrics + rate limits | ✅ `/admin/status` metrics, rate limiter, Admin Home AI tab |
| V2-9 | Release 2.0.0 | ✅ `versionName 2.0.0`, BUILD_STATUS updated |

**Run home server:** `server/v2/scripts/run-dev.ps1` → http://localhost:8787/health

**One-click models:** `server/v2/scripts/setup_models.ps1` (Windows) or `setup_models.sh` (Linux)

**Docs:** `server/v2/docs/ROUTING_AND_CACHE.md` · `server/v2/deploy/models-download-quantization.md`
