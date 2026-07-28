package com.gamecenter.app.doudizhu;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.utils.SoundManager;

import java.util.List;
import java.util.Random;

public class DouDiZhuSoundManager {

    private final Context appContext;
    private final SoundManager soundManager;
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private float musicVolume = 0.5f;
    private float effectVolume = 0.75f;
    private float voiceVolume = 0.8f;

    private boolean soundEnabled = true;

    public DouDiZhuSoundManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.soundManager = new SoundManager(appContext);
        this.soundManager.setVolume(effectVolume);
        preload();
    }

    private void preload() {
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
        soundManager.loadSound(0);
    }

    private void play(int resId) {
        if (!soundEnabled) return;
        soundManager.playSound(resId);
    }

    private void playWithVolume(int resId, float volume) {
        if (!soundEnabled) return;
        float oldVolume = soundManager.getVolume();
        soundManager.setVolume(volume);
        soundManager.playSound(resId);
        soundManager.setVolume(oldVolume);
    }

    public void setSoundEnabled(boolean enabled) {
        this.soundEnabled = enabled;
        soundManager.setEnabled(enabled);
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setMusicVolume(float volume) {
        this.musicVolume = Math.max(0f, Math.min(1f, volume));
        MediaPlayer bgPlayer = getBackgroundMediaPlayer();
        if (bgPlayer != null) {
            bgPlayer.setVolume(this.musicVolume, this.musicVolume);
        }
    }

    public void setEffectVolume(float volume) {
        this.effectVolume = Math.max(0f, Math.min(1f, volume));
        soundManager.setVolume(this.effectVolume);
    }

    public void setVoiceVolume(float volume) {
        this.voiceVolume = Math.max(0f, Math.min(1f, volume));
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public float getEffectVolume() {
        return effectVolume;
    }

    public float getVoiceVolume() {
        return voiceVolume;
    }

    public void playBackgroundMusic() {
        // 斗地主不使用循环背景音乐，避免发牌音效重复播放
        // 仅保留该方法以兼容现有调用，实际不播放任何声音
    }

    public void stopBackgroundMusic() {
        soundManager.stopBackgroundMusic();
    }

    public void pauseBackgroundMusic() {
        MediaPlayer player = getBackgroundMediaPlayer();
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    public void resumeBackgroundMusic() {
        MediaPlayer player = getBackgroundMediaPlayer();
        if (player != null && !player.isPlaying()) {
            player.start();
        }
    }

    public void playBombEffect() {
        if (!soundEnabled) return;
        playWithVolume(0, effectVolume);
        handler.postDelayed(() -> playWithVolume(0, effectVolume * 0.6f), 150);
        handler.postDelayed(() -> playWithVolume(0, effectVolume * 0.3f), 300);
    }

    public void playRocketEffect() {
        if (!soundEnabled) return;
        playWithVolume(0, effectVolume);
        handler.postDelayed(() -> playWithVolume(0, effectVolume * 0.5f), 200);
    }

    public void playPlaneEffect() {
        if (!soundEnabled) return;
        playWithVolume(0, effectVolume);
    }

    public void playSpringEffect() {
        if (!soundEnabled) return;
        playWithVolume(0, effectVolume * 0.8f);
        handler.postDelayed(() -> playWithVolume(0, effectVolume * 0.6f), 200);
    }

    public void playWinEffect() {
        if (!soundEnabled) return;
        playWithVolume(0, effectVolume);
        handler.postDelayed(() -> playWithVolume(0, effectVolume * 0.5f), 300);
        handler.postDelayed(() -> playWithVolume(0, effectVolume * 0.7f), 600);
    }

    public void playLoseEffect() {
        if (!soundEnabled) return;
        playWithVolume(0, effectVolume);
        handler.postDelayed(() -> playWithVolume(0, effectVolume * 0.6f), 400);
    }

    public void playBidEffect(boolean call, int seatIndex) {
        bid(call, seatIndex);
    }

    public void playDealEffect() {
        if (!soundEnabled) return;
        for (int i = 0; i < 6; i++) {
            final int delay = i * 80;
            final float vol = effectVolume * (1.0f - i * 0.1f);
            handler.postDelayed(() -> playWithVolume(0, vol), delay);
        }
    }

    public void click() {
        play(0);
    }

    public void deal() {
        play(0);
    }

    public void turn() {
        play(0);
    }

    public void confirm() {
        play(0);
    }

    public void notice() {
        play(0);
    }

    public void bid(boolean call) {
        bid(call, 0);
    }

    public void bid(boolean call, int seatIndex) {
        playGendered(call ? "dz_q_" : "dz_bj_", seatIndex, call ? 0 : 0);
    }

    public void pass() {
        pass(0);
    }

    public void pass(int seatIndex) {
        if (isFemaleSeat(seatIndex)) {
            playByName("card_pass_w_4", 0);
            return;
        }
        playByName("card_pass_m_" + random.nextInt(4), 0);
    }

    public void win(boolean won) {
        play(won ? 0 : 0);
    }

    public void cards(List<Card> cards, CardType type) {
        cards(cards, type, 0);
    }

    public void cards(List<Card> cards, CardType type, int seatIndex) {
        if (type == null) {
            play(0);
            return;
        }
        switch (type) {
            case BOMB:
                playGendered("card_bomb_", seatIndex, 0);
                return;
            case JOKER_BOMB:
                playGendered("card_rocket_", seatIndex, 0);
                return;
            case AIRPLANE:
            case AIRPLANE_WITH_WINGS:
                playGendered("card_plane_", seatIndex, 0);
                return;
            case STRAIGHT:
                playGendered("card_shunzi_", seatIndex, 0);
                return;
            case STRAIGHT_PAIRS:
                playGendered("card_doubleline_", seatIndex, 0);
                return;
            case SINGLE:
                playRankSound("card_single_", cards, seatIndex);
                return;
            case PAIR:
                playRankSound("card_double_", cards, seatIndex);
                return;
            default:
                play(0);
        }
    }

    private void playRankSound(String prefix, List<Card> cards, int seatIndex) {
        if (cards == null || cards.isEmpty()) {
            play(0);
            return;
        }
        int weight = cards.get(0).getWeight();
        String suffix = isFemaleSeat(seatIndex) ? "_w" : "_m";
        int resId = appContext.getResources().getIdentifier(prefix + weight + suffix,
                "raw", appContext.getPackageName());
        if (resId == 0 && isFemaleSeat(seatIndex)) {
            resId = appContext.getResources().getIdentifier(prefix + weight + "_m",
                    "raw", appContext.getPackageName());
        }
        play(resId != 0 ? resId : 0);
    }

    private boolean isFemaleSeat(int seatIndex) {
        return seatIndex % 2 != 0;
    }

    private void playGendered(String resourcePrefix, int seatIndex, int fallbackResId) {
        playByName(resourcePrefix + (isFemaleSeat(seatIndex) ? "w" : "m"), fallbackResId);
    }

    private void playByName(String resourceName, int fallbackResId) {
        int resId = appContext.getResources().getIdentifier(resourceName, "raw", appContext.getPackageName());
        play(resId != 0 ? resId : fallbackResId);
    }

    private MediaPlayer getBackgroundMediaPlayer() {
        try {
            java.lang.reflect.Field field = SoundManager.class.getDeclaredField("bgPlayer");
            field.setAccessible(true);
            return (MediaPlayer) field.get(soundManager);
        } catch (Exception e) {
            return null;
        }
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        soundManager.release();
    }
}
