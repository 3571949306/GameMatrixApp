package com.gamecenter.app.doudizhu.logic;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.model.Rank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手牌最少手数分解器（P2 AI 内核）。
 *
 * <p>回答一个问题："这手牌最少几次出完？"并给出一条达到该手数的分解路线。
 * 采用 DFS + 记忆化：每次固定从"剩余牌中最小权重"处展开（保证同一 counts 状态
 * 只被求解一次），候选组合覆盖全部 13 种牌型（单/对/三/三带一/三带二/炸弹/王炸/
 * 顺子/连对/飞机/飞机带翅膀/四带二单/四带二对）。17 张牌规模毫秒级。</p>
 *
 * <p>纯 JVM 实现：不 import 任何 Android 类，供 {@code ai.AiBrain} 做首发规划、
 * 接牌拆解代价评估与叫分手牌评估，也可 JUnit 单测。</p>
 *
 * <p>索引约定：{@code idx = weight - 3}，即 0..11 对应 3..A（可入顺），12 对应 2，
 * 13 小王，14 大王。</p>
 */
public final class HandDecomposer {

    /** counts 数组长度（权重 3..17 共 15 档） */
    public static final int COUNT_SIZE = 15;

    /** 可入顺的最大权重（A=14）对应的索引 */
    private static final int MAX_SEQ_IDX = Rank.ACE.getWeight() - 3;
    private static final int IDX_SMALL_JOKER = Rank.SMALL_JOKER.getWeight() - 3;
    private static final int IDX_BIG_JOKER = Rank.BIG_JOKER.getWeight() - 3;

    private HandDecomposer() {}

    // ============ 对外 API ============

    /**
     * 计算手牌最少出完手数。
     *
     * @param hand 手牌（任意顺序）
     * @return 最少手数；空手牌返回 0
     */
    public static int minHands(List<Card> hand) {
        if (hand == null || hand.isEmpty()) return 0;
        return minHands(countsOf(hand));
    }

    /**
     * 计算某分布的最少出完手数（基于 counts 的核心入口，供重复评估复用）。
     */
    public static int minHands(int[] counts) {
        Map<Long, Integer> memo = new HashMap<>();
        return solve(counts, memo);
    }

    /**
     * 给出一条最少手数分解路线（各手均为合法 {@link Combo}，拼起来恰好覆盖手牌）。
     *
     * <p>同为最优时选择哪条路线由展开顺序决定（确定性的）：优先更"整"的长组合，
     * 便于 AI 首发从中挑选。空手牌返回空列表。</p>
     */
    public static List<Combo> bestDecomposition(List<Card> hand) {
        List<Combo> out = new ArrayList<>();
        if (hand == null || hand.isEmpty()) return out;

        int[] counts = countsOf(hand);
        // 每个权重一份可取卡队列（按花色序），取牌即从队头弹出
        Map<Integer, List<Card>> pool = new HashMap<>();
        for (Card c : hand) {
            pool.computeIfAbsent(c.getWeight(), k -> new ArrayList<>()).add(c);
        }
        for (List<Card> list : pool.values()) {
            list.sort(Card::compareTo);
        }

        Map<Long, Integer> memo = new HashMap<>();
        while (total(counts) > 0) {
            int start = smallestIdx(counts);
            Option option = pickBestOption(counts, start, memo);
            apply(counts, option);
            Combo combo = materialize(option, pool);
            if (combo != null) {
                out.add(combo);
            }
        }
        return out;
    }

    // ============ DFS + 记忆化 ============

    private static int solve(int[] counts, Map<Long, Integer> memo) {
        int remaining = total(counts);
        if (remaining == 0) return 0;

        long key = encode(counts);
        Integer cached = memo.get(key);
        if (cached != null) return cached;

        int start = smallestIdx(counts);
        int best = remaining; // 上界：全按单张出
        for (Option option : options(counts, start)) {
            apply(counts, option);
            int sub = solve(counts, memo);
            undo(counts, option);
            if (1 + sub < best) {
                best = 1 + sub;
            }
        }
        memo.put(key, best);
        return best;
    }

