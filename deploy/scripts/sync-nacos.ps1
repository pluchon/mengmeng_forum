# 将版本化配置同步到本机 Nacos 3.x，并逐项回读校验
param(
    [string]$NamespaceId = "forum-dev",
    [string]$NamespaceName = "forum-dev",
    [string]$Group = "FORUM",
    [string]$ConsoleUrl = "http://127.0.0.1:8080",
    [string]$ClientUrl = "http://127.0.0.1:8848/nacos",
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
$deployRoot = Split-Path -Parent $PSScriptRoot
$configRoot = Join-Path $deployRoot "nacos-config"
$backupRoot = Join-Path "C:\forum-config\nacos-backup" (Get-Date -Format "yyyyMMdd-HHmmss")

$userEnvironment = [Environment]::GetEnvironmentVariables("User")
foreach ($key in $userEnvironment.Keys) {
    if ([string]$key -ieq "Path") { continue }
    [Environment]::SetEnvironmentVariable([string]$key, [string]$userEnvironment[$key], "Process")
}

$username = [Environment]::GetEnvironmentVariable("NACOS_CONSOLE_USERNAME", "Process")
$password = [Environment]::GetEnvironmentVariable("NACOS_CONSOLE_PASSWORD", "Process")
if ([string]::IsNullOrWhiteSpace($username)) { $username = "nacos" }
if ([string]::IsNullOrWhiteSpace($password)) { $password = "nacos" }

$expected = @(
    "forum-common.yml",
    "forum-gateway.yml",
    "forum-auth.yml",
    "forum-content.yml",
    "forum-im.yml",
    "forum-game.yml",
    "forum-economy.yml",
    "forum-ai.yml",
    "sentinel-forum-game-flow.json",
    "sentinel-forum-game-degrade.json",
    "sentinel-forum-content-flow.json",
    "sentinel-forum-content-degrade.json",
    "sentinel-forum-ai-flow.json",
    "sentinel-forum-ai-degrade.json"
)

foreach ($dataId in $expected) {
    $path = Join-Path $configRoot $dataId
    if (-not (Test-Path -LiteralPath $path)) { throw "缺少 Nacos 配置模板: $dataId" }
    $content = Get-Content -LiteralPath $path -Raw
    if ($content -match '(?im)^\s*(password|secret|api[_-]?key|access[_-]?key[_-]?secret)\s*:\s*["'']?(?!\$\{)[^\s"'']+') {
        throw "配置模板疑似包含明文密钥: $dataId"
    }
}

try {
    $login = Invoke-RestMethod -Uri "$ConsoleUrl/v3/auth/user/login" -Method Post `
        -Body @{ username = $username; password = $password } `
        -ContentType "application/x-www-form-urlencoded" -TimeoutSec 10
} catch {
    throw "Nacos 控制台登录失败: $($_.Exception.Message)"
}
if ([string]::IsNullOrWhiteSpace($login.accessToken)) { throw "Nacos 登录响应缺少 accessToken" }
$headers = @{ accessToken = $login.accessToken }
$encodedUser = [Uri]::EscapeDataString($username)

$namespaceResponse = Invoke-RestMethod -Uri "$ConsoleUrl/v3/console/core/namespace/list?username=$encodedUser" `
    -Method Get -Headers $headers -TimeoutSec 10
$namespaceExists = @($namespaceResponse.data | Where-Object { $_.namespace -eq $NamespaceId }).Count -gt 0
if (-not $namespaceExists) {
    if ($ValidateOnly) { throw "Nacos Namespace 不存在: $NamespaceId" }
    $created = Invoke-RestMethod -Uri "$ConsoleUrl/v3/console/core/namespace?username=$encodedUser" `
        -Method Post -Headers $headers -Body @{
            customNamespaceId = $NamespaceId
            namespaceName = $NamespaceName
            namespaceDesc = "论坛本地开发配置"
        } -ContentType "application/x-www-form-urlencoded" -TimeoutSec 10
    $createSucceeded = $created -eq $true -or $created.data -eq $true -or $created.code -eq 0
    if (-not $createSucceeded) { throw "创建 Nacos Namespace 失败: $NamespaceId" }
}

function Get-NacosContent {
    param([string]$DataId)
    $query = "dataId=$([Uri]::EscapeDataString($DataId))&groupName=$([Uri]::EscapeDataString($Group))&namespaceId=$([Uri]::EscapeDataString($NamespaceId))"
    $response = Invoke-WebRequest -Uri "$ClientUrl/v3/admin/cs/config?$query" -Method Get -Headers $headers -SkipHttpErrorCheck -TimeoutSec 10
    if ($response.StatusCode -eq 404) { return $null }
    if ($response.StatusCode -ne 200) { throw "读取 Nacos 配置失败: $DataId HTTP $($response.StatusCode)" }
    $payload = $response.Content | ConvertFrom-Json
    if ($payload.code -eq 20004) { return $null }
    if ($payload.code -ne 0 -and $payload.code -ne 200) { throw "读取 Nacos 配置失败: $DataId code=$($payload.code)" }
    if ($payload.data -is [string]) { return [string]$payload.data }
    if ($payload.data.content) { return [string]$payload.data.content }
    return $null
}

foreach ($dataId in $expected) {
    $path = Join-Path $configRoot $dataId
    $content = (Get-Content -LiteralPath $path -Raw) -replace "`r`n", "`n"
    $existing = Get-NacosContent -DataId $dataId
    if (-not $ValidateOnly -and $null -ne $existing -and $existing -ne $content) {
        New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
        [System.IO.File]::WriteAllText((Join-Path $backupRoot $dataId), $existing, [System.Text.UTF8Encoding]::new($false))
    }
    if (-not $ValidateOnly) {
        $type = if ($dataId.EndsWith(".json")) { "json" } else { "yaml" }
        # Nacos 3.x 客户端 API 只负责读取；发布必须使用需鉴权的 Admin API。
        $published = Invoke-RestMethod -Uri "$ClientUrl/v3/admin/cs/config" -Method Post -Headers $headers -Body @{
            dataId = $dataId
            groupName = $Group
            namespaceId = $NamespaceId
            content = $content
            type = $type
        } -ContentType "application/x-www-form-urlencoded" -TimeoutSec 15
        $publishSucceeded = $published -eq $true -or $published.data -eq $true -or $published.code -eq 0 -or $published.code -eq 200
        if (-not $publishSucceeded) { throw "发布 Nacos 配置失败: $dataId" }
    }
    $actual = (Get-NacosContent -DataId $dataId) -replace "`r`n", "`n"
    if ($actual -ne $content) { throw "Nacos 回读内容不一致: $dataId" }
    Write-Host "OK $dataId" -ForegroundColor DarkGray
}

Write-Host "Nacos 配置校验通过：Namespace=$NamespaceId Group=$Group Count=$($expected.Count)" -ForegroundColor Green
if (Test-Path -LiteralPath $backupRoot) {
    Write-Host "覆盖前备份：$backupRoot" -ForegroundColor Yellow
}
