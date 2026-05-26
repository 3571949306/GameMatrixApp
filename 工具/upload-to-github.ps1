# 上传到 GitHub Releases
param(
    [string]$GithubToken,
    [string]$ApkPath = "app\build\outputs\apk\release\app-release.apk",
    [string]$VersionName = "1.3.16"
)

if (-not $GithubToken) {
    Write-Host "错误：请提供 GitHub Token" -ForegroundColor Red
    Write-Host "使用方法：.\upload-to-github.ps1 -GithubToken YOUR_TOKEN" -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $ApkPath)) {
    Write-Host "错误：APK 文件不存在 - $ApkPath" -ForegroundColor Red
    exit 1
}

$tagName = "v$VersionName-beta"
$releaseName = "GameCenterApp v$VersionName (Beta)"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  上传到 GitHub Releases" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "版本：$VersionName"
Write-Host "Tag: $tagName"
Write-Host ""

$headers = @{
    "Authorization" = "token $GithubToken"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "GameCenterApp-Upload-Script"
}

# 1. 检查 Release 是否已存在
$apiUrl = "https://api.github.com/repos/3571949306/GameCenterApp/releases/tags/$tagName"

try {
    $response = Invoke-RestMethod -Uri $apiUrl -Headers $headers -Method Get
    $releaseId = $response.id
    $uploadUrl = ($response.upload_url -split "{\?")[0]
    Write-Host "使用现有 Release (ID: $releaseId)" -ForegroundColor Cyan
}
catch {
    # 创建新 Release
    $releasePayload = @{
        tag_name = $tagName
        name = $releaseName
        body = "GameCenterApp $releaseName`n`n更新内容详见 CHANGELOG.md"
        draft = $false
        prerelease = $true
    } | ConvertTo-Json
    
    $createUrl = "https://api.github.com/repos/3571949306/GameCenterApp/releases"
    $response = Invoke-RestMethod -Uri $createUrl -Headers $headers -Method Post -Body $releasePayload
    
    $releaseId = $response.id
    $uploadUrl = ($response.upload_url -split "{\?")[0]
    Write-Host "创建 Release 成功 (ID: $releaseId)" -ForegroundColor Green
}

# 2. 上传 APK
$apkFilename = "GameCenterApp-v$VersionName.apk"
$uploadHeaders = $headers.Clone()
$uploadHeaders["Content-Type"] = "application/vnd.android.package-archive"

$apkBytes = [System.IO.File]::ReadAllBytes($ApkPath)

try {
    $response = Invoke-WebRequest -Uri "$uploadUrl?name=$apkFilename" -Headers $uploadHeaders -Method Post -Body $apkBytes
    Write-Host "APK 上传成功" -ForegroundColor Green
    Write-Host ""
    Write-Host "Release URL: https://github.com/3571949306/GameCenterApp/releases/tag/$tagName" -ForegroundColor Cyan
    Write-Host ""
}
catch {
    Write-Host "APK 上传失败：$_" -ForegroundColor Red
    exit 1
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  上传完成" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
