param(
    [Parameter(Mandatory = $true)][string]$Catalog,
    [Parameter(Mandatory = $true)][string]$SignatureOut,
    [Parameter(Mandatory = $true)][string]$NginxIncludeOut,
    [Parameter(Mandatory = $true)][string]$PublicKeyOut
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$keyFile = Get-ChildItem -LiteralPath (Join-Path $repoRoot "local_private") -Recurse -File -Filter "catalog_ed25519_production.dpapi" |
    Select-Object -First 1
if (-not $keyFile) { throw "Protected production Catalog key was not found" }

Add-Type -AssemblyName System.Security
$protected = [System.IO.File]::ReadAllBytes($keyFile.FullName)
$seed = [System.Security.Cryptography.ProtectedData]::Unprotect(
    $protected,
    $null,
    [System.Security.Cryptography.DataProtectionScope]::CurrentUser
)
try {
    $env:GAME_MATRIX_CATALOG_ED25519_PRIVATE_KEY = [Convert]::ToBase64String($seed)
    & python (Join-Path $repoRoot "scripts\catalog_signing.py") `
        $Catalog `
        --signature-out $SignatureOut `
        --nginx-include-out $NginxIncludeOut `
        --public-key-out $PublicKeyOut
    if ($LASTEXITCODE -ne 0) { throw "Catalog signing failed" }
} finally {
    Remove-Item Env:GAME_MATRIX_CATALOG_ED25519_PRIVATE_KEY -ErrorAction SilentlyContinue
    [Array]::Clear($seed, 0, $seed.Length)
    [Array]::Clear($protected, 0, $protected.Length)
}
