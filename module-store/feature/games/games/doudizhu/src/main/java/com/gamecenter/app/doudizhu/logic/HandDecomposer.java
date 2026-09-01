package com.gamecenter.app.doudizhu.logic;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.model.Rank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手牌最少手数分解器（P2 AI 内核）。
 *
 * <p>回答一个问题："这手牌最少几次出完？"并给出一条达到该手数的分解路线。
 * 采用 DFS + 记忆化：每次固定从"剩余牌中最小权重"处展开（保证同一 counts 状态
 * 只被求解一次），候选组合覆盖全部 13 种牌型（单/对/三/三带一/三带二/炸弹/王炸/
 * 顺子/连对/飞机/飞机带翅膀/四带二单/四带二对），并额外枚举"主体起点高于最小牌、
 * 最小牌充当翅膀/挂件"的高起点组合（如 444555+3+6），保证最小牌无法成主体时
 * 仍能找到一手带走的路径。</p>
 *
 * <p><b>翅膀多代表元策略（P2 第二轮复查修正）</b>：翅膀在牌型合法性上可互换，
 * 但在残局手数上不可互换——把能组成顺子/连对的小牌当翅膀消耗、把 2/王等死牌
 * 留在残局会多花手数。故每个带翅膀/挂件组合不再只取"剩余最小"一组翅膀，而是
 * 枚举三组代表元（剩余最小 / 死牌优先：王、2、孤立牌先上 / 剩余最大）全部交给
 * DFS 取 minHands 最小。记忆化按 counts 状态键控，加候选只增加边数不增加状态数，
 * minHands 相对旧枚举单调不增。<b>诚实边界</b>：该策略集不构成全局最优的正确性
 * 证明，仅承诺"在回归测试语料上与精确枚举器对拍一致"——见
 * {@code HandDecomposerExactnessTest}（≤12 张穷举全部翅膀子集的精确解对拍，
 * 含四个最小手数反例与随机手牌）。</p>
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
    /** 权重 2（15）对应的索引：三张/炸弹主体可达的最高档 */
    private static final int IDX_TWO = Rank.TWO.getWeight() - 3;
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
     * 枚举"覆盖 idx 处最小牌"的全部出完方式。
     * 顺序：顺子 → 连对 → 飞机 → 飞机带翅膀 → 高起点带翅膀组合 → 三带 → 炸弹/四带 → 对 → 单 → 王炸。
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
            appendAirplaneWingOptions(out, idx, len, rest);
        }
        // 高起点带翅膀组合（主体起点 j > idx，最小牌充当翅膀/挂件）
        appendHighStartWingedOptions(counts, idx, out);
        // 三张 / 三带一 / 三带二（挂件按多代表元策略取）
        if (c >= 3) {
            int[] rest = counts.clone();
            rest[idx] = c - 3;
            for (int[] kicker : wingCandidates(rest, 1, false)) {
                int[] delta = new int[COUNT_SIZE];
                delta[idx] = 3;
                delta[kicker[0]] += 1;
                out.add(new Option(CardType.TRIO_SINGLE, idx, 4, Combo.WINGS_NONE,
                        delta, kicker));
            }
            for (int[] kicker : wingCandidates(rest, 1, true)) {
                int[] delta = new int[COUNT_SIZE];
                delta[idx] = 3;
                delta[kicker[0]] += 2;
                out.add(new Option(CardType.TRIO_PAIR, idx, 5, Combo.WINGS_NONE,
                        delta, kicker));
            }
            int[] deltaTrio = new int[COUNT_SIZE];
            deltaTrio[idx] = 3;
            out.add(new Option(CardType.TRIO, idx, 3, Combo.WINGS_NONE, deltaTrio, new int[0]));
        }
        // 炸弹 / 四带两单 / 四带两对（翅膀按多代表元策略取）
        if (c == 4) {
            int[] rest = counts.clone();
            rest[idx] = 0;
            for (int[] wings : wingCandidates(rest, 2, false)) {
                int[] delta = new int[COUNT_SIZE];
                delta[idx] = 4;
                for (int wi : wings) delta[wi] += 1;
                out.add(new Option(CardType.QUAD_SINGLE, idx, 6, Combo.WINGS_NONE,
                        delta, wings));
            }
            for (int[] wings : wingCandidates(rest, 2, true)) {
                int[] delta = new int[COUNT_SIZE];
                delta[idx] = 4;
                for (int wi : wings) delta[wi] += 2;
                out.add(new Option(CardType.QUAD_PAIR, idx, 8, Combo.WINGS_NONE,
                        delta, wings));
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

    /**
     * 高起点带翅膀组合：主体（三张/炸弹/连续三张段）起点 j &gt; idx，idx 处的最小牌
     * 充当翅膀/挂件。补全"最小牌数量不足以当主体、却能并入高起点组合的翅膀"的路径，
     * 如 {3,444,555,6} 的 444555+3+6、{3,4,5555} 的 5555+3+4。
     *
     * <p>主体完备性：任一分解中覆盖最小牌 idx 的那一手只有两种形态——主体包含 idx
     * （顺/连对/飞机起点必为 idx，主体三张/炸弹/炸弹起点必为 idx，均由原有枚举覆盖），
     * 或主体起点 j &gt; idx 而 idx 处的牌沦为翅膀/挂件（本方法枚举，"剩余最小"代表元的
     * 翅膀必然先取到 idx 处的牌）。</p>
     *
     * <p>翅膀多代表元（P2 第二轮复查修正）：e1dae47 时期"固定主体后各翅膀可互换、
     * 取剩余最小即可代表"的论证只对"翅膀恰为 idx 处最小牌"的第一张成立；k ≥ 2 时
     * 第二张翅膀（对挂件/炸弹翅膀/飞机翅膀）是自由选择，把能组成顺子/连对的小牌当
     * 翅膀消耗、把 2/王等死牌留在残局会多花手数。故翅膀改按 {@link #wingCandidates}
     * 的三代表元（剩余最小/死牌优先/剩余最大）枚举，交 DFS 择优——记忆化按 counts
     * 状态键控，只增边数不增状态数，minHands 相对旧枚举单调不增（只会更优）。该策略
     * 集不构成全局最优的正确性证明，仅承诺回归语料上与精确枚举器对拍一致（见
     * HandDecomposerExactnessTest）。</p>
     */
    private static void appendHighStartWingedOptions(int[] counts, int idx, List<Option> out) {
        // 三带一 / 三带二：主体起点 j > idx（"剩余最小"挂件必然取到 idx 处最小牌）
        for (int j = idx + 1; j <= IDX_TWO; j++) {
            if (counts[j] < 3) continue;
            int[] rest = counts.clone();
            rest[j] -= 3;
            for (int[] kicker : wingCandidates(rest, 1, false)) {
                int[] delta = new int[COUNT_SIZE];
                delta[j] = 3;
                delta[kicker[0]] += 1;
                out.add(new Option(CardType.TRIO_SINGLE, j, 4, Combo.WINGS_NONE,
                        delta, kicker));
            }
            for (int[] kicker : wingCandidates(rest, 1, true)) {
                int[] delta = new int[COUNT_SIZE];
                delta[j] = 3;
                delta[kicker[0]] += 2;
                out.add(new Option(CardType.TRIO_PAIR, j, 5, Combo.WINGS_NONE,
                        delta, kicker));
            }
        }
        // 四带两单 / 四带两对：主体起点 j > idx（"剩余最小"翅膀先取 idx 处最小牌）
        for (int j = idx + 1; j <= IDX_TWO; j++) {
            if (counts[j] != 4) continue;
            int[] rest = counts.clone();
            rest[j] = 0;
            for (int[] wings : wingCandidates(rest, 2, false)) {
                int[] delta = new int[COUNT_SIZE];
                delta[j] = 4;
                for (int wi : wings) delta[wi] += 1;
                out.add(new Option(CardType.QUAD_SINGLE, j, 6, Combo.WINGS_NONE,
                        delta, wings));
            }
            for (int[] wings : wingCandidates(rest, 2, true)) {
                int[] delta = new int[COUNT_SIZE];
                delta[j] = 4;
                for (int wi : wings) delta[wi] += 2;
                out.add(new Option(CardType.QUAD_PAIR, j, 8, Combo.WINGS_NONE,
                        delta, wings));
            }
        }
        // 飞机带翅膀：三张段起点 j > idx（"剩余最小"翅膀先取 idx 处最小牌）
        for (int len = 2; len <= 6; len++) {
            for (int j = idx + 1; j + len - 1 <= MAX_SEQ_IDX; j++) {
                if (!hasRun(counts, j, len, 3)) continue;
                int[] rest = counts.clone();
                subtractRun(rest, j, len, 3);
                appendAirplaneWingOptions(out, j, len, rest);
            }
        }
    }

    /** 飞机带翅膀：对三张段扣除后的 rest 分布，按多代表元策略生成单/对翅膀选项。 */
    private static void appendAirplaneWingOptions(List<Option> out, int startIdx, int len,
                                                  int[] rest) {
        for (int[] wings : wingCandidates(rest, len, false)) {
            out.add(wingsOption(startIdx, len, Combo.WINGS_SINGLES, rest, wings));
        }
        for (int[] wings : wingCandidates(rest, len, true)) {
            out.add(wingsOption(startIdx, len, Combo.WINGS_PAIRS, rest, wings));
        }
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

    // ============ 翅膀代表元选择（多策略，性能有界） ============

    /**
     * 多代表元翅膀候选：从剩余分布取 k 份单张（pairs=false）或 k 份对子
     * （pairs=true），至多返回 3 组互不相同的代表元翅膀（每组升序规范化）：
     * <ol>
     *   <li>剩余最小——旧策略，优先随主体消化零散小牌；</li>
     *   <li>死牌优先——王/2 → 孤立单档 → 冗余档（取走后该档仍有剩余，不破坏
     *       连型潜力）→ 降序兜底；</li>
     *   <li>剩余最大——对子挂件（如 777+AA）常在此命中。</li>
     * </ol>
     * 翅膀在牌型合法性上可互换，但在残局手数上不可互换：把能组成顺子/连对的
     * 小牌当翅膀消耗、把 2/王等死牌留在残局会多花手数。多代表元全部交给 DFS
     * 择优；记忆化按 counts 状态键控，只增边数不增状态数，minHands 相对单一
     * "剩余最小"策略单调不增。每个带翅膀组合至多 3 组候选，性能有界。
     */
    private static int[][] wingCandidates(int[] counts, int k, boolean pairs) {
        List<int[]> out = new ArrayList<>(3);
        appendWingCandidate(out, extremeWings(counts, k, pairs, true), k);
        appendWingCandidate(out, deadWingsFirst(counts, k, pairs), k);
        appendWingCandidate(out, extremeWings(counts, k, pairs, false), k);
        return out.toArray(new int[0][]);
    }

    /** 去重追加一组翅膀候选（长度不足 k 或与已有候选重复时忽略）。 */
    private static void appendWingCandidate(List<int[]> out, int[] wings, int k) {
        if (wings.length != k) return;
        for (int[] existing : out) {
            if (Arrays.equals(existing, wings)) return;
        }
        out.add(wings);
    }

    /** 单一代表元取 k 份翅膀：逐份取剩余最小（ascending）或剩余最大，失败返回空。 */
    private static int[] extremeWings(int[] counts, int k, boolean pairs, boolean ascending) {
        int need = pairs ? 2 : 1;
        int[] out = new int[k];
        int[] rest = counts.clone();
        int n = 0;
        int i = ascending ? 0 : COUNT_SIZE - 1;
        int end = ascending ? COUNT_SIZE : -1;
        while (n < k && i != end) {
            if (rest[i] >= need) {
                out[n++] = i;
                rest[i] -= need;
            } else {
                i += ascending ? 1 : -1;
            }
        }
        if (n != k) return new int[0];
        Arrays.sort(out);
        return out;
    }

    /**
     * 死牌优先代表元：按"王/2 → 孤立单档 → 冗余档 → 降序兜底"的优先序逐份取
     * 翅膀（取走即扣减，孤立/冗余判定随剩余分布动态更新），凑不满 k 返回空。
     */
    private static int[] deadWingsFirst(int[] counts, int k, boolean pairs) {
        int need = pairs ? 2 : 1;
        int[] out = new int[k];
        int[] rest = counts.clone();
        int n = 0;
        // 王 / 2：无法入任何顺子/连对，最先消耗（权重降序）
        for (int i = IDX_BIG_JOKER; i >= IDX_TWO && n < k; i--) {
            while (rest[i] >= need && n < k) {
                out[n++] = i;
                rest[i] -= need;
            }
        }
        // 孤立单档：左右相邻档均无牌，并不进任何连型（降序）
        for (int i = MAX_SEQ_IDX; i >= 0 && n < k; i--) {
            if (rest[i] < need || !isIsolatedRank(rest, i)) continue;
            while (rest[i] >= need && n < k) {
                out[n++] = i;
                rest[i] -= need;
            }
        }
        // 冗余档：取走一份后该档仍有剩余，不破坏既有连型潜力（降序）
        for (int i = MAX_SEQ_IDX; i >= 0 && n < k; i--) {
            while (rest[i] >= need + 1 && n < k) {
                out[n++] = i;
                rest[i] -= need;
            }
        }
        // 兜底：其余任意（降序），保证凑满 k
        for (int i = MAX_SEQ_IDX; i >= 0 && n < k; i--) {
            while (rest[i] >= need && n < k) {
                out[n++] = i;
                rest[i] -= need;
            }
        }
        if (n != k) return new int[0];
        Arrays.sort(out);
        return out;
    }

    /** 档位 i 是否孤立：左右相邻档均无牌（2/王不参与连型，另由前置分支处理）。 */
    private static boolean isIsolatedRank(int[] counts, int i) {
        boolean leftDead = i == 0 || counts[i - 1] == 0;
        boolean rightDead = i >= MAX_SEQ_IDX || counts[i + 1] == 0;
        return leftDead && rightDead;
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
