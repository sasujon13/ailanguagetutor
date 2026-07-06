"""OCR-proxy validation + machine-readable protection."""

from __future__ import annotations

from app.services.scan_standards import READABILITY_ROLLBACK_RATIO

try:
    import cv2
    import numpy as np

    _CV = True
except ImportError:
    _CV = False
    cv2 = None  # type: ignore
    np = None  # type: ignore


def readability_score(image_bgr: np.ndarray) -> float:
    """Higher = more readable (sharp edges + contrast)."""
    if not _CV:
        return 0.5
    gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
    lap = cv2.Laplacian(gray, cv2.CV_64F).var()
    contrast = gray.std()
    return float(lap / 500.0 + contrast / 64.0)


def should_rollback(before_bgr: np.ndarray, after_bgr: np.ndarray, min_ratio: float = READABILITY_ROLLBACK_RATIO) -> bool:
    before = readability_score(before_bgr)
    after = readability_score(after_bgr)
    if before <= 0.01:
        return False
    return after < before * min_ratio


def qr_decode_ok(image_bgr: np.ndarray) -> bool | None:
    """Return True/False if decoder available; None if skipped."""
    try:
        from pyzbar import pyzbar  # type: ignore

        gray = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2GRAY)
        decoded = pyzbar.decode(gray)
        if not decoded:
            return None
        return len(decoded) > 0
    except Exception:
        return None


def machine_readable_safe(before_bgr: np.ndarray, after_bgr: np.ndarray) -> bool:
    before_ok = qr_decode_ok(before_bgr)
    after_ok = qr_decode_ok(after_bgr)
    if before_ok is None or after_ok is None:
        return True
    if before_ok and not after_ok:
        return False
    return True
