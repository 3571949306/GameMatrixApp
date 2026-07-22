package com.gamecenter.app.games.doudizhu;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.CardType;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.utils.GameRuleUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 斗地主单机游戏主控制器。
 *
 * <p>管理斗地主单机模式的完整游戏生命周期，包括发牌、叫地主、出牌、胜负判定等核心流程。
 * 使用横屏布局（activity_doudizhu.xml），通过 {@link DouDiZhuTableView} 自定义视图渲染游戏桌面。</p>
 *
 * <p>你可以把这类比为一个"游戏裁判"——它不亲自打牌，但负责发牌、判定规则、
 * 轮流叫玩家行动，并在有人赢的时候宣布结果。</p>
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>游戏状态机管理：等待→发牌→叫地主→出牌→游戏结束（就像一局牌的"流程图"）</li>
 *   <li>玩家交互处理：出牌、不出、提示、叫地主等操作</li>
 *   <li>AI 决策调度：延迟执行 AI 的叫地主和出牌决策（模拟"思考"过程）</li>
 *   <li>游戏规则校验：牌型合法性、出牌大小比较</li>
 *   <li>UI 状态同步：按钮显隐、状态栏更新、桌面刷新</li>
 *   <li>记牌器维护：跟踪剩余牌数（帮玩家算牌）</li>
 * </ul>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>使用 Handler + Runnable 实现异步 AI 思考延迟，避免阻塞主线程
 *       （就像让AI"假装在想"，而不是瞬间出牌让游戏体验不自然）</li>
 *   <li>玩家索引固定：0=玩家，1=左AI，2=右AI，通过取模实现轮转
 *       （取模就像一个环形跑道，0→1→2→0 循环往复）</li>
 *   <li>连续两人"不出"时清空桌面，最后出牌者获得自由出牌权
 *       （这是斗地主的核心规则：两个人都不要，出牌权就回到上一个出牌的人）</li>
 *   <li>叫地主评估算法基于手牌中的王炸、炸弹、大牌数量打分
 *       （就像评估一手牌的"战斗力"，分越高越有底气当地主）</li>
 * </ul>
 */
public class DouDiZhuActivity extends AppCompatActivity {

    // ============ 常量定义 ============
    // 这些数字就像游戏中的"状态标签"，用来标记当前处于哪个阶段

    /** 游戏状态：等待开始（还没开始打牌） */
    private static final int STATE_WAITING = 0;
    /** 游戏状态：发牌中（正在把牌发给每个人） */
    private static final int STATE_DEALING = 1;
    /** 游戏状态：叫地主阶段（大家轮流决定要不要当地主） */
    private static final int STATE_BIDDING = 2;
    /** 游戏状态：出牌阶段（正式开始打牌了） */
    private static final int STATE_PLAYING = 3;
    /** 游戏状态：游戏结束（有人出完牌了，分出胜负） */
    private static final int STATE_GAME_OVER = 4;

    /** 玩家索引：本地玩家（就是屏幕前的你） */
    public static final int PLAYER_INDEX = 0;
    /** 玩家索引：左方AI（你左边的电脑对手） */
    public static final int LEFT_AI_INDEX = 1;
    /** 玩家索引：右方AI（你右边的电脑对手） */
    public static final int RIGHT_AI_INDEX = 2;

    /** 地主状态：未确定（还没开始叫地主） */
    private static final int LANDLORD_NONE = 0;
    /** 地主状态：农民（和另一个农民组队打地主） */
    private static final int LANDLORD_FARMER = 1;
    /** 地主状态：地主（一个人打两个，但多拿3张底牌） */
    private static final int LANDLORD_LORD = 2;

    /** AI 思考延迟时间（毫秒），模拟真实思考过程。
     *  <p>由难度系统配置：简单 1800ms / 普通 1200ms / 困难 600ms。
     *  延迟越长 AI 看起来"想得越久"，简单难度给玩家更多反应时间。</p> */
    private long aiThinkingDelay = 1200L;

    /** AI 决策难度因子（由难度系统配置）。
     *  <p>简单 0.6（高随机性、易出错）/ 普通 1.0（标准）/ 困难 1.5（更激进）。
     *  传入 {@link AIBot#decidePlay(List, List, GameContext, float)} 影响决策质量。</p> */
    private float difficultyFactor = 1.0f;

    /** 最高分持久化存储（斗地主不继承 BaseGameActivity，手动实例化） */
    private GameUsageStore usageStore;

    // ============ 界面组件 ============
    // 这些都是屏幕上能看到的各种按钮、文字、进度条等UI元素

    /** 桌面自定义视图，负责所有卡牌的绘制和动画（整个游戏画面的"画布"） */
    private DouDiZhuTableView tableView;

    /** 叫地主按钮组容器 */
    private LinearLayout bidButtonLayout;
    /** 出牌按钮组容器 */
    private LinearLayout playButtonLayout;
    /** 叫分按钮组容器 */
    private LinearLayout scoreButtonLayout;
    /** 按钮区域总容器 */
    private View buttonContainer;

    /** 叫地主按钮 */
    private Button btnCallLandlord;
    /** 不叫按钮 */
    private Button btnNoCall;
    /** 出牌按钮 */
    private Button btnPlayCard;
    /** 提示按钮 */
    private Button btnHint;
    /** 不出按钮 */
    private Button btnPass;
    /** 叫0分按钮 */
    private Button btnScore0, btnScore1, btnScore2, btnScore3;

    /** 顶部状态栏容器 */
    private LinearLayout topStatusBar;
    /** 地主身份指示器 */
    private TextView tvLandlordIndicator;
    /** 回合指示器 */
    private TextView tvTurnIndicator;

    /** 游戏结束对话框 */
    private LinearLayout gameOverDialog;
    /** 游戏结束标题 */
    private TextView tvGameOverTitle;
    /** 游戏结果文本 */
    private TextView tvGameOverResult;
    /** 得分详情 */
    private TextView tvScoreDetail;
    /** 再来一局按钮 */
    private Button btnPlayAgain;
    /** 退出按钮 */
    private Button btnExit;

    /** 加载进度条 */
    private ProgressBar progressLoading;

    // ============ 游戏数据 ============
    // 这些是游戏运行时的核心数据，记录着每个人手里有什么牌、出了什么牌

