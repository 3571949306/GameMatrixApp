package com.gamecenter.app;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import com.gamecenter.app.ModuleDependencyGraph.CircularDependencyException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * ModuleDependencyGraph 单元测试。
 *
 * 测试模块依赖关系图的各种功能：
 * - 添加/移除依赖关系
 * - 拓扑排序（加载顺序）
 * - 循环依赖检测
 * - 依赖查询
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {33})
public class ModuleDependencyGraphTest {

    private ModuleDependencyGraph graph;

    @Before
    public void setUp() {
        graph = new ModuleDependencyGraph();
    }

    /**
     * 测试添加依赖关系。
     */
    @Test
    public void testAddDependency() {
        // 添加依赖：moduleB 依赖于 moduleA
        graph.addDependency("moduleB", Arrays.asList("moduleA"));

        // 验证依赖关系
        List<String> allModules = graph.getAllModules();
        assertTrue("应包含 moduleA", allModules.contains("moduleA"));
        assertTrue("应包含 moduleB", allModules.contains("moduleB"));

        // 验证反向依赖
        List<String> dependents = graph.getDependents("moduleA");
        assertEquals("moduleA 应有 1 个依赖者", 1, dependents.size());
        assertEquals("依赖者应为 moduleB", "moduleB", dependents.get(0));
    }

    /**
     * 测试添加多个依赖。
     */
    @Test
    public void testAddMultipleDependencies() {
        // moduleC 依赖于 moduleA 和 moduleB
        graph.addDependency("moduleC", Arrays.asList("moduleA", "moduleB"));

        // 验证所有模块都已注册
        List<String> allModules = graph.getAllModules();
        assertEquals("应有 3 个模块", 3, allModules.size());

        // 验证依赖查询
        Set<String> allDeps = graph.getAllDependencies("moduleC");
        assertEquals("moduleC 应有 2 个依赖", 2, allDeps.size());
        assertTrue("应包含 moduleA", allDeps.contains("moduleA"));
        assertTrue("应包含 moduleB", allDeps.contains("moduleB"));
    }

    /**
     * 测试移除依赖关系。
     */
    @Test
    public void testRemoveDependency() {
        // 添加依赖
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        graph.addDependency("moduleC", Arrays.asList("moduleA"));

        // 验证添加成功
        List<String> dependents = graph.getDependents("moduleA");
        assertEquals("moduleA 应有 2 个依赖者", 2, dependents.size());

        // 移除 moduleB 的依赖
        graph.removeDependency("moduleB");

        // 验证移除成功
        dependents = graph.getDependents("moduleA");
        assertEquals("moduleA 应有 1 个依赖者", 1, dependents.size());
        assertEquals("依赖者应为 moduleC", "moduleC", dependents.get(0));
    }

    /**
     * 测试拓扑排序（无依赖）。
     */
    @Test
    public void testGetLoadOrder_NoDependencies() throws CircularDependencyException {
        // 添加无依赖的模块
        graph.addDependency("moduleA", Arrays.asList());

        // 获取加载顺序
        List<String> loadOrder = graph.getLoadOrder("moduleA");

        // 验证
        assertEquals("加载顺序应有 1 个模块", 1, loadOrder.size());
        assertEquals("应为 moduleA", "moduleA", loadOrder.get(0));
    }

    /**
     * 测试拓扑排序（有依赖）。
     */
    @Test
    public void testGetLoadOrder_WithDependencies() throws CircularDependencyException {
        // 构建依赖关系：moduleC -> moduleB -> moduleA
        graph.addDependency("moduleA", Arrays.asList());
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        graph.addDependency("moduleC", Arrays.asList("moduleB"));

        // 获取加载顺序
        List<String> loadOrder = graph.getLoadOrder("moduleC");

        // 验证顺序：moduleA -> moduleB -> moduleC
        assertEquals("加载顺序应有 3 个模块", 3, loadOrder.size());
        assertEquals("第 1 个应为 moduleA", "moduleA", loadOrder.get(0));
        assertEquals("第 2 个应为 moduleB", "moduleB", loadOrder.get(1));
        assertEquals("第 3 个应为 moduleC", "moduleC", loadOrder.get(2));
    }

