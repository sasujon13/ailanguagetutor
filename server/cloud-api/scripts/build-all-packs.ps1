# Build all 243 SQLite language packs and sync to cloud-api
param(
    [string]$Version = "1.0.0"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
Set-Location $root
. (Join-Path $root "scripts\use-android-jdk.ps1")
.\gradlew.bat :tools:pack-builder:run --args="build-all --version $Version"
