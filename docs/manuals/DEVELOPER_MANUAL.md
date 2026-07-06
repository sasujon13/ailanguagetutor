# 👩‍💻 AI Language Tutor — Developer Guide

Architecture, build, data flows, and extension points. Repository root: `ailanguagetutor/`.

---

## 1. 🏗 Architecture overview

### Android (Gradle multi-module)

```
app/                    Application entry, Hilt, BuildConfig
ui/theme, ui/components, ui/navigation
core/                   Shared libraries
  common, model, domain, database, network
  ocr, image, audio, ai, auth, billing, device
  translation, pack, locale, speech
feature/                Feature screens + ViewModels
  home, scanner, reader, practice, grammar, languages
  auth, settings, billing, onboarding, journal, help
server/
  ailt_api/            FastAPI — auth, billing, packs, cloud AI, admin reports
  v2/                   FastAPI — Home AI (local models on your PC)
  mail/                 Dev SMTP for OTP emails
docs/manuals/           Source copies of in-app manuals
```

**Patterns:** Jetpack Compose · Navigation Compose · Hilt DI · Room · Retrofit · DataStore · StateFlow

### In-app manuals (`feature:help`)

- Bundled markdown: `feature/help/src/main/assets/manuals/*.md`
- Parser: `ManualParser.kt` — supports `#` headings, bullets, `---`, fenced code
- **Avoid markdown tables** in manuals (parser merges them poorly); use bullets + unicode icons
- Sync edits to `docs/manuals/` per README

---

## 2. 🔨 Build & run (Android)

### Prerequisites

- Android Studio · JDK 17
- `local.env.properties` from example file
- Device/emulator API 26+

### Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd D:\VSCode\android\ailanguagetutor
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

### Key BuildConfig fields

- `API_BASE_URL` — cloud API (default: `https://cheradip.com/ailt/api/`)
- `HOME_AI_BASE_URL` — Home AI (default: `https://ai.cheradip.com`)
- `ADMIN_SEED_PASSWORD` — local admin fallback login

See `docs/DEV_LOCAL_SETUP.md` and `docs/ANDROID_STUDIO_SETUP.md`.

---

## 3. 🖥 Server stack

**Dev SMTP** — port 1025 — `server/mail/` — OTP emails (local)

**Home AI** — port **8787** — `server/v2/` — Runs on **your Windows PC** (Intel Arc). Exposed via **Cloudflare** as `https://ai.cheradip.com`. Curated AI modes, OCR clean, grammar, NLLB, **scan enhance** (`/scan-analyze`, `/scan-enhance`).

**App API** — port **8790** — `bcheradip/ailt_api/` (not in this repo) — Login, billing, packs, cloud LLM pool, admin reports. Exposed as `https://cheradip.com/ailt/api/`.

Production: **Cloudflare tunnel** on your Windows PC exposes Home AI and App API (no Linux server required for inference).

### Start locally (Windows)

```powershell
# SMTP (optional — auth OTP testing)
cd server\mail
.\run-dev-smtp.ps1

# Home AI (Windows — same machine as tunnel)
cd server\v2
.\scripts\run-dev.ps1

# App API (bcheradip repo)
cd D:\VSCode\cheradip\bcheradip\ailt_api
.\scripts\run-dev.ps1

# Tunnel (optional)
cloudflared tunnel run cheradip-ailt
```

Verify: `.\scripts\verify-stack.ps1` · `curl.exe https://ai.cheradip.com/health` (check `scan_models`)

### Scan ONNX on Home AI (Windows)

```powershell
cd server\v2
.\scripts\setup_scan_models.ps1
```

Models in `server/v2/models/scan/` — **u2net** (required), **yolov8** + **realesrgan** (optional ONNX). Dewarp at L5+ is OpenCV only. Health: `scan_models` array on `/health`.

---

## 4. 🤖 AI routing

### Client

1. User picks **ProcessingIntent** (Answer / Translation) and **AiEngineMode** (1–5).
2. `AiModePreferenceRepository.resolvedMode()`:
   - OCR input → Mode 4 (Lightweight) automatically
   - Mode 5 without Plus → downgraded to Smart Tutor
3. `AIManager` → Home AI or Cloud pool via `HomeAiSettingsRepository`.
4. `MixedLanguageAnalyzer` detects mixed-script input (Bn+En, Ar+En, …).
5. `AiResponseFormatter` normalizes AI output (LaTeX → readable math, line layout).

**Unified entry:** `UnifiedTextPipeline` — Scan / Type / Voice → same `AIManager` path.

Key files:

- `core/ai/AIManager.kt`
- `core/ai/MixedLanguageAnalyzer.kt`
- `core/ai/AiResponseFormatter.kt`
- `core/ai/PracticePromptBuilder.kt`
- `core/ai/UnifiedTextPipeline.kt`
- `server/v2/app/services/practice_prompt.py`
- `server/v2/app/services/mixed_language.py`

