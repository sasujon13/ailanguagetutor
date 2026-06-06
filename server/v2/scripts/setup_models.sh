#!/usr/bin/env bash
# Cheradip Home AI — one-click model download + OpenVINO setup (Linux / WSL)
# Usage: cd server/v2 && bash scripts/setup_models.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "======================================"
echo " AI LANGUAGE TUTOR — MODEL SETUP"
echo " Intel Arc / OpenVINO pipeline"
echo "======================================"
echo "Working directory: $ROOT"
echo ""

INCLUDE_OPT=""
if [[ "${INCLUDE_OPTIONAL:-0}" == "1" ]]; then
  INCLUDE_OPT="--include-optional"
  echo "Including optional models (Llama 3, Qwen 14B)"
fi

echo "[1/5] Python environment..."
if [[ ! -d .venv ]]; then
  python3 -m venv .venv
fi
# shellcheck disable=SC1091
source .venv/bin/activate
pip install -U pip -q
pip install -e ".[openvino]" -q
pip install huggingface_hub transformers -q

echo "[2/5] Creating model directories..."
export PYTHONPATH="$ROOT/scripts:${PYTHONPATH:-}"
python3 -c "from model_setup.config import ensure_dirs; ensure_dirs()"

echo "[3/5] Downloading HuggingFace models (this may take a while)..."
python3 scripts/model_setup/download_models.py $INCLUDE_OPT

echo "[4/5] Converting to OpenVINO (INT8/INT4 via Optimum)..."
python3 scripts/model_setup/convert_openvino.py $INCLUDE_OPT

echo "[5/5] Verifying installation..."
python3 scripts/model_setup/verify_models.py $INCLUDE_OPT --require-openvino || {
  echo "Verification reported issues — check logs above."
  exit 1
}

if [[ ! -f .env ]]; then
  cp .env.example .env
  sed -i 's/INFERENCE_BACKEND=stub/INFERENCE_BACKEND=openvino/' .env 2>/dev/null || \
    python3 -c "
from pathlib import Path
p = Path('.env')
t = p.read_text().replace('INFERENCE_BACKEND=stub', 'INFERENCE_BACKEND=openvino')
p.write_text(t)
"
  echo "Created .env with INFERENCE_BACKEND=openvino"
fi

echo ""
echo "======================================"
echo " ALL MODELS READY FOR INTEL ARC GPU"
echo "======================================"
echo ""
echo "Start server:"
echo "  source .venv/bin/activate"
echo "  uvicorn app.main:app --host 0.0.0.0 --port 8787"
echo ""
echo "Or: bash scripts/run-dev.sh"
