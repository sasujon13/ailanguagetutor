#!/usr/bin/env python3
"""Download ONNX weights for scan enhancement into models/scan/."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCAN_DIR = ROOT / "models" / "scan"
MANIFEST = SCAN_DIR / "manifest.json"


def _download_url(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    print(f"  GET {url}")
    try:
        urllib.request.urlretrieve(url, tmp)  # noqa: S310
    except urllib.error.URLError as e:
        tmp.unlink(missing_ok=True)
        raise RuntimeError(f"Download failed: {e}") from e
    if tmp.stat().st_size < 1024:
        tmp.unlink(missing_ok=True)
        raise RuntimeError("Downloaded file too small")
    tmp.replace(dest)
    print(f"  -> {dest} ({dest.stat().st_size // 1024} KB)")


def _download_hf(repo_id: str, filename: str, dest: Path, subfolder: str | None = None) -> None:
    try:
        from huggingface_hub import hf_hub_download
    except ImportError as e:
        raise RuntimeError("pip install huggingface_hub") from e

    print(f"  HF {repo_id}/{filename}")
    path = hf_hub_download(
        repo_id=repo_id,
        filename=filename,
        subfolder=subfolder,
    )
    downloaded = Path(path)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(downloaded.read_bytes())
    print(f"  -> {dest} ({dest.stat().st_size // 1024} KB)")


def _run_export(script: str, args: list[str], dest: Path) -> None:
    import subprocess

    script_path = ROOT / script
    if not script_path.is_file():
        raise RuntimeError(f"Missing export script: {script_path}")
    cmd = [sys.executable, str(script_path), *args, "--out", str(dest)]
    print(f"  EXPORT {' '.join(cmd)}")
    subprocess.run(cmd, check=True, cwd=str(ROOT))
    if not dest.is_file():
        raise RuntimeError("Export did not produce output file")
    print(f"  -> {dest} ({dest.stat().st_size // 1024} KB)")


def _resolve_source(spec: dict) -> tuple[str, dict]:
    source = spec.get("source", {})
    if source.get("type") == "manual":
        return "skip", source
    fallback = spec.get("fallback")
    return "primary", source if source.get("type") != "manual" else (fallback or source)


def download_model(spec: dict, force: bool) -> bool:
    model_id = spec["id"]
    dest = SCAN_DIR / spec["file"]
    if dest.is_file() and not force:
        print(f"[{model_id}] already present: {dest.name}")
        return True

    source = spec.get("source", {})
    if source.get("type") == "manual":
        print(f"[{model_id}] manual — see models/scan/README.md")
        return bool(spec.get("optional"))

    try:
        if source["type"] == "url":
            _download_url(source["url"], dest)
            return True
        if source["type"] == "huggingface":
            _download_hf(source["repo_id"], source["filename"], dest, source.get("subfolder"))
            return True
        if source["type"] == "export":
            _run_export(source["script"], source.get("args", []), dest)
            return True
    except Exception as e:
        print(f"[{model_id}] primary failed: {e}")
        fallback = spec.get("fallback")
        if not fallback:
            return bool(spec.get("optional"))
        try:
            if fallback["type"] == "url":
                _download_url(fallback["url"], dest)
            elif fallback["type"] == "huggingface":
                _download_hf(
                    fallback["repo_id"],
                    fallback["filename"],
                    dest,
                    fallback.get("subfolder"),
                )
            else:
                return bool(spec.get("optional"))
            return True
        except Exception as e2:
            print(f"[{model_id}] fallback failed: {e2}")
            return bool(spec.get("optional"))

    return False


def main() -> int:
    parser = argparse.ArgumentParser(description="Download scan ONNX models")
    parser.add_argument("--force", action="store_true", help="Re-download even if present")
    parser.add_argument("--only", nargs="*", help="Model ids (default: all non-manual)")
    args = parser.parse_args()

    if not MANIFEST.is_file():
        print(f"Missing manifest: {MANIFEST}", file=sys.stderr)
        return 1

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    specs = manifest.get("models", [])
    if args.only:
        specs = [s for s in specs if s["id"] in args.only]

    SCAN_DIR.mkdir(parents=True, exist_ok=True)
    print(f"Target: {SCAN_DIR}\n")

    ok = 0
    for spec in specs:
        print(f"[{spec['id']}]")
        if download_model(spec, force=args.force):
            ok += 1
        print()

    # Alias u2netp -> u2net if user downloaded lite variant only
    u2net = SCAN_DIR / "u2net.onnx"
    u2netp = SCAN_DIR / "u2netp.onnx"
    if not u2net.is_file() and u2netp.is_file():
        u2net.write_bytes(u2netp.read_bytes())
        print(f"Linked {u2netp.name} -> {u2net.name}\n")

    print(f"Done: {ok}/{len(specs)} models ready.")
    return 0 if ok > 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