### Home AI server pipeline

```
HTTP request
  → tier gate (Mode 5 requires Plus)
  → rate limit (Pro/Plus tiers)
  → task classifier
  → complexity score
  → model_selector (Mistral 7B / Qwen 7B / Qwen 14B / NLLB)
  → L1 cache → inference → response
  → inference_engine.models_used++ per model
```

**14B rule:** Qwen 14B only when `subscription_tier == plus` AND `ai_engine_mode == 5`.

Key files:

- `server/v2/app/services/mode_router.py`
- `server/v2/app/services/model_selector.py`
- `server/v2/app/services/inference_engine.py` (`models_used` counter)
- `server/v2/docs/ROUTING_AND_CACHE.md`

### Grammar tap example

```
ReaderScreen (tap word)
  → AIManager.explainGrammar()
  → HomeAiService.ask() POST /ask
  → ModeRouter.ask()
  → InferenceEngine.run_llm()
  → cached in Room ai_cache + server L1
```

---

## 5. 📷 Scanner & image pipeline

```
ScannerScreen / ScannerViewModel
  → MlKitDocumentScannerHelper (multi-page ML Kit capture)
  → LiveScanCaptureScreen + LiveScanAnalyzer (CameraX live boundary overlay)
  → LiveScanBoundaryDetector + DocumentBoundaryOverlay (flat vs curved)
  → ScanEnhancePipeline / ScanEnhanceStandards (levels 0–7)
  → ScanEnhanceService + ScanEnhanceAnalyzeService
      Clean → offline OpenCV on device
      AI Clean → POST https://ai.cheradip.com/scan-enhance (Pro + online)
      analyze → POST /scan-analyze (recommendation)
  → ScanExportService (PDF / images / profiles)
  → prepareForOcr() bakes enhanced bitmap before OCR
  → OcrProcessingViewModel
```

Legacy crop/clean editor (`ScanEditEngine`, 40+ presets) remains in `core/image` for advanced edit state; primary user flow is **enhance review** after capture.

Key files:

- `feature/scanner/ScannerScreen.kt` — ML Kit vs Live scan entry
- `feature/scanner/LiveScanCaptureScreen.kt` — CameraX + boundary overlay
- `feature/scanner/ScannerEditorUi.kt` — Clean | AI Clean, level selector, compare
- `feature/scanner/ScannerViewModel.kt` — enhance state, boundary refresh
- `core/image/ScanEnhancePipeline.kt` — offline Clean levels
- `core/ai/ScanEnhanceService.kt` — Home AI enhance client
- `server/v2/app/routers/scan_enhance.py` — `/scan-analyze`, `/scan-enhance`