    /** 玩家手牌（你手里的牌） */
    private List<Card> playerHandCards;
    /** 左AI手牌（左边电脑手里的牌） */
    private List<Card> leftAIHandCards;
    /** 右AI手牌（右边电脑手里的牌） */
    private List<Card> rightAIHandCards;
    /** 底牌（3张，地主获得——就像"额外奖励"） */
    private List<Card> bottomCards;

    /** 玩家上一轮出的牌 */
    private List<Card> playerPlayedCards;
    /** 左AI上一轮出的牌 */
    private List<Card> leftAIPlayedCards;
    /** 右AI上一轮出的牌 */
    private List<Card> rightAIPlayedCards;

    /** 各玩家是否选择"不出"的标志 [玩家, 左AI, 右AI]
     *  true表示该玩家本轮选择了"不要" */
    private boolean[] playerPassed = new boolean[3];

    /** 各玩家的地主状态 [玩家, 左AI, 右AI]
     *  0=未确定, 1=农民, 2=地主 */
    private int[] landlordStatus = new int[3];
    /** 地主玩家索引，-1 表示未确定（还没人叫地主） */
    private int landlordPlayerIndex = -1;

    /** 当前游戏状态（对应上面的 STATE_WAITING 等常量） */
    private int gameState = STATE_WAITING;
    /** 当前轮到谁出牌（0/1/2 三个位置轮流） */
    private int currentTurn = PLAYER_INDEX;
    /** 最后实际出牌（非过）的玩家索引，用于判断是否清空桌面
     *  比如你出了牌，左AI和右AI都"不要"，桌面就该清空，你又获得出牌权 */
    private int lastPlayerWhoPlayed = -1;

    /** 当前叫的分数（叫分模式） */
    private int currentBidScore = 0;
    /** 基础分数 */
    private int baseScore = 1;

    /** 主线程 Handler，用于延迟执行 AI 动作
     *  Handler就像一个"定时器"，可以在指定时间后执行某段代码 */
    private Handler handler = new Handler(Looper.getMainLooper());

    /** 当前挂起的 AI 思考 Runnable，用于取消前一个未执行的任务
     *  防止AI还没"想完"就被安排了新任务 */
    private Runnable aiThinkingRunnable;
    /** 音效管理器 */
    private DouDiZhuSoundManager soundManager;

    /** 新手引导序列（首次进入斗地主时弹出，4 步引导） */
    private com.gamecenter.app.ui.onboarding.CoachmarkSequence onboardingSequence;

    // ============ 生命周期 ============

    /**
     * Activity 创建入口。
     *
     * <p>初始化音效管理器、界面组件、事件监听器，并自动开始一局新游戏。
     * 这是Activity的"出生方法"，Activity一创建就会自动调用。</p>
     *
     * @param savedInstanceState 保存的实例状态（此处未使用，比如屏幕旋转时会用到）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Android 16+ (API 36) 将忽略 manifest 中的 android:screenOrientation，
        // 需在运行时强制锁定横屏。
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_doudizhu);
        soundManager = new DouDiZhuSoundManager(this);
        // 音效开关从 SettingsManager 持久化读取，同步到 DouDiZhuSoundManager 内部状态
        soundManager.setSoundEnabled(SettingsManager.getInstance(this).shouldPlayGameSound());

        // 实例化最高分持久化存储（斗地主未继承 BaseGameActivity，需手动创建）
        usageStore = new GameUsageStore(this);

        // 读取菜单页传入的难度索引（0=简单 / 1=普通 / 2=困难），配置 AI 思考延迟与决策因子
        int difficultyIndex = getIntent().getIntExtra("game_difficulty_index", 1);
        applyDifficulty(difficultyIndex);

        initViews();
        initListeners();
        startNewGame();

        if (soundManager != null && SettingsManager.getInstance(this).shouldPlayGameSound()) {
            soundManager.playBackgroundMusic();
        }

        // 首次进入触发新手引导（Spec §6：U2 免登录上手）
        // 延迟 900ms 是为了让 startNewGame 内的 showBidUI 先把 buttonContainer/topStatusBar 显示出来，
        // 否则 CoachmarkSequence 找不到目标 View 的尺寸
        onboardingSequence = new com.gamecenter.app.ui.onboarding.CoachmarkSequence(
                this,
                com.gamecenter.app.ui.onboarding.DoudizhuOnboarding.steps,
                com.gamecenter.app.ui.onboarding.DoudizhuOnboarding.STORAGE_KEY
        );
        getWindow().getDecorView().postDelayed(() -> onboardingSequence.start(), 900L);
    }

    /**
     * Activity 销毁时清理资源。
     *
     * <p>移除所有挂起的 Handler 回调（防止内存泄漏——就像走的时候要把灯关了），
     * 释放音效资源。</p>
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (onboardingSequence != null) {
            onboardingSequence.destroy();
            onboardingSequence = null;
        }
        if (soundManager != null) {
            soundManager.stopBackgroundMusic();
            soundManager.release();
            soundManager = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (soundManager != null) {
            soundManager.pauseBackgroundMusic();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从 SettingsManager 读取持久化的音效开关，同步到 soundManager 内部状态
        boolean soundOn = SettingsManager.getInstance(this).shouldPlayGameSound();
        if (soundManager != null) {
            soundManager.setSoundEnabled(soundOn);
            if (soundOn) {
                soundManager.resumeBackgroundMusic();
            }
        }
    }

    // ============ 初始化方法 ============

    /**
     * 初始化所有界面组件的引用。
     *
     * <p>通过 findViewById 绑定布局中的所有 View 组件，
     * 包括桌面视图、按钮组、状态栏、游戏结束对话框等。</p>
     */
    private void initViews() {
        tableView = findViewById(R.id.tableView);

        bidButtonLayout = findViewById(R.id.bidButtonLayout);
        playButtonLayout = findViewById(R.id.playButtonLayout);
        scoreButtonLayout = findViewById(R.id.scoreButtonLayout);
        buttonContainer = findViewById(R.id.buttonContainer);

        btnCallLandlord = findViewById(R.id.btnCallLandlord);
        btnNoCall = findViewById(R.id.btnNoCall);
        btnPlayCard = findViewById(R.id.btnPlayCard);
        btnHint = findViewById(R.id.btnHint);
        btnPass = findViewById(R.id.btnPass);

        btnScore0 = findViewById(R.id.btnScore0);
        btnScore1 = findViewById(R.id.btnScore1);
        btnScore2 = findViewById(R.id.btnScore2);
        btnScore3 = findViewById(R.id.btnScore3);

        topStatusBar = findViewById(R.id.topStatusBar);
        tvLandlordIndicator = findViewById(R.id.tvLandlordIndicator);
        tvTurnIndicator = findViewById(R.id.tvTurnIndicator);

        gameOverDialog = findViewById(R.id.gameOverDialog);
        tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvGameOverResult = findViewById(R.id.tvGameOverResult);
        tvScoreDetail = findViewById(R.id.tvScoreDetail);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        btnExit = findViewById(R.id.btnExit);

        progressLoading = findViewById(R.id.progressLoading);
    }

