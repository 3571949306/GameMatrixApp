package com.gamecenter.app.doudizhu.logic;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static org.junit.Assert.assertEquals;

import com.gamecenter.app.doudizhu.model.Card;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * HandDecomposer 与精确枚举器对拍（P2 第二轮复查）。
 *
 * <p>精确枚举器 {@link ExactDecomposer} 与主求解器同款 DFS + 记忆化框架，但带
 * 翅膀/挂件组合<b>穷举全部翅膀多重子集</b>（不设代表元上限），对 ≤12 张手牌
 * 给出真实最少手数：任意分解都可重排为"逐手覆盖当前最小牌"的顺序，故从最小牌
 * 处展开不丢最优解。断言：</p>
 * <ul>
 *   <li>四个翅膀反例（与 HandDecomposerRegressionTest 同源）minHands == 精确解；</li>
 *   <li>随机 100 副 9-12 张手牌（9/10/11/12 各 25 副，种子 20260901 固定可复现）
 *       minHands == 精确解。</li>
 * </ul>
 * <p>对拍一致即兑现 HandDecomposer javadoc 中"多代表元翅膀策略在回归测试语料上
 * 与精确解对拍一致"的承诺；翅膀策略集未来若有改动，本类是权威判据。</p>
 */
public class HandDecomposerExactnessTest {

    /** 四个翅膀反例：minHands 必须等于精确解（3 / 3 / 6 / 2）。 */
    @Test
    public void counterexamplesMatchExactSolver() {
        List<List<Card>> hands = Arrays.asList(
                of(5, 4, 6, 2, 7, 2, 8, 3, 9, 1, 10, 1, 11, 1, 12, 1, 15, 2),
                of(3, 3, 4, 1, 7, 3, 8, 1, 9, 3, 10, 3, 12, 1, 14, 2),
                of(3, 3, 4, 1, 5, 2, 6, 1, 7, 1, 8, 1, 11, 3, 12, 1, 13, 2, 15, 1, 16, 1),
                of(3, 1, 4, 3, 5, 3, 6, 1, 7, 2, 8, 1, 9, 1, 10, 1, 11, 1));
        int[] expected = {3, 3, 6, 2};
        Map<Long, Integer> memo = new HashMap<>();
        for (int i = 0; i < hands.size(); i++) {
            List<Card> hand = hands.get(i);
            int exact = ExactDecomposer.minHands(HandDecomposer.countsOf(hand), memo);
            assertEquals("反例 " + (i + 1) + " 精确解应为 " + expected[i], expected[i], exact);
            assertEquals("反例 " + (i + 1) + " minHands 应等于精确解",
                    exact, HandDecomposer.minHands(hand));
        }
    }

    /**
     * 随机 100 副 9-12 张手牌（9/10/11/12 各 25 副，固定种子可复现）：
     * minHands 必须与精确枚举器一致。memo 跨手共享（counts 键控）以控制耗时。
     */
    @Test
    public void randomHandsMatchExactSolver() {
        Random random = new Random(20260901L);
        Map<Long, Integer> memo = new HashMap<>();
        Map<Integer, Integer> sizeDist = new TreeMap<>();
        for (int round = 0; round < 100; round++) {
            int size = 9 + round % 4;
            List<Card> deck = new ArrayList<>(Arrays.asList(Card.createFullDeck()));
            Collections.shuffle(deck, random);
            List<Card> hand = new ArrayList<>(deck.subList(0, size));
            int exact = ExactDecomposer.minHands(HandDecomposer.countsOf(hand), memo);
            int modern = HandDecomposer.minHands(hand);
            sizeDist.merge(size, 1, Integer::sum);
            assertEquals("第 " + round + " 副（" + size + " 张：" + weights(hand)
                    + "）minHands 应等于精确解", exact, modern);
        }
        assertEquals("9 张应 25 副", 25, sizeDist.get(9).intValue());
        assertEquals("10 张应 25 副", 25, sizeDist.get(10).intValue());
        assertEquals("11 张应 25 副", 25, sizeDist.get(11).intValue());
        assertEquals("12 张应 25 副", 25, sizeDist.get(12).intValue());
    }

