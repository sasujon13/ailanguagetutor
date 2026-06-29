# Verify stack — Home AI (local) + App API (Linux or local bcheradip/ailt_api)
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

$homeHealth = Test-Endpoint "Home AI health (local)" "http://127.0.0.1:8787/health"
$cloudHealth = Test-Endpoint "App API health (Linux)" "https://cheradip.com/ailt/api/health"
$cloudLocal = Test-Endpoint "App API health (local bcheradip)" "http://127.0.0.1:8790/api/ailt/health"

$activeCloud = if ($cloudHealth) { $cloudHealth } else { $cloudLocal }

if ($activeCloud) {
    $packCount = $activeCloud.language_packs_available
    Write-Host ('  packs=' + $packCount + ' db=' + $activeCloud.database)
    if ($packCount -lt 243) {
        Write-Host ('WARN Expected 243 language packs — run: .\tools\pack-builder\scripts\build-all-packs.ps1')
    } else {
        Write-Host ('OK  Language pack count (' + $packCount + ')')
    }
}

$packBase = if ($cloudHealth) { "https://cheradip.com/ailt/api" } else { "http://127.0.0.1:8790/api/ailt" }
$packUrl = "$packBase/languages/en/file"
$curl = curl.exe -s -o NUL -w "%{http_code}|%{size_download}" --max-time 15 $packUrl 2>&1
if ($LASTEXITCODE -eq 0 -and $curl -match '^200\|(\d+)$') {
    Write-Host ('OK  Pack download (en) — ' + $Matches[1] + ' bytes')
} else {
    Write-Host ('FAIL Pack download — curl returned: ' + $curl)
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
curl.exe -s -o NUL -w "  cheradip.com/ailt/api -> %{http_code}`n" --max-time 15 https://cheradip.com/ailt/api/health

Write-Host ""
Write-Host "Done."
