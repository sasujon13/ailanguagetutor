"""IDE assistant orchestration for Cheradip VS Code extension."""

from __future__ import annotations

import logging
from collections.abc import AsyncIterator

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
from app.services.ide_auto_router import (
    AUTO_ID,
    build_synthesis_prompt,
    classify_ide_task,
    parallel_plan,
    run_parallel_llm,
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
    "nllb-600m": ModelSlot.NLLB,
}


def _is_auto(model_id: str | None) -> bool:
    return not model_id or model_id == AUTO_ID


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
        models: list[IdeModelInfo] = [
            IdeModelInfo(id=AUTO_ID, label="Auto (smart routing)", category="auto", available=True),
        ]
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
        for scan_id in sorted(self.loader.scan_models or []):
            sid = f"scan:{scan_id}"
            models.append(
                IdeModelInfo(id=sid, label=f"Scan · {scan_id}", category="vision", available=True),
            )
        if ModelSlot.NLLB.value not in {m.id for m in models}:
            models.append(
                IdeModelInfo(id="nllb-600m", label="NLLB 600M (translation)", category="translation", available=True),
            )
        return IdeModelsResponse(models=models, default_model=AUTO_ID)

    def _last_user_text(self, req: IdeChatRequest) -> str:
        for m in reversed(req.messages):
            if m.role == "user":
                return m.content
        return req.messages[-1].content if req.messages else ""

    async def _chat_auto(self, req: IdeChatRequest) -> tuple[str, str]:
        user_text = self._last_user_text(req)
        task = classify_ide_task(user_text, req.file_context)
        messages = [{"role": m.role, "content": m.content} for m in req.messages]
        base_prompt = build_chat_prompt(messages, [f.model_dump() for f in (req.file_context or [])])
        plan = parallel_plan(task, self.loader)
        logger.info("IDE Auto task=%s parallel=%s", task.value, [p[0] for p in plan])
        results = await run_parallel_llm(self.inference, plan, base_prompt, req.max_tokens)
        if not results:
            text, used = await self.inference.run_llm(ModelSlot.QWEN_7B, base_prompt, max_tokens=req.max_tokens)
            return text.strip(), f"auto→{used.value}"
        if len(results) == 1:
            only = next(iter(results.values()))
            return only.strip(), f"auto→{next(iter(results))}"
        synth = build_synthesis_prompt(task, user_text, results)
        text, used = await self.inference.run_llm(ModelSlot.QWEN_7B, synth, max_tokens=req.max_tokens)
        models_used = "+".join(results.keys())
        return text.strip(), f"auto({task.value})→{models_used}+synth:{used.value}"

    async def _chat_single(self, req: IdeChatRequest) -> tuple[str, str]:
        messages = [{"role": m.role, "content": m.content} for m in req.messages]
        prompt = build_chat_prompt(messages, [f.model_dump() for f in (req.file_context or [])])
        model_id = req.model or ""
        if model_id.startswith("scan:"):
            scan_id = model_id.split(":", 1)[1]
            note = (
                f"Home AI scan model `{scan_id}` handles image pipelines via "
                f"`/scan-analyze` and `/scan-enhance`. Describe your image task and "
                f"I can guide you; upload images through the scanner flow."
            )
            slot = ModelSlot.QWEN_7B
            text, used = await self.inference.run_llm(
                slot, f"{prompt}\n\nNote: {note}\nAssistant:", max_tokens=req.max_tokens,
            )
            return text.strip(), f"{model_id}+{used.value}"
        if model_id in _SLOT_BY_ID:
            slot = _SLOT_BY_ID[model_id]
        else:
            slot = _resolve_slot(model_id, "chat")
        if model_id and model_id not in _SLOT_BY_ID and model_id in (self.loader.ollama_models or []):
            text = await self.inference.run_ollama_model(model_id, prompt, max_tokens=req.max_tokens)
            return text.strip(), model_id
        text, used = await self.inference.run_llm(slot, prompt, max_tokens=req.max_tokens)
        return text.strip(), used.value

    async def chat(self, req: IdeChatRequest) -> IdeChatResponse:
        if _is_auto(req.model):
            content, model = await self._chat_auto(req)
        else:
            content, model = await self._chat_single(req)
        return IdeChatResponse(content=content, model=model, cached=False)

    async def chat_stream(self, req: IdeChatRequest) -> AsyncIterator[str]:
        if _is_auto(req.model):
            content, _ = await self._chat_auto(req)
            chunk_size = 24
            for i in range(0, len(content), chunk_size):
                yield content[i : i + chunk_size]
            return
        messages = [{"role": m.role, "content": m.content} for m in req.messages]
        prompt = build_chat_prompt(messages, [f.model_dump() for f in (req.file_context or [])])
        model_id = req.model
        if model_id and model_id not in _SLOT_BY_ID and model_id in (self.loader.ollama_models or []):
            async for chunk in self.inference.run_ollama_stream(model_id, prompt, max_tokens=req.max_tokens):
                yield chunk
            return
        slot = _resolve_slot(model_id, "chat")
        async for chunk in self.inference.run_llm_stream(slot, prompt, max_tokens=req.max_tokens):
            yield chunk

    async def complete(self, req: IdeCompleteRequest) -> IdeCompleteResponse:
        prompt = build_complete_prompt(req.prefix, req.suffix, req.filepath, req.language)
        if _is_auto(req.model):
            slot = ModelSlot.DEEPSEEK_CODER
            text, used = await self.inference.run_llm(slot, prompt, max_tokens=req.max_tokens)
            return IdeCompleteResponse(completion=_strip_fences(text), model=f"auto→{used.value}")
        model_id = req.model
        if model_id and model_id in (self.loader.ollama_models or []) and model_id not in _SLOT_BY_ID:
            text = await self.inference.run_ollama_model(model_id, prompt, max_tokens=req.max_tokens)
            return IdeCompleteResponse(completion=_strip_fences(text), model=model_id)
        slot = _resolve_slot(model_id, "complete")
        text, used = await self.inference.run_llm(slot, prompt, max_tokens=req.max_tokens)
        return IdeCompleteResponse(completion=_strip_fences(text), model=used.value)

    async def edit(self, req: IdeEditRequest) -> IdeEditResponse:
        prompt = build_edit_prompt(req.instruction, req.code, req.filepath, req.language)
        if _is_auto(req.model):
            slot = ModelSlot.DEEPSEEK_CODER
            text, used = await self.inference.run_llm(slot, prompt, max_tokens=req.max_tokens)
            return IdeEditResponse(edited_code=_strip_fences(text), model=f"auto→{used.value}")
        model_id = req.model
        if model_id and model_id in (self.loader.ollama_models or []) and model_id not in _SLOT_BY_ID:
            text = await self.inference.run_ollama_model(model_id, prompt, max_tokens=req.max_tokens)
            return IdeEditResponse(edited_code=_strip_fences(text), model=model_id)
        slot = _resolve_slot(model_id, "edit")
        text, used = await self.inference.run_llm(slot, prompt, max_tokens=req.max_tokens)
        return IdeEditResponse(edited_code=_strip_fences(text), model=used.value)
