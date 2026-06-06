# Clear Home AI L3 SQLite cache (stale NLLB stub entries, etc.)
# Usage: cd server\v2; .\scripts\clear-cache.ps1

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Db = Join-Path $Root "data\ai_cache.db"

if (Test-Path $Db) {
    Remove-Item $Db -Force
    Write-Host "Removed $Db"
} else {
    Write-Host "No cache DB at $Db (already clear)"
}

Write-Host "Restart Home AI if running: .\scripts\run-dev.ps1 -Restart"
