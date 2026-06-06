from fastapi import APIRouter, Request

from app.schemas import HealthResponse

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
async def health(request: Request):
    loader = request.app.state.model_loader
    cache = request.app.state.cache
    gpu = await loader.refresh_gpu_status()
    status = loader.status()
    return HealthResponse(
        status="ok",
        gpu_available=gpu.gpu_available,
        gpu_in_use=gpu.gpu_in_use,
        gpu_device=gpu.gpu_device,
        gpu_library=gpu.gpu_library,
        gpu_vram_total=gpu.gpu_vram_total,
        gpu_vram_available=gpu.gpu_vram_available,
        loaded_processors=gpu.loaded_processors,
        inference_backend=loader.backend_name,
        model_loaded=loader.active_llm,
        resident_models=loader.resident_models,
        queue_depth=loader.queue_depth,
        cache_hit_rate=cache.stats().get("hit_rate_pct"),
        ollama_models=status.get("ollama_models"),
        models_on_disk=status.get("models_on_disk"),
    )
