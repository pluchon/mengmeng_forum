# Export images + assemble an external package (classic dist bind mount on server)
# Usage: cd deploy; .\scripts\export-images.ps1

param([string]$OutputRoot = "C:\forum-build\luntan-package")

$ErrorActionPreference = "Stop"
$deployRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $deployRoot
$pkg = [System.IO.Path]::GetFullPath($OutputRoot)
$outDir = Join-Path $pkg "images"
$packageMarker = Join-Path $pkg ".forum-package-root"

$pathRoot = [System.IO.Path]::GetPathRoot($pkg).TrimEnd('\')
$repoFullPath = [System.IO.Path]::GetFullPath($repoRoot).TrimEnd('\')
$pkgTrimmed = $pkg.TrimEnd('\')
if ($pkgTrimmed -eq $pathRoot -or $pkgTrimmed -eq $repoFullPath -or $pkgTrimmed.StartsWith($repoFullPath + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing destructive package cleanup at unsafe path: $pkg"
}

& (Join-Path $PSScriptRoot "test-production-tls.ps1") `
    -SslRoot (Join-Path $deployRoot "ssl") `
    -NginxConfig (Join-Path $deployRoot "conf.d\20-prod-https.conf")
if (-not $?) { throw "Production domain and TLS verification failed" }

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

if (Test-Path $pkg) {
    $recognizedPackage = (Test-Path -LiteralPath $packageMarker) -or
        ((Test-Path -LiteralPath (Join-Path $pkg "DEPLOY.txt")) -and (Test-Path -LiteralPath (Join-Path $pkg "images")))
    if (-not $recognizedPackage) { throw "Refusing to replace an unrecognized existing directory: $pkg" }
    Remove-Item -LiteralPath $pkg -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Set-Content -LiteralPath $packageMarker -Value "forum deployment package root" -Encoding utf8NoBOM

Require-Path (Join-Path $deployRoot "dist\user\index.html") "front dist index.html"
Require-Path (Join-Path $deployRoot "conf.d") "nginx conf.d"

$userIndex = Join-Path $deployRoot "dist\user\index.html"
$indexHtml = Get-Content -Raw $userIndex
if ($indexHtml -notmatch '/assets/.*\.js') {
    throw "dist/user/index.html missing /assets/*.js - run npm run build in forum-vue/front"
}
$assetsDir = Join-Path $deployRoot "dist\user\assets"
if (-not (Test-Path $assetsDir)) { throw "Missing dist/user/assets" }

$images = @(
    "forum-backend:latest",
    "forum-ai-server:latest",
    "forum-ffmpeg:latest",
    "nginx:1.30.1",
    "mysql:9.7.0",
    "redis:8.0",
    "rabbitmq:4.3-management",
    "postgres:17",
    "nacos/nacos-server:v3.1.1"
)

Write-Host "Checking / pulling images ..."
foreach ($img in $images) { Ensure-DockerImage $img }

Write-Host "Saving images to $outDir ..."
docker save -o (Join-Path $outDir "forum-backend.tar") forum-backend:latest
if ($LASTEXITCODE -ne 0) { throw "docker save failed: forum-backend:latest" }

docker save -o (Join-Path $outDir "forum-ai-server.tar") forum-ai-server:latest
if ($LASTEXITCODE -ne 0) { throw "docker save failed: forum-ai-server:latest" }

docker save -o (Join-Path $outDir "infra.tar") nginx:1.30.1 mysql:9.7.0 redis:8.0 rabbitmq:4.3-management postgres:17 nacos/nacos-server:v3.1.1 forum-ffmpeg:latest
if ($LASTEXITCODE -ne 0) { throw "docker save failed: infra.tar" }

$ffmpegDir = Join-Path $deployRoot "ffmpeg"
$pkgFfmpeg = Join-Path $pkg "ffmpeg"
if (Test-Path $ffmpegDir) {
    if (Test-Path $pkgFfmpeg) { Remove-Item -Recurse -Force $pkgFfmpeg }
    Copy-Item -Recurse -Force $ffmpegDir $pkgFfmpeg
    Normalize-UnixLf (Join-Path $pkgFfmpeg "server.py")
}

foreach ($dir in @("dist", "conf.d")) {
    $src = Join-Path $deployRoot $dir
    $dst = Join-Path $pkg $dir
    if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
    Copy-Item -Recurse -Force $src $dst
}

$sslDst = Join-Path $pkg "ssl"
New-Item -ItemType Directory -Force -Path $sslDst | Out-Null
Copy-Item -LiteralPath (Join-Path $deployRoot "ssl\www.example.com.pem") -Destination $sslDst -Force
Copy-Item -LiteralPath (Join-Path $deployRoot "ssl\www.example.com.key") -Destination $sslDst -Force

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
$initDbSh = Join-Path $scriptsDir "init-db.sh"
if (-not (Test-Path $initDbSh)) {
    throw "Missing first-time DB init script: $initDbSh"
}
Copy-Item $initDbSh (Join-Path $pkg "init-db.sh") -Force
Normalize-UnixLf (Join-Path $pkg "init-db.sh")

Copy-Item (Join-Path $deployRoot "docker-compose.yaml") $pkg -Force
Copy-Item (Join-Path $deployRoot "docker-compose.prod.yml") $pkg -Force
if (Test-Path (Join-Path $deployRoot ".env.example")) {
    Copy-Item (Join-Path $deployRoot ".env.example") (Join-Path $pkg ".env.example") -Force
}
$syncNacosSh = Join-Path $scriptsDir "sync-nacos.sh"
if (-not (Test-Path $syncNacosSh)) {
    throw "Missing Nacos synchronization script: $syncNacosSh"
}
Copy-Item $syncNacosSh (Join-Path $pkg "sync-nacos.sh") -Force
Normalize-UnixLf (Join-Path $pkg "sync-nacos.sh")
$nacosConfigSource = Join-Path $deployRoot "nacos-config"
if (-not (Test-Path $nacosConfigSource)) {
    throw "Missing versioned Nacos configs: $nacosConfigSource"
}
Copy-Item $nacosConfigSource (Join-Path $pkg "nacos-config") -Recurse -Force
Write-Host "Secrets are excluded; server startup requires FORUM_ENV_FILE=/opt/forum-config/prod.env" -ForegroundColor Yellow
$sqlDst = Join-Path $pkg "sql"
New-Item -ItemType Directory -Force -Path $sqlDst | Out-Null
foreach ($domain in @("auth", "content", "im", "game", "economy", "ai")) {
    $createSql = Join-Path $repoRoot "java-cloud-standalone\$domain\server\src\main\resources\db\create.sql"
    if (-not (Test-Path $createSql)) {
        throw "Missing complete schema: $createSql"
    }
    Copy-Item $createSql (Join-Path $sqlDst "$domain-create.sql") -Force
}
$postgresSql = Join-Path $repoRoot "java-cloud-standalone\ai\server\src\main\resources\sql\postgres_ai_session.sql"
if (-not (Test-Path $postgresSql)) {
    throw "Missing PostgreSQL migration: $postgresSql"
}
Copy-Item $postgresSql (Join-Path $sqlDst "postgres_ai_session.sql") -Force
$startSh = @'
#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
test -f manifest.sha256 || { echo "ERROR: missing manifest.sha256; upload the complete package"; exit 1; }
sha256sum -c manifest.sha256
ENV_FILE="${FORUM_ENV_FILE:-/opt/forum-config/prod.env}"
COMPOSE="docker compose --env-file ${ENV_FILE} -f docker-compose.yaml -f docker-compose.prod.yml"
LOG_ROOT="${FORUM_LOG_DIR:-../logs}"
echo "Forum package release: $(cat RELEASE.txt 2>/dev/null || echo unknown)"

fix_crlf() {
  for f in start.sh up.sh init-db.sh verify-frontend-dist.sh reset-db.sh sync-nacos.sh; do
    # 末尾的 || true 不可省：循环最后一轮若文件不存在，[[ -f ]] && 会让函数返回 1，
    # 在 set -e 下整个脚本会不打印任何信息就退出。
    [[ -f "$f" ]] && sed -i 's/\r$//' "$f" || true
  done
}
fix_crlf

mkdir -p "$LOG_ROOT"/java-backend "$LOG_ROOT"/ai-server "$LOG_ROOT"/ffmpeg "$LOG_ROOT"/nginx
test -f "$ENV_FILE" || { echo "ERROR: missing external environment file: $ENV_FILE"; exit 1; }
if grep -q 'CHANGE_ME' "$ENV_FILE"; then
  echo "WARN: external environment file still has CHANGE_ME placeholders"
fi
for tar in images/forum-backend.tar images/forum-ai-server.tar images/infra.tar; do
  test -f "$tar" || { echo "ERROR: missing $tar"; exit 1; }
done

if [[ -f ./verify-frontend-dist.sh ]]; then
  chmod +x ./verify-frontend-dist.sh
  ./verify-frontend-dist.sh .
fi
if ! chmod -R a+rX dist conf.d ssl; then
  echo "WARN: unable to normalize one or more static-file permissions"
fi
# 运行中的容器可能拥有日志文件；启动脚本只保证目录可进入，不递归改写现有日志归属。
for log_dir in "$LOG_ROOT" "$LOG_ROOT"/java-backend "$LOG_ROOT"/ai-server "$LOG_ROOT"/ffmpeg "$LOG_ROOT"/nginx; do
  if ! chmod u+rwx "$log_dir"; then
    echo "WARN: unable to update log-directory permissions: $log_dir"
  fi
done

echo "==> docker load"
docker load -i images/forum-backend.tar
docker load -i images/forum-ai-server.tar
docker load -i images/infra.tar
# 必须覆盖 infra.tar 里的中间件镜像：离线服务器无法回退到 docker pull
for img in forum-backend:latest forum-ai-server:latest forum-ffmpeg:latest \
           nginx:1.30.1 nacos/nacos-server:v3.1.1 \
           mysql:9.7.0 redis:8.0 rabbitmq:4.3-management postgres:17; do
  docker image inspect "$img" >/dev/null 2>&1 || { echo "ERROR: image missing after load: $img"; exit 1; }
done
docker run --rm --user 0 \
  -v "$(cd "$LOG_ROOT/java-backend" && pwd):/app/logs" \
  --entrypoint /bin/sh forum-backend:latest \
  -c 'chown -R 1000:1000 /app/logs'

read_env() {
  local k="$1" line v
  line="$(grep -E "^${k}=" "$ENV_FILE" | tail -1 || true)"
  v="${line#*=}"
  v="${v//$'\r'/}"
  v="${v%\"}"; v="${v#\"}"
  printf '%s' "$v"
}

wait_mysql() {
  local root_pw="$1" i
  for i in $(seq 1 90); do
    if docker exec -e MYSQL_PWD="${root_pw}" forum-mysql mysql -h 127.0.0.1 -uroot -Nse "SELECT 1" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "ERROR: MySQL did not pass authenticated SELECT 1" >&2
  # 再跑一次把原始 stderr 打出来，便于区分“连不上”与“凭据错”
  docker exec -e MYSQL_PWD="${root_pw}" forum-mysql mysql -h 127.0.0.1 -uroot -Nse "SELECT 1" >&2 || true
  docker logs --tail 50 forum-mysql >&2 || true
  return 1
}

sql_escape() {
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e "s/'/''/g"
}

ensure_domain_users() {
  local root_pw="$1" domain upper user password escaped_user escaped_password
  for domain in auth content im game economy ai; do
    upper="$(printf '%s' "$domain" | tr '[:lower:]' '[:upper:]')"
    user="$(read_env "FORUM_${upper}_DB_USERNAME")"
    password="$(read_env "FORUM_${upper}_DB_PASSWORD")"
    [[ -n "$user" && -n "$password" ]] || {
      echo "ERROR: missing FORUM_${upper}_DB_USERNAME or FORUM_${upper}_DB_PASSWORD in $ENV_FILE"
      return 1
    }
    escaped_user="$(sql_escape "$user")"
    escaped_password="$(sql_escape "$password")"
    docker exec -e MYSQL_PWD="${root_pw}" -i forum-mysql mysql -uroot --default-character-set=utf8mb4 <<SQL
CREATE USER IF NOT EXISTS '${escaped_user}'@'%' IDENTIFIED BY '${escaped_password}';
ALTER USER '${escaped_user}'@'%' IDENTIFIED BY '${escaped_password}';
GRANT ALL PRIVILEGES ON \`forum_${domain}_db\`.* TO '${escaped_user}'@'%';
SQL
  done
  docker exec -e MYSQL_PWD="${root_pw}" forum-mysql mysql -uroot -e "FLUSH PRIVILEGES;" >/dev/null
}

# 表数量校验统一由 init-db.sh 负责，这里不再保留副本：
# 副本会各自持有一份表数量常量，下线表时必然漂移出不一致。

wait_postgres() {
  local user="$1" database="$2" i
  for i in $(seq 1 90); do
    if docker exec forum-postgres pg_isready -U "$user" -d "$database" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "ERROR: PostgreSQL not ready"; return 1
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
$COMPOSE up -d mysql redis rabbitmq postgres ffmpeg nacos
sync_rabbitmq_credentials
bash sync-nacos.sh "$ENV_FILE" nacos-config forum-nacos

root_pw="$(read_env MYSQL_ROOT_PASSWORD)"
wait_mysql "$root_pw"

if [[ "${SKIP_DB_INIT:-0}" != "1" ]]; then
  chmod +x ./init-db.sh
  FORUM_ENV_FILE="$ENV_FILE" FORUM_SQL_DIR=./sql bash ./init-db.sh
else
  # 跳过建表可以，但六域账号是应用连库的前提，漏建会表现成一堆 Access denied。
  echo "SKIP_DB_INIT=1：跳过建表，仍需确保六域数据库账号存在"
  ensure_domain_users "$root_pw"
fi

echo "==> compose application"
$COMPOSE up -d --force-recreate --no-deps ai-server auth content im game economy ai gateway nginx

echo "--- middleware ---"
$COMPOSE ps

# 固定 sleep 加 “&& echo || echo” 会让脚本在站点 502 时依然退出 0，
# 首次部署必须以真实探测结果决定退出码。
healthz_ok=0
for i in $(seq 1 60); do
  code="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/healthz || echo 000)"
  if [[ "$code" == "200" ]]; then
    echo "healthz OK (HTTP 200, ${i} 次探测)"
    healthz_ok=1
    break
  fi
  sleep 2
done
if [[ "$healthz_ok" != "1" ]]; then
  echo "ERROR: healthz 未就绪，最后一次 HTTP ${code}（已等待 120 秒）" >&2
  $COMPOSE ps >&2 || true
  for c in forum-nginx forum-gateway; do
    echo "--- docker logs $c --tail 50 ---" >&2
    docker logs --tail 50 "$c" >&2 || true
  done
  exit 1
fi
echo ""
echo "For an intentionally destructive rebuild, read DEPLOY.txt and use all reset confirmations."
'@
Write-UnixShellFile -Path (Join-Path $pkg "start.sh") -Content $startSh

$resetDbSh = @'
#!/bin/bash
# 显式销毁并重建 MySQL/PostgreSQL 结构（勿用 source env 文件，避免含空格变量报错）
set -euo pipefail
cd "$(dirname "$0")"
ENV_FILE="${FORUM_ENV_FILE:-/opt/forum-config/prod.env}"
test -f "$ENV_FILE" || { echo "ERROR: missing external environment file: $ENV_FILE"; exit 1; }

if [[ "${CONFIRM_RESET_DB:-}" != "1" || "${CONFIRM_MYSQL_DATA_LOSS:-}" != "YES" || "${CONFIRM_POSTGRES_DATA_LOSS:-}" != "YES" ]]; then
  echo "Refusing destructive database reset. This deletes all six MySQL databases and the PostgreSQL public schema."
  echo "Run only after a verified backup: CONFIRM_RESET_DB=1 CONFIRM_MYSQL_DATA_LOSS=YES CONFIRM_POSTGRES_DATA_LOSS=YES bash reset-db.sh"
  exit 2
fi

read_env() {
  local k="$1" line v
  line="$(grep -E "^${k}=" "$ENV_FILE" | tail -1 || true)"
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
  if docker exec -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" forum-mysql mysql -h 127.0.0.1 -uroot -Nse "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

docker exec -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" forum-mysql mysql -h 127.0.0.1 -uroot -Nse "SELECT 1" >/dev/null

for domain in auth content im game economy ai; do
  echo "==> DROP DATABASE forum_${domain}_db"
  docker exec -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" forum-mysql mysql -uroot -e "DROP DATABASE IF EXISTS \`forum_${domain}_db\`;"
done

for domain in auth content im game economy ai; do
  schema="sql/${domain}-create.sql"
  echo "==> MySQL ${schema}"
  docker exec -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" -i forum-mysql mysql -uroot --default-character-set=utf8mb4 < "${schema}"
done

if [[ -f sql/postgres_ai_session.sql ]]; then
  echo "Waiting for PostgreSQL..."
  for _ in $(seq 1 90); do
    if docker exec forum-postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done
  docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" forum-postgres psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -At -c "SELECT 1" >/dev/null
  echo "==> DROP PostgreSQL public schema"
  docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" forum-postgres psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
  echo "==> Postgres postgres_ai_session.sql"
  docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" -i forum-postgres psql -v ON_ERROR_STOP=1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" < sql/postgres_ai_session.sql
fi

echo "Destructive database reset done. Restart backend with: FORUM_ENV_FILE=$ENV_FILE bash up.sh"
'@
Write-UnixShellFile -Path (Join-Path $pkg "reset-db.sh") -Content $resetDbSh

$collectLogsSh = @'
#!/bin/bash
# 采集线上排错日志（在服务器 ~/package 执行：bash collect-logs.sh）
set -euo pipefail
cd "$(dirname "$0")"
ENV_FILE="${FORUM_ENV_FILE:-/opt/forum-config/prod.env}"
COMPOSE="docker compose --env-file ${ENV_FILE} -f docker-compose.yaml -f docker-compose.prod.yml"
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

【A】服务器 — 普通更新不要删除数据卷
  cd ~/package
  # 直接执行 C 节的 up.sh；脚本会加载离线镜像并重建应用容器。
  # 如需临时停止整栈，只能使用 down（禁止附加 -v）：
  # docker compose -f docker-compose.yaml -f docker-compose.prod.yml down
  # 日志位于 ../logs（java-backend / ai-server / ffmpeg / nginx），
  # 不随 package 替换或普通 down 删除。
  # 整包替换不会携带或覆盖 /opt/forum-config/prod.env

【B】本机 — 打包（PowerShell）
  cd <仓库>\deploy
  .\scripts\make-package.ps1

  自检通过后上传整个 package/ 到服务器 ~/package/（WinSCP/rsync）

【C】服务器 — 启动（生产密钥固定放在包目录之外）
  cd ~/package
  test -f /opt/forum-config/prod.env
  sha256sum -c manifest.sha256
  chmod +x start.sh up.sh init-db.sh verify-frontend-dist.sh reset-db.sh sync-nacos.sh

  首次部署（空库建表+起服务）： FORUM_ENV_FILE=/opt/forum-config/prod.env bash start.sh
  仅更新包后重启： FORUM_ENV_FILE=/opt/forum-config/prod.env bash up.sh
  只建库不启应用： FORUM_ENV_FILE=/opt/forum-config/prod.env bash init-db.sh
  勿单独用 docker compose up -d --build（不会 load 离线镜像）

【D】销毁并重建库（清空全部业务数据）
  CONFIRM_RESET_DB=1 CONFIRM_MYSQL_DATA_LOSS=YES CONFIRM_POSTGRES_DATA_LOSS=YES bash reset-db.sh

【E】已有数据的线上库
  只能用审核过的前向迁移改表；禁止对有数据的库跑 init-db / reset-db。

【F】验证
  docker compose -f docker-compose.yaml -f docker-compose.prod.yml ps
  curl -s http://127.0.0.1/healthz
  浏览器访问 https://你的域名

【G】线上排错（勿用 docker-compose.dev.yaml，package 内没有该文件）
  bash collect-logs.sh
  # 把 ../logs/logs-collect-*.txt 发给开发排查

镜像均在 images/*.tar 内，含 forum-ffmpeg，勿依赖 docker pull。

Navicat：SSH 隧道 127.0.0.1 → 33061/63790/54320，账号见外部 prod.env
================================================================================
'@
Write-UnixShellFile -Path (Join-Path $pkg "DEPLOY.txt") -Content $deployTxt

Write-UnixShellFile -Path (Join-Path $pkg "README.txt") -Content "See DEPLOY.txt in this folder."

# 先看退出码再取值：git 失败时返回 $null，直接 .Trim() 会抛“不能对空值调用方法”，
# 把“仓库里没有提交”掩盖成一个看不懂的 PowerShell 错误。
$gitCommitRaw = & git -C $repoRoot rev-parse --short=12 HEAD 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "无法解析发布用的 git commit（git 退出码 $LASTEXITCODE）：$gitCommitRaw"
}
$gitCommit = ($gitCommitRaw | Select-Object -Last 1 | Out-String).Trim()
if (-not $gitCommit) { throw "无法解析发布用的 git commit：git 返回空值" }
$releaseId = "{0}-{1}" -f (Get-Date -Format "yyyyMMdd-HHmmss"), $gitCommit
Set-Content -LiteralPath (Join-Path $pkg "RELEASE.txt") -Value $releaseId -Encoding utf8NoBOM

$manifestPath = Join-Path $pkg "manifest.sha256"
$manifestLines = Get-ChildItem -LiteralPath $pkg -Recurse -File |
    Where-Object { $_.FullName -ne $manifestPath } |
    Sort-Object FullName |
    ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($pkg, $_.FullName).Replace('\', '/')
        "{0}  {1}" -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $relative
    }
$manifestContent = ($manifestLines -join "`n") + "`n"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($manifestPath, $manifestContent, $utf8NoBom)

Write-Host ""
Write-Host "Package ready (upload ONLY):" -ForegroundColor Green
Write-Host "  $pkg" -ForegroundColor Green
Write-Host "  Contains: dist/ conf.d/ ssl/ images/*.tar ffmpeg/ sql/ .env.example DEPLOY.txt" -ForegroundColor Green
Write-Host ""
