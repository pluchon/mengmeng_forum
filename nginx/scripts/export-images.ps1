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

function Write-UnixShellFile {
    param([string]$Path, [string]$Content)
    $lf = ($Content -replace "`r`n", "`n" -replace "`r", "`n").TrimEnd() + "`n"
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $lf, $utf8NoBom)
}

function Normalize-UnixLf {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    $raw = [System.IO.File]::ReadAllText($Path)
    Write-UnixShellFile -Path $Path -Content $raw
}

if (Test-Path $pkg) { Remove-Item -Recurse -Force $pkg }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Require-Path (Join-Path $nginxRoot "dist\user\index.html") "front dist index.html"
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
    "forum-ffmpeg:latest",
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

docker save -o (Join-Path $outDir "infra.tar") nginx:1.30.1 mysql:9.7.0 redis:8.0 rabbitmq:4.3-management postgres:17 forum-ffmpeg:latest
if ($LASTEXITCODE -ne 0) { throw "docker save failed: infra.tar" }

$ffmpegDir = Join-Path $nginxRoot "ffmpeg"
$pkgFfmpeg = Join-Path $pkg "ffmpeg"
if (Test-Path $ffmpegDir) {
    if (Test-Path $pkgFfmpeg) { Remove-Item -Recurse -Force $pkgFfmpeg }
    Copy-Item -Recurse -Force $ffmpegDir $pkgFfmpeg
    Normalize-UnixLf (Join-Path $pkgFfmpeg "server.py") -ErrorAction SilentlyContinue
}

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

$scriptsDir = $PSScriptRoot
$verifySh = Join-Path $scriptsDir "verify-frontend-dist.sh"
$serverUpSh = Join-Path $scriptsDir "server-up.sh"
if (Test-Path $verifySh) {
    Copy-Item $verifySh (Join-Path $pkg "verify-frontend-dist.sh") -Force
    Normalize-UnixLf (Join-Path $pkg "verify-frontend-dist.sh")
}
if (Test-Path $serverUpSh) {
    Copy-Item $serverUpSh (Join-Path $pkg "up.sh") -Force
    Normalize-UnixLf (Join-Path $pkg "up.sh")
}

Copy-Item (Join-Path $nginxRoot "docker-compose.yaml") $pkg -Force
Copy-Item (Join-Path $nginxRoot "docker-compose.prod.yml") $pkg -Force
if (Test-Path (Join-Path $nginxRoot ".env.example")) {
    Copy-Item (Join-Path $nginxRoot ".env.example") (Join-Path $pkg ".env.example") -Force
}
$envProd = Join-Path $nginxRoot ".env"
if (Test-Path $envProd) {
    Copy-Item $envProd (Join-Path $pkg ".env") -Force
    Normalize-UnixLf (Join-Path $pkg ".env")
    Write-Host "Copied nginx/.env -> package/.env (LF normalized)" -ForegroundColor Yellow
} else {
    Write-Host "WARN: nginx/.env missing - on server run: cp .env.example .env && nano .env" -ForegroundColor Yellow
}
$sqlSrc = Join-Path $repoRoot "java-cloud-standalone\platform\src\main\resources\sql"
$sqlDst = Join-Path $pkg "sql"
if (Test-Path $sqlSrc) {
    New-Item -ItemType Directory -Force -Path $sqlDst | Out-Null
    Get-ChildItem $sqlSrc -Filter "*.sql" | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $sqlDst $_.Name) -Force
    }
}
$startSh = @'
#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
COMPOSE="docker compose -f docker-compose.yaml -f docker-compose.prod.yml"
LOG_ROOT="${FORUM_LOG_DIR:-../logs}"
echo "Forum package release: 20260722-mq-healthcheck-v2"

fix_crlf() {
  for f in .env start.sh verify-frontend-dist.sh reset-db.sh; do
    [[ -f "$f" ]] && sed -i 's/\r$//' "$f"
  done
}
fix_crlf

mkdir -p "$LOG_ROOT"/backend "$LOG_ROOT"/ai-server "$LOG_ROOT"/ffmpeg "$LOG_ROOT"/nginx
test -f .env || { echo "ERROR: missing .env in package/"; exit 1; }
if grep -q 'CHANGE_ME' .env 2>/dev/null; then
  echo "WARN: .env still has CHANGE_ME placeholders (JWT_SECRET / PII_CRYPTO_SECRET must be real)"
fi
for tar in images/forum-backend.tar images/forum-ai-server.tar images/infra.tar; do
  test -f "$tar" || { echo "ERROR: missing $tar"; exit 1; }
done

if [[ -f ./verify-frontend-dist.sh ]]; then
  chmod +x ./verify-frontend-dist.sh
  ./verify-frontend-dist.sh .
fi
chmod -R a+rX dist conf.d ssl 2>/dev/null || true
chmod -R u=rwX,go= "$LOG_ROOT"

echo "==> docker load"
docker load -i images/forum-backend.tar
docker load -i images/forum-ai-server.tar
docker load -i images/infra.tar
for img in forum-backend:latest forum-ai-server:latest forum-ffmpeg:latest nginx:1.30.1; do
  docker image inspect "$img" >/dev/null 2>&1 || { echo "ERROR: image missing after load: $img"; exit 1; }
