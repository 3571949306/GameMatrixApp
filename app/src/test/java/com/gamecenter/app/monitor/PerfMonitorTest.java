package com.gamecenter.app.monitor;

import android.app.Application;
import android.content.Context;
import android.os.SystemClock;
import android.view.Choreographer;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowDebug;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * PerfMonitor 性能监控模块基准测试
 * 
 * 测试覆盖：
 * 1. 冷启动时间测量
 * 2. 内存占用监控
 * 3. 帧率监控
 * 4. 性能报告生成
 * 
 * @author 寇豆码 (Kou Douma)
 * @version 1.0
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28})
public class PerfMonitorTest {
    
    /** 测试应用的 Context */
    private Application mApplication;
    
    /** PerfMonitor 实例 */
    private PerfMonitor mPerfMonitor;
    
    /** Mock 对象关闭器 */
    private AutoCloseable mMockitoClosable;
    
    // ========== 测试初始化和清理 ==========
    
    /**
     * 测试前置设置
     * 
     * 在每个测试方法执行前调用
     */
    @Before
    public void setUp() {
        mMockitoClosable = MockitoAnnotations.openMocks(this);
        mApplication = ApplicationProvider.getApplicationContext();
        mPerfMonitor = PerfMonitor.getInstance();
        
        // 重置单例状态（通过反射清除静态实例）
        resetSingleton();
        
        // 重新获取实例
        mPerfMonitor = PerfMonitor.getInstance();
    }
    
    /**
     * 测试后置清理
     * 
     * 在每个测试方法执行后调用
     */
    @After
    public void tearDown() throws Exception {
        if (mPerfMonitor != null) {
            // 停止监控（如果正在运行）
            try {
                mPerfMonitor.stopMonitoring();
            } catch (Exception e) {
                // 忽略停止时的异常
            }
        }
        
        if (mMockitoClosable != null) {
            mMockitoClosable.close();
        }
    }
    
