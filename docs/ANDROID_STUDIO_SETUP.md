# Android Studio — open, run, and build

Project path: `D:\VSCode\android\ailanguagetutor`

---

## 1. One-time setup

### Install

1. **Android Studio** (Ladybug or newer) with **Android SDK 35**
2. **JDK 17** — already configured in `gradle.properties`:
   ```properties
   org.gradle.java.home=C\:\\Program Files\\Android\\Android Studio\\jbr
   ```
3. Open **Settings → Build, Execution, Deployment → Build Tools → Gradle**
   - **Gradle JDK:** `jbr-17` (Embedded JDK) or the path above

### Open the project

1. **File → Open** → select `D:\VSCode\android\ailanguagetutor`
2. Wait for **Gradle Sync** (first sync may take several minutes)
3. If sync fails on SDK: **Tools → SDK Manager** → install **Android 15 (API 35)**

### Local env (debug builds only)

```powershell
cd D:\VSCode\android\ailanguagetutor
copy local.env.properties.example local.env.properties
```

Edit `local.env.properties`:

```properties
HOME_AI_BASE_URL=https://ai.cheradip.com
API_BASE_URL=https://ailt.cheradip.com/api/ailt/
ADMIN_SEED_PASSWORD=your-admin-password
```

Gradle reads this file when building **debug** (see `app/build.gradle.kts`).  
After editing, **Build → Rebuild Project** so `BuildConfig` picks up new URLs.

**Emulator without Cloudflare tunnel:**

```properties
HOME_AI_BASE_URL=http://10.0.2.2:8787
API_BASE_URL=http://10.0.2.2:8790/api/ailt/
```

---

## 2. Run configurations (included in repo)

After opening the project, use the run dropdown (top toolbar):

| Configuration | Use when |
|---------------|----------|
| **App Debug** (default) | Daily dev — installs `app-debug.apk`, package `…ailanguagetutor.debug` |
| **App Release** | Test release build on phone — tunnel URLs baked in, minified |
| **assembleDebug** | Build APK only → `app\build\outputs\apk\debug\app-debug.apk` |
| **assembleRelease** | Build APK only → `app\build\outputs\apk\release\app-release.apk` |

If **App Debug** shows “Module not specified”: **Run → Edit Configurations → App Debug → Module** → choose **`ailanguagetutor.app.main`**.

### Build Variants panel

**View → Tool Windows → Build Variants**

| Variant | Package | API URLs |
|---------|---------|----------|
| **debug** | `com.cheradip.ailanguagetutor.debug` | from `local.env.properties` |
| **release** | `com.cheradip.ailanguagetutor` | `ailt.cheradip.com` + `ai.cheradip.com` |

---

## 3. Run on device or emulator

### Physical phone (recommended)

1. Enable **Developer options** + **USB debugging**
2. Connect USB → allow debugging on phone
3. Select device in toolbar dropdown
4. Click **Run ▶** (`App Debug` or `App Release`)

Release builds match what you sideload for real-world testing (no `.debug` suffix).

### Emulator

1. **Tools → Device Manager → Create Device** (Pixel, API 35)
2. Start emulator
3. Run **App Debug**
4. Use `10.0.2.2` URLs in `local.env.properties` if servers run on host PC without tunnel

---

## 4. Before running (backend)

For full app features, start on your PC (see `helper.txt`):

1. XAMPP **MySQL**
2. `server\cloud-api\scripts\run-dev.ps1`
3. `server\v2\scripts\run-dev.ps1`
4. `cloudflared tunnel run cheradip-ailt` (or cloudflared Windows service)

Verify: https://ailt.cheradip.com/api/ailt/health and https://ai.cheradip.com/health

---

## 5. Gradle tasks (alternative to Run menu)

**View → Tool Windows → Gradle** → **ailanguagetutor → app → Tasks → build**

| Task | Output |
|------|--------|
| `assembleDebug` | Debug APK |
| `assembleRelease` | Release APK |
| `installDebug` | Build + install debug on connected device |

Or terminal inside Android Studio:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

---

## 6. Release signing (Play Store)

Default: release APK is signed with **debug keystore** (local install only).

For Play upload:

1. Copy `keystore.properties.example` → `keystore.properties` and fill in paths/passwords
2. In `gradle.properties` set:
   ```properties
   alt.releaseApkUsesUploadKeystore=true
   ```
3. **Build → Generate Signed App Bundle / APK**

---

## 7. Design / Split view (Compose previews)

This app is **100% Jetpack Compose** — there are **no `res/layout/*.xml` screens**. Android Studio’s **Design** and **Split** tabs only show a preview when the open `.kt` file contains an `@Preview` function.

### How to use Split / Design

1. Open a preview file in Android Studio, for example:
   - `ui/components/.../ComposePreviews.kt` — shared UI (dropdowns, input channel bar)
   - `feature/home/.../HomeScreenPreviews.kt` — Home screen
   - `feature/practice/.../PracticeHubPreviews.kt` — Practice tab
   - `ui/theme/.../ThemePreviews.kt` — colors & typography
2. Click **Split** (editor toolbar) or **View → Tool Windows → Preview**.
3. Pick a preview from the dropdown above the preview pane (e.g. “Home”, “Practice — input channels”).
4. **Build → Rebuild Project** once after Gradle sync if the preview pane stays blank.

### If Design / Split is empty

| Problem | Fix |
|---------|-----|
| Opened a file with no `@Preview` | Open one of the `*Previews.kt` files listed above |
| Red `Unresolved reference: Preview` on `@Preview` | **File → Sync Project with Gradle Files**; preview deps are in each module’s `build.gradle.kts` (`compose.ui.tooling.preview`) |
| “Preview unavailable” / rendering failed | **File → Invalidate Caches → Restart**; then Rebuild |
| Preview never refreshes | Enable **Settings → Editor → Compose → Enable live edit** (optional) |
| Only code pane visible | Click **Split** or the **Preview** tool window icon on the right |
| Opened in VS Code / Cursor | Design preview is **Android Studio only** — open this folder in Android Studio |

Interactive preview: click the **Run** icon in the `@Preview` gutter to open **Interactive Preview**.

---

## 8. Troubleshooting

| Problem | Fix |
|---------|-----|
| Gradle sync: JDK not found | Settings → Gradle → JDK → Embedded JDK 17 |
| `local.env.properties` ignored | Rebuild project after editing |
| App can’t reach API | Start cloud-api + tunnel; check URL in Logcat / Settings |
| Two apps on phone (debug + release) | Different package IDs — both can be installed |
| Run config module missing | Edit configuration → Module → `ailanguagetutor.app.main` |
| Port 8790 / 8787 in use | Stop old server processes; see `helper.txt` |

---

## 9. Admin login (debug)

Email: `sashafik.me@gmail.com`  
Password: value of `ADMIN_SEED_PASSWORD` in `local.env.properties`

Release APK has **no** admin password in the binary (by design).
