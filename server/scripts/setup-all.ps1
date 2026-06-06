# One-click setup: language packs + cloud API deps + home AI deps
# Usage: cd server; .\scripts\setup-all.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo = Split-Path -Parent $Root

Write-Host "=== AI Language Tutor — Server Setup ===" -ForegroundColor Cyan

# 1. Language packs
Write-Host "`n[1/4] Building language packs..." -ForegroundColor Yellow
Set-Location "$Repo\cloud-api"
python scripts\build_language_packs.py

# 2. Cloud API
Write-Host "`n[2/4] Cloud API (MySQL)..." -ForegroundColor Yellow
if (-not (Test-Path ".venv")) { python -m venv .venv }
& .\.venv\Scripts\pip install -U pip -q
& .\.venv\Scripts\pip install -e . -q
if (-not (Test-Path ".env")) {
    Copy-Item .env.example .env
    Write-Host "Created cloud-api/.env — set PUBLIC_BASE_URL and API keys"
}

# 3. Home AI v2
Write-Host "`n[3/4] Home AI v2 scaffold..." -ForegroundColor Yellow
Set-Location "$Repo\v2"
if (-not (Test-Path ".venv")) { python -m venv .venv }
& .\.venv\Scripts\pip install -U pip -q
& .\.venv\Scripts\pip install -e . -q
if (-not (Test-Path ".env")) {
    Copy-Item .env.example .env
}

# 4. Optional: full model download (long-running)
Write-Host "`n[4/4] AI models (optional)..." -ForegroundColor Yellow
Write-Host "Run for live inference: cd server\v2; .\scripts\setup_models.ps1"
Write-Host "Or install Ollama and pull qwen2.5:7b-instruct-q4_K_M for instant dev AI."

Write-Host "`n=== Start services ===" -ForegroundColor Green
Write-Host "Cloud API:  cd server\cloud-api; .\scripts\run-dev.ps1  -> :8790"
Write-Host "Home AI:    cd server\v2; .\scripts\run-dev.ps1        -> :8787"
Write-Host "Health:     GET /api/ailt/health  and  GET /health"

Set-Location $Repo
