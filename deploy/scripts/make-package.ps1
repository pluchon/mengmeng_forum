# One-shot: build + export an external deployment package
# Usage: cd deploy; .\scripts\make-package.ps1
#        .\scripts\make-package.ps1 -SkipDocker -SkipBackend
#        .\scripts\make-package.ps1 -ShowBuildDetails

param(
    [switch]$SkipDocker,
    [switch]$SkipFront,
    [switch]$SkipBackend,
    [switch]$SkipTests,
    [switch]$ShowBuildDetails,
    [string]$BuildProxy = $env:FORUM_BUILD_PROXY,
    [string]$DockerBuildProxy = $env:FORUM_DOCKER_BUILD_PROXY,
    [string]$OutputRoot = "C:\forum-build\luntan-package"
)

$ErrorActionPreference = "Stop"
$scriptsDir = $PSScriptRoot
$deployRoot = Split-Path -Parent $scriptsDir

& (Join-Path $scriptsDir "test-production-tls.ps1") `
    -SslRoot (Join-Path $deployRoot "ssl") `
    -NginxConfig (Join-Path $deployRoot "conf.d\20-prod-https.conf")
if (-not $?) { throw "生产域名与 TLS 证书校验失败" }

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Server bundle -> $OutputRoot" -ForegroundColor Cyan
Write-Host " Classic: dist/ bind mount (not static image)" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$buildParams = @{
    SkipDocker = $SkipDocker
    SkipFront = $SkipFront
    SkipBackend = $SkipBackend
    SkipTests = $SkipTests
    ShowBuildDetails = $ShowBuildDetails
    BuildProxy = $BuildProxy
    DockerBuildProxy = $DockerBuildProxy
}

& (Join-Path $scriptsDir "build-all.ps1") @buildParams
if (-not $?) { throw "本地构建脚本执行失败" }

& (Join-Path $scriptsDir "export-images.ps1") -OutputRoot $OutputRoot
if (-not $?) { throw "部署包导出脚本执行失败" }

& (Join-Path $scriptsDir "verify-package.ps1") -PkgRoot $OutputRoot
if (-not $?) { throw "部署包校验脚本执行失败" }

Write-Host ""
Write-Host "Done. Upload this folder to server ~/package :" -ForegroundColor Green
Write-Host "  $OutputRoot" -ForegroundColor Green
Write-Host ""
