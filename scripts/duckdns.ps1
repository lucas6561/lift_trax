# Shared, side-effect-free definitions for the local HTTPS launcher.
function Get-DuckDnsToken([string] $Repo) {
    $path = Join-Path $Repo 'config/lifttrax-duckdns.override.json'
    if (-not (Test-Path -LiteralPath $path)) {
        throw 'Create config/lifttrax-duckdns.override.json from the example and save the DuckDNS token locally.'
    }
    try { $credentials = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json }
    catch { throw 'The private DuckDNS settings file is not valid JSON.' }
    if ($null -eq $credentials -or -not ($credentials.PSObject.Properties.Name -contains 'Token') -or
        [string]::IsNullOrWhiteSpace($credentials.Token)) {
        throw 'Save your DuckDNS token in config/lifttrax-duckdns.override.json before provisioning HTTPS.'
    }
    return $credentials.Token.Trim()
}

function Assert-DuckDnsLanSettings($Settings) {
    if ($Settings.Hostname -notmatch '^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.duckdns\.org$') {
        throw 'Network HTTPS requires your registered single-label DuckDNS hostname.'
    }
    if (-not ($Settings.PSObject.Properties.Name -contains 'LanAddress')) {
        throw 'Set LanAddress to the PC private IPv4 address in the ignored HTTPS settings.'
    }
    $address = $null
    if (-not [System.Net.IPAddress]::TryParse($Settings.LanAddress, [ref]$address) -or
        $address.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
        throw 'LanAddress must be a private IPv4 address.'
    }
    $bytes = $address.GetAddressBytes()
    if (-not ($bytes[0] -eq 10 -or ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
        ($bytes[0] -eq 192 -and $bytes[1] -eq 168))) {
        throw 'Refusing to publish a non-private LAN address. This setup must stay inside the home network.'
    }
    $localAddresses = @([System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces() |
        Where-Object { $_.OperationalStatus -eq [System.Net.NetworkInformation.OperationalStatus]::Up } |
        ForEach-Object { $_.GetIPProperties().UnicastAddresses } | ForEach-Object { $_.Address.ToString() })
    if ($Settings.LanAddress -notin $localAddresses) {
        throw 'LanAddress is not assigned to this PC. Update it and use a router DHCP reservation.'
    }
}

function Set-DuckDnsLanAddress([string] $Hostname, [string] $Token, [string] $Address) {
    # Clearing first removes any stale public AAAA record as well as the old A record.
    # Never omit ip on the second request: DuckDNS would auto-detect the public WAN IP.
    $domain = [Uri]::EscapeDataString($Hostname.Substring(0, $Hostname.Length - '.duckdns.org'.Length))
    $credential = [Uri]::EscapeDataString($Token)
    foreach ($parameters in @('clear=true', "ip=$([Uri]::EscapeDataString($Address))")) {
        try {
            $response = Invoke-WebRequest -Uri "https://www.duckdns.org/update?domains=$domain&token=$credential&$parameters" `
                -TimeoutSec 30 -MaximumRedirection 0 -SkipHttpErrorCheck -Verbose:$false -Debug:$false
        } catch { throw 'DuckDNS address update failed. Verify the token and internet connection; credentials were not printed.' }
        $responseBody = if ($response.Content -is [byte[]]) {
            [System.Text.Encoding]::UTF8.GetString($response.Content)
        } else { [string] $response.Content }
        if ($response.StatusCode -ne 200 -or $responseBody.Trim() -ne 'OK') {
            throw 'DuckDNS rejected the address update. Verify that this account owns the configured hostname.'
        }
    }
    Write-Host 'DuckDNS now points to the PC private LAN address; stale IPv6 records were cleared.'
}
