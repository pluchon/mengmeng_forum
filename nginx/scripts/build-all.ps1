# Build all artifacts for docker-compose (run from nginx/)
# Usage: .\scripts\build-all.ps1
#        .\scripts\build-all.ps1 -SkipDocker

param(
    [switch]$SkipDocker,
    [switch]$SkipFront,
    [switch]$SkipBackend,
    [switch]$ShowBuildDetails
)

$ErrorActionPreference = "Stop"
$nginxRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $nginxRoot
$hostBuildProxy = "http://127.0.0.1:7897"
$containerBuildProxy = "http://host.docker.internal:7897"
$ideaMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.4\plugins\maven\lib\maven3\bin\mvn.cmd"
$mavenCommand = if (Test-Path $ideaMaven) { $ideaMaven } else { "mvn" }

# 主机侧 npm / Maven 通过本地代理；Docker 构建容器通过宿主机别名访问同一代理。
$env:http_proxy = $hostBuildProxy
$env:https_proxy = $hostBuildProxy
$env:HTTP_PROXY = $hostBuildProxy
$env:HTTPS_PROXY = $hostBuildProxy
$env:NO_PROXY = "localhost,127.0.0.1"
$env:no_proxy = $env:NO_PROXY
$env:MAVEN_OPTS = "-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"

$dockerBuildProxyArgs = @(
    "--build-arg", "http_proxy=$containerBuildProxy",
    "--build-arg", "https_proxy=$containerBuildProxy",
    "--build-arg", "HTTP_PROXY=$containerBuildProxy",
    "--build-arg", "HTTPS_PROXY=$containerBuildProxy",
    "--build-arg", "no_proxy=localhost,127.0.0.1",
    "--build-arg", "NO_PROXY=localhost,127.0.0.1"
)

function Step($msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }

function Invoke-DockerBuild {
    param(
        [string]$Tag,
        [string]$Context,
        [string]$DisplayName
    )
    Step "Docker build $DisplayName"
    if ($ShowBuildDetails) {
        & docker build --pull=false --progress=plain @dockerBuildProxyArgs -t $Tag $Context
    } else {
        & docker build --pull=false -q @dockerBuildProxyArgs -t $Tag $Context
    }
    if ($LASTEXITCODE -ne 0) { throw "$DisplayName image build failed" }
    Write-Host "Image $Tag ready" -ForegroundColor Green
}

function Invoke-NpmBuild {
    $npmCommand = Get-Command "npm.cmd" -ErrorAction SilentlyContinue
    if ($null -eq $npmCommand) {
        $npmCommand = Get-Command "npm" -ErrorAction Stop
    }

    $previousNativePreference = $PSNativeCommandUseErrorActionPreference
    try {
        # Vite 将构建警告写入 stderr；保留提示，但只根据 npm 的退出码判断构建是否失败。
        $PSNativeCommandUseErrorActionPreference = $false
        $output = @(& $npmCommand.Source run build 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $PSNativeCommandUseErrorActionPreference = $previousNativePreference
    }

    return [PSCustomObject]@{
        ExitCode = $exitCode
        Output = $output
    }
}

function Sync-Dist($src, $dst) {
    if (-not (Test-Path $src)) { throw "Missing build output: $src" }
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Get-ChildItem -Path $dst -Force | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item -Path (Join-Path $src "*") -Destination $dst -Recurse -Force
}

function Sync-EnvFromExample {
    param(
        [string]$EnvFile,
        [string]$EnvExample
    )
    if (-not (Test-Path $EnvExample)) { return }
    if (-not (Test-Path $EnvFile)) {
        Copy-Item $EnvExample $EnvFile
        Write-Host "Created $EnvFile from .env.example" -ForegroundColor Yellow
        return
    }
    $existing = @{}
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$') { $existing[$matches[1].Trim()] = $true }
    }
    $toAppend = @()
    Get-Content $EnvExample | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)=(.*)$' -and -not $existing.ContainsKey($matches[1].Trim())) {
            $toAppend += $_
        }
    }
    if ($toAppend.Count -gt 0) {
        Add-Content $EnvFile ""
        Add-Content $EnvFile "# merged from .env.example"
        $toAppend | Add-Content $EnvFile
        Write-Host "Merged $($toAppend.Count) missing key(s) into .env" -ForegroundColor Yellow
    }
}

