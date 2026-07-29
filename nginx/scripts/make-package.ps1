# One-shot: build + export nginx/package/ (ONLY upload package/ to the server)
# Usage: cd nginx; .\scripts\make-package.ps1
#        .\scripts\make-package.ps1 -SkipDocker -SkipBackend
#        .\scripts\make-package.ps1 -ShowBuildDetails

param(
    [switch]$SkipDocker,
    [switch]$SkipFront,
    [switch]$SkipBackend,
    [switch]$ShowBuildDetails
)

$ErrorActionPreference = "Stop"
$scriptsDir = $PSScriptRoot
$nginxRoot = Split-Path -Parent $scriptsDir

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Server bundle -> nginx\package\ ONLY" -ForegroundColor Cyan
Write-Host " Classic: dist/ bind mount (not static image)" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$buildParams = @{
    SkipDocker = $SkipDocker
    SkipFront = $SkipFront
    SkipBackend = $SkipBackend
    ShowBuildDetails = $ShowBuildDetails
}

& (Join-Path $scriptsDir "build-all.ps1") @buildParams
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& (Join-Path $scriptsDir "export-images.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& (Join-Path $scriptsDir "verify-package.ps1")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$pkg = Join-Path $nginxRoot "package"
Write-Host ""
Write-Host "Done. Upload this folder to server ~/package :" -ForegroundColor Green
Write-Host "  $pkg" -ForegroundColor Green
Write-Host ""
