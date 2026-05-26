$filePath = 'd:\kaifa\GameCenterApp\PROJECT_CONTEXT.md'
$bytes = [System.IO.File]::ReadAllBytes($filePath)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$content = $utf8NoBom.GetString($bytes)

Write-Host "File loaded. Length: $($content.Length)"

# Change 1: Add new row at top of "0. 最近更新" table
$old1 = "| **当前工作区** | **模块市场架构调整：默认游戏分类、移除"全部"分类、已下载模块列表、刷新按钮、商店游戏回流大厅、快捷卸载** |"
$new1 = "| **当前工作区** | **底部导航切换闪退修复：KeepStateNavigator（add/show/hide策略替代replace）、ModuleDownloader全局异常捕获+降低超时+增加日志、内存泄漏全面修复（移除WeakReference callback、Fragment回调安全检查）、40轮快速Tab切换压力测试通过** |`n| **当前工作区** | **模块市场架构调整：默认游戏分类、移除"全部"分类、已下载模块列表、刷新按钮、商店游戏回流大厅、快捷卸载** |"

if ($content.Contains($old1)) {
    $content = $content.Replace($old1, $new1)
    Write-Host "Change 1: SUCCESS - Added new row in update table"
} else {
    Write-Host "Change 1: FAILED - Could not find target string"
    $idx = $content.IndexOf('模块市场架构调整')
    Write-Host "  '模块市场架构调整' found at index: $idx"
}

# Change 2: Add KeepStateNavigator after ModuleStoreActivity description
$old2 = "- `ModuleStoreActivity`: 模块市场，右上角提供刷新和已下载模块入口，卡片支持下载/打开/卸载。游戏大厅左上角版本号下方有入口按钮。"
$new2 = "- `ModuleStoreActivity`: 模块市场，右上角提供刷新和已下载模块入口，卡片支持下载/打开/卸载。游戏大厅左上角版本号下方有入口按钮。`n- `KeepStateNavigator`: 自定义 FragmentNavigator，使用 add/show/hide 策略管理底部导航Fragment，切换Tab时不销毁重建，从根本上解决快速切换闪退和内存泄漏问题"

if ($content.Contains($old2)) {
    $content = $content.Replace($old2, $new2)
    Write-Host "Change 2: SUCCESS - Added KeepStateNavigator description"
} else {
    Write-Host "Change 2: FAILED - Could not find target string"
}

# Change 3: Update version info
$old3 = "当前版本：``versionCode=294``, ``versionName=1.4.0``"
$new3 = "当前版本：``versionCode=330``, ``versionName=1.4.0``"

if ($content.Contains($old3)) {
    $content = $content.Replace($old3, $new3)
    Write-Host "Change 3: SUCCESS - Updated version info"
} else {
    Write-Host "Change 3: FAILED - Could not find target string"
    $idx = $content.IndexOf('versionCode=294')
    Write-Host "  'versionCode=294' found at index: $idx"
}

# Change 4: Update "最后更新" line
$old4 = "最后更新：2026-05-19（战略优化：UpdateViewModel 协程化 + 网络层测试 + CI 质量门 + 安全加固 + 构建优化）"
$new4 = "最后更新：2026-05-24（底部导航切换闪退修复 + 模块下载修复 + 内存泄漏全面修复）"

if ($content.Contains($old4)) {
    $content = $content.Replace($old4, $new4)
    Write-Host "Change 4: SUCCESS - Updated 最后更新 line"
} else {
    Write-Host "Change 4: FAILED - Could not find target string"
    $idx = $content.IndexOf('最后更新：2026-05-19')
    Write-Host "  '最后更新：2026-05-19' found at index: $idx"
}

# Change 5: Add new sync record at end of file
$newSection = @"

## 2026-05-24 文档同步：底部导航切换闪退修复
- 创建 KeepStateNavigator 自定义导航器（继承 Navigator<FragmentNavigator.Destination>），使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- mobile_navigation.xml 将 fragment 标签改为 keep_state_fragment
- activity_main.xml 移除 app:navGraph，改为代码中先注册导航器再设置导航图
- MainActivity 自定义底部导航点击处理，替代 NavigationUI.setupWithNavController
- ModuleDownloader 全面重写：全局异常捕获、降低超时（连接15s/读取30s）、移除 cancelled 死代码、增加日志
- ModuleManager.loadModuleList 移除 WeakReference，直接使用 callback
- 各 Fragment 添加 isDestroyed 标记和 isAdded 安全检查
- 压力测试通过：40轮快速Tab切换无崩溃
"@

$content = $content.TrimEnd() + "`n" + $newSection + "`n"
Write-Host "Change 5: SUCCESS - Added new sync record"

# Write back as UTF-8 without BOM
$outputBytes = $utf8NoBom.GetBytes($content)
[System.IO.File]::WriteAllBytes($filePath, $outputBytes)
Write-Host "`nAll changes written to file."
