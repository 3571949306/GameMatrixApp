param(
    [int[]] $CandidatePorts = @(10808, 10809, 10810, 10812),
    [switch] $Apply,
    [switch] $Clear
)

$ErrorActionPreference = "Stop"

function Test-GitHubProxy {
    param([int] $Port)

    $proxy = "http://127.0.0.1:$Port"
    try {
        $status = & curl.exe -sS -o NUL -w "%{http_code}" -x $proxy https://github.com --connect-timeout 8 2>$null
        return ($LASTEXITCODE -eq 0 -and $status -eq "200")
    } catch {
        return $false
    }
}

if ($Clear) {
    git config --global --unset http.https://github.com.proxy 2>$null
    Write-Host "Cleared GitHub-only Git proxy."
    exit 0
}

$listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object {
        $_.LocalAddress -in @("127.0.0.1", "::1", "0.0.0.0") -and
        $_.LocalPort -in $CandidatePorts
    } |
    Select-Object LocalAddress, LocalPort, OwningProcess

Write-Host "Candidate local proxy listeners:"
if ($listeners) {
    $listeners | Sort-Object LocalPort | Format-Table -AutoSize
} else {
    Write-Host "  none"
}

$workingPort = $null
foreach ($port in $CandidatePorts) {
    if (Test-GitHubProxy -Port $port) {
        $workingPort = $port
        break
    }
}

if (-not $workingPort) {
    Write-Error "No working HTTP proxy port found for GitHub. Start v2rayN/xray local proxy and retry."
}

$proxyUrl = "http://127.0.0.1:$workingPort"
Write-Host "Working GitHub proxy: $proxyUrl"

if ($Apply) {
    git config --global http.https://github.com.proxy $proxyUrl
    Write-Host "Configured Git to use $proxyUrl only for https://github.com."
}

Write-Host "Current GitHub-only Git proxy:"
git config --global --get http.https://github.com.proxy
