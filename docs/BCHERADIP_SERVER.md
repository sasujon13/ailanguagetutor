# Linked server — bcheradip (App API)

The **App API** for AI Language Tutor is **not** in this Android repository. It is developed and deployed from **bcheradip**.

## Paths

| Role | Absolute path |
|------|----------------|
| **Server project (Django + ailt_api)** | `D:\VSCode\cheradip\bcheradip` |
| **App API (FastAPI)** | `D:\VSCode\cheradip\bcheradip\ailt_api` |
| **Android client (this repo)** | `D:\VSCode\android\ailanguagetutor` |

## Public URLs

| Service | URL | Implemented in |
|---------|-----|----------------|
| App API | `https://cheradip.com/ailt/api/` | `bcheradip/ailt_api` |
| Home AI | `https://ai.cheradip.com` | `ailanguagetutor/server/v2` |

## When to edit which repo

| Task | Edit here |
|------|-----------|
| Auth, login, OTP email, SMTP | `bcheradip/ailt_api` |
| Billing, promo, referral, admin API | `bcheradip/ailt_api` |
| Language pack files on server | `bcheradip/ailt_api/packs/` (built from this repo) |
| MySQL schema / migrations | `bcheradip/ailt_api` |
| Linux nginx / systemd + SMTP | `D:\VSCode\cheradip\bcheradip\ailt_api\deploy\DEPLOY_LINUX.md` |
| Android UI, ViewModels, API client | **this repo** (`core/network/`) |
| Home AI inference | **this repo** (`server/v2/`) |
| Build language pack ZIPs | **this repo** (`tools/pack-builder/`) |

## Quick commands

**App API (local):**
```powershell
cd D:\VSCode\cheradip\bcheradip\ailt_api
.\scripts\run-dev.ps1
```

**Sync packs from Android repo:**
```powershell
cd D:\VSCode\android\ailanguagetutor
.\tools\pack-builder\scripts\build-all-packs.ps1
```

**Health check:**
```powershell
curl.exe https://cheradip.com/ailt/api/health
```

## Android config

| Setting | Default |
|---------|---------|
| `API_BASE_URL` | `https://cheradip.com/ailt/api/` |
| `HOME_AI_BASE_URL` | `https://ai.cheradip.com` |
| Pack sync target | `D:\VSCode\cheradip\bcheradip\ailt_api\packs` |

Override pack path: `AILT_PACKS_DIR` in `local.env.properties`.

## API contract (keep in sync)

Retrofit interfaces: `core/network/AiltApiService.kt` and related `*Service.kt` files.

When you add a server route in `bcheradip/ailt_api/app/routers/`, add matching Kotlin models and repository calls in this repo.

## Database sync (dev)

From bcheradip project root:
```powershell
python manage.py mysql_db_sync --l2r   # local XAMPP → Linux
python manage.py mysql_db_sync --r2l   # Linux → local
```

Database name: **`ailanguagetutor`**.

## Cursor / AI agents

Rule file: `.cursor/rules/server-bcheradip.mdc` — instructs agents to edit `D:\VSCode\cheradip\bcheradip` for all App API server work.

Counterpart in bcheradip: `AGENTS.md` and `ailt_api/ANDROID_CLIENT.md`.
