package com.gamecenter.app.doudizhu.logic;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static com.gamecenter.app.doudizhu.logic.TestCards.shuffled;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.model.Card;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P2 复查回归测试：HandDecomposer 高起点翅膀枚举修复。
 *
 * <p>修复前 options() 只枚举"主体起点 = 最小牌索引"的组合，当最小牌数量不足
 * （不能当主体）但可做高起点组合的翅膀时，最优解永远枚举不到。本类锁定三个
 * 已验证反例、路线合法性/乱序一致性、200 局随机手牌"新算法 ≤ 旧算法"自检
 * （旧算法为本文件内的移植版 {@link LegacyDecomposer}），以及 20 张混合牌型
 * 的计时哨兵。</p>
 */
public class HandDecomposerRegressionTest {

    /** 反例 1：{3,444,555,6} 真最优 444555+3+6 = 1 手（修复前给 3 手）。 */
    @Test
    public void airplaneWingsAboveSmallestCard() {
        assertEquals(1, HandDecomposer.minHands(of(3, 1, 4, 3, 5, 3, 6, 1)));
    }

    /** 反例 2：{3,4,5555} 真最优 5555+3+4 = 1 手（修复前给 3 手）。 */
    @Test
    public void quadWithWingsAboveSmallestCard() {
        assertEquals(1, HandDecomposer.minHands(of(3, 1, 4, 1, 5, 4)));
    }

    /** 反例 3：{3,4,5,666,777} 真最优 666777+3+4 + 单5 = 2 手（修复前给 3-4 手）。 */
    @Test
    public void airplaneWithWingsLeavesSingle() {
        assertEquals(2, HandDecomposer.minHands(of(3, 1, 4, 1, 5, 1, 6, 3, 7, 3)));
    }

    /** 三个反例的路线必须合法、恰好覆盖、手数等于 minHands，且与传入顺序无关。 */
    @Test
    public void counterexamplePathsAreValidAndOrderIndependent() {
        List<List<Card>> hands = Arrays.asList(
                of(3, 1, 4, 3, 5, 3, 6, 1),
                of(3, 1, 4, 1, 5, 4),
                of(3, 1, 4, 1, 5, 1, 6, 3, 7, 3));
        for (List<Card> hand : hands) {
            int expected = HandDecomposer.minHands(hand);
            List<Combo> path = HandDecomposer.bestDecomposition(hand);
            assertEquals("路线手数应等于最少手数", expected, path.size());
            int covered = 0;
            for (Combo combo : path) {
                assertNotNull("每手都必须是合法牌型", combo);
                covered += combo.size();
            }
            assertEquals("分解必须恰好覆盖全部手牌", hand.size(), covered);

            List<Combo> shuffledPath = HandDecomposer.bestDecomposition(shuffled(hand));
            assertEquals("乱序后手数一致", expected, shuffledPath.size());
        }
    }

    /**
     * 200 局随机 17 张手牌自检：新算法 minHands ≤ 修复前算法。
     * 枚举是旧枚举的严格超集（新增高起点翅膀路径，原路径不变），
     * 理论上单调不增，此处用固定种子防实现回归。
     */
    @Test
    public void newMinHandsNeverWorseThanLegacyOn200RandomHands() {
        Random random = new Random(2026L);
        for (int round = 0; round < 200; round++) {
            List<Card> deck = new ArrayList<>(Arrays.asList(Card.createFullDeck()));
            Collections.shuffle(deck, random);
            List<Card> hand = new ArrayList<>(deck.subList(0, 17));
            int[] counts = HandDecomposer.countsOf(hand);
            int modern = HandDecomposer.minHands(counts);
            int legacy = LegacyDecomposer.minHands(counts.clone());
            assertTrue("第 " + round + " 局新算法(" + modern
                            + ")不得劣于旧算法(" + legacy + ")",
                    modern <= legacy);
        }
    }

    /**
     * 20 张混合牌型（三组四张 + 两组炸弹，含飞机与四带两对可能）分解计时哨兵。
     * 真最优 2 手：333444555+3+4+5（飞机带翅膀）+ TTTT+JJJJ（四带两对）。
     */
    @Test
    public void twentyCardMixedHandDecomposesFast() {
        List<Card> hand = of(3, 4, 4, 4, 5, 4, 10, 4, 11, 4);
        long start = System.nanoTime();
        int hands = HandDecomposer.minHands(hand);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals("333344445555+TTTT+JJJJ 应 2 手出完", 2, hands);
        assertTrue("20 张混合分解耗时 " + elapsedMs + "ms 过长", elapsedMs < 2000);
    }

    /**
     * 修复前枚举的移植版（仅"主体起点 = 最小牌索引"路径），作为 200 局对比基准。
     * 仅用于回归对照，与修复前的 HandDecomposer 逻辑逐分支一致。
     */
    private static final class LegacyDecomposer {

        private static final int SIZE = HandDecomposer.COUNT_SIZE;
        private static final int MAX_SEQ = 11;   // A(14) - 3
        private static final int IDX_TWO = 12;   // 2(15) - 3

        private LegacyDecomposer() {}

        static int minHands(int[] counts) {
            return solve(counts, new HashMap<Long, Integer>());
        }

        private static int solve(int[] counts, Map<Long, Integer> memo) {
            int remaining = total(counts);
            if (remaining == 0) return 0;
            long key = encode(counts);
            Integer cached = memo.get(key);
            if (cached != null) return cached;

            int idx = smallestIdx(counts);
            int best = remaining; // 上界：全按单张出
            for (int[] delta : options(counts, idx)) {
                apply(counts, delta);
                int sub = solve(counts, memo);
                undo(counts, delta);
                if (1 + sub < best) best = 1 + sub;
            }
            memo.put(key, best);
            return best;
        }