Web user manual: `docs/manuals/USER_MANUAL.md` → build with `bcheradip/ailt/scripts/build-manual.ps1` → [https://cheradip.com/ailt](https://cheradip.com/ailt)

---

## 6. 📄 OCR & structuring

```
ML Kit OCR (core/ocr)
  → ScannedContentClassifier (math/code/prose/…)
  → OcrStructureService
      prose → Home AI /clean-ocr, then cloud fallback
      math/code/diagram → cloud POST /ai/structure-ocr
  → AiResponseFormatter (display cleanup)
  → document_pages.structuredText
  → ReaderScreen
```

Prompts prefer **Unicode math** over raw LaTeX (`OcrStructurePrompts.kt`).

Files: `core/ai/OcrStructureService.kt`, `core/ocr/OcrTextFormatter.kt`, `feature/reader/OcrProcessingViewModel.kt`

---

## 7. 🎯 Practice hub

```
PracticeHubScreen
  → InputChannelBar (Scan/Camera/Import/Type/Voice/Listen)
  → PracticeHubViewModel
      updateTypedInput → offline incremental preview
      applyExternalInput → OCR text as InputSource.OCR_SCAN
      scheduleVoiceAutoAi → auto process after speech pause
      unifiedTextPipeline.process → AIManager
      wordMapBuilder + PracticeTappableText (styled output)
```

Files: `feature/practice/PracticeHubViewModel.kt`, `VoiceCalibrationPanel.kt`, `ui/components/InputChannelBar.kt`

---

## 8. 📚 Grammar book

```
GrammarBookScreen
  → GrammarBookRepository (Room cache + Home AI API)
  → POST /grammar-book (TOC + chapters)
  → POST /grammar-book/enrich-section (lazy expansion on scroll)
  → saveOpenChapter → LearningActivityRepository
```

Server: `server/v2/app/routers/grammar_book.py`, `server/v2/app/services/grammar_book.py`

---

## 9. 🔐 Auth & email

### Client

- `core/auth/AuthRepository.kt` — DataStore session, `signupInit`, email change, recovery
- `core/network/SessionAuthInterceptor.kt` — Bearer token
- Screens: `SignUpScreen`, `PasswordScreens` (forgot/update/change email), `ProfileScreen`

### Server

- `server/ailt_api/app/routers/auth.py` — email-only signup (`signup/init`), login, OTP flows
- `server/ailt_api/app/services/email_service.py` — SMTP send
- `server/ailt_api/app/deps.py` — `get_current_user`, `require_admin`
- Trusted device: `_is_trusted_device()` skips OTP on registered device

---

## 10. 📊 Admin Reports

### Android

- `feature/billing/AdminReportsScreen.kt`
- `core/billing/BillingRepositories.kt` — `AdminReportsRepository`
- Route: `Routes.ADMIN_REPORTS` (admin-gated in AppNavHost)

### Server

- `GET /admin/reports` — aggregates users, subscriptions, engagement, cloud AI providers
- Requires `require_admin` session

Home AI panel uses existing `GET /admin/status` via `HomeAiService.fetchAdminStatus()`.

---

## 11. 💳 Subscription tiers

`CheckAppAccessUseCase.subscriptionTier()` in `core/billing/BillingRepositories.kt`:

- TRIAL_ACTIVE, PRO_ACTIVE, SUBSCRIBED → PRO
- PLUS_ACTIVE → PLUS
- TRIAL_EXPIRED → FREE

Products: `PlayProductIds.kt` (`cheradip_alt_pro_*`, `cheradip_alt_plus_*`).

---

## 12. 🧭 Navigation

Routes: `ui/navigation/Routes.kt`  
Wiring: `ui/navigation/AppNavHost.kt`

Main tabs: Home · Practice · Learning · Profile · Settings

Admin routes: `admin`, `admin/ai`, `admin/reports`, `user_manual/{manualId}`

**Add a screen:**

```kotlin
// Routes.kt
const val MY_FEATURE = "my_feature"

// AppNavHost.kt
composable(Routes.MY_FEATURE) {
    MyFeatureScreen(onBack = { navController.popBackStack() })
}
```

Use `CheradipScrollScreen` + `LocalNavBack` for nested routes.

---

## 13. 🗄 Database (Room)

Notable entities:

- `documents`, `document_pages` — scans + OCR + structured text + content type
- `language_packs` — offline dictionary
- `ai_cache` — cached AI responses
- `learning_activities` — practice/grammar history
- Grammar book cache tables

Schema: `core/database/` · migrations in `AppDatabase.kt`

---

## 14. ➕ Example: new API call

**1. DTO** (`core/network/AiltApiModels.kt`):

```kotlin
@JsonClass(generateAdapter = true)
data class MyRequest(val text: String)

@JsonClass(generateAdapter = true)
data class MyResponse(val result: String)
```

**2. Service** (`AiltApiService.kt`):

```kotlin
@POST("my-endpoint")
suspend fun myEndpoint(@Body body: MyRequest): MyResponse
```

**3. Repository** — wrap in `@Singleton` class, inject service.

**4. ViewModel** — `viewModelScope.launch { repo.run() }`.

---

## 15. ➕ Example: new AI mode (server)

1. Add mode to `server/ai-modes.example.json`
2. Update `model_selector.py` routing
3. Add `AiEngineMode` in `core/model/AiV2Models.kt`
4. Update `availableAiModes()` tier mapping
5. Add UI meta in `AiModeUiMeta.kt`
6. Tests: `server/v2/tests/test_router.py`

---

## 16. 📦 Module rules

- `feature:*` → may depend on `core:*`, `ui:*`
- `core:*` → must NOT depend on `feature:*`
- `ui:navigation` aggregates features for NavHost
- `feature:help` — manuals only; no feature deps on help

---

## 17. 🧪 Testing

```powershell
# Android unit tests
.\gradlew.bat :core:ai:testDebugUnitTest
.\gradlew.bat :core:model:testDebugUnitTest

# Home AI router tests
cd server\v2
python -m pytest tests/ -q
```

Notable: `AiResponseFormatterTest.kt` — broken LaTeX + mixed-language detection.

---

## 18. 📚 Useful docs

- `ailanguagetutor.md` — Full product spec
- `docs/DEV_LOCAL_SETUP.md` — Tunnel + servers
- `docs/manuals/README.md` — Manual sync policy
- `server/v2/docs/ROUTING_AND_CACHE.md` — AI cache
- `server/v2/docs/MODEL_ROUTER.md` — Model selection
- `server/mail/README.md` — Dev SMTP
- `BUILD_STATUS.md` — Implementation checklist
- `helper.txt` — Quick startup commands

---

*Cheradip AI Language Tutor · Developer Guide · v2.2.0*