function Sync-Live2dAssets {
    $src = Join-Path $repoRoot "live2d\live2d-master"
    $dst = Join-Path $nginxRoot "dist\user\live2d-assets"
    if (-not (Test-Path $src)) {
        Write-Host "WARN: Live2D models not found at $src 鈥?skip live2d-assets sync" -ForegroundColor Yellow
        return
    }
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Get-ChildItem -Path $dst -Force | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item -Path (Join-Path $src "*") -Destination $dst -Recurse -Force
    $files = Get-ChildItem $dst -Recurse -File
    $mb = [math]::Round(($files | Measure-Object -Property Length -Sum).Sum / 1MB, 2)
    Write-Host "Synced live2d to nginx/dist/user/live2d-assets ($($files.Count) files, ~${mb} MB)" -ForegroundColor Green
}

if (-not $SkipFront) {
    Step "Build user front (forum-vue/front)"
    $front = Join-Path $repoRoot "forum-vue\front"
    Push-Location $front
    if (-not (Test-Path "node_modules")) { npm ci }
    $frontBuildResult = Invoke-NpmBuild
    if ($ShowBuildDetails) {
        $frontBuildResult.Output | Write-Host
    }
    if ($frontBuildResult.ExitCode -ne 0) {
        if (-not $ShowBuildDetails) {
            $frontBuildResult.Output | Write-Host
        }
        Pop-Location
        throw "front build failed"
    }
    Pop-Location
    Write-Host "Front build ready" -ForegroundColor Green
    Sync-Dist (Join-Path $front "dist") (Join-Path $nginxRoot "dist\user")
    Write-Host "Synced to nginx/dist/user" -ForegroundColor Green
    Sync-Live2dAssets
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

if (-not $SkipBackend) {
    $cloudRoot = Join-Path $repoRoot "java-cloud-standalone"
    if (-not (Test-Path (Join-Path $cloudRoot "pom.xml"))) {
        throw "Missing java-cloud-standalone/pom.xml (old backend/ module was removed)"
    }

    Step "Maven package java-cloud-standalone"
    Push-Location $cloudRoot
    & $mavenCommand -B package -DskipTests
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "mvn package failed" }
    Pop-Location
    Write-Host "JARs under java-cloud-standalone/*/server/target/ and gateway/target/" -ForegroundColor Green

    if (-not $SkipDocker) {
        $backendDockerfile = Join-Path $cloudRoot "Dockerfile.backend"
        $legacyBackend = Join-Path $repoRoot "backend"
        if (Test-Path $backendDockerfile) {
            Invoke-DockerBuild -Tag "forum-backend:latest" -Context $cloudRoot -DisplayName "forum-backend:latest"
        }
        elseif (Test-Path (Join-Path $legacyBackend "Dockerfile")) {
            Invoke-DockerBuild -Tag "forum-backend:latest" -Context $legacyBackend -DisplayName "forum-backend:latest"
        }
        elseif (Test-DockerImage "forum-backend:latest") {
            Write-Host "WARN: no Dockerfile for forum-backend; reusing existing forum-backend:latest image" -ForegroundColor Yellow
        }
        else {
            throw "forum-backend:latest image missing and no Dockerfile found. Add java-cloud-standalone/Dockerfile.backend or build/load the image before packaging."
        }
    }
}

if (-not $SkipDocker) {
    $envFile = Join-Path $nginxRoot ".env"
    $envExample = Join-Path $nginxRoot ".env.example"
    Sync-EnvFromExample -EnvFile $envFile -EnvExample $envExample

    Invoke-DockerBuild -Tag "forum-ffmpeg:latest" -Context (Join-Path $nginxRoot "ffmpeg") -DisplayName "forum-ffmpeg:latest"

    Invoke-DockerBuild -Tag "forum-ai-server:latest" -Context (Join-Path $repoRoot "ai-server") -DisplayName "forum-ai-server:latest"
}

if ($SkipFront) {
    Sync-Live2dAssets
}

Step "Done (local workspace)"
Write-Host "  nginx/dist/user   - user SPA (local compose / export source)" -ForegroundColor Yellow
Write-Host "  forum-backend:latest / forum-ai-server:latest / forum-ffmpeg:latest" -ForegroundColor Yellow
Write-Host ""
Write-Host "Local dev:  cd nginx; docker compose up -d" -ForegroundColor DarkGray
Write-Host "Server:     .\scripts\make-package.ps1" -ForegroundColor Green
Write-Host "            upload ONLY nginx\package\  (not the whole nginx folder)" -ForegroundColor Green
