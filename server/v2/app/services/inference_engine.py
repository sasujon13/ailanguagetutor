"""Inference with fallback chain and generic template last resort."""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from app.services.model_loader import ModelLoader, ModelSlot
from app.services.model_selector import fallback_chain

logger = logging.getLogger(__name__)

_GENERIC_TUTOR = (
    "I could not run the full AI model right now. "
    "Here is a brief note based on your text: {snippet}"
)

_QWEN_OLLAMA = "qwen2.5:7b-instruct-q4_K_M"


class InferenceEngine:
    def __init__(self, loader: ModelLoader):
        self.loader = loader
        self.last_model_used: str | None = None
        self.fallback_count = 0
        self.last_translation_backend: str | None = None

    async def run_llm(
        self,
        primary: ModelSlot,
        prompt: str,
        max_tokens: int = 512,
    ) -> tuple[str, ModelSlot]:
        for slot in fallback_chain(primary):
            try:
                await self.loader.ensure_llm(slot)
                text = await self._infer(slot, prompt, max_tokens)
                self.last_model_used = slot.value
                if slot != primary:
                    self.fallback_count += 1
                    logger.warning("Fallback: %s -> %s", primary.value, slot.value)
                return text, slot
            except Exception as e:
                logger.error("Inference failed on %s: %s", slot.value, e)
                continue
        self.fallback_count += 1
        snippet = prompt[:160].replace("\n", " ")
        return _GENERIC_TUTOR.format(snippet=snippet), primary

    async def run_nllb(
        self,
        text: str,
        source_lang: str,
        target_langs: list[str],
    ) -> dict[str, str]:
        await self.loader.ensure_llm(ModelSlot.NLLB)
        self.last_model_used = ModelSlot.NLLB.value
        return {
            lang: await self._infer_nllb(text, source_lang, lang)
            for lang in target_langs
        }

    async def _infer(self, slot: ModelSlot, prompt: str, max_tokens: int) -> str:
        backend = self.loader.backend_name
        if backend == "ollama":
            from app.backends.ollama import OllamaBackend

            model_map = {
                ModelSlot.QWEN_7B: _QWEN_OLLAMA,
                ModelSlot.QWEN_14B: "qwen2.5:14b-instruct-q4_K_M",
                ModelSlot.MISTRAL_7B: "mistral:7b-instruct-q4_K_M",
                ModelSlot.LLAMA_8B: "llama3:8b-instruct-q4_K_M",
            }
            ollama = OllamaBackend(self.loader.settings.ollama_base_url)
            return await ollama.generate(
                model_map.get(slot, _QWEN_OLLAMA),
                prompt,
                max_tokens,
            )

        return f"[{slot.value}] {prompt[:300]}"

    async def _infer_nllb(self, text: str, source: str, target: str) -> str:
        from app.backends.nllb_translator import get_nllb_translator

        nllb = get_nllb_translator(self.loader.settings.models_dir)
        if nllb is not None:
            try:
                translated = await asyncio.to_thread(nllb.translate, text, source, target)
                self.last_translation_backend = "nllb-600m"
                logger.info("NLLB translation %s->%s (%d chars)", source, target, len(text))
                return translated
            except Exception as e:
                logger.warning("NLLB failed (%s->%s): %s — trying LLM fallback", source, target, e)

        if self.loader.backend_name == "ollama":
            translated = await self._ollama_translate(text, source, target)
            self.last_translation_backend = "ollama-qwen-fallback"
            self.fallback_count += 1
            logger.info("Ollama Qwen translation fallback %s->%s", source, target)
            return translated

        raise RuntimeError(
            "Translation unavailable: run .\\scripts\\pull-nllb-model.ps1 or enable Ollama with Qwen 7B"
        )

    async def _ollama_translate(self, text: str, source: str, target: str) -> str:
        from app.backends.ollama import OllamaBackend

        prompt = (
            f"Translate the following text from {source} to {target}.\n"
            "Output ONLY the translation. No quotes, labels, or explanation.\n\n"
            f"{text}"
        )
        ollama = OllamaBackend(self.loader.settings.ollama_base_url)
        result = await ollama.generate(_QWEN_OLLAMA, prompt, max_tokens=1024)
        return result.strip()

    def stats(self) -> dict[str, Any]:
        return {
            "last_model_used": self.last_model_used,
            "last_translation_backend": self.last_translation_backend,
            "fallback_count": self.fallback_count,
            "active_llm": self.loader.active_llm,
        }
