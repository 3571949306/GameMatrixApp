# 全量游戏回归脚本（模块热更改造版，2026-08-29）
# 26 个游戏已模块化：统一经 DynamicGameActivity(--es gameId) 加载外置模块 APK。
# DynamicGameActivity 未导出，需 root adb（脚本内自动执行 adb root）。
# breakout 仍为宿主内置（GameRegistry 静态 Entry 直启 BreakoutActivity）。
#
# 用法: .\test_all_games.ps1 [-AdbSerial 127.0.0.1:16384]
param(
    [string]$AdbSerial = "127.0.0.1:16384",
    [string]$OutDir = "test_output\all_games_sweep"
)

$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }

$games = @(
    "gomoku","doudizhu","blackjack","checkers","dice","rock","game_2048",
    "sudoku","klotski","sokoban","pipeline","minesweeper","match","memory",
    "breakout","tiles","tetris","snake","flappy","brotato","plane",
    "reaction","guess","tic","whack","chinesechess","td"
)

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
& $adb -s $AdbSerial root | Out-Null
& $adb -s $AdbSerial shell rm /sdcard/gm_sweep.png 2>$null | Out-Null

$failed = @()
$passed = 0
foreach ($g in $games) {
    & $adb -s $AdbSerial shell am force-stop com.gamecenter.app
    Start-Sleep -Milliseconds 800
    & $adb -s $AdbSerial logcat -c
    & $adb -s $AdbSerial shell am start -n com.gamecenter.app/.SplashActivity | Out-Null
    Start-Sleep -Seconds 8
    & $adb -s $AdbSerial shell am start -n com.gamecenter.app/.DynamicGameActivity --es gameId $g 2>$null | Out-Null
    Start-Sleep -Seconds 5
    & $adb -s $AdbSerial shell screencap -p /sdcard/gm_sweep.png | Out-Null
    & $adb -s $AdbSerial pull /sdcard/gm_sweep.png "$OutDir\$g.png" 2>$null | Out-Null
    & $adb -s $AdbSerial shell rm /sdcard/gm_sweep.png | Out-Null
    $log = & $adb -s $AdbSerial logcat -d 2>$null
    $loaded  = ($log -match "外置模块加载成功" -or $log -match "BreakoutActivity").Count -gt 0
    $crashed = ($log -match "FATAL EXCEPTION").Count -gt 0
    if ($loaded -and -not $crashed) {
        Write-Host "PASS  $g" -ForegroundColor Green
        $passed++
    } else {
        Write-Host "FAIL  $g (loaded=$loaded crashed=$crashed)" -ForegroundColor Red
        $failed += $g
    }
}
Write-Host ""
Write-Host "结果: $passed/$($games.Count) 通过" -ForegroundColor Cyan
if ($failed.Count -gt 0) { Write-Host "失败清单: $($failed -join ', ')" -ForegroundColor Red; exit 1 }
