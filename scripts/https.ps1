#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Install', 'Provision', 'Start', 'Stop', 'Status', 'Certificate')]
    [string] $Action = 'Status',
    [ValidateSet('Preview', 'Network')]
    [string] $Mode = 'Preview'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'duckdns.ps1')
$runtime = Join-Path $repo 'data/https'
$statePath = Join-Path $runtime 'processes.json'
$settingsPath = Join-Path $repo 'config/lifttrax-https.override.json'
$versions = Get-Content (Join-Path $repo 'config/https/toolchain.json') -Raw | ConvertFrom-Json
$caddyDirectory = Join-Path $runtime "tools/caddy-$($versions.CaddyVersion)-duckdns-$($versions.DuckDnsVersion)"
$caddy = Join-Path $caddyDirectory 'caddy.exe'
$rootCertificate = Join-Path $runtime 'pki/pki/authorities/local/root.crt'

function Get-ManagedProcess($Record) {
    $process = Get-Process -Id $Record.Id -ErrorAction SilentlyContinue
    if ($process -and $process.Path -eq $Record.Path -and
        $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $Record.StartTicks) {
        return $process
    }
    return $null
}

function Get-ProcessRecord($Process) {
    return @{
        Id = $Process.Id
        Path = $Process.Path
        StartTicks = $Process.StartTime.ToUniversalTime().Ticks.ToString()
    }
}

function Stop-ManagedServer {
    if (-not (Test-Path -LiteralPath $statePath)) { return }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    foreach ($record in @($state.Proxy, $state.Backend)) {
        $process = Get-ManagedProcess $record
        if ($process) {
            Stop-Process -InputObject $process
            $process.WaitForExit(10000) | Out-Null
        }
    }
    Remove-Item -LiteralPath $statePath
}

function Assert-PortFree([int] $Port) {
    foreach ($address in @([System.Net.IPAddress]::Any, [System.Net.IPAddress]::IPv6Any)) {
        $listener = [System.Net.Sockets.TcpListener]::new($address, $Port)
        $listener.ExclusiveAddressUse = $true
        try { $listener.Start() }
        catch { throw "Port $Port is already in use. Stop its existing server yourself; no process was stopped." }
        finally { $listener.Stop() }
    }
}

