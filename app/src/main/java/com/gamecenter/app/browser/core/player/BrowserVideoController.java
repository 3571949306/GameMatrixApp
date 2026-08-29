package com.gamecenter.app.browser.core.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 页面视频接管控制器（浏览器"内置播放器"的内核）。
 *
 * <p>职责：
 * <ul>
 *   <li>周期性探测页面 video 元素，判断"这个网页有可播放的视频"；</li>
 *   <li>锁定目标 video 并把播放控制（播放/暂停/跳转/倍速/音量）以 DOM 调用下发；</li>
 *   <li>维护"接管态"：把 video 提到最前铺满，让网页自带播放器被原生 UI 取代；</li>
 *   <li>维护长按快进的倍速状态机（按下→提速，松手→恢复用户倍速）。</li>
 * </ul>
 *
 * <p><b>生命周期契约</b>：切 Tab / 重建 WebView 必须 {@link #bind(WebView)}，
 * 销毁前必须 {@link #destroy()}。所有异步回调带代次校验，
 * 旧 WebView 的探测结果不会写到新 Tab 的 UI 上（与建议框的代次机制同一套路）。
 */
public class BrowserVideoController {

    private static final String TAG = "BrowserVideoController";

    /** 未接管时的探测间隔（仅在找视频，开销要小）。 */
    private static final long PROBE_INTERVAL_IDLE_MS = 1200L;
    /** 接管中 / 正在播放时的探测间隔（进度条要跟手）。 */
    private static final long PROBE_INTERVAL_ACTIVE_MS = 500L;
    /**
     * 连续多少次探测不到视频就暂停探测（B21）。
     *
     * <p>避免对纯文本页持续每 1.2 秒注入一次脚本；页面加载完成时会由
     * {@link #probeOnce()} 重新唤醒。
     */
    private static final int EMPTY_PROBE_LIMIT = 6;
    /** 接管后多久校验一次效果（站点重渲染通常发生在这一两个刷新周期内）。 */
    private static final long TAKE_OVER_VERIFY_DELAY_MS = 1200L;

    /** 视频状态回调。 */
    public interface VideoStateListener {
        /** 页面中首次发现可接管视频。 */
        void onVideoDetected(@NonNull BrowserVideoState state);
        /** 状态刷新（进度、播放态、倍速变化）。 */
        void onStateUpdated(@NonNull BrowserVideoState state);
        /** 视频消失（页面跳转 / 元素被移除）。 */
        void onVideoGone();
        /**
         * 接管失败：两种接管模式都验证不过，已自动回滚。
         *
         * <p>宿主应提示用户"该站点暂不支持接管"并保留网页自带播放器。
         */
        void onTakeOverFailed();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable private WebView webView;
    @Nullable private VideoStateListener listener;

    @NonNull private BrowserVideoState state = BrowserVideoState.empty();
    private boolean hadVideo = false;
    private boolean takeOverActive = false;
    private boolean probing = false;
    private boolean destroyed = false;

    // ===== B21 探测节流 =====
    private int consecutiveEmptyProbes = 0;
    private boolean probeSuspended = false;

    // ===== B22 接管效果校验与自动降级 =====
    private boolean pendingVerify = false;
    private boolean takeOverStyleOnly = false;
    private int takeOverAttempt = 0;

    /** 代次：bind/unbind/destroy 递增，用于作废在途回调。 */
    private int generation = 0;

    /** 用户通过倍速按钮选择的倍速（长按快进结束后恢复到这个值）。 */
    private float userRate = 1.0f;
    /** 长按快进倍速（来自设置）。 */
    private float fastForwardRate = BrowserPlayerMath.DEFAULT_FAST_FORWARD_RATE;
    /** 是否处于长按快进中。 */
    private boolean fastForwarding = false;

    private final Runnable probeRunnable = new Runnable() {
        @Override
        public void run() {
            runProbe();
        }
    };

    private final Runnable verifyRunnable = new Runnable() {
        @Override
        public void run() {
            runTakeOverVerify();
        }
    };

    // ===== 绑定与生命周期 =====

    /**
     * 绑定当前 Tab 的 WebView。
     *
     * <p>会作废上一代的所有在途回调，并重置接管态（新 Tab 的页面不可能还处于旧的接管中）。
     */
    public void bind(@Nullable WebView webView) {
        boolean wasProbing = probing;
        // A rebind can happen without the host showing the overlay first (redirect,
        // pool trim or close-all). Restore the old DOM before dropping its WebView.
        releaseTakeOver();
        generation++;
        mainHandler.removeCallbacks(probeRunnable);
        this.webView = webView;
        this.state = BrowserVideoState.empty();
        this.hadVideo = false;
        this.takeOverActive = false;
        this.fastForwarding = false;
        this.consecutiveEmptyProbes = 0;
        this.probeSuspended = false;
        if (wasProbing && webView != null && !destroyed) {
            mainHandler.post(probeRunnable);
        }
    }

    /** 解绑（切 Tab 但不销毁控制器时）。 */
    public void unbind() {
        releaseTakeOver();
        generation++;
        mainHandler.removeCallbacks(probeRunnable);
        webView = null;
        takeOverActive = false;
        fastForwarding = false;
        state = BrowserVideoState.empty();
        hadVideo = false;
        consecutiveEmptyProbes = 0;
        probeSuspended = false;
    }

    /** 销毁：停止探测并释放引用。 */
    public void destroy() {
        releaseTakeOver();
        generation++;
        stopProbing();
        destroyed = true;
        webView = null;
        listener = null;
        pendingVerify = false;
        mainHandler.removeCallbacksAndMessages(null);
    }

    public void setListener(@Nullable VideoStateListener listener) {
        this.listener = listener;
    }

    // ===== 探测 =====

    public void startProbing() {
        if (destroyed) return;
        probing = true;
        resumeProbingLocked();
        mainHandler.removeCallbacks(probeRunnable);
        mainHandler.post(probeRunnable);
    }

    public void stopProbing() {
        probing = false;
        mainHandler.removeCallbacks(probeRunnable);
    }

    /**
     * 立即探测一次并唤醒被节流的探测。
     *
     * <p>页面加载完成时调用：既省去等待下一个周期，也是 B21 节流后的唯一唤醒入口。
     */
    public void probeOnce() {
        if (destroyed) return;
        resumeProbingLocked();
        runProbe();
    }

    /** 重置"连续空探测"计数并解除暂停。 */
    private void resumeProbingLocked() {
        consecutiveEmptyProbes = 0;
        probeSuspended = false;
    }

    private void runProbe() {
        if (destroyed || !probing) return;
        final WebView wv = webView;
        if (wv == null) {
            scheduleNextProbe(PROBE_INTERVAL_IDLE_MS);
            return;
        }
        final int captured = generation;
        // 走 execJs 单点漏斗，保证全类只有一处 evaluateJavascript
        execJs(BrowserVideoJs.DETECT, value -> {
            if (captured != generation || destroyed) return;
            BrowserVideoState parsed = BrowserVideoState.parse(stripJsQuotes(value));
            Log.i(TAG, "DETECT hasVideo=" + parsed.hasVideo() + " count=" + parsed.count);
            onStateParsed(parsed);
        });
    }

    private void onStateParsed(@NonNull BrowserVideoState parsed) {
        state = parsed;
        boolean has = parsed.hasVideo();

        if (has) {
            // 首次发现：先锁定元素下标，再通知宿主（宿主据此决定是否弹出接管入口）
            if (!hadVideo) {
                execSelect(parsed.index);
                if (listener != null) listener.onVideoDetected(parsed);
            } else if (listener != null) {
                listener.onStateUpdated(parsed);
            }
        } else if (hadVideo) {
            // 视频消失：若处于接管态，先还原 DOM
            if (takeOverActive) {
                execJs(BrowserVideoJs.RELEASE);
                takeOverActive = false;
            }
            if (listener != null) listener.onVideoGone();
        }
        hadVideo = has;

        // B21 节流：连续探测不到视频就暂停，直到下一个页面加载（probeOnce）再唤醒
        if (has) {
            consecutiveEmptyProbes = 0;
        } else {
            consecutiveEmptyProbes++;
            if (consecutiveEmptyProbes >= EMPTY_PROBE_LIMIT) {
                probeSuspended = true;
            }
        }

        // 倍速守序：部分站点在切集/插播广告后会把 playbackRate 重置为 1，
        // 探测到漂移就重新下发用户倍速（长按快进中跳过，避免打断手势）。
        if (has && takeOverActive && !fastForwarding
                && Math.abs(parsed.rate - userRate) > 0.01f) {
            applyRate(userRate);
        }

        scheduleNextProbe(has && (takeOverActive || parsed.isPlaying())
                ? PROBE_INTERVAL_ACTIVE_MS
                : PROBE_INTERVAL_IDLE_MS);
    }

    private void scheduleNextProbe(long delayMs) {
        if (destroyed || !probing || probeSuspended) return;
        mainHandler.removeCallbacks(probeRunnable);
        mainHandler.postDelayed(probeRunnable, delayMs);
    }

    // ===== 播放控制 =====

    public void play() {
        runAction(BrowserVideoJs.ACTION_PLAY, 0d);
        postProbeSoon();
    }

    public void pause() {
        runAction(BrowserVideoJs.ACTION_PAUSE, 0d);
        postProbeSoon();
    }

    public void togglePlay() {
        runAction(BrowserVideoJs.ACTION_TOGGLE, 0d);
        postProbeSoon();
    }

    /** 跳转到指定位置（毫秒）。直播 / 时长未知时忽略。 */
    public void seekTo(long positionMs) {
        if (state.durationMs <= 0) return;
        long target = BrowserPlayerMath.clampSeek(positionMs, state.durationMs);
        runAction(BrowserVideoJs.ACTION_SEEK, target / 1000d);
        postProbeSoon();
    }

    /** 相对跳转（毫秒增量，可为负）。 */
    public void seekBy(long deltaMs) {
        seekTo(state.currentTimeMs + deltaMs);
    }

    /** 切换静音，返回切换后是否静音（未知时返回 false）。 */
    public boolean toggleMute() {
        runAction(BrowserVideoJs.ACTION_MUTE, 0d);
        postProbeSoon();
        return !state.muted;
    }

    // ===== 倍速 =====

    /** 设置用户倍速（会持久化到 userRate，长按快进结束后回到此值）。 */
    public void setUserRate(float rate) {
        userRate = BrowserPlayerMath.clampRate(rate);
        if (!fastForwarding) {
            applyRate(userRate);
        }
    }

    public float getUserRate() {
        return userRate;
    }

    /** 设置长按快进使用的倍速（来自浏览器设置）。 */
    public void setFastForwardRate(float rate) {
        fastForwardRate = BrowserPlayerMath.clampRate(rate);
    }

    /**
     * 开始长按快进。
     *
     * @param holdMs 已按住时长；超过阈值会自动升级到更高倍速
     * @return 实际生效的倍速
     */
    public float beginFastForward(long holdMs) {
        float rate = BrowserPlayerMath.fastForwardRate(holdMs, fastForwardRate);
        if (!fastForwarding) {
            fastForwarding = true;
        }
        applyRate(rate);
        return rate;
    }

    /**
     * 长按持续中：应用手势层算出的倍速（2x → 3x 由手势层按按住时长决定）。
     *
     * <p>这里直接信任传入值而不重算，是为了让"手势层"成为倍速的唯一决策点，
     * 避免两处各自按时间推算导致抖动。
     */
    public float updateFastForward(float rate) {
        if (!fastForwarding) return userRate;
        applyRate(rate);
        return BrowserPlayerMath.clampRate(rate);
    }

    /** 结束长按快进，恢复到用户倍速。 */
    public void endFastForward() {
        if (!fastForwarding) return;
        fastForwarding = false;
        applyRate(userRate);
    }

    public boolean isFastForwarding() {
        return fastForwarding;
    }

    private void applyRate(float rate) {
        float safe = BrowserPlayerMath.clampRate(rate);
        runAction(BrowserVideoJs.ACTION_RATE, safe);
    }

    // ===== 接管 =====

    /**
     * 接管页面播放器：把 video 元素提到最前铺满，原生 UI 覆盖其上。
     *
     * @param styleOnly true 时只改样式不移动 DOM（对会监听 DOM 变化的站点更稳）
     * @return 脚本是否执行成功（不代表一定可见，实际效果以探测结果为准）
     */
    public boolean takeOver(boolean styleOnly) {
        WebView wv = webView;
        if (wv == null || !state.hasVideo()) return false;
        execSelect(state.index);
        execJs(styleOnly ? BrowserVideoJs.TAKE_OVER_STYLE_ONLY : BrowserVideoJs.TAKE_OVER);
        takeOverActive = true;
        // B22：记录本次模式并安排一次效果校验，失败会换模式重试、再失败则回滚
        takeOverStyleOnly = styleOnly;
        takeOverAttempt = 0;
        pendingVerify = true;
        scheduleTakeOverVerify();
        // 接管前处于播放态的，接管后补一次 play（部分站点会在 DOM 变动时暂停）
        if (!state.paused && !state.ended) {
            runAction(BrowserVideoJs.ACTION_PLAY, 0d);
        }
        applyRate(fastForwarding ? BrowserPlayerMath.fastForwardRate(0, fastForwardRate) : userRate);
        postProbeSoon();
        return true;
    }

    // ===== B22 接管效果校验 =====

    private void scheduleTakeOverVerify() {
        mainHandler.removeCallbacks(verifyRunnable);
        mainHandler.postDelayed(verifyRunnable, TAKE_OVER_VERIFY_DELAY_MS);
    }

    /**
     * 校验接管是否真的生效：脚本执行成功 ≠ 视频被提到最前。
     *
     * <p>站点重渲染会把 video 元素塞回原容器甚至销毁它，表现为"接管成功但画面消失"。
     * 流程：校验不过 → 换另一种模式重试一次 → 仍不过 → 回滚并回调 onTakeOverFailed。
     */
    private void runTakeOverVerify() {
        if (destroyed || !pendingVerify) return;
        final WebView wv = webView;
        if (wv == null) {
            pendingVerify = false;
            return;
        }
        final int captured = generation;
        execJs(BrowserVideoJs.VERIFY, value -> {
            if (captured != generation || destroyed || !pendingVerify) return;
            if (parseVerifyResult(value)) {
                pendingVerify = false;
                Log.d(TAG, "takeOver verified OK (styleOnly=" + takeOverStyleOnly + ")");
                return;
            }
            takeOverAttempt++;
            if (takeOverAttempt == 1) {
                // 换一种接管模式重试：移节点 ↔ 仅改样式
                takeOverStyleOnly = !takeOverStyleOnly;
                execJs(takeOverStyleOnly
                        ? BrowserVideoJs.TAKE_OVER_STYLE_ONLY
                        : BrowserVideoJs.TAKE_OVER);
                if (!state.paused && !state.ended) {
                    runAction(BrowserVideoJs.ACTION_PLAY, 0d);
                }
                Log.w(TAG, "takeOver verify failed, retry with styleOnly=" + takeOverStyleOnly);
                scheduleTakeOverVerify();
            } else {
                // 两种模式都不行：回滚，把页面交回给网页自带播放器
                pendingVerify = false;
                Log.w(TAG, "takeOver failed after 2 attempts, rolling back");
                releaseTakeOver();
                if (listener != null) listener.onTakeOverFailed();
            }
        });
    }

    /** 解析 VERIFY 脚本返回；解析不出 ok:true 一律视为失败（保守）。 */
    private static boolean parseVerifyResult(@Nullable String raw) {
        String json = stripJsQuotes(raw);
        if (json.isEmpty()) return false;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            return obj.optBoolean("ok", false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 小窗模式：把已接管的视频画面摆到 WebView 视口内的指定矩形。
     *
     * <p>调用方传入的是 Android 视图像素；video 元素已被设为
     * {@code position:fixed}，脚本出口会按 {@code devicePixelRatio} 换算为 CSS 像素。
     * 坐标相对 WebView 视口，覆盖层与 WebView 先通过屏幕坐标做一次换算。
     * 必须在 {@link #takeOver(boolean)} 之后调用。
     */
    public void setVideoRect(int leftPx, int topPx, int widthPx, int heightPx) {
        if (!takeOverActive) return;
        execJs(BrowserVideoJs.setRect(leftPx, topPx, widthPx, heightPx));
    }

    /** 从小窗恢复铺满视口。 */
    public void resetVideoRect() {
        if (!takeOverActive) return;
        execJs(BrowserVideoJs.TAKE_OVER_STYLE_ONLY);
    }

    /** 退出接管，还原页面 DOM 与样式，并恢复用户倍速。 */
    public void releaseTakeOver() {
        // B22：无论是否为校验触发，都要撤销待执行的校验，避免回滚后又去校验
        pendingVerify = false;
        mainHandler.removeCallbacks(verifyRunnable);
        if (!takeOverActive) return;
        takeOverActive = false;
        endFastForward();
        execJs(BrowserVideoJs.RELEASE);
        applyRate(userRate);
    }

    /** 当前生效的接管模式，供日志与诊断使用。 */
    public boolean isTakeOverStyleOnly() {
        return takeOverStyleOnly;
    }

    public boolean isTakeOverActive() {
        return takeOverActive;
    }

    // ===== 内部 =====

    private void runAction(@NonNull String action, double value) {
        WebView wv = webView;
        if (wv == null) return;
        execSelect(state.index >= 0 ? state.index : 0);
        execJs(BrowserVideoJs.action(action, value));
    }

    private void execSelect(int index) {
        WebView wv = webView;
        if (wv != null) execJs(BrowserVideoJs.select(index));
    }

    private void execJs(@NonNull String js) {
        execJs(js, null);
    }

    /**
     * 全类唯一的 evaluateJavascript 出口。
     *
     * <p>verify_browser.py 会强制校验"播放器只允许一处下发脚本"，
     * 集中到这里既满足约束，也便于统一兜底异常（WebView 已销毁时静默失败）。
     */
    private void execJs(@NonNull String js, @Nullable android.webkit.ValueCallback<String> callback) {
        WebView wv = webView;
        if (wv == null || destroyed) return;
        try {
            wv.evaluateJavascript(js, callback);
        } catch (Throwable t) {
            Log.w(TAG, "evaluateJavascript failed", t);
        }
    }

    /** 动作后立刻补一次探测，让进度条/播放态跟手。 */
    private void postProbeSoon() {
        mainHandler.removeCallbacks(probeRunnable);
        mainHandler.postDelayed(probeRunnable, 120L);
    }

    /**
     * evaluateJavascript 返回的是 JSON 字面量：字符串结果带首尾引号并转义，
     * 需要还原成真正的 JSON 文本再交给 {@link BrowserVideoState#parse(String)}。
     */
    @NonNull
    static String stripJsQuotes(@Nullable String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }

    @NonNull
    public BrowserVideoState getState() {
        return state;
    }
}
