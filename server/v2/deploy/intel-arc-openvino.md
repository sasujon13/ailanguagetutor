# Intel Arc — Home AI Server Deployment (v2)

Target: Intel Arc 16GB, i7-14700, 64GB RAM, NVMe SSD, Windows or Linux.

**Full model download + quantization guide:** [models-download-quantization.md](./models-download-quantization.md)

**One-click setup:** `bash scripts/setup_models.sh` or `.\scripts\setup_models.ps1`

See [models-download-quantization.md](./models-download-quantization.md) for details.

## Option A — Ollama (fastest MVP)

1. Install [Ollama](https://ollama.com) with GPU support.
2. Pull quantized model:
   ```bash
   ollama pull qwen2.5:7b-instruct-q4_K_M
   ```
3. Point `server/v2` inference backend to `http://127.0.0.1:11434`.
4. Set `MAX_CONCURRENT=4` in server env.

## Option B — OpenVINO (Intel Arc optimized — production)

1. Install OpenVINO 2024+ with GPU plugin + ONNX Runtime GPU.
2. Convert models to **ONNX**; quantize **INT4/INT8**:
   - Qwen2.5-7B-Instruct INT4 (Modes 1, 3)
   - Qwen2.5-14B-Instruct INT4 (Mode 5 — swap in/out, don't run with 7B)
   - Mistral-7B INT4 (Mode 4 OCR — keep **resident**)
   - NLLB-200 on CPU or GPU for translation modes
3. Enable KV cache reuse across batch requests.
4. **Resident models:** Mistral 7B + NLLB distilled + Whisper small.
5. **Lazy swap:** only one large LLM (7B or 14B) in VRAM at a time.

## Mode-aware loading

| Active mode | Load on GPU |
|-------------|-------------|
| 1, 3 | Qwen 7B |
| 4 (OCR auto) | Mistral 7B |
| 5 (Plus) | Qwen 14B (unload 7B first) |
| 2, 3 translate step | NLLB (separate process) |

## Performance targets

| Operation | Target |
|-----------|--------|
| OCR cleanup | < 2 s |
| Translate (paragraph) | < 3 s |
| Answer Mode /ask | 3–8 s |
| Cache hit | < 100 ms |

## Concurrency

- Async FastAPI + asyncio queue
- Max 4–8 parallel inference jobs on GPU
- Overflow → queue wait or **cloud burst** (admin toggle)

## Remote access (Android away from home LAN)

Use Cloudflare Tunnel or similar:

```text
https://ai-home.cheradip.com → localhost:8787
```

Set `HOME_AI_BASE_URL` in app admin. Use HTTPS only in production.

## Do not

- Load multiple 14B+ models simultaneously on 16GB VRAM
- Run per-word inference loops
- Expose Ollama directly to the public internet without auth — put FastAPI in front
