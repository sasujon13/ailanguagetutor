"""Task classification and parallel model routing for Cheradip IDE Auto mode."""

from __future__ import annotations

import asyncio
import logging
import re
from enum import Enum

from app.services.model_loader import ModelLoader, ModelSlot

logger = logging.getLogger(__name__)

AUTO_ID = "auto"

_IMAGE_HINTS = (
    "image", "photo", "picture", "screenshot", "png", "jpg", "jpeg", "webp",
    "scan", "ocr", "upscale", "enhance", "segment", "detect", "yolo", "u2net",
    "realesrgan", "vision", "describe this image", "what is in",
)

_CODING_HINTS = (
    "code", "function", "class", "bug", "debug", "refactor", "implement",
    "typescript", "python", "kotlin", "javascript", "compile", "syntax",
    "api", "endpoint", "sql", "regex", "```",
)

_TRANSLATE_HINTS = (
    "translate", "translation", "in english", "in french", "in spanish",
    "nllb", "localize",
)


class IdeTaskType(str, Enum):
    IMAGE = "image"
    CODING = "coding"
    TRANSLATION = "translation"
    GENERAL = "general"


def classify_ide_task(text: str, file_context: list | None = None) -> IdeTaskType:
    lower = (text or "").lower()
    ctx = " ".join(getattr(f, "path", "") or "" for f in (file_context or [])).lower()
    combined = f"{lower} {ctx}"

    if any(h in combined for h in _IMAGE_HINTS) or re.search(r"\.(png|jpe?g|gif|webp|bmp)\b", combined):
        return IdeTaskType.IMAGE
    if any(h in combined for h in _TRANSLATE_HINTS):
        return IdeTaskType.TRANSLATION
    if any(h in combined for h in _CODING_HINTS) or "```" in text:
        return IdeTaskType.CODING
    return IdeTaskType.GENERAL


def _ollama_ids_for_slots(loader: ModelLoader, slots: list[ModelSlot]) -> list[str]:
    from app.services.inference_engine import (
        _DEEPSEEK_CODER_OLLAMA,
        _QWEN_CODER_14B_OLLAMA,
        _QWEN_OLLAMA,
    )

    model_map = {
        ModelSlot.QWEN_7B: _QWEN_OLLAMA,
        ModelSlot.QWEN_14B: "qwen2.5:14b-instruct-q4_K_M",
        ModelSlot.MISTRAL_7B: "mistral:7b-instruct-q4_K_M",
        ModelSlot.LLAMA_8B: "llama3:8b-instruct-q4_K_M",
        ModelSlot.DEEPSEEK_CODER: _DEEPSEEK_CODER_OLLAMA,
        ModelSlot.QWEN_CODER_14B: _QWEN_CODER_14B_OLLAMA,
        ModelSlot.NLLB: "nllb-600m",
    }
    available = set(loader.ollama_models or [])
    out: list[str] = []
    for slot in slots:
        preferred = model_map.get(slot, _QWEN_OLLAMA)
        if preferred in available:
            out.append(preferred)
        else:
            base = preferred.split(":")[0]
            for name in available:
                if name.startswith(base):
                    out.append(name)
                    break
    return list(dict.fromkeys(out))


def parallel_plan(task: IdeTaskType, loader: ModelLoader) -> list[tuple[str, ModelSlot]]:
    """Return (ollama_id, slot) pairs to run in parallel for Auto mode."""
    scan = list(loader.scan_models or [])
    if task == IdeTaskType.IMAGE:
        slots = [ModelSlot.QWEN_7B, ModelSlot.MISTRAL_7B]
        llms = _ollama_ids_for_slots(loader, slots)
        plan: list[tuple[str, ModelSlot]] = [(m, slots[i] if i < len(slots) else ModelSlot.QWEN_7B) for i, m in enumerate(llms)]
        for s in scan[:2]:
            plan.append((f"scan:{s}", ModelSlot.MISTRAL_7B))
        return plan or [( "qwen2.5:7b-instruct-q4_K_M", ModelSlot.QWEN_7B)]

    if task == IdeTaskType.CODING:
        slots = [ModelSlot.DEEPSEEK_CODER, ModelSlot.QWEN_CODER_14B]
        llms = _ollama_ids_for_slots(loader, slots)
        if llms:
            return [(m, slots[i]) for i, m in enumerate(llms)]
        fallback = _ollama_ids_for_slots(loader, [ModelSlot.QWEN_7B])
        return [(fallback[0], ModelSlot.QWEN_7B)] if fallback else [("qwen2.5:7b-instruct-q4_K_M", ModelSlot.QWEN_7B)]

    if task == IdeTaskType.TRANSLATION:
        slots = [ModelSlot.NLLB, ModelSlot.QWEN_7B]
        llms = _ollama_ids_for_slots(loader, slots)
        if not llms:
            llms = _ollama_ids_for_slots(loader, [ModelSlot.QWEN_7B])
        return [(m, slots[min(i, len(slots) - 1)]) for i, m in enumerate(llms)]

    slots = [ModelSlot.QWEN_7B, ModelSlot.MISTRAL_7B]
    llms = _ollama_ids_for_slots(loader, slots)
    return [(m, slots[i]) for i, m in enumerate(llms)] or [("qwen2.5:7b-instruct-q4_K_M", ModelSlot.QWEN_7B)]


def build_synthesis_prompt(task: IdeTaskType, user_text: str, results: dict[str, str]) -> str:
    blocks = "\n\n".join(f"### {name}\n{body.strip()}" for name, body in results.items() if body.strip())
    return (
        f"You are Cheradip IDE assistant. Task type: {task.value}.\n"
        "Multiple specialist models answered in parallel. Synthesize ONE clear, accurate reply.\n"
        "Prefer the best code from coding models; mention scan/vision tools when relevant.\n"
        "Use ``` fences for code. Do not list model names unless helpful.\n\n"
        f"User request:\n{user_text.strip()}\n\n"
        f"Parallel model outputs:\n{blocks}\n\n"
        "Final answer:"
    )


async def run_parallel_llm(
    inference,
    plan: list[tuple[str, ModelSlot]],
    prompt: str,
    max_tokens: int,
) -> dict[str, str]:
    async def one(name: str, slot: ModelSlot) -> tuple[str, str]:
        if name.startswith("scan:"):
            scan_id = name.split(":", 1)[1]
            return name, (
                f"[Vision pipeline `{scan_id}` is available on Home AI for scan/enhance tasks. "
                f"Use POST /scan-analyze or /scan-enhance for image files.]"
            )
        try:
            text, _ = await inference.run_llm(slot, prompt, max_tokens=max_tokens)
            return name, text
        except Exception as e:
            logger.warning("Parallel model %s failed: %s", name, e)
            return name, ""

    pairs = await asyncio.gather(*(one(n, s) for n, s in plan))
    return {k: v for k, v in pairs if v}
