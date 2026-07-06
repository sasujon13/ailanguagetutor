"""Validate high enhancement levels on wrinkled / shadowed synthetic scans."""

from __future__ import annotations

import pytest

cv2 = pytest.importorskip("cv2")
import numpy as np

from app.services.scan_analyzer import analyze_image
from app.services.scan_classifier import recommend
from app.services.scan_enhancer import ScanEnhancer
from app.services.scan_validation import readability_score, should_rollback
from tests.scan_fixtures import make_folded_corner_scan, make_shadowed_scan, make_wrinkled_scan


def _encode_jpeg(image: np.ndarray) -> bytes:
    ok, buf = cv2.imencode(".jpg", image, [int(cv2.IMWRITE_JPEG_QUALITY), 92])
    assert ok
    return buf.tobytes()


@pytest.fixture(scope="module")
def enhancer() -> ScanEnhancer:
    e = ScanEnhancer()
    e.ensure_models()
    return e


@pytest.mark.parametrize(
    "fixture_fn",
    [make_shadowed_scan, make_wrinkled_scan, make_folded_corner_scan],
    ids=["shadowed", "wrinkled", "folded_corner"],
)
def test_levels_4_to_7_improve_or_preserve_readability(enhancer: ScanEnhancer, fixture_fn):
    image = fixture_fn()
    before_score = readability_score(image)
    raw = _encode_jpeg(image)

    for level in (4, 5, 6, 7):
        out_bytes, models, rec = enhancer.enhance_jpeg(raw, level, premium=True)
        out = cv2.imdecode(np.frombuffer(out_bytes, np.uint8), cv2.IMREAD_COLOR)
        assert out is not None
        after_score = readability_score(out)
        assert out_bytes != raw or level == 0
        assert not should_rollback(image, out), f"level {level} rolled back readability"
        assert after_score >= before_score * 0.90, (
            f"level {level} readability dropped too much: {before_score:.3f} -> {after_score:.3f}"
        )
        assert "opencv" in models
        assert rec is not None


def test_shadowed_scan_metrics_trigger_moderate_recommendation():
    image = make_shadowed_scan()
    metrics = analyze_image(image)
    rec = recommend(metrics, premium=True)
    assert 3 <= rec.recommended_level <= 7
    assert metrics.shadow_severity > 0.15


def test_wrinkled_scan_has_elevated_wrinkle_score():
    image = make_wrinkled_scan()
    metrics = analyze_image(image)
    assert metrics.wrinkle_score > 0.05
    rec = recommend(metrics, premium=True)
    assert rec.dewarp_cap >= 0.3


def test_level_7_uses_more_models_when_onnx_present(enhancer: ScanEnhancer):
    image = make_wrinkled_scan()
    raw = _encode_jpeg(image)
    _, models, _ = enhancer.enhance_jpeg(raw, 7, premium=True)
    # OpenCV always; ONNX names appear when weights are downloaded
    assert "opencv" in models
    if enhancer._models is not None:
        available = enhancer._models.available_for_level(7)
        for name in available:
            assert name in models
