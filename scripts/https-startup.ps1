#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Install', 'Uninstall', 'Status', 'Run', 'Once')]
    [string] $Action = 'Status'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'https-processes.ps1')
$runtime = Join-Path $repo 'data/https'
$statePath = Join-Path $runtime 'processes.json'
$taskName = 'LiftTrax Home HTTPS'
$launcher = Join-Path $PSScriptRoot 'https.ps1'
$log = Join-Path $runtime 'startup.log'

if ($Action -eq 'Install') {
    $existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($existing) { throw 'Startup task already exists. Inspect Status or Uninstall before replacing it.' }
    $user = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $pwsh = Join-Path $PSHOME 'pwsh.exe'
    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $runtime 'powershell-path.txt') -Value $pwsh
    $arguments = '-NoProfile -NonInteractive -WindowStyle Hidden -File "' + $PSCommandPath + '" -Action Run'
    $taskAction = New-ScheduledTaskAction -Execute $pwsh -Argument $arguments -WorkingDirectory $repo
    $trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
    $principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew `
        -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
        -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger $trigger `
        -Principal $principal -Settings $settings `
        -Description 'Starts and recovers existing home-only LiftTrax HTTPS after Windows sign-in. No password stored.' | Out-Null
    Start-ScheduledTask -TaskName $taskName
    Write-Host 'Home HTTPS will start automatically after Windows sign-in; recovery is running now.'
    return
}
if ($Action -eq 'Status') {
    Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue |
        Select-Object TaskName, State, Principal, Triggers, Actions
    & $launcher -Action Status
    return
}
if ($Action -eq 'Uninstall') {
    $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if ($task) {
        # Refuse to remove an unrelated task that happens to have the same name.
        $expected = '-File "' + $PSCommandPath + '" -Action Run'
        if (@($task.Actions).Count -ne 1 -or -not $task.Actions[0].Arguments.EndsWith($expected)) {
            throw 'Task does not belong to this checkout; no task was changed.'
        }
        Stop-ScheduledTask -TaskName $taskName
        Unregister-ScheduledTask -TaskName $taskName -Confirm:$false
    }
    Write-Host 'Automatic recovery removed. Use https.bat -Action Stop to stop HTTPS. Data and certificates retained.'
    return
}

New-Item -ItemType Directory -Path $runtime -Force | Out-Null
# Also serialize manual Once checks with the scheduled supervisor, per checkout.
$lock = $null
try {
    try {
        $lock = [System.IO.File]::Open((Join-Path $runtime 'startup.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch [System.IO.IOException] {
        if ($Action -eq 'Once') { throw 'Automatic recovery is already running.' }
        return
    }
    do {
        try {
            $state = if (Test-Path -LiteralPath $statePath) {
                Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
            } else { $null }
            $decision = Get-HttpsRecoveryAction $state
            if ($decision -in @('Start', 'Restart')) {
                Add-Content -LiteralPath $log -Value "$([DateTime]::UtcNow.ToString('o')) Recovering home HTTPS ($decision)."
                if ($decision -eq 'Restart') { & $launcher -Action Stop *>> $log }
                # DNS was configured during provisioning. A temporary internet outage
                # must not prevent local startup with the existing certificate/build.
                & $launcher -Action Start -Mode Network -SkipBuild -SkipDnsUpdate *>> $log
            }
            if ($Action -eq 'Once') { Write-Host "HTTPS recovery check: $decision"; return }
        } catch {
            Add-Content -LiteralPath $log -Value "$([DateTime]::UtcNow.ToString('o')) Recovery failed: $($_.Exception.Message)"
            if ($Action -eq 'Once') { throw }
        }
        Start-Sleep -Seconds 30
    } while ($true)
} finally {
    if ($lock) { $lock.Dispose() }
}
