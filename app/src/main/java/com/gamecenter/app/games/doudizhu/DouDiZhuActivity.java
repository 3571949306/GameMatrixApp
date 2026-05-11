package com.gamecenter.app.games.doudizhu;

import android.app.AlertDialog;
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
import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.CardType;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.utils.GameRuleUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 斗地主单机游戏控制器 (DouDiZhu Activity)
 * 管理游戏的生命周期、状态机和主循环
 * 使用横屏布局 (activity_doudizhu.xml)
 */
public class DouDiZhuActivity extends AppCompatActivity {

    // ============ 常量定义 ============

    // 游戏状态枚举
    private static final int STATE_WAITING = 0;           // 等待开始
    private static final int STATE_DEALING = 1;          // 发牌中
    private static final int STATE_BIDDING = 2;           // 叫地主阶段
    private static final int STATE_PLAYING = 3;           // 出牌阶段
    private static final int STATE_GAME_OVER = 4;         // 游戏结束

    // 玩家索引
    public static final int PLAYER_INDEX = 0;            // 玩家
    public static final int LEFT_AI_INDEX = 1;           // 左方AI
    public static final int RIGHT_AI_INDEX = 2;          // 右方AI

    // 地主状态
    private static final int LANDLORD_NONE = 0;          // 未确定
    private static final int LANDLORD_FARMER = 1;        // 农民
    private static final int LANDLORD_LORD = 2;          // 地主

    // 思考延迟
    private static final long AI_THINKING_DELAY = 1200L;  // AI思考延迟（毫秒）

    // ============ 界面组件 ============

    // 桌面视图（自定义View）
    private DouDiZhuTableView tableView;

    // 按钮容器
    private LinearLayout bidButtonLayout;                // 叫地主按钮组
    private LinearLayout playButtonLayout;                // 出牌按钮组
    private LinearLayout scoreButtonLayout;               // 叫分按钮组
    private View buttonContainer;

    // 操作按钮
    private Button btnCallLandlord;                      // 叫地主
    private Button btnNoCall;                            // 不叫
    private Button btnPlayCard;                          // 出牌
    private Button btnHint;                              // 提示
    private Button btnPass;                              // 不出
    private Button btnScore0, btnScore1, btnScore2, btnScore3;  // 叫分按钮

    // 状态栏
    private LinearLayout topStatusBar;
    private TextView tvLandlordIndicator;
    private TextView tvTurnIndicator;

    // 游戏结束对话框
    private LinearLayout gameOverDialog;
    private TextView tvGameOverTitle;
    private TextView tvGameOverResult;
    private TextView tvScoreDetail;
    private Button btnPlayAgain;
    private Button btnExit;

    // 加载进度
    private ProgressBar progressLoading;

    // ============ 游戏数据 ============

    // 三个玩家的手牌
    private List<Card> playerHandCards;                 // 玩家手牌
    private List<Card> leftAIHandCards;                  // 左AI手牌
    private List<Card> rightAIHandCards;                 // 右AI手牌
    private List<Card> bottomCards;                      // 底牌（3张）

    // 当前出牌记录
    private List<Card> playerPlayedCards;                // 玩家上轮出的牌
    private List<Card> leftAIPlayedCards;                // 左AI上轮出的牌
    private List<Card> rightAIPlayedCards;               // 右AI上轮出的牌

    // 玩家是否选择"不出"的标志
    private boolean[] playerPassed = new boolean[3];     // [玩家, 左AI, 右AI] 是否选择不出

    // 地主状态
    private int[] landlordStatus = new int[3];           // [玩家, 左AI, 右AI] 的地主状态
    private int landlordPlayerIndex = -1;                // 地主玩家索引

    // 当前状态
    private int gameState = STATE_WAITING;               // 当前游戏状态
    private int currentTurn = PLAYER_INDEX;              // 当前轮到谁出牌
    private int lastPlayerWhoPlayed = -1;                // 最后出牌（非过）的玩家

    // 分数
    private int currentBidScore = 0;                     // 当前叫的分数
    private int baseScore = 1;                           // 基础分数

    // Handler 用于延迟执行
    private Handler handler = new Handler(Looper.getMainLooper());

