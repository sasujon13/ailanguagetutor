"""Lazy model loading — auto-detect Ollama / OpenVINO when backend=stub."""

from __future__ import annotations

import logging
from enum import Enum
from pathlib import Path

import httpx

from app.config import Settings
from app.schemas import SubscriptionTier

from app.services.gpu_status import GpuStatus, probe_gpu

logger = logging.getLogger(__name__)


class ModelSlot(str, Enum):
    QWEN_7B = "qwen2.5-7b-int8"
    QWEN_14B = "qwen2.5-14b-int4"
    MISTRAL_7B = "mistral-7b-int4"
    LLAMA_8B = "llama3-8b-int4"
    DEEPSEEK_CODER = "deepseek-coder-v2"
    QWEN_CODER_14B = "qwen2.5-coder-14b"
    NLLB = "nllb-600m"
    WHISPER = "whisper-small"
    PIPER = "piper-en"


def _openvino_models_present(models_dir: Path) -> bool:
    if not models_dir.is_dir():
        return False
    return any(models_dir.rglob("*.xml"))


def _scan_onnx_models(models_dir: Path) -> list[str]:
    scan_dir = models_dir / "scan"
    if not scan_dir.is_dir():
        return []
    return sorted(p.stem for p in scan_dir.glob("*.onnx") if p.stat().st_size > 1024)


async def _ollama_reachable(base_url: str) -> bool:
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.get(f"{base_url.rstrip('/')}/api/tags")
            if resp.status_code != 200:
                return False
            models = resp.json().get("models", [])
            return len(models) > 0
    except Exception:
        return False


class ModelLoader:
    """Resident: NLLB + Mistral. Dynamic: one LLM at a time."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self.backend_name = settings.inference_backend
        self.gpu_available = False
        self.active_llm: str | None = None
        self.resident_models: list[str] = list(settings.resident_models)
        self.queue_depth = 0
        self._loaded: set[str] = set()
        self.ollama_models: list[str] = []
        self.models_on_disk: list[str] = []
        self.scan_models: list[str] = _scan_onnx_models(settings.models_dir)
        self.gpu_status: GpuStatus = GpuStatus()

    async def refresh_gpu_status(self) -> GpuStatus:
        self.gpu_status = await probe_gpu(
            self.backend_name,
            self.settings.ollama_base_url,
            self.settings.openvino_device,
        )
        self.gpu_available = self.gpu_status.gpu_available
        return self.gpu_status

    async def initialize(self) -> None:
        if self.backend_name == "stub":
            if await _ollama_reachable(self.settings.ollama_base_url):
                self.backend_name = "ollama"
                self.ollama_models = await self._list_ollama_models()
                logger.info("Auto-selected Ollama backend (%d models)", len(self.ollama_models))
            elif _openvino_models_present(self.settings.models_dir):
                self.backend_name = "openvino"
                self.models_on_disk = [p.parent.name for p in self.settings.models_dir.rglob("*.xml")]
                logger.info("Auto-selected OpenVINO backend")
            else:
                logger.warning("No Ollama/OpenVINO models found — inference stays in stub mode")
        elif self.backend_name == "ollama":
            if await _ollama_reachable(self.settings.ollama_base_url):
                self.ollama_models = await self._list_ollama_models()
                logger.info("Ollama backend ready (%d models)", len(self.ollama_models))
            else:
                logger.warning("INFERENCE_BACKEND=ollama but Ollama is unreachable — using stub")
                self.backend_name = "stub"

        await self.refresh_gpu_status()
        self.scan_models = _scan_onnx_models(self.settings.models_dir)

        if self.backend_name == "openvino" and not self.models_on_disk:
            self.models_on_disk = [p.parent.name for p in self.settings.models_dir.rglob("*.xml")]

        for name in self.resident_models:
            await self._load_resident(name)
        logger.info("ModelLoader ready backend=%s resident=%s", self.backend_name, self.resident_models)

    async def _list_ollama_models(self) -> list[str]:
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                resp = await client.get(f"{self.settings.ollama_base_url.rstrip('/')}/api/tags")
                resp.raise_for_status()
                return [m.get("name", "") for m in resp.json().get("models", []) if m.get("name")]
        except Exception:
            return []

    async def shutdown(self) -> None:
        self._loaded.clear()
        self.active_llm = None

    async def _load_resident(self, name: str) -> None:
        logger.info("Loading resident model: %s (%s)", name, self.backend_name)
        self._loaded.add(name)

    async def ensure_llm(self, slot: ModelSlot) -> None:
        if self.active_llm == slot.value:
            return
        if self.active_llm:
            logger.info("Unloading LLM: %s", self.active_llm)
            self._loaded.discard(self.active_llm)
        logger.info("Loading LLM: %s via %s", slot.value, self.backend_name)
        self.active_llm = slot.value
        self._loaded.add(slot.value)

    def slot_for_mode(
        self,
        mode: int,
        complexity: str = "MEDIUM",
        tier: str | None = None,
    ) -> ModelSlot:
        """Rough mode→slot hint. Qwen 14B is never chosen here — use model_selector + tier gate."""
        if mode == 4:
            return ModelSlot.MISTRAL_7B
        if mode == 2:
            return ModelSlot.NLLB
        if (
            mode == 5
            and complexity == "HIGH"
            and tier == SubscriptionTier.PLUS.value
        ):
            return ModelSlot.QWEN_14B
        return ModelSlot.QWEN_7B

    def status(self) -> dict:
        return {
            "backend": self.backend_name,
            "gpu_available": self.gpu_available,
            "gpu_in_use": self.gpu_status.gpu_in_use,
            "gpu_device": self.gpu_status.gpu_device,
            "gpu_library": self.gpu_status.gpu_library,
            "gpu_vram_total": self.gpu_status.gpu_vram_total,
            "gpu_vram_available": self.gpu_status.gpu_vram_available,
            "loaded_processors": self.gpu_status.loaded_processors,
            "active_llm": self.active_llm,
            "resident_models": self.resident_models,
            "loaded_models": sorted(self._loaded),
            "ollama_models": self.ollama_models,
            "models_on_disk": self.models_on_disk,
            "scan_models": self.scan_models,
        }