done
docker run --rm --user 0 \
  -v "$(cd "$LOG_ROOT/backend" && pwd):/app/logs" \
  --entrypoint /bin/sh forum-backend:latest \
  -c 'chown -R 1000:1000 /app/logs'

read_env() {
  local k="$1" line v
  line="$(grep -E "^${k}=" .env 2>/dev/null | tail -1 || true)"
  v="${line#*=}"
  v="${v//$'\r'/}"
  v="${v%\"}"; v="${v#\"}"
  printf '%s' "$v"
}

wait_mysql() {
  local root_pw="$1" i
  for i in $(seq 1 90); do
    if docker exec forum-mysql mysqladmin ping -h 127.0.0.1 -uroot -p"${root_pw}" --silent 2>/dev/null; then
      return 0
    fi
    sleep 2
  done
  echo "ERROR: MySQL not ready"; return 1
}

wait_rabbitmq() {
  local i
  for i in $(seq 1 90); do
    # ping 只代表 Erlang 运行时可连通；同步用户与 vhost 前必须等待 RabbitMQ 应用完成启动。
    if docker exec forum-rabbitmq rabbitmq-diagnostics -q check_running >/dev/null 2>&1; then
      echo "RabbitMQ application ready"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: RabbitMQ application did not become ready within 180 seconds"
  docker logs --tail 100 forum-rabbitmq >&2 || true
  return 1
}

sync_rabbitmq_credentials() {
  local user password vhost
  user="$(read_env RABBITMQ_USER)"
  password="$(read_env RABBITMQ_PASSWORD)"
  vhost="$(read_env RABBITMQ_VHOST)"
  user="${user:-nuonuo}"
  vhost="${vhost:-forum-demo}"
  [[ -n "$password" ]] || { echo "ERROR: RABBITMQ_PASSWORD is empty"; return 1; }

  wait_rabbitmq
  if docker exec forum-rabbitmq rabbitmqctl list_users -q | cut -f1 | grep -Fxq "$user"; then
    docker exec forum-rabbitmq rabbitmqctl change_password "$user" "$password" >/dev/null
  else
    docker exec forum-rabbitmq rabbitmqctl add_user "$user" "$password" >/dev/null
  fi
  docker exec forum-rabbitmq rabbitmqctl add_vhost "$vhost" >/dev/null 2>&1 || true
  docker exec forum-rabbitmq rabbitmqctl set_permissions -p "$vhost" "$user" ".*" ".*" ".*" >/dev/null
  docker exec forum-rabbitmq rabbitmqctl set_user_tags "$user" administrator >/dev/null
  docker exec forum-rabbitmq rabbitmqctl authenticate_user "$user" "$password" >/dev/null
  echo "RabbitMQ credentials synchronized"
}

echo "==> compose middleware"
$COMPOSE up -d mysql redis rabbitmq postgres ffmpeg
sync_rabbitmq_credentials

root_pw="$(read_env MYSQL_ROOT_PASSWORD)"
wait_mysql "$root_pw" || true

if [[ -f sql/create.sql ]] && [[ "${SKIP_DB_INIT:-0}" != "1" ]]; then
  echo "==> init MySQL (sql/create.sql)"
  docker exec -i forum-mysql mysql -uroot -p"${root_pw}" < sql/create.sql
  if [[ -f sql/postgres_ai_session.sql ]]; then
    pu="$(read_env POSTGRES_USER)"; pd="$(read_env POSTGRES_DB)"
    pu="${pu:-langgraph}"; pd="${pd:-langgraph_db}"
    docker exec -i forum-postgres psql -U "${pu}" -d "${pd}" < sql/postgres_ai_session.sql || true
  fi
fi

echo "==> compose application"
$COMPOSE up -d --force-recreate --no-deps ai-server backend-1 nginx

echo "--- middleware ---"
$COMPOSE ps
sleep 15
curl -sf http://127.0.0.1/healthz && echo " healthz OK" || echo " healthz FAIL (see: docker logs forum-backend-1 --tail 50)"
echo ""
echo "Re-init DB only: SKIP_DB_INIT=0 bash start.sh   or: bash reset-db.sh"
'@
Write-UnixShellFile -Path (Join-Path $pkg "start.sh") -Content $startSh

$resetDbSh = @'
#!/bin/bash
# 初始化 MySQL/Postgres 表结构（勿用 source .env，避免 JAVA_TOOL_OPTIONS 等含空格变量报错）
set -euo pipefail
cd "$(dirname "$0")"
sed -i 's/\r$//' .env 2>/dev/null || true

read_env() {
  local k="$1" line v
  line="$(grep -E "^${k}=" .env 2>/dev/null | tail -1 || true)"
  v="${line#*=}"
  v="${v//$'\r'/}"
  v="${v%\"}"; v="${v#\"}"
  printf '%s' "$v"
}

