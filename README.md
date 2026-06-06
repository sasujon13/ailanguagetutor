# AI Language Tutor by Cheradip

Offline-first Android language learning app — **243 languages**, OCR, practice modes, teen tutor voice.

## Docs

- **Spec:** [`ailanguagetutor.md`](ailanguagetutor.md)
- **Build progress:** [`BUILD_STATUS.md`](BUILD_STATUS.md)
- **Agent guide:** [`AGENTS.md`](AGENTS.md)

## Quick start

1. Open this folder in **Android Studio** (Ladybug / Koala or newer).
2. **Sync Gradle** — JDK 17 required.
3. Run **`app`** on emulator API 26+.

```bash
./gradlew assembleDebug
```

## Project structure

```
app/                 Application + MainActivity + Nav shell
ui/theme/            Cheradip Material 3
ui/navigation/       Route constants
ui/components/       LanguageFlagBadge, shared Compose
feature/home/        Home screen (Phase 0)
core/common/         Sha256Helper, shared utilities
catalog/             world_languages.json (243 langs)
```

## Build phases

Follow **`ailanguagetutor.md` → Build Order** (Phase 0–14). Do not skip phases.

## Version

App **1.0.0** · Package `com.cheradip.ailanguagetutor`
