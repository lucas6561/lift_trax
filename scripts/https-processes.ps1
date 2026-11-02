# Shared process identity checks. A reused PID must never authorize stopping a process.
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

function Get-HttpsRunMode([bool] $ModeWasSpecified, [string] $RequestedMode) {
    if ($ModeWasSpecified) { return $RequestedMode }
    return 'Network'
}

# Accept only this checkout's exact legacy or headless recovery command. Keep
# runtime discovery usable from the Windows PowerShell 5 batch bootstrap too.
function Get-HttpsRecoveryPowerShell($Task, [string] $StartupScript) {
    if (@($Task.Actions).Count -ne 1) { return $null }
    $action = $Task.Actions[0]
    $expected = '-NoProfile -NonInteractive -WindowStyle Hidden -File "' + $StartupScript + '" -Action Run'
    $executable = $action.Execute
    if ($executable -ieq (Join-Path $env:SystemRoot 'System32/conhost.exe')) {
        $match = [regex]::Match($action.Arguments, '^--headless "([^"]+)" (.*)$')
        if (-not $match.Success -or $match.Groups[2].Value -ine $expected) { return $null }
        $executable = $match.Groups[1].Value
    } elseif ($action.Arguments -ine $expected) {
        return $null
    }
    if (-not [System.IO.Path]::IsPathRooted($executable) -or
        [System.IO.Path]::GetFileName($executable) -ine 'pwsh.exe') { return $null }
    return $executable
}

function New-HttpsRecoveryTaskAction([string] $PowerShellPath, [string] $StartupScript) {
    # Unlike pwsh's WindowStyle, headless conhost never opens a Terminal window.
    $arguments = '--headless "' + $PowerShellPath + '" -NoProfile -NonInteractive -WindowStyle Hidden -File "' + $StartupScript + '" -Action Run'
    return New-ScheduledTaskAction -Execute (Join-Path $env:SystemRoot 'System32/conhost.exe') `
        -Argument $arguments -WorkingDirectory (Split-Path -Parent (Split-Path -Parent $StartupScript))
}

function Set-HttpsRecoveryTaskLauncher($Task, [string] $StartupScript) {
    $powerShellPath = Get-HttpsRecoveryPowerShell $Task $StartupScript
    if (-not $powerShellPath) { throw 'The startup task belongs to another checkout; no task or processes were changed.' }
    $action = New-HttpsRecoveryTaskAction $powerShellPath $StartupScript
    if ($Task.Actions[0].Execute -ine $action.Execute -or $Task.Actions[0].Arguments -cne $action.Arguments) {
        Set-ScheduledTask -TaskName $Task.TaskName -Action $action -ErrorAction Stop | Out-Null
    }
}

function Test-HttpsRecoveryCommand($Process, [string] $PowerShellPath, [string] $StartupScript) {
    if ($Process.ExecutablePath -ine $PowerShellPath) { return $false }
    # Start-Process/Windows may omit quotes around paths without spaces. Accept
    # only the complete watcher command, never a substring in another shell.
    $patterns = @($PowerShellPath, $StartupScript) | ForEach-Object {
        $literal = [regex]::Escape($_)
        if ($_ -match '\s') { '"' + $literal + '"' }
        else { '(?:"' + $literal + '"|' + $literal + ')' }
    }
    return $Process.CommandLine -imatch ('^' + $patterns[0] +
        ' -NoProfile -NonInteractive -WindowStyle Hidden -File ' + $patterns[1] + ' -Action Run$')
}

function Stop-HttpsRecoveryTask($Task, [string] $StartupScript) {
    $powerShellPath = Get-HttpsRecoveryPowerShell $Task $StartupScript
    if (-not $powerShellPath) { throw 'The startup task belongs to another checkout; no task or processes were changed.' }
    Stop-ScheduledTask -TaskName $Task.TaskName -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-ScheduledTask -TaskName $Task.TaskName -ErrorAction Stop).State -eq 'Running') {
        if ([DateTime]::UtcNow -ge $deadline) { throw 'Automatic recovery task did not stop; no server processes were changed.' }
        Start-Sleep -Milliseconds 200
    }
    # Stopping headless conhost can leave its PowerShell child alive. Capture
    # identities BEFORE reading command lines, then recheck before each stop:
    # PID reuse during discovery must not authorize stopping another process.
    $records = @{}
    Get-Process -Name pwsh -ErrorAction SilentlyContinue | Where-Object { $_.Path -ieq $powerShellPath } |
        ForEach-Object { $records[$_.Id] = Get-ProcessRecord $_ }
    foreach ($candidate in @(Get-CimInstance Win32_Process -Filter "Name = 'pwsh.exe'" -ErrorAction Stop)) {
        if ($records.ContainsKey([int]$candidate.ProcessId) -and
            (Test-HttpsRecoveryCommand $candidate $powerShellPath $StartupScript)) {
            $process = Get-ManagedProcess ([pscustomobject]$records[[int]$candidate.ProcessId])
            if ($process) {
                Stop-Process -InputObject $process -ErrorAction Stop
                if (-not $process.WaitForExit(10000)) { throw 'Automatic recovery watcher did not stop.' }
            }
        }
    }
}

function Start-HttpsRecoveryTask(
    [string] $TaskName,
    [int] $TimeoutSeconds = 15,
    [int] $PollMilliseconds = 200,
    [int] $StableMilliseconds = 1000
) {
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $runningSince = $null
    do {
        $state = (Get-ScheduledTask -TaskName $TaskName -ErrorAction Stop).State
        if ($state -eq 'Running') {
            if ($null -eq $runningSince) { $runningSince = [DateTime]::UtcNow }
            if (([DateTime]::UtcNow - $runningSince).TotalMilliseconds -ge $StableMilliseconds) { return }
        } else {
            $runningSince = $null
            Start-ScheduledTask -TaskName $TaskName
        }
        Start-Sleep -Milliseconds $PollMilliseconds
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Automatic HTTPS recovery task did not remain running: $TaskName"
}

function Get-HttpsRecoveryAction($State) {
    if ($null -eq $State) { return 'Start' }
    # Automatic recovery must never replace an operator's developer preview.
    if ($State.Mode -ne 'Network') { return 'LeavePreview' }
    if ((Get-ManagedProcess $State.Proxy) -and (Get-ManagedProcess $State.Backend)) {
        return 'KeepRunning'
    }
    return 'Restart'
}