    /**
     * 测试拓扑排序（多个依赖）。
     */
    @Test
    public void testGetLoadOrder_MultipleDependencies() throws CircularDependencyException {
        // 构建依赖关系：
        //   moduleD
        //   /    \
        // moduleB  moduleC
        //   \    /
        //   moduleA
        graph.addDependency("moduleA", Arrays.asList());
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        graph.addDependency("moduleC", Arrays.asList("moduleA"));
        graph.addDependency("moduleD", Arrays.asList("moduleB", "moduleC"));

        // 获取加载顺序
        List<String> loadOrder = graph.getLoadOrder("moduleD");

        // 验证顺序：moduleA 最先，moduleD 最后
        assertEquals("加载顺序应有 4 个模块", 4, loadOrder.size());
        assertEquals("第 1 个应为 moduleA", "moduleA", loadOrder.get(0));
        assertEquals("最后 1 个应为 moduleD", "moduleD", loadOrder.get(3));

        // 验证 moduleB 和 moduleC 在 moduleA 之后、moduleD 之前
        int indexA = loadOrder.indexOf("moduleA");
        int indexB = loadOrder.indexOf("moduleB");
        int indexC = loadOrder.indexOf("moduleC");
        int indexD = loadOrder.indexOf("moduleD");

        assertTrue("moduleB 应在 moduleA 之后", indexB > indexA);
        assertTrue("moduleC 应在 moduleA 之后", indexC > indexA);
        assertTrue("moduleD 应在 moduleB 之后", indexD > indexB);
        assertTrue("moduleD 应在 moduleC 之后", indexD > indexC);
    }

    /**
     * 测试循环依赖检测（直接循环）。
     */
    @Test(expected = CircularDependencyException.class)
    public void testCircularDependency_Direct() throws CircularDependencyException {
        // 构建循环依赖：moduleA -> moduleB -> moduleA
        graph.addDependency("moduleA", Arrays.asList("moduleB"));
        graph.addDependency("moduleB", Arrays.asList("moduleA"));

        // 应抛出 CircularDependencyException
        graph.getLoadOrder("moduleA");
    }

    /**
     * 测试循环依赖检测（间接循环）。
     */
    @Test(expected = CircularDependencyException.class)
    public void testCircularDependency_Indirect() throws CircularDependencyException {
        // 构建循环依赖：moduleA -> moduleB -> moduleC -> moduleA
        graph.addDependency("moduleA", Arrays.asList("moduleC"));
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        graph.addDependency("moduleC", Arrays.asList("moduleB"));

        // 应抛出 CircularDependencyException
        graph.getLoadOrder("moduleA");
    }

    /**
     * 测试无循环依赖。
     */
    @Test
    public void testNoCircularDependency() throws CircularDependencyException {
        // 构建无环依赖：moduleC -> moduleB -> moduleA
        graph.addDependency("moduleA", Arrays.asList());
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        graph.addDependency("moduleC", Arrays.asList("moduleB"));

        // 不应抛出异常
        List<String> loadOrder = graph.getLoadOrder("moduleC");
        assertNotNull("加载顺序不应为 null", loadOrder);
        assertEquals("应有 3 个模块", 3, loadOrder.size());
    }

    /**
     * 测试 hasCircularDependency 方法。
     */
    @Test
    public void testHasCircularDependency() {
        // 无依赖时
        assertFalse("空图应无循环依赖", graph.hasCircularDependency());

        // 无环依赖
        graph.addDependency("moduleA", Arrays.asList());
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        assertFalse("无环依赖应返回 false", graph.hasCircularDependency());

        // 有环依赖
        graph.addDependency("moduleA", Arrays.asList("moduleB")); // 创建循环
        assertTrue("有环依赖应返回 true", graph.hasCircularDependency());
    }

