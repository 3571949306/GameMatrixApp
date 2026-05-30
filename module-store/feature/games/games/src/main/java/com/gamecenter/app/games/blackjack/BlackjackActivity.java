package com.gamecenter.app.games.blackjack;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.games.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 21点（Blackjack）游戏的主界面 Activity。
 *
 * <p>实现进阶规则：加倍（Double Down）、分牌（Split）、保险（Insurance），
 * 使用6副牌，庄家在软17时必须补牌（Hit Soft 17）。
 *
 * <p>职责：
 * <ul>
 *   <li>管理游戏界面和用户交互（要牌、停牌、加倍、分牌、保险）</li>
 *   <li>维护牌组、玩家手牌、庄家手牌、筹码和赌注等游戏状态</li>
 *   <li>实现庄家AI逻辑（软17补牌规则）</li>
 *   <li>通过 {@link GameUsageStore} 记录胜负数据</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>使用6副牌（312张）模拟真实赌场牌靴，牌用完后自动重新洗牌</li>
 *   <li>牌值编码：1=A, 2-10=对应点数, 11=J, 12=Q, 13=K；J/Q/K 统一按10点计算</li>
 *   <li>分牌功能简化处理：分出的第二手牌由庄家自动补牌至17点以上，不提供交互操作</li>
 *   <li>筹码归零时系统赠送500筹码，避免游戏无法继续</li>
 * </ul>
 */
public class BlackjackActivity extends AppCompatActivity {

    /** 游戏唯一标识，用于胜负记录和统计 */
    private static final String GAME_ID = "blackjack";

    /** 玩家手牌显示文本 */
    private TextView tvPlayerCards;

    /** 庄家手牌显示文本 */
    private TextView tvDealerCards;

    /** 玩家点数显示文本 */
    private TextView tvPlayerScore;

    /** 庄家点数显示文本 */
    private TextView tvDealerScore;

    /** 游戏结果和筹码信息显示文本 */
    private TextView tvResult;

    /** 要牌按钮 */
    private MaterialButton btnHit, btnStand, btnDouble, btnSplit, btnInsurance;

    /** 重新开始和教程按钮 */
    private MaterialButton btnRestart, btnTutorial;

    /** 玩家手牌列表，每个元素为牌值（1-13） */
    private List<Integer> playerCards;

    /** 庄家手牌列表，每个元素为牌值（1-13） */
    private List<Integer> dealerCards;

    /** 玩家当前点数 */
    private int playerScore;

    /** 庄家当前点数 */
    private int dealerScore;

    /** 当前赌注金额 */
    private int bet = 100;

    /** 玩家筹码余额 */
    private int chips = 1000;

    /** 游戏是否已结束 */
    private boolean gameOver;

    /** 本局是否已使用加倍（加倍后不可再要牌） */
    private boolean doubleUsed;

    /** 本局是否已购买保险 */
    private boolean insuranceTaken;

    /** 保险赌注金额（为原始赌注的一半） */
    private int insuranceBet;

    /** 牌靴（6副牌共312张），使用列表模拟牌堆 */
    private List<Integer> deck;

    /** 当前牌靴的抽牌位置索引 */
    private int deckIndex;

    /** 游戏使用记录存储，用于持久化胜负数据 */
    private GameUsageStore usageStore;

    /**
     * Activity 创建回调。初始化布局、视图引用、按钮事件监听器，并开始新一局。
     *
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blackjack);

        TextView tvTitle = findViewById(R.id.tv_game_title);
        tvTitle.setText(R.string.game_blackjack);

        tvPlayerCards = findViewById(R.id.tv_player_cards);
        tvDealerCards = findViewById(R.id.tv_dealer_cards);
        tvPlayerScore = findViewById(R.id.tv_player_score);
        tvDealerScore = findViewById(R.id.tv_dealer_score);
        tvResult = findViewById(R.id.tv_result);

        btnHit = findViewById(R.id.btn_hit);
        btnStand = findViewById(R.id.btn_stand);
        btnDouble = findViewById(R.id.btn_double);
        btnSplit = findViewById(R.id.btn_split);
        btnInsurance = findViewById(R.id.btn_insurance);
        btnRestart = findViewById(R.id.btn_game_restart);
        btnTutorial = findViewById(R.id.btn_game_tutorial);

        btnHit.setOnClickListener(v -> hit());
        btnStand.setOnClickListener(v -> stand());
        btnDouble.setOnClickListener(v -> doubleDown());
        btnSplit.setOnClickListener(v -> split());
        btnInsurance.setOnClickListener(v -> takeInsurance());
        btnRestart.setOnClickListener(v -> startNewGame());
        btnTutorial.setOnClickListener(v -> GameTutorialHelper.showBlackjackTutorial(this));

        usageStore = new GameUsageStore(this);
        startNewGame();
    }

    /**
     * 生成一副新的6副牌牌靴并随机洗牌。
     *
     * <p>6副牌 = 6 × 13种牌面 = 78张，每种牌面6张。
     *
     * @return 洗好的牌靴列表
     */
    private List<Integer> newDeck() {
        List<Integer> d = new ArrayList<>();
        for (int deckNum = 0; deckNum < 6; deckNum++)
            for (int value = 1; value <= 13; value++)
                d.add(value);
        Collections.shuffle(d);
        return d;
    }

