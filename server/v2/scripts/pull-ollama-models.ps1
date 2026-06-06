# Pull all Home AI LLM models used by server/v2 (Ollama backend)
$ErrorActionPreference = "Stop"
$ollama = "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe"
if (-not (Test-Path $ollama)) {
    Write-Host "Install Ollama first: winget install Ollama.Ollama"
    exit 1
}

$models = @(
    "qwen2.5:7b-instruct-q4_K_M",   # Mode 1/3 — Smart Tutor, Balanced
    "qwen2.5:14b-instruct-q4_K_M",  # Mode 5 — High Accuracy (Plus)
    "mistral:7b-instruct-q4_K_M",     # Mode 4 — Lightweight / OCR cleanup
    "llama3:8b-instruct-q4_K_M"     # LLM fallback chain
)

Write-Host "Home AI Ollama models (~20 GB total on first run):"
foreach ($m in $models) { Write-Host "  - $m" }
Write-Host ""

foreach ($model in $models) {
    $installed = (& $ollama list 2>&1 | Select-Object -Skip 1 | ForEach-Object {
        ($_ -split '\s{2,}')[0].Trim()
    })
    if ($installed -contains $model) {
        Write-Host "[skip] $model (already installed)"
        continue
    }
    Write-Host "[pull] $model ..."
    & $ollama pull $model
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to pull $model"
    }
}

Write-Host ""
Write-Host "Installed models:"
& $ollama list
Write-Host ""
Write-Host "For NLLB translation (modes 2/3/5), also run:"
Write-Host "  .\scripts\pull-nllb-model.ps1"
