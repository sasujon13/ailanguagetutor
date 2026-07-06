"""Low-level ONNX Runtime helpers for scan enhancement models."""

from __future__ import annotations

from pathlib import Path
from typing import Any

try:
    import cv2
    import numpy as np

    _CV = True
except ImportError:
    _CV = False
    cv2 = None  # type: ignore
    np = None  # type: ignore

_IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32) if _CV else None
_IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32) if _CV else None


class OrtRunner:
    """Thin wrapper around onnxruntime.InferenceSession."""

    def __init__(self, model_path: Path) -> None:
        import onnxruntime as ort  # type: ignore

        self.path = model_path
        self.session = ort.InferenceSession(
            str(model_path),
            providers=["CPUExecutionProvider"],
        )
        self.inputs = self.session.get_inputs()
        self.outputs = self.session.get_outputs()

    @property
    def input_name(self) -> str:
        return self.inputs[0].name

    def run(self, feed: dict[str, Any]) -> list[np.ndarray]:
        return self.session.run(None, feed)


def u2net_foreground_mask(image_bgr: np.ndarray, runner: OrtRunner) -> np.ndarray:
    """Salient-object mask in [0,1], same HxW as input."""
    h, w = image_bgr.shape[:2]
    rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)
    resized = cv2.resize(rgb, (320, 320), interpolation=cv2.INTER_AREA)
    blob = resized.astype(np.float32) / 255.0
    blob = (blob - _IMAGENET_MEAN) / _IMAGENET_STD
    blob = blob.transpose(2, 0, 1)[None, ...]

    outputs = runner.run({runner.input_name: blob})
    mask = _pick_u2net_mask(outputs)
    mask = cv2.resize(mask, (w, h), interpolation=cv2.INTER_LINEAR)
    return np.clip(mask, 0.0, 1.0)


def u2net_white_background(image_bgr: np.ndarray, runner: OrtRunner) -> np.ndarray:
    mask = u2net_foreground_mask(image_bgr, runner)
    alpha = (mask >= 0.45).astype(np.uint8) * 255
    fg = cv2.bitwise_and(image_bgr, image_bgr, mask=alpha)
    white = np.full_like(image_bgr, 255)
    bg = cv2.bitwise_and(white, white, mask=255 - alpha)
    return cv2.add(fg, bg)


def _pick_u2net_mask(outputs: list[np.ndarray]) -> np.ndarray:
    """U²-Net emits d0..d6; pick the highest-resolution saliency map."""
    candidates: list[np.ndarray] = []
    for out in outputs:
        arr = np.squeeze(out)
        if arr.ndim == 2:
            candidates.append(arr.astype(np.float32))
    if not candidates:
        raise ValueError("U2-Net ONNX produced no 2D mask")
    return max(candidates, key=lambda m: m.shape[0] * m.shape[1])


