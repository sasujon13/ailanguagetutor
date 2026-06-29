# Build SQLite language packs for all 243 catalog languages and sync to bcheradip/ailt_api.
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
Set-Location $Root

$version = if ($args[0]) { $args[0] } else { "1.0.0" }
Write-Host "Building all language packs (version $version)..."
.\gradlew.bat :tools:pack-builder:run --args="build-all --version $version"
Write-Host "Done. Packs synced to bcheradip/ailt_api/packs/ (see AILT_PACKS_DIR in local.env.properties)"