    /**
     * 测试获取所有依赖（递归）。
     */
    @Test
    public void testGetAllDependencies() {
        // 构建依赖关系：moduleD -> moduleC -> moduleB -> moduleA
        graph.addDependency("moduleA", Arrays.asList());
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        graph.addDependency("moduleC", Arrays.asList("moduleB"));
        graph.addDependency("moduleD", Arrays.asList("moduleC"));

        // 获取所有依赖
        Set<String> allDeps = graph.getAllDependencies("moduleD");

        // 验证
        assertEquals("应有 3 个依赖", 3, allDeps.size());
        assertTrue("应包含 moduleA", allDeps.contains("moduleA"));
        assertTrue("应包含 moduleB", allDeps.contains("moduleB"));
        assertTrue("应包含 moduleC", allDeps.contains("moduleC"));
    }

    /**
     * 测试获取反向依赖（谁依赖我）。
     */
    @Test
    public void testGetDependents() {
        // 构建依赖关系
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        graph.addDependency("moduleC", Arrays.asList("moduleA"));

        // 获取依赖 moduleA 的模块
        List<String> dependents = graph.getDependents("moduleA");

        // 验证
        assertEquals("应有 2 个依赖者", 2, dependents.size());
        assertTrue("应包含 moduleB", dependents.contains("moduleB"));
        assertTrue("应包含 moduleC", dependents.contains("moduleC"));
    }

    /**
     * 测试 hasDependency 方法。
     */
    @Test
    public void testHasDependency() {
        // 构建依赖关系：moduleC -> moduleB -> moduleA
        graph.addDependency("moduleA", Arrays.asList());
        graph.addDependency("moduleB", Arrays.asList("moduleA"));
        graph.addDependency("moduleC", Arrays.asList("moduleB"));

        // 验证直接依赖
        assertTrue("moduleB 应依赖 moduleA", graph.hasDependency("moduleB", "moduleA"));

        // 验证间接依赖
        assertTrue("moduleC 应依赖 moduleA（间接）", graph.hasDependency("moduleC", "moduleA"));

        // 验证无依赖
        assertFalse("moduleA 不应依赖 moduleB", graph.hasDependency("moduleA", "moduleB"));
    }

    /**
     * 测试清空依赖关系图。
     */
    @Test
    public void testClear() {
        // 添加依赖
        graph.addDependency("moduleB", Arrays.asList("moduleA"));

        // 验证已添加
        List<String> allModules = graph.getAllModules();
        assertEquals("应有 2 个模块", 2, allModules.size());

        // 清空
        graph.clear();

        // 验证已清空
        allModules = graph.getAllModules();
        assertEquals("清空后应有 0 个模块", 0, allModules.size());
    }

    /**
     * 测试空 moduleId 处理。
     */
    @Test
    public void testEmptyModuleId() {
        // 空 moduleId 不应崩溃
        graph.addDependency("", Arrays.asList("moduleA"));
        graph.removeDependency("");

        // 获取加载顺序（空 moduleId）
        List<String> loadOrder = null;
        try {
            loadOrder = graph.getLoadOrder("");
        } catch (CircularDependencyException e) {
            fail("空 moduleId 不应抛出异常");
        }
        assertNotNull("加载顺序不应为 null", loadOrder);
        assertEquals("加载顺序应为空", 0, loadOrder.size());
    }

    /**
     * 测试 null 参数处理。
     */
    @Test
    public void testNullParameters() {
        // null 参数不应崩溃
        graph.addDependency(null, null);
        graph.removeDependency(null);

        // 获取加载顺序（null moduleId）
        List<String> loadOrder = null;
        try {
            loadOrder = graph.getLoadOrder(null);
        } catch (CircularDependencyException e) {
            fail("null moduleId 不应抛出异常");
        }
        assertNotNull("加载顺序不应为 null", loadOrder);
        assertEquals("加载顺序应为空", 0, loadOrder.size());
    }
}
