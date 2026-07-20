package com.gamecenter.app.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启动 Baseline Profile 生成器。
 *
 * 运行方式：
 *   ./gradlew :app:generateBaselineProfile
 *
 * 环境要求（任选其一）：
 *   1. 连接支持 Macrobenchmark 的官方 Android 真机或 AVD
 *   2. 执行 ./gradlew :app:generateBaselineProfile
 *
 * 生成后 baseline-prof.txt 自动写入 app/src/main/ 并打包进 APK/assets，
 * 安装即 AOT 预热，冷启动目标 < 2s（中端 arm64 设备）。
 *
 * 因不经 Google Play，不能用云 Profile，必须打包内置。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    /**
     * 生成冷启动 Baseline Profile。
     *
     * 启动 LAUNCHER 入口（SplashActivity → MainActivity），覆盖完整冷启动路径：
     *   - Application 初始化（Hilt/Room/网络栈）
     *   - SplashActivity 启动屏动画
     *   - MainActivity 首帧渲染
     *
     * 采集的热路径（class/method）会写入 baseline-prof.txt，
     * 安装时由 ProfileInstaller 解析并触发 AOT 编译。
     */
    @Test
    fun generateStartupBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.gamecenter.app"
        ) {
            // 回到桌面，确保是冷启动而非热启动
            pressHome()
            // 启动 LAUNCHER 入口（SplashActivity，MAIN/LAUNCHER intent-filter）
            startActivityAndWait()
        }
    }
}
