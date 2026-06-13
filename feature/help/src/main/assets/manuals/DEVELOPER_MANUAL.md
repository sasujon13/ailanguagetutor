# AI Language Tutor — Developer Manual

This manual describes how the app and backends are built, how data flows, and how to extend the system. Repository root: `ailanguagetutor/`.

---

## 1. Architecture overview

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
  auth, settings, billing, onboarding, journal, dictionary
server/
  cloud-api/            FastAPI — auth, billing, packs, cloud AI fallback
  v2/                   FastAPI — Home AI (local models on your PC)
docs/                   Setup guides and manuals
```

**Patterns:** Jetpack Compose, Navigation Compose, Hilt DI, Room DB, Retrofit, DataStore, StateFlow.

---

## 2. Build & run (Android)

### Prerequisites
- Android Studio with JDK 17
- `local.env.properties` from `local.env.properties.example`
- Optional: physical device or emulator API 26+

### Commands

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd D:\VSCode\android\ailanguagetutor
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

### Key BuildConfig fields (`app/build.gradle.kts`)
- `API_BASE_URL` — cloud API (default tunnel: `https://ailt.cheradip.com/api/ailt/`)
- `HOME_AI_BASE_URL` — home AI (default: `https://ai.cheradip.com`)
- `ADMIN_SEED_PASSWORD` — local admin fallback login

See `docs/DEV_LOCAL_SETUP.md` and `docs/ANDROID_STUDIO_SETUP.md`.

---

## 3. Server stack

| Service | Port | Path | Role |
|---------|------|------|------|
| Home AI | 8787 | `server/v2/` | Curated AI modes, OCR clean, grammar, NLLB |
| App API | 8790 | `server/cloud-api/` | Login, billing verify, language packs, cloud LLM pool |

Both exposed via Cloudflare tunnel in production.

### Start locally

```powershell
# Terminal 1 — Home AI
cd server\v2
.\scripts\run-dev.ps1

# Terminal 2 — App API
cd server\cloud-api
.\scripts\init-db.ps1   # first time
.\scripts\run-dev.ps1

# Terminal 3 — Tunnel (optional if service installed)
cloudflared tunnel run cheradip-ailt
```

Verify: `.\scripts\verify-stack.ps1`

---

## 4. How AI routing works

### Client side

1. User picks **ProcessingIntent** (Answer / Translation) and **AiEngineMode** (1–5).
2. `AiModePreferenceRepository.resolvedMode()` applies rules:
   - OCR input → Mode 4 (Lightweight) automatically
   - Mode 5 without Plus → downgraded to Smart Tutor
3. `AIManager` sends requests to **Home AI** or **Cloud pool** based on `HomeAiSettingsRepository`.

### Home AI server (`server/v2`)

Request pipeline (`mode_router.py`):

```
HTTP request
  → tier gate (Mode 5 requires Plus)
  → rate limit (Pro/Plus tiers)
  → task classifier (translation / OCR cleanup / answer)
  → complexity score (LOW / MEDIUM / HIGH)
  → model_selector (Mistral 7B / Qwen 7B / Qwen 14B / NLLB)
  → L1 cache check → inference → response
```

**14B rule:** Qwen 14B is used **only** when `subscription_tier == plus` **and** `ai_engine_mode == 5`.

Key files:
- `app/services/model_selector.py`
- `app/services/mode_router.py`
- `app/services/complexity.py`
- `server/v2/docs/ROUTING_AND_CACHE.md`

### Example: grammar tap flow

```
ReaderScreen (tap word)
  → AIManager.explainGrammar()
  → HomeAiService.ask() POST /ask
  → ModeRouter.ask()
  → select_model() → InferenceEngine.run_llm()
  → cached in Room ai_cache + server L1 cache
```

Kotlin entry: `core/ai/AIManager.kt`  
Network: `core/ai/HomeAiService.kt`, `core/network/AiltApiModels.kt`

---

## 5. Subscription tiers (client)

`CheckAppAccessUseCase.subscriptionTier()` in `core/billing/BillingRepositories.kt`:

| AccessState | SubscriptionTier |
|-------------|------------------|
| TRIAL_ACTIVE, PRO_ACTIVE, SUBSCRIBED | PRO |
| PLUS_ACTIVE | PLUS |
| TRIAL_EXPIRED | FREE |

