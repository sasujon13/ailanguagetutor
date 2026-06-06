# Self-hosted setup — Cloudflare Tunnel (no cheradip.com/api/ailt required)

Both backends run **on your PC** from this repo and are exposed via **Cloudflare**:

| Service | Local | Tunnel hostname | Android field |
|---------|-------|-----------------|---------------|
| **Home AI** | `server/v2/` :8787 | `https://ai.cheradip.com` | `HOME_AI_BASE_URL` |
| **App API** | `server/cloud-api/` :8790 | `https://ailt.cheradip.com/api/ailt/` | `API_BASE_URL` |

**Release APK** uses those tunnel URLs (HTTPS). The `server/` folder is never packaged in the APK.

---

## 1. One-time: Cloudflare tunnel

```powershell
cloudflared tunnel login
cloudflared tunnel create cheradip-ailt
cloudflared tunnel route dns cheradip-ailt ai.cheradip.com
cloudflared tunnel route dns cheradip-ailt ailt.cheradip.com
```

Copy [`server/deploy/cloudflare-tunnel.example.yml`](../server/deploy/cloudflare-tunnel.example.yml) to `%USERPROFILE%\.cloudflared\config.yml` and set your tunnel UUID.

Install as service (survives reboot):

```powershell
cloudflared service install
cloudflared service start
```

---

## 2. One-time: Home AI models

**Recommended (current): Ollama + NLLB**

```powershell
cd D:\VSCode\android\ailanguagetutor\server\v2
.\scripts\pull-ollama-models.ps1
.\scripts\pull-nllb-model.ps1
# .env: INFERENCE_BACKEND=ollama
```

**Optional: OpenVINO full stack (Whisper STT + Piper TTS weights)**

```powershell
cd D:\VSCode\android\ailanguagetutor\server\v2
.\scripts\setup_models.ps1
```

See **`docs/OPTIONAL_FEATURES.md`** for STT/TTS, Mode 3 polish, Play billing, and cache housekeeping.

---

## 3. Every session — start both servers + tunnel

**Terminal 1 — Home AI**

```powershell
cd D:\VSCode\android\ailanguagetutor\server\v2
.\scripts\run-dev.ps1
```

**Terminal 2 — App API** (replaces cheradip.com/api/ailt for now)

```powershell
cd D:\VSCode\android\ailanguagetutor\server\cloud-api
.\scripts\run-dev.ps1
```

**Terminal 3 — Tunnel** (skip if cloudflared service is running)

```powershell
cloudflared tunnel run cheradip-ailt
```

---

## 4. Verify (phone browser or curl)

| Check | URL |
|-------|-----|
| Home AI | https://ai.cheradip.com/health |
| App API | https://ailt.cheradip.com/api/ailt/health |
| Full smoke test | `.\scripts\verify-stack.ps1` (repo root) |

Local only: http://localhost:8787/health and http://localhost:8790/api/ailt/health

---

## 5. Android app

```powershell
copy local.env.properties.example local.env.properties
# Edit ADMIN_SEED_PASSWORD; tunnel URLs are already correct in the example.

$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd D:\VSCode\android\ailanguagetutor
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

| Build | API | Home AI |
|-------|-----|---------|
| Debug | `local.env.properties` or tunnel defaults | same |
| Release | `https://ailt.cheradip.com/api/ailt/` | `https://ai.cheradip.com` |

**Important:** Users need your PC online with both servers + tunnel running (or cloudflared service + servers as Windows/Linux services).

---

## 6. Emulator without Cloudflare

```properties
HOME_AI_BASE_URL=http://10.0.2.2:8787
API_BASE_URL=http://10.0.2.2:8790/api/ailt/
```

---

## Later: custom domain path

To use `https://cheradip.com/api/ailt/` instead of `ailt.cheradip.com`, add a Cloudflare ingress path rule on your main domain — no separate cheradip.com codebase required; keep implementing routes in `server/cloud-api/`.