    /**
     * 从牌靴中抽取一张牌。
     *
     * <p>若牌靴已用完，自动生成新牌靴并重置索引。
     *
     * @return 抽取的牌值（1-13）
     */
    private int drawCard() {
        if (deckIndex >= deck.size()) {
            deck = newDeck();
            deckIndex = 0;
        }
        return deck.get(deckIndex++);
    }

    /**
     * 开始新一局游戏。
     *
     * <p>重置所有游戏状态，生成新牌靴，为玩家和庄家各发两张牌。
     */
    private void startNewGame() {
        deck = newDeck();
        deckIndex = 0;
        playerCards = new ArrayList<>();
        dealerCards = new ArrayList<>();
        gameOver = false;
        doubleUsed = false;
        insuranceTaken = false;
        insuranceBet = 0;
        tvResult.setText("");

        // 各发两张初始手牌
        playerCards.add(drawCard());
        playerCards.add(drawCard());
        dealerCards.add(drawCard());
        dealerCards.add(drawCard());

        updateUI();
    }

    /**
     * 获取单张牌的点数值。
     *
     * <p>J(11)、Q(12)、K(13) 统一按10点计算，A(1) 在此方法中返回1，
     * 其在手牌中的灵活取值（1或11）由 {@link #calculateScore(List)} 处理。
     *
     * @param card 牌值（1-13）
     * @return 点数值（1-10）
     */
    private int getCardValue(int card) {
        if (card >= 10) return 10;
        return card;
    }

    /**
     * 计算手牌的总点数，正确处理A的灵活取值。
     *
     * <p>A 的计算逻辑：先将所有 A 按11点计算，若总点数超过21，
     * 则逐个将 A 从11点降为1点，直到总点数不超过21或没有更多 A 可降。
     *
     * @param cards 手牌列表
     * @return 最优总点数（不超过21时的最大值，或超过21时的实际值）
     */
    private int calculateScore(List<Integer> cards) {
        int score = 0;
        int aces = 0;
        for (int card : cards) {
            if (card == 1) { aces++; score += 11; }
            else score += getCardValue(card);
        }
        // 爆牌时将A从11降为1，每降一次减少10点
        while (score > 21 && aces > 0) {
            score -= 10;
            aces--;
        }
        return score;
    }

    /**
     * 将牌值转换为显示字符串。
     *
     * @param card 牌值（1-13）
     * @return 显示名称：A, 2-10, J, Q, K
     */
    private String cardToString(int card) {
        String[] names = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
        return names[card - 1];
    }

    /**
     * 判断手牌是否为"软"手牌（包含按11点计算的A且未爆牌）。
     *
     * <p>软手牌意味着即使再抽一张牌也不会立即爆牌（因为A可以从11降为1）。
     * 此判断影响庄家在软17时是否继续补牌的决策。
     *
     * @param cards 手牌列表
     * @return 如果是软手牌返回 true
     */
    private boolean isSoft(List<Integer> cards) {
        int score = 0;
        int aces = 0;
        for (int card : cards) {
            if (card == 1) { aces++; score += 11; }
            else score += getCardValue(card);
        }
        // 存在按11点计算的A且总点数未爆牌，即为软手牌
        return aces > 0 && score <= 21;
    }

    /**
     * 购买保险。
     *
     * <p>保险规则：
     * <ul>
     *   <li>仅在庄家明牌为A时可用</li>
     *   <li>保险金额为当前赌注的一半</li>
     *   <li>若庄家确实为21点（Blackjack），保险赔付2:1</li>
     * </ul>
     */
    private void takeInsurance() {
        if (gameOver || insuranceTaken || dealerCards.get(0) != 1) return;
        insuranceTaken = true;
        insuranceBet = bet / 2;
        chips -= insuranceBet;
        Toast.makeText(this, "已投保 " + insuranceBet + " 筹码", Toast.LENGTH_SHORT).show();
        updateUI();
    }

