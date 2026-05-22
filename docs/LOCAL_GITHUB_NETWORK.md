# Local GitHub Network Notes

Last checked: 2026-05-20.

## Current Fix

This machine can reach GitHub reliably through the local v2rayN/xray HTTP proxy on `127.0.0.1:10808`. Git has been configured to use that proxy only for GitHub:

```powershell
git config --global http.https://github.com.proxy http://127.0.0.1:10808
```

This avoids depending on xray TUN or a virtual network adapter for `git push`, while keeping the proxy scope limited to `https://github.com`.

## Verify

```powershell
git config --global --get http.https://github.com.proxy
git ls-remote https://github.com/3571949306/GameMatrixApp.git HEAD
```

Expected result: `git ls-remote` prints the remote `HEAD` commit.

## Re-detect And Apply

Run the helper from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File tools\network\Configure-GitHubProxy.ps1 -Apply
```

The script checks common local proxy ports, verifies GitHub access through the first working HTTP proxy, and writes the GitHub-only Git proxy setting.

## Clear

```powershell
powershell -ExecutionPolicy Bypass -File tools\network\Configure-GitHubProxy.ps1 -Clear
```

## If Gradle Fails With Socket Exhaustion

If local Gradle fails before the build with an error like:

```text
java.net.SocketException: No buffer space available (maximum connections reached?): bind
```

check whether a process owns too many UDP endpoints:

```powershell
Get-NetUDPEndpoint |
  Group-Object OwningProcess |
  Sort-Object Count -Descending |
  Select-Object -First 12 Count,Name
```

Then inspect the process:

```powershell
Get-Process -Id <PID>
```

If the process is `xray.exe` or v2rayN TUN mode, close TUN mode from v2rayN first. If Windows refuses to stop it from a normal shell, restart v2rayN or run PowerShell as administrator and stop that process. After the UDP endpoint count drops back to normal, rerun:

```powershell
.\gradlew.bat :app:test -PautoBumpVersion=false
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false
```

## If Gradle Needs The Same Proxy

Git proxy settings do not affect Gradle dependency downloads. For one-off Gradle commands behind the same local proxy, set `GRADLE_OPTS` in the current PowerShell session:

```powershell
$env:GRADLE_OPTS='-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=10808 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=10808'
```

This was needed when `lintDebug` had to download artifacts from `dl.google.com`.

## Java Runtime Used For Verification

The project currently needs Java 21 for local verification because the MediaPipe GenAI dependency contains Java 21 class files. On this machine the verified runtime is:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```
