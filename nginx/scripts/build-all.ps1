# Build all artifacts for docker-compose (run from nginx/)
# Usage: .\scripts\build-all.ps1
#        .\scripts\build-all.ps1 -SkipDocker

param(
    [switch]$SkipDocker,
    [switch]$SkipFront,
    [switch]$SkipAdmin,
    [switch]$SkipBackend
)

$ErrorActionPreference = "Stop"
$nginxRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $nginxRoot

function Step($msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }

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
    npm run build
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "front build failed" }
    Pop-Location
    Sync-Dist (Join-Path $front "dist") (Join-Path $nginxRoot "dist\user")
    Write-Host "Synced to nginx/dist/user" -ForegroundColor Green
    Sync-Live2dAssets
}

if (-not $SkipAdmin) {
    Step "Build admin (forum-vue-admin/admin)"
    $admin = Join-Path $repoRoot "forum-vue-admin\admin"
    Push-Location $admin
    if (-not (Test-Path "node_modules")) { npm ci }
    npm run build
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "admin build failed" }
    Pop-Location
    Sync-Dist (Join-Path $admin "dist") (Join-Path $nginxRoot "dist\admin")
    Write-Host "Synced to nginx/dist/admin" -ForegroundColor Green
}

if (-not $SkipBackend) {
    if (-not $SkipDocker) {
        Step "Docker build forum-backend:latest"
        docker build -t forum-backend:latest (Join-Path $repoRoot "forum-demo")
        if ($LASTEXITCODE -ne 0) { throw "forum-backend image build failed" }
        Write-Host "Image forum-backend:latest ready" -ForegroundColor Green
    } else {
        Step "Maven package forum-demo"
        Push-Location (Join-Path $repoRoot "forum-demo")
        mvn -q -B package -DskipTests
        if ($LASTEXITCODE -ne 0) { Pop-Location; throw "mvn package failed" }
        Pop-Location
        Write-Host "JAR in forum-demo/target/" -ForegroundColor Green
    }
}

if (-not $SkipDocker) {
    $envFile = Join-Path $nginxRoot ".env"
    $envExample = Join-Path $nginxRoot ".env.example"
    Sync-EnvFromExample -EnvFile $envFile -EnvExample $envExample

    Step "Docker build forum-ffmpeg:latest"
    docker build -t forum-ffmpeg:latest (Join-Path $nginxRoot "ffmpeg")
    if ($LASTEXITCODE -ne 0) { throw "forum-ffmpeg image build failed" }
    Write-Host "Image forum-ffmpeg:latest ready" -ForegroundColor Green

    Step "Docker build forum-ai-server"
    docker build -t forum-ai-server:latest (Join-Path $repoRoot "ai-server")
    if ($LASTEXITCODE -ne 0) { throw "ai-server image build failed" }
    Write-Host "Image forum-ai-server:latest ready" -ForegroundColor Green
}

if ($SkipFront) {
    Sync-Live2dAssets
}

Step "Done (local workspace)"
Write-Host "  nginx/dist/user   - user SPA (local compose / export source)" -ForegroundColor Yellow
Write-Host "  nginx/dist/admin  - admin SPA" -ForegroundColor Yellow
Write-Host "  forum-backend:latest / forum-ai-server:latest / forum-ffmpeg:latest" -ForegroundColor Yellow
Write-Host ""
Write-Host "Local dev:  cd nginx; docker compose up -d" -ForegroundColor DarkGray
Write-Host "Server:     .\scripts\make-package.ps1" -ForegroundColor Green
Write-Host "            upload ONLY nginx\package\  (not the whole nginx folder)" -ForegroundColor Green
