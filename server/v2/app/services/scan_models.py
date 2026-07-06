"""Optional ONNX models for AI Clean — loads when weights exist under models/scan/."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from app.config import settings
from app.services.scan_enhancer import LevelProfile

try:
    import cv2
    import numpy as np

    _CV = True
except ImportError:
    _CV = False
    cv2 = None  # type: ignore
    np = None  # type: ignore

_ORT_SESSION: dict[str, Any] = {}


class ScanModelRegistry:
    def __init__(self, models_dir: Path | None = None) -> None:
        self.models_dir = models_dir or settings.models_dir
        self._loaded: dict[str, str] = {}

    def load_optional(self) -> None:
        scan_dir = self.models_dir / "scan"
        if not scan_dir.is_dir():
            scan_dir.mkdir(parents=True, exist_ok=True)
            return
        for name in ("yolov8", "u2net", "dewarpnet", "realesrgan"):
            path = scan_dir / f"{name}.onnx"
            if path.is_file():
                self._loaded[name] = str(path)
                self._try_load_ort(name, path)

    def _try_load_ort(self, name: str, path: Path) -> None:
        try:
            import onnxruntime as ort  # type: ignore

            _ORT_SESSION[name] = ort.InferenceSession(
                str(path),
                providers=["CPUExecutionProvider"],
            )
        except Exception:
            pass

    def available_for_level(self, level: int) -> list[str]:
        names: list[str] = []
        if level >= 1 and "yolov8" in self._loaded:
            names.append("yolov8")
        if level >= 1 and "u2net" in self._loaded:
            names.append("u2net")
        if level >= 5 and "dewarpnet" in self._loaded:
            names.append("dewarpnet")
        if level >= 4 and "realesrgan" in self._loaded:
            names.append("realesrgan")
        return names

    def preprocess(self, image: Any, level: int, profile: LevelProfile) -> Any:
        if not _CV:
            return image
        if "u2net" in self._loaded and level >= 1:
            image = self._u2net_mask_blend(image)
        return image

    def postprocess(self, image: Any, level: int, profile: LevelProfile) -> Any:
        if not _CV:
            return image
        if "realesrgan" in self._loaded and level >= 4:
            image = self._realesrgan_upscale(image, scale=1.25 if level < 7 else 1.5)
        return image

    def _u2net_mask_blend(self, image: np.ndarray) -> np.ndarray:
        """Placeholder U-2-Net: use GrabCut document mask until ONNX wired."""
        h, w = image.shape[:2]
        mask = np.zeros((h, w), np.uint8)
        rect = (int(w * 0.05), int(h * 0.05), int(w * 0.9), int(h * 0.9))
        bgd = np.zeros((1, 65), np.float64)
        fgd = np.zeros((1, 65), np.float64)
        try:
            cv2.grabCut(image, mask, rect, bgd, fgd, 2, cv2.GC_INIT_WITH_RECT)
            fg = np.where((mask == 2) | (mask == 0), 0, 1).astype("uint8")
            bg = cv2.bitwise_and(image, image, mask=1 - fg)
            fg_img = cv2.bitwise_and(image, image, mask=fg)
            white = np.full_like(image, 255)
            bg_white = cv2.bitwise_and(white, white, mask=1 - fg)
            return cv2.add(fg_img, bg_white)
        except Exception:
            return image

    def _realesrgan_upscale(self, image: np.ndarray, scale: float) -> np.ndarray:
        h, w = image.shape[:2]
        nh, nw = int(h * scale), int(w * scale)
        up = cv2.resize(image, (nw, nh), interpolation=cv2.INTER_CUBIC)
        return cv2.resize(up, (w, h), interpolation=cv2.INTER_AREA)
