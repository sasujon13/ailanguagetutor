# Windows-safe wrapper for :app:assembleRelease.
# Avoids "AccessDeniedException" / "Unable to delete directory" when Android Studio
# and Gradle both touch build/ folders.
#
# Usage (from repo root):
#   .\scripts\assemble-release.ps1
#   .\scripts\assemble-release.ps1 -SkipPurge

param(
    [switch]$SkipPurge
)

$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

function Stop-GradleDaemons {
    Write-Host "Stopping Gradle daemons (gradlew --stop)..."
    & .\gradlew.bat --stop 2>&1 | Out-Host
    Start-Sleep -Seconds 1

    $gradleJava = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'GradleDaemon|org\.gradle\.launcher' }
    foreach ($proc in $gradleJava) {
        Write-Host "Stopping Gradle JVM PID $($proc.ProcessId)"
        Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue
    }
    if ($gradleJava) { Start-Sleep -Seconds 2 }
}

function Remove-BuildTree {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Write-Host "  $Path"
    cmd /c "rd /s /q `"$Path`"" 2>$null | Out-Null
    if (Test-Path $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (Test-Path $Path) {
        Write-Warning "Could not delete $Path - close Android Studio and run again."
        return $false
    }
    return $true
}

function Purge-BuildDirs {
    Write-Host "Purging build folders..."
    $ok = $true
    foreach ($topBuild in @(
        (Join-Path $root "app\build"),
        (Join-Path $root "build")
    )) {
        if (-not (Remove-BuildTree $topBuild)) { $ok = $false }
    }
    foreach ($moduleRoot in @("core", "feature", "ui", "tools")) {
        $moduleRootPath = Join-Path $root $moduleRoot
        if (-not (Test-Path $moduleRootPath)) { continue }
        Get-ChildItem -Path $moduleRootPath -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $buildDir = Join-Path $_.FullName "build"
            if (-not (Remove-BuildTree $buildDir)) { $ok = $false }
        }
    }
    return $ok
}

if (-not $SkipPurge) {
    Stop-GradleDaemons
    if (-not (Purge-BuildDirs)) {
        Write-Host ""
        Write-Host "Purge incomplete. Close Android Studio completely, then retry:"
        Write-Host "  .\scripts\assemble-release.ps1"
        exit 1
    }
}

Write-Host "Running assembleRelease (no daemon, single worker)..."
& .\gradlew.bat assembleRelease --no-daemon --no-parallel --max-workers=1
$exitCode = $LASTEXITCODE

if ($exitCode -eq 0) {
    $apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path $apk) {
        $sizeMb = [math]::Round((Get-Item $apk).Length / 1MB, 1)
        Write-Host ""
        Write-Host "Release APK: $apk ($sizeMb MB)"
        Write-Host ""
    }
}

exit $exitCode
