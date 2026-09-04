package com.gamecenter.app.modules;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.core.common.ModuleInterface;
import com.gamecenter.app.core.common.ModuleManifest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

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
        clearModuleManifests();
    }

    @After
    public void tearDown() {
        clearModuleManifests();
        ShadowLooper.idleMainLooper();
    }

    /** ModuleManager 是进程 singleton；每个测试都必须从空 runtime index 开始。 */
    private void clearModuleManifests() {
        try {
            Field field = ModuleManager.class.getDeclaredField("manifests");
            field.setAccessible(true);
            Map<?, ?> manifests = (Map<?, ?>) field.get(ModuleManager.INSTANCE);
            manifests.clear();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("无法清理 ModuleManager.manifests", e);
        }
    }

    private ModuleManifest readShippedVpnManifest() throws Exception {
        StringBuilder jsonText = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("modules.json"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonText.append(line);
            }
        }
        JSONArray modules = new JSONObject(jsonText.toString()).getJSONArray("modules");
        for (int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.getJSONObject(i);
            if ("vpn".equals(module.optString("id"))) {
                return ModuleManifest.Companion.fromJson(module);
            }
        }
        throw new AssertionError("assets/modules.json 缺少 VPN 条目");
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
            public void onStateChanged(String moduleId, String state) {
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
    public void testRegisterLocalFallbackIfNeeded() throws Exception {
        ModuleManifest shipped = readShippedVpnManifest();
        // 注册本地备用（应该不抛出异常）
        try {
            ModuleManager.INSTANCE.registerLocalFallbackIfNeeded(context);
            ModuleManifest vpn = ModuleManager.INSTANCE.getModuleManifest("vpn");
            assertNotNull("assets 清单必须包含 VPN", vpn);
            assertEquals(
                    "有 Context 时应保留 assets 清单自身的下载地址",
                    shipped.getDownloadUrl(),
                    vpn.getDownloadUrl());
        } catch (Exception e) {
            fail("注册本地备用不应该抛出异常: " + e.getMessage());
        }
    }

    /**
     * 无 Context 的导航清单恢复也必须使用当前配置的下载源。
     *
     * <p>回归：registerAvailableManifests() 会调用无 Context 的本地兜底，
     * 该路径曾把 vpn 的下载地址改回已废弃的 tcp0053.shop，导致 VPN 下载
     * 无法命中当前镜像级联。</p>
     */
    @Test
    public void testVpnFallbackMatchesShippedMetadataAndConfiguredUrl() throws Exception {
        ModuleManifest shipped = readShippedVpnManifest();
        ModuleManager.INSTANCE.registerLocalFallbackIfNeeded(null);

        ModuleManifest vpn = ModuleManager.INSTANCE.getModuleManifest("vpn");
        assertNotNull("VPN 本地兜底清单必须存在", vpn);
        assertEquals(shipped.getFileName(), vpn.getFileName());
        assertEquals(shipped.getFileSize(), vpn.getFileSize());
        assertEquals(shipped.getSha256(), vpn.getSha256());
        assertEquals(shipped.getCategory(), vpn.getCategory());
        assertEquals(shipped.getMinAppVersionCode(), vpn.getMinAppVersionCode());
        assertEquals(shipped.getDetails(), vpn.getDetails());
        assertEquals(
                "VPN 兜底必须跟随当前下载源配置",
                BuildConfig.DOWNLOAD_BASE_URL + shipped.getFileName(),
                vpn.getDownloadUrl());
    }

    @Test
    public void testTrustedVpnManifestIsNotOverwrittenByFallback() throws Exception {
        String sha256 = new String(new char[64]).replace('\0', 'a');
        ModuleManifest trusted = ModuleManifest.Companion.fromJson(new JSONObject()
                .put("id", "vpn")
                .put("name", "VPN cached")
                .put("versionCode", 999)
                .put("entryClass", "com.gamecenter.app.vpn.VpnModuleEntryPoint")
                .put("fileName", "vpn-v999.apk")
                .put("fileSize", 123L)
                .put("sha256", sha256)
                .put("downloadUrl", "https://catalog.example/vpn-v999.apk"));

        ModuleManager.INSTANCE.registerAvailableManifests(Collections.singletonList(trusted));
        ModuleManager.INSTANCE.registerLocalFallbackIfNeeded(null);

        assertEquals("可信 Catalog 清单不可被本地兜底覆盖", trusted,
                ModuleManager.INSTANCE.getModuleManifest("vpn"));
    }

    @Test
    public void testNullFallbackRegistersVpnWhenManifestTableIsEmpty() {
        assertTrue(ModuleManager.INSTANCE.getManifests().isEmpty());

        ModuleManager.INSTANCE.registerLocalFallbackIfNeeded(null);

        ModuleManifest vpn = ModuleManager.INSTANCE.getModuleManifest("vpn");
        assertNotNull("空 manifests 时 null fallback 仍须注册 VPN", vpn);
    }

    // 2026-06-19: 以下测试方法已移除（ModuleManager 中不存在对应 API）：
    //   - testLoadGameModule: loadGameModule(Context, String) 方法不存在
    //   - testGetBuiltInVersion: getBuiltInVersion(Context, String) 方法不存在
    // 如需恢复，请先在 ModuleManager 中实现这些 API，或改用 GameRegistry 等其他入口。
}