    /**
     * 初始化所有按钮和触摸事件的监听器。
     *
     * <p>绑定叫地主/不叫、出牌/提示/不出、叫分按钮、游戏结束按钮，
     * 以及桌面视图的卡牌触摸和桌面点击回调。</p>
     */
    private void initListeners() {
        btnCallLandlord.setOnClickListener(v -> onCallLandlord());
        btnNoCall.setOnClickListener(v -> onNoCall());

        btnPlayCard.setOnClickListener(v -> onPlayCard());
        btnHint.setOnClickListener(v -> onHint());
        btnPass.setOnClickListener(v -> onPass());

        btnScore0.setOnClickListener(v -> onBidScore(0));
        btnScore1.setOnClickListener(v -> onBidScore(1));
        btnScore2.setOnClickListener(v -> onBidScore(2));
        btnScore3.setOnClickListener(v -> onBidScore(3));

        btnPlayAgain.setOnClickListener(v -> {
            gameOverDialog.setVisibility(View.GONE);
            startNewGame();
        });
        btnExit.setOnClickListener(v -> finish());

        tableView.setOnCardTouchListener(cards -> {
            // 玩家选择了卡牌，可在此处扩展UI反馈
        });

        tableView.setOnTableClickListener(() -> {
            // 桌面被点击，可在此处扩展交互（如取消选择）
        });
    }

    // ============ 游戏流程控制 ============

    /**
     * 开始一局新游戏。
     *
     * <p>重置所有游戏数据，执行洗牌发牌，然后进入叫地主阶段。
     * 发牌通过 Handler 延迟 500ms 执行，确保 UI 先更新加载状态
     * （就像先让"发牌中"的提示显示出来，再开始发牌）。</p>
     */
    private void startNewGame() {
        // 重置游戏状态和数据
        gameState = STATE_DEALING;
        currentTurn = PLAYER_INDEX;
        lastPlayerWhoPlayed = -1;
        currentBidScore = 0;
        landlordPlayerIndex = -1;
        landlordStatus = new int[]{0, 0, 0};

        playerHandCards = new ArrayList<>();
        leftAIHandCards = new ArrayList<>();
        rightAIHandCards = new ArrayList<>();
        bottomCards = new ArrayList<>();

        playerPlayedCards = new ArrayList<>();
        leftAIPlayedCards = new ArrayList<>();
        rightAIPlayedCards = new ArrayList<>();

        playerPassed = new boolean[]{false, false, false};

        progressLoading.setVisibility(View.VISIBLE);

        // 延迟执行发牌，让加载进度条先显示
        handler.postDelayed(() -> {
            // 洗牌并发牌：返回 [玩家手牌, 左AI手牌, 右AI手牌, 底牌]
            List<Card>[] dealtCards = GameRuleUtil.shuffleAndDeal();
            playerHandCards = dealtCards[0];
            leftAIHandCards = dealtCards[1];
            rightAIHandCards = dealtCards[2];
            bottomCards = dealtCards[3];

            // 更新桌面视图
            tableView.setPlayerHandCards(playerHandCards);
            tableView.setBottomCards(bottomCards);
            tableView.setAICardCounts(leftAIHandCards.size(), rightAIHandCards.size());
            tableView.setPassStates(false, false);
            updateCardCounter();
            if (soundManager != null) soundManager.playDealEffect();

            progressLoading.setVisibility(View.GONE);

            // 进入叫地主阶段，玩家先叫
            gameState = STATE_BIDDING;
            showBidUI();

            currentTurn = PLAYER_INDEX;
            updateTurnIndicator();
        }, 500);
    }

    /**
     * 显示叫地主阶段的 UI。
     *
     * <p>显示叫地主按钮组，隐藏出牌和叫分按钮组。</p>
     */
    private void showBidUI() {
        buttonContainer.setVisibility(View.VISIBLE);
        bidButtonLayout.setVisibility(View.VISIBLE);
        playButtonLayout.setVisibility(View.GONE);
        topStatusBar.setVisibility(View.VISIBLE);
        scoreButtonLayout.setVisibility(View.GONE);

        updateLandlordIndicator();
        updateTurnIndicator();
    }

    /**
     * 显示叫分阶段的 UI（另一种叫地主方式）。
     *
     * <p>显示叫分按钮组，隐藏叫地主和出牌按钮组。</p>
     */
    private void showBidScoreUI() {
        buttonContainer.setVisibility(View.VISIBLE);
        bidButtonLayout.setVisibility(View.GONE);
        playButtonLayout.setVisibility(View.GONE);
        scoreButtonLayout.setVisibility(View.VISIBLE);
        topStatusBar.setVisibility(View.VISIBLE);
    }

    /**
     * 显示出牌阶段的 UI。
     *
     * <p>显示出牌按钮组（出牌/提示/不出），隐藏叫地主和叫分按钮组。</p>
     */
    private void showPlayUI() {
        buttonContainer.setVisibility(View.VISIBLE);
        bidButtonLayout.setVisibility(View.GONE);
        playButtonLayout.setVisibility(View.VISIBLE);
        scoreButtonLayout.setVisibility(View.GONE);
        topStatusBar.setVisibility(View.VISIBLE);

        updateTurnIndicator();
    }

    /**
     * 隐藏所有操作按钮。
     *
     * <p>通常在游戏结束或等待 AI 操作时调用。</p>
     */
    private void hideAllButtons() {
        buttonContainer.setVisibility(View.GONE);
        bidButtonLayout.setVisibility(View.GONE);
        playButtonLayout.setVisibility(View.GONE);
        scoreButtonLayout.setVisibility(View.GONE);
    }

