# One-click scan ONNX setup for Home AI server (ai.cheradip.com)
# Run from server/v2 with venv active.

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Test-ScanPythonDeps {
    param([string]$PythonExe)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & $PythonExe -c "import cv2, numpy, onnxruntime" 2>$null | Out-Null
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prev
    return $ok
}

function Test-PythonPip {
    param([string]$PythonExe)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & $PythonExe -m pip --version 2>$null | Out-Null
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prev
    return $ok
}

function Repair-VenvPip {
    param([string]$PythonExe)
    $venvRoot = Split-Path (Split-Path $PythonExe -Parent) -Parent
    $site = Join-Path $venvRoot "Lib\site-packages"
    if (Test-Path $site) {
        Get-ChildItem $site -Filter "~ip*" -ErrorAction SilentlyContinue |
            Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
        Get-ChildItem $site -Filter "pip*" -ErrorAction SilentlyContinue |
            Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & $PythonExe -m ensurepip --upgrade 2>$null | Out-Null
    & $PythonExe -m pip install -U pip -q 2>$null | Out-Null
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prev
    return $ok
}

function Install-ScanExtras {
    param([string]$PythonExe)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & $PythonExe -m pip install -e ".[scan]" -q 2>$null | Out-Null
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prev
    return $ok
}

function Resolve-PythonExe {
    if (Test-Path ".\.venv\Scripts\python.exe") {
        return ".\.venv\Scripts\python.exe"
    }
    return "python"
}

Write-Host "=== Scan model setup (upgrade.md) ===" -ForegroundColor Cyan

$py = Resolve-PythonExe
Write-Host "Using Python: $py" -ForegroundColor DarkGray

if (-not (Test-ScanPythonDeps $py)) {
    Write-Host "Installing scan extras (opencv, onnxruntime)..." -ForegroundColor Yellow
    $installed = $false
    if (Test-PythonPip $py) {
        $installed = Install-ScanExtras $py
    }
    if (-not $installed -and ($py -like "*\.venv\*")) {
        Write-Host "venv pip is broken - repairing..." -ForegroundColor Yellow
        if (Repair-VenvPip $py) {
            $installed = Install-ScanExtras $py
        }
    }
    if (-not $installed) {
        Write-Host "Falling back to system python..." -ForegroundColor Yellow
        $py = "python"
        if (-not (Install-ScanExtras $py)) {
            throw @"
scan dependencies missing.
Repair venv:  Remove-Item -Recurse -Force .venv; python -m venv .venv
Or install:   pip install -e ".[scan]"
"@
        }
    }
    if (-not (Test-ScanPythonDeps $py)) {
        throw "scan dependencies still missing after pip install (cv2, numpy, onnxruntime)"
    }
} else {
    Write-Host "Scan dependencies already available." -ForegroundColor Green
}

Write-Host "`n[1/3] u2net.onnx (~5 MB)..." -ForegroundColor Yellow
& $py scripts/download_scan_models.py --only u2net
if ($LASTEXITCODE -ne 0) { throw "u2net download failed" }

Write-Host "`n[2/3] yolov8.onnx (ultralytics export)..." -ForegroundColor Yellow
$prev = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
& $py -m pip install ultralytics -q 2>$null | Out-Null
$yoloPipOk = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prev
if (-not $yoloPipOk) { Write-Warning "pip install ultralytics failed - skipping yolov8 export" }
if ($yoloPipOk) {
    & $py scripts/export_scan_onnx.py yolov8
    if ($LASTEXITCODE -ne 0) { Write-Warning "yolov8 export failed - OpenCV crop fallback will be used" }
}

Write-Host "`n[3/3] realesrgan.onnx (torch export)..." -ForegroundColor Yellow
$ErrorActionPreference = "SilentlyContinue"
& $py -m pip install torch -q 2>$null | Out-Null
$srPipOk = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = $prev
if (-not $srPipOk) { Write-Warning "pip install torch failed - skipping realesrgan export" }
if ($srPipOk) {
    & $py scripts/export_scan_onnx.py realesrgan
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "realesrgan export failed - try: python scripts/download_scan_models.py --only realesrgan"
        Write-Warning "OpenCV sharpen fallback will be used until realesrgan.onnx is present"
    }
}

Write-Host "`nDewarp: OpenCV curve remap at L5+ (production standard, no ONNX)." -ForegroundColor Green

Write-Host "`nVerify:" -ForegroundColor Cyan
& $py -c "from app.services.scan_models import ScanModelRegistry; r=ScanModelRegistry(); r.load_optional(); print('Loaded:', list(r._loaded.keys()))"

Write-Host "`nRun tests:" -ForegroundColor Cyan
& $py -m pytest tests/test_scan_levels_validation.py -q

Write-Host "`nDone. Restart Home AI server to pick up models." -ForegroundColor Green
