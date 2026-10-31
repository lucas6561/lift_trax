#Requires -Version 7.0
# Exercise the real batch launcher from Windows PowerShell 5 with pwsh absent
# from PATH, including paths with spaces, argument forwarding and exit status.
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $repo ('build/https launcher test ' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts'), (Join-Path $fixture 'data/https') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repo 'https.bat') -Destination $fixture
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'invoke-https.ps1') -Destination (Join-Path $fixture 'scripts')
Set-Content -LiteralPath (Join-Path $fixture 'data/https/powershell-path.txt') -Value (Join-Path $PSHOME 'pwsh.exe')
Set-Content -LiteralPath (Join-Path $fixture 'scripts/https.ps1') -Value @'
#Requires -Version 7.0
param([string] $Action, [string] $Mode)
if ($Action -ne 'Status' -or $Mode -ne 'a value with spaces') { exit 91 }
Write-Output 'Launcher forwarded arguments correctly.'
exit 37
'@
$previousPath = $env:PATH
$previousProgramFiles = $env:ProgramFiles
try {
    $env:PATH = "$env:SystemRoot\System32"
    $env:ProgramFiles = Join-Path $fixture 'no installed PowerShell'
    $output = & (Join-Path $fixture 'https.bat') -Action Status -Mode 'a value with spaces' 2>&1
    if ($LASTEXITCODE -ne 37 -or $output -notcontains 'Launcher forwarded arguments correctly.') {
        throw "Launcher failed to preserve arguments or exit status: $LASTEXITCODE $output"
    }
    # A missing saved runtime must fail clearly; no real task belongs to fixture.
    Set-Content -LiteralPath (Join-Path $fixture 'data/https/powershell-path.txt') -Value 'C:\missing-powershell\pwsh.exe'
    $output = & (Join-Path $fixture 'https.bat') -Action Status 2>&1
    if ($LASTEXITCODE -eq 0 -or ($output -join "`n") -notmatch 'PowerShell 7 was not found') {
        throw 'Missing PowerShell did not produce an actionable nonzero failure.'
    }
} finally {
    $env:PATH = $previousPath
    $env:ProgramFiles = $previousProgramFiles
}
Write-Host 'HTTPS batch launcher passed without pwsh on PATH, including spaces, failure reporting, and exit codes.'
