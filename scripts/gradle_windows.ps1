[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

# JDK 17+ on Windows creates a local AF_UNIX wakeup socket for NIO selectors.
# Some desktop agent terminals provide a TEMP/TMP path that is invalid or too
# long for that socket. Keep this override local to the Gradle child process.
$tempRoot = Join-Path $env:SystemDrive 'gm-gradle-tmp'
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

$oldTemp = $env:TEMP
$oldTmp = $env:TMP
$env:TEMP = $tempRoot
$env:TMP = $tempRoot
try {
    & (Join-Path $PSScriptRoot '..\gradlew.bat') @GradleArgs
    $exitCode = $LASTEXITCODE
} finally {
    $env:TEMP = $oldTemp
    $env:TMP = $oldTmp
}

exit $exitCode
