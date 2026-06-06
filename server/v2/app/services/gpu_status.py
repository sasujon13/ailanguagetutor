"""Detect GPU availability and active use for Ollama / OpenVINO backends."""

from __future__ import annotations

import logging
import os
import re
from dataclasses import dataclass, field
from pathlib import Path

import httpx

logger = logging.getLogger(__name__)

_INFERENCE_COMPUTE_RE = re.compile(
    r'msg="inference compute".*?library=(\w+).*?description="([^"]+)"'
    r'(?:.*?total="([^"]+)")?(?:.*?available="([^"]+)")?',
    re.DOTALL,
)


@dataclass
class GpuStatus:
    gpu_available: bool = False
    gpu_in_use: bool = False
    gpu_device: str | None = None
    gpu_library: str | None = None
    gpu_vram_total: str | None = None
    gpu_vram_available: str | None = None
    loaded_processors: list[str] = field(default_factory=list)
    source: str = "unknown"

    def as_dict(self) -> dict:
        return {
            "gpu_available": self.gpu_available,
            "gpu_in_use": self.gpu_in_use,
            "gpu_device": self.gpu_device,
            "gpu_library": self.gpu_library,
            "gpu_vram_total": self.gpu_vram_total,
            "gpu_vram_available": self.gpu_vram_available,
            "loaded_processors": self.loaded_processors,
            "gpu_source": self.source,
        }


def _ollama_log_path() -> Path | None:
    local = os.environ.get("LOCALAPPDATA")
    if not local:
        return None
    path = Path(local) / "Ollama" / "server.log"
    return path if path.is_file() else None


def _parse_ollama_server_log(path: Path) -> GpuStatus:
    status = GpuStatus(source="ollama-log")
    try:
        # Last ~256 KB is enough for startup GPU discovery lines.
        size = path.stat().st_size
        with path.open("rb") as f:
            f.seek(max(0, size - 262_144))
            tail = f.read().decode("utf-8", errors="ignore")
    except OSError as e:
        logger.debug("Could not read Ollama log %s: %s", path, e)
        return status

    matches = list(_INFERENCE_COMPUTE_RE.finditer(tail))
    if not matches:
        return status

    library, description, total, available = matches[-1].groups()
    status.gpu_available = library.lower() not in ("cpu", "none", "")
    status.gpu_device = description
    status.gpu_library = library
    status.gpu_vram_total = total
    status.gpu_vram_available = available
    return status


def _processor_uses_gpu(processor: str | None, size_vram: int | None) -> bool:
    if processor:
        upper = processor.upper()
        if "GPU" in upper and "100% CPU" not in upper:
            return True
        if "/" in processor and "GPU" in upper:
            return True
    return bool(size_vram and size_vram > 0)


async def probe_ollama_gpu(base_url: str) -> GpuStatus:
    """GPU hardware from Ollama server.log; active use from /api/ps."""
    status = GpuStatus(source="ollama")
    log_path = _ollama_log_path()
    if log_path:
        status = _parse_ollama_server_log(log_path)
        status.source = "ollama"

    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.get(f"{base_url.rstrip('/')}/api/ps")
            resp.raise_for_status()
            models = resp.json().get("models") or []
    except Exception as e:
        logger.debug("Ollama /api/ps unavailable: %s", e)
        return status

    processors: list[str] = []
    in_use = False
    for model in models:
        proc = model.get("processor")
        if isinstance(proc, str):
            processors.append(proc)
        elif proc is not None:
            processors.append(str(proc))
        size_vram = model.get("size_vram")
        if _processor_uses_gpu(processors[-1] if processors else None, size_vram):
            in_use = True

    status.loaded_processors = processors
    status.gpu_in_use = in_use
    return status


def probe_openvino_gpu(device_preference: str = "GPU") -> GpuStatus:
    status = GpuStatus(source="openvino")
    try:
        from openvino.runtime import Core
    except ImportError:
        return status

    try:
        core = Core()
        devices = core.available_devices
        gpu_devices = [d for d in devices if d != "CPU"]
        status.gpu_available = bool(gpu_devices)
        if gpu_devices:
            pick = device_preference if device_preference in devices else gpu_devices[0]
            status.gpu_device = pick
            status.gpu_library = "OpenVINO"
            status.gpu_in_use = True  # resident OpenVINO models target GPU when configured
    except Exception as e:
        logger.debug("OpenVINO GPU probe failed: %s", e)
    return status


async def probe_gpu(backend: str, ollama_base_url: str, openvino_device: str = "GPU") -> GpuStatus:
    if backend == "ollama":
        return await probe_ollama_gpu(ollama_base_url)
    if backend == "openvino":
        return probe_openvino_gpu(openvino_device)
    return GpuStatus(source=backend)
