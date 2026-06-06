# Verify local stack — health, pack download, Home AI translate smoke test
# Usage: .\scripts\verify-stack.ps1

$ErrorActionPreference = "Continue"

function Test-Endpoint {
    param([string]$Name, [string]$Url)
    try {
        $r = Invoke-RestMethod -Uri $Url -TimeoutSec 15
        Write-Host ('OK  ' + $Name)
        return $r
    } catch {
        Write-Host ('FAIL ' + $Name + ' — ' + $_)
        return $null
    }
}

Write-Host "=== AI Language Tutor stack verify ==="
Write-Host ""

$homeHealth = Test-Endpoint "Home AI health" "http://127.0.0.1:8787/health"
$cloudHealth = Test-Endpoint "Cloud API health" "http://127.0.0.1:8790/api/ailt/health"

if ($cloudHealth) {
    $packCount = $cloudHealth.language_packs_available
    Write-Host ('  packs=' + $packCount + ' db=' + $cloudHealth.database)
    if ($packCount -lt 243) {
        Write-Host ('WARN Expected 243 language packs on disk — run: .\gradlew.bat :tools:pack-builder:run --args="build-all --version 1.0.0"')
    } else {
        Write-Host ('OK  Language pack count (' + $packCount + ')')
    }
}

# Pack download: use curl (Invoke-WebRequest hangs on binary FileResponse without -OutFile)
$packUrl = "http://127.0.0.1:8790/api/ailt/languages/en/file"
$curl = curl.exe -s -o NUL -w "%{http_code}|%{size_download}" --max-time 10 $packUrl 2>&1
if ($LASTEXITCODE -eq 0 -and $curl -match '^200\|(\d+)$') {
    Write-Host ('OK  Pack download (en) — ' + $Matches[1] + ' bytes')
} else {
    Write-Host ('FAIL Pack download — curl returned: ' + $curl)
    Write-Host "  Tip: use curl.exe or: Invoke-WebRequest -Uri '$packUrl' -OutFile `$env:TEMP\en.zip -TimeoutSec 15"
}

if ($homeHealth) {
    try {
        $body = @{
            processing_intent = "translation"
            ai_engine_mode    = 2
            text              = "Good morning."
            language_code     = "en"
            target_languages  = @("fr")
            subscription_tier = "pro"
        } | ConvertTo-Json
        $tr = Invoke-RestMethod -Uri "http://127.0.0.1:8787/translate" -Method POST `
            -ContentType "application/json" -Body $body -TimeoutSec 120
        $fr = $tr.translations.fr
        if ($fr -match "Je vous en prie" -and $fr -notmatch "Bonjour") {
            Write-Host ('WARN Short-phrase translate still odd: ' + $fr + ' (clear cache: server\v2\scripts\clear-cache.ps1)')
        } else {
            Write-Host ('OK  Short-phrase translate: ' + $fr)
        }
    } catch {
        Write-Host ('FAIL Home AI translate — ' + $_)
    }
}

Write-Host ""
Write-Host "Tunnels (optional):"
curl.exe -s -o NUL -w "  ai.cheradip.com -> %{http_code}`n" --max-time 15 https://ai.cheradip.com/health
curl.exe -s -o NUL -w "  ailt.cheradip.com -> %{http_code}`n" --max-time 15 https://ailt.cheradip.com/api/ailt/health

Write-Host ""
Write-Host "Done."
