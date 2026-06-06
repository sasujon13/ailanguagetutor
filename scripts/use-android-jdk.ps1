# Point this PowerShell session at Android Studio's bundled JDK (JBR).
# Usage: . .\scripts\use-android-jdk.ps1

$ErrorActionPreference = "Stop"

function Resolve-AndroidJdkHome {
    $candidates = @(
        (Join-Path $PSScriptRoot "..\gradle.properties"),
        (Join-Path (Get-Location) "gradle.properties")
    ) | Select-Object -Unique

    foreach ($props in $candidates) {
        if (-not (Test-Path $props)) { continue }
        foreach ($line in Get-Content $props) {
            if ($line -match '^\s*org\.gradle\.java\.home=(.+)$') {
                $raw = $matches[1].Trim()
                $home = $raw -replace '\\:', ':' -replace '\\\\', '\'
                if (Test-Path (Join-Path $home "bin\java.exe")) { return $home }
            }
        }
    }

    $fallbacks = @(
        "${env:ProgramFiles}\Android\Android Studio\jbr",
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "${env:ProgramFiles}\Java\jdk-21",
        "${env:ProgramFiles}\Eclipse Adoptium\jdk-21*"
    )
    foreach ($path in $fallbacks) {
        Get-Item $path -ErrorAction SilentlyContinue | ForEach-Object {
            if (Test-Path (Join-Path $_.FullName "bin\java.exe")) { return $_.FullName }
        }
    }

    throw "No JDK found. Install Android Studio or set JAVA_HOME to a JDK 17+ folder."
}

$javaHome = Resolve-AndroidJdkHome
$env:JAVA_HOME = $javaHome
if ($env:PATH -notlike "*$javaHome\bin*") {
    $env:PATH = "$javaHome\bin;$env:PATH"
}
Write-Host "JAVA_HOME=$env:JAVA_HOME"
