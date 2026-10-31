#Requires -Version 5.1
# Bootstrap through Windows' built-in shell; PowerShell 7 need not be on PATH.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$runtimePath = Join-Path $repo 'data/https/powershell-path.txt'
$candidates = @()
$command = Get-Command pwsh.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($command) { $candidates += $command.Source }
if ($env:ProgramFiles) { $candidates += Join-Path $env:ProgramFiles 'PowerShell/7/pwsh.exe' }
if (Test-Path -LiteralPath $runtimePath) {
    $candidates += (Get-Content -LiteralPath $runtimePath -Raw).Trim()
}
# Existing installations already registered an absolute executable path. Only
# accept the task if it invokes this checkout's own startup script.
try {
    $task = Get-ScheduledTask -TaskName 'LiftTrax Home HTTPS' -ErrorAction Stop
    $expected = '-File "' + (Join-Path $PSScriptRoot 'https-startup.ps1') + '" -Action Run'
    if (@($task.Actions).Count -eq 1 -and $task.Actions[0].Arguments.EndsWith($expected)) {
        $candidates += $task.Actions[0].Execute
    }
} catch { }
$executable = $candidates | Where-Object {
    $_ -and [System.IO.Path]::IsPathRooted($_) -and
    [System.IO.Path]::GetFileName($_) -eq 'pwsh.exe' -and (Test-Path -LiteralPath $_ -PathType Leaf)
} | Select-Object -First 1
if (-not $executable) {
    Write-Error 'PowerShell 7 was not found. Install PowerShell 7, then retry https.bat. Windows PowerShell 5 cannot run the HTTPS server scripts.'
    exit 1
}
& $executable -NoProfile -ExecutionPolicy RemoteSigned -File (Join-Path $PSScriptRoot 'https.ps1') @args
exit $LASTEXITCODE
