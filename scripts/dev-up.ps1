# 本地一键拉起中间件，并可选执行首次建库
# 用法:
#   .\scripts\dev-up.ps1
#   .\scripts\dev-up.ps1 -InitDb

param([switch]$InitDb)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$composeScript = Join-Path $repoRoot "deploy\scripts\dev-compose.ps1"
$initScript = Join-Path $PSScriptRoot "init-db.ps1"

Write-Host "==> 启动本地中间件" -ForegroundColor Cyan
& $composeScript up -d
if ($LASTEXITCODE -ne 0) { throw "dev-compose 失败" }

if ($InitDb) {
    Write-Host "==> 首次建库" -ForegroundColor Cyan
    & $initScript
}

Write-Host ""
Write-Host "中间件已就绪。接下来:" -ForegroundColor Green
Write-Host "  1. 前端:  cd forum-vue\front ; npm install ; npm run dev"
Write-Host "  2. Java:  在 IDEA 启动 gateway / auth / content / im / game / economy / ai"
Write-Host "  3. AI:    cd ai-server ; python main.py"
Write-Host "  Nacos 控制台: http://127.0.0.1:8080/index.html"
Write-Host "  首次建库可再执行: .\scripts\init-db.ps1"
