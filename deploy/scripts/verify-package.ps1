# 打包产物自检（make-package 结束后自动调用）
param([string]$PkgRoot = "C:\forum-build\luntan-package")

$ErrorActionPreference = "Stop"
$required = @(
    "dist\user\index.html",
    "images\forum-backend.tar",
    "images\forum-ai-server.tar",
    "images\infra.tar",
    "docker-compose.yaml",
    "docker-compose.prod.yml",
    ".env.example",
    ".forum-package-root",
    "RELEASE.txt",
    "manifest.sha256",
    "start.sh",
    "up.sh",
    "reset-db.sh",
    "collect-logs.sh",
    "init-db.sh",
    "sync-nacos.sh",
    "nacos-config\forum-common.yml",
    "nacos-config\forum-gateway.yml",
    "nacos-config\sentinel-forum-content-flow.json",
    "nacos-config\sentinel-forum-game-flow.json",
    "nacos-config\sentinel-forum-ai-flow.json",
    "verify-frontend-dist.sh",
    "sql\auth-create.sql",
    "sql\content-create.sql",
    "sql\im-create.sql",
    "sql\game-create.sql",
    "sql\economy-create.sql",
    "sql\ai-create.sql",
    "sql\postgres_ai_session.sql",
    "ssl\www.example.com.pem",
    "ssl\www.example.com.key"
)
$optional = @("ffmpeg\Dockerfile")

Write-Host "Verifying package: $PkgRoot" -ForegroundColor Cyan
foreach ($rel in $required) {
    $p = Join-Path $PkgRoot $rel
    if (-not (Test-Path $p)) { throw "Package missing required: $rel" }
    Write-Host "  OK $rel" -ForegroundColor DarkGray
}
foreach ($rel in $optional) {
    $p = Join-Path $PkgRoot $rel
    if (Test-Path $p) { Write-Host "  OK $rel" -ForegroundColor DarkGray }
    else { Write-Host "  WARN optional missing: $rel" -ForegroundColor Yellow }
}

foreach ($shName in @("start.sh", "up.sh", "reset-db.sh", "init-db.sh", "sync-nacos.sh", "verify-frontend-dist.sh")) {
    $sh = Join-Path $PkgRoot $shName
    if (-not (Test-Path $sh)) { continue }
    $raw = [System.IO.File]::ReadAllBytes($sh)
    if ($raw -contains 13) { throw "$shName contains CRLF - re-run export-images.ps1" }
}
# 递归查找：只看包根目录会漏掉子目录里被误打包的密钥文件，
# 而部署包是要整体上传到服务器的。
$leakedEnv = @(
    Get-ChildItem -LiteralPath $PkgRoot -Recurse -File -Force -Filter ".env*" |
        Where-Object { $_.Name -ne ".env.example" }
)
if ($leakedEnv.Count -gt 0) {
    $rels = $leakedEnv | ForEach-Object { $_.FullName.Substring($PkgRoot.Length).TrimStart('\') }
    throw "部署包内不得包含真实环境变量文件：$($rels -join ', ')"
}

$unexpectedDeltaSql = @(Get-ChildItem -LiteralPath (Join-Path $PkgRoot "sql") -File | Where-Object { $_.Name -match 'delta|remove-|cost-quota|message-album|session-visibility' })
if ($unexpectedDeltaSql.Count -gt 0) { throw "Package contains obsolete incremental SQL: $($unexpectedDeltaSql.Name -join ', ')" }

$expectedTables = @{ auth = 11; content = 30; im = 14; game = 12; economy = 36; ai = 17 }
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
foreach ($domain in $expectedTables.Keys) {
    $packagedSql = Join-Path $PkgRoot "sql\$domain-create.sql"
    $sourceSql = Join-Path $repoRoot "java-cloud-standalone\$domain\server\src\main\resources\db\create.sql"
    if ((Get-FileHash -LiteralPath $packagedSql -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $sourceSql -Algorithm SHA256).Hash) {
        throw "Packaged schema differs from source: $domain"
    }
    $sqlText = Get-Content -LiteralPath $packagedSql -Raw -Encoding UTF8
    if ($sqlText -match '(?im)^\s*DROP\s+DATABASE\b') { throw "$domain-create.sql must not contain DROP DATABASE" }
    $tableCount = [regex]::Matches($sqlText, '(?im)^\s*CREATE\s+TABLE\s+`').Count
    if ($tableCount -ne $expectedTables[$domain]) {
        throw "$domain-create.sql table count $tableCount does not match expected $($expectedTables[$domain])"
    }
}

$sslRoot = Join-Path $PkgRoot "ssl"
& (Join-Path $PSScriptRoot "test-production-tls.ps1") `
    -SslRoot $sslRoot `
    -NginxConfig (Join-Path $PkgRoot "conf.d\20-prod-https.conf")
if (-not $?) { throw "Packaged production domain and TLS verification failed" }
$expectedTlsFiles = @("www.example.com.key", "www.example.com.pem")
$actualTlsFiles = @(Get-ChildItem -LiteralPath $sslRoot -File | Select-Object -ExpandProperty Name | Sort-Object)
if (Compare-Object -ReferenceObject $expectedTlsFiles -DifferenceObject $actualTlsFiles) {
    throw "Package ssl directory must contain only the Nginx production certificate and key"
}

$manifestPath = Join-Path $PkgRoot "manifest.sha256"
$manifestBytes = [System.IO.File]::ReadAllBytes($manifestPath)
if ($manifestBytes -contains 13) { throw "manifest.sha256 must use LF line endings for Linux sha256sum" }
$manifestFiles = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($line in Get-Content -LiteralPath $manifestPath -Encoding UTF8) {
    if ($line -notmatch '^([0-9a-f]{64})  (.+)$') { throw "Invalid manifest line: $line" }
    $expectedHash = $Matches[1]
    $relative = $Matches[2]
    if ([IO.Path]::IsPathRooted($relative) -or $relative.Split('/') -contains '..') { throw "Unsafe manifest path: $relative" }
    $fullPath = Join-Path $PkgRoot ($relative.Replace('/', '\'))
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { throw "Manifest file missing: $relative" }
    $actualHash = (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) { throw "Manifest hash mismatch: $relative" }
    if (-not $manifestFiles.Add($relative)) { throw "Duplicate manifest path: $relative" }
}
$actualFiles = @(Get-ChildItem -LiteralPath $PkgRoot -Recurse -File |
    Where-Object { $_.FullName -ne $manifestPath } |
    ForEach-Object { [IO.Path]::GetRelativePath($PkgRoot, $_.FullName).Replace('\', '/') })
if ($manifestFiles.Count -ne $actualFiles.Count) { throw "Manifest file inventory is incomplete" }
foreach ($relative in $actualFiles) {
    if (-not $manifestFiles.Contains($relative)) { throw "File not covered by manifest: $relative" }
}
Write-Host "Package verification passed." -ForegroundColor Green
