package com.gamecenter.app.doudizhu.ai;

import com.gamecenter.app.doudizhu.logic.Combo;
import com.gamecenter.app.doudizhu.logic.HandDecomposer;
import com.gamecenter.app.doudizhu.logic.HintFinder;
import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.model.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 斗地主 AI 大脑（P2 重写，替代旧 {@code AIBot}）。
 *
 * <p>基于 P1 规则内核（{@link Combo} / {@link HintFinder}）的正式打法：
 * 首发与接牌均以"手牌最少手数分解"（{@link HandDecomposer}）为核心代价函数，
 * 吸收旧 AIBot 的角色战术（顶牌防守、放水、紧急炸弹），并新增记牌器数据
 * （外部剩余牌分布）参与的困难档决策。</p>
 *
 * <p>难度三档（对应宿主 GameLauncherHelper 0/1/2 档）真实差异：
 * <ul>
 *   <li><b>简单</b>：无手数规划的启发式——首发永远先出最小单张、对子（旧 AIBot 式
 *       "累赘牌优先"）；接牌只出"最小能压的非炸弹"，15% 概率放弃明显可压的最优解，
 *       绝不主动用炸弹；叫分保守（需强牌才叫）。</li>
 *   <li><b>普通</b>：首发按最少手数分解路线出主权重最小的非炸手；接牌出最小能压的
 *       非炸弹；地主只剩 1 张时顶最大单牌（单牌报警，队友也剩 1 张且队友在下家时
 *       改送小牌让队友先走，下家是地主则维持顶大）；地主剩 1-2 张时紧急炸弹；
 *       农民对队友一律放行。</li>
 *   <li><b>困难</b>：在普通档之上叠加记牌器——剩两手时优先出"外部压不住"的稳收手；
 *       接牌逐一评估"打出后剩余手数"选拆解代价最小者；敌方剩 1-2 张时顶最大同型牌
 *       压死；农民对队友从"永远放行"升级为"队友小牌且我有余力时接管"。</li>
 * </ul></p>
 *
 * <p>纯 JVM 实现：不 import 任何 Android 类；随机性通过注入 {@link Random}
 * （可固定种子）保证可测。所有产出在出口处经 {@link Combo#of} + {@link Combo#beats}
 * 自校验，非法着法一律降级为 pass（契约兜底，控制器另有二次复核）。</p>
 */
public final class AiBrain {

    // ============ 难度档位（对应宿主 game_difficulty_index 的 0/1/2） ============

    public static final int DIFFICULTY_EASY = 0;
    public static final int DIFFICULTY_NORMAL = 1;
    public static final int DIFFICULTY_HARD = 2;

    /** 简单档接牌放弃最优解的概率（固定注入 Random，可测） */
    static final double EASY_GIVE_UP_RATE = 0.15;

    // ============ AI 思考延迟差异化（P5） ============

    /** 各档思考延迟区间下界（毫秒，闭）：三档区间严格不重叠（简单更快、困难略慢） */
    public static final long THINK_DELAY_EASY_MIN_MS = 400L;
    public static final long THINK_DELAY_NORMAL_MIN_MS = 900L;
    public static final long THINK_DELAY_HARD_MIN_MS = 1400L;

    /** 各档思考延迟区间上界（毫秒，开）：区间宽 500ms，保留随机拟人感 */
    public static final long THINK_DELAY_EASY_MAX_MS = 900L;
    public static final long THINK_DELAY_NORMAL_MAX_MS = 1400L;
    public static final long THINK_DELAY_HARD_MAX_MS = 1900L;

    /**
     * 按难度档位生成一次随机 AI 思考延迟（P5：简单档更快、困难档略慢）。
     *
     * <p>收敛旧 {@code AIBot.getRandomThinkingDelay} 的"区间随机"思路，但把延迟
     * 差异与难度档绑定：简单 [400,900)、普通 [900,1400)、困难 [1400,1900) 毫秒，
     * 三档区间严格不重叠。随机性经注入 {@link Random} 保证可测（固定种子输出确定）。</p>
     *
     * @param difficulty 难度档位（非法值按普通档处理）
     * @param random     随机源；null 时退化为区间中位值（确定性，便于调度方在无
     *                   随机源场景下仍可用）
     * @return 思考延迟毫秒数，恒在对应档位区间内
     */
    public static long thinkingDelayMs(int difficulty, Random random) {
        long min;
        long max;
        switch (difficulty) {
            case DIFFICULTY_EASY:
                min = THINK_DELAY_EASY_MIN_MS;
                max = THINK_DELAY_EASY_MAX_MS;
                break;
            case DIFFICULTY_HARD:
                min = THINK_DELAY_HARD_MIN_MS;
                max = THINK_DELAY_HARD_MAX_MS;
                break;
            default:
                min = THINK_DELAY_NORMAL_MIN_MS;
                max = THINK_DELAY_NORMAL_MAX_MS;
                break;
        }
        if (random == null) return min + (max - min) / 2;
        return min + (long) (random.nextDouble() * (max - min));
    }

    // ============ 角色 ============

    public static final int ROLE_FARMER = 0;
    public static final int ROLE_LANDLORD = 1;

    private AiBrain() {}

    // ============ 游戏上下文 ============

    /**
     * 战术决策上下文（由调度方构建）。
     */
    public static final class GameContext {
        public final int myRole;
        public final int mySeatIndex;
        public final int[] seatRoles;
        public final int landlordSeat;
        public final int landlordRemainCards;
        public final int lastPlayerSeat;
        public final int teammateSeat;
        public final int teammateRemainCards;
        public final int nextSeatRemainCards;
        /** 与我敌对一方中最少剩牌数（农民视角=地主剩牌；地主视角=两农民较少者） */
        public final int enemyRemainMin;

        public GameContext(int myRole, int mySeatIndex, int[] seatRoles, int landlordSeat,
                           int landlordRemainCards, int lastPlayerSeat, int teammateSeat,
                           int teammateRemainCards, int nextSeatRemainCards, int enemyRemainMin) {
            this.myRole = myRole;
            this.mySeatIndex = mySeatIndex;
            this.seatRoles = seatRoles;
            this.landlordSeat = landlordSeat;
            this.landlordRemainCards = landlordRemainCards;
            this.lastPlayerSeat = lastPlayerSeat;
            this.teammateSeat = teammateSeat;
            this.teammateRemainCards = teammateRemainCards;
            this.nextSeatRemainCards = nextSeatRemainCards;
            this.enemyRemainMin = enemyRemainMin;
        }

        static GameContext defaultContext() {
            int[] roles = new int[]{ROLE_FARMER, ROLE_FARMER, ROLE_FARMER};
            return new GameContext(ROLE_FARMER, 0, roles, -1, 17, -1, -1, -1, -1, 17);
        }
    }

    // ============ 对外决策入口 ============

    /**
     * 出牌决策总入口。
     *
     * @param hand           我的手牌
     * @param previousCards  上家出的牌；null/空表示自由出牌
     * @param ctx            游戏上下文（null 时用默认农民上下文）
     * @param difficulty     {@link #DIFFICULTY_EASY} / NORMAL / HARD
     * @param random         随机源（简单档使用；null 表示确定性决策）
     * @param playedHistory  本局全部已出的牌（记牌器数据，可为空）
     * @return 出的牌；接不住或选择不出返回 null
     */
    public static List<Card> decidePlay(List<Card> hand, List<Card> previousCards,
                                        GameContext ctx, int difficulty, Random random,
                                        List<Card> playedHistory) {
        if (hand == null || hand.isEmpty()) return null;
        if (ctx == null) ctx = GameContext.defaultContext();

        Combo previous = null;
        if (previousCards != null && !previousCards.isEmpty()) {
            previous = Combo.of(previousCards);
            if (previous == null) {
                // 上家着法非法（正常流程不会发生）：防御性视为自由出牌前的空桌
                return null;
            }
        }

        List<Card> move = previous == null
                ? decideLead(hand, ctx, difficulty, playedHistory)
                : decideFollow(hand, previous, ctx, difficulty, random, playedHistory);

        // 自由出牌回合兜底：任何情况下都必须出牌（pass 会导致对局卡死）
        if (move == null && previous == null) {
            move = smallestSingle(hand);
        }

        // 契约自校验：非法着法降级为 pass（绝不产出 AI 违约）
        if (move != null) {
            Combo mine = Combo.of(move);
            if (mine == null || !mine.beats(previous)) {
                return previous == null ? smallestSingle(hand) : null;
            }
        }
        return move;
    }

    /**
     * 手牌评估 → 叫分 0-3（P3 叫分制的评分基础；P2 中叫/不叫按档位阈值映射）。
     *
     * <p>构成：大牌计数（大王 3 / 小王 2 / 王炸 +3 / 每个 2 +2 / 每个 A +1）
     * + 炸弹每个 +3 + 手数奖励（≤5 手 +6，≤7 手 +3，≤9 手 +1）。
     * 总分 ≥15 → 3 分，≥11 → 2 分，≥7 → 1 分，否则 0 分（阈值以代码为准，
     * 已由单测锁定）。</p>
     */
    public static int evaluateBidScore(List<Card> hand) {
        if (hand == null || hand.isEmpty()) return 0;
        int[] counts = HandDecomposer.countsOf(hand);
        int score = 0;
        if (counts[idx(Rank.BIG_JOKER)] > 0) score += 3;
        if (counts[idx(Rank.SMALL_JOKER)] > 0) score += 2;
        if (counts[idx(Rank.SMALL_JOKER)] > 0 && counts[idx(Rank.BIG_JOKER)] > 0) score += 3;
        score += counts[idx(Rank.TWO)] * 2;
        score += counts[idx(Rank.ACE)] * 1;
        for (int i = 0; i <= idx(Rank.ACE); i++) {
            if (counts[i] == 4) score += 3;
        }
        int hands = HandDecomposer.minHands(counts);
        if (hands <= 5) {
            score += 6;
        } else if (hands <= 7) {
            score += 3;
        } else if (hands <= 9) {
            score += 1;
        }
        return score >= 15 ? 3 : score >= 11 ? 2 : score >= 7 ? 1 : 0;
    }

    /**
     * 叫地主决策（P2 对局流程仍为叫/不叫；简单档阈值调高=保守叫分）。
     */
    public static boolean decideBid(List<Card> hand, int difficulty) {
        int score = evaluateBidScore(hand);
        return difficulty == DIFFICULTY_EASY ? score >= 2 : score >= 1;
    }

    // ============ 首发决策 ============

    private static List<Card> decideLead(List<Card> hand, GameContext ctx, int difficulty,
                                         List<Card> playedHistory) {
        // 1. 一手出完直接赢（三档通用）
        List<Combo> freeCandidates = HintFinder.findPlayable(hand, null);
        for (Combo c : freeCandidates) {
            if (c.size() == hand.size()) return c.getCards();
        }

        // 2. 单牌报警防守：地主只剩 1 张，农民顶最大单牌（普通/困难）。
        //    队友也只剩 1 张、且队友在下家时，改为送最小单牌让队友先走
        //    （快赢 > 防守）；若下家不是队友（即下家是地主），送小牌等于直接
        //    喂地主获胜（地主任意更大的单牌压过即赢），必须维持顶最大单牌。
        if (difficulty != DIFFICULTY_EASY && ctx.myRole == ROLE_FARMER
                && ctx.landlordRemainCards == 1) {
            if (ctx.teammateRemainCards == 1 && isNextSeatTeammate(ctx)) {
                List<Card> small = smallestSingle(hand);
                if (small != null) return small;
            }
            List<Card> top = biggestSingle(hand);
            if (top != null) return top;
        }

        // 3. 放水：下家是队友且快赢，送最小单牌（普通/困难）
        if (difficulty != DIFFICULTY_EASY && ctx.myRole == ROLE_FARMER
                && isNextSeatTeammate(ctx) && ctx.nextSeatRemainCards > 0
                && ctx.nextSeatRemainCards <= 2) {
            List<Card> small = smallestSingle(hand);
            if (small != null) return small;
        }

        // 4. 简单档：旧式启发式（最小单张 → 最小对 → 最小三张 → 炸弹 → 王炸）
        if (difficulty == DIFFICULTY_EASY) {
            return easyLead(hand);
        }

        // 5. 普通/困难：按最少手数分解路线首发
        List<Combo> path = HandDecomposer.bestDecomposition(hand);
        List<Combo> candidates = nonBombHands(path);
        if (candidates.isEmpty()) candidates = path;
        if (candidates.isEmpty()) return null;

        // 6. 困难档记牌收尾：只剩两手时，先出"外部压不住"的稳收手，下轮自由出牌收官
        if (difficulty == DIFFICULTY_HARD && path.size() == 2) {
            int[] outside = outsideCounts(hand, playedHistory);
            for (Combo c : candidates) {
                if (isUnbeatableOutside(c, outside)) {
                    return c.getCards();
                }
            }
        }

        // 7. 出路线中主权重最小的非炸手（消耗小牌，保结构）
        Combo best = candidates.get(0);
        for (Combo c : candidates) {
            if (c.getMainWeight() < best.getMainWeight()) {
                best = c;
            }
        }
        return best.getCards();
    }

    /** 简单档首发：模拟旧 AIBot 的"累赘牌优先"，无手数规划、不保护结构。 */
    private static List<Card> easyLead(List<Card> hand) {
        int[] counts = HandDecomposer.countsOf(hand);
        // 最小单张
        for (int i = 0; i < HandDecomposer.COUNT_SIZE; i++) {
            if (counts[i] == 1) return singleton(hand, i, 1);
        }
        // 最小对子
        for (int i = 0; i < HandDecomposer.COUNT_SIZE; i++) {
            if (counts[i] == 2) return singleton(hand, i, 2);
        }
        // 最小三张
        for (int i = 0; i < HandDecomposer.COUNT_SIZE; i++) {
            if (counts[i] == 3) return singleton(hand, i, 3);
        }
        // 炸弹、王炸（只剩这些才动）
        for (int i = 0; i < HandDecomposer.COUNT_SIZE; i++) {
            if (counts[i] == 4) return singleton(hand, i, 4);
        }
        if (counts[idx(Rank.SMALL_JOKER)] > 0 && counts[idx(Rank.BIG_JOKER)] > 0) {
            List<Card> cards = new ArrayList<>();
            cards.add(pickCard(hand, Rank.SMALL_JOKER));
            cards.add(pickCard(hand, Rank.BIG_JOKER));
            return cards;
        }
        // 兜底：手牌最小一张
        Card min = hand.get(0);
        for (Card c : hand) {
            if (c.getWeight() < min.getWeight()) min = c;
        }
        List<Card> cards = new ArrayList<>();
        cards.add(min);
        return cards;
    }

    // ============ 接牌决策 ============

    private static List<Card> decideFollow(List<Card> hand, Combo previous, GameContext ctx,
                                           int difficulty, Random random,
                                           List<Card> playedHistory) {
        List<Combo> candidates = HintFinder.findPlayable(hand, previous);
        if (candidates.isEmpty()) return null;

        // 一手出完直接赢（三档通用）
        for (Combo c : candidates) {
            if (c.size() == hand.size()) return c.getCards();
        }

        boolean fromTeammate = ctx.myRole == ROLE_FARMER
                && ctx.lastPlayerSeat >= 0 && ctx.lastPlayerSeat != ctx.mySeatIndex
                && roleOf(ctx, ctx.lastPlayerSeat) == ROLE_FARMER;

        // 农民单牌报警：地主剩 1 张且出的是单牌，顶最大单牌（普通/困难）
        if (difficulty != DIFFICULTY_EASY && !fromTeammate && ctx.myRole == ROLE_FARMER
                && ctx.landlordRemainCards == 1
                && previous.getType() == CardType.SINGLE
                && roleOf(ctx, ctx.lastPlayerSeat) == ROLE_LANDLORD) {
            Combo top = biggestOfKind(candidates, CardType.SINGLE);
            if (top != null) return top.getCards();
        }

        if (difficulty == DIFFICULTY_EASY) {
            if (random != null && random.nextDouble() < EASY_GIVE_UP_RATE) {
                return null; // 放弃明显可压的最优解（易失误）
            }
            for (Combo c : candidates) {
                if (!c.isBomb()) return c.getCards(); // 最小能压的非炸弹
            }
            return null; // 简单档不主动用炸弹
        }

        if (fromTeammate) {
            if (difficulty == DIFFICULTY_NORMAL) {
                return null; // 普通档：队友牌一律放行
            }
            return hardFollowTeammate(hand, previous, candidates);
        }

        if (difficulty == DIFFICULTY_NORMAL) {
            for (Combo c : candidates) {
                if (!c.isBomb()) return c.getCards();
            }
            // 紧急炸弹：敌方只剩 1-2 张，用最小炸弹/王炸拦住
            if (ctx.enemyRemainMin > 0 && ctx.enemyRemainMin <= 2) {
                Combo bomb = smallestBomb(candidates);
                return bomb == null ? null : bomb.getCards();
            }
            return null;
        }
        return hardFollowEnemy(hand, previous, candidates, ctx, playedHistory);
    }

    /**
     * 困难档对队友：队友快赢或出大牌时放行；队友出小牌且我能减少手数（或我快赢）
     * 时接管。
     */
    private static List<Card> hardFollowTeammate(List<Card> hand, Combo previous,
                                                 List<Combo> candidates) {
        if (previous.getMainWeight() >= Rank.QUEEN.getWeight()) {
            return null; // 队友大牌，放行
        }
        Combo best = cheapestNonBomb(hand, candidates);
        if (best == null) return null;
        boolean worthTaking = hand.size() <= 4
                || HandDecomposer.minHands(after(hand, best)) < HandDecomposer.minHands(hand);
        return worthTaking ? best.getCards() : null;
    }

    /**
     * 困难档对敌方：逐一评估"打出后剩余最少手数"（拆解代价），
     * 叠加记牌器安全牌加成与炸弹惩罚；敌方剩 1-2 张时顶最大同型牌压死。
     */
    private static List<Card> hardFollowEnemy(List<Card> hand, Combo previous,
                                              List<Combo> candidates, GameContext ctx,
                                              List<Card> playedHistory) {
        boolean enemyUrgent = ctx.enemyRemainMin > 0 && ctx.enemyRemainMin <= 2;

        // 敌方快赢：单/对直接顶最大，拦不住再上炸弹
        if (enemyUrgent && (previous.getType() == CardType.SINGLE
                || previous.getType() == CardType.PAIR)) {
            Combo top = biggestOfKind(candidates, previous.getType());
            if (top != null) return top.getCards();
            Combo bomb = smallestBomb(candidates);
            return bomb == null ? null : bomb.getCards();
        }

        int[] outside = outsideCounts(hand, playedHistory);
        Combo best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Combo c : candidates) {
            int score = HandDecomposer.minHands(after(hand, c)) * 100 + c.getMainWeight();
            if (c.isBomb() && !previous.isBomb()) {
                score += 300; // 主动动炸弹 = 浪费武器，重罚（回应敌方炸弹不罚）
            }
            if (!c.isBomb() && isUnbeatableOutside(c, outside)) {
                score -= 50; // 记牌器：外部压不住的安全牌，优先出
            }
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best == null) return null;
        if (best.isBomb() && !previous.isBomb() && !enemyUrgent) {
            return null; // 非紧急不出炸弹
        }
        return best.getCards();
    }

    // ============ 记牌器 ============

    /**
     * 外部剩余牌分布（对手与底牌中尚未出现的牌）。
     * outside[i] = 基准张数 - 我手牌该权重张数 - 已出牌该权重张数。
     */
    static int[] outsideCounts(List<Card> hand, List<Card> playedHistory) {
        int[] mine = HandDecomposer.countsOf(hand);
        int[] outside = new int[HandDecomposer.COUNT_SIZE];
        for (int i = 0; i <= idx(Rank.ACE); i++) {
            outside[i] = 4 - mine[i];
        }
        outside[idx(Rank.SMALL_JOKER)] = 1 - mine[idx(Rank.SMALL_JOKER)];
        outside[idx(Rank.BIG_JOKER)] = 1 - mine[idx(Rank.BIG_JOKER)];
        if (playedHistory != null) {
            for (Card card : playedHistory) {
                int i = idx(card.getRank());
                if (i >= 0 && i < HandDecomposer.COUNT_SIZE && outside[i] > 0) {
                    outside[i]--;
                }
            }
        }
        return outside;
    }

    /**
     * 判断组合在外部是否压不住（稳收牌）：
     * 外部无炸弹/王炸可能时——单张看外部有无更大单张，对子看有无更大对子，
     * 三张系看有无更大三张，其余长牌型保守视为可能被压。
     */
    static boolean isUnbeatableOutside(Combo combo, int[] outside) {
        // 外部可能存在炸弹或王炸 → 一律可能被压
        for (int i = 0; i <= idx(Rank.ACE); i++) {
            if (outside[i] == 4) return false;
        }
        if (outside[idx(Rank.SMALL_JOKER)] > 0 && outside[idx(Rank.BIG_JOKER)] > 0) {
            return false;
        }
        int main = combo.getMainWeight();
        int mi = main - 3; // 权重转 counts 索引
        CardType type = combo.getType();
        if (type == CardType.SINGLE) {
            for (int i = mi + 1; i < HandDecomposer.COUNT_SIZE; i++) {
                if (outside[i] > 0) return false;
            }
            return true;
        }
        if (type == CardType.PAIR) {
            for (int i = mi + 1; i < HandDecomposer.COUNT_SIZE; i++) {
                if (outside[i] >= 2) return false;
            }
            return true;
        }
        if (type == CardType.TRIO || type == CardType.TRIO_SINGLE
                || type == CardType.TRIO_PAIR) {
            for (int i = mi + 1; i <= idx(Rank.ACE); i++) {
                if (outside[i] >= 3) return false;
            }
            if (outside[idx(Rank.TWO)] >= 3) return false;
            return true;
        }
        if (type == CardType.BOMB) {
            for (int i = mi + 1; i <= idx(Rank.ACE); i++) {
                if (outside[i] == 4) return false;
            }
            return true;
        }
        if (type == CardType.JOKER_BOMB) {
            return true;
        }
        // 顺子/连对/飞机/四带二：保守认为外部可能接上
        return false;
    }

    // ============ 评估与工具 ============

    /** 非炸候选中"打出后剩余手数最少"者（平手取主权重最小）。 */
    private static Combo cheapestNonBomb(List<Card> hand, List<Combo> candidates) {
        Combo best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Combo c : candidates) {
            if (c.isBomb()) continue;
            int score = HandDecomposer.minHands(after(hand, c)) * 100 + c.getMainWeight();
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** 候选中某牌型主权重最大者。 */
    private static Combo biggestOfKind(List<Combo> candidates, CardType type) {
        Combo best = null;
        for (Combo c : candidates) {
            if (c.getType() == type && !c.isBomb()
                    && (best == null || c.getMainWeight() > best.getMainWeight())) {
                best = c;
            }
        }
        return best;
    }

    /** 候选中第一个炸弹/王炸（HintFinder 已按炸弹主权重升序）。 */
    private static Combo smallestBomb(List<Combo> candidates) {
        for (Combo c : candidates) {
            if (c.isBomb()) return c;
        }
        return null;
    }

    /** 去掉炸弹/王炸后的分解路线各手。 */
    private static List<Combo> nonBombHands(List<Combo> path) {
        List<Combo> out = new ArrayList<>();
        for (Combo c : path) {
            if (!c.isBomb()) out.add(c);
        }
        return out;
    }

    /** 手牌扣除一个已出组合后的剩余。 */
    private static List<Card> after(List<Card> hand, Combo played) {
        List<Card> rest = new ArrayList<>(hand);
        for (Card c : played.getCards()) {
            rest.remove(c);
        }
        return rest;
    }

    /** 从手牌中取指定权重 idx 的 n 张（任意花色）。 */
    private static List<Card> singleton(List<Card> hand, int idx, int n) {
        int weight = idx + 3;
        List<Card> out = new ArrayList<>();
        for (Card c : hand) {
            if (c.getWeight() == weight) {
                out.add(c);
                if (out.size() == n) break;
            }
        }
        return out.size() == n ? out : null;
    }

    /** 手牌中最大的非炸单牌。 */
    private static List<Card> biggestSingle(List<Card> hand) {
        Card best = null;
        for (Card c : hand) {
            if (best == null || c.getWeight() > best.getWeight()) {
                best = c;
            }
        }
        if (best == null) return null;
        List<Card> out = new ArrayList<>();
        out.add(best);
        return out;
    }

    /** 手牌中最小的单牌（可能从对子/三张中拆，简单放水场景可接受）。 */
    private static List<Card> smallestSingle(List<Card> hand) {
        Card best = null;
        for (Card c : hand) {
            if (best == null || c.getWeight() < best.getWeight()) {
                best = c;
            }
        }
        if (best == null) return null;
        List<Card> out = new ArrayList<>();
        out.add(best);
        return out;
    }

    private static Card pickCard(List<Card> hand, Rank rank) {
        for (Card c : hand) {
            if (c.getRank() == rank) return c;
        }
        return null;
    }

    private static int idx(Rank rank) {
        return rank.getWeight() - 3;
    }

    private static int idx(int weight) {
        return weight - 3;
    }

    private static int roleOf(GameContext ctx, int seat) {
        if (ctx.seatRoles == null || seat < 0 || seat >= ctx.seatRoles.length) {
            return ROLE_FARMER;
        }
        return ctx.seatRoles[seat];
    }

    private static boolean isNextSeatTeammate(GameContext ctx) {
        int next = (ctx.mySeatIndex + 1) % 3;
        return ctx.myRole == ROLE_FARMER && roleOf(ctx, next) == ROLE_FARMER;
    }
}
