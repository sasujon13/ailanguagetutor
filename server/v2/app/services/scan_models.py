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

from app.services.scan_onnx import (  # noqa: E402
    OrtRunner,
    dewarpnet_unwarp,
    realesrgan_enhance,
    u2net_white_background,
    yolov8_document_box,
)


class ScanModelRegistry:
    def __init__(self, models_dir: Path | None = None) -> None:
        self.models_dir = models_dir or settings.models_dir
        self._loaded: dict[str, str] = {}
        self._runners: dict[str, OrtRunner] = {}

    def load_optional(self) -> None:
        scan_dir = self.models_dir / "scan"
        scan_dir.mkdir(parents=True, exist_ok=True)
        aliases = {
            "u2net": ("u2net.onnx", "u2netp.onnx"),
            "realesrgan": ("realesrgan.onnx",),
            "dewarpnet": ("dewarpnet.onnx",),
            "yolov8": ("yolov8.onnx", "yolov8n.onnx"),
        }
        for name, filenames in aliases.items():
            path = self._resolve_model(scan_dir, filenames)
            if path is None:
                continue
            self._loaded[name] = str(path)
            self._try_load_ort(name, path)

    @staticmethod
    def _resolve_model(scan_dir: Path, filenames: tuple[str, ...]) -> Path | None:
        for filename in filenames:
            path = scan_dir / filename
            if path.is_file() and path.stat().st_size > 1024:
                return path
        return None

    def _try_load_ort(self, name: str, path: Path) -> None:
        try:
            self._runners[name] = OrtRunner(path)
        except Exception:
            self._loaded.pop(name, None)

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
        if "yolov8" in self._runners and level >= 1:
            image = self._yolov8_crop(image)
        if "u2net" in self._runners and level >= 1:
            image = self._u2net_mask_blend(image)
        return image

    def dewarp(self, image: Any, level: int, strength: float) -> Any:
        """Production uses OpenCV dewarp in scan_pipeline; DewarpNet ONNX is not deployed."""
        if not _CV or level < 5 or strength <= 0.05:
            return image
        if "dewarpnet" in self._runners:
            try:
                return dewarpnet_unwarp(image, self._runners["dewarpnet"], strength=strength)
            except Exception:
                pass
        return image

    def postprocess(self, image: Any, level: int, profile: LevelProfile) -> Any:
        if not _CV:
            return image
        if "realesrgan" in self._runners and level >= 4:
            scale = 1.25 if level < 7 else 1.5
            try:
                return realesrgan_enhance(image, self._runners["realesrgan"], target_scale=scale)
            except Exception:
                return self._realesrgan_upscale_fallback(image, scale)
        return image

    def _yolov8_crop(self, image: np.ndarray) -> np.ndarray:
        runner = self._runners.get("yolov8")
        if runner is None:
            return image
        try:
            box = yolov8_document_box(image, runner)
            if box is None:
                return image
            x1, y1, x2, y2 = box
            return image[y1:y2, x1:x2]
        except Exception:
            return image

    def _u2net_mask_blend(self, image: np.ndarray) -> np.ndarray:
        runner = self._runners.get("u2net")
        if runner is not None:
            try:
                return u2net_white_background(image, runner)
            except Exception:
                pass
        return self._u2net_grabcut_fallback(image)

    def _u2net_grabcut_fallback(self, image: np.ndarray) -> np.ndarray:
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

    def _realesrgan_upscale_fallback(self, image: np.ndarray, scale: float) -> np.ndarray:
        h, w = image.shape[:2]
        nh, nw = int(h * scale), int(w * scale)
        up = cv2.resize(image, (nw, nh), interpolation=cv2.INTER_CUBIC)
        return cv2.resize(up, (w, h), interpolation=cv2.INTER_AREA)
