package com.gamecenter.app.ai.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * 设备硬件探针与端侧 AI 模型智能推荐。
 *
 * 根据设备总内存、当前可用内存、CPU 核心数，评估设备档位并推荐最适宜的端侧模型。
 */
object DeviceProfiler {

    enum class DeviceTier(val displayName: String, val desc: String) {
        /** 入门档（总内存 < 4GB）：推荐使用零内存规则引擎或按需云端 */
        ENTRY_TIER("入门设备", "适合本地规则引擎，极速低功耗"),
        /** 主流档（总内存 4GB ~ 6GB）：推荐使用 Qwen2.5-0.5B 等超轻量端侧模型 */
        MID_TIER("主流设备", "支持 0.5B 轻量端侧大模型，兼顾速度与质量"),
        /** 进阶档（总内存 >= 8GB）：推荐使用 Qwen2.5-1.5B / Gemma-3-1B 等高性能模型 */
        HIGH_TIER("高性能设备", "支持 1B~2B 级端侧大模型与多步复杂推理")
    }

    /**
     * 获取设备总 RAM 内存大小（MB）
     */
    fun getTotalRamMb(context: Context): Long {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024)
        } catch (e: Exception) {
            2048L // 异常时保守估计为 2GB
        }
    }

    /**
     * 获取当前系统可用 RAM 大小（MB）
     */
    fun getAvailableRamMb(context: Context): Long {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            memInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            512L
        }
    }

    /**
     * 获取 CPU 可用核心数
     */
    fun getCpuCores(): Int {
        return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }

    /**
     * 评估当前设备的 AI 计算档位
     */
    fun getDeviceTier(context: Context): DeviceTier {
        val totalRam = getTotalRamMb(context)
        return when {
            totalRam >= 7000 -> DeviceTier.HIGH_TIER
            totalRam >= 3500 -> DeviceTier.MID_TIER
            else -> DeviceTier.ENTRY_TIER
        }
    }

    /**
     * 获取最推荐的端侧默认模型 ID
     */
    fun getRecommendedModelId(context: Context): String {
        return when (getDeviceTier(context)) {
            DeviceTier.HIGH_TIER -> "qwen2.5-1.5b-it-q4"
            DeviceTier.MID_TIER -> "qwen2.5-0.5b-it-q4"
            DeviceTier.ENTRY_TIER -> "on-device"
        }
    }

    /**
     * 判断当前设备是否具备运行指定模型的内存与系统要求
     */
    fun canRunModel(context: Context, minRamMb: Int, minSdk: Int = 24): Boolean {
        if (Build.VERSION.SDK_INT < minSdk) return false
        val totalRam = getTotalRamMb(context)
        return totalRam >= (minRamMb * 0.85).toLong() // 允许 15% 宽松余量
    }
}
