"""Orchestrator: tier gate → classify → cache → select model → infer with fallback."""

import logging

from fastapi import HTTPException

from app.schemas import (
    AiRequest,
    AskResponse,
    CleanOcrResponse,
    InputSource,
    ProcessingIntent,
    SubscriptionTier,
    TranslateResponse,
)
from app.services.cache_l1 import CacheManager, cache_key
from app.services.complexity import complexity_score
from app.services.inference_engine import InferenceEngine
from app.services.model_loader import ModelLoader, ModelSlot
from app.services.model_selector import select_model
from app.services.rate_limit import RateLimiter
from app.services.task_classifier import TaskIntent, classify_intent

logger = logging.getLogger(__name__)


class ModeRouter:
    """
    Request → classify → complexity → model select → cache check → inference → response
    """

    def __init__(self, loader: ModelLoader, cache: CacheManager, rate_limiter: RateLimiter | None = None):
        self.loader = loader
        self.cache = cache
        self.rate_limiter = rate_limiter or RateLimiter()
        self.inference = InferenceEngine(loader)
        self.routes_total = 0
        self.routes_by_intent: dict[str, int] = {}

    def _enforce_rate_limit(self, req: AiRequest, device_id: str | None) -> None:
        self.rate_limiter.check(device_id or "anonymous", req.subscription_tier)

    def _tier_gate(self, req: AiRequest) -> None:
        if req.ai_engine_mode == 5 and req.subscription_tier != SubscriptionTier.PLUS:
            raise HTTPException(status_code=403, detail="PLUS_REQUIRED")

    def _key(self, req: AiRequest, endpoint: str) -> str:
        mode = 4 if endpoint == "clean-ocr" else req.ai_engine_mode
        return cache_key(
            req.text,
            req.processing_intent.value,
            mode,
            req.language_code,
            req.target_languages,
        )

    def _record(self, intent: TaskIntent) -> None:
        self.routes_total += 1
        self.routes_by_intent[intent.value] = self.routes_by_intent.get(intent.value, 0) + 1

    def stats(self) -> dict:
        return {
            "routes_total": self.routes_total,
            "routes_by_intent": self.routes_by_intent,
            "inference": self.inference.stats(),
            "cache": self.cache.stats(),
            "rate_limit": self.rate_limiter.stats(),
        }

    async def clean_ocr(self, req: AiRequest, device_id: str | None = None) -> CleanOcrResponse:
        self._tier_gate(req)
        intent = TaskIntent.OCR_CLEANUP
        self._record(intent)

        key = self._key(req, "clean-ocr")
        if cached := self.cache.get(key):
            return CleanOcrResponse(cleaned_text=cached, mode=4, cached=True)

        self._enforce_rate_limit(req, device_id)
        slot = select_model(intent, req)
        cleaned, _ = await self.inference.run_llm(
            slot,
            f"Fix OCR errors and structure this text:\n\n{req.text}",
            max_tokens=1024,
        )
        self.cache.set(key, cleaned)
        return CleanOcrResponse(cleaned_text=cleaned, mode=4, cached=False)

    async def translate(self, req: AiRequest, device_id: str | None = None) -> TranslateResponse:
        self._tier_gate(req)
        intent = classify_intent(req, "translate")
        if intent != TaskIntent.TRANSLATION:
            intent = TaskIntent.TRANSLATION
        self._record(intent)

        if req.input_source == InputSource.OCR and req.ai_engine_mode != 2:
            req = req.model_copy(update={"ai_engine_mode": 4})

        key = self._key(req, "translate")
        if cached := self.cache.get(key):
            return TranslateResponse(translations=cached, mode=req.ai_engine_mode, cached=True)

        self._enforce_rate_limit(req, device_id)
        targets = req.target_languages or ["fr"]
        translations = await self.inference.run_nllb(req.text, req.language_code, targets)
        self.cache.set(key, translations)
        return TranslateResponse(translations=translations, mode=req.ai_engine_mode, cached=False)

    async def ask(self, req: AiRequest, device_id: str | None = None) -> AskResponse:
        self._tier_gate(req)
        intent = classify_intent(req, "ask")
        if intent == TaskIntent.TRANSLATION:
            raise HTTPException(400, detail="Use /translate for translation intent")
        self._record(intent)

        key = self._key(req, "ask")
        if cached := self.cache.get(key):
            return AskResponse(
                explanation=cached["explanation"],
                simple=cached.get("simple"),
                mode=req.ai_engine_mode,
                cached=True,
            )

        self._enforce_rate_limit(req, device_id)
        ocr_noise = req.input_source == InputSource.OCR
        lang_count = 1 + len(req.target_languages or [])
        cx = complexity_score(req.text, ocr_noise, lang_count)
        slot = select_model(intent, req, cx)

        prompt = (
            "You are a language tutor. Answer using only the provided text.\n\n"
            f"Text ({req.language_code}):\n{req.text}\n\n"
            "Give a clear explanation for the learner."
        )
        explanation, used = await self.inference.run_llm(slot, prompt, max_tokens=2048)
        simple = explanation[:200] + ("…" if len(explanation) > 200 else "")
        payload = {"explanation": explanation, "simple": simple, "model": used.value}
        self.cache.set(key, payload)

        return AskResponse(
            explanation=explanation,
            simple=simple,
            mode=req.ai_engine_mode,
            cached=False,
        )
