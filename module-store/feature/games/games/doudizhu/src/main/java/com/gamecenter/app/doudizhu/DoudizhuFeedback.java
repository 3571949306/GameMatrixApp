package com.gamecenter.app.doudizhu;

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
 * 斗地主音效与震动桥（P5）：把模块的反馈意图（{@link DoudizhuEffectMapper.Sfx} /
 * {@link DoudizhuEffectMapper.Shake}）落到宿主基础设施上。
 *
 * <p><b>方案选型（宿主 res/raw + SoundManager）</b>：宿主 {@code res/raw} 已内置
 * 全套斗地主语音资产（{@code card_bomb_sound / card_rocket_sound / card_plane_sound /
 * card_doubleline_m / sound_sendpk / sound_win / sound_lose} 等），且模块构建依赖
 * 宿主 R.jar（compileOnly），经 {@code com.gamecenter.app.R.raw} 引用零新增资产、
 * 天然满足"动态模块禁止引用模块自有 res id"的工程约束。全部播放走宿主
 * {@link SoundManager}（SoundPool），开关实时读宿主 {@link SettingsManager}
 * 的 {@code shouldPlayGameSound()} / {@code shouldVibrate()}（设置页"音效总开关/
 * 游戏音效/震动"三开关，游戏中途切换立即生效）。</p>
 *
 * <p><b>降级保证（不崩）</b>：构造与每个播放入口均 try-catch 包裹。仅当构造阶段
 * 失败（SoundPool 初始化异常等）才置 {@code soundBroken} 并永久短路音效（保留
 * 震动）；单个播放/震动调用失败（资源缺失 {@code Resources.NotFoundException}、
 * Vibrator 不可用等）只记 log 不置位——一次失败不禁音，下次调用重试，对局流程
 * 不受影响。模块独立运行（类加载不到宿主类时）由动态模块宿主环境保证宿主类
 * 恒在；JVM 单测不触达本类。</p>
 *
 * <p>用法：在牌桌视图创建时构造一次，{@link #release()} 在
 * {@code onDetachedFromWindow} 调用。全部入口须在主线程调用。</p>
 */
public class DoudizhuFeedback {

    private static final String TAG = "DoudizhuFeedback";

    // ============ 震动时长（毫秒，对齐宿主 GameFeedback 档位） ============

    public static final long VIBRATE_LIGHT_MS = 20L;
    public static final long VIBRATE_MEDIUM_MS = 40L;
    public static final long VIBRATE_STRONG_MS = 80L;

    private final SoundManager soundManager;
    private final SettingsManager settings;
    private final Vibrator vibrator;
    /**
     * 音效链路是否已损坏：仅构造阶段失败（SoundPool 初始化异常）时置位并
     * 永久禁音（保留震动）；播放阶段失败只记 log 不置位，后续调用继续重试。
     */
    private boolean soundBroken = false;

    public DoudizhuFeedback(Context context) {
        Context app = context.getApplicationContext();
        SettingsManager sm = null;
        try {
            sm = SettingsManager.getInstance(app);
        } catch (Exception e) {
            Log.w(TAG, "SettingsManager 获取失败，音效/震动开关按默认开启处理: " + e);
        }
        this.settings = sm;

        SoundManager manager = null;
        try {
            manager = new SoundManager(app);
            manager.setVolume(0.8f);
            // 预加载全部用到的音效（宿主 res/raw 资产，R.jar 编译期解析）
            manager.loadSound(R.raw.sound_sendpk);
            manager.loadSound(R.raw.card_bomb_sound);
            manager.loadSound(R.raw.card_rocket_sound);
            manager.loadSound(R.raw.card_plane_sound);
            manager.loadSound(R.raw.card_doubleline_m);
            manager.loadSound(R.raw.sound_win);
            manager.loadSound(R.raw.sound_lose);
        } catch (Exception e) {
            Log.w(TAG, "音效初始化失败，降级为无音效（仅震动）: " + e);
            soundBroken = true;
        }
        this.soundManager = manager;
        this.vibrator = acquireVibrator(app);
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

    // ============ 音效 ============

    /**
     * 播放指定音效（开关实时读宿主设置，关闭时静默跳过）。
     *
     * @param sfx 音效种类
     */
    public void playSfx(DoudizhuEffectMapper.Sfx sfx) {
        if (sfx == null || soundBroken || soundManager == null) return;
        int resId = resIdFor(sfx);
        if (resId == 0) return;
        try {
            soundManager.setEnabled(settings == null || settings.shouldPlayGameSound());
            soundManager.playSound(resId);
        } catch (Exception e) {
            Log.w(TAG, "音效播放失败 sfx=" + sfx + ": " + e);
        }
    }

    /** 音效种类 → 宿主 res/raw 资源 ID；0 表示无对应资产（跳过播放） */
    private static int resIdFor(DoudizhuEffectMapper.Sfx sfx) {
        switch (sfx) {
            case PLAY: return R.raw.sound_sendpk;
            case BOMB: return R.raw.card_bomb_sound;
            case ROCKET: return R.raw.card_rocket_sound;
            case PLANE: return R.raw.card_plane_sound;
            case DOUBLE_LINE: return R.raw.card_doubleline_m;
            case WIN: return R.raw.sound_win;
            case LOSE: return R.raw.sound_lose;
            default: return 0;
        }
    }

    // ============ 震动 ============

    /**
     * 执行震动（开关实时读宿主设置 {@code shouldVibrate()}，关闭时静默跳过）。
     *
     * @param ms 震动时长（毫秒），非正值忽略
     */
    public void vibrate(long ms) {
        if (ms <= 0) return;
        if (settings != null && !settings.shouldVibrate()) return;
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms,
                        VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception e) {
            Log.w(TAG, "震动失败: " + e);
        }
    }

    /** 震动档位 → 时长映射（统一入口，便于上层一次调用完成特效+音效+震动） */
    public void shake(DoudizhuEffectMapper.Shake shake) {
        if (shake == null) return;
        switch (shake) {
            case LIGHT: vibrate(VIBRATE_LIGHT_MS); break;
            case MEDIUM: vibrate(VIBRATE_MEDIUM_MS); break;
            case STRONG: vibrate(VIBRATE_STRONG_MS); break;
            default: break;
        }
    }

    // ============ 生命周期 ============

    /** 释放音效资源（SoundPool）。震动无需释放。 */
    public void release() {
        if (soundManager != null) {
            try {
                soundManager.release();
            } catch (Exception ignored) {
            }
        }
    }
}
