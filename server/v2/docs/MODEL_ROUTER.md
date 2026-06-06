# Model Router — Production Design

Core intelligence layer: **classify → score → select → cache → infer → fallback**.

```text
Request → Preprocessor → Intent Classifier → Complexity Scorer → Model Selector
       → Cache Check → Inference Engine → Response
```

Implementation: `app/services/`

| Module | Role |
|--------|------|
| `task_classifier.py` | TRANSLATION · ANSWER · OCR_CLEANUP |
| `complexity.py` | Score 0–N → LOW / MEDIUM / HIGH |
| `model_selector.py` | Pick ModelSlot + fallback chain |
| `mode_router.py` | Orchestrator + tier gate |
| `inference_engine.py` | Run model with fallbacks |
| `cache_l1.py` | Cache-first (never infer if hit) |

## Intent rules

| Intent | Route |
|--------|-------|
| Translation | **NLLB only** |
| OCR cleanup | **Mistral 7B** |
| Answer / tutor | LLM by complexity + user mode |

## Complexity scoring

```python
score = 0
if text_length > 500: score += 2
if why/how/explain:     score += 3
if multiple languages: score += 2
if OCR noise:          score += 2
if wants detail:       score += 2

# score <= 3  → LOW    → Mistral 7B
# score <= 6  → MEDIUM → Qwen 7B
# score > 6   → HIGH   → Qwen 14B (Plus + mode 5)
```

## Model selection summary

| Task | Model |
|------|-------|
| Translation | NLLB |
| OCR cleanup | Mistral |
| Simple Q&A | Mistral |
| Medium reasoning | Qwen 7B |
| Complex reasoning | Qwen 14B |

## Fallback chain

```text
Qwen 14B → Qwen 7B → Mistral 7B → Llama 8B → generic template
```

## Curated user modes (Layer B)

User mode from Android still constrains selection — see `select_model()` in `model_selector.py`.

## Admin metrics

`GET /admin/status` → `router.routes_by_intent`, `inference.fallback_count`, `cache.hit_rate_pct`

## CI/CD

- **CI:** `.github/workflows/home-ai-ci.yml` — pytest + Docker build on push
- **Deploy:** `deploy/deploy.sh` — load image, run models setup if needed, start container on **8787**

Intel Arc env on deploy:

```bash
export INFERENCE_BACKEND=openvino
export OPENVINO_DEVICE=GPU
export ONEAPI_DEVICE_SELECTOR=level_zero:gpu
```
