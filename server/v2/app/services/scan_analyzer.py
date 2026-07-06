"""Smart pre-process analyzer — image quality metrics before enhancement."""

from __future__ import annotations

from dataclasses import dataclass

try:
    import cv2
    import numpy as np

    _CV = True
except ImportError:
    _CV = False
    cv2 = None  # type: ignore
    np = None  # type: ignore


@dataclass(frozen=True)
class ScanAnalysisMetrics:
    blur_score: float  # higher = sharper
    brightness: float  # 0..1
    contrast: float  # 0..1
    shadow_severity: float  # 0..1
    color_richness: float  # 0..1
    edge_density: float  # proxy text density
    wrinkle_score: float  # 0..1
    damage_score: float  # 0..1
    has_machine_readable: bool
    aspect_ratio: float


def analyze_image(image_bgr: np.ndarray) -> ScanAnalysisMetrics:
    if not _CV:
        raise RuntimeError("OpenCV required for scan analysis")
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape[:2]
    lap = cv2.Laplacian(gray, cv2.CV_64F)
    blur_score = float(lap.var()) / 1000.0

    brightness = float(gray.mean()) / 255.0
    contrast = float(gray.std()) / 128.0

    # Shadow: bottom vs top luminance delta
    third = max(h // 3, 1)
    top_mean = float(gray[:third, :].mean())
    bot_mean = float(gray[-third:, :].mean())
    shadow_severity = max(0.0, min(1.0, abs(top_mean - bot_mean) / 80.0))

    hsv = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2HSV)
    sat = hsv[:, :, 1].astype(np.float32) / 255.0
    color_richness = float(sat.std() * 2.0)

    edges = cv2.Canny(gray, 50, 150)
    edge_density = float(edges.mean()) / 255.0

    # Wrinkle proxy: local variance spikes
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    wrinkle = cv2.absdiff(gray, blur)
    wrinkle_score = min(1.0, float(wrinkle.mean()) / 40.0)

    # Damage: dark stains + low local contrast
    dark_ratio = float((gray < 40).mean())
    damage_score = min(1.0, dark_ratio * 3.0 + wrinkle_score * 0.5)

    has_mr = _detect_qr_region(gray)

    aspect = w / max(h, 1)
    return ScanAnalysisMetrics(
        blur_score=blur_score,
        brightness=brightness,
        contrast=contrast,
        shadow_severity=shadow_severity,
        color_richness=color_richness,
        edge_density=edge_density,
        wrinkle_score=wrinkle_score,
        damage_score=damage_score,
        has_machine_readable=has_mr,
        aspect_ratio=aspect,
    )


def _detect_qr_region(gray: np.ndarray) -> bool:
    """Fast QR-like square finder pattern heuristic."""
    h, w = gray.shape
    small = cv2.resize(gray, (min(400, w), min(400, h)))
    _, thresh = cv2.threshold(small, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    contours, _ = cv2.findContours(thresh, cv2.RETR_TREE, cv2.CHAIN_APPROX_SIMPLE)
    squares = 0
    for cnt in contours:
        peri = cv2.arcLength(cnt, True)
        approx = cv2.approxPolyDP(cnt, 0.04 * peri, True)
        if len(approx) == 4 and cv2.contourArea(approx) > 100:
            squares += 1
    return squares >= 3