    /**
     * 贪心重建路径：在 start 处选一个使 {@code 1 + solve(next) == solve(cur)} 的选项。
     * 选项枚举顺序固定（长组合在前），保证结果确定性。
     */
    private static Option pickBestOption(int[] counts, int start, Map<Long, Integer> memo) {
        int cur = solve(counts, memo);
        for (Option option : options(counts, start)) {
            apply(counts, option);
            int sub = solve(counts, memo);
            undo(counts, option);
            if (1 + sub == cur) {
                return option;
            }
        }
        // 兜底（理论上不可达）：拆最小单张
        int[] delta = new int[COUNT_SIZE];
        delta[start] = 1;
        return new Option(CardType.SINGLE, start, 1, Combo.WINGS_NONE, delta, new int[0]);
    }

    // ============ 选项枚举 ============

    /** 一个分解选项：牌型 + 主权重索引 + delta（从 counts 扣除的分布）+ 翅膀索引。 */
    private static final class Option {
        final CardType type;
        final int mainIdx;
        final int length;
        final int wingsKind;
        final int[] delta;
        /** 翅膀/挂件权重索引（单翅膀每项 1 张；对翅膀每项 2 张） */
        final int[] wingIdx;

        Option(CardType type, int mainIdx, int length, int wingsKind, int[] delta, int[] wingIdx) {
            this.type = type;
            this.mainIdx = mainIdx;
            this.length = length;
            this.wingsKind = wingsKind;
            this.delta = delta;
            this.wingIdx = wingIdx;
        }
    }

    /**
     * 枚举"以 idx 为最小权重"的全部出完方式。
     * 顺序：顺子 → 连对 → 飞机 → 飞机带翅膀 → 三带 → 炸弹/四带 → 对 → 单 → 王炸。
     */
    private static List<Option> options(int[] counts, int idx) {
        List<Option> out = new ArrayList<>();
        int c = counts[idx];

        // 顺子（以 idx 开头，5..12 连单）
        for (int len = 5; idx + len - 1 <= MAX_SEQ_IDX && len <= 12; len++) {
            if (hasRun(counts, idx, len, 1)) {
                out.add(runOption(CardType.STRAIGHT, idx, len, 1, Combo.WINGS_NONE));
            }
        }
        // 连对
        for (int len = 3; idx + len - 1 <= MAX_SEQ_IDX && len <= 10; len++) {
            if (hasRun(counts, idx, len, 2)) {
                out.add(runOption(CardType.STRAIGHT_PAIRS, idx, len, 2, Combo.WINGS_NONE));
            }
        }
        // 纯飞机与带翅膀飞机
        for (int len = 2; idx + len - 1 <= MAX_SEQ_IDX && len <= 6; len++) {
            if (!hasRun(counts, idx, len, 3)) continue;
            out.add(runOption(CardType.AIRPLANE, idx, len, 3, Combo.WINGS_NONE));
            int[] rest = counts.clone();
            subtractRun(rest, idx, len, 3);
            int[] wingsS = smallestSingles(rest, len);
            if (wingsS.length == len) {
            out.add(wingsOption(idx, len, Combo.WINGS_SINGLES, rest, wingsS));
                }
            int[] wingsP = smallestPairs(rest, len);
            if (wingsP.length == len) {
                out.add(wingsOption(idx, len, Combo.WINGS_PAIRS, rest, wingsP));
            }
        }
        // 三张 / 三带一 / 三带二
        if (c >= 3) {
            int[] rest = counts.clone();
            rest[idx] = c - 3;
            int kickS = smallestSingleIdx(rest, idx);
            if (kickS >= 0) {
                int[] delta = new int[COUNT_SIZE];
                delta[idx] = 3;
                delta[kickS] = 1;
                out.add(new Option(CardType.TRIO_SINGLE, idx, 4, Combo.WINGS_NONE,
                        delta, new int[]{kickS}));
            }
            int kickP = smallestPairIdx(rest, idx);
            if (kickP >= 0) {
                int[] delta = new int[COUNT_SIZE];
                delta[idx] = 3;
                delta[kickP] = 2;
                out.add(new Option(CardType.TRIO_PAIR, idx, 5, Combo.WINGS_NONE,
                        delta, new int[]{kickP}));
            }
            int[] deltaTrio = new int[COUNT_SIZE];
            deltaTrio[idx] = 3;
            out.add(new Option(CardType.TRIO, idx, 3, Combo.WINGS_NONE, deltaTrio, new int[0]));
        }
        // 炸弹 / 四带两单 / 四带两对
        if (c == 4) {
            int[] rest = counts.clone();
            rest[idx] = 0;
            int[] singles2 = smallestSingles(rest, 2);
            if (singles2.length == 2) {
                int[] delta = new int[COUNT_SIZE];
                delta[idx] = 4;
                for (int wi : singles2) delta[wi] += 1;
                out.add(new Option(CardType.QUAD_SINGLE, idx, 6, Combo.WINGS_NONE,
                        delta, singles2));
            }
            int[] pairs2 = smallestPairs(rest, 2);
            if (pairs2.length == 2) {
                int[] delta = new int[COUNT_SIZE];
                delta[idx] = 4;
                for (int wi : pairs2) delta[wi] += 2;
                out.add(new Option(CardType.QUAD_PAIR, idx, 8, Combo.WINGS_NONE,
                        delta, pairs2));
            }
            int[] deltaBomb = new int[COUNT_SIZE];
            deltaBomb[idx] = 4;
            out.add(new Option(CardType.BOMB, idx, 4, Combo.WINGS_NONE, deltaBomb, new int[0]));
        }
        // 对子 / 单张
        if (c >= 2) {
            int[] delta = new int[COUNT_SIZE];
            delta[idx] = 2;
            out.add(new Option(CardType.PAIR, idx, 2, Combo.WINGS_NONE, delta, new int[0]));
        }
        int[] deltaSingle = new int[COUNT_SIZE];
        deltaSingle[idx] = 1;
        out.add(new Option(CardType.SINGLE, idx, 1, Combo.WINGS_NONE, deltaSingle, new int[0]));
        // 王炸
        if (idx == IDX_SMALL_JOKER && counts[IDX_BIG_JOKER] >= 1) {
            int[] delta = new int[COUNT_SIZE];
            delta[IDX_SMALL_JOKER] = 1;
            delta[IDX_BIG_JOKER] = 1;
            out.add(new Option(CardType.JOKER_BOMB, IDX_SMALL_JOKER, 2, Combo.WINGS_NONE,
                    delta, new int[]{IDX_BIG_JOKER}));
        }
        return out;
    }