        private static List<int[]> options(int[] counts, int idx) {
            List<int[]> out = new ArrayList<>();
            int c = counts[idx];

            // 顺子
            for (int len = 5; idx + len - 1 <= MAX_SEQ && len <= 12; len++) {
                if (hasRun(counts, idx, len, 1)) {
                    int[] d = new int[SIZE];
                    for (int i = idx; i < idx + len; i++) d[i] = 1;
                    out.add(d);
                }
            }
            // 连对
            for (int len = 3; idx + len - 1 <= MAX_SEQ && len <= 10; len++) {
                if (hasRun(counts, idx, len, 2)) {
                    int[] d = new int[SIZE];
                    for (int i = idx; i < idx + len; i++) d[i] = 2;
                    out.add(d);
                }
            }
            // 飞机（纯 / 带翅膀），主体起点 = idx
            for (int len = 2; idx + len - 1 <= MAX_SEQ && len <= 6; len++) {
                if (!hasRun(counts, idx, len, 3)) continue;
                int[] d = new int[SIZE];
                for (int i = idx; i < idx + len; i++) d[i] = 3;
                out.add(d);
                int[] rest = counts.clone();
                for (int i = idx; i < idx + len; i++) rest[i] -= 3;
                int[] ws = smallestSingles(rest, len);
                if (ws.length == len) {
                    int[] d2 = d.clone();
                    for (int wi : ws) d2[wi] += 1;
                    out.add(d2);
                }
                int[] wp = smallestPairs(rest, len);
                if (wp.length == len) {
                    int[] d2 = d.clone();
                    for (int wi : wp) d2[wi] += 2;
                    out.add(d2);
                }
            }
            // 三张 / 三带一 / 三带二
            if (c >= 3) {
                int[] rest = counts.clone();
                rest[idx] = c - 3;
                int ks = -1;
                for (int i = 0; i < SIZE; i++) {
                    if (i != idx && rest[i] > 0) { ks = i; break; }
                }
                if (ks >= 0) {
                    int[] d = new int[SIZE];
                    d[idx] = 3;
                    d[ks] = 1;
                    out.add(d);
                }
                int kp = -1;
                for (int i = 0; i < SIZE; i++) {
                    if (i != idx && rest[i] >= 2) { kp = i; break; }
                }
                if (kp >= 0) {
                    int[] d = new int[SIZE];
                    d[idx] = 3;
                    d[kp] = 2;
                    out.add(d);
                }
                int[] d = new int[SIZE];
                d[idx] = 3;
                out.add(d);
            }
            // 炸弹 / 四带两单 / 四带两对
            if (c == 4) {
                int[] rest = counts.clone();
                rest[idx] = 0;
                int[] s2 = smallestSingles(rest, 2);
                if (s2.length == 2) {
                    int[] d = new int[SIZE];
                    d[idx] = 4;
                    for (int wi : s2) d[wi] += 1;
                    out.add(d);
                }
                int[] p2 = smallestPairs(rest, 2);
                if (p2.length == 2) {
                    int[] d = new int[SIZE];
                    d[idx] = 4;
                    for (int wi : p2) d[wi] += 2;
                    out.add(d);
                }
                int[] d = new int[SIZE];
                d[idx] = 4;
                out.add(d);
            }
            // 对子 / 单张
            if (c >= 2) {
                int[] d = new int[SIZE];
                d[idx] = 2;
                out.add(d);
            }
            int[] d1 = new int[SIZE];
            d1[idx] = 1;
            out.add(d1);
            // 王炸
            if (idx == 13 && counts[14] >= 1) {
                int[] d = new int[SIZE];
                d[13] = 1;
                d[14] = 1;
                out.add(d);
            }
            return out;
        }

        private static int[] smallestSingles(int[] counts, int k) {
            int[] out = new int[k];
            int[] rest = counts.clone();
            for (int n = 0; n < k; n++) {
                int pick = -1;
                for (int i = 0; i < SIZE; i++) {
                    if (rest[i] > 0) { pick = i; break; }
                }
                if (pick < 0) return new int[0];
                out[n] = pick;
                rest[pick]--;
            }
            return out;
        }

        private static int[] smallestPairs(int[] counts, int k) {
            int[] out = new int[k];
            int[] rest = counts.clone();
            for (int n = 0; n < k; n++) {
                int pick = -1;
                for (int i = 0; i < SIZE; i++) {
                    if (rest[i] >= 2) { pick = i; break; }
                }
                if (pick < 0) return new int[0];
                out[n] = pick;
                rest[pick] -= 2;
            }
            return out;
        }

        private static boolean hasRun(int[] counts, int idx, int len, int perRank) {
            for (int i = idx; i < idx + len; i++) {
                if (counts[i] < perRank) return false;
            }
            return true;
        }

        private static void apply(int[] counts, int[] delta) {
            for (int i = 0; i < SIZE; i++) counts[i] -= delta[i];
        }

        private static void undo(int[] counts, int[] delta) {
            for (int i = 0; i < SIZE; i++) counts[i] += delta[i];
        }

        private static int total(int[] counts) {
            int s = 0;
            for (int c : counts) s += c;
            return s;
        }

        private static int smallestIdx(int[] counts) {
            for (int i = 0; i < SIZE; i++) {
                if (counts[i] > 0) return i;
            }
            return -1;
        }

        private static long encode(int[] counts) {
            long key = 0;
            for (int i = 0; i < SIZE; i++) {
                key |= ((long) counts[i]) << (i * 3);
            }
            return key;
        }
    }
}
