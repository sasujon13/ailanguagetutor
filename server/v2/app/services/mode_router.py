"""Orchestrator: tier gate → classify → cache → select model → infer with fallback."""

import logging

from fastapi import HTTPException

from app.schemas import (
    AiRequest,
    AiPrefetchRequest,
    AiPrefetchResponse,
    AskResponse,
    CleanOcrResponse,
    GrammarPrefetchRequest,
    GrammarPrefetchResponse,
    GrammarPrefetchResultItem,
    GrammarBookRequest,
    GrammarBookResponse,
    GrammarBookEnrichRequest,
    GrammarBookEnrichResponse,
    InputSource,
    ProcessingIntent,
    SubscriptionTier,
    TranslateResponse,
    TranslateStringsRequest,
    TranslateStringsResponse,
)
from app.services.cache_l1 import CacheManager, cache_key
from app.services.complexity import complexity_score
from app.services.grammar_prompt import build_grammar_prompt
from app.services.grammar_book import (
    build_grammar_book_prompt,
    build_section_enrich_prompt,
    fallback_grammar_book,
    grammar_book_cache_key,
    grammar_book_enrich_cache_key,
    parse_grammar_book,
    parse_section_enrich,
)
from app.services.translate_strings import (
    _cache_key as translate_strings_cache_key,
    build_batch_prompt,
    parse_batch_response,
)
from app.services.inference_engine import InferenceEngine
from app.services.model_loader import ModelLoader, ModelSlot
from app.services.model_selector import select_model
from app.services.practice_prompt import build_answer_prompt
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
        targets = [
            t for t in (req.target_languages or ["fr"])
            if t.lower() != req.language_code.lower()
        ]
        if not targets:
            raise HTTPException(
                400,
                detail="Translation requires a target language different from the source.",
            )
        polish = req.ai_engine_mode == 3
        translations = await self.inference.run_nllb(
            req.text,
            req.language_code,
            targets,
            polish=polish,
        )
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

        targets = req.target_languages or [req.language_code]
        prompt = build_answer_prompt(req.text, req.language_code, targets)
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

    async def prefetch_grammar(
        self,
        req: GrammarPrefetchRequest,
        device_id: str | None = None,
    ) -> GrammarPrefetchResponse:
        """Warm L1/L2/L3 cache for upcoming word taps (3 words, 3 sentences, or 1 paragraph)."""
        if req.subscription_tier == SubscriptionTier.FREE:
            return GrammarPrefetchResponse(
                warmed=0,
                cached=0,
                total=len(req.items),
                language_code=req.language_code,
                target_languages=req.target_languages,
            )

        target = req.target_languages[0] if req.target_languages else "en"
        warmed = 0
        cached_hits = 0
        results: list[GrammarPrefetchResultItem] = []
        mode = 4 if req.input_source == InputSource.OCR else req.ai_engine_mode

        for item in req.items[:6]:
            prompt = build_grammar_prompt(
                item.context_text,
                item.focus_word,
                req.language_code,
                target,
                req.grammar_depth,
            )
            key = cache_key(
                prompt,
                ProcessingIntent.ANSWER.value,
                mode,
                req.language_code,
                req.target_languages,
            )
            cached = self.cache.get(key)
            if cached is not None:
                cached_hits += 1
                explanation = cached.get("explanation", "") if isinstance(cached, dict) else str(cached)
                results.append(
                    GrammarPrefetchResultItem(focus_word=item.focus_word, explanation=explanation)
                )
                continue

            ai_req = AiRequest(
                processing_intent=ProcessingIntent.ANSWER,
                ai_engine_mode=req.ai_engine_mode,
                input_source=req.input_source,
                subscription_tier=req.subscription_tier,
                text=prompt,
                language_code=req.language_code,
                target_languages=req.target_languages,
            )
            try:
                self._tier_gate(ai_req)
                self._enforce_rate_limit(ai_req, device_id)
                slot = select_model(TaskIntent.ANSWER, ai_req, complexity_score(prompt, False, 1))
                explanation, _ = await self.inference.run_llm(slot, prompt, max_tokens=1024)
                simple = explanation[:200] + ("…" if len(explanation) > 200 else "")
                self.cache.set(key, {"explanation": explanation, "simple": simple})
                warmed += 1
                results.append(
                    GrammarPrefetchResultItem(focus_word=item.focus_word, explanation=explanation)
                )
            except Exception as e:
                logger.warning("Grammar prefetch item failed: %s", e)

        return GrammarPrefetchResponse(
            warmed=warmed,
            cached=cached_hits,
            total=len(req.items),
            language_code=req.language_code,
            target_languages=req.target_languages,
            results=results,
        )

    async def prefetch_ai(
        self,
        req: AiPrefetchRequest,
        device_id: str | None = None,
    ) -> AiPrefetchResponse:
        """Single background warm: grammar items + at most one explain or translate chunk."""
        grammar_req = GrammarPrefetchRequest(
            grammar_depth=req.grammar_depth,
            language_code=req.language_code,
            target_languages=req.target_languages,
            ai_engine_mode=req.ai_engine_mode,
            input_source=req.input_source,
            subscription_tier=req.subscription_tier,
            items=req.grammar_items[:6],
        )
        grammar = await self.prefetch_grammar(grammar_req, device_id)

        explain_warmed = explain_cached = False
        translate_warmed = translate_cached = False

        if req.subscription_tier != SubscriptionTier.FREE:
            if req.explain_text and req.explain_text.strip():
                chunk = req.explain_text.strip()[:480]
                ai_req = AiRequest(
                    processing_intent=ProcessingIntent.ANSWER,
                    ai_engine_mode=req.ai_engine_mode,
                    input_source=req.input_source,
                    subscription_tier=req.subscription_tier,
                    text=chunk,
                    language_code=req.language_code,
                    target_languages=req.target_languages,
                )
                key = self._key(ai_req, "ask")
                if self.cache.get(key) is not None:
                    explain_cached = True
                else:
                    try:
                        await self.ask(ai_req, device_id)
                        explain_warmed = True
                    except Exception as e:
                        logger.warning("Explain prefetch failed: %s", e)

            elif req.translate_text and req.translate_text.strip():
                chunk = req.translate_text.strip()[:480]
                ai_req = AiRequest(
                    processing_intent=ProcessingIntent.TRANSLATION,
                    ai_engine_mode=2,
                    input_source=req.input_source,
                    subscription_tier=req.subscription_tier,
                    text=chunk,
                    language_code=req.language_code,
                    target_languages=req.target_languages or ["fr"],
                )
                key = self._key(ai_req, "translate")
                if self.cache.get(key) is not None:
                    translate_cached = True
                else:
                    try:
                        await self.translate(ai_req, device_id)
                        translate_warmed = True
                    except Exception as e:
                        logger.warning("Translate prefetch failed: %s", e)

        return AiPrefetchResponse(
            grammar=grammar,
            explain_warmed=explain_warmed,
            explain_cached=explain_cached,
            translate_warmed=translate_warmed,
            translate_cached=translate_cached,
        )

    async def grammar_book(
        self,
        req: GrammarBookRequest,
        device_id: str | None = None,
    ) -> GrammarBookResponse:
        """Generate or return cached structured grammar learning book for a language."""
        lang = req.language_code.lower()
        name = req.language_name or lang
        key = grammar_book_cache_key(lang)

        if req.subscription_tier == SubscriptionTier.FREE:
            book = fallback_grammar_book(lang, name)
            return book.model_copy(update={"cached": False})

        if cached := self.cache.get(key):
            if isinstance(cached, dict) and cached.get("chapters"):
                return GrammarBookResponse(**cached, cached=True)

        ai_req = AiRequest(
            processing_intent=ProcessingIntent.ANSWER,
            ai_engine_mode=req.ai_engine_mode,
            input_source=InputSource.TYPED,
            subscription_tier=req.subscription_tier,
            text=build_grammar_book_prompt(lang, name),
            language_code=lang,
            target_languages=[],
        )
        try:
            self._tier_gate(ai_req)
            self._enforce_rate_limit(ai_req, device_id)
            slot = select_model(TaskIntent.ANSWER, ai_req, complexity_score(ai_req.text, False, 1))
            raw, _ = await self.inference.run_llm(slot, ai_req.text, max_tokens=4096)
            book = parse_grammar_book(raw, lang, name)
        except Exception as e:
            logger.warning("Grammar book generation failed: %s", e)
            book = fallback_grammar_book(lang, name)

        payload = book.model_dump()
        self.cache.set(key, payload)
        return book.model_copy(update={"cached": False})

    async def grammar_book_enrich_section(
        self,
        req: GrammarBookEnrichRequest,
        device_id: str | None = None,
    ) -> GrammarBookEnrichResponse:
        """Expand a grammar book section with deeper AI explanation (cached per section)."""
        lang = req.language_code.lower()
        key = grammar_book_enrich_cache_key(lang, req.chapter_number, req.section_heading)

        if req.subscription_tier == SubscriptionTier.FREE:
            return GrammarBookEnrichResponse(
                expanded_body=req.section_body,
                extra_examples=[],
                learner_tip="Upgrade to Pro for AI-expanded grammar lessons.",
                cached=False,
            )

        if cached := self.cache.get(key):
            if isinstance(cached, dict) and cached.get("expanded_body"):
                return GrammarBookEnrichResponse(**cached, cached=True)

        prompt = build_section_enrich_prompt(
            lang,
            req.language_name or lang,
            req.chapter_number,
            req.chapter_title,
            req.section_heading,
            req.section_body,
            req.examples,
        )
        ai_req = AiRequest(
            processing_intent=ProcessingIntent.ANSWER,
            ai_engine_mode=req.ai_engine_mode,
            input_source=InputSource.TYPED,
            subscription_tier=req.subscription_tier,
            text=prompt,
            language_code=lang,
            target_languages=[],
        )
        try:
            self._tier_gate(ai_req)
            self._enforce_rate_limit(ai_req, device_id)
            slot = select_model(TaskIntent.ANSWER, ai_req, complexity_score(ai_req.text, False, 1))
            raw, _ = await self.inference.run_llm(slot, ai_req.text, max_tokens=1024)
            parsed = parse_section_enrich(raw, req.section_body)
        except Exception as e:
            logger.warning("Grammar section enrich failed: %s", e)
            parsed = parse_section_enrich("{}", req.section_body)

        response = GrammarBookEnrichResponse(**parsed, cached=False)
        self.cache.set(key, response.model_dump())
        return response

    async def translate_strings(
        self,
        req: TranslateStringsRequest,
        device_id: str | None = None,
    ) -> TranslateStringsResponse:
        """Batch-translate app UI strings (en -> target) via LLM or NLLB per string."""
        target = req.target_language.lower()
        source = req.source_language.lower() or "en"
        strings = req.strings or {}
        if not strings or target == source:
            return TranslateStringsResponse(translations=strings, cached=True, backend="noop")

        key = translate_strings_cache_key(target)
        if cached := self.cache.get(key):
            if isinstance(cached, dict):
                return TranslateStringsResponse(translations=cached, cached=True, backend="cache")

        translations: dict[str, str] = {}
        backend_used = "nllb"

        # Short UI labels: NLLB per key; fallback LLM batch if many failures
        for k, text in strings.items():
            if not text.strip():
                translations[k] = text
                continue
            try:
                nllb = await self.inference.run_nllb(text, source, [target])
                translations[k] = nllb.get(target, text)
            except Exception as e:
                logger.warning("NLLB string translate failed for %s: %s", k, e)
                translations[k] = text

        # Polish batch via LLM when Ollama available (better for grammar/UI tone)
        if self.loader.backend_name == "ollama":
            try:
                ai_req = AiRequest(
                    processing_intent=ProcessingIntent.ANSWER,
                    ai_engine_mode=1,
                    input_source=InputSource.TYPED,
                    subscription_tier=SubscriptionTier.PRO,
                    text=build_batch_prompt(source, target, strings),
                    language_code=source,
                    target_languages=[target],
                )
                self._enforce_rate_limit(ai_req, device_id)
                slot = select_model(TaskIntent.ANSWER, ai_req, complexity_score(ai_req.text, False, 1))
                raw, _ = await self.inference.run_llm(slot, ai_req.text, max_tokens=2048)
                translations = parse_batch_response(raw, translations)
                backend_used = "llm-batch"
            except Exception as e:
                logger.warning("LLM batch UI translate failed: %s", e)

        self.cache.set(key, translations)
        return TranslateStringsResponse(
            translations=translations,
            cached=False,
            backend=backend_used,
        )
