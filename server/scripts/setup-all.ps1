# Setup Home AI + optional language packs (App API is in bcheradip/ailt_api)
# Usage: cd server; .\scripts\setup-all.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Repo = Split-Path -Parent $Root

Write-Host "=== AI Language Tutor — Server Setup (Home AI) ===" -ForegroundColor Cyan
Write-Host "App API: D:\VSCode\cheradip\bcheradip\ailt_api (cheradip.com/ailt/api/)" -ForegroundColor DarkGray

# 1. Language packs -> bcheradip/ailt_api/packs
Write-Host "`n[1/3] Building language packs (sync to bcheradip)..." -ForegroundColor Yellow
Set-Location $Repo
.\gradlew.bat :tools:pack-builder:run --args="build-all --version 1.0.0" -q

# 2. Home AI v2
Write-Host "`n[2/3] Home AI v2..." -ForegroundColor Yellow
Set-Location "$Repo\v2"
if (-not (Test-Path ".venv")) { python -m venv .venv }
& .\.venv\Scripts\pip install -U pip -q
& .\.venv\Scripts\pip install -e . -q
if (-not (Test-Path ".env")) {
    Copy-Item .env.example .env
}

# 3. Optional models
Write-Host "`n[3/3] AI models (optional)..." -ForegroundColor Yellow
Write-Host "Run for live inference: cd server\v2; .\scripts\setup_models.ps1"
Write-Host "Or install Ollama and pull qwen2.5:7b-instruct-q4_K_M for instant dev AI."

Write-Host "`n=== Start services ===" -ForegroundColor Green
Write-Host "Home AI:    cd server\v2; .\scripts\run-dev.ps1  -> :8787  (ai.cheradip.com)"
Write-Host "App API:    see D:\VSCode\cheradip\bcheradip\ailt_api\README.md"
Write-Host "Health:     GET https://cheradip.com/ailt/api/health  and  GET https://ai.cheradip.com/health"

Set-Location $Repo
