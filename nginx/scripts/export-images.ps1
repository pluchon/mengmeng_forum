# 导出 compose 所需镜像，便于拷贝到新服务器（无源码构建）
# 用法: cd nginx && .\scripts\export-images.ps1

$ErrorActionPreference = "Stop"
$nginxRoot = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $nginxRoot "package\images"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Test-DockerImage {
    param([string]$Name)
    # docker 在镜像不存在时向 stderr 输出，PowerShell 会当成 ErrorRecord；临时关闭 Stop
    $prevEa = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $null = docker image inspect $Name 2>&1
    $ok = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $prevEa
    return $ok
}

function Ensure-DockerImage {
    param([string]$Name)
    if (Test-DockerImage $Name) {
        Write-Host "  OK  $Name" -ForegroundColor DarkGray
        return
    }
    Write-Host "Pulling $Name ..."
    docker pull $Name
    if ($LASTEXITCODE -ne 0) {
        throw "docker pull failed: $Name"
    }
}

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
foreach ($img in $images) {
    Ensure-DockerImage $img
}

Write-Host "Saving images to $outDir ..."
docker save -o (Join-Path $outDir "forum-backend.tar") forum-backend:latest
if ($LASTEXITCODE -ne 0) { throw "docker save failed: forum-backend:latest (run build-all.ps1 first)" }

docker save -o (Join-Path $outDir "forum-ai-server.tar") forum-ai-server:latest
if ($LASTEXITCODE -ne 0) { throw "docker save failed: forum-ai-server:latest (run build-all.ps1 first)" }

docker save -o (Join-Path $outDir "infra.tar") nginx:1.30.1 mysql:9.7.0 redis:8.0 rabbitmq:4.3-management postgres:17
if ($LASTEXITCODE -ne 0) { throw "docker save failed: infra images" }

$pkg = Join-Path $nginxRoot "package"
@("dist", "conf.d", "ssl", "logs") | ForEach-Object {
    $src = Join-Path $nginxRoot $_
    $dst = Join-Path $pkg $_
    if (Test-Path $src) {
        if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
        Copy-Item -Recurse -Force $src $dst
    }
}
Copy-Item (Join-Path $nginxRoot "docker-compose.yaml") $pkg -Force
Copy-Item (Join-Path $nginxRoot "docker-compose.prod.yml") $pkg -Force
Copy-Item (Join-Path $nginxRoot ".env.example") $pkg -Force
Copy-Item (Join-Path $nginxRoot "DEPLOY-SERVER.md") $pkg -Force

$live2dPkg = Join-Path $pkg "dist\user\live2d-assets"
if (Test-Path $live2dPkg) {
    $lf = Get-ChildItem $live2dPkg -Recurse -File
    $lmb = [math]::Round(($lf | Measure-Object -Property Length -Sum).Sum / 1MB, 2)
    Write-Host "Package includes live2d-assets ($($lf.Count) files, ~${lmb} MB)" -ForegroundColor Green
} else {
    Write-Host "WARN: dist/user/live2d-assets missing — run build-all.ps1 with live2d/live2d-master present" -ForegroundColor Yellow
}

Write-Host "Package ready: $pkg" -ForegroundColor Green
Write-Host "Upload the entire package/ folder to your server." -ForegroundColor Yellow
