"""12-step scan enhancement pipeline per upgrade.md."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.services.scan_classifier import DocumentClass, ScanEnhanceRecommendation
from app.services.scan_enhancer import LEVEL_PROFILES, LevelProfile
from app.services.scan_opencv_ops import denoise, enhance_colors, perspective_correct, shadow_cleanup, sharpen
from app.services.scan_validation import machine_readable_safe, readability_score, should_rollback

try:
    import cv2
    import numpy as np

    _CV = True
except ImportError:
    _CV = False
    cv2 = None  # type: ignore
    np = None  # type: ignore


@dataclass(frozen=True)
class PipelineResult:
    image: Any
    models_used: list[str]
    rolled_back: bool


class ScanPipeline:
    def __init__(self, models: Any | None = None) -> None:
        self._models = models

    def run(
        self,
        image: np.ndarray,
        level: int,
        recommendation: ScanEnhanceRecommendation | None = None,
    ) -> PipelineResult:
        if not _CV:
            raise RuntimeError("OpenCV required")
        if level <= 0:
            return PipelineResult(image, ["original"], False)

        profile = LEVEL_PROFILES[level]
        doc_class = recommendation.document_class if recommendation else DocumentClass.MIXED
        dewarp_cap = recommendation.dewarp_cap if recommendation else 1.0
        dewarp = min(profile.dewarp, dewarp_cap)

        models = ["opencv"]
        original = image.copy()

        # 1–2 document detection / segmentation
        image = self._segment_document(image, level)
        if self._models is not None:
            image = self._models.preprocess(image, level, profile)
            models.extend(self._models.available_for_level(level))

        # 3–6 curve + dewarp
        image = perspective_correct(image, dewarp)
        if level >= 5 and dewarp > 0.4:
            if self._models is not None:
                image = self._models.dewarp(image, level, dewarp)
            image = self._book_center_unwarp(image, dewarp, doc_class)

        # 7 artifact (finger/skin heuristic at higher cleanup)
        if profile.cleanup >= 0.25:
            image = self._reduce_finger_artifacts(image, profile.cleanup * 0.5)

        # 8–10 shadow, denoise, enhance
        image = shadow_cleanup(image, profile.cleanup)
        image = denoise(image, profile.cleanup)
        image = enhance_colors(image, self._color_strength(profile, doc_class))
        image = sharpen(image, profile.cleanup, profile.enhancement)

        if self._models is not None:
            image = self._models.postprocess(image, level, profile)

        # 11–12 OCR validation + rollback
        rolled_back = False
        if should_rollback(original, image):
            image = original.copy()
            image = shadow_cleanup(image, profile.cleanup * 0.5)
            image = denoise(image, profile.cleanup * 0.4)
            rolled_back = True
            models.append("rollback-readability")

        if not machine_readable_safe(original, image):
            image = original.copy()
            image = shadow_cleanup(image, profile.cleanup * 0.35)
            rolled_back = True
            models.append("rollback-qr")

        # final optimization pass
        if level >= 6 and not rolled_back:
            image = sharpen(image, profile.cleanup * 0.5, profile.enhancement * 0.3)

        _ = readability_score(image)  # reserved for metrics/logging
        return PipelineResult(image, models, rolled_back)

    @staticmethod
    def _color_strength(profile: LevelProfile, doc_class: DocumentClass) -> float:
        if doc_class in (DocumentClass.VISUAL_HEAVY, DocumentClass.MIXED):
            return profile.enhancement
        if doc_class == DocumentClass.OFFICIAL_ID:
            return profile.enhancement * 0.3
        if doc_class == DocumentClass.TEXT_HEAVY:
            return profile.enhancement * 0.7
        return profile.enhancement

    @staticmethod
    def _segment_document(image: np.ndarray, level: int) -> np.ndarray:
        if level < 1:
            return image
        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        blur = cv2.GaussianBlur(gray, (5, 5), 0)
        edges = cv2.Canny(blur, 50, 150)
        contours, _ = cv2.findContours(edges, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
        if not contours:
            return image
        h, w = image.shape[:2]
        cnt = max(contours, key=cv2.contourArea)
        if cv2.contourArea(cnt) < (w * h) * 0.15:
            return image
        x, y, cw, ch = cv2.boundingRect(cnt)
        pad = int(min(w, h) * 0.01)
        x1 = max(0, x - pad)
        y1 = max(0, y - pad)
        x2 = min(w, x + cw + pad)
        y2 = min(h, y + ch + pad)
        return image[y1:y2, x1:x2]

    @staticmethod
    def _book_center_unwarp(image: np.ndarray, strength: float, doc_class: DocumentClass) -> np.ndarray:
        if doc_class not in (DocumentClass.MIXED, DocumentClass.DAMAGED, DocumentClass.TEXT_HEAVY):
            return image
        h, w = image.shape[:2]
        map_x = np.zeros((h, w), dtype=np.float32)
        map_y = np.zeros((h, w), dtype=np.float32)
        for y in range(h):
            ny = (y / max(h - 1, 1)) * 2 - 1
            bend = strength * 0.03 * (ny ** 2) * h
            for x in range(w):
                map_x[y, x] = x
                map_y[y, x] = y + bend
        return cv2.remap(image, map_x, map_y, cv2.INTER_LINEAR)

    @staticmethod
    def _reduce_finger_artifacts(image: np.ndarray, strength: float) -> np.ndarray:
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        lower = np.array([0, 30, 50])
        upper = np.array([25, 180, 255])
        mask = cv2.inRange(hsv, lower, upper)
        if mask.mean() < 2:
            return image
        mask = cv2.dilate(mask, np.ones((5, 5), np.uint8), iterations=2)
        inpaint_radius = max(1, int(3 * strength))
        return cv2.inpaint(image, mask, inpaint_radius, cv2.INPAINT_TELEA)
