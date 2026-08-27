package com.gamecenter.app.modules

import com.gamecenter.app.core.common.ModuleManifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1 隔离策略回归：内置/外置装载判定。
 *
 * 关闭隔离缺口后：
 * - fileName 为空且 builtIn → 宿主内嵌直载（预期行为，不判外置）；
 * - fileName 非空（含预装内置 APK）→ 一律外置，要求文件存在且清单配置非空 SHA-256；
 * - 文件缺失或清单缺 SHA-256 → 必须拒绝外置（不允许回退宿主陈旧副本）。
 */
class ModuleLoaderIsolationTest {

    private fun manifest(fileName: String = "", sha256: String = "a".repeat(64), builtIn: Boolean = false) =
        ModuleManifest(
            id = "test_module",
            name = "Test",
            fileName = fileName,
            sha256 = sha256,
            builtIn = builtIn
        )

    @Test
    fun `host embedded module without file must load from host classloader`() {
        // fileName 为空的宿主内嵌模块：不应走外置路径
        assertFalse(ModuleLoader.shouldLoadExternal(manifest(fileName = "", builtIn = true), fileExists = false))
    }

    @Test
    fun `file module with valid sha and existing file must load externally`() {
        // 预装内置 APK（fileName 非空 + SHA 非空 + 文件存在）→ 外置 DexClassLoader
        assertTrue(
            ModuleLoader.shouldLoadExternal(
                manifest(fileName = "game_go_v100.apk", builtIn = true),
                fileExists = true
            )
        )
    }

    @Test
    fun `file module missing on disk must be rejected even if builtIn`() {
        // 内置模块文件缺失 → 拒绝外置，绝不回退宿主直载（隔离缺口关闭）
        assertFalse(
            ModuleLoader.shouldLoadExternal(
                manifest(fileName = "game_go_v100.apk", builtIn = true),
                fileExists = false
            )
        )
    }

    @Test
    fun `file module without sha must be rejected`() {
        // 内置/外置模块清单缺 SHA-256 → 拒绝装载（不允许免检）
        assertFalse(
            ModuleLoader.shouldLoadExternal(
                manifest(fileName = "feature_tools_v100.apk", sha256 = "", builtIn = true),
                fileExists = true
            )
        )
    }

    @Test
    fun `non-builtin module without file must not load externally`() {
        assertFalse(ModuleLoader.shouldLoadExternal(manifest(fileName = "remote.apk"), fileExists = false))
    }
}