    /**
     * 要牌（Hit）：玩家再抽一张牌。
     *
     * <p>若加倍后不可再要牌。抽牌后若爆牌（超过21点），立即判负。
     */
    private void hit() {
        if (gameOver) return;
        if (doubleUsed) return;
        playerCards.add(drawCard());
        playerScore = calculateScore(playerCards);
        updateUI();

        if (playerScore > 21) {
            endGame("爆牌! 你输了");
        }
    }

    /**
     * 停牌（Stand）：玩家不再要牌，轮到庄家操作。
     */
    private void stand() {
        if (gameOver) return;
        dealerPlay();
    }

    /**
     * 加倍（Double Down）：将赌注翻倍，再抽一张牌后自动停牌。
     *
     * <p>加倍条件：
     * <ul>
     *   <li>仅在初始两张牌时可用</li>
     *   <li>本局未使用过加倍</li>
     *   <li>筹码足够支付额外赌注</li>
     * </ul>
     */
    private void doubleDown() {
        if (gameOver || playerCards.size() != 2 || doubleUsed) return;
        if (chips < bet) { Toast.makeText(this, "筹码不足", Toast.LENGTH_SHORT).show(); return; }
        doubleUsed = true;
        chips -= bet;
        bet *= 2;
        playerCards.add(drawCard());
        playerScore = calculateScore(playerCards);
        updateUI();

        if (playerScore > 21) {
            endGame("爆牌! 你输了");
        } else {
            dealerPlay();
        }
    }

    /**
     * 分牌（Split）：将两张相同点数的牌分为两手，分别与庄家对决。
     *
     * <p>分牌规则：
     * <ul>
     *   <li>仅在初始两张牌且点数相同时可用</li>
     *   <li>需要额外支付等额赌注</li>
     *   <li>第一手牌由玩家正常操作（停牌后进入庄家阶段）</li>
     *   <li>第二手牌由系统自动补牌至17点以上</li>
     * </ul>
     *
     * <p>注意：当前实现为简化版分牌，第二手牌不提供交互操作。
     */
    private void split() {
        if (gameOver || playerCards.size() != 2) return;
        // 比较两张牌的点数值（J/Q/K 统一为10点）
        int c1 = playerCards.get(0) >= 10 ? 10 : playerCards.get(0);
        int c2 = playerCards.get(1) >= 10 ? 10 : playerCards.get(1);
        if (c1 != c2) { Toast.makeText(this, "只有相同点数才能分牌", Toast.LENGTH_SHORT).show(); return; }
        if (chips < bet) { Toast.makeText(this, "筹码不足", Toast.LENGTH_SHORT).show(); return; }

        // 扣除分牌额外赌注
        chips -= bet;

        // 将第二张牌移至分出的手牌，为第一手补一张新牌
        int secondCard = playerCards.remove(1);
        playerCards.add(drawCard());
        playerScore = calculateScore(playerCards);

        // 构建分出的第二手牌
        List<Integer> splitHand = new ArrayList<>();
        splitHand.add(secondCard);
        splitHand.add(drawCard());

        // 第一手牌若爆牌则直接判负
        if (playerScore > 21) {
            endGame("爆牌! 你输了");
            return;
        }

        // 第一手牌执行庄家对决
        stand();

        // 第二手牌由系统自动补牌至17点以上
        int splitScore = calculateScore(splitHand);
        while (splitScore < 17) {
            splitHand.add(drawCard());
            splitScore = calculateScore(splitHand);
        }

        // 结算第二手牌的筹码
        if (splitScore > 21) chips -= bet;
        else if (dealerScore > 21 || splitScore > dealerScore) chips += bet * 2;
        else if (splitScore < dealerScore) chips -= bet;
    }

