#!/usr/bin/env python3
"""Run levels 0,4,5,6,7 on real scan images and write comparison + metrics report."""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

try:
    import cv2
    import numpy as np
except ImportError:
    print("Install scan extras: pip install -e '.[scan]'", file=sys.stderr)
    raise SystemExit(1)

from app.services.scan_analyzer import analyze_image
from app.services.scan_classifier import recommend
from app.services.scan_enhancer import ScanEnhancer
from app.services.scan_validation import readability_score, should_rollback

LEVELS = (0, 4, 5, 6, 7)


def _load_image(path: Path) -> tuple[np.ndarray, bytes]:
    data = path.read_bytes()
    image = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise ValueError(f"Could not decode image: {path}")
    return image, data


def _stack_row(images: list[np.ndarray], labels: list[str]) -> np.ndarray:
    h = max(img.shape[0] for img in images)
    padded = []
    for img, label in zip(images, labels, strict=True):
        scale = h / img.shape[0]
        w = int(img.shape[1] * scale)
        resized = cv2.resize(img, (w, h))
        band = np.full((40, w, 3), 30, dtype=np.uint8)
        cv2.putText(band, label, (8, 28), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (240, 240, 240), 2)
        padded.append(np.vstack([band, resized]))
    return np.hstack(padded)


def validate_image(enhancer: ScanEnhancer, path: Path, out_dir: Path) -> dict:
    original, raw = _load_image(path)
    metrics = analyze_image(original)
    rec = recommend(metrics, premium=True)
    before = readability_score(original)

    row_images = [original]
    row_labels = ["original"]
    level_results: list[dict] = []

    for level in LEVELS:
        out_bytes, models, _ = enhancer.enhance_jpeg(raw, level, premium=True)
        out = cv2.imdecode(np.frombuffer(out_bytes, np.uint8), cv2.IMREAD_COLOR)
        assert out is not None
        after = readability_score(out)
        rolled = should_rollback(original, out)
        level_results.append(
            {
                "level": level,
                "readability_before": round(before, 4),
                "readability_after": round(after, 4),
                "delta": round(after - before, 4),
                "rolled_back": rolled,
                "models": models,
            }
        )
        row_images.append(out)
        row_labels.append(f"L{level}")

    grid = _stack_row(row_images, row_labels)
    stem = path.stem
    grid_path = out_dir / f"{stem}_levels.jpg"
    cv2.imwrite(str(grid_path), grid, [int(cv2.IMWRITE_JPEG_QUALITY), 90])

    return {
        "source": str(path),
        "grid": str(grid_path),
        "metrics": {
            "blur_score": round(metrics.blur_score, 4),
            "shadow_severity": round(metrics.shadow_severity, 4),
            "wrinkle_score": round(metrics.wrinkle_score, 4),
            "contrast": round(metrics.contrast, 4),
        },
        "recommendation": {
            "label": rec.label,
            "mode": rec.recommended_mode,
            "level": rec.recommended_level,
            "document_class": rec.document_class.value,
        },
        "levels": level_results,
        "onnx_loaded": list(getattr(enhancer._models, "_loaded", {}).keys()) if enhancer._models else [],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate scan levels 4–7 on real images")
    parser.add_argument("images", nargs="+", type=Path, help="JPEG/PNG scan paths")
    parser.add_argument("--out", type=Path, default=Path("reports/scan_validation"))
    args = parser.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    enhancer = ScanEnhancer()
    enhancer.ensure_models()

    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "onnx_models": list(getattr(enhancer._models, "_loaded", {}).keys()) if enhancer._models else [],
        "images": [],
    }

    for path in args.images:
        if not path.is_file():
            print(f"Skip missing: {path}")
            continue
        print(f"Processing {path}...")
        entry = validate_image(enhancer, path, args.out)
        report["images"].append(entry)
        print(f"  -> {entry['grid']}")

    report_path = args.out / "report.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"\nReport: {report_path}")
    return 0 if report["images"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