    private static Option runOption(CardType type, int idx, int len, int perRank, int wingsKind) {
        int[] delta = new int[COUNT_SIZE];
        for (int i = idx; i < idx + len; i++) {
            delta[i] = perRank;
        }
        return new Option(type, idx, len, wingsKind, delta, new int[0]);
    }

    /** 飞机带翅膀：delta = run×3 + 翅膀（rest 为已扣 run 的分布）。 */
    private static Option wingsOption(int idx, int len, int wingsKind, int[] rest, int[] wingIdx) {
        int[] delta = new int[COUNT_SIZE];
        for (int i = idx; i < idx + len; i++) {
            delta[i] = 3;
        }
        for (int wi : wingIdx) {
            delta[wi] += (wingsKind == Combo.WINGS_SINGLES) ? 1 : 2;
        }
        // mainIdx 记录三张段起点，materialize 依此重建 run
        return new Option(CardType.AIRPLANE_WITH_WINGS, idx, len, wingsKind, delta, wingIdx);
    }

    // ============ 翅膀选择（从剩余分布取最小的单张/对子） ============

    /** 取 k 张最小单张的权重索引（可重复取同一权重的多张）。 */
    private static int[] smallestSingles(int[] counts, int k) {
        int[] out = new int[k];
        int[] rest = counts.clone();
        for (int n = 0; n < k; n++) {
            int pick = -1;
            for (int i = 0; i < COUNT_SIZE; i++) {
                if (rest[i] > 0) {
                    pick = i;
                    break;
                }
            }
            if (pick < 0) return new int[0];
            out[n] = pick;
            rest[pick]--;
        }
        return out;
    }

    /** 取 k 个最小对子的权重索引。 */
    private static int[] smallestPairs(int[] counts, int k) {
        int[] out = new int[k];
        int[] rest = counts.clone();
        for (int n = 0; n < k; n++) {
            int pick = -1;
            for (int i = 0; i < COUNT_SIZE; i++) {
                if (rest[i] >= 2) {
                    pick = i;
                    break;
                }
            }
            if (pick < 0) return new int[0];
            out[n] = pick;
            rest[pick] -= 2;
        }
        return out;
    }

    /** 最小的异权重单张索引（排除 exclude）。 */
    private static int smallestSingleIdx(int[] counts, int exclude) {
        for (int i = 0; i < COUNT_SIZE; i++) {
            if (i != exclude && counts[i] > 0) return i;
        }
        return -1;
    }

