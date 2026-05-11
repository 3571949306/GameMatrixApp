package com.gamecenter.app.games.blackjack;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameTutorialHelper;
import com.gamecenter.app.games.GameUsageStore;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 21点 — 进阶规则：加倍/分牌/保险 + 6副牌 + 软17庄家必须补牌
 */
public class BlackjackActivity extends AppCompatActivity {

    private static final String GAME_ID = "blackjack";
    private TextView tvPlayerCards;
    private TextView tvDealerCards;
    private TextView tvPlayerScore;
    private TextView tvDealerScore;
    private TextView tvResult;

    private MaterialButton btnHit, btnStand, btnDouble, btnSplit, btnInsurance;
    private MaterialButton btnRestart, btnTutorial;

    private List<Integer> playerCards;
    private List<Integer> dealerCards;
    private int playerScore;
    private int dealerScore;
    private int bet = 100;
    private int chips = 1000;
    private boolean gameOver;
    private boolean doubleUsed;
    private boolean insuranceTaken;
    private int insuranceBet;
    private List<Integer> deck;
    private int deckIndex;
    private GameUsageStore usageStore;

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

    private List<Integer> newDeck() {
        List<Integer> d = new ArrayList<>();
        for (int deckNum = 0; deckNum < 6; deckNum++)
            for (int value = 1; value <= 13; value++)
                d.add(value);
        Collections.shuffle(d);
        return d;
    }

    private int drawCard() {
        if (deckIndex >= deck.size()) {
            deck = newDeck();
            deckIndex = 0;
        }
        return deck.get(deckIndex++);
    }

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

        playerCards.add(drawCard());
        playerCards.add(drawCard());
        dealerCards.add(drawCard());
        dealerCards.add(drawCard());

        updateUI();
    }

    private int getCardValue(int card) {
        if (card >= 10) return 10;
        return card;
    }

    private int calculateScore(List<Integer> cards) {
        int score = 0;
        int aces = 0;
        for (int card : cards) {
            if (card == 1) { aces++; score += 11; }
            else score += getCardValue(card);
        }
        while (score > 21 && aces > 0) {
            score -= 10;
            aces--;
        }
        return score;
    }

    private String cardToString(int card) {
        String[] names = {"A","2","3","4","5","6","7","8","9","10","J","Q","K"};
        return names[card - 1];
    }

    private boolean isSoft(List<Integer> cards) {
        int score = 0;
        int aces = 0;
        for (int card : cards) {
            if (card == 1) { aces++; score += 11; }
            else score += getCardValue(card);
        }
        return aces > 0 && score <= 21;
    }

    private void takeInsurance() {
        if (gameOver || insuranceTaken || dealerCards.get(0) != 1) return;
        insuranceTaken = true;
        insuranceBet = bet / 2;
        chips -= insuranceBet;
        Toast.makeText(this, "已投保 " + insuranceBet + " 筹码", Toast.LENGTH_SHORT).show();
        updateUI();
    }

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

    private void stand() {
        if (gameOver) return;
        dealerPlay();
    }

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

    private void split() {
        if (gameOver || playerCards.size() != 2) return;
        int c1 = playerCards.get(0) >= 10 ? 10 : playerCards.get(0);
        int c2 = playerCards.get(1) >= 10 ? 10 : playerCards.get(1);
        if (c1 != c2) { Toast.makeText(this, "只有相同点数才能分牌", Toast.LENGTH_SHORT).show(); return; }
        if (chips < bet) { Toast.makeText(this, "筹码不足", Toast.LENGTH_SHORT).show(); return; }

        chips -= bet;
        int secondCard = playerCards.remove(1);
        playerCards.add(drawCard());
        playerScore = calculateScore(playerCards);

        List<Integer> splitHand = new ArrayList<>();
        splitHand.add(secondCard);
        splitHand.add(drawCard());

        if (playerScore > 21) {
            endGame("爆牌! 你输了");
            return;
        }
        stand();
        int splitScore = calculateScore(splitHand);
        while (splitScore < 17) {
            splitHand.add(drawCard());
            splitScore = calculateScore(splitHand);
        }
        if (splitScore > 21) chips -= bet;
        else if (dealerScore > 21 || splitScore > dealerScore) chips += bet * 2;
        else if (splitScore < dealerScore) chips -= bet;
    }

    private void dealerPlay() {
        if (insuranceTaken && dealerCards.get(0) == 1 && calculateScore(dealerCards) == 21) {
            chips += insuranceBet * 3;
            tvResult.append(" 保险赔付!");
        }

        dealerScore = calculateScore(dealerCards);
        while (dealerScore < 17 || (dealerScore == 17 && isSoft(dealerCards))) {
            dealerCards.add(drawCard());
            dealerScore = calculateScore(dealerCards);
        }

        updateUI();

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

    private void endGame(String msg) {
        tvResult.setText(msg + "  筹码: " + chips);
        gameOver = true;
        updateUI();

        if (msg.contains("赢了")) {
            usageStore.recordWin(GAME_ID);
        } else if (msg.contains("输") && !msg.contains("平局")) {
            usageStore.recordLoss(GAME_ID);
        }

        if (chips <= 0) {
            chips = 500;
            tvResult.setText(tvResult.getText() + "  系统赠送500筹码");
        }
    }

    private void updateUI() {
        playerScore = calculateScore(playerCards);
        dealerScore = calculateScore(dealerCards);

        StringBuilder ps = new StringBuilder("你的牌: ");
        for (int card : playerCards) ps.append(cardToString(card)).append(" ");
        tvPlayerCards.setText(ps.toString());
        tvPlayerScore.setText("分数: " + playerScore + " (" + (isSoft(playerCards)?"软":"硬") + ")  筹码: " + chips + "  押注: " + bet);

        StringBuilder ds = new StringBuilder("庄家的牌: ");
        if (gameOver) {
            for (int card : dealerCards) ds.append(cardToString(card)).append(" ");
        } else {
            ds.append(cardToString(dealerCards.get(0))).append(" ?");
        }
        tvDealerCards.setText(ds.toString());
        tvDealerScore.setText(gameOver ? "分数: " + dealerScore : "分数: ?");

        boolean canDouble = playerCards.size() == 2 && !doubleUsed && chips >= bet && !gameOver;
        boolean canSplit = playerCards.size() == 2 && !doubleUsed && !gameOver;
        int c1v = playerCards.size() >= 1 ? Math.min(10, playerCards.get(0)) : -1;
        int c2v = playerCards.size() >= 2 ? Math.min(10, playerCards.get(1)) : -2;
        if (c1v != c2v) canSplit = false;

        boolean insuranceAvailable = !gameOver && !insuranceTaken
                && dealerCards.size() >= 1 && dealerCards.get(0) == 1;

        btnHit.setEnabled(!gameOver);
        btnStand.setEnabled(!gameOver);
        btnDouble.setEnabled(canDouble);
        btnSplit.setEnabled(canSplit);
        btnInsurance.setEnabled(insuranceAvailable);
    }
}
