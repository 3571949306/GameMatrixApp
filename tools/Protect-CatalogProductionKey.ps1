param(
    [string]$OutputPath = "",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutputPath) {
    $privateRoot = Join-Path $repoRoot "local_private"
    $deploymentDir = Get-ChildItem -LiteralPath $privateRoot -Directory -Recurse |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName "upload_config_hk.json") } |
        Select-Object -First 1
    if (-not $deploymentDir) { throw "VPS deployment private directory was not found" }
    $OutputPath = Join-Path $deploymentDir.FullName "catalog_ed25519_production.dpapi"
}
if ((Test-Path -LiteralPath $OutputPath) -and -not $Force) {
    throw "Protected production key already exists; use -Force only for an intentional rotation"
}

Add-Type -AssemblyName System.Security
$seed = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($seed)
    $protected = [System.Security.Cryptography.ProtectedData]::Protect(
        $seed,
        $null,
        [System.Security.Cryptography.DataProtectionScope]::CurrentUser
    )
    [System.IO.File]::WriteAllBytes($OutputPath, $protected)
} finally {
    $rng.Dispose()
    [Array]::Clear($seed, 0, $seed.Length)
}
Write-Output "Protected production Catalog key created outside the repository trust boundary."
