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
 * P2 复查回归测试：HandDecomposer 高起点翅膀枚举修复 + 翅膀多代表元策略。
 *
 * <p>第一轮修复锁定三个"高起点翅膀"反例；第二轮复查实证"翅膀一律取剩余最小"
 * 在残局手数上不保证最优（翅膀把能组成顺子/连对的小牌当挂件消耗、把 2/王等
 * 死牌留在残局会多花手数），新增四个最小手数反例（须严格优于旧枚举）与
 * 200 局随机手牌"存在严格更优局"哨兵。精确解对拍见
 * {@link HandDecomposerExactnessTest}，路线合法性/乱序一致性、20 张混合牌型
 * 的计时哨兵也保留在本类。</p>
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

    // ===== P2 第二轮复查反例：翅膀"一律取剩余最小"不保证残局手数最优 =====

    /**
     * 复查反例 1：5555 66 77 888 9 T J Q 22——"剩余最小"翅膀把 66/77 随 5555
     * 消化、死牌 22 留在残局，旧枚举 4+ 手；真最优 3 手（5555+22 / 667788 / 89TJQ），
     * 由"死牌优先"代表元命中 5555+22。新算法须严格优于旧枚举（防 no-op 回归）。
     */
    @Test
    public void counterexampleQuadWingsPreferDeadTwos() {
        int[] counts = HandDecomposer.countsOf(
                of(5, 4, 6, 2, 7, 2, 8, 3, 9, 1, 10, 1, 11, 1, 12, 1, 15, 2));
        assertEquals("真最优 3 手：5555+22 / 667788 / 89TJQ", 3,
                HandDecomposer.minHands(counts.clone()));
        assertTrue("新算法应严格优于旧枚举",
                HandDecomposer.minHands(counts.clone()) < LegacyDecomposer.minHands(counts.clone()));
    }

    /**
     * 复查反例 2：333 4 777 8 999 TTT Q AA——"剩余最小"让 777 带上最小对 99、
     * 拆散 999TTT 飞机所需结构，旧枚举 4 手；真最优 3 手（333+4 / 777+AA /
     * 999TTT+8+Q），AA 由"死牌优先（孤立档）"/"剩余最大"挂件代表元命中。
     */
    @Test
    public void counterexampleTrioPairKickerPrefersIsolatedPair() {
        int[] counts = HandDecomposer.countsOf(
                of(3, 3, 4, 1, 7, 3, 8, 1, 9, 3, 10, 3, 12, 1, 14, 2));
        assertEquals("真最优 3 手：333+4 / 777+AA / 999TTT+8+Q", 3,
                HandDecomposer.minHands(counts.clone()));
        assertTrue("新算法应严格优于旧枚举",
                HandDecomposer.minHands(counts.clone()) < LegacyDecomposer.minHands(counts.clone()));
    }

    /**
     * 复查反例 3：333 4 55 6 7 8 JJJ Q KK 2 小王——"剩余最小"挂件 333+4 拆散
     * 45678 顺子潜力，得 7 手；真最优 6 手（如 333+5 / 45678 / JJJ+Q / KK / 2 /
     * 小王，或等价的 333+2 / 333+小王 变体），由"死牌优先"/"剩余最大"挂件命中。
     */
    @Test
    public void counterexampleTrioKickerKeepsStraightPotential() {
        int[] counts = HandDecomposer.countsOf(
                of(3, 3, 4, 1, 5, 2, 6, 1, 7, 1, 8, 1, 11, 3, 12, 1, 13, 2, 15, 1, 16, 1));
        assertEquals("真最优 6 手", 6, HandDecomposer.minHands(counts.clone()));
        assertTrue("新算法应严格优于旧枚举",
                HandDecomposer.minHands(counts.clone()) < LegacyDecomposer.minHands(counts.clone()));
    }

    /**
     * 复查反例 4：3 444 555 6 77 8 9 T J——"剩余最小"翅膀取 444555+3+6，把 7
     * 留成单张，得 3 手；真最优 2 手（444555+3+7 / 6789TJ 顺子）。7 既非死牌也非
     * 最大牌，由"死牌优先"的冗余档命中（7 有两张，取走一张不破坏连型潜力）。
     */
    @Test
    public void counterexampleAirplaneWingsPreferRedundantRank() {
        int[] counts = HandDecomposer.countsOf(
                of(3, 1, 4, 3, 5, 3, 6, 1, 7, 2, 8, 1, 9, 1, 10, 1, 11, 1));
        assertEquals("真最优 2 手：444555+3+7 / 6789TJ", 2,
                HandDecomposer.minHands(counts.clone()));
        assertTrue("新算法应严格优于旧枚举",
                HandDecomposer.minHands(counts.clone()) < LegacyDecomposer.minHands(counts.clone()));
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
     * 枚举是旧枚举的严格超集（高起点翅膀路径 + 翅膀多代表元，原路径不变），
     * 理论上单调不增，此处用固定种子防实现回归。
     *
     * <p>语料额外并入四个翅膀反例：它们对旧枚举必然严格更优，使"存在严格更优局"
     * 哨兵确定性地守护翅膀多代表元策略不退化为 no-op（纯随机语料不能保证命中
     * 反例形态）。</p>
     */
    @Test
    public void newMinHandsNeverWorseThanLegacyOn200RandomHands() {
        List<int[]> corpus = new ArrayList<>();
        // 已知翅膀反例（对新枚举必严格更优，防 no-op 哨兵的确定性锚点）
        corpus.add(HandDecomposer.countsOf(of(5, 4, 6, 2, 7, 2, 8, 3, 9, 1, 10, 1, 11, 1, 12, 1, 15, 2)));
        corpus.add(HandDecomposer.countsOf(of(3, 3, 4, 1, 7, 3, 8, 1, 9, 3, 10, 3, 12, 1, 14, 2)));
        corpus.add(HandDecomposer.countsOf(of(3, 3, 4, 1, 5, 2, 6, 1, 7, 1, 8, 1, 11, 3, 12, 1, 13, 2, 15, 1, 16, 1)));
        corpus.add(HandDecomposer.countsOf(of(3, 1, 4, 3, 5, 3, 6, 1, 7, 2, 8, 1, 9, 1, 10, 1, 11, 1)));
        // 200 局随机 17 张
        Random random = new Random(2026L);
        for (int round = 0; round < 200; round++) {
            List<Card> deck = new ArrayList<>(Arrays.asList(Card.createFullDeck()));
            Collections.shuffle(deck, random);
            corpus.add(HandDecomposer.countsOf(new ArrayList<>(deck.subList(0, 17))));
        }

        int strictWins = 0;
        for (int round = 0; round < corpus.size(); round++) {
            int[] counts = corpus.get(round);
            int modern = HandDecomposer.minHands(counts.clone());
            int legacy = LegacyDecomposer.minHands(counts.clone());
            assertTrue("第 " + round + " 局新算法(" + modern
                            + ")不得劣于旧算法(" + legacy + ")",
                    modern <= legacy);
            if (modern < legacy) strictWins++;
        }
        assertTrue("对比语料中应存在新算法严格更优的局（防 no-op 回归），实际 "
                        + strictWins + " 局",
                strictWins >= 1);
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
