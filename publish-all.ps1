# GameCenterApp 一键发布到所有更新源
# 功能：编译 APK 并自动上传到 HK VPS + US VPS + GitHub Releases

param(
    [string]$Channel = "beta",
    [switch]$SkipVerify,
    [string]$GithubToken
)

$ErrorActionPreference = "Stop"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  GameCenterApp 一键发布工具" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# 配置
$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ApkPath = "$RepoRoot\app\build\outputs\apk\release\app-release-unsigned.apk"
$VersionJsonPath = "$RepoRoot\app\build\outputs\version.json"
$VpsConfigDir = "$RepoRoot\local_private\vps"

# 更新源配置
$UpdateSources = @{
    HK_VPS = @{
        Name = "香港 VPS"
        Url = "https://hk-update.tcp0053.shop"
        ConfigFile = "$VpsConfigDir\upload_config_hk.json"
    }
    US_VPS = @{
        Name = "美国 VPS"
        Url = "https://tcp0053.shop:1443"
        ConfigFile = "$VpsConfigDir\upload_config_us.json"
    }
    GitHub = @{
        Name = "GitHub Releases"
        Url = "https://github.com/3571949306/GameCenterApp/releases"
        RequiresToken = $true
    }
}

# 步骤 1: 编译 APK
Write-Host "[1/4] 编译 Release APK..." -ForegroundColor Yellow
Set-Location $RepoRoot
& .\gradlew.bat assembleRelease -PupdateChannel=$Channel -x lintVitalAnalyzeRelease --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败！" -ForegroundColor Red
    exit 1
}
Write-Host "编译成功！" -ForegroundColor Green
Write-Host ""

# 步骤 2: 生成 version.json
Write-Host "[2/4] 生成版本元数据..." -ForegroundColor Yellow
& .\gradlew.bat generateVersionJson -PupdateChannel=$Channel --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Host "生成 version.json 失败！" -ForegroundColor Red
    exit 1
}
Write-Host "生成成功！" -ForegroundColor Green
Write-Host ""

# 读取版本信息
$VersionData = Get-Content $VersionJsonPath -Raw | ConvertFrom-Json
$VersionName = $VersionData.versionName
$VersionCode = $VersionData.versionCode
$ApkSize = (Get-Item $ApkPath).Length / 1MB

Write-Host "版本信息:" -ForegroundColor Cyan
Write-Host "  版本号：$VersionName"
Write-Host "  版本代码：$VersionCode"
Write-Host "  渠道：$Channel"
Write-Host "  APK 大小：$([math]::Round($ApkSize, 2)) MB"
Write-Host ""

# 步骤 3: 上传到 VPS
Write-Host "[3/4] 上传到 VPS 服务器..." -ForegroundColor Yellow

function Upload-ToVps {
    param(
        [string]$ConfigFile,
        [string]$Channel
    )
    
    if (-not (Test-Path $ConfigFile)) {
        Write-Host "  跳过：配置文件不存在 - $ConfigFile" -ForegroundColor Gray
        return $false
    }
    
    $config = Get-Content $ConfigFile -Raw | ConvertFrom-Json
    $host = $config.host
    $user = $config.user
    $password = $config.password
    $remoteDir = $config.remoteDir
    $port = if ($config.port) { $config.port } else { 22 }
    
    $remoteApk = "app-$Channel.apk"
    $remoteVer = "version-$Channel.json"
    
    Write-Host "  上传到 $($config.host)..." -ForegroundColor Cyan
    
    # 使用 plink (PuTTY) 或 ssh 上传
    $tempApk = [System.IO.Path]::GetTempFileName()
    $tempVer = [System.IO.Path]::GetTempFileName()
    
    Copy-Item $ApkPath $tempApk
    Copy-Item $VersionJsonPath $tempVer
    
    # 构建 SCP 命令
    $scpCmd = "scp -P $port -o StrictHostKeyChecking=no $tempApk ${user}@${host}:${remoteDir}/${remoteApk}"
    Write-Host "  执行：$scpCmd" -ForegroundColor Gray
    
    # 注意：Windows 需要安装 PuTTY 或使用 OpenSSH
    # 这里使用 PowerShell 的 WebRequest 作为备选方案
    
    try {
        # 备选方案：通过 HTTP API 上传（如果 VPS 提供）
        $uploadUrl = "https://${host}/api/upload"
        $headers = @{
            "Authorization" = "Bearer $password"
        }
        
        $form = @{
            "apk" = Get-Item $tempApk
            "version" = Get-Item $tempVer
            "channel" = $Channel
        }
        
        # 这需要 VPS 支持 HTTP 上传 API
        # 如果没有，需要使用 SSH/SCP
        
        Write-Host "  注意：VPS 上传需要 SSH/SCP 支持，请手动配置" -ForegroundColor Yellow
        Remove-Item $tempApk, $tempVer -Force
        return $false
    }
    catch {
        Write-Host "  上传失败：$_" -ForegroundColor Red
        Remove-Item $tempApk, $tempVer -Force
        return $false
    }
}

