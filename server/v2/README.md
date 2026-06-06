# Cheradip Home AI Server — v2.0.0

> Personal PC (Intel Arc) runs primary AI inference. Cloud handles auth/billing.

## One-click model setup (Intel Arc)

```powershell
cd server\v2
.\scripts\setup_models.ps1
```

```bash
cd server/v2
bash scripts/setup_models.sh
```

Downloads Qwen, Mistral, NLLB, Whisper → OpenVINO INT8/INT4 → verifies → sets `.env`.

| Env var | Purpose |
|---------|---------|
| `HF_TOKEN` | Gated models (Llama 3) |
| `INCLUDE_OPTIONAL=1` | Also download Qwen 14B + Llama 3 |

## Start server

```powershell
.\scripts\run-dev.ps1
```

→ http://localhost:8787/docs · http://localhost:8787/health

## Endpoints

| Method | Path | Status |
|--------|------|--------|
| GET | `/health` | ✅ |
| GET | `/ai/modes?tier=pro` | ✅ |
| POST | `/clean-ocr` | stub → OpenVINO after setup |
| POST | `/translate` | stub → NLLB after setup |
| POST | `/ask` | stub → Qwen after setup |
| POST | `/tts` | Piper (manual) |
| GET | `/admin/status` | ✅ |

## Scripts

| Script | Purpose |
|--------|---------|
| `setup_models.ps1` / `.sh` | Download + convert + verify |
| `run-dev.ps1` / `.sh` | Start FastAPI on port **8787** |
| `model_setup/download_models.py` | HF snapshots |
| `model_setup/convert_openvino.py` | Optimum export |
| `model_setup/verify_models.py` | Health check |

## Docs

- [docs/MODEL_ROUTER.md](docs/MODEL_ROUTER.md) — classifier, fallback, cache-first
- [docs/ROUTING_AND_CACHE.md](docs/ROUTING_AND_CACHE.md)
- [deploy/models-download-quantization.md](deploy/models-download-quantization.md)

## CI/CD

| Item | Path |
|------|------|
| GitHub Actions CI | `.github/workflows/home-ai-ci.yml` |
| Dockerfile | `Dockerfile` (port **8787**) |
| Server deploy | `deploy/deploy.sh` |

## v2 phases

| Phase | Status |
|-------|--------|
| V2-0 Android enums | ✅ |
| V2-1 FastAPI scaffold | ✅ |
| V2-2 Model setup pipeline | ✅ `setup_models` |
| V2-3–V2-6 Router + L1/L2/L3 cache | ✅ |
| V2-7–V2-9 Android paywall + admin + release | ✅ |