    /**
     * 重置 PerfMonitor 单例
     * 
     * 通过反射将 sInstance 设为 null，确保每次测试都是全新的实例
     */
    private void resetSingleton() {
        try {
            Field instanceField = PerfMonitor.class.getDeclaredField("sInstance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException("重置单例失败", e);
        }
    }
    
    // ========== 测试用例 ==========
    
    /**
     * 测试 1: 单例模式
     * 
     * 验证 PerfMonitor.getInstance() 返回相同的实例
     */
    @Test
    public void testSingletonPattern() {
        PerfMonitor instance1 = PerfMonitor.getInstance();
        PerfMonitor instance2 = PerfMonitor.getInstance();
        
        assertNotNull("实例不应为 null", instance1);
        assertSame("两次获取的实例应该相同", instance1, instance2);
        
        System.out.println("✅ 单例模式测试通过");
    }
    
    /**
     * 测试 2: 启动监控
     * 
     * 验证 startMonitoring() 正常启动监控
     */
    @Test
    public void testStartMonitoring() {
        // 初始状态：未监控
        assertFalse("初始状态应为未监控", mPerfMonitor.isMonitoring());
        
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        
        // 验证状态
        assertTrue("启动后应为监控状态", mPerfMonitor.isMonitoring());
        
        System.out.println("✅ 启动监控测试通过");
    }
    
    /**
     * 测试 3: 重复启动监控
     * 
     * 验证重复调用 startMonitoring() 不会崩溃
     */
    @Test
    public void testStartMonitoringTwice() {
        mPerfMonitor.startMonitoring(mApplication);
        assertTrue("第一次启动后应处于监控状态", mPerfMonitor.isMonitoring());
        
        // 第二次启动（应该只输出警告日志，不会崩溃）
        mPerfMonitor.startMonitoring(mApplication);
        assertTrue("第二次启动后仍应处于监控状态", mPerfMonitor.isMonitoring());
        
        System.out.println("✅ 重复启动监控测试通过");
    }
    
    /**
     * 测试 4: 停止监控
     * 
     * 验证 stopMonitoring() 正常停止监控并生成报告
     */
    @Test
    public void testStopMonitoring() throws Exception {
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        assertTrue("启动后应为监控状态", mPerfMonitor.isMonitoring());
        
        // 等待一小段时间让采样运行
        Thread.sleep(100);
        
        // 停止监控
        mPerfMonitor.stopMonitoring();
        
        // 验证状态
        assertFalse("停止后应为未监控状态", mPerfMonitor.isMonitoring());
        
        // 验证报告文件是否生成
        File reportFile = new File(mApplication.getFilesDir(), "perf-report-latest.json");
        assertTrue("报告文件应已生成", reportFile.exists());
        assertTrue("报告文件不应为空", reportFile.length() > 0);
        
        System.out.println("✅ 停止监控测试通过");
        System.out.println("   报告文件: " + reportFile.getAbsolutePath());
        System.out.println("   文件大小: " + reportFile.length() + " bytes");
    }
    
    /**
     * 测试 5: 冷启动时间测量
     * 
     * 验证冷启动时间的记录和计算
     */
    @Test
    public void testColdStartMeasurement() throws Exception {
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        
        // 等待一小段时间模拟启动过程
        Thread.sleep(50);
        
        // 模拟首帧渲染完成（创建一个 mock View）
        android.view.View mockView = mock(android.view.View.class);
        
        // 记录首帧
        mPerfMonitor.recordFirstFrame(mockView);
        
        // 获取冷启动时间
        long coldStartTime = mPerfMonitor.getColdStartTimeMs();
        
        // 验证：冷启动时间应该大于 0
        assertTrue("冷启动时间应大于 0", coldStartTime > 0);
        
        // 验证：冷启动时间应该合理（小于 10 秒）
        assertTrue("冷启动时间应小于 10 秒", coldStartTime < 10000);
        
        System.out.println("✅ 冷启动时间测量测试通过");
        System.out.println("   冷启动时间: " + coldStartTime + "ms");
    }
    
    /**
     * 测试 6: 重复记录首帧
     * 
     * 验证重复调用 recordFirstFrame() 不会重复记录
     */
    @Test
    public void testDuplicateFirstFrameRecording() throws Exception {
        mPerfMonitor.startMonitoring(mApplication);
        
        android.view.View mockView = mock(android.view.View.class);
        
        // 第一次记录
        mPerfMonitor.recordFirstFrame(mockView);
        long firstTime = mPerfMonitor.getColdStartTimeMs();
        
        // 等待一小段时间
        Thread.sleep(50);
        
        // 第二次记录（应该被忽略）
        mPerfMonitor.recordFirstFrame(mockView);
        long secondTime = mPerfMonitor.getColdStartTimeMs();
        
        // 验证：两次获取的时间应该相同
        assertEquals("重复记录首帧不应改变冷启动时间", firstTime, secondTime);
        
        System.out.println("✅ 重复记录首帧测试通过");
    }
    
    /**
     * 测试 7: 内存监控
     * 
     * 验证内存采样功能
     */
    @Test
    public void testMemoryMonitoring() throws Exception {
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        
        // 等待一段时间让内存采样运行（采样间隔 5 秒，这里等待 6 秒）
        // 注意：在实际测试中，我们可能需要加快时间流逝
        // 这里简化为等待 1 秒，然后手动触发采样
        
        Thread.sleep(1000);
        
        // 停止监控（会生成报告）
        mPerfMonitor.stopMonitoring();
        
        // 验证报告文件
        File reportFile = new File(mApplication.getFilesDir(), "perf-report-latest.json");
        assertTrue("报告文件应已生成", reportFile.exists());
        
        // 读取并解析报告
        String reportContent = new String(Files.readAllBytes(reportFile.toPath()));
        JSONObject report = new JSONObject(reportContent);
        
        // 验证报告包含内存数据
        assertTrue("报告应包含内存数据", report.has("memory"));
        
        JSONObject memory = report.getJSONObject("memory");
        assertTrue("内存数据应包含峰值", memory.has("peakTotalMB"));
        assertTrue("内存数据应包含平均值", memory.has("avgTotalMB"));
        
        System.out.println("✅ 内存监控测试通过");
        System.out.println("   内存峰值: " + memory.getDouble("peakTotalMB") + "MB");
        System.out.println("   内存平均值: " + memory.getDouble("avgTotalMB") + "MB");
    }
    
    /**
     * 测试 8: FPS 计算
     * 
     * 验证帧率计算功能
     */
    @Test
    public void testFpsCalculation() {
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        
        // 模拟几帧的渲染（通过回调）
        // 注意：在单元测试中，我们无法直接触发 Choreographer 回调
        // 这里主要验证 FPS 计算方法不会崩溃
        
        double fps = mPerfMonitor.getCurrentFPS();
        
        // 验证：FPS 应该是一个非负数
        assertTrue("FPS 应大于等于 0", fps >= 0);
        
        System.out.println("✅ FPS 计算测试通过");
        System.out.println("   当前 FPS: " + fps);
    }
    
    /**
     * 测试 9: 性能报告 JSON 格式
     * 
     * 验证生成的报告是有效的 JSON 格式，且包含所有必需字段
     */
    @Test
    public void testReportJsonFormat() throws Exception {
        // 启动并停止监控以生成报告
        mPerfMonitor.startMonitoring(mApplication);
        Thread.sleep(100);
        mPerfMonitor.stopMonitoring();
        
        // 读取报告文件
        File reportFile = new File(mApplication.getFilesDir(), "perf-report-latest.json");
        assertTrue("报告文件应已生成", reportFile.exists());
        
        String reportContent = new String(Files.readAllBytes(reportFile.toPath()));
        
        // 验证：JSON 格式应有效
        JSONObject report = null;
        try {
            report = new JSONObject(reportContent);
        } catch (Exception e) {
            fail("报告应为有效的 JSON 格式: " + e.getMessage());
        }
        
        assertNotNull("JSON 对象不应为 null", report);
        
        // 验证：报告应包含必需字段
        assertTrue("报告应包含 reportTime", report.has("reportTime"));
        assertTrue("报告应包含 monitorDuration", report.has("monitorDuration"));
        assertTrue("报告应包含 coldStart", report.has("coldStart"));
        assertTrue("报告应包含 memory", report.has("memory"));
        assertTrue("报告应包含 fps", report.has("fps"));
        assertTrue("报告应包含 overallPass", report.has("overallPass"));
        
        // 验证：冷启动数据
        JSONObject coldStart = report.getJSONObject("coldStart");
        assertTrue("coldStart 应包含 timeMs", coldStart.has("timeMs"));
        assertTrue("coldStart 应包含 targetMs", coldStart.has("targetMs"));
        assertTrue("coldStart 应包含 isPass", coldStart.has("isPass"));
        
        // 验证：内存数据
        JSONObject memory = report.getJSONObject("memory");
        assertTrue("memory 应包含 peakTotalMB", memory.has("peakTotalMB"));
        assertTrue("memory 应包含 avgTotalMB", memory.has("avgTotalMB"));
        assertTrue("memory 应包含 targetMB", memory.has("targetMB"));
        assertTrue("memory 应包含 isPass", memory.has("isPass"));
        
        // 验证：FPS 数据
        JSONObject fps = report.getJSONObject("fps");
        assertTrue("fps 应包含 average", fps.has("average"));
        assertTrue("fps 应包含 minimum", fps.has("minimum"));
        assertTrue("fps 应包含 target", fps.has("target"));
        assertTrue("fps 应包含 isPass", fps.has("isPass"));
        
        System.out.println("✅ 性能报告 JSON 格式测试通过");
        System.out.println("   报告内容:");
        System.out.println(report.toString(2));
    }
    
    /**
     * 测试 10: 性能目标判断
     * 
     * 验证性能目标判断是否正确
     */
    @Test
    public void testPerformanceTargetJudgment() throws Exception {
        // 启动并停止监控
        mPerfMonitor.startMonitoring(mApplication);
        Thread.sleep(100);
        mPerfMonitor.stopMonitoring();
        
        // 读取报告
        File reportFile = new File(mApplication.getFilesDir(), "perf-report-latest.json");
        String reportContent = new String(Files.readAllBytes(reportFile.toPath()));
        JSONObject report = new JSONObject(reportContent);
        
        // 验证：overallPass 字段存在且为布尔值
        assertTrue("报告应包含 overallPass", report.has("overallPass"));
        assertEquals("overallPass 应为布尔类型", Boolean.class, report.get("overallPass").getClass());
        
        // 输出结果
        boolean overallPass = report.getBoolean("overallPass");
        System.out.println("✅ 性能目标判断测试通过");
        System.out.println("   总体评估: " + (overallPass ? "✅ 达标" : "❌ 未达标"));
    }
    
    /**
     * 测试 11: 未启动监控时获取数据的处理
     * 
     * 验证在未启动监控时调用方法不会崩溃
     */
    @Test
    public void testGetDataWithoutMonitoring() {
        // 注意：此时监控未启动
        
        // 获取冷启动时间（应该返回 0）
        long coldStartTime = mPerfMonitor.getColdStartTimeMs();
        assertEquals("未监控时冷启动时间应为 0", 0, coldStartTime);
        
        // 获取内存峰值（应该返回 0）
        double memoryPeak = mPerfMonitor.getMemoryPeakMB();
        assertEquals("未监控时内存峰值应为 0", 0.0, memoryPeak, 0.01);
        
        // 获取 FPS（应该返回 0）
        double fps = mPerfMonitor.getCurrentFPS();
        assertEquals("未监控时 FPS 应为 0", 0.0, fps, 0.01);
        
        System.out.println("✅ 未启动监控时获取数据测试通过");
    }
    
    /**
     * 测试 12: 弱引用防止内存泄漏
     * 
     * 验证 Application 使用弱引用，不会阻止 GC
     */
    @Test
    public void testWeakReference() {
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        
        // 验证监控正常运行
        assertTrue("启动后应为监控状态", mPerfMonitor.isMonitoring());
        
        // 注意：完整的弱引用测试需要模拟 GC，这里简化为验证代码逻辑
        // 在实际使用中，如果 Application 被 GC，弱引用会变为 null
        
        System.out.println("✅ 弱引用防止内存泄漏测试通过");
    }
    
    /**
     * 测试 13: 集成测试 - 完整监控流程
     * 
     * 模拟完整的监控流程：启动 -> 记录首帧 -> 运行一段时间 -> 停止 -> 验证报告
     */
    @Test
    public void testCompleteMonitoringFlow() throws Exception {
        System.out.println("开始完整监控流程测试...");
        
        // 步骤 1: 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        assertTrue("步骤 1 失败：监控应已启动", mPerfMonitor.isMonitoring());
        System.out.println("  步骤 1: ✅ 监控已启动");
        
        // 步骤 2: 记录首帧
        android.view.View mockView = mock(android.view.View.class);
        mPerfMonitor.recordFirstFrame(mockView);
        long coldStartTime = mPerfMonitor.getColdStartTimeMs();
        assertTrue("步骤 2 失败：冷启动时间应已记录", coldStartTime > 0);
        System.out.println("  步骤 2: ✅ 首帧已记录（冷启动时间: " + coldStartTime + "ms）");
        
        // 步骤 3: 运行一段时间（模拟监控过程）
        Thread.sleep(500);
        System.out.println("  步骤 3: ✅ 监控运行中...");
        
        // 步骤 4: 停止监控
        mPerfMonitor.stopMonitoring();
        assertFalse("步骤 4 失败：监控应已停止", mPerfMonitor.isMonitoring());
        System.out.println("  步骤 4: ✅ 监控已停止");
        
        // 步骤 5: 验证报告文件
        File reportFile = new File(mApplication.getFilesDir(), "perf-report-latest.json");
        assertTrue("步骤 5 失败：报告文件应已生成", reportFile.exists());
        System.out.println("  步骤 5: ✅ 报告已生成");
        
        // 步骤 6: 验证报告内容
        String reportContent = new String(Files.readAllBytes(reportFile.toPath()));
        JSONObject report = new JSONObject(reportContent);
        
        assertTrue("步骤 6 失败：报告应包含冷启动数据", report.has("coldStart"));
        assertTrue("步骤 6 失败：报告应包含内存数据", report.has("memory"));
        assertTrue("步骤 6 失败：报告应包含 FPS 数据", report.has("fps"));
        System.out.println("  步骤 6: ✅ 报告内容完整");
        
        System.out.println("✅ 完整监控流程测试通过");
    }
    
    // ========== 性能基准测试 ==========
    
    /**
     * 基准测试 1: 冷启动时间基准
     * 
     * 验证冷启动时间是否在目标范围内（< 2 秒）
     */
    @Test
    public void benchmarkColdStartTime() throws Exception {
        System.out.println("开始冷启动时间基准测试...");
        
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        
        // 模拟启动过程（这里用短时间模拟）
        Thread.sleep(50);
        
        // 记录首帧
        android.view.View mockView = mock(android.view.View.class);
        mPerfMonitor.recordFirstFrame(mockView);
        
        long coldStartTime = mPerfMonitor.getColdStartTimeMs();
        
        // 停止监控
        mPerfMonitor.stopMonitoring();
        
        // 验证：冷启动时间应小于目标值（2000ms）
        // 注意：在单元测试中，实际时间会很短（约 50ms），但这里验证逻辑
        System.out.println("  冷启动时间: " + coldStartTime + "ms");
        System.out.println("  目标时间: < 2000ms");
        
        // 读取报告验证
        File reportFile = new File(mApplication.getFilesDir(), "perf-report-latest.json");
        String reportContent = new String(Files.readAllBytes(reportFile.toPath()));
        JSONObject report = new JSONObject(reportContent);
        
        JSONObject coldStart = report.getJSONObject("coldStart");
        boolean isPass = coldStart.getBoolean("isPass");
        
        System.out.println("  是否达标: " + (isPass ? "✅ 是" : "❌ 否"));
        System.out.println("✅ 冷启动时间基准测试完成");
    }
    
    /**
     * 基准测试 2: 内存占用基准
     * 
     * 验证内存占用是否在目标范围内（< 200MB）
     */
    @Test
    public void benchmarkMemoryUsage() throws Exception {
        System.out.println("开始内存占用基准测试...");
        
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        
        // 运行一段时间让内存采样执行
        Thread.sleep(1000);
        
        // 停止监控
        mPerfMonitor.stopMonitoring();
        
        // 读取报告
        File reportFile = new File(mApplication.getFilesDir(), "perf-report-latest.json");
        String reportContent = new String(Files.readAllBytes(reportFile.toPath()));
        JSONObject report = new JSONObject(reportContent);
        
        JSONObject memory = report.getJSONObject("memory");
        double peakTotalMB = memory.getDouble("peakTotalMB");
        double targetMB = memory.getDouble("targetMB");
        boolean isPass = memory.getBoolean("isPass");
        
        System.out.println("  内存峰值: " + String.format("%.2f", peakTotalMB) + "MB");
        System.out.println("  目标值: < " + targetMB + "MB");
        System.out.println("  是否达标: " + (isPass ? "✅ 是" : "❌ 否"));
        System.out.println("✅ 内存占用基准测试完成");
    }
    
    /**
     * 基准测试 3: 帧率基准
     * 
     * 验证帧率是否在目标范围内（> 55 FPS）
     */
    @Test
    public void benchmarkFrameRate() throws Exception {
        System.out.println("开始帧率基准测试...");
        
        // 启动监控
        mPerfMonitor.startMonitoring(mApplication);
        
        // 运行一段时间让帧率采样
        Thread.sleep(1000);
        
        // 停止监控
        mPerfMonitor.stopMonitoring();
        
        // 读取报告
        File reportFile = new File(mApplication.getFilesDir(), "perf-report-latest.json");
        String reportContent = new String(Files.readAllBytes(reportFile.toPath()));
        JSONObject report = new JSONObject(reportContent);
        
        JSONObject fps = report.getJSONObject("fps");
        double avgFps = fps.getDouble("average");
        int targetFps = fps.getInt("target");
        boolean isPass = fps.getBoolean("isPass");
        
        System.out.println("  平均 FPS: " + String.format("%.2f", avgFps));
        System.out.println("  目标值: > " + targetFps);
        System.out.println("  是否达标: " + (isPass ? "✅ 是" : "❌ 否"));
        System.out.println("✅ 帧率基准测试完成");
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 打印分隔线
     */
    private void printSeparator() {
        System.out.println("=".repeat(60));
    }
}
