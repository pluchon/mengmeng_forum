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

# 子脚本内部用 exit 1 结束时，$? 仍然是 True，只有 $LASTEXITCODE 会变。
# 单看 $? 会让失败的构建步骤被当成成功，继续打出一个不完整的部署包。
function Invoke-PackageStep {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [hashtable]$Arguments = @{}
    )
    $global:LASTEXITCODE = 0
    & $ScriptPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Name 失败（退出码 $LASTEXITCODE）：$ScriptPath"
    }
}

Invoke-PackageStep -Name "生产域名与 TLS 证书校验" `
    -ScriptPath (Join-Path $scriptsDir "test-production-tls.ps1") `
    -Arguments @{
        SslRoot     = (Join-Path $deployRoot "ssl")
        NginxConfig = (Join-Path $deployRoot "conf.d\20-prod-https.conf")
    }

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

Invoke-PackageStep -Name "本地构建" `
    -ScriptPath (Join-Path $scriptsDir "build-all.ps1") -Arguments $buildParams

Invoke-PackageStep -Name "部署包导出" `
    -ScriptPath (Join-Path $scriptsDir "export-images.ps1") `
    -Arguments @{ OutputRoot = $OutputRoot }

Invoke-PackageStep -Name "部署包校验" `
    -ScriptPath (Join-Path $scriptsDir "verify-package.ps1") `
    -Arguments @{ PkgRoot = $OutputRoot }

Write-Host ""
Write-Host "Done. Upload this folder to server ~/package :" -ForegroundColor Green
Write-Host "  $OutputRoot" -ForegroundColor Green
Write-Host ""
