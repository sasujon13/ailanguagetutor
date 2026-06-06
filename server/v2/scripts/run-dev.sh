#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
[[ -d .venv ]] || { echo "Run bash scripts/setup_models.sh first"; exit 1; }
source .venv/bin/activate
uvicorn app.main:app --host 0.0.0.0 --port 8787 --reload
