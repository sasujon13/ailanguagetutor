"""IDE assistant orchestration for Cheradip VS Code extension."""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from typing import Any

from app.schemas import (
    IdeChatRequest,
    IdeChatResponse,
    IdeCompleteRequest,
    IdeCompleteResponse,
    IdeEditRequest,
    IdeEditResponse,
    IdeModelInfo,
    IdeModelsResponse,
)
from app.services.ide_prompt import build_chat_prompt, build_complete_prompt, build_edit_prompt
from app.services.inference_engine import InferenceEngine
from app.services.model_loader import ModelLoader, ModelSlot

logger = logging.getLogger(__name__)

_IDE_MODELS: list[tuple[str, str, str]] = [
    ("deepseek-coder-v2:latest", "DeepSeek Coder V2", "coding"),
    ("qwen2.5-coder:14b", "Qwen 2.5 Coder 14B", "coding"),
    ("qwen2.5:14b-instruct-q4_K_M", "Qwen 2.5 14B", "general"),
    ("qwen2.5:7b-instruct-q4_K_M", "Qwen 2.5 7B", "general"),
    ("mistral:7b-instruct-q4_K_M", "Mistral 7B", "general"),
    ("llama3:8b-instruct-q4_K_M", "Llama 3 8B", "general"),
]

_SLOT_BY_ID: dict[str, ModelSlot] = {
    "deepseek-coder-v2:latest": ModelSlot.DEEPSEEK_CODER,
    "deepseek-coder-v2": ModelSlot.DEEPSEEK_CODER,
    "qwen2.5-coder:14b": ModelSlot.QWEN_CODER_14B,
    "qwen2.5:14b-instruct-q4_K_M": ModelSlot.QWEN_14B,
    "qwen2.5:7b-instruct-q4_K_M": ModelSlot.QWEN_7B,
    "mistral:7b-instruct-q4_K_M": ModelSlot.MISTRAL_7B,
    "llama3:8b-instruct-q4_K_M": ModelSlot.LLAMA_8B,
}


def _resolve_slot(model_id: str | None, task: str = "chat") -> ModelSlot:
    if model_id and model_id in _SLOT_BY_ID:
        return _SLOT_BY_ID[model_id]
    if task in ("complete", "edit", "coding"):
        return ModelSlot.DEEPSEEK_CODER
    return ModelSlot.QWEN_7B


def _strip_fences(text: str) -> str:
    stripped = text.strip()
    if stripped.startswith("```"):
        lines = stripped.split("\n")
        if len(lines) >= 2 and lines[-1].strip() == "```":
            return "\n".join(lines[1:-1])
        if len(lines) >= 1:
            return "\n".join(lines[1:]).rstrip("`")
    return stripped


class IdeService:
    def __init__(self, loader: ModelLoader, inference: InferenceEngine):
        self.loader = loader
        self.inference = inference

    def list_models(self) -> IdeModelsResponse:
        available = set(self.loader.ollama_models or [])
        models: list[IdeModelInfo] = []
        for model_id, label, category in _IDE_MODELS:
            models.append(
                IdeModelInfo(
                    id=model_id,
                    label=label,
                    category=category,
                    available=model_id in available or any(model_id.split(":")[0] in m for m in available),
                )
            )
        for name in sorted(available):
            if not any(m.id == name for m in models):
                models.append(IdeModelInfo(id=name, label=name, category="ollama", available=True))
        default = "deepseek-coder-v2:latest" if any(m.id == "deepseek-coder-v2:latest" for m in models) else (
            models[0].id if models else "qwen2.5:7b-instruct-q4_K_M"
        )
        return IdeModelsResponse(models=models, default_model=default)

    async def chat(self, req: IdeChatRequest) -> IdeChatResponse:
        messages = [{"role": m.role, "content": m.content} for m in req.messages]
        prompt = build_chat_prompt(messages, [f.model_dump() for f in (req.file_context or [])])
        slot = _resolve_slot(req.model, "chat")
        text, used = await self.inference.run_llm(slot, prompt, max_tokens=req.max_tokens)
        return IdeChatResponse(content=text.strip(), model=used.value, cached=False)

    async def chat_stream(self, req: IdeChatRequest) -> AsyncIterator[str]:
        messages = [{"role": m.role, "content": m.content} for m in req.messages]
        prompt = build_chat_prompt(messages, [f.model_dump() for f in (req.file_context or [])])
        slot = _resolve_slot(req.model, "chat")
        async for chunk in self.inference.run_llm_stream(slot, prompt, max_tokens=req.max_tokens):
            yield chunk

    async def complete(self, req: IdeCompleteRequest) -> IdeCompleteResponse:
        prompt = build_complete_prompt(req.prefix, req.suffix, req.filepath, req.language)
        slot = _resolve_slot(req.model, "complete")
        text, used = await self.inference.run_llm(slot, prompt, max_tokens=req.max_tokens)
        return IdeCompleteResponse(completion=_strip_fences(text), model=used.value)

    async def edit(self, req: IdeEditRequest) -> IdeEditResponse:
        prompt = build_edit_prompt(req.instruction, req.code, req.filepath, req.language)
        slot = _resolve_slot(req.model, "edit")
        text, used = await self.inference.run_llm(slot, prompt, max_tokens=req.max_tokens)
        return IdeEditResponse(edited_code=_strip_fences(text), model=used.value)
