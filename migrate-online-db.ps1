# =============================================================================
# 线上 / 本地：MySQL 拆库迁移脚本（PowerShell）
#
# 用法（仓库根目录）：
#   .\migrate-online-db.ps1 users
#   $env:CONFIRM_DROP='YES'; .\migrate-online-db.ps1 init
#   .\migrate-online-db.ps1 status
#
# 数据迁移（forum_db -> 六域库）请用 Git Bash / Linux：
#   MYSQL_CONTAINER=forum-mysql bash migrate-online-db.sh migrate-data
#
# 默认连接本地开发容器 forum-mysql-dev；生产：
#   $env:MYSQL_CONTAINER='forum-mysql'
# =============================================================================
param(
    [ValidateSet("users", "init", "migrate-data", "drop-forum-db", "status")]
    [string]$Action = "users"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

if (-not $env:MYSQL_CONTAINER) { $env:MYSQL_CONTAINER = "forum-mysql-dev" }
if (-not $env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD = "123456789" }

# 优先用 Git Bash（能访问 Windows Docker）；跳过 WSL 的 System32\bash.exe
function Get-GitBash {
    $candidates = @(
        "C:\Program Files\Git\bin\bash.exe",
        "C:\Program Files (x86)\Git\bin\bash.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    $cmd = Get-Command bash.exe -ErrorAction SilentlyContinue
    if ($null -ne $cmd -and $cmd.Source -notmatch '\\System32\\|WindowsApps\\') {
        return $cmd.Source
    }
    return $null
}

$gitBash = Get-GitBash
if ($null -ne $gitBash -and $Action -eq "migrate-data") {
    $env:MYSQL_CONTAINER = $env:MYSQL_CONTAINER
    $env:MYSQL_ROOT_PASSWORD = $env:MYSQL_ROOT_PASSWORD
    & $gitBash (Join-Path $Root "migrate-online-db.sh") $Action
    if ($LASTEXITCODE -ne 0) { throw "migrate-online-db.sh failed: $LASTEXITCODE" }
    exit 0
}

function Invoke-MysqlSql {
    param([string]$Sql)
    $Sql | docker exec -i $env:MYSQL_CONTAINER mysql -uroot "-p$($env:MYSQL_ROOT_PASSWORD)" --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw "mysql exec failed" }
}

function Invoke-MysqlFile {
    param([string]$Path)
    Get-Content -Raw -Encoding UTF8 $Path | docker exec -i $env:MYSQL_CONTAINER mysql -uroot "-p$($env:MYSQL_ROOT_PASSWORD)" --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { throw "mysql file exec failed: $Path" }
}

$domains = @("auth", "content", "im", "game", "economy", "ai")
$users = @{
    auth     = @{ User = "forum_auth";     Pass = "forum_auth_pass";     Db = "forum_auth_db" }
    content  = @{ User = "forum_content";  Pass = "forum_content_pass";  Db = "forum_content_db" }
    im       = @{ User = "forum_im";       Pass = "forum_im_pass";       Db = "forum_im_db" }
    game     = @{ User = "forum_game";     Pass = "forum_game_pass";     Db = "forum_game_db" }
    economy  = @{ User = "forum_economy";  Pass = "forum_economy_pass";  Db = "forum_economy_db" }
    ai       = @{ User = "forum_ai";       Pass = "forum_ai_pass";       Db = "forum_ai_db" }
}

function Ensure-Users {
    $parts = New-Object System.Collections.Generic.List[string]
    foreach ($d in $domains) {
        $u = $users[$d]
        $parts.Add("CREATE DATABASE IF NOT EXISTS ``$($u.Db)`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
        $parts.Add("CREATE USER IF NOT EXISTS '$($u.User)'@'%' IDENTIFIED BY '$($u.Pass)';")
        $parts.Add("ALTER USER '$($u.User)'@'%' IDENTIFIED BY '$($u.Pass)';")
        $parts.Add("GRANT ALL PRIVILEGES ON ``$($u.Db)``.* TO '$($u.User)'@'%';")
    }
    $parts.Add("FLUSH PRIVILEGES;")
    Write-Host "==> create domain users + grants"
    Invoke-MysqlSql ($parts -join "`n")
}

switch ($Action) {
    "users" { Ensure-Users }
    "init" {
        if ($env:CONFIRM_DROP -ne "YES") {
            throw "init drops databases. Set `$env:CONFIRM_DROP='YES' first."
        }
        foreach ($d in $domains) {
            $sql = Join-Path $Root "java-cloud-standalone\$d\server\src\main\resources\db\create.sql"
            if (-not (Test-Path $sql)) { throw "Missing $sql" }
            Write-Host "==> apply $sql"
            Invoke-MysqlFile $sql
        }
        Ensure-Users
    }
    "migrate-data" {
        throw @"
migrate-data 需要 Git Bash 或 Linux 执行：
  MYSQL_CONTAINER=$($env:MYSQL_CONTAINER) MYSQL_ROOT_PASSWORD=*** bash migrate-online-db.sh migrate-data
若要从旧 forum_db 搬完后删除：
  CONFIRM_DROP_FORUM_DB=YES MYSQL_CONTAINER=$($env:MYSQL_CONTAINER) bash migrate-online-db.sh migrate-data
"@
    }
    "drop-forum-db" {
        if ($env:CONFIRM_DROP_FORUM_DB -ne "YES") {
            throw "Set `$env:CONFIRM_DROP_FORUM_DB='YES' first."
        }
        Invoke-MysqlSql "DROP DATABASE IF EXISTS ``forum_db``;"
    }
    "status" {
        Invoke-MysqlSql "SHOW DATABASES;"
        foreach ($d in $domains) {
            Invoke-MysqlSql "SELECT '$d' AS domain, COUNT(*) AS tables FROM information_schema.TABLES WHERE TABLE_SCHEMA='forum_${d}_db' AND TABLE_TYPE='BASE TABLE';"
        }
    }
}

Write-Host "Done." -ForegroundColor Green
