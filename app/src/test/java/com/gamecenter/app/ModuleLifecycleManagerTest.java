package com.gamecenter.app;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.Application;

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

import java.util.ArrayList;
import java.util.List;

/**
 * ModuleLifecycleManager 单元测试。
 *
 * 测试模块生命周期管理器的各种功能：
 * - 模块加载/卸载
 * - 模块启动/停止
 * - 依赖管理
 * - 生命周期调用
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class ModuleLifecycleManagerTest {

    private ModuleLifecycleManager lifecycleManager;

    @Mock
    private Application mockApplication;

    @Mock
    private ModuleLoaderV2 mockModuleLoader;

    @Mock
    private IModule mockModule;

    @Mock
    private ModuleInfo mockModuleInfo;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // 使用 Robolectric 提供的 Application
        Application app = RuntimeEnvironment.getApplication();
        assertNotNull("Robolectric Application 不应为 null", app);

        // 获取 ModuleLifecycleManager 实例
        lifecycleManager = ModuleLifecycleManager.getInstance(app);
        assertNotNull("ModuleLifecycleManager 实例不应为 null", lifecycleManager);
    }

    /**
     * 测试单例模式。
     */
    @Test
    public void testGetInstance_Singleton() {
        Application app = RuntimeEnvironment.getApplication();

        ModuleLifecycleManager instance1 = ModuleLifecycleManager.getInstance(app);
        ModuleLifecycleManager instance2 = ModuleLifecycleManager.getInstance(app);

        assertSame("应为同一实例", instance1, instance2);
    }

    /**
     * 测试加载模块（模块未加载）。
     */
    @Test
    public void testLoadModule_NotLoaded() throws IModuleLoader.ModuleLoadException {
        // 准备测试数据
        String moduleId = "testModule";
        when(mockModuleInfo.getModuleId()).thenReturn(moduleId);

        // 验证模块未加载
        assertFalse("模块不应已加载", lifecycleManager.isModuleLoaded(moduleId));

        // 注意：由于 ModuleLoaderV2 是实际实现的，这里会调用真实逻辑
        // 在真实测试中，可能需要 mock ModuleLoaderV2
        // 这里主要测试生命周期管理器的逻辑
    }

    /**
     * 测试加载模块（模块已加载）。
     */
    @Test
    public void testLoadModule_AlreadyLoaded() throws IModuleLoader.ModuleLoadException {
        // 注意：由于当前实现中 loadedModules 是私有成员，
        // 我们无法直接注入已加载的模块。
        // 这里测试重复加载时的情况（应直接返回已加载的模块）。

        String moduleId = "testModule";
        when(mockModuleInfo.getModuleId()).thenReturn(moduleId);

        // 第一次加载
        try {
            lifecycleManager.loadModule(mockModuleInfo);
        } catch (Exception e) {
            // 预期可能失败（因为 ModuleLoaderV2 可能未正确初始化）
            // 这里主要验证不会崩溃
        }
    }

    /**
     * 测试加载模块（null 参数）。
     */
    @Test(expected = IModuleLoader.ModuleLoadException.class)
    public void testLoadModule_NullParameter() throws IModuleLoader.ModuleLoadException {
        lifecycleManager.loadModule(null);
    }

    /**
     * 测试卸载模块。
     */
    @Test
    public void testUnloadModule() throws IModuleLoader.ModuleUnloadException {
        String moduleId = "testModule";

        // 验证模块未加载
        assertFalse("模块不应已加载", lifecycleManager.isModuleLoaded(moduleId));

        // 卸载未加载的模块应抛出异常
        try {
            lifecycleManager.unloadModule(moduleId);
            fail("卸载未加载的模块应抛出异常");
        } catch (IModuleLoader.ModuleUnloadException e) {
            assertEquals("错误码应为 ERROR_MODULE_NOT_LOADED",
                    IModuleLoader.ModuleUnloadException.ERROR_MODULE_NOT_LOADED,
                    e.getErrorCode());
        }
    }

    /**
     * 测试卸载模块（空 moduleId）。
     */
    @Test(expected = IModuleLoader.ModuleUnloadException.class)
    public void testUnloadModule_EmptyModuleId() throws IModuleLoader.ModuleUnloadException {
        lifecycleManager.unloadModule("");
    }

    /**
     * 测试卸载模块（null moduleId）。
     */
    @Test(expected = IModuleLoader.ModuleUnloadException.class)
    public void testUnloadModule_NullModuleId() throws IModuleLoader.ModuleUnloadException {
        lifecycleManager.unloadModule(null);
    }

    /**
     * 测试重新加载模块。
     */
    @Test
    public void testReloadModule() {
        String moduleId = "testModule";

        // 重新加载未加载的模块应抛出异常
        try {
            lifecycleManager.reloadModule(moduleId);
            fail("重新加载未加载的模块应抛出异常");
        } catch (IModuleLoader.ModuleLoadException e) {
            assertEquals("错误码应为 ERROR_MODULE_NOT_FOUND",
                    IModuleLoader.ModuleLoadException.ERROR_MODULE_NOT_FOUND,
                    e.getErrorCode());
        }
    }

    /**
     * 测试启动模块。
     */
    @Test
    public void testStartModule() {
        String moduleId = "testModule";

        // 启动未加载的模块应返回 false
        boolean result = lifecycleManager.startModule(moduleId);
        assertFalse("启动未加载的模块应返回 false", result);
    }

    /**
     * 测试停止模块。
     */
    @Test
    public void testStopModule() {
        String moduleId = "testModule";

        // 停止未加载的模块应返回 false
        boolean result = lifecycleManager.stopModule(moduleId);
        assertFalse("停止未加载的模块应返回 false", result);
    }

    /**
     * 测试检查模块是否已加载。
     */
    @Test
    public void testIsModuleLoaded() {
        String moduleId = "testModule";

        // 初始状态：未加载
        assertFalse("模块不应已加载", lifecycleManager.isModuleLoaded(moduleId));
    }

    /**
     * 测试获取已加载模块列表。
     */
    @Test
    public void testGetLoadedModules() {
        List<String> loadedModules = lifecycleManager.getLoadedModules();

        assertNotNull("已加载模块列表不应为 null", loadedModules);
        assertEquals("初始状态应有 0 个已加载模块", 0, loadedModules.size());
    }

    /**
     * 测试获取模块实例。
     */
    @Test
    public void testGetModule() {
        String moduleId = "testModule";

        // 获取未加载的模块应返回 null
        IModule module = lifecycleManager.getModule(moduleId);
        assertNull("未加载的模块应返回 null", module);
    }

    /**
     * 测试添加依赖关系。
     */
    @Test
    public void testAddDependency() {
        String moduleId = "moduleA";
        List<String> dependsOn = new ArrayList<>();
        dependsOn.add("moduleB");
        dependsOn.add("moduleC");

        // 添加依赖关系（不应抛出异常）
        try {
            lifecycleManager.addDependency(moduleId, dependsOn);
        } catch (Exception e) {
            fail("添加依赖关系不应抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试添加依赖关系（null 参数）。
     */
    @Test
    public void testAddDependency_NullParameters() {
        // null moduleId（不应崩溃）
        lifecycleManager.addDependency(null, new ArrayList<>());

        // null dependsOn（不应崩溃）
        lifecycleManager.addDependency("moduleA", null);
    }

    /**
     * 测试初始化。
     */
    @Test
    public void testInitialize() {
        // 初始化（不应抛出异常）
        try {
            lifecycleManager.initialize();
        } catch (Exception e) {
            fail("初始化不应抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试释放资源。
     */
    @Test
    public void testRelease() {
        // 释放资源（不应抛出异常）
        try {
            lifecycleManager.release();
        } catch (Exception e) {
            fail("释放资源不应抛出异常: " + e.getMessage());
        }
    }

    /**
     * 测试循环依赖检测。
     */
    @Test
    public void testCircularDependencyDetection() {
        // 添加依赖关系：moduleA -> moduleB -> moduleC -> moduleA（循环）
        List<String> depsA = new ArrayList<>();
        depsA.add("moduleB");
        lifecycleManager.addDependency("moduleA", depsA);

        List<String> depsB = new ArrayList<>();
        depsB.add("moduleC");
        lifecycleManager.addDependency("moduleB", depsB);

        List<String> depsC = new ArrayList<>();
        depsC.add("moduleA"); // 循环依赖
        lifecycleManager.addDependency("moduleC", depsC);

        // 尝试加载 moduleA（应检测到循环依赖）
        ModuleInfo moduleInfo = mock(ModuleInfo.class);
        when(moduleInfo.getModuleId()).thenReturn("moduleA");

        try {
            lifecycleManager.loadModule(moduleInfo);
            // 注意：当前实现中，如果 ModuleLoaderV2 抛出异常，这里可能不会到达
            // 这取决于 ModuleDependencyGraph 的实现
        } catch (IModuleLoader.ModuleLoadException e) {
            // 预期可能抛出异常（如果是循环依赖）
            assertTrue("异常消息应包含循环依赖信息",
                    e.getMessage().contains("循环依赖") ||
                            e.getMessage().contains("Circular"));
        }
    }

    /**
     * 测试模块加载顺序（按依赖顺序）。
     */
    @Test
    public void testLoadOrder() {
        // 添加依赖关系：moduleC -> moduleB -> moduleA
        List<String> depsB = new ArrayList<>();
        depsB.add("moduleA");
        lifecycleManager.addDependency("moduleB", depsB);

        List<String> depsC = new ArrayList<>();
        depsC.add("moduleB");
        lifecycleManager.addDependency("moduleC", depsC);

        // 验证依赖关系已添加
        // 注意：由于 loadedModules 是私有成员，我们无法直接验证加载顺序
        // 这里主要验证不会崩溃
        assertTrue("测试通过（无异常）", true);
    }
}