MYSQL_ROOT_PASSWORD="$(read_env MYSQL_ROOT_PASSWORD)"
POSTGRES_USER="$(read_env POSTGRES_USER)"
POSTGRES_PASSWORD="$(read_env POSTGRES_PASSWORD)"
POSTGRES_DB="$(read_env POSTGRES_DB)"
POSTGRES_USER="${POSTGRES_USER:-langgraph}"
POSTGRES_DB="${POSTGRES_DB:-langgraph_db}"

echo "Waiting for MySQL..."
for _ in $(seq 1 90); do
  if docker exec forum-mysql mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent 2>/dev/null; then
    break
  fi
  sleep 2
done

echo "==> MySQL create.sql (DROP + CREATE forum_db)"
docker exec -i forum-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < sql/create.sql

if [[ -f sql/postgres_ai_session.sql ]]; then
  echo "==> Postgres postgres_ai_session.sql"
  export PGPASSWORD="${POSTGRES_PASSWORD}"
  docker exec -i forum-postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" < sql/postgres_ai_session.sql
fi

echo "Database init done. Restart backend: docker compose -f docker-compose.yaml -f docker-compose.prod.yml restart backend-1 nginx"
'@
Write-UnixShellFile -Path (Join-Path $pkg "reset-db.sh") -Content $resetDbSh

$collectLogsSh = @'
#!/bin/bash
# 采集线上排错日志（在服务器 ~/package 执行：bash collect-logs.sh）
set -euo pipefail
cd "$(dirname "$0")"
COMPOSE="docker compose -f docker-compose.yaml -f docker-compose.prod.yml"
LOG_ROOT="${FORUM_LOG_DIR:-../logs}"
mkdir -p "$LOG_ROOT"
OUT="$LOG_ROOT/logs-collect-$(date +%Y%m%d-%H%M%S).txt"
{
  echo "=== $(date -Iseconds) compose ps ==="
  $COMPOSE ps -a
  echo ""
  echo "=== forum-backend-1 (last 120) ==="
  docker logs forum-backend-1 --tail 120 2>&1 || true
  echo ""
  echo "=== forum-ai-server (last 120, AI) ==="
  docker logs forum-ai-server --tail 120 2>&1 | grep -i -E 'error|exception|traceback' || docker logs forum-ai-server --tail 120 2>&1 || true
  echo ""
  echo "=== forum-ffmpeg (last 60) ==="
  docker logs forum-ffmpeg --tail 60 2>&1 || true
  echo ""
  echo "=== forum-nginx (last 40) ==="
  docker logs forum-nginx --tail 40 2>&1 || true
  echo ""
} > "$OUT"
echo "Wrote $OUT"
'@
Write-UnixShellFile -Path (Join-Path $pkg "collect-logs.sh") -Content $collectLogsSh

$deployTxt = @'
================================================================================
  萌萌论坛 — 服务器部署清单（只上传本 package/ 目录到 ~/package）
================================================================================

【A】服务器 — 停旧栈并删数据卷（要重建库表时必做）
  cd ~/package
  docker compose -f docker-compose.yaml -f docker-compose.prod.yml down -v
  # 日志位于 ../logs，不随 package 替换或 down -v 删除。
  # 若整包替换：可先备份 ssl/ 与 .env

【B】本机 — 打包（PowerShell）
  cd <仓库>\nginx
  # 确认 nginx\.env 已是生产配置（会复制进 package\.env）
  .\scripts\make-package.ps1

  自检通过后上传整个 package/ 到服务器 ~/package/（WinSCP/rsync）

【C】服务器 — 启动（.env 若有 Windows 换行必须先 sed）
  cd ~/package
  sed -i 's/\r$//' .env start.sh up.sh verify-frontend-dist.sh reset-db.sh
  chmod +x start.sh up.sh verify-frontend-dist.sh reset-db.sh

  首次部署 / 要初始化库：  bash start.sh
  仅更新包后重启（推荐）： bash up.sh
  勿单独用： docker compose up -d --build
    （不会 docker load 离线镜像，也不会 chmod dist，易导致前端 403）

【D】服务器 — 初始化 MySQL + Postgres 表（空库 / 重建）
  bash reset-db.sh

【E】验证
  docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps
  curl -s http://127.0.0.1/healthz
  浏览器访问 https://你的域名

【F】线上排错（勿用 docker-compose.dev.yaml，package 内没有该文件）
  bash collect-logs.sh
  # 把 ../logs/logs-collect-*.txt 发给开发排查

镜像均在 images/*.tar 内，含 forum-ffmpeg，勿依赖 docker pull。

Navicat：SSH 隧道 127.0.0.1 → 33061/63790/54320，账号见 .env
================================================================================
'@
Write-UnixShellFile -Path (Join-Path $pkg "DEPLOY.txt") -Content $deployTxt

Write-UnixShellFile -Path (Join-Path $pkg "README.txt") -Content "See DEPLOY.txt in this folder."

Write-Host ""
Write-Host "Package ready (upload ONLY):" -ForegroundColor Green
Write-Host "  $pkg" -ForegroundColor Green
Write-Host "  Contains: dist/ conf.d/ ssl/ images/*.tar ffmpeg/ sql/ .env DEPLOY.txt" -ForegroundColor Green
Write-Host ""
