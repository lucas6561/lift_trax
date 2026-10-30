#Requires -Version 7.0
# Offline behavior checks: no credentials, DNS changes, certificates, or database writes.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'duckdns.ps1')
$privateToken = 'synthetic-secret-never-log'
$script:requests = [System.Collections.Generic.List[Uri]]::new()
$script:responseMode = 'OK'
function Invoke-WebRequest {
    param([Uri] $Uri, $TimeoutSec, $MaximumRedirection, [switch] $SkipHttpErrorCheck, [switch] $Verbose, [switch] $Debug)
    $script:requests.Add($Uri)
    if ($script:responseMode -eq 'Exception') { throw "Synthetic network error for $Uri" }
    if ($script:responseMode -eq 'Bytes') { return @{StatusCode=200;Content=[System.Text.Encoding]::UTF8.GetBytes("OK`n")} }
    return @{StatusCode=200;Content=$script:responseMode}
}
function Require([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}
function Require-Failure([scriptblock] $Operation, [string] $Expected) {
    try { & $Operation; throw 'Expected failure did not occur.' }
    catch {
        Require ($_.Exception.Message.Contains($Expected)) 'Failure was not actionable.'
        Require (-not $_.Exception.Message.Contains($privateToken)) 'Failure exposed a credential.'
    }
}

$settings = [pscustomobject]@{Hostname='selected-host.duckdns.org';LanAddress='8.8.8.8'}
Require-Failure { Assert-DuckDnsLanSettings $settings } 'non-private LAN address'
$settings.LanAddress='127.0.0.1'
Require-Failure { Assert-DuckDnsLanSettings $settings } 'non-private LAN address'
$settings.Hostname='another.example.com'
Require-Failure { Assert-DuckDnsLanSettings $settings } 'registered single-label DuckDNS'
$settings.Hostname='selected-host.duckdns.org'
$settings.LanAddress='not-an-address'
Require-Failure { Assert-DuckDnsLanSettings $settings } 'private IPv4 address'

Set-DuckDnsLanAddress 'selected-host.duckdns.org' $privateToken '192.168.1.42'
Require ($requests.Count -eq 2) 'Address update did not clear stale AAAA before setting A.'
Require ($requests[0].Query.Contains('clear=true')) 'Stale address records were not cleared.'
Require ($requests[1].Query.Contains('ip=192.168.1.42')) 'Explicit LAN IP was missing; WAN auto-detection would be unsafe.'
Require ($requests[1].Host -eq 'www.duckdns.org' -and $requests[1].Scheme -eq 'https') 'Credential destination changed.'
$responseMode='Bytes'
Set-DuckDnsLanAddress 'selected-host.duckdns.org' $privateToken '192.168.1.42'
$responseMode='KO'
Require-Failure { Set-DuckDnsLanAddress 'selected-host.duckdns.org' $privateToken '192.168.1.42' } 'rejected'
$responseMode='Exception'
Require-Failure { Set-DuckDnsLanAddress 'selected-host.duckdns.org' $privateToken '192.168.1.42' } 'update failed'
Write-Host 'HTTPS settings and credential-redaction checks passed without network or database access.'
