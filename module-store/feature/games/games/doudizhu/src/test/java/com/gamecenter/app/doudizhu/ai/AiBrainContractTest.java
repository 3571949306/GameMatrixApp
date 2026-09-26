package com.gamecenter.app.doudizhu.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.logic.Combo;
import com.gamecenter.app.doudizhu.logic.HandDecomposer;
import com.gamecenter.app.doudizhu.model.Card;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * AI 契约测试（对齐 AGENTS.md 象棋/围棋契约风格）：
 * 固定种子随机局面下，三档难度的全部产出必须满足——
 * 自由出牌必出、着法为合法牌型、能压过上家、牌全部来自手牌；非法着法 0 容忍。
 */
public class AiBrainContractTest {

    private static final int ROUNDS = 200;

    @Test
    public void aiMovesNeverViolateContract() {
        int violations = 0;
        for (int difficulty = AiBrain.DIFFICULTY_EASY;
             difficulty <= AiBrain.DIFFICULTY_HARD; difficulty++) {
            violations += runContractRounds(difficulty, 1000L + difficulty);
        }
        assertEquals("AI 契约违规必须为 0（DDZ_AI_CONTRACT_VIOLATION）", 0, violations);
    }

    @Test
    public void leadAlwaysProducesMove() {
        Random random = new Random(2026L);
        for (int i = 0; i < ROUNDS; i++) {
            List<Card> hand = randomHand(random, 17);
            List<Card> history = randomHistory(random, hand);
            for (int difficulty = AiBrain.DIFFICULTY_EASY;
                 difficulty <= AiBrain.DIFFICULTY_HARD; difficulty++) {
                List<Card> move = AiBrain.decidePlay(hand, null, landlordContext(), difficulty,
                        random, history);
                assertNotNull("自由出牌必须出牌: round=" + i + " diff=" + difficulty, move);
                assertTrue(move.size() <= hand.size());
                assertNotNull("首发必须是合法牌型",
                        Combo.of(move));
            }
        }
    }

    /** 跑一轮契约：返回违规次数。 */
    private int runContractRounds(int difficulty, long seed) {
        Random random = new Random(seed);
        int violations = 0;
        for (int i = 0; i < ROUNDS; i++) {
            List<Card> hand = randomHand(random, 17);
            List<Card> rest = deckMinus(hand, random);
            List<Card> history = randomHistory(random, hand);
            List<Card> previous = randomPrevious(rest, random);

            List<Card> move = AiBrain.decidePlay(hand, previous, landlordContext(),
                    difficulty, random, history);
            if (move == null) {
                // pass 仅在跟牌时合法；自由出牌必须出
                if (previous == null) {
                    violations++;
                }
                continue;
            }
            if (previous == null && move.isEmpty()) {
                violations++;
                continue;
            }
            // 着法必须是合法牌型
            Combo combo = Combo.of(move);
            if (combo == null) {
                violations++;
                continue;
            }
            // 必须能压过上家
            Combo prev = previous == null || previous.isEmpty()
                    ? null : Combo.of(previous);
            if (!combo.beats(prev)) {
                violations++;
                continue;
            }
            // 出的牌必须全部来自手牌（多重集）
            if (!isSubsetOfHand(move, hand)) {
                violations++;
            }
        }
        return violations;
    }

    // ============ 局面生成工具 ============

    private static AiBrain.GameContext landlordContext() {
        int[] roles = {AiBrain.ROLE_LANDLORD, AiBrain.ROLE_FARMER, AiBrain.ROLE_FARMER};
        return new AiBrain.GameContext(AiBrain.ROLE_LANDLORD, 0, roles, 0, 17,
                -1, -1, -1, 1, 17);
    }

    /** 从一副牌随机抽 n 张作为手牌。 */
    private static List<Card> randomHand(Random random, int n) {
        List<Card> deck = new ArrayList<>(java.util.Arrays.asList(Card.createFullDeck()));
        Collections.shuffle(deck, random);
        return new ArrayList<>(deck.subList(0, n));
    }

    /** 剩余的牌（随机顺序），供 previous 生成。 */
    private static List<Card> deckMinus(List<Card> hand, Random random) {
        List<Card> rest = new ArrayList<>(java.util.Arrays.asList(Card.createFullDeck()));
        rest.removeAll(hand);
        Collections.shuffle(rest, random);
        return rest;
    }

    /** 随机已出牌历史（0-20 张，与手牌不相交）。 */
    private static List<Card> randomHistory(Random random, List<Card> hand) {
        List<Card> pool = new ArrayList<>(java.util.Arrays.asList(Card.createFullDeck()));
        pool.removeAll(hand);
        int n = random.nextInt(21);
        n = Math.min(n, pool.size());
        List<Card> history = new ArrayList<>(pool.subList(0, n));
        return history;
    }

    /** 从 rest 随机抽一小撮牌，是合法牌型就当上家出的牌，否则返回 null。 */
    private static List<Card> randomPrevious(List<Card> rest, Random random) {
        for (int attempt = 0; attempt < 8; attempt++) {
            if (rest.isEmpty()) return null;
            int n = 1 + random.nextInt(Math.min(8, rest.size()));
            List<Card> pick = new ArrayList<>(rest.subList(0, n));
            if (Combo.of(pick) != null) {
                return pick;
            }
            Collections.shuffle(rest, random);
        }
        return null;
    }

    private static boolean isSubsetOfHand(List<Card> move, List<Card> hand) {
        List<Card> remaining = new ArrayList<>(hand);
        for (Card card : move) {
            if (!remaining.remove(card)) {
                return false;
            }
        }
        return true;
    }
}
