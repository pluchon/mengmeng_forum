# 按端口停止 java-cloud-standalone 本地进程（仅匹配本工程 jar 命令行）
$ErrorActionPreference = "Continue"
$CloudRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$marker = ($CloudRoot -replace '\\', '/')

$ports = @(10086, 10101, 10102, 10103, 10104, 10105, 10106)
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object {
  $cmd = [string]$_.CommandLine
  if (-not $cmd) { return }
  $norm = $cmd -replace '\\', '/'
  if ($norm -notlike "*$marker*") { return }
  $hit = $false
  foreach ($p in $ports) {
    if ($norm -match "forum-(gateway|auth|content|im|game|economy|ai)") { $hit = $true; break }
  }
  if ($hit) {
    Write-Host "[stop] PID=$($_.ProcessId)"
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
  }
}
Write-Host "done"