    // AI 思考延迟 Runnable
    private Runnable aiThinkingRunnable;
    private DouDiZhuSoundManager soundManager;

    // ============ 生命周期 ============

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doudizhu);
        soundManager = new DouDiZhuSoundManager(this);

        initViews();
        initListeners();
        startNewGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (soundManager != null) {
            soundManager.release();
            soundManager = null;
        }
    }

    // ============ 初始化方法 ============

    /**
     * 初始化界面组件
     */
    private void initViews() {
        // 桌面视图
        tableView = findViewById(R.id.tableView);

        // 按钮容器
        bidButtonLayout = findViewById(R.id.bidButtonLayout);
        playButtonLayout = findViewById(R.id.playButtonLayout);
        scoreButtonLayout = findViewById(R.id.scoreButtonLayout);
        buttonContainer = findViewById(R.id.buttonContainer);

        // 操作按钮
        btnCallLandlord = findViewById(R.id.btnCallLandlord);
        btnNoCall = findViewById(R.id.btnNoCall);
        btnPlayCard = findViewById(R.id.btnPlayCard);
        btnHint = findViewById(R.id.btnHint);
        btnPass = findViewById(R.id.btnPass);

        btnScore0 = findViewById(R.id.btnScore0);
        btnScore1 = findViewById(R.id.btnScore1);
        btnScore2 = findViewById(R.id.btnScore2);
        btnScore3 = findViewById(R.id.btnScore3);

        // 状态栏
        topStatusBar = findViewById(R.id.topStatusBar);
        tvLandlordIndicator = findViewById(R.id.tvLandlordIndicator);
        tvTurnIndicator = findViewById(R.id.tvTurnIndicator);

        // 游戏结束对话框
        gameOverDialog = findViewById(R.id.gameOverDialog);
        tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvGameOverResult = findViewById(R.id.tvGameOverResult);
        tvScoreDetail = findViewById(R.id.tvScoreDetail);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        btnExit = findViewById(R.id.btnExit);

        // 加载进度
        progressLoading = findViewById(R.id.progressLoading);
    }

    /**
     * 初始化按钮监听器
     */
    private void initListeners() {
        // 叫地主相关按钮
        btnCallLandlord.setOnClickListener(v -> onCallLandlord());
        btnNoCall.setOnClickListener(v -> onNoCall());

        // 出牌相关按钮
        btnPlayCard.setOnClickListener(v -> onPlayCard());
        btnHint.setOnClickListener(v -> onHint());
        btnPass.setOnClickListener(v -> onPass());

        // 叫分按钮
        btnScore0.setOnClickListener(v -> onBidScore(0));
        btnScore1.setOnClickListener(v -> onBidScore(1));
        btnScore2.setOnClickListener(v -> onBidScore(2));
        btnScore3.setOnClickListener(v -> onBidScore(3));

        // 游戏结束按钮
        btnPlayAgain.setOnClickListener(v -> {
            gameOverDialog.setVisibility(View.GONE);
            startNewGame();
        });
        btnExit.setOnClickListener(v -> finish());

        // 桌面触摸监听
        tableView.setOnCardTouchListener(cards -> {
            // 玩家选择了卡牌，可以在这里更新UI
        });

        // 桌面点击监听（用于取消选择等）
        tableView.setOnTableClickListener(() -> {
            // 桌面被点击
        });
    }

    // ============ 游戏流程控制 ============

    /**
     * 开始一局新游戏
     */
    private void startNewGame() {
        // 重置游戏数据
        gameState = STATE_DEALING;
        currentTurn = PLAYER_INDEX;
        lastPlayerWhoPlayed = -1;
        currentBidScore = 0;
        landlordPlayerIndex = -1;
        landlordStatus = new int[]{0, 0, 0};

        // 初始化手牌列表
        playerHandCards = new ArrayList<>();
        leftAIHandCards = new ArrayList<>();
        rightAIHandCards = new ArrayList<>();
        bottomCards = new ArrayList<>();

        playerPlayedCards = new ArrayList<>();
        leftAIPlayedCards = new ArrayList<>();
        rightAIPlayedCards = new ArrayList<>();

        // 重置"不出"标志
        playerPassed = new boolean[]{false, false, false};

        // 显示加载中
        progressLoading.setVisibility(View.VISIBLE);

        // 延迟执行发牌，让UI先更新
        handler.postDelayed(() -> {
            // 洗牌并发牌
            List<Card>[] dealtCards = GameRuleUtil.shuffleAndDeal();
            playerHandCards = dealtCards[0];
            leftAIHandCards = dealtCards[1];
            rightAIHandCards = dealtCards[2];
            bottomCards = dealtCards[3];

            // 更新UI
            tableView.setPlayerHandCards(playerHandCards);
            tableView.setBottomCards(bottomCards);
            tableView.setAICardCounts(leftAIHandCards.size(), rightAIHandCards.size());
            tableView.setPassStates(false, false);
            updateCardCounter();
            if (soundManager != null) soundManager.deal();

            // 隐藏加载
            progressLoading.setVisibility(View.GONE);

            // 进入叫地主阶段
            gameState = STATE_BIDDING;
            showBidUI();

            // 玩家先叫
            currentTurn = PLAYER_INDEX;
            updateTurnIndicator();
        }, 500);
    }

    /**
     * 显示叫地主相关UI
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
     * 显示叫分UI（可选的实现方式）
     */
    private void showBidScoreUI() {
        buttonContainer.setVisibility(View.VISIBLE);
        bidButtonLayout.setVisibility(View.GONE);
        playButtonLayout.setVisibility(View.GONE);
        scoreButtonLayout.setVisibility(View.VISIBLE);
        topStatusBar.setVisibility(View.VISIBLE);
    }

    /**
     * 显示出牌相关UI
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
     * 隐藏所有操作按钮
     */
    private void hideAllButtons() {
        buttonContainer.setVisibility(View.GONE);
        bidButtonLayout.setVisibility(View.GONE);
        playButtonLayout.setVisibility(View.GONE);
        scoreButtonLayout.setVisibility(View.GONE);
    }

    private void playClickSound() {
        if (soundManager != null) {
            soundManager.click();
        }
    }

    private int[] createFullDeckCounter() {
        int[] counts = new int[15];
        for (int i = 0; i < 13; i++) {
            counts[i] = 4;
        }
        counts[13] = 1;
        counts[14] = 1;
        return counts;
    }

    private int rankCounterIndex(Card card) {
        if (card == null) return -1;
        int weight = card.getWeight();
        if (weight >= Rank.THREE.getWeight() && weight <= Rank.BIG_JOKER.getWeight()) {
            return weight - Rank.THREE.getWeight();
        }
        return -1;
    }

    private void subtractCardsFromCounter(int[] counts, List<Card> cards) {
        if (counts == null || cards == null) return;
        for (Card card : cards) {
            int index = rankCounterIndex(card);
            if (index >= 0 && index < counts.length) {
                counts[index] = Math.max(0, counts[index] - 1);
            }
        }
    }

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
     * 玩家点击"叫地主"
     */
    private void onCallLandlord() {
        playClickSound();
        if (gameState != STATE_BIDDING || currentTurn != PLAYER_INDEX) {
            return;
        }
        if (soundManager != null) soundManager.bid(true);

        // 玩家叫地主
        setLandlord(PLAYER_INDEX);
        startPlayingPhase();
    }

    /**
     * 玩家点击"不叫"
     */
    private void onNoCall() {
        playClickSound();
        if (gameState != STATE_BIDDING || currentTurn != PLAYER_INDEX) {
            return;
        }
        if (soundManager != null) soundManager.bid(false);

        // 切换到下一个AI叫地主
        currentTurn = LEFT_AI_INDEX;
        updateTurnIndicator();
        scheduleAIAction();
    }

    /**
     * 玩家选择叫分（另一种叫地主方式）
     */
    private void onBidScore(int score) {
        playClickSound();
        if (gameState != STATE_BIDDING || currentTurn != PLAYER_INDEX) {
            return;
        }

        if (score > currentBidScore) {
            currentBidScore = score;
            landlordPlayerIndex = PLAYER_INDEX;
            setLandlord(PLAYER_INDEX);
            Toast.makeText(this, "你叫了 " + score + " 分", Toast.LENGTH_SHORT).show();

            if (score == 3) {
                // 叫3分直接开始
                startPlayingPhase();
            } else {
                // 继续让AI叫
                currentTurn = LEFT_AI_INDEX;
                updateTurnIndicator();
                scheduleAIAction();
            }
        } else {
            Toast.makeText(this, "必须叫比当前更高的分数", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 设置指定玩家为地主，并分配底牌
     */
    private void setLandlord(int playerIndex) {
        landlordPlayerIndex = playerIndex;
        landlordStatus[playerIndex] = LANDLORD_LORD;

        // 其他玩家是农民
        for (int i = 0; i < 3; i++) {
            if (i != playerIndex) {
                landlordStatus[i] = LANDLORD_FARMER;
            }
        }

        // 地主获得底牌
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

        tableView.setAllLandlordStatus(landlordStatus);
        updateCardCounter();
        updateLandlordIndicator();
    }

    /**
     * 开始出牌阶段
     */
    private void startPlayingPhase() {
        gameState = STATE_PLAYING;

        // 确定先手（地主先出）
        currentTurn = landlordPlayerIndex;
        lastPlayerWhoPlayed = landlordPlayerIndex;

        // 清空上轮的出牌
        playerPassed = new boolean[]{false, false, false};
        clearAllPlayedCards();
        tableView.setPassStates(false, false);
        updateCardCounter();

        // 显示出牌UI
        showPlayUI();

        // 如果是AI先手，AI出牌
        if (currentTurn != PLAYER_INDEX) {
            scheduleAIAction();
        }
    }

    // ============ 出牌逻辑 ============

    /**
     * 玩家点击"出牌"按钮
     */
    private void onPlayCard() {
        playClickSound();
        if (gameState != STATE_PLAYING || currentTurn != PLAYER_INDEX) {
            return;
        }

        // 获取选中的牌
        List<Card> selectedCards = tableView.getSelectedCards();

        if (selectedCards.isEmpty()) {
            Toast.makeText(this, "请选择要出的牌", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取上家出的牌
        List<Card> previousCards = getLastPlayedCards(lastPlayerWhoPlayed);

        // 校验合法性
        CardType selectedType = GameRuleUtil.getCardType(selectedCards);
        if (selectedType == CardType.ERROR) {
            Toast.makeText(this, "选择的牌型不合法", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查能否打过上家
        if (!GameRuleUtil.canPlayPass(selectedCards, previousCards)) {
            Toast.makeText(this, "打不过上家的牌", Toast.LENGTH_SHORT).show();
            return;
        }

        // 玩家选择出牌，重置"不出"标志
        playerPassed = new boolean[]{false, false, false};
        lastPlayerWhoPlayed = PLAYER_INDEX;

        // 合法出牌
        if (soundManager != null) soundManager.cards(selectedCards, selectedType);
        playerPlayedCards = new ArrayList<>(selectedCards);
        removeCardsFromHand(playerHandCards, selectedCards);

        // 播放动画
        tableView.playCardAnim(selectedCards, () -> {
            // 动画完成后更新UI
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
     * 玩家点击"提示"按钮
     */
    private void onHint() {
        playClickSound();
        if (gameState != STATE_PLAYING || currentTurn != PLAYER_INDEX) {
            return;
        }

        // 获取上家出的牌
        List<Card> previousCards = getLastPlayedCards(lastPlayerWhoPlayed);

        // 查找能打过的牌
        List<List<Card>> playableCombos = GameRuleUtil.findPlayableCombos(playerHandCards, previousCards);

        if (playableCombos.isEmpty()) {
            Toast.makeText(this, "没有能打过的牌，请选择'不出'", Toast.LENGTH_SHORT).show();
        } else {
            // 自动选中提示的牌组（第一组）
            List<Card> hintCards = playableCombos.get(0);
            selectCardsByList(hintCards);
            Toast.makeText(this, "提示：建议出 " + GameRuleUtil.getCardType(hintCards).getName(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 玩家点击"不出"按钮
     */
    private void onPass() {
        playClickSound();
        if (gameState != STATE_PLAYING || currentTurn != PLAYER_INDEX) {
            return;
        }

        if (getLastPlayedCards(lastPlayerWhoPlayed) == null) {
            Toast.makeText(this, "当前你先出牌，不能不要", Toast.LENGTH_SHORT).show();
            return;
        }

        // 设置玩家选择"不出"的标志
        playerPassed[PLAYER_INDEX] = true;
        if (soundManager != null) soundManager.pass();

        // 清空玩家出牌区，显示"不出"
        playerPlayedCards.clear();
        tableView.setPlayerPlayedCards(null);
        tableView.setPassStates(playerPassed[LEFT_AI_INDEX], playerPassed[RIGHT_AI_INDEX]);
        updateCardCounter();
        tableView.clearSelection();

        // 检查是否需要清空桌面（连续两人选择"不出"）
        boolean cleared = checkAndClearTable();
        if (cleared) {
            continueFromCurrentTurn();
        } else {
            switchToNextPlayer();
        }
    }

    /**
     * 根据牌组选中手牌
     */
    private void selectCardsByList(List<Card> cardsToSelect) {
        tableView.clearSelection();
        List<Card> selected = new ArrayList<>();
        for (Card card : cardsToSelect) {
            int index = playerHandCards.indexOf(card);
            if (index >= 0) {
                selected.add(card);
            }
        }
        // 通过反射或公共方法选中
        // 这里简化处理，直接提示玩家手动选择
    }

    // ============ AI 逻辑 ============

    /**
     * 安排AI的行动（叫地主或出牌）
     */
    private void scheduleAIAction() {
        // 取消之前的AI任务
        if (aiThinkingRunnable != null) {
            handler.removeCallbacks(aiThinkingRunnable);
        }

        // 延迟执行AI思考
        aiThinkingRunnable = () -> {
            if (gameState == STATE_BIDDING) {
                // AI叫地主
                executeAIBid(currentTurn);
            } else if (gameState == STATE_PLAYING) {
                // AI出牌
                executeAIAction(currentTurn);
            }
        };
        handler.postDelayed(aiThinkingRunnable, AI_THINKING_DELAY);
    }

    /**
     * 执行AI的叫地主决策
     */
    private void executeAIBid(int aiIndex) {
        if (gameState != STATE_BIDDING) {
            return;
        }

        // 获取AI的手牌
        List<Card> aiHand = (aiIndex == LEFT_AI_INDEX) ? leftAIHandCards : rightAIHandCards;

        // AI评估手牌质量，决定是否叫地主
        boolean shouldBid = evaluateHandForBid(aiHand);
        if (soundManager != null) soundManager.bid(shouldBid);

        if (shouldBid) {
            // AI决定叫地主
            String aiName = (aiIndex == LEFT_AI_INDEX) ? "左AI" : "右AI";
            Toast.makeText(this, aiName + " 叫地主", Toast.LENGTH_SHORT).show();

            setLandlord(aiIndex);
            startPlayingPhase();
        } else {
            // AI决定不叫
            String aiName = (aiIndex == LEFT_AI_INDEX) ? "左AI" : "右AI";
            Toast.makeText(this, aiName + " 不叫", Toast.LENGTH_SHORT).show();

            // 切换到下一个玩家
            int nextPlayer = (aiIndex + 1) % 3;

            // 检查是否所有人都选择不叫
            if (nextPlayer == PLAYER_INDEX) {
                // 如果轮回到玩家，玩家必须叫地主
                Toast.makeText(this, "所有人都选择不叫，你必须叫地主", Toast.LENGTH_SHORT).show();
                currentTurn = PLAYER_INDEX;
                updateTurnIndicator();
            } else {
                // 继续让下一个AI叫地主
                currentTurn = nextPlayer;
                updateTurnIndicator();
                scheduleAIAction();
            }
        }
    }

    /**
     * 评估手牌质量，决定是否叫地主
     * @param handCards 手牌
     * @return true表示叫地主，false表示不叫
     */
    private boolean evaluateHandForBid(List<Card> handCards) {
        if (handCards == null || handCards.isEmpty()) {
            return false;
        }

        int score = 0;

        // 统计各牌值的数量
        Map<Integer, Integer> rankCountMap = new HashMap<>();
        for (Card card : handCards) {
            int weight = card.getWeight();
            rankCountMap.put(weight, rankCountMap.getOrDefault(weight, 0) + 1);
        }

        // 检查王炸
        boolean hasSmallJoker = rankCountMap.containsKey(Rank.SMALL_JOKER.getWeight());
        boolean hasBigJoker = rankCountMap.containsKey(Rank.BIG_JOKER.getWeight());
        if (hasSmallJoker && hasBigJoker) {
            score += 8; // 王炸加8分
        } else {
            if (hasSmallJoker) score += 3;
            if (hasBigJoker) score += 4;
        }

        // 检查炸弹
        for (int count : rankCountMap.values()) {
            if (count == 4) {
                score += 6; // 每个炸弹加6分
            }
        }

        // 检查2的数量
        int twoCount = rankCountMap.getOrDefault(Rank.TWO.getWeight(), 0);
        score += twoCount * 2; // 每个2加2分

        // 检查A的数量
        int aceCount = rankCountMap.getOrDefault(Rank.ACE.getWeight(), 0);
        score += aceCount; // 每个A加1分

        // 检查大牌（K、Q、J）
        int kingCount = rankCountMap.getOrDefault(Rank.KING.getWeight(), 0);
        int queenCount = rankCountMap.getOrDefault(Rank.QUEEN.getWeight(), 0);
        int jackCount = rankCountMap.getOrDefault(Rank.JACK.getWeight(), 0);
        score += kingCount * 0.5;
        score += queenCount * 0.3;
        score += jackCount * 0.2;

        // 如果总分大于等于7，叫地主
        return score >= 7;
    }

    /**
     * 执行AI的出牌行动
     */
    private void executeAIAction(int aiIndex) {
        if (gameState != STATE_PLAYING) {
            return;
        }

        // 获取AI的手牌
        List<Card> aiHand = (aiIndex == LEFT_AI_INDEX) ? leftAIHandCards : rightAIHandCards;

        // 获取上家出的牌（从最后一个实际出牌者获取）
        List<Card> previousCards = getLastPlayedCards(lastPlayerWhoPlayed);

        // AI决策出牌
        List<Card> aiPlayedCards = AIBot.decidePlay(aiHand, previousCards);

        // 更新AI出牌记录
        boolean cleared = false;
        if (aiPlayedCards != null && !aiPlayedCards.isEmpty()) {
            // AI选择出牌
            playerPassed = new boolean[]{false, false, false};
            lastPlayerWhoPlayed = aiIndex;
            if (soundManager != null) {
                soundManager.cards(aiPlayedCards, GameRuleUtil.getCardType(aiPlayedCards));
            }

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
            // AI选择不出
            playerPassed[aiIndex] = true;
            if (soundManager != null) soundManager.pass();
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

        // 更新AI手牌数量显示
        tableView.setAICardCounts(leftAIHandCards.size(), rightAIHandCards.size());
        tableView.setPassStates(playerPassed[LEFT_AI_INDEX], playerPassed[RIGHT_AI_INDEX]);
        updateCardCounter();

        // 检查是否需要清空桌面（连续两人选择"不出"）
        if (aiPlayedCards == null || aiPlayedCards.isEmpty()) {
            cleared = checkAndClearTable();
        }

        // 检查是否获胜
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
     * 切换到下一个玩家
     */
    private void switchToNextPlayer() {
        // 轮换：0 -> 1 -> 2 -> 0
        currentTurn = (currentTurn + 1) % 3;
        continueFromCurrentTurn();
    }

    private void continueFromCurrentTurn() {
        updateTurnIndicator();

        // 如果是玩家，启用按钮；否则安排AI
        if (currentTurn == PLAYER_INDEX) {
            enablePlayerControls(true);
        } else {
            enablePlayerControls(false);
            scheduleAIAction();
        }
    }

    /**
     * 启用/禁用玩家控制
     */
    private void enablePlayerControls(boolean enable) {
        btnPlayCard.setEnabled(enable);
        btnHint.setEnabled(enable);
        btnPass.setEnabled(enable);
    }

    /**
     * 从手牌中移除已出的牌
     */
    private void removeCardsFromHand(List<Card> hand, List<Card> playedCards) {
        for (Card card : playedCards) {
            hand.remove(card);
        }
    }

    /**
     * 获取指定玩家上轮出的牌
     * 如果该玩家选择"不出"或没有出牌，返回null
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

        // 如果该玩家选择"不出"或没有出牌，返回null
        if (playedCards == null || playedCards.isEmpty() || playerPassed[playerIndex]) {
            return null;
        }

        return playedCards;
    }

    /**
     * 清空所有出牌
     */
    private void clearAllPlayedCards() {
        playerPlayedCards.clear();
        leftAIPlayedCards.clear();
        rightAIPlayedCards.clear();
        tableView.clearAllPlayedCards();
    }

    /**
     * 检查是否需要清空桌面（连续两人选择"不出"）
     * 当连续两人选择"不出"时，最后一个实际出牌者可以自由出牌
     */
    private boolean checkAndClearTable() {
        if (lastPlayerWhoPlayed < 0 || lastPlayerWhoPlayed >= 3) {
            return false;
        }

        // 统计最后出牌者之外选择"不出"的人数
        int passCount = 0;
        for (int i = 0; i < playerPassed.length; i++) {
            if (i != lastPlayerWhoPlayed && playerPassed[i]) {
                passCount++;
            }
        }

        // 如果连续两人选择"不出"，清空桌面
        if (passCount >= 2) {
            clearAllPlayedCards();
            playerPassed = new boolean[]{false, false, false};
            currentTurn = lastPlayerWhoPlayed;
            return true;
        }
        return false;
    }

    /**
     * 检查获胜条件
     * @param playerIndex 出牌玩家索引
     * @return 是否有人获胜
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
            // 有人手牌归零，获胜
            showGameOver(playerIndex);
            return true;
        }

        return false;
    }

    /**
     * 显示游戏结束
     */
    private void showGameOver(int winnerIndex) {
        gameState = STATE_GAME_OVER;
        hideAllButtons();

        // 判断获胜者身份
        boolean winnerIsLandlord = (winnerIndex == landlordPlayerIndex);

        String result;
        if (winnerIndex == PLAYER_INDEX) {
            result = "你赢了！";
        } else {
            result = "你输了！";
        }
        if (soundManager != null) soundManager.win(winnerIndex == PLAYER_INDEX);

        // 计算得分
        int scoreChange = calculateScore(winnerIndex, winnerIsLandlord);

        tvGameOverTitle.setText("游戏结束");
        tvGameOverResult.setText(result);
        tvScoreDetail.setText("本局得分：" + (scoreChange >= 0 ? "+" : "") + scoreChange);

        gameOverDialog.setVisibility(View.VISIBLE);
    }

    /**
     * 计算得分
     */
    private int calculateScore(int winnerIndex, boolean winnerIsLandlord) {
        // 基础分数 * 倍数
        // 简化实现：获胜方得 50 分
        return winnerIsLandlord ? 100 : 50;
    }

    // ============ UI 更新方法 ============

    /**
     * 更新地主指示器
     */
    private void updateLandlordIndicator() {
        StringBuilder sb = new StringBuilder("地主：");
        if (landlordPlayerIndex == PLAYER_INDEX) {
            sb.append("你");
        } else if (landlordPlayerIndex == LEFT_AI_INDEX) {
            sb.append("左AI");
        } else if (landlordPlayerIndex == RIGHT_AI_INDEX) {
            sb.append("右AI");
        } else {
            sb.append("待定");
        }
        tvLandlordIndicator.setText(sb.toString());
    }

    /**
     * 更新回合指示器
     */
    private void updateTurnIndicator() {
        String turnText;
        switch (currentTurn) {
            case PLAYER_INDEX:
                turnText = "你出牌";
                break;
            case LEFT_AI_INDEX:
                turnText = "左AI出牌";
                break;
            case RIGHT_AI_INDEX:
                turnText = "右AI出牌";
                break;
            default:
                turnText = "";
        }
        tvTurnIndicator.setText("轮到：" + turnText);
    }
}
