#Requires -Version 7.0
# Offline checks: no DNS updates, task registration, process stops or database writes.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'https-processes.ps1')
function Require([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}
$process = Get-Process -Id $PID
$current = [pscustomobject](Get-ProcessRecord $process)
$reused = [pscustomobject]@{Id=$PID;Path=$current.Path;StartTicks='0'}
$wrongPath = [pscustomobject]@{Id=$PID;Path='C:/unrelated.exe';StartTicks=$current.StartTicks}
Require ($null -ne (Get-ManagedProcess $current)) 'Actual process identity was rejected.'
Require ($null -eq (Get-ManagedProcess $reused)) 'A reused PID was accepted.'
Require ($null -eq (Get-ManagedProcess $wrongPath)) 'An unrelated executable was accepted.'
Require ((Get-HttpsRecoveryAction $null) -eq 'Start') 'First startup would not recover.'
$state = [pscustomobject]@{Mode='Network';Proxy=$current;Backend=$current}
Require ((Get-HttpsRecoveryAction $state) -eq 'KeepRunning') 'Healthy processes would be restarted.'
$state.Proxy=$reused
Require ((Get-HttpsRecoveryAction $state) -eq 'Restart') 'Dead proxy would not recover.'
$state.Proxy=$current
$state.Backend=$reused
Require ((Get-HttpsRecoveryAction $state) -eq 'Restart') 'Dead backend would not recover.'
$state.Proxy=$reused
Require ((Get-HttpsRecoveryAction $state) -eq 'Restart') 'Stale reboot state would not recover.'
$state.Mode='Preview'
Require ((Get-HttpsRecoveryAction $state) -eq 'LeavePreview') 'Automatic recovery would replace a preview.'
Write-Host 'HTTPS reboot, partial failure, process identity, and preview isolation checks passed.'
