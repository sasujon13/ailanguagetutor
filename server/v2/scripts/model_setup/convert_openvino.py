#!/usr/bin/env python3
"""Convert downloaded HF weights to OpenVINO INT8/INT4 via optimum-cli."""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys

from model_setup.config import load_specs


def run_export(source: str, dest: str, task: str, weight_format: str) -> None:
    dest_path = __import__("pathlib").Path(dest)
    if dest_path.exists():
        shutil.rmtree(dest_path)
    dest_path.mkdir(parents=True, exist_ok=True)

    cmd = [
        sys.executable,
        "-m",
        "optimum.cli",
        "export",
        "openvino",
        "--model",
        source,
        "--task",
        task,
        "--weight-format",
        weight_format,
        str(dest_path),
    ]
    print(f"  $ {' '.join(cmd)}")
    subprocess.run(cmd, check=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--include-optional", action="store_true")
    parser.add_argument("--only", type=str, default="")
    args = parser.parse_args()

    try:
        import optimum  # noqa: F401
    except ImportError:
        print("ERROR: pip install 'optimum[openvino]' openvino")
        return 1

    task_map = {
        "causal": ("text-generation-with-past", None),
        "seq2seq": ("text2text-generation-with-past", None),
        "whisper": ("automatic-speech-recognition", None),
    }

    specs = load_specs(include_optional=args.include_optional)
    if args.only:
        ids = {x.strip() for x in args.only.split(",")}
        specs = [s for s in specs if s.id in ids]

    print(f"Converting {len(specs)} model(s) to OpenVINO...\n")
    errors = 0

    for spec in specs:
        source = spec.hf_dir
        dest = spec.ov_dir
        if not source.exists() or not any(source.iterdir()):
            print(f"  [{spec.id}] SKIP — missing HF source: {source}\n")
            errors += 1
            continue
        task, _ = task_map[spec.model_type]
        wf = spec.weight_format
        print(f"[{spec.id}] -> {dest} ({wf})")
        try:
            run_export(str(source), str(dest), task, wf)
            print("  OK\n")
        except subprocess.CalledProcessError as e:
            print(f"  FAIL (exit {e.returncode})\n")
            errors += 1
        except Exception as e:
            print(f"  FAIL: {e}\n")
            errors += 1

    print("OpenVINO conversion complete." if errors == 0 else f"Finished with {errors} error(s).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
