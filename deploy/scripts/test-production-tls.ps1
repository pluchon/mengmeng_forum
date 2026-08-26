# Validate the production Nginx domain and TLS material before packaging.
param(
    [Parameter(Mandatory = $true)]
    [string]$SslRoot,
    [Parameter(Mandatory = $true)]
    [string]$NginxConfig,
    [string]$CertificateName = "www.example.com",
    [string[]]$RequiredDnsNames = @("example.com", "www.example.com"),
    [ValidateRange(1, 90)]
    [int]$MinimumRemainingDays = 14
)

$ErrorActionPreference = "Stop"
$sslFullPath = [IO.Path]::GetFullPath($SslRoot)
$nginxFullPath = [IO.Path]::GetFullPath($NginxConfig)
$certificatePath = Join-Path $sslFullPath "$CertificateName.pem"
$privateKeyPath = Join-Path $sslFullPath "$CertificateName.key"

foreach ($requiredPath in @($nginxFullPath, $certificatePath, $privateKeyPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Production TLS file is missing: $requiredPath"
    }
}

$nginxText = Get-Content -LiteralPath $nginxFullPath -Raw -Encoding UTF8
$configuredNames = [regex]::Matches($nginxText, '(?im)^\s*server_name\s+([^;]+);') |
    ForEach-Object { $_.Groups[1].Value -split '\s+' } |
    Where-Object { $_ } |
    Sort-Object -Unique
foreach ($dnsName in $RequiredDnsNames) {
    if ($configuredNames -notcontains $dnsName) {
        throw "Production Nginx config does not declare server_name $dnsName"
    }
}

$escapedCertificateName = [regex]::Escape($CertificateName)
if ($nginxText -notmatch "(?m)^\s*ssl_certificate\s+/etc/nginx/ssl/$escapedCertificateName\.pem;") {
    throw "Production Nginx config does not reference $CertificateName.pem"
}
if ($nginxText -notmatch "(?m)^\s*ssl_certificate_key\s+/etc/nginx/ssl/$escapedCertificateName\.key;") {
    throw "Production Nginx config does not reference $CertificateName.key"
}

$certificateCollection = [Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
$certificateWithKey = $null
$chain = $null
try {
    $certificateCollection.ImportFromPemFile($certificatePath)
    if ($certificateCollection.Count -lt 2) {
        throw "Production PEM must contain the leaf certificate and intermediate chain: $certificatePath"
    }

    $certificateWithKey = [Security.Cryptography.X509Certificates.X509Certificate2]::CreateFromPemFile(
        $certificatePath,
        $privateKeyPath
    )
    if (-not $certificateWithKey.HasPrivateKey) {
        throw "Production certificate does not have a matching private key: $CertificateName"
    }

    $now = [DateTimeOffset]::UtcNow
    $notBefore = [DateTimeOffset]$certificateWithKey.NotBefore.ToUniversalTime()
    $notAfter = [DateTimeOffset]$certificateWithKey.NotAfter.ToUniversalTime()
    if ($notBefore -gt $now) {
        throw "Production certificate is not valid yet: $($notBefore.ToString('u'))"
    }
    $minimumExpiry = $now.AddDays($MinimumRemainingDays)
    if ($notAfter -lt $minimumExpiry) {
        throw "Production certificate expires too soon: $($notAfter.ToString('u')); require at least $MinimumRemainingDays remaining days"
    }

    $sanExtension = $certificateWithKey.Extensions |
        Where-Object { $_.Oid.Value -eq '2.5.29.17' } |
        Select-Object -First 1
    if (-not $sanExtension) {
        throw "Production certificate has no Subject Alternative Name extension"
    }
    $sanText = $sanExtension.Format($false)
    $sanNames = [regex]::Matches($sanText, '(?i)DNS Name=([^,]+)') |
        ForEach-Object { $_.Groups[1].Value.Trim().ToLowerInvariant() } |
        Sort-Object -Unique
    foreach ($dnsName in $RequiredDnsNames) {
        if ($sanNames -notcontains $dnsName.ToLowerInvariant()) {
            throw "Production certificate SAN does not cover $dnsName"
        }
    }

    $chain = [Security.Cryptography.X509Certificates.X509Chain]::new()
    $chain.ChainPolicy.RevocationMode = [Security.Cryptography.X509Certificates.X509RevocationMode]::NoCheck
    for ($index = 1; $index -lt $certificateCollection.Count; $index++) {
        [void]$chain.ChainPolicy.ExtraStore.Add($certificateCollection[$index])
    }
    if (-not $chain.Build($certificateCollection[0])) {
        $chainErrors = $chain.ChainStatus |
            ForEach-Object { "$($_.Status): $($_.StatusInformation.Trim())" }
        throw "Production certificate chain validation failed: $($chainErrors -join '; ')"
    }

    $remainingDays = [math]::Floor(($notAfter - $now).TotalDays)
    Write-Host "Production TLS verification passed: $($RequiredDnsNames -join ', '), expires $($notAfter.ToString('u')) ($remainingDays days remaining)." -ForegroundColor Green
}
finally {
    if ($chain) { $chain.Dispose() }
    if ($certificateWithKey) { $certificateWithKey.Dispose() }
    $certificateCollection.Dispose()
}
