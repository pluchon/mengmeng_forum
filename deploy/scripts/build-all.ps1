# Build all artifacts for docker-compose (run from deploy/)
# Usage: .\scripts\build-all.ps1
#        .\scripts\build-all.ps1 -SkipDocker

param(
    [switch]$SkipDocker,
    [switch]$SkipFront,
    [switch]$SkipBackend,
    [switch]$SkipTests,
    [switch]$ShowBuildDetails,
    [string]$BuildProxy = $env:FORUM_BUILD_PROXY,
    [string]$DockerBuildProxy = $env:FORUM_DOCKER_BUILD_PROXY
)

$ErrorActionPreference = "Stop"
$deployRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $deployRoot

function Resolve-MavenCommand {
    if ($env:FORUM_MAVEN_COMMAND) {
        if (-not (Test-Path -LiteralPath $env:FORUM_MAVEN_COMMAND)) { throw "FORUM_MAVEN_COMMAND does not exist" }
        return $env:FORUM_MAVEN_COMMAND
    }
    $pathMaven = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if ($null -eq $pathMaven) { $pathMaven = Get-Command "mvn" -ErrorAction SilentlyContinue }
    if ($null -ne $pathMaven) { return $pathMaven.Source }
    $jetBrainsRoot = "C:\Program Files\JetBrains"
    if (Test-Path -LiteralPath $jetBrainsRoot) {
        $candidate = Get-ChildItem -LiteralPath $jetBrainsRoot -Directory -Filter "IntelliJ IDEA*" |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName "plugins\maven\lib\maven3\bin\mvn.cmd" } |
            Where-Object { Test-Path -LiteralPath $_ } |
            Select-Object -First 1
        if ($candidate) { return $candidate }
    }
    throw "Maven not found. Set FORUM_MAVEN_COMMAND to IDEA's mvn.cmd."
}

$mavenCommand = Resolve-MavenCommand

if ($BuildProxy) {
    $proxyUri = [Uri]$BuildProxy
    if (-not $proxyUri.IsAbsoluteUri -or -not $proxyUri.Host -or $proxyUri.Port -le 0) { throw "Invalid FORUM_BUILD_PROXY" }
    $env:http_proxy = $BuildProxy
    $env:https_proxy = $BuildProxy
    $env:HTTP_PROXY = $BuildProxy
    $env:HTTPS_PROXY = $BuildProxy
    $proxyMavenOptions = "-Dhttp.proxyHost=$($proxyUri.Host) -Dhttp.proxyPort=$($proxyUri.Port) -Dhttps.proxyHost=$($proxyUri.Host) -Dhttps.proxyPort=$($proxyUri.Port)"
    $env:MAVEN_OPTS = (($env:MAVEN_OPTS, $proxyMavenOptions) | Where-Object { $_ }) -join " "
}

$dockerBuildProxyArgs = @()
if ($DockerBuildProxy) {
    $dockerBuildProxyArgs = @(
        "--build-arg", "http_proxy=$DockerBuildProxy",
        "--build-arg", "https_proxy=$DockerBuildProxy",
        "--build-arg", "HTTP_PROXY=$DockerBuildProxy",
        "--build-arg", "HTTPS_PROXY=$DockerBuildProxy",
        "--build-arg", "no_proxy=localhost,127.0.0.1",
        "--build-arg", "NO_PROXY=localhost,127.0.0.1"
    )
}

function Step($msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }

function Invoke-DockerBuild {
    param(
        [string]$Tag,
        [string]$Context,
        [string]$DisplayName,
        [string]$Dockerfile
    )
    Step "Docker build $DisplayName"
    if ($ShowBuildDetails) {
        if ($Dockerfile) {
            & docker build --pull=false --progress=plain @dockerBuildProxyArgs -f $Dockerfile -t $Tag $Context
        } else {
            & docker build --pull=false --progress=plain @dockerBuildProxyArgs -t $Tag $Context
        }
    } else {
        if ($Dockerfile) {
            & docker build --pull=false -q @dockerBuildProxyArgs -f $Dockerfile -t $Tag $Context
        } else {
            & docker build --pull=false -q @dockerBuildProxyArgs -t $Tag $Context
        }
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

function Assert-DeployChildPath([string]$Path) {
    $fullPath = [IO.Path]::GetFullPath($Path)
    $allowedPrefix = [IO.Path]::GetFullPath($deployRoot).TrimEnd('\') + '\'
    if (-not $fullPath.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify path outside deploy root: $fullPath"
    }
}

function Sync-Dist($src, $dst) {
    if (-not (Test-Path $src)) { throw "Missing build output: $src" }
    Assert-DeployChildPath $dst
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Get-ChildItem -LiteralPath $dst -Force | Remove-Item -Recurse -Force
    Copy-Item -Path (Join-Path $src "*") -Destination $dst -Recurse -Force
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
    Sync-Dist (Join-Path $front "dist") (Join-Path $deployRoot "dist\user")
    Write-Host "Synced to deploy/dist/user" -ForegroundColor Green
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
    $mavenArgs = @("-B", "package")
    if ($SkipTests) { $mavenArgs += "-DskipTests" }
    & $mavenCommand @mavenArgs
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "mvn package failed" }
    Pop-Location
    Write-Host "JARs under java-cloud-standalone/*/server/target/ and gateway/target/" -ForegroundColor Green

    if (-not $SkipDocker) {
        $backendDockerfile = Join-Path $cloudRoot "Dockerfile.backend"
        if (Test-Path $backendDockerfile) {
            Invoke-DockerBuild -Tag "forum-backend:latest" -Context $cloudRoot -DisplayName "forum-backend:latest" -Dockerfile $backendDockerfile
        }
        else {
            throw "Missing java-cloud-standalone/Dockerfile.backend. Refusing to package a stale forum-backend image."
        }
    }
}

if (-not $SkipDocker) {
    Invoke-DockerBuild -Tag "forum-ffmpeg:latest" -Context (Join-Path $deployRoot "ffmpeg") -DisplayName "forum-ffmpeg:latest"

    Invoke-DockerBuild -Tag "forum-ai-server:latest" -Context (Join-Path $repoRoot "ai-server") -DisplayName "forum-ai-server:latest"
}

Step "Done (local workspace)"
Write-Host "  deploy/dist/user  - user SPA (local compose / export source)" -ForegroundColor Yellow
Write-Host "  forum-backend:latest / forum-ai-server:latest / forum-ffmpeg:latest" -ForegroundColor Yellow
Write-Host ""
Write-Host "Local dev:  .\scripts\dev-compose.ps1 up -d" -ForegroundColor DarkGray
Write-Host "Server:     .\scripts\make-package.ps1" -ForegroundColor Green
Write-Host "            upload ONLY C:\forum-build\luntan-package" -ForegroundColor Green
