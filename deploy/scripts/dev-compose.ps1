# 使用 Windows 用户环境变量启动本地中间件，禁止隐式读取项目 .env
#
# 这里必须用自动变量 $args 透传，不能用 [Parameter(ValueFromRemainingArguments)]：
# 后者会把 -v、-d 这类以连字符开头的 token 当作参数名，匹配不到就静默丢弃，
# 导致 `down -v` 实际执行成 `down`（卷没删却不报错）、`up -d` 执行成 `up`（前台挂住）。
$ErrorActionPreference = "Stop"

$composeArgs = if ($args.Count -gt 0) { $args } else { @("up", "-d") }
$deployRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $deployRoot "docker-compose.dev.yaml"

$userEnvironment = [Environment]::GetEnvironmentVariables("User")
foreach ($key in $userEnvironment.Keys) {
    if ([string]$key -ieq "Path") { continue }
    [Environment]::SetEnvironmentVariable([string]$key, [string]$userEnvironment[$key], "Process")
}

$required = @(
    "MYSQL_ROOT_PASSWORD",
    "REDIS_PASSWORD",
    "RABBITMQ_PASSWORD",
    "POSTGRES_PASSWORD",
    "NACOS_AUTH_TOKEN",
    "NACOS_AUTH_IDENTITY_KEY",
    "NACOS_AUTH_IDENTITY_VALUE"
)
$missing = @($required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_, "Process")) })
if ($missing.Count -gt 0) {
    throw "缺少 Windows 用户环境变量: $($missing -join ', ')"
}

$env:COMPOSE_DISABLE_ENV_FILE = "1"
& docker compose --project-directory $deployRoot -f $composeFile @composeArgs
if ($LASTEXITCODE -ne 0) {
    throw "docker compose 执行失败，退出码: $LASTEXITCODE"
}
