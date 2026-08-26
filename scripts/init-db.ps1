# 本地首次建库：对 forum-mysql-dev / forum-postgres-dev 执行六域 create.sql
# 用法（仓库根目录）:
#   .\scripts\init-db.ps1
# 需要先起中间件：.\scripts\dev-up.ps1
# 需要 Windows 用户环境变量里的 MYSQL_ROOT_PASSWORD 与六域账号

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$userEnvironment = [Environment]::GetEnvironmentVariables("User")
foreach ($key in $userEnvironment.Keys) {
    if ([string]$key -ieq "Path") { continue }
    [Environment]::SetEnvironmentVariable([string]$key, [string]$userEnvironment[$key], "Process")
}

function Require-Env([string]$name) {
    $v = [Environment]::GetEnvironmentVariable($name, "Process")
    if ([string]::IsNullOrWhiteSpace($v)) { throw "缺少环境变量: $name" }
    return $v
}

$rootPw = Require-Env "MYSQL_ROOT_PASSWORD"
$mysql = "forum-mysql-dev"
$postgres = "forum-postgres-dev"
$expected = @{ auth = 11; content = 30; im = 14; game = 12; economy = 38; ai = 17 }

$mysqlState = docker inspect -f "{{.State.Running}}" $mysql 2>$null
if ($mysqlState -ne "true") { throw "容器未 Running: $mysql ，先执行 deploy\scripts\dev-compose.ps1" }

$ready = $false
for ($i = 1; $i -le 90; $i++) {
    docker exec -e "MYSQL_PWD=$rootPw" $mysql mysql -h 127.0.0.1 -uroot -Nse "SELECT 1" 1>$null 2>$null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) { throw "MySQL 未通过 SELECT 1 校验" }

function Get-TableCount([string]$domain) {
    $sql = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='forum_${domain}_db' AND TABLE_TYPE='BASE TABLE';"
    $out = docker exec -e "MYSQL_PWD=$rootPw" $mysql mysql -uroot -N -B -e $sql
    if ($LASTEXITCODE -ne 0) { throw "查询 forum_${domain}_db 表数量失败: $out" }
    return ([string]$out).Trim()
}

$empty = 0
$valid = 0
foreach ($domain in @("auth", "content", "im", "game", "economy", "ai")) {
    $actual = Get-TableCount $domain
    if ($actual -eq "0") { $empty++ }
    elseif ($actual -eq [string]$expected[$domain]) { $valid++ }
    else { throw "forum_${domain}_db 现有 $actual 张表，基线期望 $($expected[$domain])，拒绝覆盖" }
}

if ($empty -eq 6) {
    foreach ($domain in @("auth", "content", "im", "game", "economy", "ai")) {
        $schema = Join-Path $repoRoot "java-cloud-standalone\$domain\server\src\main\resources\db\create.sql"
        if (-not (Test-Path $schema)) { throw "缺少 $schema" }
        Write-Host "==> $domain create.sql"
        Get-Content -LiteralPath $schema -Raw -Encoding UTF8 | docker exec -e "MYSQL_PWD=$rootPw" -i $mysql mysql -uroot --default-character-set=utf8mb4
        if ($LASTEXITCODE -ne 0) { throw "导入 $domain 失败" }
    }
} elseif ($valid -eq 6) {
    Write-Host "六域库已与基线一致，跳过 MySQL 导入"
} else {
    throw "六域库部分为空、部分已建，请先手工理清后再初始化"
}

foreach ($domain in @("auth", "content", "im", "game", "economy", "ai")) {
    $upper = $domain.ToUpperInvariant()
    $user = Require-Env "FORUM_${upper}_DB_USERNAME"
    $pass = Require-Env "FORUM_${upper}_DB_PASSWORD"
    $userEsc = $user.Replace("\", "\\").Replace("'", "''")
    $passEsc = $pass.Replace("\", "\\").Replace("'", "''")
    $grant = @"
CREATE USER IF NOT EXISTS '${userEsc}'@'%' IDENTIFIED BY '${passEsc}';
ALTER USER '${userEsc}'@'%' IDENTIFIED BY '${passEsc}';
GRANT ALL PRIVILEGES ON ``forum_${domain}_db``.* TO '${userEsc}'@'%';
"@
    $grant | docker exec -e "MYSQL_PWD=$rootPw" -i $mysql mysql -uroot --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw "创建账号失败: $domain" }
}
docker exec -e "MYSQL_PWD=$rootPw" $mysql mysql -uroot -e "FLUSH PRIVILEGES;" | Out-Null

foreach ($domain in @("auth", "content", "im", "game", "economy", "ai")) {
    $actual = Get-TableCount $domain
    Write-Host "forum_${domain}_db tables=$actual expected=$($expected[$domain])"
    if ($actual -ne [string]$expected[$domain]) { throw "表数量校验失败: $domain" }
}

$pgState = docker inspect -f "{{.State.Running}}" $postgres 2>$null
if ($pgState -eq "true") {
    $pu = [Environment]::GetEnvironmentVariable("POSTGRES_USER", "Process")
    if ([string]::IsNullOrWhiteSpace($pu)) { $pu = "langgraph" }
    $pd = [Environment]::GetEnvironmentVariable("POSTGRES_DB", "Process")
    if ([string]::IsNullOrWhiteSpace($pd)) { $pd = "langgraph_db" }
    $pgSql = Join-Path $repoRoot "java-cloud-standalone\ai\server\src\main\resources\sql\postgres_ai_session.sql"
    if (Test-Path $pgSql) {
        Write-Host "==> postgres_ai_session.sql"
        Get-Content -LiteralPath $pgSql -Raw -Encoding UTF8 | docker exec -i $postgres psql -v ON_ERROR_STOP=1 -U $pu -d $pd
        if ($LASTEXITCODE -ne 0) { throw "PostgreSQL 初始化失败" }
    }
}

Write-Host "本地 init-db 完成" -ForegroundColor Green
