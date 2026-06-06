#!/usr/bin/env python3
"""Download HuggingFace weights into models/hf/."""

from __future__ import annotations

import argparse
import os
import sys

from huggingface_hub import snapshot_download
from huggingface_hub.utils import GatedRepoError, HfHubHTTPError

from model_setup.config import ensure_dirs, load_specs


def main() -> int:
    parser = argparse.ArgumentParser(description="Download Cheradip home AI models")
    parser.add_argument("--include-optional", action="store_true", help="Include Llama 3 + Qwen 14B")
    args = parser.parse_args()

    ensure_dirs()
    specs = load_specs(include_optional=args.include_optional)
    failed: list[str] = []

    print(f"Downloading {len(specs)} model(s) from HuggingFace...\n")

    for spec in specs:
        dest = spec.hf_dir
        dest.mkdir(parents=True, exist_ok=True)
        print(f"  [{spec.id}] {spec.huggingface}")
        print(f"       -> {dest}")
        try:
            snapshot_download(
                repo_id=spec.huggingface,
                local_dir=str(dest),
                local_dir_use_symlinks=False,
                token=os.environ.get("HF_TOKEN"),
            )
            print(f"       OK\n")
        except GatedRepoError:
            print(f"       SKIP (gated — set HF_TOKEN and accept license on HuggingFace)\n")
            failed.append(spec.id)
        except HfHubHTTPError as e:
            print(f"       FAIL: {e}\n")
            failed.append(spec.id)

    if failed:
        print("Optional/skipped:", ", ".join(failed))
    print("Download step complete.")
    return 0 if len(failed) < len(specs) else 1


if __name__ == "__main__":
    sys.exit(main())
