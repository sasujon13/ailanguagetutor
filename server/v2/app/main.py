"""Cheradip Home AI Server — FastAPI application."""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import admin, ai_modes, ask, clean_ocr, grammar_book, health, prefetch_ai, prefetch_grammar, scan_enhance, stt, translate, translate_strings, tts
from app.services.cache_l1 import CacheManager
from app.services.cache_l3 import L3Cache
from app.services.mode_router import ModeRouter
from app.services.model_loader import ModelLoader
from app.services.rate_limit import RateLimiter


@asynccontextmanager
async def lifespan(app: FastAPI):
    loader = ModelLoader(settings)
    cache = CacheManager(l3=L3Cache(db_path=settings.cache_db_path))
    rate_limiter = RateLimiter()
    await loader.initialize()
    app.state.model_loader = loader
    app.state.cache = cache
    app.state.rate_limiter = rate_limiter
    app.state.mode_router = ModeRouter(loader, cache, rate_limiter)
    yield
    cache.shutdown()
    await loader.shutdown()


app = FastAPI(
    title="Cheradip Home AI",
    version="2.0.0-dev",
    description="Local AI engine for AI Language Tutor (Intel Arc)",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, tags=["health"])
app.include_router(ai_modes.router, prefix="/ai", tags=["ai"])
app.include_router(clean_ocr.router, tags=["inference"])
app.include_router(scan_enhance.router, tags=["inference"])
app.include_router(translate.router, tags=["inference"])
app.include_router(ask.router, tags=["inference"])
app.include_router(prefetch_grammar.router, tags=["inference"])
app.include_router(prefetch_ai.router, tags=["inference"])
app.include_router(grammar_book.router, tags=["inference"])
app.include_router(translate_strings.router, tags=["inference"])
app.include_router(stt.router, tags=["inference"])
app.include_router(tts.router, tags=["inference"])
app.include_router(admin.router, prefix="/admin", tags=["admin"])


@app.get("/")
async def root():
    return {
        "service": "cheradip-home-ai",
        "version": "2.0.0-dev",
        "docs": "/docs",
    }
