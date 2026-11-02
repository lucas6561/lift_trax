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
Require ((Get-HttpsRunMode $false $null) -eq 'Network') 'Omitted mode did not select DuckDNS network HTTPS.'
Require ((Get-HttpsRunMode $true 'Network') -eq 'Network') 'Explicit network mode changed.'
Require ((Get-HttpsRunMode $true 'Preview') -eq 'Preview') 'Explicit preview mode changed.'
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
$script:startAttempts = 0
function Start-ScheduledTask { $script:startAttempts++ }
function Get-ScheduledTask {
    [pscustomobject]@{State=$(if ($script:startAttempts -ge 2) { 'Running' } else { 'Ready' })}
}
Start-HttpsRecoveryTask 'Synthetic LiftTrax Task' 2 1 0
Require ($script:startAttempts -eq 2) 'Recovery task start did not retry after a lost first start.'

function New-ScheduledTaskAction {
    param($Execute, $Argument, $WorkingDirectory)
    [pscustomobject]@{Execute=$Execute;Arguments=$Argument;WorkingDirectory=$WorkingDirectory}
}
$script:taskUpdates = 0
function Set-ScheduledTask {
    param($TaskName, $Action, $ErrorAction)
    $script:taskUpdates++
    $script:updatedAction = $Action
}
$startupScript = 'C:\checkout with spaces\scripts\https-startup.ps1'
$powerShellPath = 'C:\PowerShell with spaces\pwsh.exe'
$legacyAction = [pscustomobject]@{
    Execute=$powerShellPath
    Arguments='-NoProfile -NonInteractive -WindowStyle Hidden -File "' + $startupScript + '" -Action Run'
}
$task = [pscustomobject]@{TaskName='Synthetic LiftTrax Task';Actions=@($legacyAction)}
Require ((Get-HttpsRecoveryPowerShell $task $startupScript) -eq $powerShellPath) 'Legacy runtime discovery failed.'
Set-HttpsRecoveryTaskLauncher $task $startupScript
Require ($taskUpdates -eq 1) 'Legacy task was not upgraded.'
Require ($updatedAction.Execute -eq (Join-Path $env:SystemRoot 'System32/conhost.exe')) 'Recovery does not use the system console host.'
Require ($updatedAction.Arguments.StartsWith('--headless "' + $powerShellPath + '" ')) 'Recovery could create a visible console or lose a runtime path with spaces.'
Require ($updatedAction.WorkingDirectory -eq 'C:\checkout with spaces') 'Task working directory changed.'
$task.Actions = @($updatedAction)
Require ((Get-HttpsRecoveryPowerShell $task $startupScript) -eq $powerShellPath) 'Headless runtime discovery failed.'
Set-HttpsRecoveryTaskLauncher $task $startupScript
Require ($taskUpdates -eq 1) 'An already upgraded task was rewritten.'
Require ($null -eq (Get-HttpsRecoveryPowerShell $task 'C:\other checkout\scripts\https-startup.ps1')) 'Another checkout was accepted.'
try {
    Set-HttpsRecoveryTaskLauncher $task 'C:\other checkout\scripts\https-startup.ps1'
    throw 'An unrelated task was upgraded.'
} catch {
    Require ($_.Exception.Message.Contains('belongs to another checkout')) 'Unrelated task failure was not actionable.'
}
Require ($taskUpdates -eq 1) 'An unrelated task was modified.'
$task.Actions[0].Arguments += ' -ExtraCommand'
Require ($null -eq (Get-HttpsRecoveryPowerShell $task $startupScript)) 'Unexpected command arguments were accepted.'
$task.Actions = @($legacyAction, $legacyAction)
Require ($null -eq (Get-HttpsRecoveryPowerShell $task $startupScript)) 'Multiple task actions were accepted.'
Write-Host 'HTTPS recovery, process identity, preview isolation, and headless task migration checks passed.'