    /**
     * 庄家自动补牌逻辑。
     *
     * <p>庄家规则：
     * <ul>
     *   <li>点数小于17时必须继续补牌</li>
     *   <li>软17（包含按11点计算的A，总点数为17）时也必须补牌（Hit Soft 17 规则）</li>
     *   <li>保险赔付：若玩家购买了保险且庄家为21点，赔付2:1</li>
     * </ul>
     *
     * <p>补牌结束后比较双方点数，结算赌注。
     */
    private void dealerPlay() {
        // 保险赔付检查：玩家已购买保险 + 庄家明牌为A + 庄家确为21点
        if (insuranceTaken && dealerCards.get(0) == 1 && calculateScore(dealerCards) == 21) {
            chips += insuranceBet * 3;
            tvResult.append(" 保险赔付!");
        }

        // 庄家补牌：点数<17必须补，软17也必须补（Hit Soft 17）
        dealerScore = calculateScore(dealerCards);
        while (dealerScore < 17 || (dealerScore == 17 && isSoft(dealerCards))) {
            dealerCards.add(drawCard());
            dealerScore = calculateScore(dealerCards);
        }

        updateUI();

        // 结算比较
        if (dealerScore > 21) {
            endGame("庄家爆牌! 你赢了! +" + (bet * 2));
            chips += bet * 2;
        } else if (playerScore > dealerScore) {
            endGame("你赢了! " + playerScore + " > " + dealerScore + "  +" + (bet * 2));
            chips += bet * 2;
        } else if (playerScore < dealerScore) {
            endGame("你输了! " + playerScore + " < " + dealerScore);
            chips -= bet;
        } else {
            endGame("平局! 退还筹码");
            chips += bet;
        }
    }

    /**
     * 结束游戏，显示结果并记录胜负。
     *
     * <p>根据结果消息中的关键词判断胜负，记录到使用统计。
     * 若筹码归零，系统赠送500筹码以继续游戏。
     *
     * @param msg 结果消息文本
     */
    private void endGame(String msg) {
        tvResult.setText(msg + "  筹码: " + chips);
        gameOver = true;
        updateUI();

        // 根据消息关键词判断胜负并记录
        if (msg.contains("赢了")) {
            usageStore.recordWin(GAME_ID);
        } else if (msg.contains("输") && !msg.contains("平局")) {
            usageStore.recordLoss(GAME_ID);
        }

        // 筹码归零保护：赠送500筹码避免无法继续
        if (chips <= 0) {
            chips = 500;
            tvResult.setText(tvResult.getText() + "  系统赠送500筹码");
        }
    }

    /**
     * 更新所有界面元素的显示内容。
     *
     * <p>更新内容包括：
     * <ul>
     *   <li>玩家手牌、点数（标注软/硬）、筹码余额和赌注</li>
     *   <li>庄家手牌（游戏进行中仅显示明牌，结束后显示全部）</li>
     *   <li>各操作按钮的可用状态（加倍、分牌、保险有条件限制）</li>
     * </ul>
     */
    private void updateUI() {
        playerScore = calculateScore(playerCards);
        dealerScore = calculateScore(dealerCards);

        // 显示玩家手牌和点数信息
        StringBuilder ps = new StringBuilder("你的牌: ");
        for (int card : playerCards) ps.append(cardToString(card)).append(" ");
        tvPlayerCards.setText(ps.toString());
        tvPlayerScore.setText("分数: " + playerScore + " (" + (isSoft(playerCards)?"软":"硬") + ")  筹码: " + chips + "  押注: " + bet);

        // 庄家手牌：游戏进行中隐藏暗牌，结束后全部显示
        StringBuilder ds = new StringBuilder("庄家的牌: ");
        if (gameOver) {
            for (int card : dealerCards) ds.append(cardToString(card)).append(" ");
        } else {
            ds.append(cardToString(dealerCards.get(0))).append(" ?");
        }
        tvDealerCards.setText(ds.toString());
        tvDealerScore.setText(gameOver ? "分数: " + dealerScore : "分数: ?");

        // 加倍条件：仅初始两张牌、未使用加倍、筹码充足、游戏未结束
        boolean canDouble = playerCards.size() == 2 && !doubleUsed && chips >= bet && !gameOver;

        // 分牌条件：仅初始两张牌、未使用加倍、游戏未结束、两张牌点数相同
        boolean canSplit = playerCards.size() == 2 && !doubleUsed && !gameOver;
        int c1v = playerCards.size() >= 1 ? Math.min(10, playerCards.get(0)) : -1;
        int c2v = playerCards.size() >= 2 ? Math.min(10, playerCards.get(1)) : -2;
        if (c1v != c2v) canSplit = false;

        // 保险条件：游戏未结束、未购买保险、庄家明牌为A
        boolean insuranceAvailable = !gameOver && !insuranceTaken
                && dealerCards.size() >= 1 && dealerCards.get(0) == 1;

        btnHit.setEnabled(!gameOver);
        btnStand.setEnabled(!gameOver);
        btnDouble.setEnabled(canDouble);
        btnSplit.setEnabled(canSplit);
        btnInsurance.setEnabled(insuranceAvailable);
    }
}
