# Cheradip Home AI — one-click model setup (Windows / Intel Arc)
# Usage: cd server\v2; .\scripts\setup_models.ps1
# Optional: $env:INCLUDE_OPTIONAL=1 for Llama 3 + Qwen 14B
# Gated models: $env:HF_TOKEN = "hf_..."

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

Write-Host "======================================"
Write-Host " AI LANGUAGE TUTOR — MODEL SETUP"
Write-Host " Intel Arc / OpenVINO pipeline"
Write-Host "======================================"
Write-Host "Working directory: $Root`n"

$includeOpt = @()
if ($env:INCLUDE_OPTIONAL -eq "1") {
    $includeOpt = @("--include-optional")
    Write-Host "Including optional models (Llama 3, Qwen 14B)`n"
}

Write-Host "[1/5] Python environment..."
if (-not (Test-Path ".venv")) {
    python -m venv .venv
}
& .\.venv\Scripts\pip install -U pip -q
& .\.venv\Scripts\pip install -e ".[openvino]" -q
& .\.venv\Scripts\pip install huggingface_hub transformers -q

Write-Host "[2/5] Creating model directories..."
$env:PYTHONPATH = "$Root\scripts"
& .\.venv\Scripts\python -c "from model_setup.config import ensure_dirs; ensure_dirs()"

Write-Host "[3/5] Downloading HuggingFace models (this may take a while)..."
& .\.venv\Scripts\python scripts\model_setup\download_models.py @includeOpt

Write-Host "[4/5] Converting to OpenVINO (INT8/INT4 via Optimum)..."
& .\.venv\Scripts\python scripts\model_setup\convert_openvino.py @includeOpt

Write-Host "[5/5] Verifying installation..."
& .\.venv\Scripts\python scripts\model_setup\verify_models.py @includeOpt --require-openvino
if ($LASTEXITCODE -ne 0) {
    Write-Error "Verification failed — check logs above."
}

if (-not (Test-Path ".env")) {
    Copy-Item .env.example .env
    (Get-Content .env) -replace 'INFERENCE_BACKEND=stub', 'INFERENCE_BACKEND=openvino' | Set-Content .env
    Write-Host "Created .env with INFERENCE_BACKEND=openvino"
}

Write-Host @"

======================================
 ALL MODELS READY FOR INTEL ARC GPU
======================================

Start server:
  .\scripts\run-dev.ps1

  -> http://localhost:8787/health
"@
