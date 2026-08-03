// 同步声明：此文件与 app/src/main/java/com/gamecenter/app/games/doudizhu/AIBot.java 保持同步，修改时请同步修改对方文件
package com.gamecenter.app.doudizhu;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.model.Rank;
import com.gamecenter.app.doudizhu.utils.GameRuleUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 斗地主 AI 机器人逻辑类 - 重构第二版 (AI Bot)
 *
 * <p>具备角色战术与大局观的智能决策引擎，所有方法均为 static，
 * 作为无状态工具类使用，每次调用时根据传入参数独立决策。</p>
 *
 * <p>你可以把这个类想象成AI的"军师"——它不负责执行（那是AIHelper的事），
 * 只负责"出谋划策"：分析手牌结构、判断角色战术、决定出什么牌。
 * 每次被问到，它都会根据当前情况独立思考，不记住之前的决策。</p>
 *
 * <p><b>核心优化：</b></p>
 * <ul>
 *   <li>手牌结构化预处理 - 分类为炸弹组、顺子组、三条组、对子组、单牌组
 *       （就像整理手牌：把炸弹放一堆、顺子放一堆，方便决策）</li>
 *   <li>绝对保护机制 - 炸弹/王炸绝不拆开当单牌或对子使用
 *       （炸弹是"杀手锏"，不能浪费在小地方）</li>
 *   <li>角色战术 - 顶牌战术（农民对地主）、放水战术（农民对农民）、单牌报警极限防守</li>
 *   <li>优化首发出牌 - 优先打出"累赘牌"（无法组合的最小单牌/对子）</li>
 * </ul>
 *
 * <p><b>决策流程：</b></p>
 * <ol>
 *   <li>调用 {@link #decidePlay} 入口方法</li>
 *   <li>通过 {@link #analyzeHandStructure} 将手牌结构化预处理</li>
 *   <li>首发出牌走 {@link #decideLeadPlay}，接牌走 {@link #decideFollowPlay}</li>
 *   <li>在决策过程中融合角色战术（顶牌/放水/极限防守）</li>
 * </ol>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>无状态设计：所有方法为 static，不持有游戏状态，线程安全</li>
 *   <li>手牌结构化：将手牌预分类为 HandStructure，避免重复计算</li>
 *   <li>保护机制：炸弹和王炸在接牌时作为最后手段，不轻易拆开</li>
 *   <li>高价值对子（A、2）不轻易拆成单牌使用</li>
 * </ul>
 */
public class AIBot {

    /** AI 思考最小延迟时间（毫秒），用于模拟真实思考过程 */
    private static final long THINKING_DELAY_MIN = 800L;
    /** AI 思考最大延迟时间（毫秒） */
    private static final long THINKING_DELAY_MAX = 1500L;

    // ============ 角色常量 ============

    /** 玩家身份：农民 */
    public static final int ROLE_FARMER = 0;
    /** 玩家身份：地主 */
    public static final int ROLE_LANDLORD = 1;

    /** 总座位数 */
    public static final int SEAT_COUNT = 3;

    // ============ 手牌分类结构 ============

    /**
     * 手牌分类结构 - 将手牌预处理为结构化数据。
     *
     * <p>将手牌按牌型分类存储，便于后续决策时快速查找可用的牌组。
     * 分类优先级从高到低：王炸 > 炸弹 > 顺子/连对 > 三带 > 对子 > 单牌。</p>
     *
     * <p><b>重要：炸弹和王炸受绝对保护，不会在接牌时被拆开当单牌/对子使用。</b></p>
     */
    private static class HandStructure {
        /** 王炸（大小王），受绝对保护 */
        List<Card> jokerBomb = new ArrayList<>();
        /** 炸弹列表（四张相同），受绝对保护 */
        List<List<Card>> bombs = new ArrayList<>();
        /** 三条列表 */
        List<List<Card>> trios = new ArrayList<>();
        /** 对子列表 */
        List<List<Card>> pairs = new ArrayList<>();
        /** 单牌列表（不含组成对子/三张的牌） */
        List<Card> singles = new ArrayList<>();
        /** 顺子列表（5张及以上连续单牌） */
        List<List<Card>> straights = new ArrayList<>();
        /** 连对列表（3对及以上连续对子） */
        List<List<Card>> straightPairs = new ArrayList<>();
        /** 三带一组合列表 */
        List<List<Card>> trioWithSingle = new ArrayList<>();
        /** 三带一对组合列表 */
        List<List<Card>> trioWithPair = new ArrayList<>();
        /** 剩余杂牌（无法归类的牌） */
        List<Card> leftoverCards = new ArrayList<>();

        /** 小王（单独管理，便于王炸检测） */
        Card smallJoker = null;
        /** 大王（单独管理，便于王炸检测） */
        Card bigJoker = null;

        /** 原始手牌副本（兜底使用） */
        List<Card> originalHand = new ArrayList<>();
    }

    // ============ 游戏上下文 ============

    /**
     * 游戏上下文 - 包含战术决策所需的信息。
     *
     * <p>提供角色、座位、地主剩余手牌等上下文信息，
     * 使 AI 能够根据游戏局势做出战术性决策（如顶牌、放水、极限防守）。</p>
     */
    public static class GameContext {
        /** 我的身份：ROLE_FARMER 或 ROLE_LANDLORD */
        public int myRole;
        /** 我的座位索引 */
        public int mySeatIndex;
        /** 各座位的身份（0=农民, 1=地主） */
        public int[] seatRoles;
        /** 地主座位索引 */
        public int landlordSeat;
        /** 地主剩余手牌数（用于极限防守判断） */
        public int landlordRemainCards;
        public int lastPlayerSeat;
        public int teammateSeat;
        public int teammateRemainCards;
        public int nextSeatRemainCards;

        /**
         * 构造游戏上下文。
         *
         * @param myRole             我的身份
         * @param mySeatIndex        我的座位索引
         * @param seatRoles          各座位的身份数组
         * @param landlordSeat       地主座位索引
         * @param landlordRemainCards 地主剩余手牌数
         */
        public GameContext(int myRole, int mySeatIndex, int[] seatRoles, int landlordSeat, int landlordRemainCards) {
            this(myRole, mySeatIndex, seatRoles, landlordSeat, landlordRemainCards, -1, -1, -1, -1);
        }

        public GameContext(int myRole, int mySeatIndex, int[] seatRoles, int landlordSeat, int landlordRemainCards,
                           int lastPlayerSeat, int teammateSeat, int teammateRemainCards, int nextSeatRemainCards) {
            this.myRole = myRole;
            this.mySeatIndex = mySeatIndex;
            this.seatRoles = seatRoles;
            this.landlordSeat = landlordSeat;
            this.landlordRemainCards = landlordRemainCards;
            this.lastPlayerSeat = lastPlayerSeat;
            this.teammateSeat = teammateSeat;
            this.teammateRemainCards = teammateRemainCards;
            this.nextSeatRemainCards = nextSeatRemainCards;
        }
    }

    // ============ 对外接口（增强版）============

    /**
     * 机器人出牌决策方法（兼容旧接口）。
     *
     * <p>使用默认游戏上下文（仅基于手牌决策，不考虑角色战术）。
     * 如需启用角色战术，请使用 {@link #decidePlay(List, List, GameContext)}。</p>
     *
     * @param aiHandCards  AI 的当前手牌
     * @param previousCards 上家打出的牌组（null 表示上家选择不出或为首发出牌）
     * @return 能打过的牌组列表，接不住或选择不出时返回 null
     */
    public static List<Card> decidePlay(List<Card> aiHandCards, List<Card> previousCards) {
        return decidePlay(aiHandCards, previousCards, null);
    }

    /**
     * 机器人出牌决策方法（完整版）。
     *
     * <p>根据游戏上下文（角色、位置、地主剩余手牌）做战术决策。
     * 决策流程：</p>
     * <ol>
     *   <li>空手牌检查</li>
     *   <li>构建手牌结构 {@link HandStructure}</li>
     *   <li>首发出牌走 {@link #decideLeadPlay}，接牌走 {@link #decideFollowPlay}</li>
     * </ol>
     *
     * @param aiHandCards   AI 的当前手牌
     * @param previousCards 上家打出的牌组（null 表示首发出牌或上家不出）
     * @param context       游戏上下文（可为 null，使用默认上下文）
     * @return 能打过的牌组列表，接不住或选择不出时返回 null
     */
    public static List<Card> decidePlay(List<Card> aiHandCards, List<Card> previousCards, GameContext context) {
        return decidePlay(aiHandCards, previousCards, context, 1.0f);
    }

    /**
     * 机器人出牌决策方法（带难度因子）。
     *
     * <p>在原有决策基础上，难度因子 {@code difficultyFactor} 影响 AI 的随机性与激进程度：</p>
     * <ul>
     *   <li>低难度（factor &lt; 1.0，如 0.6）：
     *     <ul>
     *       <li>接牌时以一定概率放弃（被动、易错）</li>
     *       <li>首发出牌时按概率选非最优牌（出偏大的累赘牌，模拟新手失误）</li>
     *       <li>首手开局时按概率从多个候选中随机选，避免每局开局走法雷同</li>
     *     </ul>
     *   </li>
     *   <li>普通难度（factor = 1.0）：保持原有最优决策</li>
     *   <li>高难度（factor &gt; 1.0）：始终不放弃、保持最优，更激进</li>
     * </ul>
     *
     * @param aiHandCards      AI 的当前手牌
     * @param previousCards    上家打出的牌组（null 表示首发出牌或上家不出）
     * @param context          游戏上下文（可为 null，使用默认上下文）
     * @param difficultyFactor 难度因子（&lt;1.0 弱、=1.0 普通、&gt;1.0 强）
     * @return 能打过的牌组列表，接不住或选择不出时返回 null
     */
    public static List<Card> decidePlay(List<Card> aiHandCards, List<Card> previousCards,
                                        GameContext context, float difficultyFactor) {
        if (aiHandCards == null || aiHandCards.isEmpty()) {
            return null;
        }

        // 无上下文时创建默认上下文（默认为农民角色）
        if (context == null) {
            context = createDefaultContext();
        }

        // 构建手牌结构化数据
        HandStructure hs = analyzeHandStructure(aiHandCards);

        List<Card> decided;
        // 首发出牌（上家没有出牌或选择不出）
        if (previousCards == null || previousCards.isEmpty()) {
            decided = decideLeadPlay(hs, context);
            // 修复：低难度首发出牌也要应用随机性（之前只对接牌生效，导致低难度 AI 首发同样强）
            if (decided != null && difficultyFactor < 1.0f) {
                decided = applyLeadPlayVariety(decided, hs, difficultyFactor);
            }
        } else {
            // 获取上家牌型和主牌权重，用于接牌决策
            CardType previousType = GameRuleUtil.getCardType(previousCards);
            int previousMainWeight = GameRuleUtil.getMainWeight(previousCards);
            // 接牌策略
            decided = decideFollowPlay(hs, previousType, previousMainWeight, previousCards.size(), context);
        }

        // 难度因子影响：低难度引入随机性（偶尔放弃接牌，更被动），高难度保持激进不放弃
        if (decided != null && difficultyFactor < 1.0f
                && previousCards != null && !previousCards.isEmpty()) {
            float passChance = (1.0f - difficultyFactor) * 0.35f;
            if (Math.random() < passChance) {
                return null;
            }
        }
        return decided;
    }

    /**
     * 首发出牌随机化（仅低难度启用）。
     * <p>
     * 解决两个问题：
     * <ul>
     *   <li>"难度分级不明显"：低难度 AI 首发原本和普通难度一样强，现在按概率替换为非最优牌，
     *       让低难度 AI 更易被战胜。</li>
     *   <li>"开局步骤几乎一样"：当原决策是单牌时，按概率从手牌中其他单牌候选里加权随机选一张，
     *       增加开局多样性。</li>
     * </ul>
     *
     * <p>策略：
     * <ul>
     *   <li>概率 = (1.0 - factor) * 0.5，最高约 20%（factor=0.6 时）</li>
     *   <li>原决策是单牌时，从手牌中其他单牌候选里选一张权重略大的牌替换</li>
     *   <li>原决策是对子/三带等组合时，按概率替换为更大的同类组合</li>
     *   <li>不替换王炸/炸弹（保留保护机制）</li>
     * </ul>
     *
     * @param decided          原最优决策
     * @param hs               手牌结构
     * @param difficultyFactor 难度因子
     * @return 可能被替换后的决策
     */
    private static List<Card> applyLeadPlayVariety(List<Card> decided, HandStructure hs, float difficultyFactor) {
        // 概率检查
        float varietyChance = (1.0f - difficultyFactor) * 0.5f;
        if (Math.random() >= varietyChance) return decided;
        // 不动炸弹/王炸
        if (decided == null || decided.isEmpty()) return decided;
        int decidedWeight = decided.get(0).getWeight();
        // 大小王权重为 16/17，不动
        if (decidedWeight >= 16) return decided;

        // 原决策是单牌：从手牌中其他单牌里选一张略大的替换（模拟"出错牌"）
        if (decided.size() == 1 && !hs.singles.isEmpty()) {
            // 候选：权重比原决策稍大（最多+3）、不超过 K 的单牌
            List<Card> candidates = new ArrayList<>();
            for (Card c : hs.singles) {
                if (c.getWeight() > decidedWeight && c.getWeight() <= decidedWeight + 3
                        && c.getWeight() < 13) {
                    candidates.add(c);
                }
            }
            if (!candidates.isEmpty()) {
                Card picked = candidates.get((int) (Math.random() * candidates.size()));
                return new ArrayList<>(Collections.singletonList(picked));
            }
        }
        // 原决策是对子：从手牌其他对子里选略大的替换
        if (decided.size() == 2 && !hs.pairs.isEmpty()) {
            List<List<Card>> candidates = new ArrayList<>();
            for (List<Card> pair : hs.pairs) {
                if (pair.isEmpty()) continue;
                int w = pair.get(0).getWeight();
                if (w > decidedWeight && w <= decidedWeight + 3 && w < 13) {
                    candidates.add(pair);
                }
            }
            if (!candidates.isEmpty()) {
                return new ArrayList<>(candidates.get((int) (Math.random() * candidates.size())));
            }
        }
        return decided;
    }

    /**
     * 创建默认游戏上下文（当无法获取真实上下文时使用）。
     *
     * <p>默认为农民角色，地主剩余17张牌。
     * 此上下文不包含角色战术信息，AI 将仅基于手牌结构决策。</p>
     *
     * @return 默认游戏上下文
     */
    private static GameContext createDefaultContext() {
        return new GameContext(ROLE_FARMER, 0, new int[]{ROLE_FARMER, ROLE_FARMER, ROLE_FARMER}, -1, 17);
    }

    private static int nextSeat(int seatIndex) {
        return (seatIndex + 1 + SEAT_COUNT) % SEAT_COUNT;
    }

    private static boolean isValidSeat(int seatIndex) {
        return seatIndex >= 0 && seatIndex < SEAT_COUNT;
    }

    private static boolean isSeatRole(GameContext context, int seatIndex, int role) {
        return context != null
                && context.seatRoles != null
                && isValidSeat(seatIndex)
                && seatIndex < context.seatRoles.length
                && context.seatRoles[seatIndex] == role;
    }

    private static boolean isPreviousPlayFromTeammate(GameContext context) {
        return context != null
                && context.myRole == ROLE_FARMER
                && isValidSeat(context.lastPlayerSeat)
                && context.lastPlayerSeat != context.mySeatIndex
                && isSeatRole(context, context.lastPlayerSeat, ROLE_FARMER);
    }

    private static boolean isPreviousPlayFromLandlord(GameContext context) {
        return context != null
                && context.myRole == ROLE_FARMER
                && isValidSeat(context.lastPlayerSeat)
                && isSeatRole(context, context.lastPlayerSeat, ROLE_LANDLORD);
    }

    private static boolean isNextTeammateReadyToWin(GameContext context) {
        return context != null
                && context.myRole == ROLE_FARMER
                && context.nextSeatRemainCards > 0
                && context.nextSeatRemainCards <= 2
                && isSeatRole(context, nextSeat(context.mySeatIndex), ROLE_FARMER);
    }

    private static boolean shouldUseEmergencyBomb(GameContext context) {
        return isPreviousPlayFromLandlord(context) && context.landlordRemainCards > 0 && context.landlordRemainCards <= 2;
    }

    // ============ 手牌结构化预处理 ============

    /**
     * 分析手牌结构，将手牌分类为炸弹、王炸、三条、对子、单牌、顺子等。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>统计各牌值（weight）的数量</li>
     *   <li>分离大小王，检测王炸</li>
     *   <li>按数量分组：4张→炸弹，3张→三条，2张→对子，1张→单牌</li>
     *   <li>从单牌中尝试构建顺子</li>
     *   <li>从对子中尝试构建连对</li>
     *   <li>从三条+单牌/对子构建三带一/三带一对</li>
     *   <li>按权重降序排序所有分组</li>
     * </ol>
     *
     * @param handCards 原始手牌
     * @return 结构化的手牌数据
     */
    private static HandStructure analyzeHandStructure(List<Card> handCards) {
        HandStructure hs = new HandStructure();
        hs.originalHand = new ArrayList<>(handCards);

        // 统计各牌值数量
        Map<Integer, List<Card>> rankGroups = new HashMap<>();
        for (Card card : handCards) {
            int weight = card.getWeight();
            if (!rankGroups.containsKey(weight)) {
                rankGroups.put(weight, new ArrayList<>());
            }
            rankGroups.get(weight).add(card);
        }

        // 分离大小王（单独管理，便于王炸检测）
        for (Card card : handCards) {
            if (card.getRank() == Rank.SMALL_JOKER) {
                hs.smallJoker = card;
            } else if (card.getRank() == Rank.BIG_JOKER) {
                hs.bigJoker = card;
            }
        }

        // 检测王炸：大小王同时存在时组合为王炸，并从分组中移除
        if (hs.smallJoker != null && hs.bigJoker != null) {
            hs.jokerBomb.add(hs.smallJoker);
            hs.jokerBomb.add(hs.bigJoker);
            rankGroups.remove(Rank.SMALL_JOKER.getWeight());
            rankGroups.remove(Rank.BIG_JOKER.getWeight());
        }

        // 按牌值数量分组
        for (Map.Entry<Integer, List<Card>> entry : rankGroups.entrySet()) {
            List<Card> cards = entry.getValue();
            int count = cards.size();

            switch (count) {
                case 4:
                    hs.bombs.add(new ArrayList<>(cards));
                    break;
                case 3:
                    hs.trios.add(new ArrayList<>(cards));
                    break;
                case 2:
                    hs.pairs.add(new ArrayList<>(cards));
                    break;
                case 1:
                    hs.singles.add(cards.get(0));
                    break;
                default:
                    hs.leftoverCards.addAll(cards);
                    break;
            }
        }

        // 从单牌中尝试构建顺子（5张及以上连续牌，不含2和王）
        hs.straights = findPossibleStraights(hs.singles);

        // 从对子中尝试构建连对（3对及以上连续对子，不含2和王）
        hs.straightPairs = findPossibleStraightPairs(hs.pairs);

        // 从三条+单牌构建三带一
        hs.trioWithSingle = findPossibleTrioWithSingle(hs.trios, hs.singles);

        // 从三条+对子构建三带一对
        hs.trioWithPair = findPossibleTrioWithPair(hs.trios, hs.pairs);

        // 按权重降序排序所有分组（最大的在前，便于决策时优先使用小牌）
        sortHandStructure(hs);

        return hs;
    }

    /**
     * 对手牌结构中所有分组按权重降序排序。
     *
     * <p>排序后最大的牌在列表头部，最小的牌在列表尾部。
     * 这样在出牌时可以从尾部取最小的牌（累赘牌优先），
     * 在接牌时可以从头部取最大的牌（确保能管上）。</p>
     *
     * @param hs 手牌结构
     */
    private static void sortHandStructure(HandStructure hs) {
        // 单牌按权重降序（最大的在前）
        Collections.sort(hs.singles, (c1, c2) -> Integer.compare(c2.getWeight(), c1.getWeight()));
        sortCardListList(hs.bombs);
        sortCardListList(hs.trios);
        sortCardListList(hs.pairs);
        sortCardListList(hs.straights);
        sortCardListList(hs.straightPairs);
        sortCardListList(hs.trioWithSingle);
        sortCardListList(hs.trioWithPair);
    }

    /**
     * 对卡牌列表的列表按首牌权重降序排序。
     *
     * @param list 卡牌列表的列表
     */
    private static void sortCardListList(List<List<Card>> list) {
        Collections.sort(list, (l1, l2) -> {
            if (l1.isEmpty() || l2.isEmpty()) return 0;
            return Integer.compare(l2.get(0).getWeight(), l1.get(0).getWeight());
        });
    }

    // ============ 战术决策辅助方法 ============

    /**
     * 检查下一个出牌的座位是否为地主。
     *
     * <p>用于顶牌战术判断：如果下家是地主，农民应出偏大的牌阻断地主。</p>
     *
     * @param context 游戏上下文
     * @return true 表示下家是地主
     */
    private static boolean isNextSeatLandlord(GameContext context) {
        int nextSeat = nextSeat(context.mySeatIndex);
        return isSeatRole(context, nextSeat, ROLE_LANDLORD);
    }

    /**
     * 检查下家是否为农民（用于放水战术）。
     *
     * <p>如果下家是农民队友，应出最小的牌帮助队友跑牌。</p>
     *
     * @param context 游戏上下文
     * @return true 表示下家是农民
     */
    private static boolean isNextSeatFarmer(GameContext context) {
        int nextSeat = nextSeat(context.mySeatIndex);
        return isSeatRole(context, nextSeat, ROLE_FARMER);
    }

    /**
     * 检查是否需要极限单牌防守。
     *
     * <p>条件：AI 是农民，且地主只剩 1 张牌。
     * 此时农民必须出最大的单牌，防止地主用小牌跑完。</p>
     *
     * @param context 游戏上下文
     * @return true 表示需要极限防守
     */
    private static boolean needsOneCardDefense(GameContext context) {
        return context.myRole == ROLE_FARMER
            && context.landlordRemainCards == 1;
    }

    /**
     * 获取最大的单牌（用于极限防守）。
     *
     * <p>注意：singles 已按权重降序排序，第一个元素即为最大。</p>
     *
     * @param hs 手牌结构
     * @return 最大的单牌，无单牌时返回 null
     */
    private static Card getLargestSingle(HandStructure hs) {
        if (hs.singles.isEmpty()) {
            return null;
        }
        return hs.singles.get(0);
    }

    /**
     * 获取能打过上家的最小单牌（考虑极限防守）。
     *
     * <p>极限防守模式下直接返回最大单牌（不管是否能管上），
     * 正常模式下从单牌中找到比目标权重大的最小牌。</p>
     *
     * @param hs           手牌结构
     * @param targetWeight 需要超过的目标权重
     * @param context      游戏上下文
     * @return 能管上的最小单牌，没有时返回 null
     */
    private static Card getBestSingleToBeat(HandStructure hs, int targetWeight, GameContext context) {
        // 极限防守优先：必须出最大的牌
        if (needsOneCardDefense(context)) {
            return getLargestSingle(hs);
        }

        // 正常情况：出最小的能管上的牌（singles 降序排列，从后往前找）
        for (int i = hs.singles.size() - 1; i >= 0; i--) {
            Card single = hs.singles.get(i);
            if (single.getWeight() > targetWeight) {
                return single;
            }
        }
        return null;
    }

    // ============ 首发出牌策略（融合战术）============

    /**
     * 首发决策 - 融合角色战术。
     *
     * <p>按优先级依次尝试：</p>
     * <ol>
     *   <li>战术1：单牌报警极限防守（地主只剩1张时，农民出最大单牌）</li>
     *   <li>战术2：顶牌战术（农民对地主，出偏大的牌阻断）</li>
     *   <li>战术3：放水战术（农民对农民，出最小的牌帮助队友）</li>
     *   <li>默认策略：出累赘牌（无法组合的最小单牌/对子）</li>
     * </ol>
     *
     * @param hs      手牌结构
     * @param context 游戏上下文
     * @return 首发出的牌组
     */
    private static List<Card> decideLeadPlay(HandStructure hs, GameContext context) {
        // 战术1：单牌报警极限防守（最高优先级）
        if (needsOneCardDefense(context)) {
            Card topSingle = getLargestSingle(hs);
            if (topSingle != null) {
                return new ArrayList<>(Collections.singletonList(topSingle));
            }
        }

        if (isNextTeammateReadyToWin(context)) {
            Card smallestCard = getSmallestCard(hs);
            if (smallestCard != null) {
                return new ArrayList<>(Collections.singletonList(smallestCard));
            }
        }

        // 战术2：顶牌战术（农民对地主，出Q以上的偏大牌阻断）
        if (context.myRole == ROLE_FARMER && isNextSeatLandlord(context)) {
            Card blockingCard = getBlockingCard(hs);
            if (blockingCard != null) {
                return new ArrayList<>(Collections.singletonList(blockingCard));
            }
        }

        // 战术3：放水战术（农民对农民，出最小的牌帮助队友跑牌）
        if (context.myRole == ROLE_FARMER && isNextSeatFarmer(context)) {
            Card smallestCard = getSmallestCard(hs);
            if (smallestCard != null) {
                return new ArrayList<>(Collections.singletonList(smallestCard));
            }
        }

        // 默认首发策略：出累赘牌
        return decideLeadPlayDefault(hs);
    }

    /**
     * 获取顶牌（用于阻断地主）。
     *
     * <p>选择 Q（权重12）以上的牌进行阻断，防止地主用小牌跑牌。
     * 如果没有足够的顶牌，回退到出最小的牌。</p>
     *
     * @param hs 手牌结构
     * @return 顶牌卡牌，无可用牌时返回 null
     */
    private static Card getBlockingCard(HandStructure hs) {
        int minBlockingWeight = Rank.QUEEN.getWeight();

        for (int i = hs.singles.size() - 1; i >= 0; i--) {
            Card card = hs.singles.get(i);
            if (card.getWeight() >= minBlockingWeight) {
                return card;
            }
        }

        // 没有足够的顶牌，回退到出最小的牌
        return getSmallestCard(hs);
    }

    /**
     * 获取最小的牌（用于放水战术）。
     *
     * <p>优先从单牌中取最小牌，无单牌时尝试拆对子取最小的一张。</p>
     *
     * @param hs 手牌结构
     * @return 最小的卡牌，无可用牌时返回 null
     */
    private static Card getSmallestCard(HandStructure hs) {
        if (hs.singles.isEmpty()) {
            // 无单牌时拆对子（取最小对子的第一张）
            if (!hs.pairs.isEmpty()) {
                return hs.pairs.get(hs.pairs.size() - 1).get(0);
            }
            return null;
        }
        // singles 已按降序排序，最后一个是最小的
        return hs.singles.get(hs.singles.size() - 1);
    }

    /**
     * 默认首发策略（累赘牌优先）。
     *
     * <p>按优先级依次尝试：</p>
     * <ol>
     *   <li>最小的单牌（最优先出，减少手牌数）</li>
     *   <li>最小的对子</li>
     *   <li>最小的三带一</li>
     *   <li>最小的三条</li>
     *   <li>最小的顺子</li>
     *   <li>最小的连对</li>
     *   <li>最小的三带一对</li>
     *   <li>最小的炸弹（迫不得已）</li>
     *   <li>王炸（最后手段）</li>
     *   <li>兜底：出原始手牌中最小的任意一张</li>
     * </ol>
     *
     * @param hs 手牌结构
     * @return 首发出的牌组，无牌可出时返回 null
     */
    private static List<Card> decideLeadPlayDefault(HandStructure hs) {
        // 1. 优先出单牌中的累赘牌（最小的单牌）
        if (!hs.singles.isEmpty()) {
            Card smallest = hs.singles.get(hs.singles.size() - 1);
            return new ArrayList<>(Collections.singletonList(smallest));
        }

        // 2. 出最小的对子
        if (!hs.pairs.isEmpty()) {
            return new ArrayList<>(hs.pairs.get(hs.pairs.size() - 1));
        }

        // 3. 出最小的三带一
        if (!hs.trioWithSingle.isEmpty()) {
            return new ArrayList<>(hs.trioWithSingle.get(hs.trioWithSingle.size() - 1));
        }

        // 4. 出最小的三条（不带）
        if (!hs.trios.isEmpty()) {
            return new ArrayList<>(hs.trios.get(hs.trios.size() - 1));
        }

        // 5. 出最小的顺子
        if (!hs.straights.isEmpty()) {
            List<Card> smallestStraight = hs.straights.get(hs.straights.size() - 1);
            return new ArrayList<>(smallestStraight);
        }

        // 6. 出最小的连对
        if (!hs.straightPairs.isEmpty()) {
            return new ArrayList<>(hs.straightPairs.get(hs.straightPairs.size() - 1));
        }

        // 7. 出最小的三带一对
        if (!hs.trioWithPair.isEmpty()) {
            return new ArrayList<>(hs.trioWithPair.get(hs.trioWithPair.size() - 1));
        }

        // 8. 最后出炸弹（迫不得已）
        if (!hs.bombs.isEmpty()) {
            return new ArrayList<>(hs.bombs.get(hs.bombs.size() - 1));
        }

        // 9. 王炸（最后手段）
        if (!hs.jokerBomb.isEmpty()) {
            return new ArrayList<>(hs.jokerBomb);
        }

        // 10. 兜底：出原始手牌中最小的任意一张
        if (!hs.originalHand.isEmpty()) {
            List<Card> sorted = new ArrayList<>(hs.originalHand);
            Collections.sort(sorted, (c1, c2) -> Integer.compare(c1.getWeight(), c2.getWeight()));
            return new ArrayList<>(Collections.singletonList(sorted.get(0)));
        }

        return null;
    }

    // ============ 接牌策略（融合战术）============

    /**
     * 接牌决策 - 融合角色战术。
     *
     * <p>根据上家的牌型选择对应的接牌处理方法，并在关键位置融入战术：</p>
     * <ul>
     *   <li>王炸无法接（直接返回 null）</li>
     *   <li>炸弹接牌走 {@link #handleBombFollow}</li>
     *   <li>极限防守：地主只剩1张牌时，单牌必须出最大</li>
     *   <li>其他牌型走对应的 handleXxxFollow 方法</li>
     * </ul>
     *
     * @param hs                手牌结构
     * @param previousType      上家牌型
     * @param previousMainWeight 上家主牌权重
     * @param previousCount     上家出牌数量
     * @param context           游戏上下文
     * @return 能管上的牌组，接不住时返回 null
     */
    private static List<Card> decideFollowPlay(HandStructure hs, CardType previousType,
                                               int previousMainWeight, int previousCount,
                                               GameContext context) {
        // 王炸无法接
        if (previousType == CardType.JOKER_BOMB) {
            return null;
        }

        if (isPreviousPlayFromTeammate(context)) {
            return null;
        }

        // 炸弹接牌需要更大的炸弹或王炸
        if (previousType == CardType.BOMB) {
            return handleBombFollow(hs, previousMainWeight);
        }

        // 极限防守：地主只剩1张牌且上家出单牌，农民必须出最大单牌
        if (needsOneCardDefense(context) && previousType == CardType.SINGLE) {
            Card topSingle = getLargestSingle(hs);
            if (topSingle != null && topSingle.getWeight() > previousMainWeight) {
                return new ArrayList<>(Collections.singletonList(topSingle));
            }
            return null;
        }

        // 根据上家牌型分派到对应的接牌处理方法
        List<Card> result;
        switch (previousType) {
            case SINGLE:
                result = handleSingleFollow(hs, previousMainWeight, context);
                break;
            case PAIR:
                result = handlePairFollow(hs, previousMainWeight);
                break;
            case TRIO:
                result = handleTrioFollow(hs, previousMainWeight);
                break;
            case TRIO_SINGLE:
                result = handleTrioSingleFollow(hs, previousMainWeight);
                break;
            case TRIO_PAIR:
                result = handleTrioPairFollow(hs, previousMainWeight);
                break;
            case STRAIGHT:
                result = handleStraightFollow(hs, previousMainWeight, previousCount);
                break;
            case STRAIGHT_PAIRS:
                // 连对的 pairCount = 出牌数 / 2
                result = handleStraightPairsFollow(hs, previousMainWeight, previousCount / 2);
                break;
            default:
                result = null;
                break;
        }
        if (result == null && shouldUseEmergencyBomb(context)) {
            return findSmallestBombOrRocket(hs);
        }
        return result;
    }

    /**
     * 处理炸弹接牌。
     *
     * <p>优先使用王炸，其次找更大的炸弹。炸弹受绝对保护，
     * 不会为了接非炸弹牌型而拆开炸弹。</p>
     *
     * @param hs             手牌结构
     * @param previousWeight 上家炸弹的权重
     * @return 能管上的炸弹牌组，接不住时返回 null
     */
    private static List<Card> handleBombFollow(HandStructure hs, int previousWeight) {
        // 王炸可以管任何炸弹
        if (!hs.jokerBomb.isEmpty()) {
            return new ArrayList<>(hs.jokerBomb);
        }

        // 找比上家更大的炸弹
        for (int i = hs.bombs.size() - 1; i >= 0; i--) {
            List<Card> bomb = hs.bombs.get(i);
            if (bomb.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(bomb);
            }
        }

        return null;
    }

    /**
     * 处理单牌接牌 - 融入战术。
     *
     * <p>接牌优先级：</p>
     * <ol>
     *   <li>极限防守：地主只剩1张时出最大单牌</li>
     *   <li>从纯单牌中找比上家大的最小牌</li>
     *   <li>拆对子（不拆高价值对子 A/2）</li>
     *   <li>拆三条（迫不得已）</li>
     * </ol>
     *
     * @param hs             手牌结构
     * @param previousWeight 上家单牌的权重
     * @param context        游戏上下文
     * @return 能管上的单牌列表，接不住时返回 null
     */
    private static List<Card> findSmallestBombOrRocket(HandStructure hs) {
        if (!hs.bombs.isEmpty()) {
            return new ArrayList<>(hs.bombs.get(hs.bombs.size() - 1));
        }
        if (!hs.jokerBomb.isEmpty()) {
            return new ArrayList<>(hs.jokerBomb);
        }
        return null;
    }

    private static List<Card> handleSingleFollow(HandStructure hs, int previousWeight, GameContext context) {
        // 极限防守：必须出最大的牌
        if (needsOneCardDefense(context)) {
            Card topSingle = getLargestSingle(hs);
            if (topSingle != null && topSingle.getWeight() > previousWeight) {
                return new ArrayList<>(Collections.singletonList(topSingle));
            }
            return null;
        }

        // 正常接牌：从纯单牌中找比上家大的最小单牌
        Card bestSingle = getBestSingleToBeat(hs, previousWeight, context);
        if (bestSingle != null) {
            return new ArrayList<>(Collections.singletonList(bestSingle));
        }

        // 尝试拆对子（不拆高价值对子 A/2）
        for (int i = hs.pairs.size() - 1; i >= 0; i--) {
            List<Card> pair = hs.pairs.get(i);
            int weight = pair.get(0).getWeight();
            if (weight > previousWeight && !isHighValuePair(weight)) {
                List<Card> result = new ArrayList<>();
                result.add(pair.get(0));
                return result;
            }
        }

        // 尝试拆三条（迫不得已）
        for (int i = hs.trios.size() - 1; i >= 0; i--) {
            List<Card> trio = hs.trios.get(i);
            int weight = trio.get(0).getWeight();
            if (weight > previousWeight) {
                List<Card> result = new ArrayList<>();
                result.add(trio.get(0));
                return result;
            }
        }

        return null;
    }

    /**
     * 处理对子接牌。
     *
     * <p>从对子列表中找比上家大的最小对子。
     * 注意：不会拆炸弹来组对子（炸弹受绝对保护）。</p>
     *
     * @param hs             手牌结构
     * @param previousWeight 上家对子的权重
     * @return 能管上的对子列表，接不住时返回 null
     */
    private static List<Card> handlePairFollow(HandStructure hs, int previousWeight) {
        for (int i = hs.pairs.size() - 1; i >= 0; i--) {
            List<Card> pair = hs.pairs.get(i);
            if (pair.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(pair);
            }
        }
        return null;
    }

    /**
     * 处理三条接牌。
     *
     * @param hs             手牌结构
     * @param previousWeight 上家三条的权重
     * @return 能管上的三条列表，接不住时返回 null
     */
    private static List<Card> handleTrioFollow(HandStructure hs, int previousWeight) {
        for (List<Card> trio : hs.trios) {
            if (trio.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(trio);
            }
        }
        return null;
    }

    /**
     * 处理三带一接牌。
     *
     * <p>优先从预构建的三带一组合中查找，找不到时尝试用三条+单牌临时组合。</p>
     *
     * @param hs             手牌结构
     * @param previousWeight 上家三条部分的权重
     * @return 能管上的三带一列表，接不住时返回 null
     */
    private static List<Card> handleTrioSingleFollow(HandStructure hs, int previousWeight) {
        // 从预构建的三带一组合中查找
        for (List<Card> combo : hs.trioWithSingle) {
            if (combo.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(combo);
            }
        }

        // 尝试用三条+单牌临时组合
        for (List<Card> trio : hs.trios) {
            if (trio.get(0).getWeight() > previousWeight && !hs.singles.isEmpty()) {
                List<Card> result = new ArrayList<>(trio);
                for (Card single : hs.singles) {
                    // 确保带出的单牌不与三条重复
                    if (single.getWeight() != trio.get(0).getWeight()) {
                        result.add(single);
                        return result;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 处理三带一对接牌。
     *
     * @param hs             手牌结构
     * @param previousWeight 上家三条部分的权重
     * @return 能管上的三带一对列表，接不住时返回 null
     */
    private static List<Card> handleTrioPairFollow(HandStructure hs, int previousWeight) {
        for (List<Card> combo : hs.trioWithPair) {
            if (combo.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(combo);
            }
        }
        return null;
    }

    /**
     * 处理顺子接牌。
     *
     * <p>从预构建的顺子中查找比上家大的顺子，截取所需长度。</p>
     *
     * @param hs             手牌结构
     * @param previousWeight 上家顺子的起始权重
     * @param length         顺子所需长度
     * @return 能管上的顺子列表，接不住时返回 null
     */
    private static List<Card> handleStraightFollow(HandStructure hs, int previousWeight, int length) {
        for (List<Card> straight : hs.straights) {
            if (straight.size() >= length && straight.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(straight.subList(0, length));
            }
        }
        return null;
    }

    /**
     * 处理连对接牌。
     *
     * <p>从预构建的连对中查找比上家大的连对，截取所需对数。</p>
     *
     * @param hs             手牌结构
     * @param previousWeight 上家连对的起始权重
     * @param pairCount      连对所需的对数
     * @return 能管上的连对列表，接不住时返回 null
     */
    private static List<Card> handleStraightPairsFollow(HandStructure hs, int previousWeight, int pairCount) {
        for (List<Card> straightPairs : hs.straightPairs) {
            if (straightPairs.size() / 2 >= pairCount &&
                straightPairs.get(0).getWeight() > previousWeight) {
                List<Card> result = new ArrayList<>();
                for (int i = 0; i < pairCount * 2; i++) {
                    result.add(straightPairs.get(i));
                }
                return result;
            }
        }
        return null;
    }

    // ============ 辅助方法 ============

    /**
     * 判断对子是否为高价值（不轻易拆成单牌使用）。
     *
     * <p>A 和 2 的对子属于高价值对子，除非迫不得已不会拆开。</p>
     *
     * @param weight 对子的权重值
     * @return true 表示为高价值对子
     */
    private static boolean isHighValuePair(int weight) {
        if (weight >= Rank.ACE.getWeight()) return true;
        if (weight == Rank.TWO.getWeight()) return true;
        return false;
    }

    /**
     * 判断权重是否为王牌（小王或大王）。
     *
     * @param weight 牌面权重
     * @return true 表示为王牌
     */
    private static boolean isJokerWeight(int weight) {
        return weight == Rank.SMALL_JOKER.getWeight() || weight == Rank.BIG_JOKER.getWeight();
    }

    /**
     * 判断权重是否为 2。
     *
     * <p>2 不能参与顺子和连对（斗地主规则）。</p>
     *
     * @param weight 牌面权重
     * @return true 表示为 2
     */
    private static boolean isTwoWeight(int weight) {
        return weight == Rank.TWO.getWeight();
    }

    /**
     * 从单牌中查找所有可能的顺子组合。
     *
     * <p>顺子规则：5张及以上连续单牌，不含2和王。
     * 算法：先过滤掉2和王，按权重升序排序，
     * 然后滑动窗口查找连续递增的子序列。</p>
     *
     * @param singles 单牌列表
     * @return 所有可能的顺子组合列表
     */
    private static List<List<Card>> findPossibleStraights(List<Card> singles) {
        List<List<Card>> straights = new ArrayList<>();
        if (singles.size() < 5) return straights;

        // 过滤掉2和王（不能参与顺子）
        List<Card> validSingles = new ArrayList<>();
        for (Card card : singles) {
            if (!card.getRank().isJoker() && !card.getRank().isTwo()) {
                validSingles.add(card);
            }
        }

        if (validSingles.size() < 5) return straights;

        // 按权重升序排序
        Collections.sort(validSingles, (c1, c2) -> Integer.compare(c1.getWeight(), c2.getWeight()));

        // 滑动窗口查找连续递增的子序列
        for (int start = 0; start <= validSingles.size() - 5; start++) {
            List<Card> straight = new ArrayList<>();
            straight.add(validSingles.get(start));

            for (int i = start + 1; i < validSingles.size(); i++) {
                Card current = validSingles.get(i);
                Card last = straight.get(straight.size() - 1);

                if (current.getWeight() == last.getWeight() + 1) {
                    // 连续递增，加入顺子
                    straight.add(current);
                    if (straight.size() >= 5) {
                        // 达到5张即可构成顺子，记录当前状态
                        List<Card> newStraight = new ArrayList<>(straight);
                        straights.add(newStraight);
                    }
                } else if (current.getWeight() > last.getWeight() + 1) {
                    // 不连续，终止当前窗口
                    break;
                }
                // 相等权重（同一点数多张）跳过
            }
        }

        return straights;
    }

    /**
     * 从对子中查找所有可能的连对组合。
     *
     * <p>连对规则：3对及以上连续对子，不含2和王。
     * 算法：先过滤掉2和王的权重，按权重升序排序，
     * 然后滑动窗口查找连续递增的权重序列，再从原始对子中收集对应卡牌。</p>
     *
     * @param pairs 对子列表
     * @return 所有可能的连对组合列表
     */
    private static List<List<Card>> findPossibleStraightPairs(List<List<Card>> pairs) {
        List<List<Card>> straightPairs = new ArrayList<>();
        if (pairs.size() < 3) return straightPairs;

        // 过滤掉2和王的权重
        List<Integer> validWeights = new ArrayList<>();
        for (List<Card> pair : pairs) {
            int weight = pair.get(0).getWeight();
            if (!isJokerWeight(weight) && !isTwoWeight(weight)) {
                validWeights.add(weight);
            }
        }

        if (validWeights.size() < 3) return straightPairs;

        Collections.sort(validWeights);

        // 滑动窗口查找连续递增的权重序列
        for (int start = 0; start <= validWeights.size() - 3; start++) {
            List<Integer> straightWeights = new ArrayList<>();
            straightWeights.add(validWeights.get(start));

            for (int i = start + 1; i < validWeights.size(); i++) {
                int currentWeight = validWeights.get(i);
                int lastWeight = straightWeights.get(straightWeights.size() - 1);

                if (currentWeight == lastWeight + 1) {
                    straightWeights.add(currentWeight);
                    if (straightWeights.size() >= 3) {
                        // 达到3对即可构成连对，从原始对子中收集卡牌
                        List<Card> straightPair = new ArrayList<>();
                        for (int w : straightWeights) {
                            for (List<Card> pair : pairs) {
                                if (!pair.isEmpty() && pair.get(0).getWeight() == w) {
                                    straightPair.addAll(pair);
                                    break;
                                }
                            }
                        }
                        // 验证收集的卡牌数量是否正确（每个权重应恰好2张）
                        if (straightPair.size() == straightWeights.size() * 2) {
                            straightPairs.add(straightPair);
                        }
                    }
                } else if (currentWeight > lastWeight + 1) {
                    break;
                }
            }
        }

        return straightPairs;
    }

    /**
     * 从三条和单牌中查找所有可能的三带一组合。
     *
     * <p>对每个三条，找到权重不同于该三条的最小单牌作为附带牌。</p>
     *
     * @param trios   三条列表
     * @param singles 单牌列表
     * @return 所有可能的三带一组合列表
     */
    private static List<List<Card>> findPossibleTrioWithSingle(List<List<Card>> trios, List<Card> singles) {
        List<List<Card>> result = new ArrayList<>();

        for (List<Card> trio : trios) {
            if (singles.isEmpty()) break;
            int trioWeight = trio.get(0).getWeight();

            // 找权重不同于三条的最小单牌
            Card smallestSingle = null;
            for (Card single : singles) {
                if (single.getWeight() != trioWeight) {
                    if (smallestSingle == null || single.getWeight() < smallestSingle.getWeight()) {
                        smallestSingle = single;
                    }
                }
            }

            if (smallestSingle != null) {
                List<Card> combo = new ArrayList<>(trio);
                combo.add(smallestSingle);
                result.add(combo);
            }
        }

        return result;
    }

    /**
     * 从三条和对子中查找所有可能的三带一对组合。
     *
     * <p>对每个三条，找到权重不同于该三条的最小对子作为附带对子。</p>
     *
     * @param trios 三条列表
     * @param pairs 对子列表
     * @return 所有可能的三带一对组合列表
     */
    private static List<List<Card>> findPossibleTrioWithPair(List<List<Card>> trios, List<List<Card>> pairs) {
        List<List<Card>> result = new ArrayList<>();

        for (List<Card> trio : trios) {
            if (pairs.isEmpty()) break;
            int trioWeight = trio.get(0).getWeight();

            // 找权重不同于三条的最小对子
            List<Card> smallestPair = null;
            for (List<Card> pair : pairs) {
                if (pair.get(0).getWeight() != trioWeight) {
                    if (smallestPair == null || pair.get(0).getWeight() < smallestPair.get(0).getWeight()) {
                        smallestPair = pair;
                    }
                }
            }

            if (smallestPair != null) {
                List<Card> combo = new ArrayList<>(trio);
                combo.addAll(smallestPair);
                result.add(combo);
            }
        }

        return result;
    }

    /**
     * 获取随机的 AI 思考延迟时间。
     *
     * <p>在 [THINKING_DELAY_MIN, THINKING_DELAY_MAX) 范围内随机取值，
     * 用于模拟真实玩家的思考时间，提升游戏体验。</p>
     *
     * @return 随机延迟时间（毫秒）
     */
    public static long getRandomThinkingDelay() {
        return THINKING_DELAY_MIN + (long) (Math.random() * (THINKING_DELAY_MAX - THINKING_DELAY_MIN));
    }
}
