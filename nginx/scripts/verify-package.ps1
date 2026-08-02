# 打包产物自检（make-package 结束后自动调用）
param([string]$PkgRoot = (Join-Path (Split-Path -Parent $PSScriptRoot) "package"))

$ErrorActionPreference = "Stop"
$required = @(
    "dist\user\index.html",
    "images\forum-backend.tar",
    "images\forum-ai-server.tar",
    "images\infra.tar",
    "docker-compose.yaml",
    "docker-compose.prod.yml",
    ".env",
    "start.sh",
    "up.sh",
    "migrate-online-db.sh",
    "verify-frontend-dist.sh",
    "sql\auth-create.sql",
    "sql\content-create.sql",
    "sql\im-create.sql",
    "sql\game-create.sql",
    "sql\economy-create.sql",
    "sql\ai-create.sql",
    "sql\postgres_ai_session.sql"
)
$optional = @("ssl", "ffmpeg\Dockerfile", "reset-db.sh", "collect-logs.sh")

Write-Host "Verifying package: $PkgRoot" -ForegroundColor Cyan
foreach ($rel in $required) {
    $p = Join-Path $PkgRoot $rel
    if (-not (Test-Path $p)) { throw "Package missing required: $rel" }
    Write-Host "  OK $rel" -ForegroundColor DarkGray
}
foreach ($rel in $optional) {
    $p = Join-Path $PkgRoot $rel
    if (Test-Path $p) { Write-Host "  OK $rel" -ForegroundColor DarkGray }
    else { Write-Host "  WARN optional missing: $rel" -ForegroundColor Yellow }
}

foreach ($shName in @("start.sh", "up.sh", "reset-db.sh", "migrate-online-db.sh", "verify-frontend-dist.sh")) {
    $sh = Join-Path $PkgRoot $shName
    if (-not (Test-Path $sh)) { continue }
    $raw = [System.IO.File]::ReadAllBytes($sh)
    if ($raw -contains 13) { throw "$shName contains CRLF - re-run export-images.ps1" }
}
$envPath = Join-Path $PkgRoot ".env"
if (Test-Path $envPath) {
    $raw = [System.IO.File]::ReadAllBytes($envPath)
    if ($raw -contains 13) { throw ".env contains CRLF - re-save nginx/.env as UTF-8 LF or re-run export" }
}
Write-Host "Package verification passed." -ForegroundColor Green