    /**
     * 播放按钮点击音效。
     */
    private void playClickSound() {
        if (soundManager != null) {
            soundManager.click();
        }
    }

    /**
     * 根据牌型播放对应的增强版音效。
     *
     * <p>炸弹、火箭、飞机等特殊牌型使用增强音效，
     * 普通牌型使用基础语音音效。</p>
     */
    private void playCardEffect(List<Card> cards, CardType type, int seatIndex) {
        if (soundManager == null || type == null) return;
        switch (type) {
            case BOMB:
                soundManager.playBombEffect();
                break;
            case JOKER_BOMB:
                soundManager.playRocketEffect();
                break;
            case AIRPLANE:
            case AIRPLANE_WITH_WINGS:
                soundManager.playPlaneEffect();
                break;
            default:
                soundManager.cards(cards, type, seatIndex);
                break;
        }
    }

    /**
     * 创建一副完整牌的记牌器初始计数数组。
     *
     * <p>记牌器就像一个"剩余牌数统计表"：
     * 索引 0-12 对应 3~K（各4张），索引 13 对应小王（1张），索引 14 对应大王（1张）。
     * 一副牌54张，每种普通牌4张，大小王各1张。</p>
     *
     * @return 初始计数数组，长度15
     */
    private int[] createFullDeckCounter() {
        int[] counts = new int[15];
        for (int i = 0; i < 13; i++) {
            counts[i] = 4;
        }
        counts[13] = 1;
        counts[14] = 1;
        return counts;
    }

    /**
     * 计算卡牌在记牌器数组中的索引。
     *
     * @param card 卡牌
     * @return 记牌器索引，无效卡牌返回 -1
     */
    private int rankCounterIndex(Card card) {
        if (card == null) return -1;
        int weight = card.getWeight();
        if (weight >= Rank.THREE.getWeight() && weight <= Rank.BIG_JOKER.getWeight()) {
            return weight - Rank.THREE.getWeight();
        }
        return -1;
    }

    /**
     * 从记牌器计数数组中减去指定卡牌列表的计数。
     *
     * @param counts 记牌器计数数组（原地修改）
     * @param cards  要减去的卡牌列表
     */
    private void subtractCardsFromCounter(int[] counts, List<Card> cards) {
        if (counts == null || cards == null) return;
        for (Card card : cards) {
            int index = rankCounterIndex(card);
            if (index >= 0 && index < counts.length) {
                counts[index] = Math.max(0, counts[index] - 1);
            }
        }
    }

    /**
     * 更新记牌器显示。
     *
     * <p>从一副完整牌中减去玩家手牌和所有已出的牌，得到剩余牌数。</p>
     */
    private void updateCardCounter() {
        if (tableView == null) return;
        int[] counts = createFullDeckCounter();
        subtractCardsFromCounter(counts, playerHandCards);
        subtractCardsFromCounter(counts, playerPlayedCards);
        subtractCardsFromCounter(counts, leftAIPlayedCards);
        subtractCardsFromCounter(counts, rightAIPlayedCards);
        tableView.setCardCounterCounts(counts);
    }

    // ============ 叫地主逻辑 ============

    /**
     * 玩家点击"叫地主"按钮的处理。
     *
     * <p>校验当前状态后，将玩家设为地主并进入出牌阶段。</p>
     */
    private void onCallLandlord() {
        playClickSound();
        if (gameState != STATE_BIDDING || currentTurn != PLAYER_INDEX) {
            return;
        }
        if (soundManager != null) soundManager.bid(true, PLAYER_INDEX);

        setLandlord(PLAYER_INDEX);
        startPlayingPhase();
    }

    /**
     * 玩家点击"不叫"按钮的处理。
     *
     * <p>校验当前状态后，轮转到下一个 AI 进行叫地主决策。</p>
     */
    private void onNoCall() {
        playClickSound();
        if (gameState != STATE_BIDDING || currentTurn != PLAYER_INDEX) {
            return;
        }
        if (soundManager != null) soundManager.bid(false, PLAYER_INDEX);

        currentTurn = LEFT_AI_INDEX;
        updateTurnIndicator();
        scheduleAIAction();
    }

