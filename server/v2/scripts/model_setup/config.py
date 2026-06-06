"""Paths and manifest — single source for setup scripts."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODELS_DIR = ROOT / "models"
MANIFEST_PATH = MODELS_DIR / "manifest.json"
HF_CACHE_DIR = MODELS_DIR / "hf"


@dataclass
class ModelSpec:
    id: str
    path: str
    huggingface: str | None
    model_type: str  # causal | seq2seq | whisper
    weight_format: str  # int8 | int4
    optional: bool = False

    @property
    def hf_dir(self) -> Path:
        parts = self.path.split("/")
        name = parts[-1].replace("-int8", "").replace("-int4", "")
        parent = "/".join(parts[:-1]) if len(parts) > 1 else ""
        base = HF_CACHE_DIR / parent if parent else HF_CACHE_DIR
        return base / name / "source"

    @property
    def ov_dir(self) -> Path:
        return MODELS_DIR / self.path


def load_specs(include_optional: bool = True) -> list[ModelSpec]:
    with MANIFEST_PATH.open(encoding="utf-8") as f:
        manifest = json.load(f)

    specs: list[ModelSpec] = []
    for m in manifest.get("models", []):
        hf = m.get("huggingface")
        if not hf:
            continue
        fmt = m.get("format", "openvino-int8")
        weight = "int4" if "int4" in fmt else "int8"
        optional = m.get("id") in ("llama3-8b-int4", "qwen2.5-14b-int4")
        if optional and not include_optional:
            continue
        if "nllb" in m["id"]:
            mtype = "seq2seq"
        elif "whisper" in m["id"]:
            mtype = "whisper"
        else:
            mtype = "causal"
        specs.append(
            ModelSpec(
                id=m["id"],
                path=m["path"],
                huggingface=hf,
                model_type=mtype,
                weight_format=weight,
                optional=optional,
            )
        )
    return specs


def ensure_dirs() -> None:
    for sub in ("llm", "translation", "stt", "tts", "hf"):
        (MODELS_DIR / sub).mkdir(parents=True, exist_ok=True)
