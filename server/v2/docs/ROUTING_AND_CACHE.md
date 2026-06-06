# AI Routing + Multi-Tier Cache (v2.0.0)

Implementation reference for Cheradip Home AI Server. Aligns with `ailanguagetutor.md` → Version 2.0.0.

## Overview

Two user-facing layers + three server layers:

```text
User picks:  Processing intent (Answer | Translation) + AI mode (1–5) + tier (Pro | Plus)
Server runs: Task classifier → Complexity score → Model selector → Cache → Inference
```

Users **never** see Mistral, Qwen, NLLB, or Llama in the UI.

---

## Task classifier

File: `app/services/task_classifier.py`

| Task type | When | Next step |
|-----------|------|-----------|
| `OCR_CLEAN` | `/clean-ocr` or OCR preprocessing | Mistral 7B or Qwen 7B (Mode 4) |
| `TRANSLATE` | Translation intent or modes 2/3/5 translate path | NLLB |
| `ASK` | Answer intent or modes 1/3/5 tutor path | Complexity → model selector |
| `VOICE` | Raw audio | Whisper → text → re-classify |

Rule-based first pass:

```python
def classify(request) -> TaskType:
    if request.audio_bytes:
        return TaskType.VOICE
    if request.endpoint == "clean-ocr" or request.input_source == "ocr" and request.needs_cleanup:
        return TaskType.OCR_CLEAN
    if request.processing_intent == "translation":
        return TaskType.TRANSLATE
    return TaskType.ASK
```

Optional lightweight model (fastText / tiny classifier) only if rules are ambiguous.

---

## Complexity scoring

File: `app/services/complexity.py`

Used **only** for `ASK` path when selecting Mistral 7B vs Qwen 7B vs Qwen 14B.

```python
def score(text: str, ocr_noise: float, question_type: str, lang_count: int) -> int:
    s = 0
    s += min(len(text) // 500, 5)           # length buckets
    s += lang_count * 2
    s += {"why": 3, "how": 3, "explain": 3}.get(question_type, 0)
    s += int(ocr_noise * 5)                 # 0.0–1.0 from OCR confidence
    return s

def bucket(score: int) -> str:
    if score <= 3: return "LOW"
    if score <= 8: return "MEDIUM"
    return "HIGH"
```

**Qwen 14B** only when:

- `bucket == HIGH`
- `ai_engine_mode == 5`
- `subscription_tier == plus`

Otherwise cap at Qwen 7B (MEDIUM) or Mistral 7B (LOW / Mode 4).

---

## Model selector

File: `app/services/model_selector.py`

Combines **user mode**, **task type**, **complexity**, and **GPU state**.

| Mode | Translate | Ask (LOW) | Ask (MEDIUM) | Ask (HIGH) |
|------|-----------|-----------|--------------|------------|
| 1 Smart Tutor | NLLB | Mistral 7B | Qwen 7B | Qwen 7B |
| 2 Fast Translation | NLLB | — | — | — |
| 3 Balanced | NLLB + cleanup | Mistral 7B | Qwen 7B | Qwen 7B |
| 4 Lightweight | NLLB | Mistral 7B | Mistral 7B | Qwen 7B |
| 5 High Accuracy (Plus) | NLLB + blend | Qwen 7B | Qwen 7B | **Qwen 14B** |

Fallback chain: primary → Llama 3 8B → cloud burst (admin enabled).

Translation: **NLLB only**. Call Qwen 7B once if NLLB returns `UNSUPPORTED_PAIR` — never per-sentence.

---

## Mode router (orchestrator)

File: `app/services/mode_router.py`

```python
async def route(request: AiRequest) -> AiResponse:
    # 1. Tier gate
    if request.ai_engine_mode == 5 and request.subscription_tier != "plus":
        raise HTTPException(403, detail="PLUS_REQUIRED")

    # 2. OCR auto Mode 4
    if request.input_source == "ocr" and request.endpoint != "clean-ocr":
        await clean_ocr_pipeline(request)  # forces Mode 4 internally

    # 3. Classify
    task = classify(request)

    # 4. Cache (before any GPU work)
    key = cache_key(request)
    if hit := await cache.get(key):
        return hit

    # 5. Select model + queue
    model = select_model(request, task)
    async with gpu_queue.slot(model):
        result = await infer(model, request, task)

    # 6. Store all tiers
    await cache.set(key, result)
    return result
```

---

## Cache layers

### L1 — RAM (process-local)

File: `app/services/cache_l1.py`

- TTL: **1–5 seconds**
- LRU, max ~500 entries
- Same user double-tap / retry → instant

### L2 — Shared (Redis or fallback dict)

File: `app/services/cache_l2.py`

- Key: `sha256(normalized_text + processing_intent + ai_engine_mode + src + targets)`
- TTL: 24h–7d by task type (translate longer than ask)
- Env: `REDIS_URL` optional; use `InMemoryL2` for solo dev

### L3 — SQLite persistent

File: `app/services/cache_l3.py`

- Table: `ai_cache (key, response_json, created_at, hit_count, mode, intent)`
- Used for admin stats + warm L2 on restart
- Android Room `ai_cache` mirrors hot keys client-side

### Cache flow

```text
get(key):
  L1 → L2 → L3
  on L2/L3 hit: promote upward

set(key, value):
  write L1 + L2 + L3 atomically (best effort)
```

**Hard rule:** if any tier hits, **do not** call inference. Increment `cache_hits` metric.

---

## Cache key normalization

```python
def normalize_text(text: str) -> str:
    return " ".join(text.lower().split())  # collapse whitespace, lowercase

def cache_key(req) -> str:
    payload = f"{normalize_text(req.text)}|{req.processing_intent}|{req.ai_engine_mode}|{req.language_code}|{','.join(sorted(req.target_languages))}"
    return hashlib.sha256(payload.encode()).hexdigest()
```

Do **not** include `device_id` in key — shared cache across users for identical content.

---

## Intel Arc inference order

1. Check cache (all tiers)
2. Acquire queue slot
3. Lazy-load model if not resident (swap 7B ↔ 14B)
4. OpenVINO / ONNX Runtime GPU inference
5. Post-process JSON
6. Write cache + release slot

See `deploy/intel-arc-openvino.md`.

---

## Metrics (admin `/admin/status`)

| Metric | Purpose |
|--------|---------|
| `cache_hit_rate_l1/l2/l3` | Tuning TTL and infra cost |
| `queue_depth` | Burst / scale decisions |
| `model_loaded` | Current GPU resident model |
| `inference_ms_by_task` | SLA monitoring |
| `requests_by_mode` | Usage by tier/mode |

---

## Critical design rules

- No per-word AI calls
- No duplicate uncached identical requests (dedupe in queue by key)
- Translation Mode never uses LLM except rare NLLB fallback
- Mode 4 auto on OCR — mandatory
- Mode 5 requires Plus tier
- Stateless APIs — no user session on GPU
