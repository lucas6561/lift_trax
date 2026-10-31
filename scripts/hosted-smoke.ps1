param(
    [Parameter(Mandatory = $true)]
    [string] $BaseUrl,

    [string] $SessionCookie = ""
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd("/")

$health = Invoke-WebRequest -Uri "$base/health" -TimeoutSec 90 -SkipHttpErrorCheck
if ($health.StatusCode -ne 200 -or $health.Content.Trim() -ne "ok") {
    throw "Expected /health to return HTTP 200 with 'ok'; got HTTP $($health.StatusCode)."
}

function Invoke-NoRedirect {
    param(
        [string] $Uri,
        [string] $Method = "Get",
        [hashtable] $Headers = @{},
        [object] $Body = $null
    )
    $request = @{
        Uri = $Uri
        Method = $Method
        MaximumRedirection = 0
        SkipHttpErrorCheck = $true
        # PowerShell emits a non-terminating error for a deliberately unfollowed redirect.
        ErrorAction = "SilentlyContinue"
        TimeoutSec = 90
    }
    if ($Headers.Count -gt 0) {
        $request.Headers = $Headers
    }
    if ($null -ne $Body) {
        $request.Body = $Body
    }
    try {
        return Invoke-WebRequest @request
    } catch {
        if ($_.Exception.Response) {
            return $_.Exception.Response
        }
        throw
    }
}

function Assert-Contains {
    param([string] $Text, [string] $Expected, [string] $Label)
    if (-not $Text.Contains($Expected)) {
        throw "$Label did not contain expected text: $Expected"
    }
}

$anonymous = Invoke-NoRedirect "$base/"
if ($anonymous.StatusCode -notin @(301, 302, 303, 307, 308)) {
    throw "Expected anonymous / to redirect to login; got HTTP $($anonymous.StatusCode)."
}
$location = [string]($anonymous.Headers.Location | Select-Object -First 1)
if ([string]::IsNullOrWhiteSpace($location) -or -not $location.Contains("/auth/login")) {
    throw "Expected anonymous redirect location to include /auth/login; got '$location'."
}

$callback = Invoke-WebRequest -Uri "$base/auth/callback?error=access_denied&error_description=secret" -SkipHttpErrorCheck
if ($callback.StatusCode -ne 400) {
    throw "Expected invalid callback to return HTTP 400; got HTTP $($callback.StatusCode)."
}
Assert-Contains $callback.Content "Authentication failed" "Callback failure page"
if ($callback.Content.Contains("secret")) {
    throw "Callback failure page exposed provider error details."
}

if ([string]::IsNullOrWhiteSpace($SessionCookie)) {
    Write-Host "Anonymous hosted smoke passed. Set LIFTTRAX_HOSTED_SMOKE_SESSION_COOKIE to run authenticated logging smoke."
    exit 0
}

$headers = @{ Cookie = "lt_session=$SessionCookie" }
$homePage = Invoke-WebRequest -Uri "$base/" -Headers $headers
Assert-Contains $homePage.Content "Signed in as" "Authenticated home"

# The form is rendered on Train, while Today only renders the dashboard.
$train = Invoke-WebRequest -Uri "$base/?tab=add-execution" -Headers $headers
Assert-Contains $train.Content "data-current-section='train'" "Authenticated Train shell"

$csrfMatch = [regex]::Match($train.Content, "name='csrfToken' value='([^']+)'")
if (-not $csrfMatch.Success) {
    throw "Authenticated home did not include a CSRF token."
}
$csrf = $csrfMatch.Groups[1].Value
$scopeMatch = [regex]::Match($train.Content, "name='accountScope' value='([^']+)'")
if (-not $scopeMatch.Success) {
    throw "Authenticated Train page did not include an account scope."
}
$accountScope = $scopeMatch.Groups[1].Value
# Replay the same CSRF cookie and account-bound form fields a browser submits.
$headers.Cookie = "lt_session=$SessionCookie; lt_csrf=$csrf"
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$lift = "Smoke Lift $stamp"

$addLift = Invoke-NoRedirect "$base/add-lift" -Method Post -Headers $headers -Body @{
    csrfToken = $csrf
    accountScope = $accountScope
    name = $lift
    region = "UPPER"
    main = "accessory"
    muscles = ""
    notes = "hosted smoke"
}
if ($addLift.StatusCode -ne 303) {
    throw "Expected add-lift to redirect after success; got HTTP $($addLift.StatusCode)."
}

$homeAfterLift = Invoke-WebRequest -Uri "$base/?tab=add-execution" -Headers $headers
$csrfMatch = [regex]::Match($homeAfterLift.Content, "name='csrfToken' value='([^']+)'")
if (-not $csrfMatch.Success) {
    throw "Home after lift creation did not include a CSRF token."
}
$csrf = $csrfMatch.Groups[1].Value

$addExecution = Invoke-NoRedirect "$base/add-execution" -Method Post -Headers $headers -Body @{
    csrfToken = $csrf
    accountScope = $accountScope
    lift = $lift
    date = (Get-Date -Format "yyyy-MM-dd")
    setCount = "1"
    metricType = "reps"
    metricValue = "1"
    weight = "1 lb"
    rpe = ""
    notes = "hosted smoke"
}
if ($addExecution.StatusCode -ne 303) {
    throw "Expected add-execution to redirect after success; got HTTP $($addExecution.StatusCode)."
}

$encodedLift = [uri]::EscapeDataString($lift)
$detail = Invoke-WebRequest -Uri "$base/lift?name=$encodedLift" -Headers $headers
Assert-Contains $detail.Content $lift "Created lift detail"
Assert-Contains $detail.Content "hosted smoke" "Persisted execution detail"
$history = Invoke-WebRequest -Uri "$base/?tab=executions&search=$encodedLift" -Headers $headers
Assert-Contains $history.Content "data-current-section='history'" "Authenticated History shell"
Assert-Contains $history.Content $lift "Persisted execution history"

Write-Host "Hosted smoke passed."
