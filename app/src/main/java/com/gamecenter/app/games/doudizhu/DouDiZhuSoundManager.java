package com.gamecenter.app.games.doudizhu;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.gamecenter.app.R;
import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.CardType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DouDiZhuSoundManager {

    private final Context appContext;
    private final SoundPool soundPool;
    private final Map<Integer, Integer> sounds = new HashMap<>();

    public DouDiZhuSoundManager(Context context) {
        this.appContext = context.getApplicationContext();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build();
        preload();
    }

    private void preload() {
        load(R.raw.sound_click_button);
        load(R.raw.sound_sendpk);
        load(R.raw.card_pass_m_0);
        load(R.raw.dz_q_m);
        load(R.raw.dz_bj_m);
        load(R.raw.card_bomb_sound);
        load(R.raw.card_rocket_sound);
        load(R.raw.card_plane_sound);
        load(R.raw.card_shunzi_m);
        load(R.raw.card_doubleline_m);
        load(R.raw.sound_win);
        load(R.raw.sound_lose);
        load(R.raw.ui_turn);
        load(R.raw.ui_confirm);
        load(R.raw.ui_notice);
    }

    private void load(int resId) {
        if (!sounds.containsKey(resId)) {
            sounds.put(resId, soundPool.load(appContext, resId, 1));
        }
    }

    private void play(int resId) {
        Integer soundId = sounds.get(resId);
        if (soundId == null) {
            soundId = soundPool.load(appContext, resId, 1);
            sounds.put(resId, soundId);
        }
        soundPool.play(soundId, 0.75f, 0.75f, 1, 0, 1.0f);
    }

    public void click() {
        play(R.raw.sound_click_button);
    }

    public void deal() {
        play(R.raw.sound_sendpk);
    }

    public void turn() {
        play(R.raw.ui_turn);
    }

    public void confirm() {
        play(R.raw.ui_confirm);
    }

    public void notice() {
        play(R.raw.ui_notice);
    }

    public void bid(boolean call) {
        play(call ? R.raw.dz_q_m : R.raw.dz_bj_m);
    }

    public void pass() {
        play(R.raw.card_pass_m_0);
    }

    public void win(boolean won) {
        play(won ? R.raw.sound_win : R.raw.sound_lose);
    }

    public void cards(List<Card> cards, CardType type) {
        if (type == null) {
            play(R.raw.sound_sendpk);
            return;
        }
        switch (type) {
            case BOMB:
                play(R.raw.card_bomb_sound);
                return;
            case JOKER_BOMB:
                play(R.raw.card_rocket_sound);
                return;
            case AIRPLANE:
            case AIRPLANE_WITH_WINGS:
                play(R.raw.card_plane_sound);
                return;
            case STRAIGHT:
                play(R.raw.card_shunzi_m);
                return;
            case STRAIGHT_PAIRS:
                play(R.raw.card_doubleline_m);
                return;
            case SINGLE:
                playRankSound("card_single_", cards);
                return;
            case PAIR:
                playRankSound("card_double_", cards);
                return;
            default:
                play(R.raw.sound_sendpk);
        }
    }

    private void playRankSound(String prefix, List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            play(R.raw.sound_sendpk);
            return;
        }
        int weight = cards.get(0).getWeight();
        int resId = appContext.getResources().getIdentifier(prefix + weight + "_m",
                "raw", appContext.getPackageName());
        play(resId != 0 ? resId : R.raw.sound_sendpk);
    }

    public void release() {
        soundPool.release();
    }
}
