# Network Tools

`Configure-GitHubProxy.ps1` detects a working local v2rayN/xray HTTP proxy and configures Git to use it only for `https://github.com`.

Typical use:

```powershell
powershell -ExecutionPolicy Bypass -File tools\network\Configure-GitHubProxy.ps1 -Apply
```

See `docs/LOCAL_GITHUB_NETWORK.md` for the current machine notes and recovery steps.

