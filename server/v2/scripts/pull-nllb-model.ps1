# Download NLLB-600M for real local translation (~2.5 GB)
# Usage: cd server\v2; .\scripts\pull-nllb-model.ps1

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

Write-Host "NLLB-600M download (~2.5 GB) for translation modes 2, 3, 5"
Write-Host "Target: models\hf\translation\nllb-600m\source`n"

if (-not (Test-Path ".venv")) {
    python -m venv .venv
}

$py = Join-Path $Root ".venv\Scripts\python.exe"
Remove-Item -Recurse -Force ".venv\Lib\site-packages\~ip" -ErrorAction SilentlyContinue

& $py -c "import huggingface_hub, transformers, torch" 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[1/2] Installing translation dependencies..."
    & $py -m pip install "transformers>=4.45" "torch>=2.4" sentencepiece protobuf huggingface_hub -q
    if ($LASTEXITCODE -ne 0) { Write-Error "Failed to install translation dependencies"; exit 1 }
} else {
    Write-Host "[1/2] Translation dependencies already installed — skipping"
}

Write-Host "[2/2] Downloading facebook/nllb-200-distilled-600M ..."
& $py (Join-Path $Root "scripts\download_nllb.py")
if ($LASTEXITCODE -ne 0) { Write-Error "NLLB download failed"; exit 1 }

Write-Host "`nRestart Home AI server: .\scripts\run-dev.ps1"
Write-Host "Translation will use NLLB automatically when weights are present."
