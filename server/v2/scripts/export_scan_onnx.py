#!/usr/bin/env python3
"""Export optional scan ONNX models (YOLOv8n, Real-ESRGAN) when PyTorch tooling is available."""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = Path(__file__).resolve().parent
if str(SCRIPTS) not in sys.path:
    sys.path.insert(0, str(SCRIPTS))
DEFAULT_OUT = ROOT / "models" / "scan"


def export_yolov8(out: Path) -> None:
    try:
        from ultralytics import YOLO
    except ImportError as e:
        raise RuntimeError("pip install ultralytics") from e

    model = YOLO("yolov8n.pt")
    exported = model.export(format="onnx", imgsz=640, opset=12, simplify=True)
    path = Path(str(exported))
    out.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, out)
    if path.resolve() != out.resolve():
        path.unlink(missing_ok=True)


def export_realesrgan(out: Path) -> None:
    try:
        import torch
        from rrdbnet_arch import RRDBNet
    except ImportError as e:
        raise RuntimeError("pip install torch — or place realesrgan.onnx manually") from e

    import urllib.error
    import urllib.request

    weights = ROOT / "models" / "scan" / "_cache" / "RealESRGAN_x4plus.pth"
    weights.parent.mkdir(parents=True, exist_ok=True)
    if not weights.is_file():
        urls = [
            "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.1.0/RealESRGAN_x4plus.pth",
            "https://huggingface.co/nateraw/real-esrgan/resolve/main/RealESRGAN_x4plus.pth",
        ]
        last_err: Exception | None = None
        for url in urls:
            print(f"Downloading {url}")
            try:
                urllib.request.urlretrieve(url, weights)  # noqa: S310
                if weights.stat().st_size > 1_000_000:
                    break
                weights.unlink(missing_ok=True)
            except (urllib.error.URLError, OSError) as e:
                last_err = e
                weights.unlink(missing_ok=True)
        else:
            raise RuntimeError(f"Could not download RealESRGAN weights: {last_err}") from last_err

    model = RRDBNet(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32, scale=4)
    loadnet = torch.load(weights, map_location=torch.device("cpu"), weights_only=True)
    key = "params_ema" if "params_ema" in loadnet else "params"
    model.load_state_dict(loadnet[key], strict=True)
    model.eval()

    dummy = torch.randn(1, 3, 128, 128)
    out.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(
        model,
        dummy,
        str(out),
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {2: "h", 3: "w"}, "output": {2: "h_out", 3: "w_out"}},
        opset_version=12,
        dynamo=False,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Export scan ONNX models")
    parser.add_argument("target", choices=["yolov8", "realesrgan", "all"])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()

    targets = ["yolov8", "realesrgan"] if args.target == "all" else [args.target]
    for name in targets:
        dest = args.out / ("yolov8.onnx" if name == "yolov8" else "realesrgan.onnx")
        print(f"[{name}] -> {dest}")
        if name == "yolov8":
            export_yolov8(dest)
        else:
            export_realesrgan(dest)
        print(f"OK {dest.stat().st_size // 1024} KB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
