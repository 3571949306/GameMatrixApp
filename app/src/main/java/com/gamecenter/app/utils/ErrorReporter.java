package com.gamecenter.app.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.network.OkHttpClientProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Singleton;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 错误上报器 —— 负责将应用运行时异常和日志信息上报至远程服务器。
 *
 * <p>简单来说，这个类就像应用的"黑匣子"——当应用出了问题（比如崩溃、报错），
 * 它会自动把错误信息收集起来，尝试通过网络发送给开发者，让开发者知道出了什么问题。
 * 如果网络不通，它也会把错误信息保存到手机本地文件里，等以后有机会再发送。</p>
 *
 * <p>核心职责：
 * <ul>
 *   <li>捕获并序列化异常信息（含堆栈跟踪、设备信息、线程名等），以 JSON 格式发送到服务端</ul>
 *   <li>支持普通运行时日志（非异常）的上报</li>
 *   <li>内置每小时频率限制（{@link #MAX_ERRORS_PER_HOUR}），防止异常风暴导致大量网络请求</li>
 *   <li>网络发送失败时自动降级为本地文件存储，确保错误信息不丢失</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>采用单例 + 双重检查锁（DCL）模式，保证全局唯一实例且线程安全</li>
 *   <li>使用单线程守护线程池（{@link ExecutorService}），所有上报操作异步执行，不阻塞主线程</li>
 *   <li>频率限制使用 {@link AtomicInteger} 实现无锁计数，避免多线程竞争</li>
 *   <li>标记为 {@link Singleton} 供 Dagger 依赖注入使用</li>
 * </ul>
 */
@Singleton
public final class ErrorReporter {

    private static final String TAG = "ErrorReporter";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    /** 每小时允许上报的最大错误数量，超过此限制后丢弃后续错误。
     *  就像手机流量套餐有上限一样，防止错误太多时疯狂发请求，把服务器"轰炸"了 */
    private static final int MAX_ERRORS_PER_HOUR = 10;

    /** 一小时的毫秒数，用于频率限制的时间窗口计算 */
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;

    /** 单例实例，使用 volatile 保证多线程下的可见性。
     *  volatile 就像一个"公告板"——当一个线程修改了它的值，其他线程立刻就能看到最新值，
     *  不会读到过期的"缓存"数据。如果没有 volatile，某个线程可能还在看旧值，导致创建多个实例。*/
    private static volatile ErrorReporter instance;

    /** 应用上下文（使用 ApplicationContext 避免Activity泄漏）。
     *  ApplicationContext 是整个应用的上下文，和具体的 Activity 无关，
     *  这样即使 Activity 被销毁了，ErrorReporter 也不会一直引用着它导致内存无法回收 */
    private final Context context;

    /** 单线程执行器，所有上报任务串行执行，避免并发问题。
     *  可以理解为一个只有一个窗口的邮局——所有寄信请求排成一队，一个一个处理，
     *  这样就不会出现两个人同时抢着用同一个窗口的混乱情况 */
    private final ExecutorService executor;

    /** 当前小时窗口内已上报的错误计数，使用原子操作保证线程安全。
     *  AtomicInteger 就像一个带锁的计数器，多个线程同时按它时不会数错 */
    private final AtomicInteger hourlyCount;

    /** 当前小时窗口的起始时间戳（毫秒） */
    private volatile long hourStartMs;

    /** 远程反馈服务器的 URL 地址 */
    private volatile String feedbackUrl;

    /**
     * 构造错误上报器。
     *
     * @param context 应用上下文，内部会调用 {@code getApplicationContext()} 避免内存泄漏
     */
    public ErrorReporter(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ErrorReporter");
            // 守护线程（Daemon）：就像后台默默工作的"清洁工"，当所有"正式员工"（用户线程）都下班了，
            // 守护线程也会自动结束，不会阻止程序退出
            t.setDaemon(true);
            return t;
        });
        this.hourlyCount = new AtomicInteger(0);
        this.hourStartMs = System.currentTimeMillis();
        this.feedbackUrl = BuildConfig.FEEDBACK_URL;
    }

    /**
     * 获取错误上报器的单例实例（双重检查锁模式）。
     *
     * <p>"单例"就是整个应用只创建一个对象，就像一个学校只有一个校长。
     * "双重检查锁"是一种既保证线程安全又不过度影响性能的技巧——
     * 先快速检查一次（不加锁，速度快），如果已经创建就直接返回；
     * 如果没有，再加锁仔细检查一次，避免多线程同时创建多个实例。</p>
     *
     * @param context 应用上下文
     * @return 全局唯一的 ErrorReporter 实例
     */
    public static ErrorReporter getInstance(Context context) {
        if (instance == null) {
            synchronized (ErrorReporter.class) {
                if (instance == null) {
                    instance = new ErrorReporter(context);
                }
            }
        }
        return instance;
    }

    /**
     * 上报异常（无附加信息）。
     *
     * @param throwable 需要上报的异常对象，为 null 时直接返回
     */
    public void report(Throwable throwable) {
        report(throwable, null);
    }

    /**
     * 上报异常，可携带附加描述信息。
     *
     * <p>处理流程：
     * <ol>
     *   <li>空值校验 —— throwable 为 null 时静默返回</li>
     *   <li>频率限制检查 —— 超过每小时上限则丢弃</li>
     *   <li>异步构建 JSON 负载并发送到服务器</li>
     *   <li>发送失败时降级为本地文件存储</li>
     * </ol>
     *
     * @param throwable 需要上报的异常对象，为 null 时直接返回
     * @param extraInfo  附加信息（如发生场景描述），可为 null
     */
    public void report(Throwable throwable, String extraInfo) {
        if (throwable == null) return;
        if (!checkRateLimit()) {
            Log.d(TAG, "Rate limit reached, skipping error report");
            return;
        }
        executor.execute(() -> {
            try {
                String payload = buildPayload(throwable, extraInfo);
                sendToServer(payload);
            } catch (Exception e) {
                Log.e(TAG, "Failed to report error", e);
                // 网络发送失败，降级为本地文件存储
                saveToLocal(throwable, extraInfo);
            }
        });
    }

    /**
     * 上报普通运行时日志消息（非异常）。
     *
     * <p>与 {@link #report(Throwable)} 不同，此方法用于记录运行时级别的日志信息，
     * 如警告、关键业务事件等，不包含堆栈跟踪。
     *
     * @param level   日志级别（如 "WARN"、"INFO"、"ERROR"）
     * @param message 日志内容，为 null 时直接返回
     */
    public void reportMessage(String level, String message) {
        if (message == null) return;
        if (!checkRateLimit()) return;
        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("type", "runtime_log");
                payload.put("level", level);
                payload.put("message", message);
                payload.put("appVersion", BuildConfig.VERSION_NAME);
                payload.put("versionCode", BuildConfig.VERSION_CODE);
                payload.put("device", Build.MODEL);
                payload.put("androidVersion", Build.VERSION.SDK_INT);
                payload.put("timestamp", System.currentTimeMillis());
                sendToServer(payload.toString());
            } catch (Exception e) {
                Log.e(TAG, "Failed to report message", e);
            }
        });
    }

    /**
     * 频率限制检查 —— 滑动窗口计数器。
     *
     * <p>逻辑说明：
     * <ol>
     *   <li>如果当前时间已超过一小时窗口，则重置计数器和窗口起始时间</li>
     *   <li>原子递增计数器，若超过 {@link #MAX_ERRORS_PER_HOUR} 则拒绝上报</li>
     * </ol>
     *
     * @return true 表示允许上报，false 表示已达到频率上限
     */
    private boolean checkRateLimit() {
        long now = System.currentTimeMillis();
        // 超过一小时窗口，重置计数
        if (now - hourStartMs > ONE_HOUR_MS) {
            hourStartMs = now;
            hourlyCount.set(0);
        }
        return hourlyCount.incrementAndGet() <= MAX_ERRORS_PER_HOUR;
    }

    /**
     * 将异常信息构建为 JSON 格式的上报负载。
     *
     * <p>负载包含：异常消息、类名、完整堆栈跟踪、附加信息、
     * 应用版本、设备型号、制造商、Android版本、时间戳、当前线程名。
     *
     * @param throwable 异常对象
     * @param extraInfo 附加信息，可为 null
     * @return JSON 字符串格式的上报负载；构建失败时返回兜底的错误 JSON
     */
    private String buildPayload(Throwable throwable, String extraInfo) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", "error_report");
            payload.put("message", throwable.getMessage());
            payload.put("className", throwable.getClass().getName());

            // 将完整堆栈跟踪转为字符串
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            payload.put("stackTrace", sw.toString());

            if (extraInfo != null) {
                payload.put("extraInfo", extraInfo);
            }

            payload.put("appVersion", BuildConfig.VERSION_NAME);
            payload.put("versionCode", BuildConfig.VERSION_CODE);
            payload.put("device", Build.MODEL);
            payload.put("manufacturer", Build.MANUFACTURER);
            payload.put("androidVersion", Build.VERSION.SDK_INT);
            payload.put("timestamp", System.currentTimeMillis());

            // 记录当前线程名，便于排查线程相关问题
            JSONArray threadInfo = new JSONArray();
            threadInfo.put(Thread.currentThread().getName());
            payload.put("thread", threadInfo);

            return payload.toString();
        } catch (Exception e) {
            // JSON 构建失败时返回兜底信息，确保至少有基本的错误类型标识
            return "{\"type\":\"error_report\",\"message\":\"build_payload_failed\"}";
        }
    }

    /**
     * 将 JSON 负载通过 HTTP POST 发送到远程反馈服务器。
     *
     * <p>URL 处理逻辑：如果配置的 feedbackUrl 不以 "/error" 结尾，
     * 则自动追加该路径后缀，并去除末尾多余的斜杠。
     *
     * @param payload JSON 格式的上报数据
     */
    private void sendToServer(String payload) {
        // 分发架构 v2：反馈入口跟随分发边缘（与下载源同一组主机），按序级联；
        // 全部失败仍回退旧 FEEDBACK_URL（若配置）。
        List<String> candidates = new ArrayList<>();
        if (BuildConfig.DL_MIRROR_BASES != null && !BuildConfig.DL_MIRROR_BASES.isEmpty()) {
            for (String host : BuildConfig.DL_MIRROR_BASES.split(",")) {
                String h = host.trim();
                if (!h.isEmpty()) candidates.add(h + "/api/feedback/error");
            }
        }
        if (feedbackUrl != null && !feedbackUrl.isEmpty()) {
            String legacy = feedbackUrl;
            candidates.add(legacy.endsWith("/error") ? legacy : legacy.replaceAll("/+$", "") + "/error");
        }
        if (candidates.isEmpty()) {
            Log.d(TAG, "No feedback URL configured");
            return;
        }
        OkHttpClient client = OkHttpClientProvider.getInstance(context).getHttpClient();
        RequestBody body = RequestBody.create(payload, JSON_TYPE);
        for (String url : candidates) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .build();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Error report sent successfully to " + url);
                        return;
                    }
                    Log.w(TAG, "Error report failed on " + url + ": " + response.code());
                }
            } catch (Exception e) {
                Log.w(TAG, "Feedback endpoint unreachable: " + url + " (" + e.getMessage() + ")");
            }
        }
        Log.e(TAG, "All feedback endpoints failed");
    }

    /**
     * 将异常信息保存到本地文件（网络发送失败时的降级方案）。
     *
     * <p>文件存储在应用私有目录的 "error_reports" 子目录下，
     * 文件名格式为 "error_{yyyyMMdd_HHmmss}.log"。
     * 文件内容包含：时间、应用版本、设备型号、Android版本、附加信息、异常类名和堆栈。
     *
     * @param throwable 异常对象
     * @param extraInfo 附加信息，可为 null
     */
    private void saveToLocal(Throwable throwable, String extraInfo) {
        try {
            File dir = new File(context.getFilesDir(), "error_reports");
            if (!dir.exists()) dir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File file = new File(dir, "error_" + timestamp + ".log");
            try (FileWriter fw = new FileWriter(file)) {
                fw.write("Time: ");
                fw.write(timestamp);
                fw.write("\n");
                fw.write("App: ");
                fw.write(BuildConfig.VERSION_NAME);
                fw.write(" (");
                fw.write(String.valueOf(BuildConfig.VERSION_CODE));
                fw.write(")\n");
                fw.write("Device: ");
                fw.write(Build.MODEL);
                fw.write("\n");
                fw.write("Android: ");
                fw.write(String.valueOf(Build.VERSION.SDK_INT));
                fw.write("\n");
                if (extraInfo != null) {
                    fw.write("Extra: ");
                    fw.write(extraInfo);
                    fw.write("\n");
                }
                fw.write("Error: ");
                fw.write(throwable.getClass().getName());
                fw.write(": ");
                fw.write(throwable.getMessage());
                fw.write("\n");
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                fw.write(sw.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save error locally", e);
        }
    }
}
