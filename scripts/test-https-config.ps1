#Requires -Version 7.0
# Validates both real Caddy configurations without issuing certificates or updating DNS.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$versions = Get-Content (Join-Path $repo 'config/https/toolchain.json') -Raw | ConvertFrom-Json
$caddy = Join-Path $repo "data/https/tools/caddy-$($versions.CaddyVersion)-duckdns-$($versions.DuckDnsVersion)/caddy.exe"
if (-not (Test-Path -LiteralPath $caddy)) { throw 'Install Caddy/DuckDNS before testing its configuration.' }
$values = @{
    LIFTTRAX_TLS_STORAGE = (Join-Path $repo 'build/https-config-test').Replace('\', '/')
    LIFTTRAX_HTTPS_HOST = 'test-host.duckdns.org'; LIFTTRAX_HTTPS_PORT = '8443'
    LIFTTRAX_HTTP_PORT = '18081'; LIFTTRAX_LEGACY_PORT = '8080'
    LIFTTRAX_BACKEND_PORT = '18080'; LIFTTRAX_PROXY_BIND = '127.0.0.1'
    LIFTTRAX_DUCKDNS_TOKEN = 'offline-synthetic-token'
}
$previous = @{}
try {
    foreach ($name in $values.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }
    foreach ($file in @('Caddyfile', 'Caddyfile.preview')) {
        $config = Join-Path $repo "config/https/$file"
        $json = & $caddy adapt --config $config --adapter caddyfile
        if ($LASTEXITCODE -ne 0) { throw "$file did not adapt successfully." }
        $adapted = $json | ConvertFrom-Json
        if ($file -eq 'Caddyfile' -and ($json -notmatch '"name"\s*:\s*"duckdns"' -or $json -match '"module"\s*:\s*"internal"')) {
            throw 'Network mode must use DuckDNS ACME, never the internal CA.'
        }
        if ($file -eq 'Caddyfile') {
            $listeners = @($adapted.apps.http.servers.PSObject.Properties | ForEach-Object { $_.Value.listen })
            if ($listeners | Where-Object { $_ -match ':8080$' }) {
                throw 'Network HTTPS must not reserve the existing HTTP server port 8080.'
            }
        }
        if ($file -eq 'Caddyfile.preview' -and $json -notmatch '"module"\s*:\s*"internal"') {
            throw 'Developer preview must use its isolated internal CA.'
        }
        & $caddy validate --config $config --adapter caddyfile
        if ($LASTEXITCODE -ne 0) { throw "$file did not validate with the installed provider." }
        Write-Host "Verified actual Caddy configuration: $file"
    }
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
