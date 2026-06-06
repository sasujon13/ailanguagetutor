from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/status")
async def admin_status(request: Request):
    loader = request.app.state.model_loader
    cache = request.app.state.cache
    router_svc = request.app.state.mode_router
    rate_limit = request.app.state.rate_limiter
    gpu = await loader.refresh_gpu_status()
    cache_stats = cache.stats()
    return {
        "model_loaded": loader.active_llm,
        "resident_models": loader.resident_models,
        "queue_depth": loader.queue_depth,
        "cache_stats": cache_stats,
        "cache_hit_rate_l1": cache_stats.get("cache_hit_rate_l1"),
        "cache_hit_rate_l2": cache_stats.get("cache_hit_rate_l2"),
        "cache_hit_rate_l3": cache_stats.get("cache_hit_rate_l3"),
        "gpu_available": gpu.gpu_available,
        "gpu_in_use": gpu.gpu_in_use,
        "gpu_device": gpu.gpu_device,
        "gpu_library": gpu.gpu_library,
        "gpu_vram_total": gpu.gpu_vram_total,
        "gpu_vram_available": gpu.gpu_vram_available,
        "loaded_processors": gpu.loaded_processors,
        "inference_backend": loader.backend_name,
        "router": router_svc.stats(),
        "rate_limit": rate_limit.stats(),
    }
