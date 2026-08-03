package com.gamecenter.app.monitor;

import android.app.Application;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewTreeObserver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 性能监控模块
 * 
 * 功能：
 * 1. 冷启动时间测量
 * 2. 内存占用监控（Java Heap + Native Heap）
 * 3. 帧率监控（FPS）
 * 4. 性能报告生成（JSON格式）
 * 
 * 使用单例模式，通过 PerfMonitor.getInstance() 获取实例
 * 
 * @author Kou Douma (寇豆码)
 * @version 1.0
 */
public class PerfMonitor {
    
    // ========== 常量定义 ==========
    
    /** 日志标签 */
    private static final String TAG = "PerfMonitor";
    
    /** 内存采样间隔（毫秒） */
    private static final long MEMORY_SAMPLE_INTERVAL = 5000L;
    
    /** 冷启动时间目标（毫秒） */
    private static final long COLD_START_TARGET = 2000L;
    
    /** 内存占用目标（MB） */
    private static final long MEMORY_TARGET_MB = 200L;
    
    /** FPS 目标 */
    private static final int FPS_TARGET = 55;
    
    /** 性能报告保存路径 */
    private static final String REPORT_FILE_NAME = "perf-report-latest.json";
    
    // ========== 单例实例 ==========
    
    /** 单例实例 */
    private static volatile PerfMonitor sInstance;
    
    // ========== 上下文引用 ==========
    
    /** Application 上下文（弱引用，避免内存泄漏） */
    private WeakReference<Application> mApplicationRef;
    
    /** 主线程 Handler */
    private Handler mMainHandler;
    
    /** 后台采样线程 */
    private HandlerThread mSamplingThread;
    private Handler mSamplingHandler;
    
    // ========== 冷启动时间相关 ==========
    
    /** 启动开始时间（纳秒） */
    private long mStartTime = 0L;
    
    /** 首帧渲染完成时间（纳秒） */
    private long mFirstFrameTime = 0L;
    
    /** 冷启动时间（毫秒） */
    private long mColdStartTimeMs = 0L;
    
    /** 是否已记录首帧 */
    private boolean mIsFirstFrameRecorded = false;
    
    // ========== 内存监控相关 ==========
    
    /** 内存采样数据列表 */
    private List<MemorySample> mMemorySamples;
    
    /** Java Heap 峰值（字节） */
    private long mJavaHeapPeak = 0L;
    
    /** Native Heap 峰值（字节） */
    private long mNativeHeapPeak = 0L;
    

    /** 内存采样是否运行中 */
    private boolean mIsMemorySamplingRunning = false;
    
    // ========== 帧率监控相关 ==========
    
    /** Choreographer 实例 */
    private Choreographer mChoreographer;
    
    /** 帧回调 */
    private Choreographer.FrameCallback mFrameCallback;
    
    /** 帧时间列表（用于计算 FPS） */
    private List<Long> mFrameTimes;
    
    /** 是否正在监控帧率 */
    private boolean mIsFrameMonitoring = false;
    
    /** 总帧数 */
    private int mTotalFrames = 0;
    
    /** FPS 采样开始时间 */
    private long mFpsSamplingStartTime = 0L;
    
    // ========== 监控状态 ==========
    
    /** 是否正在监控 */
    private boolean mIsMonitoring = false;
    
    // ========== 数据模型 ==========
    
    /**
     * 内存采样数据模型
     */
    private static class MemorySample {
        /** 采样时间 */
        long timestamp;
        /** Java Heap 已使用（字节） */
        long javaHeapUsed;
        /** Native Heap 已分配（字节） */
        long nativeHeapAllocated;
        /** 总内存（字节） */
        long totalMemory;
        
        MemorySample(long timestamp, long javaHeapUsed, long nativeHeapAllocated) {
            this.timestamp = timestamp;
            this.javaHeapUsed = javaHeapUsed;
            this.nativeHeapAllocated = nativeHeapAllocated;
            this.totalMemory = javaHeapUsed + nativeHeapAllocated;
        }
    }
    
    // ========== 单例模式实现 ==========
    
    /**
     * 获取 PerfMonitor 单例实例
     * 
     * @return PerfMonitor 实例
     */
    public static PerfMonitor getInstance() {
        if (sInstance == null) {
            synchronized (PerfMonitor.class) {
                if (sInstance == null) {
                    sInstance = new PerfMonitor();
                }
            }
        }
        return sInstance;
    }
    
