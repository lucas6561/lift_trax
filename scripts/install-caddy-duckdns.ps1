#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$versions = Get-Content (Join-Path $repo 'config/https/toolchain.json') -Raw | ConvertFrom-Json
$tools = Join-Path $repo 'data/https/tools'
$destination = Join-Path $tools "caddy-$($versions.CaddyVersion)-duckdns-$($versions.DuckDnsVersion)"
$binary = Join-Path $destination 'caddy.exe'
$receipt = Join-Path $destination 'sha256.txt'
if ((Test-Path -LiteralPath $binary) -and (Test-Path -LiteralPath $receipt)) {
    if ((Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash -eq (Get-Content $receipt -Raw).Trim()) {
        Write-Host "Verified existing Caddy/DuckDNS build: $binary"
        return
    }
    throw 'Installed Caddy checksum changed. Review it before reinstalling.'
}
if ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture -ne 'X64') {
    throw 'This build setup supports Windows x64.'
}
$sdk = Join-Path $tools "go-$($versions.GoVersion)"
$go = Join-Path $sdk 'go/bin/go.exe'
New-Item -ItemType Directory -Path $tools -Force | Out-Null
if (-not (Test-Path -LiteralPath $go)) {
    $archive = Join-Path $tools "go$($versions.GoVersion).windows-amd64.zip"
    Invoke-WebRequest "https://go.dev/dl/go$($versions.GoVersion).windows-amd64.zip" -OutFile $archive -TimeoutSec 120
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $versions.GoArchiveSha256) {
        throw 'Official Go SDK archive checksum mismatch.'
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $sdk -Force
    Remove-Item -LiteralPath $archive -Force
}
$previous = @{}
$values = @{
    PATH = "$(Split-Path $go);$env:PATH"
    GOTOOLCHAIN = 'local'
    GOMODCACHE = Join-Path $tools 'go-modules'
    GOCACHE = Join-Path $tools 'go-cache'
}
New-Item -ItemType Directory -Path $destination -Force | Out-Null
Push-Location $tools
try {
    foreach ($name in $values.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }
    & $go run "github.com/caddyserver/xcaddy/cmd/xcaddy@v$($versions.XCaddyVersion)" build `
        "v$($versions.CaddyVersion)" --with "github.com/caddy-dns/duckdns@v$($versions.DuckDnsVersion)" --output $binary
    if ($LASTEXITCODE -ne 0) { throw 'Pinned Caddy/DuckDNS build failed.' }
    $modules = & $binary list-modules --versions
    if ($LASTEXITCODE -ne 0 -or -not ($modules -match '^dns.providers.duckdns\s')) {
        throw 'The custom binary does not contain the required DuckDNS module.'
    }
    & $binary version
    (Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash | Set-Content -LiteralPath $receipt
    & $go version -m $binary | Set-Content -LiteralPath (Join-Path $destination 'build-info.txt')
    Write-Host 'Pinned Caddy with DuckDNS support installed. No system-wide Go installation was changed.'
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    Pop-Location
}

# The server is self-contained. Retain caches after a failed build for retry, but
# release these setup-only files after success, especially on small system disks.
$toolsAbsolute = [System.IO.Path]::GetFullPath($tools)
foreach ($name in @('go-cache', 'go-modules', "go-$($versions.GoVersion)")) {
    $target = [System.IO.Path]::GetFullPath((Join-Path $toolsAbsolute $name))
    if (-not $target.StartsWith($toolsAbsolute + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Build-cache cleanup target is outside the intended workspace tools folder.'
    }
    if (Test-Path -LiteralPath $target) {
        if ((Get-Item -LiteralPath $target).Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
            throw 'Refusing recursive cleanup of a build-cache reparse point.'
        }
        Remove-Item -LiteralPath $target -Recurse -Force
    }
}
Write-Host 'Temporary compiler and module caches removed; runtime binary and verification receipt retained.'
