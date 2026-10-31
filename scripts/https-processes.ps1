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

function Get-HttpsRecoveryAction($State) {
    if ($null -eq $State) { return 'Start' }
    # Automatic recovery must never replace an operator's developer preview.
    if ($State.Mode -ne 'Network') { return 'LeavePreview' }
    if ((Get-ManagedProcess $State.Proxy) -and (Get-ManagedProcess $State.Backend)) {
        return 'KeepRunning'
    }
    return 'Restart'
}
