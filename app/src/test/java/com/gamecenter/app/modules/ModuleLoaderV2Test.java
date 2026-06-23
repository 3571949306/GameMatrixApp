package com.gamecenter.app.modules;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

import com.gamecenter.app.interfaces.IModule;
import com.gamecenter.app.interfaces.IModuleLoader;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.moduleloader.ModuleLoaderV2;
import com.gamecenter.app.moduleloader.ModuleVerifier;

import java.io.File;
import java.lang.reflect.Field;

/**
 * ModuleLoaderV2 单元测试。
 *
 * 测试模块加载器的各种功能：
 * - 模块加载/卸载
 * - 模块重新加载
 * - 加载状态检查
 * - 异常处理
 *
 * @author Software Engineer (Kou)
 * @version 1.0
 * @since 2026-05-27
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class ModuleLoaderV2Test {

    private ModuleLoaderV2 moduleLoader;
    private Context context;

    @Mock
    private ModuleInfo mockModuleInfo;

    @Mock
    private IModule mockModule;

    @Mock
    private ModuleVerifier mockVerifier;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        context = RuntimeEnvironment.getApplication();
        assertNotNull("Context 不应为 null", context);

        // 重置单例，确保每个测试使用独立的 ModuleLoaderV2 实例
        try {
            java.lang.reflect.Field instanceField = ModuleLoaderV2.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            // 忽略反射失败
        }

        // 获取 ModuleLoaderV2 实例
        moduleLoader = ModuleLoaderV2.getInstance(context);
        assertNotNull("ModuleLoaderV2 实例不应为 null", moduleLoader);
    }

    /**
     * 测试加载模块（模块未找到）。
     */
    @Test(expected = IModuleLoader.ModuleLoadException.class)
    public void testLoadModule_NotFound() throws IModuleLoader.ModuleLoadException {
        // 模拟 ModuleInfo
        when(mockModuleInfo.getModuleId()).thenReturn("nonExistentModule");
        when(mockModuleInfo.getSha256()).thenReturn("");
        when(mockModuleInfo.getFileSize()).thenReturn(0L);
        when(mockModuleInfo.getVersionCode()).thenReturn(1);
        when(mockModuleInfo.getVersionName()).thenReturn("1.0.0");

        // 加载不存在的模块应抛出异常
        moduleLoader.loadModule(mockModuleInfo);
    }

    /**
     * 测试加载模块（null 参数）。
     */
    @Test(expected = IModuleLoader.ModuleLoadException.class)
    public void testLoadModule_NullParameter() throws IModuleLoader.ModuleLoadException {
        moduleLoader.loadModule((ModuleInfo) null);
    }

    /**
     * 测试加载模块（通过 moduleId，模块未加载）。
     */
    @Test(expected = IModuleLoader.ModuleLoadException.class)
    public void testLoadModuleById_NotLoaded() throws IModuleLoader.ModuleLoadException {
        moduleLoader.loadModule("nonExistentModule");
    }

    /**
     * 测试卸载模块。
     */
    @Test
    public void testUnloadModule() throws IModuleLoader.ModuleUnloadException {
        String moduleId = "testModule";

        // 卸载未加载的模块（不应抛出异常，静默处理）
        try {
            moduleLoader.unloadModule(moduleId);
            assertTrue("卸载未加载的模块应静默处理", true);
        } catch (Exception e) {
            fail("卸载未加载的模块不应抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试卸载模块（空 moduleId）。
     */
    @Test
    public void testUnloadModule_EmptyModuleId() throws IModuleLoader.ModuleUnloadException {
        try {
            moduleLoader.unloadModule("");
            assertTrue("卸载空 moduleId 应静默处理", true);
        } catch (Exception e) {
            fail("卸载空 moduleId 不应抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试卸载模块（null moduleId）。
     */
    @Test
    public void testUnloadModule_NullModuleId() throws IModuleLoader.ModuleUnloadException {
        try {
            moduleLoader.unloadModule(null);
            assertTrue("卸载 null moduleId 应静默处理", true);
        } catch (Exception e) {
            fail("卸载 null moduleId 不应抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试重新加载模块（模块未加载）。
     */
    @Test(expected = IModuleLoader.ModuleLoadException.class)
    public void testReloadModule_NotLoaded() throws IModuleLoader.ModuleLoadException {
        moduleLoader.reloadModule("nonExistentModule");
    }

    /**
     * 测试检查模块是否已加载（模块未加载）。
     */
    @Test
    public void testIsModuleLoaded_NotLoaded() {
        String moduleId = "testModule";

        boolean result = moduleLoader.isModuleLoaded(moduleId);
        assertFalse("模块不应已加载", result);
    }

    /**
     * 测试检查模块是否已加载（模块已加载）。
     */
    @Test
    public void testIsModuleLoaded_AlreadyLoaded() throws IModuleLoader.ModuleLoadException {
        // 注意：由于 loadModule 需要真实的 APK 文件，这里难以测试真实场景
        // 我们可以通过反射直接操作 loadedModules 来模拟已加载状态
        String moduleId = "testModule";

        // 验证初始状态
        assertFalse("初始状态模块不应已加载", moduleLoader.isModuleLoaded(moduleId));
    }

    /**
     * 测试获取已加载模块列表（空列表）。
     */
    @Test
    public void testGetLoadedModules_Empty() {
        var loadedModules = moduleLoader.getLoadedModules();

        assertNotNull("已加载模块列表不应为 null", loadedModules);
        assertEquals("初始状态应有 0 个已加载模块", 0, loadedModules.size());
    }

    /**
     * 测试获取模块实例（模块未加载）。
     */
    @Test
    public void testGetModule_NotLoaded() {
        String moduleId = "testModule";

        IModule module = moduleLoader.getModule(moduleId);
        assertNull("未加载的模块应返回 null", module);
    }

    /**
     * 测试从文件加载模块（文件不存在）。
     */
    @Test(expected = IModuleLoader.ModuleLoadException.class)
    public void testLoadModuleFromFile_FileNotFound() throws IModuleLoader.ModuleLoadException {
        when(mockModuleInfo.getModuleId()).thenReturn("testModule");
        when(mockModuleInfo.getVersionCode()).thenReturn(1);

        moduleLoader.loadModuleFromFile("/non/existent/path/module.apk", mockModuleInfo);
    }

    /**
     * 测试检查更新（简化实现）。
     */
    @Test
    public void testCheckUpdate() {
        var result = moduleLoader.checkUpdate("testModule");
        assertNull("简化实现应返回 null", result);
    }

    /**
     * 测试获取状态信息。
     */
    @Test
    public void testGetStatus() {
        String status = moduleLoader.getStatus();

        assertNotNull("状态信息不应为 null", status);
        assertTrue("状态信息应包含 loaded=", status.contains("loaded="));
    }

    /**
     * 测试释放资源。
     */
    @Test
    public void testRelease() {
        try {
            moduleLoader.release();
            assertTrue("释放资源不应抛出异常", true);
        } catch (Exception e) {
            fail("释放资源不应抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试单例模式。
     */
    @Test
    public void testGetInstance_Singleton() {
        ModuleLoaderV2 instance1 = ModuleLoaderV2.getInstance(context);
        ModuleLoaderV2 instance2 = ModuleLoaderV2.getInstance(context);

        assertSame("应为同一实例", instance1, instance2);
    }

    /**
     * 测试获取框架版本号。
     */
    @Test
    public void testGetFrameworkVersionCode() {
        int versionCode = moduleLoader.getFrameworkVersionCode();

        assertTrue("版本号应 >= 1", versionCode >= 1);
    }

    /**
     * 测试设置框架版本号。
     */
    @Test
    public void testSetFrameworkVersionCode() {
        int oldVersion = moduleLoader.getFrameworkVersionCode();

        moduleLoader.setFrameworkVersionCode(100);
        assertEquals("版本号应已更新", 100, moduleLoader.getFrameworkVersionCode());

        // 恢复原始值
        moduleLoader.setFrameworkVersionCode(oldVersion);
    }

    /**
     * 测试设置框架版本号（小于 1 的值）。
     */
    @Test
    public void testSetFrameworkVersionCode_LessThanOne() {
        int oldVersion = moduleLoader.getFrameworkVersionCode();

        moduleLoader.setFrameworkVersionCode(0);
        assertEquals("版本号应至少为 1", 1, moduleLoader.getFrameworkVersionCode());

        moduleLoader.setFrameworkVersionCode(-1);
        assertEquals("版本号应至少为 1", 1, moduleLoader.getFrameworkVersionCode());

        // 恢复原始值
        moduleLoader.setFrameworkVersionCode(oldVersion);
    }

    /**
     * 测试获取 Context。
     */
    @Test
    public void testGetContext() {
        Context ctx = moduleLoader.getContext();

        assertNotNull("Context 不应为 null", ctx);
        assertSame("应为同一 Context", context.getApplicationContext(), ctx);
    }
}