    /**
     * 玩家选择叫分的处理（另一种叫地主方式）。
     *
     * <p>叫分必须高于当前分数。叫3分直接开始游戏，否则继续让 AI 叫。
     * 如果叫分不高于当前分数，提示用户。</p>
     *
     * @param score 玩家选择的分数（0-3）
     */
    private void onBidScore(int score) {
        playClickSound();
        if (gameState != STATE_BIDDING || currentTurn != PLAYER_INDEX) {
            return;
        }

        if (score > currentBidScore) {
            currentBidScore = score;
            landlordPlayerIndex = PLAYER_INDEX;
            Toast.makeText(this, getString(R.string.game_doudizhu_you_bid, score), Toast.LENGTH_SHORT).show();

            if (score == 3) {
                setLandlord(PLAYER_INDEX);
                startPlayingPhase();
            } else {
                currentTurn = LEFT_AI_INDEX;
                updateTurnIndicator();
                scheduleAIAction();
            }
        } else {
            Toast.makeText(this, R.string.game_doudizhu_must_bid_higher, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 设置指定玩家为地主，并分配底牌。
     *
     * <p>将指定玩家标记为地主，其余玩家标记为农民。
     * 将3张底牌加入地主手牌并重新排序。</p>
     *
     * @param playerIndex 地主玩家索引
     */
    private void setLandlord(int playerIndex) {
        landlordPlayerIndex = playerIndex;
        landlordStatus[playerIndex] = LANDLORD_LORD;

        // 其余玩家设为农民
        for (int i = 0; i < 3; i++) {
            if (i != playerIndex) {
                landlordStatus[i] = LANDLORD_FARMER;
            }
        }

        // 地主获得底牌并重新排序
        switch (playerIndex) {
            case PLAYER_INDEX:
                playerHandCards.addAll(bottomCards);
                GameRuleUtil.sortCardsByWeightAscending(playerHandCards);
                tableView.setPlayerHandCards(playerHandCards);
                break;
            case LEFT_AI_INDEX:
                leftAIHandCards.addAll(bottomCards);
                GameRuleUtil.sortCardsByWeightAscending(leftAIHandCards);
                break;
            case RIGHT_AI_INDEX:
                rightAIHandCards.addAll(bottomCards);
                GameRuleUtil.sortCardsByWeightAscending(rightAIHandCards);
                break;
        }

        // 更新身份标签
        String[] labels = new String[3];
        labels[PLAYER_INDEX] = (landlordStatus[PLAYER_INDEX] == LANDLORD_LORD) ? getString(R.string.game_doudizhu_role_you_landlord) : getString(R.string.game_doudizhu_role_you_farmer);
        labels[LEFT_AI_INDEX] = (landlordStatus[LEFT_AI_INDEX] == LANDLORD_LORD) ? getString(R.string.game_doudizhu_role_left_ai_landlord) : getString(R.string.game_doudizhu_role_left_ai_farmer);
        labels[RIGHT_AI_INDEX] = (landlordStatus[RIGHT_AI_INDEX] == LANDLORD_LORD) ? getString(R.string.game_doudizhu_role_right_ai_landlord) : getString(R.string.game_doudizhu_role_right_ai_farmer);
        tableView.setPlayerLabels(labels);

        tableView.setAllLandlordStatus(landlordStatus);
        updateCardCounter();
        updateLandlordIndicator();
    }

    /**
     * 进入出牌阶段。
     *
     * <p>地主先出牌，清空所有出牌记录和"不出"标志，
     * 切换到出牌 UI，如果地主是 AI 则调度 AI 出牌。</p>
     */
    private void startPlayingPhase() {
        gameState = STATE_PLAYING;

        // 地主先手
        currentTurn = landlordPlayerIndex;
        lastPlayerWhoPlayed = landlordPlayerIndex;

        playerPassed = new boolean[]{false, false, false};
        clearAllPlayedCards();
        tableView.setPassStates(false, false);
        updateCardCounter();

        showPlayUI();

        // 如果地主是 AI，调度 AI 出牌
        if (currentTurn != PLAYER_INDEX) {
            scheduleAIAction();
        }
    }

    // ============ 出牌逻辑 ============

    /**
     * 玩家点击"出牌"按钮的处理。
     *
     * <p>执行以下校验和操作：</p>
     * <ol>
     *   <li>校验当前状态和轮次</li>
     *   <li>获取选中的牌，检查是否为空</li>
     *   <li>校验牌型合法性（{@link CardType#ERROR} 表示不合法）</li>
     *   <li>校验是否能打过上家的牌</li>
     *   <li>执行出牌：从手牌移除、播放音效和动画、更新 UI</li>
     *   <li>检查获胜条件</li>
     * </ol>
     */
    private void onPlayCard() {
        playClickSound();
        if (gameState != STATE_PLAYING || currentTurn != PLAYER_INDEX) {
            return;
        }

        List<Card> selectedCards = tableView.getSelectedCards();

        if (selectedCards.isEmpty()) {
            Toast.makeText(this, R.string.game_doudizhu_select_cards, Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取上家出的牌（用于比较大小）
        List<Card> previousCards = getLastPlayedCards(lastPlayerWhoPlayed);

        // 校验牌型合法性
        CardType selectedType = GameRuleUtil.getCardType(selectedCards);
        if (selectedType == CardType.ERROR) {
            Toast.makeText(this, R.string.game_doudizhu_invalid_card_type, Toast.LENGTH_SHORT).show();
            return;
        }

        // 校验是否能打过上家
        if (!GameRuleUtil.canPlayPass(selectedCards, previousCards)) {
            Toast.makeText(this, R.string.game_doudizhu_cannot_beat, Toast.LENGTH_SHORT).show();
            return;
        }

        // 出牌成功：重置"不出"标志，记录最后出牌者
        playerPassed = new boolean[]{false, false, false};
        lastPlayerWhoPlayed = PLAYER_INDEX;

        playCardEffect(selectedCards, selectedType, PLAYER_INDEX);
        playerPlayedCards = new ArrayList<>(selectedCards);
        removeCardsFromHand(playerHandCards, selectedCards);

        // 播放出牌动画，动画完成后更新 UI
        tableView.playCardAnim(selectedCards, () -> {
            tableView.setPlayerHandCards(playerHandCards);
            tableView.setPlayerPlayedCards(playerPlayedCards);
            tableView.setAICardCounts(leftAIHandCards.size(), rightAIHandCards.size());
            tableView.setPassStates(playerPassed[LEFT_AI_INDEX], playerPassed[RIGHT_AI_INDEX]);
            updateCardCounter();
            tableView.clearSelection();
            if (checkWinCondition(PLAYER_INDEX)) {
                return;
            }
            switchToNextPlayer();
        });
    }

    /**
     * 玩家点击"提示"按钮的处理。
     *
     * <p>查找能打过上家的牌组组合，自动选中第一组并提示牌型名称。
     * 如果没有能打过的牌，提示玩家选择"不出"。</p>
     */
    private void onHint() {
        playClickSound();
        if (gameState != STATE_PLAYING || currentTurn != PLAYER_INDEX) {
            return;
        }

        List<Card> previousCards = getLastPlayedCards(lastPlayerWhoPlayed);

        // 查找所有能打过上家的牌组
        List<List<Card>> playableCombos = GameRuleUtil.findPlayableCombos(playerHandCards, previousCards);

        if (playableCombos.isEmpty()) {
            Toast.makeText(this, R.string.game_doudizhu_no_beat_pass, Toast.LENGTH_SHORT).show();
        } else {
            // 自动选中第一组提示牌
            List<Card> hintCards = playableCombos.get(0);
            selectCardsByList(hintCards);
            Toast.makeText(this, getString(R.string.game_doudizhu_hint_suggest, GameRuleUtil.getCardType(hintCards).getName()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 玩家点击"不出"按钮的处理。
     *
     * <p>校验当前是否为先手（先手不能不出），标记玩家选择"不出"，
     * 检查是否需要清空桌面（连续两人不出），然后切换到下一个玩家。</p>
     */
    private void onPass() {
        playClickSound();
        if (gameState != STATE_PLAYING || currentTurn != PLAYER_INDEX) {
            return;
        }

        // 先手时不能选择不出
        if (getLastPlayedCards(lastPlayerWhoPlayed) == null) {
            Toast.makeText(this, R.string.game_doudizhu_cannot_pass_first, Toast.LENGTH_SHORT).show();
            return;
        }

        playerPassed[PLAYER_INDEX] = true;
        if (soundManager != null) soundManager.pass(PLAYER_INDEX);

        // 清空玩家出牌区，显示"不出"状态
        playerPlayedCards.clear();
        tableView.setPlayerPlayedCards(null);
        tableView.setPassStates(playerPassed[LEFT_AI_INDEX], playerPassed[RIGHT_AI_INDEX]);
        updateCardCounter();
        tableView.clearSelection();

        // 检查连续两人不出→清空桌面，最后出牌者自由出牌
        boolean cleared = checkAndClearTable();
        if (cleared) {
            continueFromCurrentTurn();
        } else {
            switchToNextPlayer();
        }
    }

    /**
     * 根据牌组列表选中手牌中对应的卡牌。
     *
     * <p>当前为简化实现，仅提示玩家手动选择。
     * 完整实现应通过 tableView 的公共方法设置选中状态。</p>
     *
     * @param cardsToSelect 要选中的卡牌列表
     */
    private void selectCardsByList(List<Card> cardsToSelect) {
        tableView.selectCards(cardsToSelect);
    }

    // ============ AI 逻辑 ============

    /**
     * 根据难度索引配置 AI 行为参数。
     *
     * <p>难度影响两个维度：</p>
     * <ul>
     *   <li><b>AI 思考延迟</b>：简单 1800ms（慢，玩家有反应时间）/ 普通 1200ms / 困难 600ms（快）</li>
     *   <li><b>决策难度因子</b>：简单 0.6（引入随机不出）/ 普通 1.0（标准）/ 困难 1.5（保持激进）</li>
     * </ul>
     *
     * @param difficultyIndex 难度索引（0=简单 / 1=普通 / 2=困难），越界时回退为普通
     */
    private void applyDifficulty(int difficultyIndex) {
        switch (difficultyIndex) {
            case 0: // 简单：AI 慢且易出错
                aiThinkingDelay = 1800L;
                difficultyFactor = 0.6f;
                break;
            case 2: // 困难：AI 快且激进
                aiThinkingDelay = 600L;
                difficultyFactor = 1.5f;
                break;
            case 1: // 普通：默认
            default:
                aiThinkingDelay = 1200L;
                difficultyFactor = 1.0f;
                break;
        }
    }

    /**
     * 调度 AI 的行动（叫地主或出牌）。
     *
     * <p>取消之前挂起的 AI 任务，然后延迟 {@link #aiThinkingDelay} 毫秒后执行。
     * 根据当前游戏状态决定执行叫地主还是出牌逻辑。</p>
     */
    private void scheduleAIAction() {
        // 取消之前未执行的 AI 任务，防止重复调度
        if (aiThinkingRunnable != null) {
            handler.removeCallbacks(aiThinkingRunnable);
        }

        aiThinkingRunnable = () -> {
            if (gameState == STATE_BIDDING) {
                executeAIBid(currentTurn);
            } else if (gameState == STATE_PLAYING) {
                executeAIAction(currentTurn);
            }
        };
        handler.postDelayed(aiThinkingRunnable, aiThinkingDelay);
    }

    /**
     * 执行 AI 的叫地主决策。
     *
     * <p>AI 通过 {@link #evaluateHandForBid(List)} 评估手牌质量决定是否叫地主。
     * 如果所有玩家都不叫，玩家必须叫地主。</p>
     *
     * @param aiIndex AI 玩家索引（LEFT_AI_INDEX 或 RIGHT_AI_INDEX）
     */
    private void executeAIBid(int aiIndex) {
        if (gameState != STATE_BIDDING) {
            return;
        }

        List<Card> aiHand = (aiIndex == LEFT_AI_INDEX) ? leftAIHandCards : rightAIHandCards;

        boolean shouldBid = evaluateHandForBid(aiHand);
        if (soundManager != null) soundManager.bid(shouldBid, currentTurn);

        if (shouldBid) {
            String aiName = (aiIndex == LEFT_AI_INDEX) ? getString(R.string.game_doudizhu_player_left_ai) : getString(R.string.game_doudizhu_player_right_ai);
            Toast.makeText(this, getString(R.string.game_doudizhu_ai_calls_landlord, aiName), Toast.LENGTH_SHORT).show();

            setLandlord(aiIndex);
            startPlayingPhase();
        } else {
            String aiName = (aiIndex == LEFT_AI_INDEX) ? getString(R.string.game_doudizhu_player_left_ai) : getString(R.string.game_doudizhu_player_right_ai);
            Toast.makeText(this, getString(R.string.game_doudizhu_ai_passes_bid, aiName), Toast.LENGTH_SHORT).show();

            int nextPlayer = (aiIndex + 1) % 3;

            // 如果轮回到玩家，说明所有人都不叫，玩家必须叫
            if (nextPlayer == PLAYER_INDEX) {
                Toast.makeText(this, R.string.game_doudizhu_all_pass_must_call, Toast.LENGTH_SHORT).show();
                currentTurn = PLAYER_INDEX;
                updateTurnIndicator();
            } else {
                // 继续让下一个 AI 决策
                currentTurn = nextPlayer;
                updateTurnIndicator();
                scheduleAIAction();
            }
        }
    }

    /**
     * 评估手牌质量，决定 AI 是否叫地主。
     *
     * <p>这个方法就像给AI的手牌"打分"——牌越好分越高，分够了就叫地主：</p>
     * <ul>
     *   <li>王炸（大小王）：+8 分（最强组合，几乎无敌）</li>
     *   <li>小王单独：+3 分</li>
     *   <li>大王单独：+4 分</li>
     *   <li>每个炸弹（四张相同）：+6 分（炸弹一出，对手只能用更大的炸弹压）</li>
     *   <li>每个2：+2 分（2是普通牌中最大的）</li>
     *   <li>每个A：+1 分</li>
     *   <li>每个K：+0.5 分，Q：+0.3 分，J：+0.2 分</li>
     * </ul>
     * <p>总分 ≥ 7 时叫地主（7分相当于手上有王炸+一个2，或者两个炸弹）。</p>
     *
     * @param handCards AI 的手牌
     * @return true 表示叫地主，false 表示不叫
     */
    private boolean evaluateHandForBid(List<Card> handCards) {
        if (handCards == null || handCards.isEmpty()) {
            return false;
        }

        double score = 0;

        // 统计各牌值的数量
        Map<Integer, Integer> rankCountMap = new HashMap<>();
        for (Card card : handCards) {
            int weight = card.getWeight();
            rankCountMap.put(weight, rankCountMap.getOrDefault(weight, 0) + 1);
        }

        // 王炸检测
        boolean hasSmallJoker = rankCountMap.containsKey(Rank.SMALL_JOKER.getWeight());
        boolean hasBigJoker = rankCountMap.containsKey(Rank.BIG_JOKER.getWeight());
        if (hasSmallJoker && hasBigJoker) {
            score += 8;
        } else {
            if (hasSmallJoker) score += 3;
            if (hasBigJoker) score += 4;
        }

        // 炸弹检测（四张相同）
        for (int count : rankCountMap.values()) {
            if (count == 4) {
                score += 6;
            }
        }

        // 2 的数量加分
        int twoCount = rankCountMap.getOrDefault(Rank.TWO.getWeight(), 0);
        score += twoCount * 2;

        // A 的数量加分
        int aceCount = rankCountMap.getOrDefault(Rank.ACE.getWeight(), 0);
        score += aceCount;

        // 大牌（K、Q、J）加权加分
        int kingCount = rankCountMap.getOrDefault(Rank.KING.getWeight(), 0);
        int queenCount = rankCountMap.getOrDefault(Rank.QUEEN.getWeight(), 0);
        int jackCount = rankCountMap.getOrDefault(Rank.JACK.getWeight(), 0);
        score += kingCount * 0.5;
        score += queenCount * 0.3;
        score += jackCount * 0.2;

        return score >= 7;
    }

    /**
     * 执行 AI 的出牌行动。
     *
     * <p>通过 {@link AIBot#decidePlay(List, List, GameContext, float)} 获取 AI 的出牌决策，
     * 更新手牌和出牌记录，检查获胜条件和桌面清空条件。</p>
     *
     * @param aiIndex AI 玩家索引
     */
    private void executeAIAction(int aiIndex) {
        if (gameState != STATE_PLAYING) {
            return;
        }

        List<Card> aiHand = (aiIndex == LEFT_AI_INDEX) ? leftAIHandCards : rightAIHandCards;

        // 获取上家出的牌
        List<Card> previousCards = getLastPlayedCards(lastPlayerWhoPlayed);

        // AI 决策出牌（返回 null 表示选择不出）；传入难度因子影响决策质量
        List<Card> aiPlayedCards = AIBot.decidePlay(aiHand, previousCards, null, difficultyFactor);

        boolean cleared = false;
        if (aiPlayedCards != null && !aiPlayedCards.isEmpty()) {
            // AI 选择出牌
            playerPassed = new boolean[]{false, false, false};
            lastPlayerWhoPlayed = aiIndex;
            playCardEffect(aiPlayedCards, GameRuleUtil.getCardType(aiPlayedCards), aiIndex);

            switch (aiIndex) {
                case LEFT_AI_INDEX:
                    leftAIPlayedCards = new ArrayList<>(aiPlayedCards);
                    tableView.setLeftAIPlayedCards(leftAIPlayedCards);
                    removeCardsFromHand(leftAIHandCards, aiPlayedCards);
                    break;
                case RIGHT_AI_INDEX:
                    rightAIPlayedCards = new ArrayList<>(aiPlayedCards);
                    tableView.setRightAIPlayedCards(rightAIPlayedCards);
                    removeCardsFromHand(rightAIHandCards, aiPlayedCards);
                    break;
            }
        } else {
            // AI 选择不出
            playerPassed[aiIndex] = true;
            if (soundManager != null) soundManager.pass(aiIndex);
            switch (aiIndex) {
                case LEFT_AI_INDEX:
                    leftAIPlayedCards.clear();
                    tableView.setLeftAIPlayedCards(null);
                    break;
                case RIGHT_AI_INDEX:
                    rightAIPlayedCards.clear();
                    tableView.setRightAIPlayedCards(null);
                    break;
            }
        }

        tableView.setAICardCounts(leftAIHandCards.size(), rightAIHandCards.size());
        tableView.setPassStates(playerPassed[LEFT_AI_INDEX], playerPassed[RIGHT_AI_INDEX]);
        updateCardCounter();

        // AI 不出时检查是否需要清空桌面
        if (aiPlayedCards == null || aiPlayedCards.isEmpty()) {
            cleared = checkAndClearTable();
        }

        // AI 出牌后检查获胜条件
        if (aiPlayedCards != null && !aiPlayedCards.isEmpty() && checkWinCondition(aiIndex)) {
            return;
        }

        if (cleared) {
            continueFromCurrentTurn();
        } else {
            switchToNextPlayer();
        }
    }

    // ============ 游戏流程控制 ============

    /**
     * 切换到下一个玩家（按 0→1→2→0 轮转）。
     */
    private void switchToNextPlayer() {
        currentTurn = (currentTurn + 1) % 3;
        continueFromCurrentTurn();
    }

    /**
     * 从当前轮次继续游戏。
     *
     * <p>如果是玩家回合，启用操作按钮；如果是 AI 回合，禁用按钮并调度 AI 行动。</p>
     */
    private void continueFromCurrentTurn() {
        updateTurnIndicator();

        if (currentTurn == PLAYER_INDEX) {
            enablePlayerControls(true);
        } else {
            enablePlayerControls(false);
            scheduleAIAction();
        }
    }

    /**
     * 启用或禁用玩家的出牌控制按钮。
     *
     * @param enable true 启用，false 禁用
     */
    private void enablePlayerControls(boolean enable) {
        btnPlayCard.setEnabled(enable);
        btnHint.setEnabled(enable);
        btnPass.setEnabled(enable);
    }

    /**
     * 从手牌列表中移除已出的牌。
     *
     * @param hand        手牌列表
     * @param playedCards 要移除的牌列表
     */
    private void removeCardsFromHand(List<Card> hand, List<Card> playedCards) {
        for (Card card : playedCards) {
            hand.remove(card);
        }
    }

    /**
     * 获取指定玩家最后出的牌。
     *
     * <p>如果该玩家选择"不出"或没有出牌记录，返回 null。
     * 返回 null 时表示当前为先手局面，可以自由出牌。</p>
     *
     * @param playerIndex 玩家索引（0-2）
     * @return 最后出的牌列表，先手或不出时返回 null
     */
    private List<Card> getLastPlayedCards(int playerIndex) {
        if (playerIndex < 0 || playerIndex >= 3) {
            return null;
        }

        List<Card> playedCards = null;
        switch (playerIndex) {
            case PLAYER_INDEX:
                playedCards = playerPlayedCards;
                break;
            case LEFT_AI_INDEX:
                playedCards = leftAIPlayedCards;
                break;
            case RIGHT_AI_INDEX:
                playedCards = rightAIPlayedCards;
                break;
        }

        // 该玩家选择"不出"或没有出牌时返回 null
        if (playedCards == null || playedCards.isEmpty() || playerPassed[playerIndex]) {
            return null;
        }

        return playedCards;
    }

    /**
     * 清空所有玩家的出牌记录。
     */
    private void clearAllPlayedCards() {
        playerPlayedCards.clear();
        leftAIPlayedCards.clear();
        rightAIPlayedCards.clear();
        tableView.clearAllPlayedCards();
    }

    /**
     * 检查是否需要清空桌面（连续两人选择"不出"）。
     *
     * <p>斗地主规则：当最后出牌者以外的两人都选择"不出"时，
     * 清空桌面，最后出牌者获得自由出牌权。
     * 举个例子：你出了对3，左AI说"不要"，右AI也说"不要"，
     * 那桌面就清空了，轮到你自由出任何牌。</p>
     *
     * @return true 表示已清空桌面，当前轮次已设为最后出牌者
     */
    private boolean checkAndClearTable() {
        if (lastPlayerWhoPlayed < 0 || lastPlayerWhoPlayed >= 3) {
            return false;
        }

        // 统计最后出牌者以外选择"不出"的人数
        int passCount = 0;
        for (int i = 0; i < playerPassed.length; i++) {
            if (i != lastPlayerWhoPlayed && playerPassed[i]) {
                passCount++;
            }
        }

        // 连续两人"不出"→清空桌面，最后出牌者自由出牌
        if (passCount >= 2) {
            clearAllPlayedCards();
            playerPassed = new boolean[]{false, false, false};
            currentTurn = lastPlayerWhoPlayed;
            return true;
        }
        return false;
    }

    /**
     * 检查获胜条件。
     *
     * <p>当某位玩家手牌清空时，该方获胜。</p>
     *
     * @param playerIndex 出牌玩家索引
     * @return true 表示有人获胜，已触发游戏结束流程
     */
    private boolean checkWinCondition(int playerIndex) {
        List<Card> hand = null;
        switch (playerIndex) {
            case PLAYER_INDEX:
                hand = playerHandCards;
                break;
            case LEFT_AI_INDEX:
                hand = leftAIHandCards;
                break;
            case RIGHT_AI_INDEX:
                hand = rightAIHandCards;
                break;
        }

        if (hand != null && hand.isEmpty()) {
            showGameOver(playerIndex);
            return true;
        }

        return false;
    }

    /**
     * 显示游戏结束界面。
     *
     * <p>根据获胜者身份判定玩家输赢，计算得分，显示游戏结束对话框。</p>
     *
     * @param winnerIndex 获胜者索引
     */
    private void showGameOver(int winnerIndex) {
        gameState = STATE_GAME_OVER;
        hideAllButtons();

        boolean winnerIsLandlord = (winnerIndex == landlordPlayerIndex);

        String result;
        if (winnerIndex == PLAYER_INDEX) {
            result = getString(R.string.game_doudizhu_you_win);
        } else {
            result = getString(R.string.game_doudizhu_you_lose);
        }
        if (soundManager != null) {
            if (winnerIndex == PLAYER_INDEX) {
                soundManager.playWinEffect();
            } else {
                soundManager.playLoseEffect();
            }
        }

        int scoreChange = calculateScore(winnerIndex, winnerIsLandlord);

        // 玩家获胜时持久化本局得分到最高分（GameUsageStore 内部仅在新分高于历史最高时更新）
        // 输局不计分，避免把对手的得分误记为玩家最高分
        if (winnerIndex == PLAYER_INDEX && usageStore != null) {
            usageStore.recordScore("doudizhu", scoreChange);
        }

        tvGameOverTitle.setText(R.string.game_doudizhu_game_over);
        tvGameOverResult.setText(result);
        tvScoreDetail.setText(getString(R.string.game_doudizhu_score_detail, (scoreChange >= 0 ? "+" : "") + scoreChange));

        gameOverDialog.setVisibility(View.VISIBLE);
    }

    /**
     * 计算本局得分。
     *
     * <p>简化实现：地主获胜得 100 分（一个人赢两个，奖励多），
     * 农民获胜得 50 分（两个人赢一个，奖励少一些）。</p>
     *
     * @param winnerIndex      获胜者索引
     * @param winnerIsLandlord 获胜者是否为地主
     * @return 得分变化值
     */
    private int calculateScore(int winnerIndex, boolean winnerIsLandlord) {
        return winnerIsLandlord ? 100 : 50;
    }

    // ============ UI 更新方法 ============

    /**
     * 更新地主身份指示器文本。
     */
    private void updateLandlordIndicator() {
        String name;
        if (landlordPlayerIndex == PLAYER_INDEX) {
            name = getString(R.string.game_doudizhu_player_you);
        } else if (landlordPlayerIndex == LEFT_AI_INDEX) {
            name = getString(R.string.game_doudizhu_player_left_ai);
        } else if (landlordPlayerIndex == RIGHT_AI_INDEX) {
            name = getString(R.string.game_doudizhu_player_right_ai);
        } else {
            name = getString(R.string.game_doudizhu_player_pending);
        }
        tvLandlordIndicator.setText(getString(R.string.game_doudizhu_landlord_label) + name);
    }

    /**
     * 更新回合指示器文本，显示当前轮到谁出牌。
     */
    private void updateTurnIndicator() {
        String turnText;
        switch (currentTurn) {
            case PLAYER_INDEX:
                turnText = getString(R.string.game_doudizhu_turn_you);
                break;
            case LEFT_AI_INDEX:
                turnText = getString(R.string.game_doudizhu_turn_left_ai);
                break;
            case RIGHT_AI_INDEX:
                turnText = getString(R.string.game_doudizhu_turn_right_ai);
                break;
            default:
                turnText = "";
        }
        tvTurnIndicator.setText(getString(R.string.game_doudizhu_turn_label, turnText));
    }
}