def realesrgan_enhance(
    image_bgr: np.ndarray,
    runner: OrtRunner,
    target_scale: float = 1.25,
    tile: int = 128,
) -> np.ndarray:
    """
    Run Real-ESRGAN x4 ONNX on overlapping tiles, then resize to target_scale.
    Works with 128x128 patch models (e.g. Qualcomm export).
    """
    h, w = image_bgr.shape[:2]
    if target_scale <= 1.01:
        return image_bgr

    rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    out_h = int(h * 4)
    out_w = int(w * 4)
    acc = np.zeros((out_h, out_w, 3), dtype=np.float32)
    weight = np.zeros((out_h, out_w, 1), dtype=np.float32)
    stride = max(tile // 2, 32)

    for y in range(0, h, stride):
        for x in range(0, w, stride):
            patch = rgb[y : y + tile, x : x + tile]
            ph, pw = patch.shape[:2]
            if ph < 16 or pw < 16:
                continue
            pad_h = tile - ph
            pad_w = tile - pw
            if pad_h or pad_w:
                patch = np.pad(patch, ((0, pad_h), (0, pad_w), (0, 0)), mode="reflect")

            inp = patch.transpose(2, 0, 1)[None, ...]
            try:
                sr = runner.run({runner.input_name: inp})[0]
            except Exception:
                continue
            sr = np.squeeze(sr)
            if sr.ndim == 3 and sr.shape[0] == 3:
                sr = sr.transpose(1, 2, 0)
            sr = sr[: ph * 4, : pw * 4]
            sr = np.clip(sr, 0.0, 1.0)

            oy, ox = y * 4, x * 4
            acc[oy : oy + sr.shape[0], ox : ox + sr.shape[1]] += sr
            weight[oy : oy + sr.shape[0], ox : ox + sr.shape[1]] += 1.0

    weight = np.maximum(weight, 1e-6)
    full = acc / weight
    full_bgr = cv2.cvtColor((full * 255.0).astype(np.uint8), cv2.COLOR_RGB2BGR)
    target_w = max(1, int(w * target_scale))
    target_h = max(1, int(h * target_scale))
    up = cv2.resize(full_bgr, (target_w, target_h), interpolation=cv2.INTER_AREA)
    return cv2.resize(up, (w, h), interpolation=cv2.INTER_AREA)


def yolov8_document_box(image_bgr: np.ndarray, runner: OrtRunner, conf: float = 0.25) -> tuple[int, int, int, int] | None:
    """Return x1,y1,x2,y2 of the largest high-confidence box, if any."""
    h, w = image_bgr.shape[:2]
    size = 640
    rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)
    scale = min(size / h, size / w)
    nh, nw = int(h * scale), int(w * scale)
    resized = cv2.resize(rgb, (nw, nh))
    canvas = np.full((size, size, 3), 114, dtype=np.uint8)
    canvas[:nh, :nw] = resized
    blob = canvas.astype(np.float32) / 255.0
    blob = blob.transpose(2, 0, 1)[None, ...]

    outputs = runner.run({runner.input_name: blob})
    if not outputs:
        return None
    preds = np.squeeze(outputs[0])
    if preds.ndim != 2 or preds.shape[1] < 6:
        return None

    boxes: list[tuple[float, tuple[int, int, int, int]]] = []
    for row in preds:
        score = float(row[4])
        if score < conf:
            continue
        cx, cy, bw, bh = row[0:4]
        x1 = int((cx - bw / 2) / scale)
        y1 = int((cy - bh / 2) / scale)
        x2 = int((cx + bw / 2) / scale)
        y2 = int((cy + bh / 2) / scale)
        x1, y1 = max(0, x1), max(0, y1)
        x2, y2 = min(w, x2), min(h, y2)
        if x2 - x1 < w * 0.12 or y2 - y1 < h * 0.12:
            continue
        boxes.append((score * bw * bh, (x1, y1, x2, y2)))

    if not boxes:
        return None
    return max(boxes, key=lambda item: item[0])[1]


def dewarpnet_unwarp(image_bgr: np.ndarray, runner: OrtRunner, strength: float = 1.0) -> np.ndarray:
    """
    Apply DewarpNet-style backward mapping when an ONNX grid model is present.
    Expects output flow/grid; falls back to caller on shape mismatch.
    """
    h, w = image_bgr.shape[:2]
    side = 256
    rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    resized = cv2.resize(rgb, (side, side), interpolation=cv2.INTER_AREA)
    inp = resized.transpose(2, 0, 1)[None, ...]
    outputs = runner.run({runner.input_name: inp})
    grid = np.squeeze(outputs[0])
    if grid.ndim != 3 or grid.shape[0] != 2:
        raise ValueError("Unexpected DewarpNet ONNX output shape")

    gx = cv2.resize(grid[0], (w, h), interpolation=cv2.INTER_LINEAR).astype(np.float32)
    gy = cv2.resize(grid[1], (w, h), interpolation=cv2.INTER_LINEAR).astype(np.float32)
    gx = gx * strength + np.linspace(0, w - 1, w, dtype=np.float32)[None, :]
    gy = gy * strength + np.linspace(0, h - 1, h, dtype=np.float32)[:, None]
    return cv2.remap(image_bgr, gx, gy, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
