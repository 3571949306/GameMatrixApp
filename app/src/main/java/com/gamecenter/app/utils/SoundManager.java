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
 * 通用音效管理器
 * 提供游戏音效播放功能，支持音效池和背景音乐
 */
public class SoundManager {

    private static final String TAG = "SoundManager";
    private static final int MAX_STREAMS = 5;

    private final Context context;
    private SoundPool soundPool;
    private final Map<Integer, Integer> soundMap;
    private MediaPlayer bgPlayer;
    private boolean enabled = true;
    private float volume = 1.0f;

    public SoundManager(Context context) {
        this.context = context.getApplicationContext();
        this.soundMap = new HashMap<>();
        initSoundPool();
    }

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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    public float getVolume() {
        return volume;
    }

    public void loadSound(int resId) {
        if (!soundMap.containsKey(resId)) {
            int soundId = soundPool.load(context, resId, 1);
            soundMap.put(resId, soundId);
        }
    }

    public void playSound(int resId) {
        playSound(resId, false);
    }

    public void playSound(int resId, boolean loop) {
        if (!enabled || soundPool == null) return;

        Integer soundId = soundMap.get(resId);
        if (soundId == null) {
            loadSound(resId);
            soundId = soundMap.get(resId);
        }

        if (soundId != null) {
            soundPool.play(soundId, volume, volume, 1, loop ? -1 : 0, 1.0f);
        }
    }

    public void stopSound(int resId) {
        Integer soundId = soundMap.get(resId);
        if (soundId != null) {
            soundPool.stop(soundId);
        }
    }

    public void playBackgroundMusic(int resId) {
        playBackgroundMusic(resId, true);
    }

    public void playBackgroundMusic(int resId, boolean loop) {
        if (!enabled) return;

        stopBackgroundMusic();

        try {
            bgPlayer = MediaPlayer.create(context, resId);
            if (bgPlayer != null) {
                bgPlayer.setVolume(volume * 0.5f, volume * 0.5f);
                bgPlayer.setLooping(loop);
                bgPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play background music: " + e.getMessage());
        }
    }

    public void stopBackgroundMusic() {
        if (bgPlayer != null) {
            try {
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

    public void pauseBackgroundMusic() {
        if (bgPlayer != null && bgPlayer.isPlaying()) {
            bgPlayer.pause();
        }
    }

    public void resumeBackgroundMusic() {
        if (bgPlayer != null && !bgPlayer.isPlaying()) {
            bgPlayer.start();
        }
    }

    public void release() {
        stopBackgroundMusic();

        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        soundMap.clear();
    }
}
