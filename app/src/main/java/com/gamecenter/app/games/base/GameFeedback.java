package com.gamecenter.app.games.base;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.utils.SoundManager;

/**
 * P3 统一游戏反馈助手：为内置游戏提供音效与震动反馈。
 *
 * <p>所有播放入口内部都会实时读取 {@link SettingsManager} 的
 * shouldPlayGameSound()/shouldVibrate()，确保游戏内反馈严格遵循
 * 设置页的"音效总开关/游戏音效/震动"三个开关（游戏中途切换立即生效）。</p>
 *
 * <p>共享音效资源（res/raw）：
 * <ul>
 *   <li>ui_turn.wav — 落子/滑动/移动</li>
 *   <li>ui_confirm.wav — 按钮确认/轻操作</li>
 *   <li>ui_notice.wav — 提示/非法操作</li>
 *   <li>sound_win.mp3 — 胜利</li>
 *   <li>sound_lose.mp3 — 失败</li>
 * </ul>
 * </p>
 *
 * <p>用法：onCreate 中创建并在 onDestroy 中 {@link #release()}。
 * 反馈调用点均在主线程（View 点击/游戏回调）。</p>
 */
public class GameFeedback {

    private static final String TAG = "GameFeedback";

    /** 轻震动时长（落子/点击） */
    public static final long VIBRATE_LIGHT = 20L;
    /** 中等震动时长（吃子/重要事件） */
    public static final long VIBRATE_MEDIUM = 40L;
    /** 强震动时长（失败/爆炸） */
    public static final long VIBRATE_STRONG = 80L;

    private final Context context;
    private final SoundManager soundManager;
    private final Vibrator vibrator;
    /** 音效加载是否完整失败（SoundPool 异常时禁用声音，只保留震动） */
    private boolean soundBroken = false;

    public GameFeedback(Context context) {
        this.context = context.getApplicationContext();
        SoundManager sm = null;
        try {
            sm = new SoundManager(this.context);
            sm.setVolume(0.6f);
            sm.loadSound(R.raw.ui_turn);
            sm.loadSound(R.raw.ui_confirm);
            sm.loadSound(R.raw.ui_notice);
            sm.loadSound(R.raw.sound_win);
            sm.loadSound(R.raw.sound_lose);
        } catch (Exception e) {
            Log.w(TAG, "音效初始化失败，仅保留震动反馈: " + e.getMessage());
            soundBroken = true;
        }
        this.soundManager = sm;
        this.vibrator = acquireVibrator(this.context);
    }

    private static Vibrator acquireVibrator(Context appContext) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vm = (VibratorManager)
                        appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return vm != null ? vm.getDefaultVibrator() : null;
            }
            return (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 音效 ====================

    /** 落子/滑动/移动音效 */
    public void playMove() {
        play(R.raw.ui_turn);
    }

    /** 按钮确认/轻操作音效 */
    public void playClick() {
        play(R.raw.ui_confirm);
    }

    /** 提示/非法操作音效 */
    public void playNotice() {
        play(R.raw.ui_notice);
    }

    /** 胜利音效 */
    public void playWin() {
        play(R.raw.sound_win);
    }

    /** 失败音效 */
    public void playLose() {
        play(R.raw.sound_lose);
    }

    private void play(int resId) {
        if (soundBroken || soundManager == null) return;
        try {
            soundManager.setEnabled(SettingsManager.getInstance(context).shouldPlayGameSound());
            soundManager.playSound(resId);
        } catch (Exception e) {
            Log.w(TAG, "音效播放失败 resId=" + resId + ": " + e.getMessage());
        }
    }

    // ==================== 震动 ====================

    /** 轻震动（落子/点击） */
    public void vibrateLight() {
        vibrate(VIBRATE_LIGHT);
    }

    /** 中等震动（吃子/重要事件） */
    public void vibrateMedium() {
        vibrate(VIBRATE_MEDIUM);
    }

    /** 强震动（失败/爆炸） */
    public void vibrateStrong() {
        vibrate(VIBRATE_STRONG);
    }

    private void vibrate(long ms) {
        if (!SettingsManager.getInstance(context).shouldVibrate()) return;
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception e) {
            Log.w(TAG, "震动反馈失败: " + e.getMessage());
        }
    }

    // ==================== 组合反馈 ====================

    /** 落子/移动组合反馈：音效 + 轻震动 */
    public void feedbackMove() {
        playMove();
        vibrateLight();
    }

    /** 胜利组合反馈：音效 + 中震动 */
    public void feedbackWin() {
        playWin();
        vibrateMedium();
    }

    /** 失败组合反馈：音效 + 强震动 */
    public void feedbackLose() {
        playLose();
        vibrateStrong();
    }

    /** 错误/非法操作组合反馈：提示音 + 轻震动 */
    public void feedbackError() {
        playNotice();
        vibrateLight();
    }

    // ==================== 生命周期 ====================

    /** 释放音效资源（onDestroy 中调用），震动无需释放 */
    public void release() {
        if (soundManager != null) {
            try {
                soundManager.release();
            } catch (Exception ignored) {
            }
        }
    }
}
