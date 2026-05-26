# 上传 GameCenterApp 到香港 VPS
param(
    [string]$Channel = "beta",
    [string]$ApkPath = "app\build\outputs\apk\release\app-release.apk",
    [string]$VersionJsonPath = "app\build\outputs\apk\release\version.json"
)

$ErrorActionPreference = "Stop"

# VPS 配置
$VpsHost = "149.104.29.181"
$VpsUser = "root"
$VpsPassword = $env:GAMECENTER_VPS_PASSWORD
$VpsPort = 22
$RemoteDir = "/var/www/update/app"

if ([string]::IsNullOrWhiteSpace($VpsPassword)) {
    throw "Set GAMECENTER_VPS_PASSWORD before running this legacy helper."
}

$RemoteApk = "app-$Channel.apk"
$RemoteVersion = "version-$Channel.json"

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  上传到香港 VPS" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "VPS 信息:" -ForegroundColor Yellow
Write-Host "  主机：$VpsHost"
Write-Host "  用户：$VpsUser"
Write-Host "  远程目录：$RemoteDir"
Write-Host ""

# 检查文件是否存在
if (-not (Test-Path $ApkPath)) {
    Write-Host "错误：APK 文件不存在 - $ApkPath" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $VersionJsonPath)) {
    Write-Host "错误：version.json 不存在 - $VersionJsonPath" -ForegroundColor Red
    exit 1
}

# 读取版本信息
$VersionData = Get-Content $VersionJsonPath -Raw | ConvertFrom-Json
Write-Host "版本信息:" -ForegroundColor Yellow
Write-Host "  versionCode: $($VersionData.versionCode)"
Write-Host "  versionName: $($VersionData.versionName)"
Write-Host "  channel: $($VersionData.channel)"
Write-Host ""

# 使用 WinSCP 上传（如果已安装）
$WinScpExe = "C:\Program Files (x86)\WinSCP\WinSCP.com"
if (Test-Path $WinScpExe) {
    Write-Host "使用 WinSCP 上传..." -ForegroundColor Cyan
    
    $SessionUrl = "scp://$($VpsUser):$([System.Web.HttpUtility]::UrlEncode($VpsPassword))@$($VpsHost)/"
    
    $Script = @"
option batch abort
option confirm off
open $SessionUrl
put "$ApkPath" "$RemoteDir/$RemoteApk"
put "$VersionJsonPath" "$RemoteDir/$RemoteVersion"
exit
"@
    
    $TempScript = [System.IO.Path]::GetTempFileName()
    $Script | Out-File -FilePath $TempScript -Encoding UTF8
    
    & $WinScpExe /script=$TempScript
    
    Remove-Item $TempScript -Force
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ 上传成功！" -ForegroundColor Green
    } else {
        Write-Host "✗ 上传失败" -ForegroundColor Red
        exit 1
    }
} else {
    # 备选方案：使用 plink (PuTTY)
    $PlinkExe = "C:\Program Files\PuTTY\plink.exe"
    $PscpExe = "C:\Program Files\PuTTY\pscp.exe"
    
    if (Test-Path $PscpExe) {
        Write-Host "使用 PSCP 上传..." -ForegroundColor Cyan
        
        $TempApk = [System.IO.Path]::GetTempFileName()
        $TempVer = [System.IO.Path]::GetTempFileName()
        
        Copy-Item $ApkPath $TempApk
        Copy-Item $VersionJsonPath $TempVer
        
        # 上传 APK
        $ApkCmd = "& `"$PscpExe`" -P $VpsPort -pw `"$VpsPassword`" -o StrictHostKeyChecking=no `"$TempApk`" ${VpsUser}@${VpsHost}:${RemoteDir}/${RemoteApk}"
        Write-Host "上传 APK..." -ForegroundColor Cyan
        Invoke-Expression $ApkCmd
        
        # 上传 version.json
        $VerCmd = "& `"$PscpExe`" -P $VpsPort -pw `"$VpsPassword`" -o StrictHostKeyChecking=no `"$TempVer`" ${VpsUser}@${VpsHost}:${RemoteDir}/${RemoteVersion}"
        Write-Host "上传 version.json..." -ForegroundColor Cyan
        Invoke-Expression $VerCmd
        
        Remove-Item $TempApk, $TempVer -Force
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✓ 上传成功！" -ForegroundColor Green
        } else {
            Write-Host "✗ 上传失败" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "错误：未找到 WinSCP 或 PSCP，请安装其中之一" -ForegroundColor Red
        Write-Host "  WinSCP: https://winscp.net"
        Write-Host "  PuTTY: https://putty.org" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host ""
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  上传完成" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "远程文件:" -ForegroundColor Yellow
Write-Host "  $RemoteDir/$RemoteApk"
Write-Host "  $RemoteDir/$RemoteVersion"
Write-Host ""
Write-Host "访问 URL:" -ForegroundColor Yellow
Write-Host "  https://hk-update.tcp0053.shop/$RemoteApk"
Write-Host "  https://hk-update.tcp0053.shop/$RemoteVersion"
Write-Host ""