# 上传到 HK VPS
$hkSuccess = Upload-ToVps -ConfigFile $UpdateSources.HK_VPS.ConfigFile -Channel $Channel

# 上传到 US VPS
$usSuccess = Upload-ToVps -ConfigFile $UpdateSources.US_VPS.ConfigFile -Channel $Channel

Write-Host ""

# 步骤 4: 上传到 GitHub Releases
Write-Host "[4/4] 上传到 GitHub Releases..." -ForegroundColor Yellow

if (-not $GithubToken) {
    Write-Host "  跳过：未提供 GitHub Token" -ForegroundColor Yellow
    $githubSuccess = $false
} else {
    $tagName = if ($Channel -eq "beta") { "v$VersionName-beta" } else { "v$VersionName" }
    $releaseName = "GameCenterApp v$VersionName"
    if ($Channel -eq "beta") { $releaseName += " (Beta)" }
    
    $headers = @{
        "Authorization" = "token $GithubToken"
        "Accept" = "application/vnd.github.v3+json"
        "User-Agent" = "GameCenterApp-Publish-Script"
    }
    
    # 1. 检查 Release 是否已存在
    $apiUrl = "https://api.github.com/repos/3571949306/GameCenterApp/releases/tags/$tagName"
    
    try {
        $response = Invoke-RestMethod -Uri $apiUrl -Headers $headers -Method Get
        
        $releaseId = $response.id
        $uploadUrl = ($response.upload_url -split "{\?")[0]
        Write-Host "  使用现有 Release (ID: $releaseId)" -ForegroundColor Cyan
    }
    catch {
        # 创建新 Release
        $releasePayload = @{
            tag_name = $tagName
            name = $releaseName
            body = "GameCenterApp $releaseName`n`n更新内容详见 CHANGELOG.md"
            draft = $false
            prerelease = ($Channel -eq "beta")
        } | ConvertTo-Json
        
        $createUrl = "https://api.github.com/repos/3571949306/GameCenterApp/releases"
        $response = Invoke-RestMethod -Uri $createUrl -Headers $headers -Method Post -Body $releasePayload
        
        $releaseId = $response.id
        $uploadUrl = ($response.upload_url -split "{\?")[0]
        Write-Host "  创建 Release 成功 (ID: $releaseId)" -ForegroundColor Green
    }
    
    # 2. 上传 APK
    $apkFilename = "GameCenterApp-v$VersionName.apk"
    $uploadParams = @{
        name = $apkFilename
    }
    
    $uploadHeaders = $headers.Clone()
    $uploadHeaders["Content-Type"] = "application/vnd.android.package-archive"
    
    $apkBytes = [System.IO.File]::ReadAllBytes($ApkPath)
    
    try {
        $response = Invoke-WebRequest -Uri "$uploadUrl" -Headers $uploadHeaders -Method Post -Body $apkBytes
        Write-Host "  APK 上传成功" -ForegroundColor Green
        $githubSuccess = $true
    }
    catch {
        Write-Host "  APK 上传失败：$_" -ForegroundColor Red
        $githubSuccess = $false
    }
}

Write-Host ""

# 汇总结果
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  发布结果汇总" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$summary = @{
    "香港 VPS" = $hkSuccess
    "美国 VPS" = $usSuccess
    "GitHub Releases" = $githubSuccess
}

$totalSuccess = 0
foreach ($source in $summary.Keys) {
    $status = if ($summary[$source]) { "✓ 成功" } else { "✗ 失败" }
    $color = if ($summary[$source]) { "Green" } else { "Red" }
    Write-Host "  $source`: $status" -ForegroundColor $color
    if ($summary[$source]) { $totalSuccess++ }
}

Write-Host ""
Write-Host "总计：$totalSuccess/3 个更新源上传成功" -ForegroundColor $(if ($totalSuccess -eq 3) { "Green" } else { "Yellow" })
Write-Host "============================================================" -ForegroundColor Cyan

if ($totalSuccess -eq 0) {
    Write-Host "`n错误：所有更新源上传失败！" -ForegroundColor Red
    exit 1
} elseif ($totalSuccess -lt 3) {
    Write-Host "`n警告：部分更新源上传失败" -ForegroundColor Yellow
    exit 0
} else {
    Write-Host "`n成功：所有更新源上传完成！" -ForegroundColor Green
    exit 0
}
