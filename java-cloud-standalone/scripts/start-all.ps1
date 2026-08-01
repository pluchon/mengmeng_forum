# 启动 java-cloud-standalone 七业务服务 + Gateway（需已 mvn package，且 Docker/Nacos 就绪）
$ErrorActionPreference = "Stop"
$CloudRoot = Split-Path $PSScriptRoot -Parent
$Java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }
$LogDir = Join-Path $CloudRoot "logs"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

$services = @(
  @{ Name = "forum-auth";     Port = 10101; Jar = "forum-auth\target\forum-auth-0.0.1-SNAPSHOT.jar" },
  @{ Name = "forum-economy";  Port = 10105; Jar = "forum-economy\target\forum-economy-0.0.1-SNAPSHOT.jar" },
  @{ Name = "forum-im";       Port = 10103; Jar = "forum-im\target\forum-im-0.0.1-SNAPSHOT.jar" },
  @{ Name = "forum-content";  Port = 10102; Jar = "forum-content\target\forum-content-0.0.1-SNAPSHOT.jar" },
  @{ Name = "forum-game";     Port = 10104; Jar = "forum-game\target\forum-game-0.0.1-SNAPSHOT.jar" },
  @{ Name = "forum-ai";       Port = 10106; Jar = "forum-ai\target\forum-ai-0.0.1-SNAPSHOT.jar" },
  @{ Name = "forum-gateway";  Port = 10086; Jar = "forum-gateway\target\forum-gateway-0.0.1-SNAPSHOT.jar" }
)

function Test-Port([int]$Port) {
  try {
    $c = New-Object System.Net.Sockets.TcpClient
    $c.Connect("127.0.0.1", $Port)
    $c.Close()
    return $true
  } catch {
    return $false
  }
}

foreach ($svc in $services) {
  if (Test-Port $svc.Port) {
    Write-Host "[skip] $($svc.Name) already listening :$($svc.Port)"
    continue
  }
  $jar = Join-Path $CloudRoot $svc.Jar
  if (-not (Test-Path $jar)) {
    throw "Missing jar: $jar — run mvn -DskipTests package first"
  }
  $outLog = Join-Path $LogDir "$($svc.Name).out.log"
  $errLog = Join-Path $LogDir "$($svc.Name).err.log"
  Write-Host "[start] $($svc.Name) -> :$($svc.Port)"
  Start-Process -FilePath $Java -ArgumentList @("-jar", $jar) -WorkingDirectory (Split-Path $jar -Parent) `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog -WindowStyle Hidden | Out-Null
}

Write-Host "Waiting for Gateway :10086 ..."
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
  if (Test-Port 10086) {
    try {
      $h = Invoke-WebRequest "http://127.0.0.1:10086/actuator/health" -UseBasicParsing -TimeoutSec 3
      if ($h.StatusCode -eq 200) {
        Write-Host "Gateway UP"
        exit 0
      }
    } catch {}
  }
  Start-Sleep -Seconds 2
}
Write-Host "Gateway not ready within 3 minutes; check $LogDir"
exit 1
