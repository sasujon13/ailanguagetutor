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
| GET | `/ide/models` | Cheradip VS Code extension |
| POST | `/ide/chat` | Streaming IDE chat (SSE) |
| POST | `/ide/complete` | Inline completion |
| POST | `/ide/edit` | Inline code edit |

| POST | `/scan-enhance` | AI Clean levels 0–7 (ONNX + OpenCV) |
| POST | `/scan-analyze` | Pre-process metrics + recommendation |

## Scan enhancement ONNX

**Windows setup:** `.\scripts\setup_scan_models.ps1` (venv repair + download/export + tests)

```powershell
cd server\v2
pip install -e ".[scan]"
.\scripts\setup_scan_models.ps1
# or step by step:
python scripts/download_scan_models.py --only u2net
python scripts/export_scan_onnx.py yolov8       # pip install ultralytics
python scripts/export_scan_onnx.py realesrgan   # pip install torch only
python scripts/validate_scan_levels.py photo.jpg --out reports/scan_validation
python -m pytest tests/test_scan_levels_validation.py -v
```

Weights live in `models/scan/` — see [models/scan/README.md](models/scan/README.md).

`/health` includes `scan_models`: loaded ONNX ids (e.g. `u2net`, `yolov8`, `realesrgan`). Home AI runs on **Windows** behind Cloudflare (`ai.cheradip.com`), not on Linux.

## Scripts

| Script | Purpose |
|--------|---------|
| `setup_models.ps1` / `.sh` | Download + convert + verify |
| `run-dev.ps1` / `.sh` | Start FastAPI on port **8787** |
| `model_setup/download_models.py` | HF snapshots |
| `model_setup/convert_openvino.py` | Optimum export |
| `model_setup/verify_models.py` | Health check |
| `download_scan_models.py` | Scan ONNX weights (u2net) |
| `setup_scan_models.ps1` | One-click scan ONNX setup (Windows) |
| `export_scan_onnx.py` | Export yolov8 / realesrgan ONNX |
| `validate_scan_levels.py` | Level 0/4/5/6/7 comparison report |

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
