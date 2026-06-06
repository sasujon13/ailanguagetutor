#!/usr/bin/env python3
"""Download NLLB-600M weights for local translation."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "models" / "hf" / "translation" / "nllb-600m" / "source"


def main() -> int:
    try:
        from huggingface_hub import snapshot_download
    except ImportError:
        print("Missing huggingface_hub — run: pip install huggingface_hub transformers torch sentencepiece")
        return 1

    DEST.mkdir(parents=True, exist_ok=True)
    print(f"Downloading facebook/nllb-200-distilled-600M -> {DEST}")
    snapshot_download(
        repo_id="facebook/nllb-200-distilled-600M",
        local_dir=str(DEST),
        local_dir_use_symlinks=False,
    )
    print(f"NLLB download complete: {DEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
