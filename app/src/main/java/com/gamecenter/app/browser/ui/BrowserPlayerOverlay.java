package com.gamecenter.app.browser.ui;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.player.BrowserPlayerGestureHelper;
import com.gamecenter.app.browser.core.player.BrowserPlayerMath;
import com.gamecenter.app.browser.core.player.BrowserVideoController;
import com.gamecenter.app.browser.core.player.BrowserVideoState;

/**
 * 浏览器内置播放器的原生 UI 层（夸克式"接管网页播放器"）。
 *
 * <p><b>它不渲染画面</b>：画面仍是 WebView 里那个被 JS 提到最前、铺满视口的
 * {@code <video>}。本类负责：
 * <ul>
 *   <li>原生控制条（播放/暂停、进度、±10s、倍速、全屏、小窗、退出）；</li>
 *   <li>手势层（长按倍速快进、双击暂停、横向 seek、两侧调音量/亮度）；</li>
 *   <li>把控制指令转发给 {@link BrowserVideoController} 下发到页面 DOM。</li>
 * </ul>
 *
 * <p>这样既能"跳过网页自己的播放器"（原生 UI 完全盖住它），
 * 又不需要重新缓冲视频——MSE/HLS/blob 源因此全部可用。
 */
public class BrowserPlayerOverlay implements BrowserPlayerGestureHelper.PlayerGestureCallback {

    private static final String TAG = "BrowserPlayerOverlay";

    /** 控件自动隐藏延时。 */
    private static final long CONTROLS_AUTO_HIDE_MS = 3500L;
    /** 快进/快退步长。 */
    private static final long SEEK_STEP_MS = 10_000L;
    /** 小窗尺寸（dp，16:9）。 */
    private static final int MINI_WIDTH_DP = 200;
    private static final int MINI_HEIGHT_DP = 113;
    private static final int MINI_MARGIN_DP = 12;

    /** 播放器与宿主的交互契约。 */
    public interface Host {
        /** 用户点击"退出内置播放器"：还原页面 DOM、关闭覆盖层。 */
        void onPlayerExit();
        /** 请求显隐浏览器的顶栏/底栏（播放器全屏态）。 */
        void onRequestChromeHidden(boolean hidden);
        /** 小窗态变化，宿主据此调整返回键处理。 */
        void onMiniModeChanged(boolean mini);
        /**
         * H-1：用户点击"下载"且当前视频源是 http/https 直链。
         * blob:/data: 等 MSE 源不会触发本回调（按钮直接隐藏）。
         */
        void onDownloadVideo(@NonNull String videoUrl);
    }

    private final Context context;
    /** 覆盖层挂载点（webview_container 内，与视频画面同区域）。 */
    private final ViewGroup container;
    /** WebView 容器，用于把覆盖层坐标换算成 WebView 视口坐标。 */
    @Nullable private final ViewGroup webViewContainer;
    private final BrowserVideoController controller;
    private final Host host;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable private final AudioManager audioManager;

    @Nullable private View root;
    @Nullable private View gestureSurface;
    @Nullable private LinearLayout topBar;
    @Nullable private LinearLayout bottomBar;
    @Nullable private LinearLayout hintContainer;
    @Nullable private TextView hintText;
    @Nullable private TextView hintSubText;
    @Nullable private TextView titleView;
    @Nullable private TextView positionView;
    @Nullable private TextView durationView;
    @Nullable private SeekBar seekBar;
    @Nullable private ImageButton playButton;
    @Nullable private ImageButton fullscreenButton;
    @Nullable private ImageButton downloadButton;
    @Nullable private TextView speedButton;

    @Nullable private BrowserPlayerGestureHelper gestureHelper;

