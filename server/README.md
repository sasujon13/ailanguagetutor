# Cheradip AI Language Tutor — server (this repo)

This Android repo keeps **Home AI only** (`server/v2/`). The **App API** (auth, billing, SMTP, packs, cloud AI) lives in the separate **bcheradip** project:

**`D:\VSCode\cheradip\bcheradip\ailt_api`** → production **`https://cheradip.com/ailt/api/`**

## Services in this repo

| Service | Path | Public URL | Android config |
|---------|------|------------|----------------|
| **Home AI** | `server/v2/` :8787 | `https://ai.cheradip.com` | `HOME_AI_BASE_URL` |
| **App API** | *bcheradip `ailt_api/`* :8790 | `https://cheradip.com/ailt/api/` | `API_BASE_URL` |

Run Home AI locally:

```powershell
cd server\v2
.\scripts\run-dev.ps1
```

App API setup, deploy, and `.env`: see **`D:\VSCode\cheradip\bcheradip\ailt_api\README.md`**.

**Linked server doc:** [`docs/BCHERADIP_SERVER.md`](../docs/BCHERADIP_SERVER.md) · pointer file: [`BCHERADIP.link`](BCHERADIP.link)

## Language packs

Build in this repo; packs sync to bcheradip:

```powershell
.\tools\pack-builder\scripts\build-all-packs.ps1
```

Default destination: `D:\VSCode\cheradip\bcheradip\ailt_api\packs\`  
Override: `AILT_PACKS_DIR` in `local.env.properties` or environment.

## API endpoint reference

The Android app calls these on **`https://cheradip.com/ailt/api/`**:

| Area | Routes |
|------|--------|
| Auth | `auth/login`, `auth/register`, `auth/verify-email`, … |
| Device | `device/register` |
| Billing | `billing/verify` |
| Promo / referral | `promo/*`, `referral/*` |
| Languages | `languages/list`, `languages/{code}/download` |
| Admin | `admin/promo-codes`, `admin/ai/providers`, … |
| Cloud AI | `ai/activity-metadata`, `ai/explain-paragraph` |

Implementation: `bcheradip/ailt_api/app/routers/`. API keys stay on the server — never in the APK.

## Version 2.0.0 — Home AI (local PC)

Primary on-device AI inference runs on your PC via **`server/v2/`** → tunnel **`https://ai.cheradip.com`**.

See `docs/OPTIONAL_FEATURES.md` and `server/v2/docs/ROUTING_AND_CACHE.md`.
