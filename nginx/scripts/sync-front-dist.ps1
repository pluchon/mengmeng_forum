# 将 forum-vue/front 构建产物同步到 nginx/dist/user
# 用法（在 nginx 目录）: .\scripts\sync-front-dist.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$front = Join-Path $root "..\forum-vue\front"
$distSrc = Join-Path $front "dist"
$distDst = Join-Path $root "dist\user"

if (-not (Test-Path $distSrc)) {
    Write-Host "未找到 $distSrc，请先在 forum-vue/front 执行: npm run build"
    exit 1
}

New-Item -ItemType Directory -Force -Path $distDst | Out-Null
Remove-Item -Recurse -Force (Join-Path $distDst "*") -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force (Join-Path $distSrc "*") $distDst
Write-Host "已同步到 $distDst"
