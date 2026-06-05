# 将本机密钥加载到当前 PowerShell 会话（不写入仓库）
# 用法（在仓库根目录）:  . .\scripts\load-dev-env.ps1

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$secretsFile = Join-Path $PSScriptRoot "dev-secrets.ps1"

if (-not (Test-Path $secretsFile)) {
    Write-Host "未找到 scripts/dev-secrets.ps1" -ForegroundColor Yellow
    Write-Host "请执行: copy scripts\dev-secrets.ps1.example scripts\dev-secrets.ps1" -ForegroundColor Yellow
    Write-Host "或在 Windows 用户环境变量中配置 DASHSCOPE_API_KEY 等，见 README「配置说明」" -ForegroundColor Yellow
    return
}

. $secretsFile
Write-Host "已加载 scripts/dev-secrets.ps1 到当前会话" -ForegroundColor Green
Write-Host "  PII_CRYPTO_SECRET = $(if ($env:PII_CRYPTO_SECRET) { '已设置' } else { '未设置' })" -ForegroundColor DarkGray
Write-Host "  DASHSCOPE_API_KEY = $(if ($env:DASHSCOPE_API_KEY) { '已设置' } else { '未设置' })" -ForegroundColor DarkGray
