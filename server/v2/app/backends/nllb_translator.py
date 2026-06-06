"""NLLB-600M translation via Transformers (CPU). Weights from setup_models / pull-nllb-model.ps1."""

from __future__ import annotations

import logging
from pathlib import Path

from app.backends.nllb_lang import to_flores

logger = logging.getLogger(__name__)

_instance: "NllbTranslator | None" = None


class NllbTranslator:
    def __init__(self, model_dir: Path):
        self.model_dir = model_dir
        self._tokenizer = None
        self._model = None

    @classmethod
    def find_model_dir(cls, models_dir: Path) -> Path | None:
        candidates = [
            models_dir / "hf" / "translation" / "nllb-600m" / "source",
            models_dir / "translation" / "nllb-600m" / "source",
            models_dir / "hf" / "translation" / "nllb-600m",
        ]
        for path in candidates:
            if (path / "config.json").exists():
                return path
        return None

    def available(self) -> bool:
        return (self.model_dir / "config.json").exists()

    def _ensure_loaded(self) -> None:
        if self._model is not None:
            return
        try:
            from transformers import AutoModelForSeq2SeqLM, AutoTokenizer
        except ImportError as e:
            raise RuntimeError(
                "pip install transformers torch sentencepiece (see scripts/pull-nllb-model.ps1)"
            ) from e

        path = self.model_dir
        if not (path / "config.json").exists():
            raise RuntimeError(f"NLLB weights not found: {path}")

        logger.info("Loading NLLB-600M from %s", path)
        self._tokenizer = AutoTokenizer.from_pretrained(str(path))
        self._model = AutoModelForSeq2SeqLM.from_pretrained(str(path))
        self._model.eval()

    def translate(self, text: str, source: str, target: str) -> str:
        src = to_flores(source)
        tgt = to_flores(target)
        if not src or not tgt:
            raise ValueError(f"Unsupported language pair: {source}->{target}")

        self._ensure_loaded()
        assert self._tokenizer is not None and self._model is not None

        self._tokenizer.src_lang = src
        tgt_id = self._tokenizer.convert_tokens_to_ids(tgt)
        inputs = self._tokenizer(text, return_tensors="pt", truncation=True, max_length=512)
        outputs = self._model.generate(**inputs, forced_bos_token_id=tgt_id, max_length=512)
        return self._tokenizer.batch_decode(outputs, skip_special_tokens=True)[0].strip()


def get_nllb_translator(models_dir: Path) -> NllbTranslator | None:
    global _instance
    model_path = NllbTranslator.find_model_dir(models_dir)
    if model_path is None:
        return None
    if _instance is None or _instance.model_dir != model_path:
        _instance = NllbTranslator(model_path)
    return _instance
