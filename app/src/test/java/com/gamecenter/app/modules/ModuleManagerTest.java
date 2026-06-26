package com.gamecenter.app.modules;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.gamecenter.app.core.common.ModuleInterface;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * ModuleManager 单元测试。
 *
 * <p>测试模块管理器的核心功能：
 * <ul>
 *   <li>模块下载</li>
 *   <li>模块安装</li>
 *   <li>模块加载/卸载</li>
 *   <li>模块信息查询</li>
 * </ul>
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-05-27
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 29, 30, 31, 32, 33, 34, 35})
public class ModuleManagerTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        ShadowLooper.idleMainLooper();
    }

    /**
     * 测试下载不存在的模块。
     *
     * <p>下载不存在的模块应该触发 onError 回调。
     */
    @Test
    public void testDownloadNonExistentModule() throws InterruptedException {
        final boolean[] onErrorCalled = {false};
        final CountDownLatch latch = new CountDownLatch(1);

        ModuleManager.INSTANCE.downloadModule(context, "nonExistentModule", new ModuleDownloader.Callback() {
            @Override
            public void onProgress(String moduleId, long downloaded, long total, long speedKbps) {
                // 不应该被调用
            }

            @Override
            public void onComplete(String moduleId, File file) {
                // 不应该被调用
            }

            @Override
            public void onError(String moduleId, String message) {
                onErrorCalled[0] = true;
                latch.countDown();
            }

            @Override
            public void onError(String moduleId, int errorCode, String message) {
                onErrorCalled[0] = true;
                latch.countDown();
            }

            @Override
            public void onSourceSwitch(String moduleId, int sourceIndex, String url) {
                // 可能不会被调用
            }
        });

        latch.await(3, TimeUnit.SECONDS);
        ShadowLooper.idleMainLooper();

        assertTrue("下载不存在的模块应该触发 onError", onErrorCalled[0]);
    }

    /**
     * 测试检查模块是否已安装。
     */
    @Test
    public void testIsModuleInstalled() {
        // 测试不存在的模块
        boolean result = ModuleManager.INSTANCE.isModuleInstalled(context, "nonExistentModule");
        assertFalse("不存在的模块应该返回 false", result);
    }

    /**
     * 测试检查模块是否已加载。
     */
    @Test
    public void testIsModuleLoaded() {
        // 测试不存在的模块
        boolean result = ModuleManager.INSTANCE.isModuleLoaded("nonExistentModule");
        assertFalse("不存在的模块应该返回 false", result);
    }

    /**
     * 测试加载不存在的模块。
     */
    @Test
    public void testLoadNonExistentModule() {
        ModuleInterface result = ModuleManager.INSTANCE.loadModule(context, "nonExistentModule");
        assertNull("加载不存在的模块应该返回 null", result);
    }

    /**
     * 测试卸载模块。
     */
    @Test
    public void testUnloadModule() {
        // 卸载不存在的模块（应该不抛出异常）
        try {
            ModuleManager.INSTANCE.unloadModule(context, "nonExistentModule");
            // 如果没有异常，测试通过
            assertTrue(true);
        } catch (Exception e) {
            fail("卸载不存在的模块不应该抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试卸载并删除模块。
     */
    @Test
    public void testUninstallModule() {
        // 卸载并删除不存在的模块（应该不抛出异常）
        try {
            ModuleManager.INSTANCE.uninstallModule(context, "nonExistentModule");
            assertTrue(true);
        } catch (Exception e) {
            fail("卸载并删除不存在的模块不应该抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试获取可用模块列表。
     */
    @Test
    public void testGetAvailableModules() {
        List<ModuleManifest> modules = ModuleManager.INSTANCE.getAvailableModules();

        assertNotNull("模块列表不应为 null", modules);
        // 注意：初始状态可能没有模块，所以列表可能为空
    }

    /**
     * 测试根据 ID 获取模块清单。
     */
    @Test
    public void testGetModuleManifest() {
        ModuleManifest result = ModuleManager.INSTANCE.getModuleManifest("nonExistentModule");

        assertNull("不存在的模块应该返回 null", result);
    }

    /**
     * 测试获取已安装模块 ID 列表。
     */
    @Test
    public void testGetInstalledModuleIds() {
        // 2026-06-19: getInstalledModuleIds 返回 Set<String>（与 ModuleManager.kt 实际签名一致）
        Set<String> ids = ModuleManager.INSTANCE.getInstalledModuleIds(context);

        assertNotNull("已安装模块 ID 列表不应为 null", ids);
        // 初始状态可能没有已安装模块
    }

    /**
     * 测试取消下载。
     */
    @Test
    public void testCancelDownload() {
        // 取消不存在的模块下载（应该不抛出异常）
        try {
            ModuleManager.INSTANCE.cancelDownload("nonExistentModule");
            assertTrue(true);
        } catch (Exception e) {
            fail("取消不存在的模块下载不应该抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试注册本地备用 URL（如果需要）。
     */
    @Test
    public void testRegisterLocalFallbackIfNeeded() {
        // 注册本地备用（应该不抛出异常）
        try {
            ModuleManager.INSTANCE.registerLocalFallbackIfNeeded(context);
            assertTrue(true);
        } catch (Exception e) {
            fail("注册本地备用不应该抛出异常: " + e.getMessage());
        }
    }

    // 2026-06-19: 以下测试方法已移除（ModuleManager 中不存在对应 API）：
    //   - testLoadGameModule: loadGameModule(Context, String) 方法不存在
    //   - testGetBuiltInVersion: getBuiltInVersion(Context, String) 方法不存在
    // 如需恢复，请先在 ModuleManager 中实现这些 API，或改用 GameRegistry 等其他入口。
}
