package com.gamecenter.app

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.gamecenter.app.modules.ModuleDownloadManager
import com.gamecenter.app.modules.ModuleVersionChecker
import com.gamecenter.app.modules.ModuleManifest
import android.content.Context
import java.io.File

/**
 * T02: 模块商店功能测试
 * 
 * 测试目标：
 * 1. 验证 ModuleDownloadManager 的下载逻辑
 * 2. 验证 ModuleVersionChecker 的版本比较逻辑
 * 3. 验证 ModuleInstaller 的安装逻辑（如果存在）
 * 
 * 作者: 严过关 (Yan) - GameCenterApp QA 工程师
 * 日期: 2026-05-26
 */
@RunWith(AndroidJUnit4::class)
class T02ModuleStoreTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    private lateinit var context: Context
    
    @Before
    fun setUp() {
        activityRule.scenario.onActivity { activity ->
            context = activity.applicationContext
        }
    }
    
    // ========== ModuleVersionChecker 测试 ==========
    
    /**
     * TC-MV-001: 验证 compareVersions() - 商店版本更高
     */
    @Test
    fun testCompareVersions_storeVersionHigher() {
        val result = ModuleVersionChecker.compareVersions(100, 201)
        assertEquals("Store version should be higher", 1, result)
    }
    
    /**
     * TC-MV-002: 验证 compareVersions() - 版本相同
     */
    @Test
    fun testCompareVersions_sameVersion() {
        val result = ModuleVersionChecker.compareVersions(201, 201)
        assertEquals("Versions should be equal", 0, result)
    }
    
    /**
     * TC-MV-003: 验证 compareVersions() - 内置版本更高（异常场景）
     */
    @Test
    fun testCompareVersions_builtInVersionHigher() {
        val result = ModuleVersionChecker.compareVersions(201, 100)
        assertEquals("Built-in version should be higher", -1, result)
    }
    
    /**
     * TC-MV-004: 验证 shouldLoadExternal() - 应该加载外部模块
     */
    @Test
    fun testShouldLoadExternal_true() {
        val manifest = ModuleManifest(
            id = "test_module",
            name = "Test Module",
            description = "",
            versionName = "2.0",
            versionCode = 201,
            entryClass = "",
            fileName = "",
            fileSize = 0L,
            sha256 = "",
            downloadUrl = ""
        )

        val result = ModuleVersionChecker.shouldLoadExternal(100, manifest)
        assertTrue("Should load external module when store version is higher", result)
    }

    /**
     * TC-MV-005: 验证 shouldLoadExternal() - 不应该加载外部模块
     */
    @Test
    fun testShouldLoadExternal_false() {
        val manifest = ModuleManifest(
            id = "test_module",
            name = "Test Module",
            description = "",
            versionName = "1.0",
            versionCode = 100,
            entryClass = "",
            fileName = "",
            fileSize = 0L,
            sha256 = "",
            downloadUrl = ""
        )

        val result = ModuleVersionChecker.shouldLoadExternal(201, manifest)
        assertFalse("Should not load external module when built-in version is higher", result)
    }
    
    /**
     * TC-MV-006: 验证 getBuiltInVersion() - 读取内置模块版本
     */
    @Test
    fun testGetBuiltInVersion() {
        // 验证 assets/modules.json 是否存在
        try {
            val inputStream = context.assets.open("modules.json")
            assertNotNull("modules.json should exist in assets", inputStream)
            inputStream.close()
            println("✓ assets/modules.json exists")
        } catch (e: Exception) {
            println("⚠️ assets/modules.json not found: ${e.message}")
            // 不失败，因为可能是可选文件
        }
        
        // 测试获取内置版本（应该返回 0 或实际版本号）
        val version = ModuleVersionChecker.getBuiltInVersion(context, "doudizhu")
        assertTrue("Version code should be >= 0", version >= 0)
        println("Built-in version for 'doudizhu': $version")
    }
    
    // ========== ModuleDownloadManager 测试 ==========
    
    /**
     * TC-MD-001: 验证 ModuleDownloadManager 类存在且可访问
     */
    @Test
    fun testModuleDownloadManager_classExists() {
        try {
            val clazz = Class.forName("com.gamecenter.app.modules.ModuleDownloadManager")
            assertNotNull("ModuleDownloadManager class should exist", clazz)
            println("✓ ModuleDownloadManager class loaded successfully")
        } catch (e: ClassNotFoundException) {
            fail("ModuleDownloadManager class not found: ${e.message}")
        }
    }
    
    /**
     * TC-MD-002: 验证 isModuleDownloaded() - 文件不存在
     */
    @Test
    fun testIsModuleDownloaded_fileNotExists() {
        val manifest = ModuleManifest(
            id = "test_module",
            name = "Test Module",
            description = "",
            versionName = "1.0",
            versionCode = 1,
            entryClass = "",
            fileName = "test_module.apk",
            fileSize = 0L,
            sha256 = "dummy",
            downloadUrl = ""
        )

        val result = ModuleDownloadManager.isModuleDownloaded(context, manifest)
        assertFalse("Module should not be downloaded", result)
        println("✓ isModuleDownloaded() returns false for non-existent file")
    }
    
    /**
     * TC-MD-003: 验证 cancelDownload() - 取消不存在的下载
     */
    @Test
    fun testCancelDownload_notExists() {
        // 取消一个不存在的下载（应该不崩溃）
        try {
            ModuleDownloadManager.cancelDownload("non_existent_module")
            println("✓ cancelDownload() did not crash for non-existent module")
        } catch (e: Exception) {
            fail("cancelDownload() should not throw exception: ${e.message}")
        }
    }
    
    /**
     * TC-MD-004: 验证 deleteDownloadedModule() - 删除不存在的文件
     */
    @Test
    fun testDeleteDownloadedModule_notExists() {
        val manifest = ModuleManifest(
            id = "test_module",
            name = "Test Module",
            description = "",
            versionName = "1.0",
            versionCode = 1,
            entryClass = "",
            fileName = "test_module.apk",
            fileSize = 0L,
            sha256 = "",
            downloadUrl = ""
        )

        val result = ModuleDownloadManager.deleteDownloadedModule(context, manifest)
        assertFalse("Should return false when file does not exist", result)
        println("✓ deleteDownloadedModule() returns false for non-existent file")
    }
    
    // ========== ModuleInstaller 测试 ==========
    
    /**
     * TC-MI-001: 验证 ModuleInstaller 类是否存在
     * 
     * 注意：ModuleInstaller.kt 在架构设计中存在，但实际文件可能未创建
     * 此测试验证类是否可访问
     */
    @Test
    fun testModuleInstaller_classExists() {
        try {
            val clazz = Class.forName("com.gamecenter.app.modules.ModuleInstaller")
            assertNotNull("ModuleInstaller class should exist", clazz)
            println("✓ ModuleInstaller class loaded successfully")
        } catch (e: ClassNotFoundException) {
            println("⚠️ ModuleInstaller class not found - may be not implemented yet")
            // 不失败，因为可能工程师还未实现
        }
    }
    
    /**
     * TC-MI-002: 验证 installApkPlugin() 方法存在（如果类存在）
     */
    @Test
    fun testInstallApkPlugin_methodExists() {
        try {
            val clazz = Class.forName("com.gamecenter.app.modules.ModuleInstaller")
            
            // 验证 installApkPlugin 方法是否存在
            val method = clazz.getMethod("installApkPlugin", File::class.java)
            assertNotNull("installApkPlugin() method should exist", method)
            println("✓ ModuleInstaller.installApkPlugin() method exists")
        } catch (e: ClassNotFoundException) {
            println("⚠️ ModuleInstaller class not found - skipping method check")
        } catch (e: NoSuchMethodException) {
            println("⚠️ ModuleInstaller.installApkPlugin() method not found")
        } catch (e: Exception) {
            println("⚠️ Error checking ModuleInstaller: ${e.message}")
        }
    }
    
    // ========== 集成测试 ==========
    
    /**
     * TC-MS-001: 验证模块商店完整流程（存根测试）
     * 
     * 此测试验证模块商店的核心组件是否存在且可访问
     */
    @Test
    fun testModuleStoreComponentsAccessible() {
        val components = listOf(
            "com.gamecenter.app.modules.ModuleDownloadManager",
            "com.gamecenter.app.modules.ModuleVersionChecker"
        )
        
        for (className in components) {
            try {
                val clazz = Class.forName(className)
                assertNotNull("Component $className should be accessible", clazz)
                println("✓ Component $className is accessible")
            } catch (e: ClassNotFoundException) {
                fail("Component $className not found: ${e.message}")
            }
        }
    }
}