    /** 最小的异权重对子索引（排除 exclude）。 */
    private static int smallestPairIdx(int[] counts, int exclude) {
        for (int i = 0; i < COUNT_SIZE; i++) {
            if (i != exclude && counts[i] >= 2) return i;
        }
        return -1;
    }

    // ============ 分布工具 ============

    /** 由手牌统计 counts 分布。 */
    public static int[] countsOf(List<Card> hand) {
        int[] counts = new int[COUNT_SIZE];
        for (Card card : hand) {
            int idx = card.getWeight() - 3;
            if (idx >= 0 && idx < COUNT_SIZE) {
                counts[idx]++;
            }
        }
        return counts;
    }

    private static int total(int[] counts) {
        int sum = 0;
        for (int c : counts) sum += c;
        return sum;
    }

    private static int smallestIdx(int[] counts) {
        for (int i = 0; i < COUNT_SIZE; i++) {
            if (counts[i] > 0) return i;
        }
        return -1;
    }

    private static boolean hasRun(int[] counts, int idx, int len, int perRank) {
        for (int i = idx; i < idx + len; i++) {
            if (counts[i] < perRank) return false;
        }
        return true;
    }

    private static void subtractRun(int[] counts, int idx, int len, int perRank) {
        for (int i = idx; i < idx + len; i++) {
            counts[i] -= perRank;
        }
    }

    private static void apply(int[] counts, Option option) {
        for (int i = 0; i < COUNT_SIZE; i++) {
            counts[i] -= option.delta[i];
        }
    }

    private static void undo(int[] counts, Option option) {
        for (int i = 0; i < COUNT_SIZE; i++) {
            counts[i] += option.delta[i];
        }
    }

    /** 记忆化 key：每档 3 bit（counts ≤ 4，王 ≤ 1），15 档共 45 bit。 */
    private static long encode(int[] counts) {
        long key = 0;
        for (int i = 0; i < COUNT_SIZE; i++) {
            key |= ((long) counts[i]) << (i * 3);
        }
        return key;
    }

    // ============ 选项 → 真实 Combo ============

    /** 按选项从牌池取真实卡牌并经 {@link Combo#of} 校验；理论上恒非 null（防御）。 */
    private static Combo materialize(Option option, Map<Integer, List<Card>> pool) {
        List<Card> cards = new ArrayList<>();
        switch (option.type) {
            case STRAIGHT:
            case STRAIGHT_PAIRS:
            case AIRPLANE: {
                int perRank = option.type == CardType.STRAIGHT ? 1
                        : option.type == CardType.STRAIGHT_PAIRS ? 2 : 3;
                for (int i = option.mainIdx; i < option.mainIdx + option.length; i++) {
                    take(pool, cards, i, perRank);
                }
                break;
            }
            case AIRPLANE_WITH_WINGS: {
                for (int i = option.mainIdx; i < option.mainIdx + option.length; i++) {
                    take(pool, cards, i, 3);
                }
                for (int wi : option.wingIdx) {
                    take(pool, cards, wi, option.wingsKind == Combo.WINGS_SINGLES ? 1 : 2);
                }
                break;
            }
            case TRIO_SINGLE:
            case TRIO_PAIR:
                take(pool, cards, option.mainIdx, 3);
                take(pool, cards, option.wingIdx[0],
                        option.type == CardType.TRIO_PAIR ? 2 : 1);
                break;
            case QUAD_SINGLE:
            case QUAD_PAIR:
                take(pool, cards, option.mainIdx, 4);
                for (int wi : option.wingIdx) {
                    take(pool, cards, wi, option.type == CardType.QUAD_PAIR ? 2 : 1);
                }
                break;
            case BOMB:
                take(pool, cards, option.mainIdx, 4);
                break;
            case TRIO:
                take(pool, cards, option.mainIdx, 3);
                break;
            case PAIR:
                take(pool, cards, option.mainIdx, 2);
                break;
            case SINGLE:
                take(pool, cards, option.mainIdx, 1);
                break;
            case JOKER_BOMB:
                take(pool, cards, IDX_SMALL_JOKER, 1);
                take(pool, cards, IDX_BIG_JOKER, 1);
                break;
            default:
                return null;
        }
        return Combo.of(cards);
    }

    private static void take(Map<Integer, List<Card>> pool, List<Card> out, int weight, int n) {
        List<Card> list = pool.get(weight + 3);
        if (list == null || list.isEmpty()) return;
        for (int i = 0; i < n && !list.isEmpty(); i++) {
            out.add(list.remove(0));
        }
    }
}
