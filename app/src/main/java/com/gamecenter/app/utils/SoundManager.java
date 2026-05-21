package com.gamecenter.app.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.Log;

import com.gamecenter.app.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用音效管理器 —— 提供游戏音效播放功能，支持短音效池和背景音乐。
 *
 * <p>简单来说，这个类就像游戏里的"音响师"——它负责管理两种声音：
 * 一种是短促的音效（比如点击按钮的"咔嗒"声、得分时的"叮"声），
 * 另一种是长篇的背景音乐（比如游戏场景中循环播放的旋律）。</p>
 *
 * <p>核心职责：
 * <ul>
 *   <li>短音效管理：通过 {@link SoundPool} 加载和播放短促音效（如点击、得分、爆炸等），
 *       支持同时播放多个音效（最多 {@link #MAX_STREAMS} 路）</li>
 *   <li>背景音乐管理：通过 {@link MediaPlayer} 播放长音频背景音乐，支持循环播放、暂停/恢复</li>
 *   <li>全局控制：统一的音效开关（enabled）和音量（volume）控制</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>SoundPool 用于短音效（低延迟、可并发），MediaPlayer 用于背景音乐（适合长音频流）</li>
 *   <li>音效资源 ID 到 SoundPool ID 的映射通过 {@link HashMap} 管理，避免重复加载</li>
 *   <li>背景音乐音量乘以 0.5 衰减系数，使其不盖过短音效</li>
 *   <li>使用 ApplicationContext 防止 Activity 泄漏</li>
 * </ul>
 */
public class SoundManager {

    private static final String TAG = "SoundManager";

    /** SoundPool 最大并发流数量，同时播放的短音效上限 */
    private static final int MAX_STREAMS = 5;

    /** 应用上下文（使用 ApplicationContext 避免Activity泄漏） */
    private final Context context;

    /** 短音效池，用于播放低延迟的短促音效。
     *  SoundPool 就像一个"预加载的音效库"——先把音效加载到内存里，
     *  播放时可以立刻响应，延迟很低，适合按钮点击、得分等需要即时反馈的场景 */
    private SoundPool soundPool;

    /** 音效资源 ID → SoundPool 内部 ID 的映射表，避免同一音效重复加载。
     *  就像一本通讯录——通过"名字"（资源ID）查找"电话号码"（SoundPool内部ID） */
    private final Map<Integer, Integer> soundMap;

    /** 背景音乐播放器，同一时刻只允许播放一首背景音乐。
     *  MediaPlayer 就像一个"音乐播放器APP"——适合播放较长的音频文件，
     *  支持暂停、继续、循环播放等操作，但启动比 SoundPool 稍慢 */
    private MediaPlayer bgPlayer;

    /** 音效全局开关，为 false 时所有播放请求被静默忽略。
     *  就像音响的总开关——关掉之后，不管你按多少次播放键都不会有声音 */
    private boolean enabled = true;

    /** 音效音量，范围 [0.0, 1.0] */
    private float volume = 1.0f;

    /**
     * 构造音效管理器。
     *
     * @param context 上下文，内部会调用 {@code getApplicationContext()} 避免内存泄漏
     */
    public SoundManager(Context context) {
        this.context = context.getApplicationContext();
        this.soundMap = new HashMap<>();
        initSoundPool();
    }

    /**
     * 初始化 SoundPool 实例。
     *
     * <p>配置音频属性为游戏用途（USAGE_GAME）和提示音类型（CONTENT_TYPE_SONIFICATION），
     * 使系统在音频焦点管理中将此类音频正确归类。
     */
    private void initSoundPool() {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attributes)
                .build();
    }

    /**
     * 设置音效全局开关。
     *
     * @param enabled true 启用音效，false 禁用所有音效播放
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 查询音效是否启用。
     *
     * @return true 表示音效已启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置音效音量。
     *
     * <p>音量值会被钳位到 [0.0, 1.0] 范围内，超出范围的值会被自动修正。
     *
     * @param volume 音量值，0.0 为静音，1.0 为最大音量
     */
    public void setVolume(float volume) {
        // 钳位到 [0.0, 1.0] 范围，防止非法值
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    /**
     * 获取当前音效音量。
     *
     * @return 音量值，范围 [0.0, 1.0]
     */
    public float getVolume() {
        return volume;
    }

    /**
     * 加载音效资源到 SoundPool。
     *
     * <p>如果该资源 ID 已加载过（存在于 soundMap 中），则跳过重复加载。
     * 加载后会将 SoundPool 分配的内部 ID 存入映射表，供后续播放使用。
     *
     * @param resId 音效资源 ID（R.raw.xxx）
     */
    public void loadSound(int resId) {
        if (!soundMap.containsKey(resId)) {
            int soundId = soundPool.load(context, resId, 1);
            soundMap.put(resId, soundId);
        }
    }

    /**
     * 播放短音效（不循环）。
     *
     * @param resId 音效资源 ID（R.raw.xxx）
     */
    public void playSound(int resId) {
        playSound(resId, false);
    }

    /**
     * 播放短音效，可指定是否循环。
     *
     * <p>如果音效尚未加载，会自动调用 {@link #loadSound(int)} 进行即时加载。
     * 注意：即时加载可能存在短暂延迟，首次播放的音效可能听不到。
     *
     * @param resId 音效资源 ID（R.raw.xxx）
     * @param loop  是否循环播放，true 表示无限循环，false 表示播放一次
     */
    public void playSound(int resId, boolean loop) {
        // 音效被禁用或 SoundPool 未初始化时，静默返回
        if (!enabled || soundPool == null) return;

        Integer soundId = soundMap.get(resId);
        if (soundId == null) {
            // 音效未预加载，尝试即时加载（可能存在延迟）
            loadSound(resId);
            soundId = soundMap.get(resId);
        }

        if (soundId != null) {
            // SoundPool.play 参数：左声道音量、右声道音量、优先级、循环次数(-1=无限)、播放速率
            soundPool.play(soundId, volume, volume, 1, loop ? -1 : 0, 1.0f);
        }
    }

    /**
     * 停止播放指定短音效。
     *
     * @param resId 音效资源 ID（R.raw.xxx）
     */
    public void stopSound(int resId) {
        Integer soundId = soundMap.get(resId);
        if (soundId != null) {
            soundPool.stop(soundId);
        }
    }

    /**
     * 播放背景音乐（默认循环）。
     *
     * @param resId 音乐资源 ID（R.raw.xxx）
     */
    public void playBackgroundMusic(int resId) {
        playBackgroundMusic(resId, true);
    }

    /**
     * 播放背景音乐，可指定是否循环。
     *
     * <p>播放新的背景音乐前会自动停止并释放当前正在播放的背景音乐，
     * 确保同一时刻只有一首背景音乐在播放。
     *
     * <p>背景音乐音量设置为当前音量的 50%（volume * 0.5f），
     * 这是一种常见做法，使背景音乐不盖过前景音效。
     *
     * @param resId 音乐资源 ID（R.raw.xxx）
     * @param loop  是否循环播放
     */
    public void playBackgroundMusic(int resId, boolean loop) {
        if (!enabled) return;

        // 先停止当前背景音乐，避免多个 MediaPlayer 同时播放
        stopBackgroundMusic();

        try {
            bgPlayer = MediaPlayer.create(context, resId);
            if (bgPlayer != null) {
                // 背景音乐音量衰减为当前音量的50%，避免盖过短音效
                bgPlayer.setVolume(volume * 0.5f, volume * 0.5f);
                bgPlayer.setLooping(loop);
                bgPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play background music: " + e.getMessage());
        }
    }

    /**
     * 停止并释放背景音乐播放器。
     *
     * <p>会先检查播放状态再停止，避免在未播放状态下调用 stop() 导致异常。
     * 停止后立即释放 MediaPlayer 资源，并将引用置 null。
     */
    public void stopBackgroundMusic() {
        if (bgPlayer != null) {
            try {
                // 先检查是否正在播放，避免 IllegalStateException
                if (bgPlayer.isPlaying()) {
                    bgPlayer.stop();
                }
                bgPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping background music: " + e.getMessage());
            }
            bgPlayer = null;
        }
    }

    /**
     * 暂停背景音乐播放。
     *
     * <p>仅在背景音乐正在播放时执行暂停操作，可通过 {@link #resumeBackgroundMusic()} 恢复。
     */
    public void pauseBackgroundMusic() {
        if (bgPlayer != null && bgPlayer.isPlaying()) {
            bgPlayer.pause();
        }
    }

    /**
     * 恢复背景音乐播放。
     *
     * <p>仅在背景音乐已暂停（未在播放）时执行恢复操作。
     */
    public void resumeBackgroundMusic() {
        if (bgPlayer != null && !bgPlayer.isPlaying()) {
            bgPlayer.start();
        }
    }

    /**
     * 释放所有音效资源。
     *
     * <p>包括：停止并释放背景音乐播放器、释放 SoundPool、清空音效映射表。
     * 调用后此 SoundManager 实例不再可用，如需继续使用需重新创建实例。
     */
    public void release() {
        stopBackgroundMusic();

        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundMap.clear();
    }
}
