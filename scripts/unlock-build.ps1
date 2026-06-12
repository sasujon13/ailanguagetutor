# Fixes Windows "Unable to delete directory ... build\intermediates" during Gradle builds.
# Android Studio preview / Rebuild fails when Gradle daemons or Studio lock .class files.
#
# Usage (from repo root):
#   .\scripts\unlock-build.ps1
#   .\scripts\unlock-build.ps1 -ThenBuild
#   .\scripts\unlock-build.ps1 -ThenPreview

param(
    [switch]$ThenBuild,
    [switch]$ThenPreview
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

function Remove-ModuleBuildDirs {
    Write-Host "Removing module build folders..."
    $moduleRoots = @(
        (Join-Path $root "core"),
        (Join-Path $root "feature"),
        (Join-Path $root "ui")
    )
    foreach ($moduleRoot in $moduleRoots) {
        if (-not (Test-Path $moduleRoot)) { continue }
        Get-ChildItem -Path $moduleRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object {
            $buildDir = Join-Path $_.FullName "build"
            if (-not (Test-Path $buildDir)) { return }
            Write-Host "  $buildDir"
            Remove-Item -LiteralPath $buildDir -Recurse -Force -ErrorAction SilentlyContinue
            if (Test-Path $buildDir) {
                Write-Warning "Could not delete $buildDir - close Android Studio completely and run again."
            }
        }
    }
}

Stop-GradleDaemons
Remove-ModuleBuildDirs

if ($ThenPreview) {
    Write-Host "Compiling preview modules (no daemon)..."
    & .\gradlew.bat `
        :ui:theme:compileDebugKotlin `
        :ui:components:compileDebugKotlin `
        :feature:home:compileDebugKotlin `
        :feature:practice:compileDebugKotlin `
        --no-daemon
    exit $LASTEXITCODE
}

if ($ThenBuild) {
    Write-Host "Running assembleDebug (no daemon)..."
    & .\gradlew.bat :app:assembleDebug --no-daemon
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Done."
Write-Host "If folders are still locked: exit Android Studio, then run:"
Write-Host '  .\scripts\unlock-build.ps1 -ThenPreview'
