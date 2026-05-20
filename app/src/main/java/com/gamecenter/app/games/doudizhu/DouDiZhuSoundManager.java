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

/**
 * 斗地主音效管理器。
 *
 * <p>负责游戏中所有音效的预加载与播放，基于 Android {@link SoundPool} 实现短音效的快速播放。</p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>管理音效资源的预加载与缓存，避免播放时延迟</li>
 *   <li>根据游戏事件（点击、发牌、出牌、炸弹、胜利等）播放对应音效</li>
 *   <li>根据牌型和牌面值动态匹配音效资源（如单牌/对子的点数语音）</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>使用 {@link SoundPool} 而非 {@link android.media.MediaPlayer}，因为 SoundPool 适合播放短促音效，延迟低</li>
 *   <li>最大并发流数设为 4，避免同时播放过多音效</li>
 *   <li>音效资源 ID 到 SoundPool 内部 ID 的映射通过 {@link HashMap} 缓存，首次播放未预加载的音效时自动加载</li>
 *   <li>单牌/对子音效通过资源名称动态查找（如 card_single_15_m 对应权重15的单牌）</li>
 * </ul>
 */
public class DouDiZhuSoundManager {

    /** 应用上下文（使用 ApplicationContext 避免 Activity 泄漏） */
    private final Context appContext;

    /** SoundPool 实例，用于短音效播放 */
    private final SoundPool soundPool;

    /**
     * 音效缓存映射：key = 资源ID（R.raw.xxx），value = SoundPool 加载后返回的 soundId。
     * 用于避免重复加载同一音效资源。
     */
    private final Map<Integer, Integer> sounds = new HashMap<>();

    /**
     * 构造音效管理器。
     *
     * <p>初始化 SoundPool 并预加载所有常用音效资源。</p>
     *
     * @param context 上下文（内部会转为 ApplicationContext，防止 Activity 泄漏）
     */
    public DouDiZhuSoundManager(Context context) {
        this.appContext = context.getApplicationContext();
        // 配置 SoundPool：游戏用途，最大4路并发
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

    /**
     * 预加载所有常用音效资源到 SoundPool 缓存中。
     *
     * <p>预加载的音效包括：</p>
     * <ul>
     *   <li>UI 音效：按钮点击、回合提示、确认、通知</li>
     *   <li>发牌/出牌音效：发牌、出牌</li>
     *   <li>叫地主音效：叫地主、不叫</li>
     *   <li>特殊牌型音效：炸弹、火箭（王炸）、飞机、顺子、连对</li>
     *   <li>胜负音效：胜利、失败</li>
     * </ul>
     */
    private void preload() {
        load(R.raw.sound_click_button);   // 按钮点击音效
        load(R.raw.sound_sendpk);         // 发牌/出牌通用音效
        load(R.raw.card_pass_m_0);        // "不出"音效
        load(R.raw.dz_q_m);              // "叫地主"音效
        load(R.raw.dz_bj_m);             // "不叫"音效
        load(R.raw.card_bomb_sound);      // 炸弹音效
        load(R.raw.card_rocket_sound);    // 火箭（王炸）音效
        load(R.raw.card_plane_sound);     // 飞机音效
        load(R.raw.card_shunzi_m);        // 顺子音效
        load(R.raw.card_doubleline_m);    // 连对音效
        load(R.raw.sound_win);            // 胜利音效
        load(R.raw.sound_lose);           // 失败音效
        load(R.raw.ui_turn);              // 回合提示音效
        load(R.raw.ui_confirm);           // 确认音效
        load(R.raw.ui_notice);            // 通知音效
    }

    /**
     * 加载单个音效资源到 SoundPool 并缓存映射。
     *
     * @param resId 音效资源 ID（如 R.raw.sound_click_button）
     */
    private void load(int resId) {
        if (!sounds.containsKey(resId)) {
            sounds.put(resId, soundPool.load(appContext, resId, 1));
        }
    }

    /**
     * 播放指定资源 ID 的音效。
     *
     * <p>如果该音效尚未加载，会先动态加载再播放。
     * 播放音量为 0.75（左右声道相同），优先级为 1，不循环，正常速率。</p>
     *
     * @param resId 音效资源 ID
     */
    private void play(int resId) {
        Integer soundId = sounds.get(resId);
        if (soundId == null) {
            // 音效未预加载，动态加载并缓存
            soundId = soundPool.load(appContext, resId, 1);
            sounds.put(resId, soundId);
        }
        soundPool.play(soundId, 0.75f, 0.75f, 1, 0, 1.0f);
    }

    /** 播放按钮点击音效 */
    public void click() {
        play(R.raw.sound_click_button);
    }

    /** 播放发牌音效 */
    public void deal() {
        play(R.raw.sound_sendpk);
    }

    /** 播放回合提示音效 */
    public void turn() {
        play(R.raw.ui_turn);
    }

    /** 播放确认音效 */
    public void confirm() {
        play(R.raw.ui_confirm);
    }

    /** 播放通知音效 */
    public void notice() {
        play(R.raw.ui_notice);
    }

    /**
     * 播放叫地主相关音效。
     *
     * @param call true 表示"叫地主"音效，false 表示"不叫"音效
     */
    public void bid(boolean call) {
        play(call ? R.raw.dz_q_m : R.raw.dz_bj_m);
    }

    /** 播放"不出"音效 */
    public void pass() {
        play(R.raw.card_pass_m_0);
    }

    /**
     * 播放胜负音效。
     *
     * @param won true 播放胜利音效，false 播放失败音效
     */
    public void win(boolean won) {
        play(won ? R.raw.sound_win : R.raw.sound_lose);
    }

    /**
     * 根据出牌的牌型播放对应音效。
     *
     * <p>特殊牌型有专属音效（炸弹、火箭、飞机、顺子、连对），
     * 单牌和对子会根据牌面值动态查找对应语音资源（如 card_single_15_m），
     * 其他牌型使用通用出牌音效。</p>
     *
     * @param cards 出的牌列表（用于确定单牌/对子的点数）
     * @param type  牌型（决定播放哪种音效）
     */
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
                // 单牌：根据牌面权重播放对应点数语音
                playRankSound("card_single_", cards);
                return;
            case PAIR:
                // 对子：根据牌面权重播放对应点数语音
                playRankSound("card_double_", cards);
                return;
            default:
                play(R.raw.sound_sendpk);
        }
    }

    /**
     * 根据牌面权重播放对应点数的语音音效。
     *
     * <p>音效资源命名规则：{prefix}{weight}_m，例如 card_single_15_m 表示权重15的单牌语音。
     * 如果找不到对应的资源文件，则回退到通用出牌音效。</p>
     *
     * @param prefix 资源名称前缀（如 "card_single_" 或 "card_double_"）
     * @param cards  出的牌列表（取第一张牌的权重值）
     */
    private void playRankSound(String prefix, List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            play(R.raw.sound_sendpk);
            return;
        }
        // 取第一张牌的权重值，拼接资源名称动态查找
        int weight = cards.get(0).getWeight();
        int resId = appContext.getResources().getIdentifier(prefix + weight + "_m",
                "raw", appContext.getPackageName());
        // 找到对应资源则播放，否则回退到通用出牌音效
        play(resId != 0 ? resId : R.raw.sound_sendpk);
    }

    /**
     * 释放 SoundPool 资源。
     *
     * <p>应在 Activity/Fragment 销毁时调用，释放所有音效资源。</p>
     */
    public void release() {
        soundPool.release();
    }
}
