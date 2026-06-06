"""OpenVINO runtime backend (V2-2 — load INT8/INT4 models from models/)."""

import logging
from pathlib import Path

logger = logging.getLogger(__name__)


class OpenVinoBackend:
    def __init__(self, models_dir: Path, device: str = "GPU"):
        self.models_dir = models_dir
        self.device = device
        self._core = None
        self._compiled: dict[str, object] = {}

    def available(self) -> bool:
        try:
            from openvino.runtime import Core  # noqa: F401

            return True
        except ImportError:
            return False

    def load(self, model_name: str) -> None:
        path = self.models_dir / model_name
        xml = path / f"{model_name}.xml"
        if not xml.exists():
            logger.warning("OpenVINO model not found: %s — using stub", xml)
            return
        from openvino.runtime import Core

        if self._core is None:
            self._core = Core()
        logger.info("Compiling %s on %s", xml, self.device)
        model = self._core.read_model(str(xml))
        self._compiled[model_name] = self._core.compile_model(model, self.device)

    def infer(self, model_name: str, inputs: dict) -> dict:
        if model_name not in self._compiled:
            raise RuntimeError(f"Model not loaded: {model_name}")
        compiled = self._compiled[model_name]
        result = compiled(inputs)
        return dict(result)