    /**
     * 私有构造函数
     */
    private PerfMonitor() {
        mMainHandler = new Handler(Looper.getMainLooper());
        mMemorySamples = new CopyOnWriteArrayList<>();
        mFrameTimes = new ArrayList<>();
        
        // 初始化 Choreographer（需要在主线程调用）
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mChoreographer = Choreographer.getInstance();
            }
        });
        
        Log.i(TAG, "PerfMonitor 初始化完成");
    }
    
    // ========== 公共接口方法 ==========
    
    /**
     * 开始性能监控
     * 
     * 此方法应在 Application.onCreate() 中调用
     * 
     * @param application Application 实例
     */
    public void startMonitoring(Application application) {
        if (mIsMonitoring) {
            Log.w(TAG, "监控已经在运行中");
            return;
        }
        
        mApplicationRef = new WeakReference<>(application);
        mIsMonitoring = true;
        
        // 记录启动开始时间
        mStartTime = System.nanoTime();
        
        // 启动内存采样
        startMemorySampling();
        
        // 启动帧率监控
        startFrameMonitoring();
        
        Log.i(TAG, "性能监控已启动");
    }
    
    /**
     * 停止性能监控
     * 
     * 此方法应在适当的时机调用（如应用退出时）
     */
    public void stopMonitoring() {
        if (!mIsMonitoring) {
            Log.w(TAG, "监控未运行");
            return;
        }
        
        mIsMonitoring = false;
        
        // 停止内存采样
        stopMemorySampling();
        
        // 停止帧率监控
        stopFrameMonitoring();
        
        // 生成性能报告
        generateReport();
        
        Log.i(TAG, "性能监控已停止");
    }
    
    /**
     * 记录首帧渲染完成时间
     * 
     * 此方法应在首帧渲染完成后调用（如在 Activity 的 onWindowFocusChanged 中）
     * 或者通过 ViewTreeObserver 监听
     * 
     * @param view 已完成首帧渲染的 View
     */
    public void recordFirstFrame(View view) {
        if (mIsFirstFrameRecorded) {
            return;
        }
        
        mFirstFrameTime = System.nanoTime();
        mColdStartTimeMs = (mFirstFrameTime - mStartTime) / 1_000_000; // 转换为毫秒
        mIsFirstFrameRecorded = true;
        
        Log.i(TAG, "冷启动时间: " + mColdStartTimeMs + "ms (目标: <" + COLD_START_TARGET + "ms)");
        
        // 判断是否达标
        if (mColdStartTimeMs > COLD_START_TARGET) {
            Log.w(TAG, "⚠️ 冷启动时间未达标！");
        } else {
            Log.i(TAG, "✅ 冷启动时间达标");
        }
    }
    
    /**
     * 通过 ViewTreeObserver 自动记录首帧
     * 
     * @param view 需要监听的 View（通常是根 View）
     */
    public void setupFirstFrameDetection(View view) {
        if (view == null) {
            Log.e(TAG, "View 不能为 null");
            return;
        }
        
        view.getViewTreeObserver().addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // 首次布局完成时记录首帧
                    if (!mIsFirstFrameRecorded) {
                        recordFirstFrame(view);
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            }
        );
    }
    
    // ========== 内存监控实现 ==========
    
    /**
     * 启动内存采样
     */
    private void startMemorySampling() {
        if (mIsMemorySamplingRunning) {
            return;
        }
        
        mIsMemorySamplingRunning = true;
        
        // 创建后台采样线程
        mSamplingThread = new HandlerThread("PerfMonitor-MemorySampling");
        mSamplingThread.start();
        mSamplingHandler = new Handler(mSamplingThread.getLooper());
        
        // 开始采样
        mSamplingHandler.post(mMemorySamplingRunnable);
        
        Log.d(TAG, "内存采样已启动，间隔: " + MEMORY_SAMPLE_INTERVAL + "ms");
    }
    
    /**
     * 停止内存采样
     */
    private void stopMemorySampling() {
        if (!mIsMemorySamplingRunning) {
            return;
        }
        
        mIsMemorySamplingRunning = false;
        
        if (mSamplingHandler != null) {
            mSamplingHandler.removeCallbacks(mMemorySamplingRunnable);
        }
        
        if (mSamplingThread != null) {
            mSamplingThread.quitSafely();
            mSamplingThread = null;
        }
        
        Log.d(TAG, "内存采样已停止");
    }
    
    /**
     * 内存采样 Runnable
     */
    private Runnable mMemorySamplingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsMemorySamplingRunning) {
                return;
            }
            
            // 执行内存采样
            sampleMemory();
            
            // 安排下一次采样
            if (mSamplingHandler != null) {
                mSamplingHandler.postDelayed(this, MEMORY_SAMPLE_INTERVAL);
            }
        }
    };
    
    /**
     * 执行一次内存采样
     */
    private void sampleMemory() {
        Runtime runtime = Runtime.getRuntime();
        
        // Java Heap 使用量
        long javaHeapUsed = runtime.totalMemory() - runtime.freeMemory();
        
        // Native Heap 分配量（API Level 1+）
        long nativeHeapAllocated = Debug.getNativeHeapAllocatedSize();
        
        // 记录峰值
        if (javaHeapUsed > mJavaHeapPeak) {
            mJavaHeapPeak = javaHeapUsed;
        }
        if (nativeHeapAllocated > mNativeHeapPeak) {
            mNativeHeapPeak = nativeHeapAllocated;
        }
        
        // 保存采样数据
        MemorySample sample = new MemorySample(
            System.currentTimeMillis(),
            javaHeapUsed,
            nativeHeapAllocated
        );
        mMemorySamples.add(sample);
        
        // 日志记录（每 3 次采样记录一次，避免日志过多）
        if (mMemorySamples.size() % 3 == 0) {
            Log.d(TAG, String.format(Locale.US,
                "内存采样 #%d: Java Heap=%.2fMB, Native Heap=%.2fMB, 总计=%.2fMB",
                mMemorySamples.size(),
                javaHeapUsed / (1024.0 * 1024.0),
                nativeHeapAllocated / (1024.0 * 1024.0),
                (javaHeapUsed + nativeHeapAllocated) / (1024.0 * 1024.0)
            ));
        }
    }
    
    // ========== 帧率监控实现 ==========
    
    /**
     * 启动帧率监控
     */
    private void startFrameMonitoring() {
        if (mIsFrameMonitoring) {
            return;
        }
        
        mIsFrameMonitoring = true;
        mFpsSamplingStartTime = System.currentTimeMillis();
        
        // 在主线程设置 Choreographer 回调
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mFrameCallback = new Choreographer.FrameCallback() {
                    @Override
                    public void doFrame(long frameTimeNanos) {
                        if (!mIsFrameMonitoring) {
                            return;
                        }
                        
                        // 记录帧时间
                        mFrameTimes.add(frameTimeNanos);
                        mTotalFrames++;
                        
                        // 保持列表大小合理（最多保存最近 300 帧，约 5 秒 @ 60fps）
                        if (mFrameTimes.size() > 300) {
                            mFrameTimes.remove(0);
                        }
                        
                        // 继续监听下一帧
                        if (mChoreographer != null && mIsFrameMonitoring) {
                            mChoreographer.postFrameCallback(this);
                        }
                    }
                };
                
                if (mChoreographer != null) {
                    mChoreographer.postFrameCallback(mFrameCallback);
                    Log.d(TAG, "帧率监控已启动");
                } else {
                    Log.e(TAG, "Choreographer 未初始化");
                }
            }
        });
    }
    
    /**
     * 停止帧率监控
     */
    private void stopFrameMonitoring() {
        if (!mIsFrameMonitoring) {
            return;
        }
        
        mIsFrameMonitoring = false;
        
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mChoreographer != null && mFrameCallback != null) {
                    mChoreographer.removeFrameCallback(mFrameCallback);
                }
            }
        });
        
        Log.d(TAG, "帧率监控已停止");
    }
    
    /**
     * 计算当前 FPS
     * 
     * @return FPS 值，如果无法计算则返回 0
     */
    private double calculateFPS() {
        if (mFrameTimes.size() < 2) {
            return 0.0;
        }
        
        // 取最近 60 帧计算（约 1 秒 @ 60fps）
        int sampleSize = Math.min(mFrameTimes.size(), 60);
        List<Long> recentFrames = mFrameTimes.subList(
            mFrameTimes.size() - sampleSize,
            mFrameTimes.size()
        );
        
        // 计算平均帧间隔（纳秒）
        double totalInterval = 0;
        for (int i = 1; i < recentFrames.size(); i++) {
            totalInterval += (recentFrames.get(i) - recentFrames.get(i - 1));
        }
        double avgIntervalNs = totalInterval / (recentFrames.size() - 1);
        
        // FPS = 1_000_000_000 / 平均帧间隔（纳秒）
        double fps = 1_000_000_000.0 / avgIntervalNs;
        
        return fps;
    }
    
    /**
     * 获取 FPS 统计信息
     * 
     * @return FPS 统计对象
     */
    private FpsStats calculateFpsStats() {
        if (mFrameTimes.size() < 2) {
            return new FpsStats(0, 0, 0);
        }
        
        List<Double> fpsValues = new ArrayList<>();
        
        // 计算每 10 帧的平均 FPS
        for (int i = 10; i < mFrameTimes.size(); i += 10) {
            List<Long> window = mFrameTimes.subList(i - 10, i);
            double totalInterval = 0;
            for (int j = 1; j < window.size(); j++) {
                totalInterval += (window.get(j) - window.get(j - 1));
            }
            double avgIntervalNs = totalInterval / (window.size() - 1);
            double fps = 1_000_000_000.0 / avgIntervalNs;
            fpsValues.add(fps);
        }
        
        if (fpsValues.isEmpty()) {
            return new FpsStats(0, 0, 0);
        }
        
        // 计算平均值和最小值
        double sum = 0;
        double min = Double.MAX_VALUE;
        for (double fps : fpsValues) {
            sum += fps;
            if (fps < min) {
                min = fps;
            }
        }
        
        double avg = sum / fpsValues.size();
        
        return new FpsStats(avg, min, fpsValues.size());
    }
    
    /**
     * FPS 统计数据结构
     */
    private static class FpsStats {
        double average;
        double minimum;
        int sampleCount;
        
        FpsStats(double average, double minimum, int sampleCount) {
            this.average = average;
            this.minimum = minimum;
            this.sampleCount = sampleCount;
        }
    }
    
    // ========== 报告生成 ==========
    
    /**
     * 生成性能报告
     * 
     * @return 报告 JSON 字符串
     */
    private String generateReport() {
        try {
            JSONObject report = new JSONObject();
            
            // 报告元数据
            report.put("reportTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            report.put("monitorDuration", System.currentTimeMillis() - mFpsSamplingStartTime);
            
            // 1. 冷启动时间
            JSONObject coldStart = new JSONObject();
            coldStart.put("timeMs", mColdStartTimeMs);
            coldStart.put("targetMs", COLD_START_TARGET);
            coldStart.put("isPass", mColdStartTimeMs < COLD_START_TARGET);
            report.put("coldStart", coldStart);
            
            // 2. 内存占用
            FpsStats fpsStats = calculateFpsStats();
            JSONObject memory = new JSONObject();
            double javaHeapPeakMB = mJavaHeapPeak / (1024.0 * 1024.0);
            double nativeHeapPeakMB = mNativeHeapPeak / (1024.0 * 1024.0);
            double totalPeakMB = (mJavaHeapPeak + mNativeHeapPeak) / (1024.0 * 1024.0);
            
            // 计算平均值
            double totalJavaHeap = 0;
            double totalNativeHeap = 0;
            for (MemorySample sample : mMemorySamples) {
                totalJavaHeap += sample.javaHeapUsed;
                totalNativeHeap += sample.nativeHeapAllocated;
            }
            double avgJavaHeapMB = mMemorySamples.isEmpty() ? 0 : 
                (totalJavaHeap / mMemorySamples.size()) / (1024.0 * 1024.0);
            double avgNativeHeapMB = mMemorySamples.isEmpty() ? 0 : 
                (totalNativeHeap / mMemorySamples.size()) / (1024.0 * 1024.0);
            double avgTotalMB = avgJavaHeapMB + avgNativeHeapMB;
            
            memory.put("peakJavaHeapMB", javaHeapPeakMB);
            memory.put("peakNativeHeapMB", nativeHeapPeakMB);
            memory.put("peakTotalMB", totalPeakMB);
            memory.put("avgJavaHeapMB", avgJavaHeapMB);
            memory.put("avgNativeHeapMB", avgNativeHeapMB);
            memory.put("avgTotalMB", avgTotalMB);
            memory.put("targetMB", MEMORY_TARGET_MB);
            memory.put("isPass", totalPeakMB < MEMORY_TARGET_MB);
            memory.put("sampleCount", mMemorySamples.size());
            
            // 内存采样详情
            JSONArray samples = new JSONArray();
            for (MemorySample sample : mMemorySamples) {
                JSONObject sampleObj = new JSONObject();
                sampleObj.put("timestamp", sample.timestamp);
                sampleObj.put("javaHeapMB", sample.javaHeapUsed / (1024.0 * 1024.0));
                sampleObj.put("nativeHeapMB", sample.nativeHeapAllocated / (1024.0 * 1024.0));
                sampleObj.put("totalMB", sample.totalMemory / (1024.0 * 1024.0));
                samples.put(sampleObj);
            }
            memory.put("samples", samples);
            report.put("memory", memory);
            
            // 3. 帧率
            JSONObject fps = new JSONObject();
            fps.put("average", fpsStats.average);
            fps.put("minimum", fpsStats.minimum);
            fps.put("target", FPS_TARGET);
            fps.put("isPass", fpsStats.average > FPS_TARGET);
            fps.put("totalFrames", mTotalFrames);
            fps.put("sampleCount", fpsStats.sampleCount);
            report.put("fps", fps);
            
            // 4. 总体评估
            boolean allPass = coldStart.getBoolean("isPass") && 
                           memory.getBoolean("isPass") && 
                           fps.getBoolean("isPass");
            report.put("overallPass", allPass);
            
            // 保存报告到文件
            saveReportToFile(report);
            
            // 日志输出报告摘要
            logReportSummary(report);
            
            return report.toString();
            
        } catch (JSONException e) {
            Log.e(TAG, "生成报告失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 保存报告到文件
     * 
     * @param report JSON 报告对象
     */
    private void saveReportToFile(JSONObject report) {
        if (mApplicationRef == null || mApplicationRef.get() == null) {
            Log.e(TAG, "Application 上下文不可用，无法保存报告");
            return;
        }
        
        Context context = mApplicationRef.get();
        File file = new File(context.getFilesDir(), REPORT_FILE_NAME);
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(report.toString());
            Log.i(TAG, "性能报告已保存: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "保存报告失败: " + e.getMessage());
        }
    }
    
    /**
     * 日志输出报告摘要
     * 
     * @param report JSON 报告对象
     */
    private void logReportSummary(JSONObject report) {
        try {
            Log.i(TAG, "========== 性能报告摘要 ==========");
            
            // 冷启动
            JSONObject coldStart = report.getJSONObject("coldStart");
            Log.i(TAG, String.format(Locale.US,
                "冷启动时间: %dms %s",
                coldStart.getLong("timeMs"),
                coldStart.getBoolean("isPass") ? "✅" : "❌"
            ));
            
            // 内存
            JSONObject memory = report.getJSONObject("memory");
            Log.i(TAG, String.format(Locale.US,
                "内存峰值: %.2fMB %s (目标: <%.0fMB)",
                memory.getDouble("peakTotalMB"),
                memory.getBoolean("isPass") ? "✅" : "❌",
                memory.getDouble("targetMB")
            ));
            
            // FPS
            JSONObject fps = report.getJSONObject("fps");
            Log.i(TAG, String.format(Locale.US,
                "FPS 平均: %.2f %s (目标: >%d)",
                fps.getDouble("average"),
                fps.getBoolean("isPass") ? "✅" : "❌",
                fps.getInt("target")
            ));
            
            // 总体
            Log.i(TAG, "总体评估: " + (report.getBoolean("overallPass") ? "✅ 达标" : "❌ 未达标"));
            Log.i(TAG, "===================================");
            
        } catch (JSONException e) {
            Log.e(TAG, "解析报告失败: " + e.getMessage());
        }
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 获取冷启动时间（毫秒）
     * 
     * @return 冷启动时间，如果未记录则返回 0
     */
    public long getColdStartTimeMs() {
        return mColdStartTimeMs;
    }
    
    /**
     * 获取内存峰值（MB）
     * 
     * @return 内存峰值
     */
    public double getMemoryPeakMB() {
        return (mJavaHeapPeak + mNativeHeapPeak) / (1024.0 * 1024.0);
    }
    
    /**
     * 获取当前 FPS
     * 
     * @return 当前 FPS 值
     */
    public double getCurrentFPS() {
        return calculateFPS();
    }
    
    /**
     * 检查是否正在监控
     * 
     * @return true 如果正在监控
     */
    public boolean isMonitoring() {
        return mIsMonitoring;
    }
}
