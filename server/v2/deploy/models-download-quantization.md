# Intel Arc — Model Download & Quantization Guide (v2)

Production guide for Cheradip Home AI Server on **Intel Arc 16GB**, **i7-14700**, Linux or Windows.

See also: [intel-arc-openvino.md](./intel-arc-openvino.md) · [ROUTING_AND_CACHE.md](../docs/ROUTING_AND_CACHE.md)

## Goal

Run efficiently on one GPU:

| Component | Model | Mode |
|-----------|-------|------|
| Answer / tutor | Qwen2.5 7B / 14B | Modes 1, 3, 5 |
| OCR cleanup | Mistral 7B | Mode 4 (auto) |
| Translation | NLLB-200 distilled | Modes 2, 3, 5 — **never LLM-only** |
| STT | Whisper small/base | Voice input |
| TTS | Piper | Voice output (CPU OK) |
| Fallback | Llama 3 8B | When primary LLM errors |

## Required stack

| Layer | Tool |
|-------|------|
| GPU inference | **OpenVINO 2024+** (primary) |
| Alternative MVP | Ollama + Intel GPU offload |
| Format | **ONNX** → OpenVINO IR |
| Quantization | **INT8** production · **INT4** low VRAM |

Install OpenVINO:

```bash
pip install openvino optimum[openvino] onnx onnxruntime
```

Update Intel Arc drivers to latest stable.

## Model sources (HuggingFace)

| Role | Repository | Quantized target |
|------|------------|------------------|
| LLM primary | `Qwen/Qwen2.5-7B-Instruct` | `models/llm/qwen2.5-7b-int8/` |
| LLM Plus | `Qwen/Qwen2.5-14B-Instruct` | `models/llm/qwen2.5-14b-int4/` |
| LLM fast / OCR | `mistralai/Mistral-7B-Instruct-v0.3` | `models/llm/mistral-7b-int4/` |
| LLM fallback | `meta-llama/Meta-Llama-3-8B-Instruct` | `models/llm/llama3-8b-int4/` |
| Translation | `facebook/nllb-200-distilled-600M` | `models/translation/nllb-600m/` |
| STT | `openai/whisper-small` | `models/stt/whisper-small/` |
| TTS | Piper voices | `models/tts/piper-en/` |

Use **`scripts/setup_models.sh`** (Linux/WSL) or **`scripts/setup_models.ps1`** (Windows) for **one-click** download + OpenVINO conversion + verify.

```bash
cd server/v2
bash scripts/setup_models.sh
```

```powershell
cd server\v2
.\scripts\setup_models.ps1
```

Optional gated models (Llama 3, Qwen 14B):

```bash
export HF_TOKEN=hf_...
export INCLUDE_OPTIONAL=1
bash scripts/setup_models.sh
```

### What `setup_models` does

| Step | Script | Action |
|------|--------|--------|
| 1 | — | Create venv, install `optimum[openvino]` |
| 2 | `model_setup/config.py` | Create `models/` layout |
| 3 | `model_setup/download_models.py` | HuggingFace snapshot download |
| 4 | `model_setup/convert_openvino.py` | Optimum INT8/INT4 export |
| 5 | `model_setup/verify_models.py` | Check HF + OpenVINO XML |
| 6 | — | Set `INFERENCE_BACKEND=openvino` in `.env` |

**Design rule:** download everything; **load only what the router needs** (lazy swap — one large LLM in VRAM).

## Storage layout

```text
server/v2/models/
  llm/
    qwen2.5-7b-int8/
    qwen2.5-14b-int4/
    mistral-7b-int4/
    llama3-8b-int4/
  translation/
    nllb-600m/
  stt/
    whisper-small/
  tts/
    piper-en/
  manifest.json
```

## Quantization pipeline

**Never run FP32/FP16 in production.**

### Step 1 — Export to ONNX (Optimum)

```bash
optimum-cli export onnx \
  --model Qwen/Qwen2.5-7B-Instruct \
  --task text-generation-with-past \
  models/llm/qwen2.5-7b-onnx/
```

### Step 2 — Convert to OpenVINO IR

```bash
mo --input_model models/llm/qwen2.5-7b-onnx/model.onnx \
   --output_dir models/llm/qwen2.5-7b-int8 \
   --compress_to_fp16
```

### Step 3 — INT8 quantization (recommended)

```bash
optimum-cli export openvino \
  --model Qwen/Qwen2.5-7B-Instruct \
  --task text-generation-with-past \
  --weight-format int8 \
  models/llm/qwen2.5-7b-int8/
```

### Step 4 — Load in Python

```python
from app.backends.openvino_runtime import OpenVinoBackend

backend = OpenVinoBackend(Path("./models"), device="GPU")
backend.load("llm/qwen2.5-7b-int8")
```

## Lazy loading (mandatory)

```text
Request → classify task → cache check
  → ensure resident (NLLB, Mistral) loaded
  → if LLM needed: unload previous LLM → load Qwen 7B OR 14B
  → infer → cache → response
```

| Memory | Strategy |
|--------|----------|
| **Always resident** | NLLB 600M, Mistral 7B INT4 (OCR) |
| **One at a time** | Qwen 7B ↔ Qwen 14B ↔ Llama 8B |
| **On demand** | Whisper, Piper (CPU preferred for Piper) |

Implemented in `app/services/model_loader.py`.

## Intel Arc optimization rules

1. **Batching** — queue up to `MAX_CONCURRENT=4–8` compatible requests
2. **KV cache reuse** — do not recompute prefix tokens
3. **Context limits** — Answer 1024–2048 tokens · Translation 512–1024
4. **GPU first** — `openvino_device=GPU`, CPU fallback when saturated

## Model usage map

| App mode | Model |
|----------|-------|
| Answer Mode | Qwen 7B → Mistral fallback |
| Translation Mode | NLLB only |
| OCR cleanup | Mistral 7B (Mode 4 auto) |
| Voice in | Whisper small |
| Voice out | Piper |

## Performance targets (Arc 16GB)

| Task | Target |
|------|--------|
| Translation (NLLB) | < 2–3 s |
| Answer Mode | 3–8 s |
| OCR cleanup | < 2 s |
| STT | near real-time |
| TTS | < 1 s |
| Cache hit | < 100 ms |

## Production rules

- NEVER full precision weights in hot path
- ALWAYS INT8 or INT4
- ALWAYS cache before infer (`NEVER call AI if cache exists`)
- NEVER two large LLMs in VRAM simultaneously
- ALWAYS NLLB for translation
- Qwen/Mistral/Llama for reasoning only

## Quick start (MVP without OpenVINO export)

1. Install [Ollama](https://ollama.com) with GPU
2. `ollama pull qwen2.5:7b-instruct-q4_K_M`
3. Set `INFERENCE_BACKEND=ollama` in `.env`
4. Run `uvicorn app.main:app --host 0.0.0.0 --port 8787`

Move to OpenVINO when ready for production INT8 pipeline.