function Invoke-Backup([string] $Label) {
    $backupOutput = & $script:java "-Dlifttrax.config=$script:appConfig" -cp $script:classpath `
        com.lifttrax.cli.PostgresSqliteBackupCli (Join-Path $runtime "backups/$Label.db")
    if ($LASTEXITCODE -ne 0) { throw 'Database backup failed; HTTPS startup aborted.' }
    $backupLine = @($backupOutput | Where-Object { $_ -like 'Postgres snapshot created: *' })
    if ($backupLine.Count -ne 1) { throw 'Backup did not report exactly one validated snapshot.' }
    Write-Host ($backupOutput -join [Environment]::NewLine)
    return $backupLine[0].Substring('Postgres snapshot created: '.Length)
}

function Start-HiddenProcess([string] $Executable, [string[]] $Arguments, [string] $Label) {
    # Start-Process joins arguments on Windows; quote each path-bearing argument.
    $quoted = @($Arguments | ForEach-Object {
        if ($_.Contains('"') -or $_.EndsWith('\')) { throw 'Unsupported quote or trailing slash in process argument.' }
        '"' + $_ + '"'
    })
    return Start-Process -FilePath $Executable -ArgumentList $quoted -WorkingDirectory $repo `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime "$Label.out.log") `
        -RedirectStandardError (Join-Path $runtime "$Label.err.log")
}

New-Item -ItemType Directory -Path $runtime -Force | Out-Null
switch ($Action) {
    'Install' {
        & (Join-Path $PSScriptRoot 'install-caddy-duckdns.ps1')
        return
    }
    'Stop' { Stop-ManagedServer; Write-Host 'Managed HTTPS processes stopped. Database and certificates retained.'; return }
    'Status' {
        if (-not (Test-Path -LiteralPath $statePath)) { Write-Host 'Managed HTTPS server is stopped.'; return }
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        [pscustomobject]@{
            Url = $state.Url; Mode = $state.Mode
            ProxyRunning = [bool](Get-ManagedProcess $state.Proxy)
            BackendRunning = [bool](Get-ManagedProcess $state.Backend)
            BeforeSnapshot = $state.BeforeSnapshot; AfterSnapshot = $state.AfterSnapshot
        }
        return
    }
    'Certificate' {
        Write-Host 'Network mode uses a publicly trusted certificate. No certificate installation is needed on client devices.'
        return
    }
}

if (-not (Test-Path -LiteralPath $caddy)) { throw 'Run scripts/https.ps1 -Action Install first.' }
if (-not (Test-Path -LiteralPath $settingsPath)) {
    throw 'Copy config/lifttrax-https.example.json to config/lifttrax-https.override.json and set the hostname and existing app config.'
}
$settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
if ($settings.Hostname -notmatch '^(?=.{1,253}$)[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?$') {
    throw 'Hostname must be a DNS hostname or IPv4 address, without a scheme, port, or path.'
}
$script:appConfig = [System.IO.Path]::GetFullPath((Join-Path $repo $settings.AppConfig))
if (-not (Test-Path -LiteralPath $appConfig -PathType Leaf)) { throw 'The existing app configuration does not exist.' }
if ($Action -eq 'Start' -and (Test-Path -LiteralPath $statePath)) {
    throw 'A managed HTTPS state file already exists. Use Status, then Stop before starting again.'
}

$isPublic = $Action -eq 'Provision' -or $Mode -eq 'Network'
$isLocalListener = $Action -eq 'Provision' -or $Mode -eq 'Preview'
$duckDnsToken = $null
if ($isPublic) {
    Assert-DuckDnsLanSettings $settings
    $duckDnsToken = Get-DuckDnsToken $repo
}
$siteHost = if ($isPublic) { $settings.Hostname } else { 'lifttrax-preview.localhost' }
$httpsPort = if ($isLocalListener) { 8443 } else { 443 }
$httpPort = if ($isLocalListener) { 18081 } else { 80 }
$legacyPort = if ($isLocalListener) { 18082 } else { 8080 }
$backendPort = 18080
$proxyPorts = @($httpsPort, $httpPort)
if ($isLocalListener) { $proxyPorts += $legacyPort }
foreach ($port in $proxyPorts) { Assert-PortFree $port }
if ($Action -eq 'Start') { Assert-PortFree $backendPort }
$javaCandidate = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
# Oracle's PATH entry can be a short-lived launcher. Start the actual JVM so its
# recorded PID owns the backend for the full lifetime, including Stop/rollback.
$javaSettings = & $javaCandidate -XshowSettings:properties -version 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Java runtime discovery failed.' }
$javaHomeLine = @($javaSettings | ForEach-Object { $_.ToString() } | Where-Object { $_ -match '^\s*java.home = ' })
if ($javaHomeLine.Count -ne 1) { throw 'Could not locate the actual Java runtime.' }
$script:java = Join-Path ($javaHomeLine[0] -replace '^\s*java.home = ', '') 'bin/java.exe'
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'The actual Java executable does not exist.' }
$script:classpath = Join-Path $repo 'build/install/lift-trax-java/lib/*'
Push-Location $repo
$backendProcess = $null
$proxyProcess = $null
$proxyEnvironment = @{}
try {
    if ($isPublic) { Set-DuckDnsLanAddress $siteHost $duckDnsToken $settings.LanAddress }
    if ($Action -eq 'Start') {
      & (Join-Path $repo 'gradlew.bat') --quiet installDist
      if ($LASTEXITCODE -ne 0) { throw 'Application build failed.' }
      $before = Invoke-Backup 'before-https'
      $backendProcess = Start-HiddenProcess $java @(
        "-Dlifttrax.config=$appConfig", '-Dlifttrax.web.bind=127.0.0.1',
        '-Dlifttrax.auth.secureCookies=true', "-Dlifttrax.auth.redirectUri=https://${siteHost}:$httpsPort/auth/callback",
        '-cp', $classpath, 'com.lifttrax.cli.WebServerCli', "$backendPort"
    ) 'backend'
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        if ($backendProcess.HasExited) { throw 'Backend exited. See data/https/backend.err.log.' }
        try {
            $health = Invoke-WebRequest "http://127.0.0.1:$backendPort/health" -TimeoutSec 2
            if ($health.StatusCode -eq 200) { break }
        } catch { Start-Sleep -Milliseconds 300 }
    } while ([DateTime]::UtcNow -lt $deadline)
      if ([DateTime]::UtcNow -ge $deadline) { throw 'Backend readiness timed out.' }
    }
    $values = @{
        LIFTTRAX_TLS_STORAGE = (Join-Path $runtime $(if ($isPublic) { 'public-pki' } else { 'pki' })).Replace('\', '/')
        LIFTTRAX_HTTPS_HOST = $siteHost; LIFTTRAX_HTTPS_PORT = "$httpsPort"
        LIFTTRAX_HTTP_PORT = "$httpPort"; LIFTTRAX_LEGACY_PORT = "$legacyPort"
        LIFTTRAX_BACKEND_PORT = "$backendPort"
        LIFTTRAX_PROXY_BIND = $(if ($isLocalListener) { '127.0.0.1' } else { '0.0.0.0' })
    }
    if ($isPublic) { $values['LIFTTRAX_DUCKDNS_TOKEN'] = $duckDnsToken }
    foreach ($name in $values.Keys) {
        $proxyEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }
    $caddyfile = Join-Path $repo $(if ($isPublic) { 'config/https/Caddyfile' } else { 'config/https/Caddyfile.preview' })
    & $caddy validate --config $caddyfile --adapter caddyfile
    if ($LASTEXITCODE -ne 0) { throw 'Proxy configuration validation failed.' }
    $label = if ($Action -eq 'Provision') { 'provision' } else { 'proxy' }
    $proxyProcess = Start-HiddenProcess $caddy @('run', '--config', $caddyfile, '--adapter', 'caddyfile') $label
    $deadline = [DateTime]::UtcNow.AddSeconds($(if ($isPublic) { 600 } else { 30 }))
    do {
        if ($proxyProcess.HasExited) { throw "Proxy exited. See the private data/https/$label.err.log." }
        if ($isPublic -or (Test-Path -LiteralPath $rootCertificate)) {
            $trustArguments = if ($isPublic) { @() } else { @('--cacert', $rootCertificate) }
            $code = & curl.exe --silent --noproxy '*' --ssl-revoke-best-effort @trustArguments `
                --resolve "${siteHost}:${httpsPort}:127.0.0.1" --max-time 2 --output NUL --write-out '%{http_code}' `
                "https://${siteHost}:$httpsPort/health"
            if ($LASTEXITCODE -eq 0 -and ($code -eq '200' -or ($Action -eq 'Provision' -and $code -eq '502'))) { break }
        }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    if ([DateTime]::UtcNow -ge $deadline) { throw 'HTTPS readiness timed out.' }
    if ($Action -eq 'Provision') {
        Write-Host "Public certificate issued and validated for $siteHost using normal Windows trust. No client enrollment required."
        return
    }
    $after = Invoke-Backup 'after-https'
    & $java -cp $classpath com.lifttrax.cli.CompareSnapshotsCli $before $after
    if ($LASTEXITCODE -ne 0) { throw 'Snapshot contents changed. HTTPS startup aborted; inspect snapshots before proceeding.' }
    @{
        Mode = $Mode; Url = "https://${siteHost}:$httpsPort"
        Proxy = Get-ProcessRecord $proxyProcess; Backend = Get-ProcessRecord $backendProcess
        BeforeSnapshot = $before; AfterSnapshot = $after
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $statePath
    Write-Host "HTTPS ready: https://${siteHost}:$httpsPort"
    if ($Mode -eq 'Network') { Write-Host 'The existing HTTP server on port 8080 is independent and remains available.' }
    Write-Host $(if ($isPublic) { 'Open this address normally on client devices; no certificate installation is needed.' } else { 'Local developer preview only; client-device enrollment is not the network deployment path.' })
} catch {
    foreach ($process in @($proxyProcess, $backendProcess)) {
        if ($process -and -not $process.HasExited) { Stop-Process -InputObject $process }
    }
    throw
} finally {
    if ($Action -eq 'Provision' -and $proxyProcess -and -not $proxyProcess.HasExited) {
        Stop-Process -InputObject $proxyProcess
    }
    foreach ($name in $proxyEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $proxyEnvironment[$name], 'Process')
    }
    $duckDnsToken = $null
    Pop-Location
}
