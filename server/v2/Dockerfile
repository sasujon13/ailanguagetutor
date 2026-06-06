# Cheradip Home AI Server — Intel Arc optimized image
# Build: docker build -t cheradip-home-ai server/v2
# Run:   docker run -p 8787:8787 -v ./models:/app/models cheradip-home-ai

FROM python:3.11-slim-bookworm

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml README.md ./
COPY app ./app

RUN pip install --no-cache-dir -e .

ENV HOST=0.0.0.0
ENV PORT=8787
ENV MODELS_DIR=/app/models
ENV INFERENCE_BACKEND=stub
# Intel Arc (set on host with OpenVINO):
# ENV OPENVINO_DEVICE=GPU
# ENV ONEAPI_DEVICE_SELECTOR=level_zero:gpu

EXPOSE 8787

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8787/health')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8787"]