    private boolean showing = false;
    private boolean mini = false;
    private boolean chromeHidden = false;
    private boolean userDragging = false;
    private boolean destroyed = false;

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!showing || mini) return;
            // 暂停中不自动隐藏，否则用户没法点播放
            if (controller.getState().isPlaying()) {
                setControlsVisible(false);
            }
        }
    };

    private final Runnable syncVideoRectRunnable = new Runnable() {
        @Override
        public void run() {
            syncVideoRectToOverlay();
        }
    };

    public BrowserPlayerOverlay(@NonNull Context context,
                                @NonNull ViewGroup container,
                                @Nullable ViewGroup webViewContainer,
                                @NonNull BrowserVideoController controller,
                                @NonNull Host host) {
        this.context = context.getApplicationContext();
        this.container = container;
        this.webViewContainer = webViewContainer;
        this.controller = controller;
        this.host = host;
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        inflate();
    }

    // ===== 初始化 =====

    private void inflate() {
        View view = LayoutInflater.from(container.getContext())
                .inflate(R.layout.overlay_browser_player, container, false);
        container.addView(view);
        root = view;

        gestureSurface = view.findViewById(R.id.player_gesture_surface);
        topBar = view.findViewById(R.id.player_top_bar);
        bottomBar = view.findViewById(R.id.player_bottom_bar);
        hintContainer = view.findViewById(R.id.player_hint_container);
        hintText = view.findViewById(R.id.player_hint_text);
        hintSubText = view.findViewById(R.id.player_hint_sub_text);
        titleView = view.findViewById(R.id.player_title);
        positionView = view.findViewById(R.id.player_position);
        durationView = view.findViewById(R.id.player_duration);
        seekBar = view.findViewById(R.id.player_seek);
        playButton = view.findViewById(R.id.player_btn_play);
        fullscreenButton = view.findViewById(R.id.player_btn_fullscreen);
        downloadButton = view.findViewById(R.id.player_btn_download);
        speedButton = view.findViewById(R.id.player_btn_speed);

        gestureHelper = new BrowserPlayerGestureHelper(container.getContext(), this);
        View surface = gestureSurface;
        if (surface != null) {
            surface.setOnTouchListener((v, event) -> {
                // 小窗态下整块区域用于拖动窗口，不再走播放手势
                if (mini) return handleMiniDrag(v, event);
                BrowserPlayerGestureHelper helper = gestureHelper;
                return helper != null && helper.onTouch(v, event);
            });
        }

        if (playButton != null) {
            playButton.setOnClickListener(v -> {
                controller.togglePlay();
                resetHideTimer();
            });
        }
        ImageButton rewind = view.findViewById(R.id.player_btn_rewind);
        if (rewind != null) {
            rewind.setOnClickListener(v -> {
                controller.seekBy(-SEEK_STEP_MS);
                resetHideTimer();
            });
        }
        ImageButton forward = view.findViewById(R.id.player_btn_forward);
        if (forward != null) {
            forward.setOnClickListener(v -> {
                controller.seekBy(SEEK_STEP_MS);
                resetHideTimer();
            });
        }
        if (speedButton != null) {
            speedButton.setOnClickListener(v -> {
                float next = BrowserPlayerMath.nextSpeed(
                        controller.getUserRate(), BrowserPlayerMath.SPEED_LADDER);
                controller.setUserRate(next);
                speedButton.setText(BrowserPlayerMath.formatRate(next));
                resetHideTimer();
            });
        }
        if (fullscreenButton != null) {
            fullscreenButton.setOnClickListener(v -> {
                chromeHidden = !chromeHidden;
                host.onRequestChromeHidden(chromeHidden);
                updateFullscreenIcon();
                // Hiding/showing the browser chrome changes the WebView viewport.
                // The taken-over DOM video uses an explicit rect, so wait for that
                // layout pass and then map the native overlay's new bounds back to
                // WebView coordinates. Without this, full-screen retained the old
                // content-area height on some devices.
                if (root != null) root.post(syncVideoRectRunnable);
                resetHideTimer();
            });
        }
        ImageButton miniButton = view.findViewById(R.id.player_btn_mini);
        if (miniButton != null) {
            miniButton.setOnClickListener(v -> setMini(!mini));
        }
        if (downloadButton != null) {
            downloadButton.setOnClickListener(v -> {
                String src = controller.getState().currentSrc;
                if (!src.isEmpty() && controller.getState().isDirectPlayable()) {
                    host.onDownloadVideo(src);
                    resetHideTimer();
                }
            });
        }
        ImageButton close = view.findViewById(R.id.player_btn_close);
        if (close != null) {
            close.setOnClickListener(v -> host.onPlayerExit());
        }

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) return;
                    long duration = controller.getState().durationMs;
                    if (duration <= 0) return;
                    long target = duration * progress / 1000L;
                    if (positionView != null) {
                        positionView.setText(BrowserPlayerMath.formatTime(target));
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    userDragging = true;
                    resetHideTimer();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    userDragging = false;
                    long duration = controller.getState().durationMs;
                    if (duration > 0) {
                        controller.seekTo(duration * seekBar.getProgress() / 1000L);
                    }
                    resetHideTimer();
                }
            });
        }
    }

    // ===== 生命周期 =====

    public void show() {
        if (destroyed || root == null) return;
        showing = true;
        root.setVisibility(View.VISIBLE);
        setControlsVisible(true);
        resetHideTimer();
        // 必须 post：此刻布局尚未完成，getWidth()/getHeight() 可能还是 0，
        // 直接同步换算会向页面下发一个 0 尺寸的矩形，视频会缩成一点。
        root.post(syncVideoRectRunnable);
    }

    public void hide() {
        if (root == null) return;
        showing = false;
        mainHandler.removeCallbacks(hideControlsRunnable);
        mainHandler.removeCallbacks(syncVideoRectRunnable);
        root.setVisibility(View.GONE);
        if (chromeHidden) {
            chromeHidden = false;
            host.onRequestChromeHidden(false);
            updateFullscreenIcon();
        }
    }

    public void destroy() {
        if (chromeHidden) {
            chromeHidden = false;
            host.onRequestChromeHidden(false);
        }
        showing = false;
        mini = false;
        userDragging = false;
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (gestureHelper != null) {
            gestureHelper.cancel();
            gestureHelper = null;
        }
        if (root != null) {
            container.removeView(root);
            root = null;
        }
    }

    public boolean isShowing() {
        return showing;
    }

    public boolean isMini() {
        return mini;
    }

    /** 宿主在 Fragment 暂停 / 隐藏时调用，防止手指离开屏幕后仍在倍速。 */
    public void cancelGestures() {
        if (gestureHelper != null) gestureHelper.cancel();
        hideHint();
    }

    public void setFastForwardRate(float rate) {
        if (gestureHelper != null) gestureHelper.setFastForwardRate(rate);
    }

    /** H-4：开关长按快进手势。 */
    public void setLongPressEnabled(boolean enabled) {
        if (gestureHelper != null) gestureHelper.setLongPressEnabled(enabled);
    }

    /**
     * 控制器探测到新状态时回调：刷新进度、时间、播放图标。
     */
    public void onStateUpdated(@NonNull BrowserVideoState state) {
        if (destroyed) return;
        if (titleView != null && state.title != null && !state.title.isEmpty()) {
            titleView.setText(state.title);
        }
        if (positionView != null) {
            positionView.setText(BrowserPlayerMath.formatTime(state.currentTimeMs));
        }
        if (durationView != null) {
            durationView.setText(state.isLive()
                    ? context.getString(R.string.browser_player_live)
                    : BrowserPlayerMath.formatTime(state.durationMs));
        }
        if (seekBar != null) {
            seekBar.setEnabled(!state.isLive());
            if (!userDragging) {
                seekBar.setProgress(state.progressPercent() * 10);
            }
        }
        if (playButton != null) {
            playButton.setImageResource(state.isPlaying()
                    ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
            playButton.setContentDescription(context.getString(state.isPlaying()
                    ? R.string.browser_player_pause : R.string.browser_player_play));
        }
        if (speedButton != null && !controller.isFastForwarding()) {
            speedButton.setText(BrowserPlayerMath.formatRate(controller.getUserRate()));
        }
        // H-1：仅直链（http/https）可下载；blob:/data:（MSE）脱离页面上下文拿不到完整流
        if (downloadButton != null) {
            downloadButton.setVisibility(state.isDirectPlayable() ? View.VISIBLE : View.GONE);
        }
    }

    // ===== PlayerGestureCallback =====

    @Override
    public boolean isPlaying() {
        return controller.getState().isPlaying();
    }

    @Override
    public long getDurationMs() {
        return controller.getState().durationMs;
    }

    @Override
    public long getCurrentPositionMs() {
        return controller.getState().currentTimeMs;
    }

    @Override
    public boolean isSeekable() {
        BrowserVideoState state = controller.getState();
        return state.hasVideo() && !state.isLive();
    }

    @Override
    public void onTogglePlay() {
        controller.togglePlay();
        resetHideTimer();
    }

    @Override
    public void onToggleControls() {
        if (topBar == null) return;
        boolean visible = topBar.getVisibility() == View.VISIBLE;
        setControlsVisible(!visible);
        resetHideTimer();
    }

    @Override
    public void onSeekPreview(long deltaMs, long targetMs) {
        String sign = deltaMs >= 0 ? "+" : "-";
        long seconds = Math.abs(deltaMs) / 1000L;
        showHint(context.getString(R.string.browser_player_seek_delta, sign, seconds),
                BrowserPlayerMath.formatTime(targetMs)
                        + " / " + BrowserPlayerMath.formatTime(controller.getState().durationMs));
    }

    @Override
    public void onSeekCommit(long targetMs) {
        controller.seekTo(targetMs);
        hideHint();
        resetHideTimer();
    }

    @Override
    public void onVolumeDelta(float delta) {
        if (audioManager == null) return;
        try {
            int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int next = current + Math.round(delta * max);
            if (next < 0) next = 0;
            if (next > max) next = max;
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
            showHint(context.getString(R.string.browser_player_volume) + " " + (next * 100 / max) + "%", "");
        } catch (Throwable t) {
            Log.w(TAG, "adjust volume failed", t);
        }
    }

    @Override
    public void onBrightnessDelta(float delta) {
        Activity activity = resolveActivity(container.getContext());
        if (activity == null) return;
        try {
            Window window = activity.getWindow();
            WindowManager.LayoutParams lp = window.getAttributes();
            float base = lp.screenBrightness;
            if (base < 0f) base = 0.5f; // -1 表示跟随系统
            float next = base + delta;
            if (next < 0.01f) next = 0.01f;
            if (next > 1f) next = 1f;
            lp.screenBrightness = next;
            window.setAttributes(lp);
            showHint(context.getString(R.string.browser_player_brightness) + " " + (int) (next * 100) + "%", "");
        } catch (Throwable t) {
            Log.w(TAG, "adjust brightness failed", t);
        }
    }

    @Override
    public void onFastForwardStart(float rate) {
        controller.beginFastForward(0L);
        showFastForwardHint(rate);
    }

    @Override
    public void onFastForwardUpdate(float rate) {
        controller.updateFastForward(rate);
        showFastForwardHint(rate);
    }

    @Override
    public void onFastForwardEnd() {
        controller.endFastForward();
        hideHint();
        if (speedButton != null) {
            speedButton.setText(BrowserPlayerMath.formatRate(controller.getUserRate()));
        }
        resetHideTimer();
    }

    // ===== 小窗 =====

    private void setMini(boolean enable) {
        if (root == null || mini == enable) return;
        mini = enable;
        ViewGroup.LayoutParams lp = root.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) return;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lp;
        float density = context.getResources().getDisplayMetrics().density;
        if (mini) {
            setControlsVisible(false);
            params.width = (int) (MINI_WIDTH_DP * density);
            params.height = (int) (MINI_HEIGHT_DP * density);
            params.gravity = Gravity.BOTTOM | Gravity.END;
            int margin = (int) (MINI_MARGIN_DP * density);
            params.setMargins(margin, margin, margin, margin);
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.gravity = Gravity.NO_GRAVITY;
            params.setMargins(0, 0, 0, 0);
            controller.resetVideoRect();
            setControlsVisible(true);
        }
        root.setLayoutParams(params);
        root.post(syncVideoRectRunnable);
        host.onMiniModeChanged(mini);
        resetHideTimer();
    }

    /** 小窗拖动：更新 margins 并同步视频矩形。 */
    private boolean handleMiniDrag(@NonNull View view, @NonNull MotionEvent event) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof FrameLayout.LayoutParams)) return false;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lp;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                view.setTag(R.id.player_root, new float[]{event.getRawX(), event.getRawY(),
                        params.leftMargin, params.topMargin});
                return true;
            case MotionEvent.ACTION_MOVE: {
                Object tag = view.getTag(R.id.player_root);
                if (!(tag instanceof float[])) return true;
                float[] start = (float[]) tag;
                int dx = (int) (event.getRawX() - start[0]);
                int dy = (int) (event.getRawY() - start[1]);
                int left = (int) Math.max(0, start[2] + dx);
                int top = (int) Math.max(0, start[3] + dy);
                params.gravity = Gravity.TOP | Gravity.START;
                params.leftMargin = left;
                params.topMargin = top;
                view.setLayoutParams(params);
                syncVideoRectToOverlay();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.setTag(R.id.player_root, null);
                return true;
            default:
                return false;
        }
    }

    /**
     * 把覆盖层的位置换算成 WebView 视口坐标并同步给视频元素。
     *
     * <p>video 是 {@code position:fixed}，坐标相对 WebView 视口；
     * 覆盖层在 Fragment 视图树里，两者需通过屏幕坐标做一次换算。
     */
    private void syncVideoRectToOverlay() {
        if (root == null || !showing) return;
        View target = findWebView();
        if (target == null) return;
        int[] overlayPos = new int[2];
        int[] webPos = new int[2];
        root.getLocationOnScreen(overlayPos);
        target.getLocationOnScreen(webPos);
        int left = overlayPos[0] - webPos[0];
        int top = overlayPos[1] - webPos[1];
        if (left < 0) left = 0;
        if (top < 0) top = 0;
        controller.setVideoRect(left, top, root.getWidth(), root.getHeight());
    }

    /**
     * 找到当前可见的 WebView：优先返回 webview_container 内的 WebView 实例，
     * 找不到时退回容器本身（坐标换算误差可接受）。
     */
    @Nullable
    private View findWebView() {
        ViewGroup group = webViewContainer;
        if (group == null) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof android.webkit.WebView && child.getVisibility() == View.VISIBLE) {
                return child;
            }
        }
        return group;
    }

    // ===== 内部工具 =====

    private void setControlsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (topBar != null) topBar.setVisibility(visibility);
        if (bottomBar != null) bottomBar.setVisibility(visibility);
    }

    private void resetHideTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable);
        if (mini) return;
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_AUTO_HIDE_MS);
    }

    private void showFastForwardHint(float rate) {
        String rateText = BrowserPlayerMath.formatRate(rate);
        showHint(context.getString(R.string.browser_player_fast_forward, rateText),
                context.getString(R.string.browser_player_fast_forward_hint,
                        BrowserPlayerMath.formatRate(controller.getUserRate())));
        if (speedButton != null) speedButton.setText(rateText);
    }

    private void showHint(@NonNull String main, @NonNull String sub) {
        if (hintContainer == null) return;
        hintContainer.setVisibility(View.VISIBLE);
        if (hintText != null) hintText.setText(main);
        if (hintSubText != null) {
            hintSubText.setText(sub);
            hintSubText.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void hideHint() {
        if (hintContainer != null) hintContainer.setVisibility(View.GONE);
    }

    private void updateFullscreenIcon() {
        if (fullscreenButton == null) return;
        fullscreenButton.setImageResource(chromeHidden
                ? R.drawable.ic_browser_player_fullscreen_exit
                : R.drawable.ic_browser_player_fullscreen);
        fullscreenButton.setContentDescription(context.getString(chromeHidden
                ? R.string.browser_player_fullscreen_exit
                : R.string.browser_player_fullscreen));
    }

    /** 从任意 Context 解包出 Activity（Fragment 传入的可能是 ContextThemeWrapper）。 */
    @Nullable
    private static Activity resolveActivity(@Nullable Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }
}
