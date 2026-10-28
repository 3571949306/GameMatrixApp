package com.gamecenter.app.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 21点核心逻辑引擎，支持难度分级、标准拉斯维加斯规则、数学基本策略建议与出牌撤销机制。
 */
public class BlackjackGame {

    public static final int DIFFICULTY_EASY = 0;    // 简单：庄家暗牌提示，庄家软/硬17均停牌
    public static final int DIFFICULTY_MEDIUM = 1;  // 中等：经典拉斯维加斯规则，庄家软17要牌
    public static final int DIFFICULTY_HARD = 2;    // 困难：严苛庄家AI，真实牌靴切牌与防爆牌策略

    public static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    public static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

    public enum RoundResult {
        ONGOING,
        PLAYER_BLACKJACK,
        PLAYER_WIN,
        DEALER_BUST,
        DEALER_WIN,
        PLAYER_BUST,
        TIE
    }

    private int difficulty = DIFFICULTY_MEDIUM;
    private final List<String> deck = new ArrayList<>();
    private final List<String> playerHand = new ArrayList<>();
    private final List<String> dealerHand = new ArrayList<>();
    private final List<String> playerHitHistory = new ArrayList<>();

    private boolean roundActive = false;
    private RoundResult lastResult = RoundResult.ONGOING;

    private int playerWins = 0;
    private int dealerWins = 0;
    private int ties = 0;
    private int winStreak = 0;
    private int maxStreak = 0;
    private int currentScore = 0;
    private int highScore = 0;

    private Random random = new Random();

    public BlackjackGame() {
        initDeck();
    }

