# Network Tools

`Configure-GitHubProxy.ps1` detects a working local v2rayN/xray HTTP proxy and configures Git to use it only for `https://github.com`.

Typical use:

```powershell
powershell -ExecutionPolicy Bypass -File 工具\\network\Configure-GitHubProxy.ps1 -Apply
```

See `文档/LOCAL_GITHUB_NETWORK.md` for the current machine notes and recovery steps.


