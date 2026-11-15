#Requires -Version 7.0
#Requires -RunAsAdministrator
# Run once from an elevated PowerShell window after installing Caddy.
param([switch] $TrustCurrentNetwork)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$versions = Get-Content (Join-Path $repo 'config/https/toolchain.json') -Raw | ConvertFrom-Json
$program = Join-Path $repo "data/https/tools/caddy-$($versions.CaddyVersion)-duckdns-$($versions.DuckDnsVersion)/caddy.exe"
if (-not (Test-Path -LiteralPath $program -PathType Leaf)) {
    throw 'Install Caddy with https.bat -Action Install first.'
}
$javaCandidate = (Get-Command java -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$javaSettings = & $javaCandidate -XshowSettings:properties -version 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Java runtime discovery failed.' }
$javaHomeLine = @($javaSettings | ForEach-Object { $_.ToString() } | Where-Object { $_ -match '^\s*java.home = ' })
if ($javaHomeLine.Count -ne 1) { throw 'Could not locate the actual Java runtime.' }
$javaProgram = Join-Path ($javaHomeLine[0] -replace '^\s*java.home = ', '') 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaProgram -PathType Leaf)) { throw 'The actual Java executable does not exist.' }
if ($TrustCurrentNetwork) {
    $connections = @(Get-NetConnectionProfile)
    if ($connections.Count -ne 1) {
        throw 'Multiple or no network connections found. Select your trusted home network manually in Windows Settings.'
    }
    if ($connections[0].NetworkCategory.ToString() -ne 'Private') {
        Set-NetConnectionProfile -InterfaceIndex $connections[0].InterfaceIndex -NetworkCategory Private
        Write-Host 'The current connection is now Private, as explicitly requested with -TrustCurrentNetwork.'
    }
}
$ruleName = 'LiftTrax-LocalHttps'
$existing = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
if ($existing -and $existing.Group -ne 'LiftTrax') {
    throw 'An unrelated firewall rule uses this name; review it manually.'
}
$applicationRules = @(Get-NetFirewallApplicationFilter -Program $program | Get-NetFirewallRule)
$javaRules = @(Get-NetFirewallApplicationFilter -Program $javaProgram | Get-NetFirewallRule)
$auditDirectory = Join-Path $repo 'data/https'
New-Item -ItemType Directory -Path $auditDirectory -Force | Out-Null
$auditPath = Join-Path $auditDirectory ('firewall-before-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '.json')
@($applicationRules; $javaRules) | Select-Object Name,DisplayName,Enabled,Profile,Action,Direction |
    ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $auditPath

# A canceled Windows app prompt can leave explicit blocks that override an allow.
# Reconcile only inbound rules attached to this exact, dedicated Caddy executable.
# Disable broader allows first so the canonical rule remains the sole exposure.
foreach ($rule in $applicationRules) {
    if ($rule.Name -ne $ruleName -and $rule.Direction.ToString() -eq 'Inbound' -and
        $rule.Enabled.ToString() -eq 'True' -and $rule.Action.ToString() -eq 'Allow') {
        $rule | Disable-NetFirewallRule | Out-Null
    }
}
if ($existing) {
    $existing | Set-NetFirewallRule -Enabled True -Direction Inbound -Action Allow -Profile Private -EdgeTraversalPolicy Block
    $existing | Get-NetFirewallApplicationFilter | Set-NetFirewallApplicationFilter -Program $program
    $existing | Get-NetFirewallPortFilter | Set-NetFirewallPortFilter -Protocol TCP -LocalPort 443,80
    $existing | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -RemoteAddress LocalSubnet
} else {
    New-NetFirewallRule -Name $ruleName -DisplayName 'LiftTrax HTTPS (private local network)' `
        -Group LiftTrax -Enabled True -Direction Inbound -Action Allow -Profile Private `
        -Protocol TCP -LocalPort 443,80 -Program $program -RemoteAddress LocalSubnet `
        -EdgeTraversalPolicy Block | Out-Null
}
$blockRuleName = 'LiftTrax-BlockDirectJava'
$existingBlock = Get-NetFirewallRule -Name $blockRuleName -ErrorAction SilentlyContinue
if ($existingBlock -and $existingBlock.Group -ne 'LiftTrax') {
    throw 'An unrelated firewall rule uses the direct-Java block name; review it manually.'
}
if ($existingBlock) {
    $existingBlock | Set-NetFirewallRule -Enabled True -Direction Inbound -Action Block -Profile Any -EdgeTraversalPolicy Block
    $existingBlock | Get-NetFirewallApplicationFilter | Set-NetFirewallApplicationFilter -Program $javaProgram
    $existingBlock | Get-NetFirewallPortFilter | Set-NetFirewallPortFilter -Protocol TCP -LocalPort 8080,18080
    $existingBlock | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter -RemoteAddress Any
} else {
    New-NetFirewallRule -Name $blockRuleName -DisplayName 'LiftTrax block direct Java HTTP' `
        -Group LiftTrax -Enabled True -Direction Inbound -Action Block -Profile Any `
        -Protocol TCP -LocalPort 8080,18080 -Program $javaProgram -RemoteAddress Any `
        -EdgeTraversalPolicy Block | Out-Null
}
foreach ($rule in $applicationRules) {
    if ($rule.Direction.ToString() -eq 'Inbound' -and $rule.Enabled.ToString() -eq 'True' -and
        $rule.Action.ToString() -eq 'Block' -and $rule.Profile.ToString() -match 'Private|Any') {
        $rule | Disable-NetFirewallRule | Out-Null
    }
}
$verifiedAllow = Get-NetFirewallRule -Name $ruleName -ErrorAction Stop
$verifiedAllowApplication = $verifiedAllow | Get-NetFirewallApplicationFilter
$verifiedAllowPorts = $verifiedAllow | Get-NetFirewallPortFilter
$verifiedAllowAddresses = $verifiedAllow | Get-NetFirewallAddressFilter
$verifiedBlock = Get-NetFirewallRule -Name $blockRuleName -ErrorAction Stop
$verifiedBlockApplication = $verifiedBlock | Get-NetFirewallApplicationFilter
$verifiedBlockPorts = $verifiedBlock | Get-NetFirewallPortFilter
$verifiedBlockAddresses = $verifiedBlock | Get-NetFirewallAddressFilter
$allowPortText = @($verifiedAllowPorts.LocalPort) -join ','
$blockPortText = @($verifiedBlockPorts.LocalPort) -join ','
if ($verifiedAllow.Enabled.ToString() -ne 'True' -or $verifiedAllow.Direction.ToString() -ne 'Inbound' -or
    $verifiedAllow.Action.ToString() -ne 'Allow' -or $verifiedAllow.Profile.ToString() -ne 'Private' -or
    $verifiedAllowApplication.Program -ine $program -or $allowPortText -notmatch '(^|,)443(,|$)' -or
    $allowPortText -notmatch '(^|,)80(,|$)' -or $verifiedAllowAddresses.RemoteAddress -notcontains 'LocalSubnet') {
    throw 'The final Caddy firewall allow rule did not match the required private/local-subnet topology.'
}
if ($verifiedBlock.Enabled.ToString() -ne 'True' -or $verifiedBlock.Direction.ToString() -ne 'Inbound' -or
    $verifiedBlock.Action.ToString() -ne 'Block' -or $verifiedBlock.Profile.ToString() -ne 'Any' -or
    $verifiedBlockApplication.Program -ine $javaProgram -or $blockPortText -notmatch '(^|,)8080(,|$)' -or
    $blockPortText -notmatch '(^|,)18080(,|$)' -or $verifiedBlockAddresses.RemoteAddress -notcontains 'Any') {
    throw 'The final direct-Java firewall block did not match the required all-profile topology.'
}
$afterPath = Join-Path $auditDirectory ('firewall-after-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '.json')
@(
    [pscustomobject]@{
        Name=$verifiedAllow.Name; Enabled=$verifiedAllow.Enabled.ToString(); Direction=$verifiedAllow.Direction.ToString()
        Action=$verifiedAllow.Action.ToString(); Profile=$verifiedAllow.Profile.ToString()
        Program=$verifiedAllowApplication.Program; Protocol=$verifiedAllowPorts.Protocol.ToString()
        LocalPort=@($verifiedAllowPorts.LocalPort); RemoteAddress=@($verifiedAllowAddresses.RemoteAddress)
    }
    [pscustomobject]@{
        Name=$verifiedBlock.Name; Enabled=$verifiedBlock.Enabled.ToString(); Direction=$verifiedBlock.Direction.ToString()
        Action=$verifiedBlock.Action.ToString(); Profile=$verifiedBlock.Profile.ToString()
        Program=$verifiedBlockApplication.Program; Protocol=$verifiedBlockPorts.Protocol.ToString()
        LocalPort=@($verifiedBlockPorts.LocalPort); RemoteAddress=@($verifiedBlockAddresses.RemoteAddress)
    }
) | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $afterPath
Write-Host 'Caddy is allowed on TCP 443/80 from the local subnet on Private networks only.'
Write-Host 'Conflicting private Caddy blocks and broader Caddy allows are disabled; other applications were not changed.'
Write-Host "Prior Caddy rule settings saved to $auditPath"
Write-Host "Verified final firewall rule settings saved to $afterPath"
Write-Host 'Direct Java HTTP on TCP 8080/18080 is blocked inbound on every network profile; loopback remains available.'
Write-Host 'No Public-network allow rule was added.'