    public void initDeck() {
        deck.clear();
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                deck.add(rank + suit);
            }
        }
        Collections.shuffle(deck, random);
    }

    public void startNewRound() {
        if (deck.size() < 12) {
            initDeck();
        }
        playerHand.clear();
        dealerHand.clear();
        playerHitHistory.clear();
        roundActive = true;
        lastResult = RoundResult.ONGOING;

        playerHand.add(drawCard());
        dealerHand.add(drawCard());
        playerHand.add(drawCard());
        dealerHand.add(drawCard());

        // 检查初局双方是否有天生 21 点 (Natural Blackjack)
        boolean playerBj = isBlackjack(playerHand);
        boolean dealerBj = isBlackjack(dealerHand);

        if (playerBj || dealerBj) {
            roundActive = false;
            if (playerBj && dealerBj) {
                lastResult = RoundResult.TIE;
                ties++;
                currentScore += 10;
            } else if (playerBj) {
                lastResult = RoundResult.PLAYER_BLACKJACK;
                playerWins++;
                winStreak++;
                if (winStreak > maxStreak) maxStreak = winStreak;
                currentScore += 50; // Blackjack 超高分
            } else {
                lastResult = RoundResult.DEALER_WIN;
                dealerWins++;
                winStreak = 0;
            }
            if (currentScore > highScore) highScore = currentScore;
        }
    }

    public String drawCard() {
        if (deck.isEmpty()) {
            initDeck();
        }
        return deck.remove(deck.size() - 1);
    }

    /**
     * 玩家选择要牌 (Hit)
     */
    public boolean hit() {
        if (!roundActive) return false;
        String card = drawCard();
        playerHand.add(card);
        playerHitHistory.add(card);

        int score = calculateHandValue(playerHand);
        if (score > 21) {
            // 爆牌
            roundActive = false;
            lastResult = RoundResult.PLAYER_BUST;
            dealerWins++;
            winStreak = 0;
            return false;
        } else if (score == 21) {
            // 满21点，自动停牌交由庄家
            stand();
            return false;
        }
        return true;
    }

    /**
     * 玩家选择停牌 (Stand)，庄家行动
     */
    public void stand() {
        if (!roundActive) return;
        roundActive = false;

        executeDealerTurn();

        int playerScore = calculateHandValue(playerHand);
        int dealerScore = calculateHandValue(dealerHand);

        if (dealerScore > 21) {
            lastResult = RoundResult.DEALER_BUST;
            playerWins++;
            winStreak++;
            currentScore += 20;
        } else if (playerScore > dealerScore) {
            lastResult = RoundResult.PLAYER_WIN;
            playerWins++;
            winStreak++;
            currentScore += 20;
        } else if (playerScore < dealerScore) {
            lastResult = RoundResult.DEALER_WIN;
            dealerWins++;
            winStreak = 0;
        } else {
            lastResult = RoundResult.TIE;
            ties++;
            currentScore += 5;
        }

        if (winStreak > maxStreak) maxStreak = winStreak;
        if (currentScore > highScore) highScore = currentScore;
    }

    /**
     * 庄家按难度规则抽牌
     */
    private void executeDealerTurn() {
        while (true) {
            int score = calculateHandValue(dealerHand);
            boolean isSoft = isSoftHand(dealerHand);

            if (difficulty == DIFFICULTY_EASY) {
                // 简单：达到17即停牌
                if (score >= 17) break;
            } else if (difficulty == DIFFICULTY_MEDIUM) {
                // 经典：硬17停牌，软17(含A算11)继续要牌
                if (score > 17) break;
                if (score == 17 && !isSoft) break;
            } else {
                // 困难：根据玩家点数博弈，点数低于17必摸，17以上若落后且风险可控则博牌
                int playerScore = calculateHandValue(playerHand);
                if (score < 17) {
                    // 必摸
                } else if (score < playerScore && score <= 18 && isSoft) {
                    // 软18落后于玩家19或20，博一把
                } else {
                    break;
                }
            }
            dealerHand.add(drawCard());
        }
    }

    /**
     * 撤销最后一次要牌 (Undo Hit)
     */
    public boolean undoHit() {
        if (!playerHitHistory.isEmpty() && playerHand.size() > 2) {
            String lastCard = playerHand.remove(playerHand.size() - 1);
            playerHitHistory.remove(playerHitHistory.size() - 1);
            deck.add(lastCard); // 归还牌靴
            roundActive = true;
            lastResult = RoundResult.ONGOING;
            return true;
        }
        return false;
    }

    public boolean canUndo() {
        return !playerHitHistory.isEmpty() && playerHand.size() > 2;
    }

    /**
     * 基于二十一点经典数学基本策略 (Basic Strategy) 提供当前局面最优决策建议
     */
    public String getBasicStrategyHint() {
        if (!roundActive) return "对局已结束，请开启新局";
        if (playerHand.isEmpty() || dealerHand.isEmpty()) return "请等待发牌";

        int playerScore = calculateHandValue(playerHand);
        String dealerUpCard = dealerHand.get(0);
        int dealerUpVal = getCardValue(dealerUpCard);
        boolean isSoft = isSoftHand(playerHand);

        if (playerScore >= 21) {
            return "点数已达 21 点，必须停牌！";
        }

        if (isSoft) {
            if (playerScore >= 19) {
                return "软" + playerScore + "点极其强大，建议【停牌】守胜";
            } else if (playerScore == 18) {
                if (dealerUpVal == 2 || dealerUpVal == 7 || dealerUpVal == 8) {
                    return "庄家面牌 " + dealerUpVal + "，手持软18建议【停牌】";
                } else {
                    return "手持软18有容错空间，建议【要牌】冲击更高点数";
                }
            } else {
                return "软" + playerScore + "点有 A 垫底绝不爆牌，强烈建议【要牌】";
            }
        } else {
            if (playerScore >= 17) {
                return "硬" + playerScore + "点极易爆牌，基本策略强烈建议【停牌】";
            } else if (playerScore >= 13) {
                if (dealerUpVal >= 2 && dealerUpVal <= 6) {
                    return "庄家面牌弱(" + dealerUpVal + ")易爆牌，建议【停牌】稳妥静观其变";
                } else {
                    return "庄家面牌强(" + dealerUpVal + ")不易爆，我方13~16点劣势必须冒险【要牌】";
                }
            } else if (playerScore == 12) {
                if (dealerUpVal >= 4 && dealerUpVal <= 6) {
                    return "庄家明牌 " + dealerUpVal + " 处于爆牌高危区，建议【停牌】等待庄家自爆";
                } else {
                    return "12点遇庄家强牌，建议【要牌】";
                }
            } else if (playerScore == 10 || playerScore == 11) {
                return "起手 " + playerScore + " 点是黄金点数，命中 10 点率极高，必须【要牌】！";
            } else {
                return "点数仅 " + playerScore + " 点绝无爆牌风险，果断【要牌】";
            }
        }
    }

    public static int calculateHandValue(List<String> hand) {
        int value = 0;
        int aces = 0;
        for (String card : hand) {
            String rank = getCardRank(card);
            if (rank.equals("A")) {
                value += 11;
                aces++;
            } else if (rank.equals("K") || rank.equals("Q") || rank.equals("J") || rank.equals("10")) {
                value += 10;
            } else {
                try {
                    value += Integer.parseInt(rank);
                } catch (NumberFormatException ignored) {}
            }
        }
        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }
        return value;
    }

    public static boolean isSoftHand(List<String> hand) {
        int value = 0;
        int aces = 0;
        for (String card : hand) {
            String rank = getCardRank(card);
            if (rank.equals("A")) {
                value += 11;
                aces++;
            } else if (rank.equals("K") || rank.equals("Q") || rank.equals("J") || rank.equals("10")) {
                value += 10;
            } else {
                try {
                    value += Integer.parseInt(rank);
                } catch (NumberFormatException ignored) {}
            }
        }
        return aces > 0 && value <= 21;
    }

    public static boolean isBlackjack(List<String> hand) {
        return hand.size() == 2 && calculateHandValue(hand) == 21;
    }

    public static String getCardRank(String card) {
        if (card == null || card.length() < 2) return "";
        return card.substring(0, card.length() - 1);
    }

    public static String getCardSuit(String card) {
        if (card == null || card.length() < 2) return "";
        return card.substring(card.length() - 1);
    }

    public static int getCardValue(String card) {
        String rank = getCardRank(card);
        if (rank.equals("A")) return 11;
        if (rank.equals("K") || rank.equals("Q") || rank.equals("J") || rank.equals("10")) return 10;
        try {
            return Integer.parseInt(rank);
        } catch (Exception e) {
            return 0;
        }
    }

    // Getters and Setters
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

    public List<String> getPlayerHand() { return new ArrayList<>(playerHand); }
    public List<String> getDealerHand() { return new ArrayList<>(dealerHand); }
    public int getPlayerScore() { return calculateHandValue(playerHand); }
    public int getDealerScore() { return calculateHandValue(dealerHand); }
    public boolean isRoundActive() { return roundActive; }
    public RoundResult getLastResult() { return lastResult; }

    public int getPlayerWins() { return playerWins; }
    public int getDealerWins() { return dealerWins; }
    public int getTies() { return ties; }
    public int getWinStreak() { return winStreak; }
    public int getMaxStreak() { return maxStreak; }
    public int getCurrentScore() { return currentScore; }
    public int getHighScore() { return highScore; }
    public void setHighScore(int hs) { this.highScore = hs; }
    public int getDeckRemaining() { return deck.size(); }

    // Test helpers
    void setHandsForTest(List<String> player, List<String> dealer, boolean active) {
        this.playerHand.clear();
        this.playerHand.addAll(player);
        this.dealerHand.clear();
        this.dealerHand.addAll(dealer);
        this.roundActive = active;
    }

    void setRandomForTest(Random r) {
        this.random = r;
    }
}