Play Billing products: `PlayProductIds.kt` (`cheradip_alt_pro_*`, `cheradip_alt_plus_*`).

---

## 6. Navigation

Routes defined in `ui/navigation/Routes.kt`.  
Wiring in `ui/navigation/AppNavHost.kt`.

Main tabs: Home, Practice, Library (History), Profile, Settings.

Nested routes (back arrow): scanner, reader, paywall, user manual, admin, etc.

**Example — add a new screen:**

```kotlin
// Routes.kt
const val MY_FEATURE = "my_feature"

// AppNavHost.kt
composable(Routes.MY_FEATURE) {
    MyFeatureScreen(onBack = { navController.popBackStack() })
}
```

Use `CheradipScrollScreen` + `onBack` or rely on `LocalNavBack` for non-tab routes.

---

## 7. Database (Room)

Schema in `core/database/`. Notable entities:

- `documents`, `document_pages` — scanned files + OCR text + content type
- `language_packs` — offline dictionary data
- `ai_cache` — cached AI responses
- `learning_activities` — practice history

Migrations: version bumps in `AppDatabase.kt` (destructive allowed in dev).

---

## 8. OCR & structuring pipeline

```
ScannerViewModel → DocumentImageStorage
  → OcrProcessingViewModel
  → ML Kit OCR (core/ocr)
  → ScannedContentClassifier (math/code/prose/…)
  → OcrStructureService
      prose → home /clean-ocr, then cloud fallback
      math/code/diagram → cloud POST /ai/structure-ocr only
  → save to document_pages.structuredText
  → ReaderScreen
```

Files: `core/ocr/`, `core/ai/OcrStructureService.kt`, `feature/reader/OcrProcessingViewModel.kt`

---

## 9. Auth

- Remote: `POST auth/login` → session token in DataStore
- Local fallback: seeded admin email + `ADMIN_SEED_PASSWORD`
- `AuthUser.role == "admin"` gates admin UI

Interceptor: `SessionAuthInterceptor` adds `Authorization: Bearer …`

---

## 10. Example: add a new API call

**1. Define DTO** (`core/network/AiltApiModels.kt`):

```kotlin
@Serializable
data class MyRequest(val text: String)

@Serializable
data class MyResponse(val result: String)
```

**2. Add to service** (`AiltApiService.kt`):

```kotlin
@POST("my-endpoint")
suspend fun myEndpoint(@Body body: MyRequest): MyResponse
```

**3. Wrap in repository** (`core/ai/` or `core/domain/`):

```kotlin
@Singleton
class MyRepository @Inject constructor(private val api: AiltAiService) {
    suspend fun run(text: String) = api.myEndpoint(MyRequest(text)).result
}
```

**4. Call from ViewModel** with `viewModelScope.launch`.

---

## 11. Example: new curated AI mode (server)

1. Add mode to `server/ai-modes.example.json`
2. Update `model_selector.py` routing rules
3. Add `AiEngineMode` entry in `core/model/AiV2Models.kt`
4. Update `availableAiModes()` tier mapping
5. Add UI meta in `AiModeUiMeta.kt`
6. Add tests in `server/v2/tests/test_router.py`

---

## 12. Module dependency rules

- `feature:*` may depend on `core:*` and `ui:*`
- `core:*` must not depend on `feature:*`
- `ui:navigation` aggregates feature modules for NavHost

When adding `feature:help`:
- Register in `settings.gradle.kts`
- Add `implementation(project(":feature:help"))` to `ui:navigation`

---

## 13. Testing

```powershell
# Android unit tests
.\gradlew.bat :core:model:testDebugUnitTest

# Home AI router tests
cd server\v2
python -m pytest tests/ -q
```

---

## 14. Useful docs

| Document | Topic |
|----------|-------|
| `ailanguagetutor.md` | Full product spec |
| `docs/DEV_LOCAL_SETUP.md` | Tunnel + servers |
| `server/v2/docs/ROUTING_AND_CACHE.md` | AI cache & routing |
| `server/v2/docs/MODEL_ROUTER.md` | Model selection |
| `BUILD_STATUS.md` | Implementation checklist |

---

*Cheradip AI Language Tutor · Developer Manual · v2.0.0*
