"""AI Clean scan enhancement — pipeline + optional ONNX models."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.services.scan_analyzer import ScanAnalysisMetrics, analyze_image
from app.services.scan_classifier import ScanEnhanceRecommendation, recommend

try:
    import cv2
    import numpy as np

    _CV_AVAILABLE = True
except ImportError:
    _CV_AVAILABLE = False
    cv2 = None  # type: ignore
    np = None  # type: ignore


@dataclass(frozen=True)
class LevelProfile:
    cleanup: float
    dewarp: float
    enhancement: float


LEVEL_PROFILES: dict[int, LevelProfile] = {
    0: LevelProfile(0.0, 0.0, 0.0),
    1: LevelProfile(0.10, 0.10, 0.0),
    2: LevelProfile(0.25, 0.20, 0.10),
    3: LevelProfile(0.40, 0.35, 0.25),
    4: LevelProfile(0.45, 0.45, 0.50),
    5: LevelProfile(0.65, 0.60, 0.65),
    6: LevelProfile(0.85, 0.85, 0.80),
    7: LevelProfile(1.00, 1.00, 1.00),
}


class ScanEnhancer:
    """Level-based document enhancement with analyzer, classifier, and validation."""

    def __init__(self) -> None:
        self._models: Any | None = None
        self._pipeline: ScanPipeline | None = None

    def analyze(self, image_bytes: bytes) -> tuple[ScanAnalysisMetrics, ScanEnhanceRecommendation]:
        if not _CV_AVAILABLE:
            raise RuntimeError("OpenCV not installed. pip install -e '.[scan]'")
        arr = np.frombuffer(image_bytes, dtype=np.uint8)
        image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Could not decode image")
        metrics = analyze_image(image)
        rec = recommend(metrics, premium=True)
        return metrics, rec

    def enhance_jpeg(
        self,
        image_bytes: bytes,
        level: int,
        jpeg_quality: int = 92,
        premium: bool = True,
        doc_class_hint: str | None = None,
    ) -> tuple[bytes, list[str], ScanEnhanceRecommendation | None]:
        if not _CV_AVAILABLE:
            raise RuntimeError("OpenCV not installed. pip install -e '.[scan]'")
        level = int(max(0, min(7, level)))
        if level == 0:
            return image_bytes, ["original"], None

        arr = np.frombuffer(image_bytes, dtype=np.uint8)
        image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
        if image is None:
            raise ValueError("Could not decode image")

        metrics = analyze_image(image)
        rec = recommend(metrics, premium=premium)
        if doc_class_hint:
            from app.services.scan_classifier import DocumentClass

            try:
                dc = DocumentClass(doc_class_hint)
                rec = ScanEnhanceRecommendation(
                    document_class=dc,
                    recommended_mode=rec.recommended_mode,
                    recommended_level=rec.recommended_level,
                    dewarp_cap=rec.dewarp_cap,
                    label=rec.label,
                )
            except ValueError:
                pass

        self.ensure_models()
        from app.services.scan_pipeline import ScanPipeline

        pipeline = self._pipeline or ScanPipeline(self._models)
        result = pipeline.run(image, level, rec)

        ok, encoded = cv2.imencode(
            ".jpg",
            result.image,
            [int(cv2.IMWRITE_JPEG_QUALITY), jpeg_quality],
        )
        if not ok:
            raise RuntimeError("JPEG encode failed")
        models = result.models_used
        if result.rolled_back:
            models = list(models) + ["validation-rollback"]
        return encoded.tobytes(), models, rec

    def ensure_models(self) -> None:
        if self._models is not None:
            return
        try:
            from app.services.scan_models import ScanModelRegistry
            from app.services.scan_pipeline import ScanPipeline

            self._models = ScanModelRegistry()
            self._models.load_optional()
            self._pipeline = ScanPipeline(self._models)
        except Exception:
            from app.services.scan_pipeline import ScanPipeline

            self._models = None
            self._pipeline = ScanPipeline(None)


_enhancer: ScanEnhancer | None = None


def get_scan_enhancer() -> ScanEnhancer:
    global _enhancer
    if _enhancer is None:
        _enhancer = ScanEnhancer()
        _enhancer.ensure_models()
    return _enhancer
