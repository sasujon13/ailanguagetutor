# Start local SMTP receiver (port 1025) — saves mail to server/mail/inbox/
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

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

# pip writes notices to stderr; do not use $ErrorActionPreference Stop here.
& $pyExe -m pip install --quiet aiosmtpd 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to install aiosmtpd. Run: $pyExe -m pip install aiosmtpd"
}

Write-Host "Starting dev SMTP on 127.0.0.1:1025 (Ctrl+C to stop)..."
& $pyExe dev_smtp_server.py
