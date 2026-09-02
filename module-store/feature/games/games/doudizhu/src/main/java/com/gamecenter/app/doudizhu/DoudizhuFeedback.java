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

import java.util.HashSet;
import java.util.Set;

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
 * <p><b>降级保证（不崩）</b>：构造与每个播放入口均 try-catch 包裹，且粒度为
 * <b>逐项独立</b>（P5 审查修复 C）——</p>
 * <ul>
 *   <li>预加载逐项 try-catch：单个音效资产加载失败只废那一个（播放时该项跳过），
 *       不拖垮其他音效；仅 SoundManager 构造本身失败才置 {@code soundBroken}
 *       永久禁音（保留震动）。</li>
 *   <li>设置开关、震动器获取、{@code getApplicationContext()} 等构造期调用全部
 *       纳入防护，任何一步失败都按各自默认值降级，不中断构造。</li>
 *   <li>单个播放/震动调用失败（资源缺失、Vibrator 不可用等）只记 log 不置位——
 *       一次失败不禁音，下次调用重试，对局流程不受影响。</li>
 *   <li>{@link #release()} 后置 {@code released}：后续 {@code playSfx}/{@code vibrate}
 *       短路，防 detach 后延迟回调打到已释放的 SoundPool/Vibrator。</li>
 * </ul>
 *
 * <p>模块独立运行（类加载不到宿主类时）由动态模块宿主环境保证宿主类恒在；
 * JVM 单测不触达本类。</p>
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
     * 音效链路是否已损坏：仅 SoundManager 构造失败时置位并永久禁音（保留震动）；
     * 单个资产加载失败只废该资产，播放阶段失败只记 log 不置位。
     */
    private boolean soundBroken = false;
    /** 已释放标志：release() 后 playSfx/vibrate 短路，防回推已释放的 SoundPool。 */
    private volatile boolean released = false;
    /**
     * 预加载失败的音效资产（宿主 res/raw 资源 ID）：播放时直接跳过这些项，
     * 不再反复触发失败加载；其余资产照常。
     */
    private final Set<Integer> brokenSfxResIds = new HashSet<>();

    public DoudizhuFeedback(Context context) {
        Context app = null;
        try {
            app = context.getApplicationContext();
        } catch (Exception e) {
            Log.w(TAG, "getApplicationContext 失败，退回原 Context: " + e);
        }
        if (app == null) {
            app = context;
        }

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
        } catch (Exception e) {
            Log.w(TAG, "SoundManager 初始化失败，降级为无音效（仅震动）: " + e);
            soundBroken = true;
        }
        this.soundManager = manager;
        preloadAllSfx();
        this.vibrator = acquireVibrator(app);
    }

    /**
     * 预加载全部音效资产：逐项 try-catch，单个资产失败（如打包缺文件抛
     * {@code Resources.NotFoundException}）只废那一个，不拖垮其余音效。
     */
    private void preloadAllSfx() {
        if (soundBroken || soundManager == null) return;
        tryLoadSfx("sound_sendpk", R.raw.sound_sendpk);
        tryLoadSfx("card_bomb_sound", R.raw.card_bomb_sound);
        tryLoadSfx("card_rocket_sound", R.raw.card_rocket_sound);
        tryLoadSfx("card_plane_sound", R.raw.card_plane_sound);
        tryLoadSfx("card_doubleline_m", R.raw.card_doubleline_m);
        tryLoadSfx("sound_win", R.raw.sound_win);
        tryLoadSfx("sound_lose", R.raw.sound_lose);
    }

    /** 单个音效资产的防护加载；失败记入 {@link #brokenSfxResIds}（该项禁用）。 */
    private void tryLoadSfx(String name, int resId) {
        try {
            soundManager.loadSound(resId);
        } catch (Exception e) {
            brokenSfxResIds.add(resId);
            Log.w(TAG, "音效资产加载失败（该项禁用，其余不受影响）: " + name + ": " + e);
        }
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
            Log.w(TAG, "Vibrator 获取失败，降级为无震动: " + e);
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
        if (sfx == null || released || soundBroken || soundManager == null) return;
        int resId = resIdFor(sfx);
        if (resId == 0 || brokenSfxResIds.contains(resId)) return;
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
        if (ms <= 0 || released) return;
        try {
            if (settings != null && !settings.shouldVibrate()) return;
        } catch (Exception e) {
            Log.w(TAG, "震动开关读取失败，按关闭处理: " + e);
            return;
        }
        if (vibrator == null) return;
        try {
            if (!vibrator.hasVibrator()) return;
        } catch (Exception e) {
            Log.w(TAG, "hasVibrator 检测失败，跳过本次震动: " + e);
            return;
        }
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

    /** 释放音效资源（SoundPool）。置 released 后 playSfx/vibrate 短路。震动无需释放。 */
    public void release() {
        if (released) return;
        released = true;
        if (soundManager != null) {
            try {
                soundManager.release();
            } catch (Exception e) {
                Log.w(TAG, "SoundManager 释放失败（忽略，继续走完 release 流程）: " + e);
            }
        }
    }
}
