# Local & production setup

| Service | Where it runs | Public URL | Android field |
|---------|---------------|------------|---------------|
| **Home AI** | This repo `server/v2/` :8787 | `https://ai.cheradip.com` | `HOME_AI_BASE_URL` |
| **App API** | `D:\VSCode\cheradip\bcheradip\ailt_api` | `https://cheradip.com/ailt/api/` | `API_BASE_URL` |

**Release APK** uses `cheradip.com/ailt/api/` + `ai.cheradip.com`. The `server/` folder is never packaged in the APK.

---

## 1. App API (Linux production)

See **`D:\VSCode\cheradip\bcheradip\ailt_api\README.md`** — MySQL `ailanguagetutor`, SMTP, nginx `/ailt/api/`.

Verify: `curl https://cheradip.com/ailt/api/health`

### Local App API (optional)

```powershell
cd D:\VSCode\cheradip\bcheradip\ailt_api
.\scripts\run-dev.ps1
```

Emulator: `API_BASE_URL=http://10.0.2.2:8790/api/ailt/`

---

## 2. Home AI (your PC)

### One-time: models

```powershell
cd D:\VSCode\android\ailanguagetutor\server\v2
.\scripts\pull-ollama-models.ps1
.\scripts\pull-nllb-model.ps1
```

### Cloudflare tunnel (Home AI only)

```powershell
cloudflared tunnel login
cloudflared tunnel create cheradip-ailt
cloudflared tunnel route dns cheradip-ailt ai.cheradip.com
```

Copy [`server/deploy/cloudflare-tunnel.example.yml`](../server/deploy/cloudflare-tunnel.example.yml) to `%USERPROFILE%\.cloudflared\config.yml`.

### Every session

```powershell
cd D:\VSCode\android\ailanguagetutor\server\v2
.\scripts\run-dev.ps1
cloudflared tunnel run cheradip-ailt   # or cloudflared service
```

Verify: `https://ai.cheradip.com/health`

---

## 3. Android app

```powershell
copy local.env.properties.example local.env.properties
# Set ADMIN_SEED_PASSWORD

cd D:\VSCode\android\ailanguagetutor
.\gradlew.bat :app:assembleDebug
```

| Build | API | Home AI |
|-------|-----|---------|
| Debug | `local.env.properties` or `https://cheradip.com/ailt/api/` | `https://ai.cheradip.com` |
| Release | `https://cheradip.com/ailt/api/` | `https://ai.cheradip.com` |

Admin → **Developer** tab: set Home AI fallback to **0 ms** for cloud-only AI until Home AI is running.

---

## 4. Language packs

```powershell
.\tools\pack-builder\scripts\build-all-packs.ps1
```

Syncs to `D:\VSCode\cheradip\bcheradip\ailt_api\packs\`.

---

## 5. Smoke test

```powershell
.\scripts\verify-stack.ps1
```
