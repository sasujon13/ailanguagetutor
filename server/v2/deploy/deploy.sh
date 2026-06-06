#!/usr/bin/env bash
# Deploy Cheradip Home AI on your Intel Arc server
# Usage: bash deploy/deploy.sh [path-to-image.tar]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

IMAGE_NAME="${IMAGE_NAME:-cheradip-home-ai}"
CONTAINER_NAME="${CONTAINER_NAME:-cheradip-home-ai}"
PORT="${PORT:-8787}"
TAR_PATH="${1:-}"

echo "=== Cheradip Home AI Deploy ==="

if [[ -n "$TAR_PATH" && -f "$TAR_PATH" ]]; then
  echo "Loading Docker image from $TAR_PATH..."
  docker load < "$TAR_PATH"
else
  echo "Building Docker image locally..."
  docker build -t "$IMAGE_NAME" .
fi

docker stop "$CONTAINER_NAME" 2>/dev/null || true
docker rm "$CONTAINER_NAME" 2>/dev/null || true

if [[ ! -d models/llm ]] || [[ -z "$(ls -A models/llm 2>/dev/null)" ]]; then
  echo "Models not found — running setup_models.sh (first deploy)..."
  bash scripts/setup_models.sh
fi

echo "Starting container on port $PORT..."
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  -p "${PORT}:8787" \
  -v "$ROOT/models:/app/models:ro" \
  -e INFERENCE_BACKEND="${INFERENCE_BACKEND:-openvino}" \
  -e OPENVINO_DEVICE=GPU \
  -e ONEAPI_DEVICE_SELECTOR=level_zero:gpu \
  "$IMAGE_NAME"

echo "Verifying health..."
sleep 3
curl -sf "http://127.0.0.1:${PORT}/health" | python3 -m json.tool

echo "=== Deployment complete ==="
echo "API: http://127.0.0.1:${PORT}/docs"
