#!/usr/bin/env python3
"""Verify HF downloads and OpenVINO exports."""

from __future__ import annotations

import argparse
import sys

from model_setup.config import MANIFEST_PATH, MODELS_DIR, load_specs


def has_openvino_export(path) -> bool:
    return path.exists() and any(path.glob("*.xml")) or (path / "openvino_model.xml").exists()


def has_hf_source(path) -> bool:
    return path.exists() and any(path.iterdir())


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--include-optional", action="store_true")
    parser.add_argument("--require-openvino", action="store_true")
    args = parser.parse_args()

    if not MANIFEST_PATH.exists():
        print(f"ERROR: missing {MANIFEST_PATH}")
        return 1

    specs = load_specs(include_optional=args.include_optional)
    print("Cheradip model verification\n" + "=" * 40)

    missing_hf: list[str] = []
    missing_ov: list[str] = []

    for spec in specs:
        hf_ok = has_hf_source(spec.hf_dir)
        ov_ok = has_openvino_export(spec.ov_dir)
        hf_tag = "OK" if hf_ok else "MISSING"
        ov_tag = "OK" if ov_ok else "MISSING"
        print(f"  {spec.id}")
        print(f"    HF source:  [{hf_tag}] {spec.hf_dir}")
        print(f"    OpenVINO:   [{ov_tag}] {spec.ov_dir}")
        if not hf_ok:
            missing_hf.append(spec.id)
        if args.require_openvino and not ov_ok:
            missing_ov.append(spec.id)

    print("=" * 40)
    if missing_hf:
        print("Missing HF downloads:", ", ".join(missing_hf))
    if missing_ov:
        print("Missing OpenVINO exports:", ", ".join(missing_ov))

    piper = MODELS_DIR / "tts" / "piper-en"
    if not piper.exists():
        print("Note: Piper TTS manual install -> models/tts/piper-en/ (see deploy docs)")

    if args.require_openvino and missing_ov:
        return 1
    if missing_hf and not args.require_openvino:
        print("\nPartial OK — run convert_openvino.py after downloads finish.")
        return 0 if len(missing_hf) <= sum(1 for s in specs if s.optional) else 1

    print("\nAll required models verified.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
