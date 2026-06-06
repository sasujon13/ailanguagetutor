# Start Cheradip Home AI (dev) - requires Ollama + pulled models
# Usage: .\scripts\run-dev.ps1
#        .\scripts\run-dev.ps1 -Restart

param(
    [switch]$Restart
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$Port = 8787
$PidFile = Join-Path $Root ".run-dev-reloader.pid"

function Stop-ProcessTree {
    param([int]$ProcId)

    if ($ProcId -le 0) { return }
    if (-not (Get-Process -Id $ProcId -ErrorAction SilentlyContinue)) { return }

    Write-Host "Stopping PID $ProcId ..."
    Stop-Process -Id $ProcId -Force -ErrorAction SilentlyContinue
    cmd.exe /c "taskkill /F /T /PID $ProcId" 1>$null 2>$null
}

function Stop-HomeAiOnPort {
    param([int]$ListenPort)

    if (Test-Path $PidFile) {
        $saved = Get-Content $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($saved -match '^\d+$') {
            Stop-ProcessTree -ProcId ([int]$saved)
        }
    }

    $pids = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($conn in Get-NetTCPConnection -LocalPort $ListenPort -ErrorAction SilentlyContinue) {
        [void]$pids.Add([int]$conn.OwningProcess)
    }

    $rootPattern = [regex]::Escape($Root)
    $v2Pattern = 'ailanguagetutor[\\/]server[\\/]v2'
    Get-CimInstance Win32_Process -Filter "Name='python.exe'" -ErrorAction SilentlyContinue | ForEach-Object {
        $cmd = $_.CommandLine
        if (-not $cmd) { return }
        if ($cmd -match $rootPattern -or $cmd -match $v2Pattern) { [void]$pids.Add([int]$_.ProcessId); return }
        if ($cmd -match "app\.main:app" -and $cmd -match "$ListenPort") { [void]$pids.Add([int]$_.ProcessId); return }
        if ($cmd -match "multiprocessing\.spawn" -and $cmd -match "uvicorn") { [void]$pids.Add([int]$_.ProcessId) }
    }

    foreach ($procId in $pids) {
        Stop-ProcessTree -ProcId $procId
    }

    Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

$existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($existing -or $Restart) {
    if ($Restart) {
        Write-Host "Restarting Home AI on port $Port ..."
        Stop-HomeAiOnPort -ListenPort $Port
    } elseif ($existing) {
        Write-Host "Port $Port is already in use (PID $($existing.OwningProcess))."
        Write-Host "Home AI is probably already running -> http://127.0.0.1:${Port}/health"
        Write-Host ""
        Write-Host "To restart:"
        Write-Host "  .\scripts\run-dev.ps1 -Restart"
        exit 0
    }
}

$still = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($still) {
    Write-Host "Port $Port still in use - stopping remaining Python workers ..."
    Stop-HomeAiOnPort -ListenPort $Port
    $still = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
}
if ($still) {
    Write-Host "Port $Port still in use. Close the terminal running Home AI, or run:"
    Write-Host "  Get-Process python | Stop-Process -Force"
    exit 1
}

$ollama = "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe"
if (-not (Test-Path $ollama)) {
    Write-Host "Ollama not found. Install: winget install Ollama.Ollama"
    Write-Host "Then pull models: .\scripts\pull-ollama-models.ps1"
    exit 1
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    (Get-Content ".env.example") -replace 'INFERENCE_BACKEND=stub', 'INFERENCE_BACKEND=ollama' | Set-Content ".env"
    Write-Host "Created .env with INFERENCE_BACKEND=ollama"
}

$models = & $ollama list 2>&1
if ($models -match "qwen2.5:7b-instruct-q4_K_M") {
    Write-Host "Ollama model ready: qwen2.5:7b-instruct-q4_K_M"
} else {
    Write-Host "Pulling primary model (first run, ~4.7 GB)..."
    & $ollama pull qwen2.5:7b-instruct-q4_K_M
}

if (-not (Test-Path ".venv")) {
    python -m venv .venv
    .\.venv\Scripts\pip install -e .
}

Write-Host "Starting Home AI on http://127.0.0.1:8787/health"
$uvicorn = Join-Path $Root ".venv\Scripts\uvicorn.exe"
$proc = Start-Process -FilePath $uvicorn -ArgumentList @(
    "app.main:app", "--host", "0.0.0.0", "--port", "$Port", "--reload"
) -WorkingDirectory $Root -PassThru -NoNewWindow
$proc.Id | Set-Content -Path $PidFile -Encoding ascii
Wait-Process -Id $proc.Id
Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