    private static String weights(List<Card> hand) {
        StringBuilder sb = new StringBuilder();
        for (Card c : hand) {
            sb.append(c.getWeight()).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * 精确枚举器（仅 ≤12 张对拍用）：每个状态穷举"覆盖最小牌"的全部出完方式，
     * 飞机/三带/四带的翅膀用 {@link #enumWings} 枚举全部多重子集（同权重可重复
     * 取），故 minHands 即真实最优；记忆化按 counts 键控，可跨手共享。
     */
    static final class ExactDecomposer {

        private static final int SIZE = HandDecomposer.COUNT_SIZE;
        private static final int MAX_SEQ = 11;   // A(14) - 3
        private static final int IDX_TWO = 12;   // 2(15) - 3

        private ExactDecomposer() {}

        static int minHands(int[] counts, Map<Long, Integer> memo) {
            return solve(counts, memo);
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
            // 顺子 / 连对
            for (int len = 5; idx + len - 1 <= MAX_SEQ && len <= 12; len++) {
                if (hasRun(counts, idx, len, 1)) out.add(runDelta(idx, len, 1));
            }
            for (int len = 3; idx + len - 1 <= MAX_SEQ && len <= 10; len++) {
                if (hasRun(counts, idx, len, 2)) out.add(runDelta(idx, len, 2));
            }
            // 飞机（主体起点 = idx）带全部翅膀多重子集
            for (int len = 2; idx + len - 1 <= MAX_SEQ && len <= 6; len++) {
                if (!hasRun(counts, idx, len, 3)) continue;
                int[] rest = counts.clone();
                subtractRun(rest, idx, len, 3);
                appendWingedDeltas(out, idx, len, rest);
            }
            // 高起点三带 / 四带 / 飞机（主体起点 j > idx，idx 处牌做翅膀/挂件）
            for (int j = idx + 1; j <= IDX_TWO; j++) {
                if (counts[j] >= 3) {
                    int[] rest = counts.clone();
                    rest[j] -= 3;
                    for (int i = 0; i < SIZE; i++) {
                        if (rest[i] >= 1) out.add(kickerDelta(j, i, 1));
                    }
                    for (int i = 0; i < SIZE; i++) {
                        if (rest[i] >= 2) out.add(kickerDelta(j, i, 2));
                    }
                }
                if (counts[j] == 4) {
                    int[] rest = counts.clone();
                    rest[j] = 0;
                    enumWings(out, rest, j, 0, 1, 2, 0, new int[2], 0);
                    enumWings(out, rest, j, 0, 2, 2, 0, new int[2], 0);
                }
            }
            for (int len = 2; len <= 6; len++) {
                for (int j = idx + 1; j + len - 1 <= MAX_SEQ; j++) {
                    if (!hasRun(counts, j, len, 3)) continue;
                    int[] rest = counts.clone();
                    subtractRun(rest, j, len, 3);
                    appendWingedDeltas(out, j, len, rest);
                }
            }
            // 三张 / 三带一 / 三带二（挂件穷举，含 counts==4 时同权重挂件=炸弹等价）
            if (c >= 3) {
                int[] rest = counts.clone();
                rest[idx] = c - 3;
                for (int i = 0; i < SIZE; i++) {
                    if (rest[i] >= 1) out.add(kickerDelta(idx, i, 1));
                }
                for (int i = 0; i < SIZE; i++) {
                    if (rest[i] >= 2) out.add(kickerDelta(idx, i, 2));
                }
                out.add(singleDelta(idx, 3));
            }
            // 炸弹 / 四带两单 / 四带两对（翅膀穷举多重子集）
            if (c == 4) {
                int[] rest = counts.clone();
                rest[idx] = 0;
                enumWings(out, rest, idx, 0, 1, 2, 0, new int[2], 0);
                enumWings(out, rest, idx, 0, 2, 2, 0, new int[2], 0);
                out.add(singleDelta(idx, 4));
            }
            // 对子 / 单张
            if (c >= 2) out.add(singleDelta(idx, 2));
            out.add(singleDelta(idx, 1));
            // 王炸
            if (idx == 13 && counts[14] >= 1) {
                int[] d = new int[SIZE];
                d[13] = 1;
                d[14] = 1;
                out.add(d);
            }
            return out;
        }

        /** 飞机带翅膀：三张段 + len 份单张（need=1）或 len 份对子（need=2）的全部多重子集。 */
        private static void appendWingedDeltas(List<int[]> out, int start, int len, int[] rest) {
            enumWings(out, rest, start, len, 1, len, 0, new int[len], 0);
            enumWings(out, rest, start, len, 2, len, 0, new int[len], 0);
        }

        /**
         * 穷举 rest 中取 k 份、每份 need 张的翅膀多重集（fromIdx 起可重复取同一
         * 权重）；len &gt; 0 时 delta 为飞机三张段 + 翅膀，len == 0 时为四带挂件。
         */
        private static void enumWings(List<int[]> out, int[] rest, int start, int len, int need,
                                      int k, int fromIdx, int[] cur, int filled) {
            if (filled == k) {
                int[] d = new int[SIZE];
                if (len > 0) {
                    for (int i = start; i < start + len; i++) d[i] = 3;
                } else {
                    d[start] = 4;
                }
                for (int wi : cur) d[wi] += need;
                out.add(d);
                return;
            }
            for (int i = fromIdx; i < SIZE; i++) {
                if (rest[i] >= need) {
                    rest[i] -= need;
                    cur[filled] = i;
                    enumWings(out, rest, start, len, need, k, i, cur, filled + 1);
                    rest[i] += need;
                }
            }
        }

        /** 三带/四带挂件 delta：主体三张/炸弹 + 挂件 need 张。 */
        private static int[] kickerDelta(int main, int wing, int need) {
            int[] d = new int[SIZE];
            d[main] = 3;
            d[wing] += need;
            return d;
        }

        private static int[] runDelta(int idx, int len, int per) {
            int[] d = new int[SIZE];
            for (int i = idx; i < idx + len; i++) d[i] = per;
            return d;
        }

        private static int[] singleDelta(int idx, int n) {
            int[] d = new int[SIZE];
            d[idx] = n;
            return d;
        }

        private static boolean hasRun(int[] counts, int idx, int len, int per) {
            for (int i = idx; i < idx + len; i++) {
                if (counts[i] < per) return false;
            }
            return true;
        }

        private static void subtractRun(int[] counts, int idx, int len, int per) {
            for (int i = idx; i < idx + len; i++) {
                counts[i] -= per;
            }
        }

        private static void apply(int[] counts, int[] delta) {
            for (int i = 0; i < SIZE; i++) counts[i] -= delta[i];
        }

        private static void undo(int[] counts, int[] delta) {
            for (int i = 0; i < SIZE; i++) counts[i] += delta[i];
        }

        private static int total(int[] counts) {
            int sum = 0;
            for (int c : counts) sum += c;
            return sum;
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
