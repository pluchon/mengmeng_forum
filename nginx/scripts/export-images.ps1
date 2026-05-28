# Export images + assemble nginx/package/ (classic dist bind mount on server)
# Usage: cd nginx; .\scripts\export-images.ps1

$ErrorActionPreference = "Stop"
$nginxRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $nginxRoot
$pkg = Join-Path $nginxRoot "package"
$outDir = Join-Path $pkg "images"

function Require-Path {
    param([string]$Path, [string]$Hint)
    if (-not (Test-Path $Path)) {
        throw "Missing ${Hint}: ${Path}. Run .\scripts\build-all.ps1 first."
    }
}

function Test-DockerImage {
    param([string]$Name)
    $prevEa = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $null = docker image inspect $Name 2>&1
    $ok = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $prevEa
    return $ok
}

function Ensure-DockerImage {
    param([string]$Name)
    if (Test-DockerImage $Name) { Write-Host "  OK  $Name" -ForegroundColor DarkGray; return }
    Write-Host "Pulling $Name ..."
    docker pull $Name
    if ($LASTEXITCODE -ne 0) { throw "docker pull failed: $Name" }
}

if (Test-Path $pkg) { Remove-Item -Recurse -Force $pkg }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $pkg "logs") | Out-Null

Require-Path (Join-Path $nginxRoot "dist\user\index.html") "front dist index.html"
Require-Path (Join-Path $nginxRoot "dist\admin\index.html") "admin dist index.html"
Require-Path (Join-Path $nginxRoot "conf.d") "nginx conf.d"

$userIndex = Join-Path $nginxRoot "dist\user\index.html"
$indexHtml = Get-Content -Raw $userIndex
if ($indexHtml -notmatch '/assets/.*\.js') {
    throw "dist/user/index.html missing /assets/*.js - run npm run build in forum-vue/front"
}
$assetsDir = Join-Path $nginxRoot "dist\user\assets"
if (-not (Test-Path $assetsDir)) { throw "Missing dist/user/assets" }

$images = @(
    "forum-backend:latest",
    "forum-ai-server:latest",
    "nginx:1.30.1",
    "mysql:9.7.0",
    "redis:8.0",
    "rabbitmq:4.3-management",
    "postgres:17"
)

Write-Host "Checking / pulling images ..."
foreach ($img in $images) { Ensure-DockerImage $img }

Write-Host "Saving images to $outDir ..."
docker save -o (Join-Path $outDir "forum-backend.tar") forum-backend:latest
if ($LASTEXITCODE -ne 0) { throw "docker save failed: forum-backend:latest" }

docker save -o (Join-Path $outDir "forum-ai-server.tar") forum-ai-server:latest
if ($LASTEXITCODE -ne 0) { throw "docker save failed: forum-ai-server:latest" }

docker save -o (Join-Path $outDir "infra.tar") nginx:1.30.1 mysql:9.7.0 redis:8.0 rabbitmq:4.3-management postgres:17
if ($LASTEXITCODE -ne 0) { throw "docker save failed: infra.tar" }

foreach ($dir in @("dist", "conf.d", "ssl")) {
    $src = Join-Path $nginxRoot $dir
    $dst = Join-Path $pkg $dir
    if (Test-Path $src) {
        if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
        Copy-Item -Recurse -Force $src $dst
    }
    elseif ($dir -eq "ssl") {
        Write-Host "WARN: nginx/ssl/ empty - add certs before HTTPS" -ForegroundColor Yellow
        New-Item -ItemType Directory -Force -Path $dst | Out-Null
    }
}

$verifySh = Join-Path $scriptsDir "verify-frontend-dist.sh"
if (Test-Path $verifySh) {
    Copy-Item $verifySh (Join-Path $pkg "verify-frontend-dist.sh") -Force
}

Copy-Item (Join-Path $nginxRoot "docker-compose.yaml") $pkg -Force
Copy-Item (Join-Path $nginxRoot "docker-compose.prod.yml") $pkg -Force
if (Test-Path (Join-Path $nginxRoot ".env.example")) {
    Copy-Item (Join-Path $nginxRoot ".env.example") (Join-Path $pkg ".env.example") -Force
}
$sqlSrc = Join-Path $repoRoot "forum-demo\src\main\resources\sql"
$sqlDst = Join-Path $pkg "sql"
if (Test-Path $sqlSrc) {
    New-Item -ItemType Directory -Force -Path $sqlDst | Out-Null
    foreach ($sql in @("create.sql", "patch_ai_models_qwen37.sql", "patch_vip_quota_models.sql")) {
        $f = Join-Path $sqlSrc $sql
        if (Test-Path $f) { Copy-Item $f $sqlDst -Force }
    }
}

$startSh = @'
#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p logs/backend
test -f .env || { echo "Run: cp .env.example .env && nano .env"; exit 1; }
if [[ -f ./verify-frontend-dist.sh ]]; then
  chmod +x ./verify-frontend-dist.sh
  ./verify-frontend-dist.sh .
fi
chmod -R a+rX dist conf.d ssl 2>/dev/null || true
# logs 可能由容器以 root 创建，chmod 失败时不要中断部署
if [[ -d logs ]]; then
  chmod -R a+rX logs 2>/dev/null || sudo rm -rf logs
fi
mkdir -p logs/backend
chown -R 1000:1000 logs/backend 2>/dev/null || chmod -R 777 logs/backend 2>/dev/null || true
docker load -i images/forum-backend.tar
docker load -i images/forum-ai-server.tar
docker load -i images/infra.tar
docker compose -f docker-compose.yaml -f docker-compose.prod.yml up -d --force-recreate
echo "--- host middleware ports (127.0.0.1) ---"
ss -tlnp 2>/dev/null | grep -E '33061|63790|54320|56720|15672' || true
docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps mysql redis postgres rabbitmq
echo "--- dist mount check ---"
docker compose -f docker-compose.yaml -f docker-compose.prod.yml exec nginx ls -la /usr/share/nginx/user/index.html
docker compose -f docker-compose.yaml -f docker-compose.prod.yml exec nginx ls /usr/share/nginx/user/assets | head -5
echo "--- curl ---"
curl -s http://127.0.0.1/healthz
'@
[System.IO.File]::WriteAllText((Join-Path $pkg "start.sh"), $startSh.Trim() + "`n", [System.Text.UTF8Encoding]::new($false))

$readme = @"
Upload ONLY this package/ folder to ~/package on the server.
See repository README.md for full deploy steps.

  cp .env.example .env && nano .env
  chmod +x start.sh verify-frontend-dist.sh
  ./verify-frontend-dist.sh .
  ./start.sh
"@
[System.IO.File]::WriteAllText((Join-Path $pkg "README.txt"), $readme.Trim() + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))

Write-Host ""
Write-Host "Package ready (upload ONLY):" -ForegroundColor Green
Write-Host "  $pkg" -ForegroundColor Green
Write-Host "  Contains: dist/ conf.d/ ssl/ images/*.tar" -ForegroundColor Green
Write-Host ""
