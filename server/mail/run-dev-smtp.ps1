# Start local SMTP receiver (port 1025) — saves mail to server/mail/inbox/
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$Port = 1025
$existing = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($existing) {
    Write-Host "Port $Port is already in use (PID $($existing.OwningProcess))."
    Write-Host "SMTP mail receiver is probably already running on 127.0.0.1:$Port"
    Write-Host "Inbox: $Root\inbox\"
    Write-Host ""
    Write-Host "To restart, stop the old process first:"
    Write-Host "  Stop-Process -Id $($existing.OwningProcess) -Force"
    exit 0
}

$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
    if (-not $python) {
        Write-Error "Python not found. Install Python 3.11+ from python.org"
    }
    $pyExe = "py"
} else {
    $pyExe = "python"
}

& $pyExe -m pip install --quiet aiosmtpd 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to install aiosmtpd. Run: $pyExe -m pip install aiosmtpd"
}

Write-Host "Starting SMTP on 127.0.0.1:$Port (Ctrl+C to stop)..."
Write-Host "Inbox: $Root\inbox\"
& $pyExe dev_smtp_server.py
