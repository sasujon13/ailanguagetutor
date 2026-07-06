"""Tests for scan enhancement pipeline."""

import pytest

cv2 = pytest.importorskip("cv2")
import numpy as np

from app.services.scan_enhancer import LEVEL_PROFILES, ScanEnhancer


def _tiny_jpeg() -> bytes:
    img = np.full((120, 80, 3), 240, dtype=np.uint8)
    cv2.rectangle(img, (10, 10), (70, 100), (30, 30, 30), 2)
    ok, buf = cv2.imencode(".jpg", img)
    assert ok
    return buf.tobytes()


def test_level_profiles_cover_0_to_7():
    assert set(LEVEL_PROFILES) == set(range(8))
    assert LEVEL_PROFILES[0].cleanup == 0.0
    assert LEVEL_PROFILES[7].cleanup == 1.0


def test_level_0_returns_original_bytes():
    enhancer = ScanEnhancer()
    raw = _tiny_jpeg()
    out, models, rec = enhancer.enhance_jpeg(raw, 0)
    assert out == raw
    assert models == ["original"]
    assert rec is None


def test_level_3_produces_different_jpeg():
    enhancer = ScanEnhancer()
    raw = _tiny_jpeg()
    out, models, rec = enhancer.enhance_jpeg(raw, 3)
    assert out != raw
    assert "opencv" in models
    assert len(out) > 100
    assert rec is not None
