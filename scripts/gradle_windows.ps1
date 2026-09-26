[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

# JDK 17+ 在 Windows 上会为 NIO 选择器创建本地 AF_UNIX 唤醒套接字。
# 某些桌面代理终端提供的 TEMP/TMP 路径对该套接字无效或过长。
# 此覆盖仅限于 Gradle 子进程。
$tempRoot = Join-Path $env:SystemDrive 'gm-gradle-tmp'
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

$oldTemp = $env:TEMP
$oldTmp = $env:TMP
$env:TEMP = $tempRoot
$env:TMP = $tempRoot

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  GameMatrixApp Gradle 构建监控" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "【系统信息】" -ForegroundColor Yellow
$os = Get-CimInstance Win32_OperatingSystem
$cpu = Get-CimInstance Win32_Processor
Write-Host "  操作系统: $($os.Caption) $($os.Version)" -ForegroundColor White
Write-Host "  CPU: $($cpu.Name)" -ForegroundColor White
Write-Host "  CPU 核心: $($cpu.NumberOfCores) 核 / $($cpu.NumberOfLogicalProcessors) 线程" -ForegroundColor White
Write-Host "  内存: $([math]::Round($os.TotalVisibleMemorySize/1MB, 2)) GB 总计, $([math]::Round($os.FreePhysicalMemory/1MB, 2)) GB 可用" -ForegroundColor White
Write-Host ""

Write-Host "【大小核信息】" -ForegroundColor Yellow
$cpuCores = $cpu.NumberOfCores
$logicalProcessors = $cpu.NumberOfLogicalProcessors
Write-Host "  物理核心数: $cpuCores" -ForegroundColor White
Write-Host "  逻辑处理器数: $logicalProcessors" -ForegroundColor White

$hasHybrid = $false
$perfCores = 0
$effCores = 0

$cpuProps = $cpu | Get-Member -MemberType NoteProperty | Select-Object -ExpandProperty Name
if ($cpuProps -contains "NumberOfPerformanceCores") {
    $perfCores = $cpu.NumberOfPerformanceCores
    $effCores = $cpu.NumberOfEfficientCores
    $hasHybrid = $true
}

if ($hasHybrid) {
    Write-Host "  性能核心 (P-Core): $perfCores 核" -ForegroundColor Green
    Write-Host "  能效核心 (E-Core): $effCores 核" -ForegroundColor Green
    Write-Host "  架构类型: 大小核混合架构" -ForegroundColor Green
} else {
    Write-Host "  架构类型: 传统对称多处理架构" -ForegroundColor White
}
Write-Host ""

Write-Host "【Gradle JVM 配置】" -ForegroundColor Yellow
$gradlePropsPath = Join-Path $PSScriptRoot '..\gradle.properties'
$jvmArgs = Get-Content $gradlePropsPath | Where-Object { $_ -match 'org\.gradle\.jvmargs' }
if ($jvmArgs) {
    Write-Host "  JVM 参数: $jvmArgs" -ForegroundColor White
}
Write-Host ""

Write-Host "【开始构建】" -ForegroundColor Yellow
Write-Host "  命令: .\gradlew.bat $($GradleArgs -join ' ')" -ForegroundColor White
Write-Host "  时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor White
Write-Host ""

$startTime = Get-Date

try {
    $logFile = Join-Path $tempRoot "gradle_build_$(Get-Date -Format 'yyyyMMdd_HHmmss').log"
    Write-Host "  日志文件: $logFile" -ForegroundColor Gray
    Write-Host ""

    $gradlewPath = Join-Path $PSScriptRoot '..\gradlew.bat'
    & $gradlewPath @GradleArgs 2>&1 | Tee-Object -FilePath $logFile
    $exitCode = $LASTEXITCODE
} finally {
    $env:TEMP = $oldTemp
    $env:TMP = $oldTmp
}

$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  构建完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  退出码: $exitCode" -ForegroundColor $(if ($exitCode -eq 0) { "Green" } else { "Red" })
Write-Host "  耗时: $($duration.ToString('mm\:ss\.fff'))" -ForegroundColor White
Write-Host "  结束时间: $($endTime.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor White
Write-Host ""

if ($exitCode -eq 0) {
    Write-Host "  构建成功!" -ForegroundColor Green
} else {
    Write-Host "  构建失败!" -ForegroundColor Red
    Write-Host "  查看日志: $logFile" -ForegroundColor Yellow
}

exit $exitCode