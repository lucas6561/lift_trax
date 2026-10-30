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
$auditDirectory = Join-Path $repo 'data/https'
New-Item -ItemType Directory -Path $auditDirectory -Force | Out-Null
$auditPath = Join-Path $auditDirectory ('firewall-before-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '.json')
$applicationRules | Select-Object Name,DisplayName,Enabled,Profile,Action,Direction |
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
foreach ($rule in $applicationRules) {
    if ($rule.Direction.ToString() -eq 'Inbound' -and $rule.Enabled.ToString() -eq 'True' -and
        $rule.Action.ToString() -eq 'Block' -and $rule.Profile.ToString() -match 'Private|Any') {
        $rule | Disable-NetFirewallRule | Out-Null
    }
}
Write-Host 'Caddy is allowed on TCP 443/80 from the local subnet on Private networks only.'
Write-Host 'Conflicting private Caddy blocks and broader Caddy allows are disabled; other applications were not changed.'
Write-Host "Prior Caddy rule settings saved to $auditPath"
Write-Host 'The Java backend port 18080 was not opened. No Public-network rule was added.'
