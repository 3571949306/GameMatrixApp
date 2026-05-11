package com.gamecenter.app.games.doudizhu;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.CardType;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.utils.GameRuleUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 斗地主 AI 机器人逻辑类 - 重构第二版 (AI Bot)
 * 具备角色战术与大局观的智能决策
 *
 * 核心优化：
 * 1. 手牌结构化预处理 - 分类为炸弹组、顺子组、三条组、对子组、单牌组
 * 2. 绝对保护机制 - 炸弹/王炸绝不拆开当单牌或对子使用
 * 3. 角色战术 - 顶牌战术、放水战术、单牌报警极限防守
 * 4. 优化首发出牌 - 优先打出"累赘牌"（无法组合的最小单牌/对子）
 */
public class AIBot {

    // AI 思考延迟时间（毫秒）
    private static final long THINKING_DELAY_MIN = 800L;
    private static final long THINKING_DELAY_MAX = 1500L;

    // ============ 角色常量 ============

    // 玩家身份
    public static final int ROLE_FARMER = 0;    // 农民
    public static final int ROLE_LANDLORD = 1;   // 地主

    // 座位常量
    public static final int SEAT_COUNT = 3;     // 总座位数

    // ============ 手牌分类结构 ============

    /**
     * 手牌分类结构 - 将手牌预处理为结构化数据
     */
    private static class HandStructure {
        // 高价值资产（受绝对保护）
        List<Card> jokerBomb = new ArrayList<>();      // 王炸
        List<List<Card>> bombs = new ArrayList<>();    // 炸弹列表
        List<List<Card>> trios = new ArrayList<>();    // 三条列表
        List<List<Card>> pairs = new ArrayList<>();    // 对子列表
        List<Card> singles = new ArrayList<>();        // 单牌列表（不含组成对子/三张的）
        List<List<Card>> straights = new ArrayList<>(); // 顺子列表
        List<List<Card>> straightPairs = new ArrayList<>(); // 连对列表
        List<List<Card>> trioWithSingle = new ArrayList<>(); // 三带一
        List<List<Card>> trioWithPair = new ArrayList<>();  // 三带一对
        List<Card> leftoverCards = new ArrayList<>();   // 剩余杂牌

        // 王牌（单独管理）
        Card smallJoker = null;
        Card bigJoker = null;

        // 原始手牌副本
        List<Card> originalHand = new ArrayList<>();
    }

    // ============ 游戏上下文 ============

    /**
     * 游戏上下文 - 包含战术决策所需的信息
     */
    public static class GameContext {
        public int myRole;           // 我的身份：ROLE_FARMER 或 ROLE_LANDLORD
        public int mySeatIndex;      // 我的座位索引
        public int[] seatRoles;     // 各座位的身份（0=农民, 1=地主）
        public int landlordSeat;     // 地主座位索引
        public int landlordRemainCards; // 地主剩余手牌数

        public GameContext(int myRole, int mySeatIndex, int[] seatRoles, int landlordSeat, int landlordRemainCards) {
            this.myRole = myRole;
            this.mySeatIndex = mySeatIndex;
            this.seatRoles = seatRoles;
            this.landlordSeat = landlordSeat;
            this.landlordRemainCards = landlordRemainCards;
        }
    }

    // ============ 对外接口（增强版）============

    /**
     * 机器人出牌决策方法（兼容旧接口）
     * 使用默认游戏上下文（仅基于手牌决策）
     *
     * @param aiHandCards AI 的当前手牌
     * @param previousCards 上家打出的牌组（null 表示上家选择过）
     * @return 能打过的牌组列表，如果接不住返回 null
     */
    public static List<Card> decidePlay(List<Card> aiHandCards, List<Card> previousCards) {
        return decidePlay(aiHandCards, previousCards, null);
    }

    /**
     * 机器人出牌决策方法（完整版）
     * 根据游戏上下文（角色、位置、地主剩余手牌）做战术决策
     *
     * @param aiHandCards AI 的当前手牌
     * @param previousCards 上家打出的牌组（null 表示上家选择过）
     * @param context 游戏上下文（可为 null，使用默认上下文）
     * @return 能打过的牌组列表，如果接不住返回 null
     */
    public static List<Card> decidePlay(List<Card> aiHandCards, List<Card> previousCards, GameContext context) {
        // 空手牌检查
        if (aiHandCards == null || aiHandCards.isEmpty()) {
            return null;
        }

        // 如果没有上下文，创建默认上下文
        if (context == null) {
            context = createDefaultContext();
        }

        // 构建手牌结构
        HandStructure hs = analyzeHandStructure(aiHandCards);

        // 如果上家没有出牌，AI 可以出任意合法牌（首发出牌）
        if (previousCards == null || previousCards.isEmpty()) {
            return decideLeadPlay(hs, context);
        }

        // 获取上家牌型
        CardType previousType = GameRuleUtil.getCardType(previousCards);
        int previousMainWeight = GameRuleUtil.getMainWeight(previousCards);

        // 接牌策略
        return decideFollowPlay(hs, previousType, previousMainWeight, previousCards.size(), context);
    }

    /**
     * 创建默认游戏上下文（当无法获取真实上下文时使用）
     */
    private static GameContext createDefaultContext() {
        return new GameContext(ROLE_FARMER, 0, new int[]{ROLE_FARMER, ROLE_FARMER, ROLE_FARMER}, -1, 17);
    }

    // ============ 手牌结构化预处理 ============

    /**
     * 分析手牌结构
     * 将手牌分类为：炸弹、王炸、三条、对子、单牌、顺子等
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

        // 分离大小王
        for (Card card : handCards) {
            if (card.getRank() == Rank.SMALL_JOKER) {
                hs.smallJoker = card;
            } else if (card.getRank() == Rank.BIG_JOKER) {
                hs.bigJoker = card;
            }
        }

        // 检测王炸
        if (hs.smallJoker != null && hs.bigJoker != null) {
            hs.jokerBomb.add(hs.smallJoker);
            hs.jokerBomb.add(hs.bigJoker);
            rankGroups.remove(Rank.SMALL_JOKER.getWeight());
            rankGroups.remove(Rank.BIG_JOKER.getWeight());
        }

        // 遍历各牌值分组
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

        // 尝试从单牌中构建顺子
        hs.straights = findPossibleStraights(hs.singles);

        // 尝试从对子中构建连对
        hs.straightPairs = findPossibleStraightPairs(hs.pairs);

        // 检测三带一
        hs.trioWithSingle = findPossibleTrioWithSingle(hs.trios, hs.singles);

        // 检测三带一对
        hs.trioWithPair = findPossibleTrioWithPair(hs.trios, hs.pairs);

        // 按权重排序所有组
        sortHandStructure(hs);

        return hs;
    }

    private static void sortHandStructure(HandStructure hs) {
        Collections.sort(hs.singles, (c1, c2) -> Integer.compare(c2.getWeight(), c1.getWeight()));
        sortCardListList(hs.bombs);
        sortCardListList(hs.trios);
        sortCardListList(hs.pairs);
        sortCardListList(hs.straights);
        sortCardListList(hs.straightPairs);
        sortCardListList(hs.trioWithSingle);
        sortCardListList(hs.trioWithPair);
    }

    private static void sortCardListList(List<List<Card>> list) {
        Collections.sort(list, (l1, l2) -> {
            if (l1.isEmpty() || l2.isEmpty()) return 0;
            return Integer.compare(l2.get(0).getWeight(), l1.get(0).getWeight());
        });
    }

    // ============ 战术决策辅助方法 ============

    /**
     * 检查下一个出牌的座位是否为地主
     */
    private static boolean isNextSeatLandlord(GameContext context) {
        int nextSeat = (context.mySeatIndex + 1) % SEAT_COUNT;
        return context.seatRoles[nextSeat] == ROLE_LANDLORD;
    }

    /**
     * 检查下家是否为农民（用于放水战术）
     */
    private static boolean isNextSeatFarmer(GameContext context) {
        int nextSeat = (context.mySeatIndex + 1) % SEAT_COUNT;
        return context.seatRoles[nextSeat] == ROLE_FARMER;
    }

    /**
     * 检查是否需要极限单牌防守
     * 条件：AI是农民，地主只剩1张牌，且需要出单牌
     */
    private static boolean needsOneCardDefense(GameContext context) {
        return context.myRole == ROLE_FARMER
            && context.landlordRemainCards == 1;
    }

    /**
     * 获取最大的单牌（用于极限防守）
     */
    private static Card getLargestSingle(HandStructure hs) {
        if (hs.singles.isEmpty()) {
            return null;
        }
        // singles 已按权重降序排序
        return hs.singles.get(0);
    }

    /**
     * 获取能打过上家的最小单牌（考虑极限防守）
     */
    private static Card getBestSingleToBeat(HandStructure hs, int targetWeight, GameContext context) {
        // 极限防守优先：必须出最大的牌
        if (needsOneCardDefense(context)) {
            return getLargestSingle(hs);
        }

        // 正常情况：出最小的能管上的牌
        for (Card single : hs.singles) {
            if (single.getWeight() > targetWeight) {
                return single;
            }
        }
        return null;
    }

    // ============ 首发出牌策略（融合战术）============

    /**
     * 首发决策 - 融合角色战术
     */
    private static List<Card> decideLeadPlay(HandStructure hs, GameContext context) {
        // ========== 战术1：单牌报警极限防守（最高优先级）==========
        // 如果地主只剩1张牌，农民必须出最大单牌
        if (needsOneCardDefense(context)) {
            Card topSingle = getLargestSingle(hs);
            if (topSingle != null) {
                return new ArrayList<>(Collections.singletonList(topSingle));
            }
        }

        // ========== 战术2：顶牌战术（农民对地主）==========
        // 如果下家是地主，禁止出最小牌，必须出偏大的牌
        if (context.myRole == ROLE_FARMER && isNextSeatLandlord(context)) {
            Card blockingCard = getBlockingCard(hs);
            if (blockingCard != null) {
                return new ArrayList<>(Collections.singletonList(blockingCard));
            }
        }

        // ========== 战术3：放水战术（农民对农民）==========
        // 如果下家是农民，帮助队友跑牌，出最小牌
        if (context.myRole == ROLE_FARMER && isNextSeatFarmer(context)) {
            Card smallestCard = getSmallestCard(hs);
            if (smallestCard != null) {
                return new ArrayList<>(Collections.singletonList(smallestCard));
            }
        }

        // ========== 默认首发策略：出累赘牌 =========
        return decideLeadPlayDefault(hs);
    }

    /**
     * 获取顶牌（用于阻断地主）
     * 选择手中偏大的牌（J、Q、K、A等）进行阻断
     */
    private static Card getBlockingCard(HandStructure hs) {
        // 选择最小的能阻断地主的牌
        // 通常选择 Q（约权重12）以上的牌
        int minBlockingWeight = Rank.QUEEN.getWeight();

        for (Card card : hs.singles) {
            if (card.getWeight() >= minBlockingWeight) {
                return card;
            }
        }

        // 如果没有足够的顶牌，出最小的牌
        return getSmallestCard(hs);
    }

    /**
     * 获取最小的牌（用于放水）
     */
    private static Card getSmallestCard(HandStructure hs) {
        if (hs.singles.isEmpty()) {
            // 尝试拆对子
            if (!hs.pairs.isEmpty()) {
                return hs.pairs.get(hs.pairs.size() - 1).get(0);
            }
            return null;
        }
        // singles 已按降序排序，最后一个是最小的
        return hs.singles.get(hs.singles.size() - 1);
    }

    /**
     * 默认首发策略（累赘牌优先）
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

        if (!hs.trios.isEmpty()) {
            return new ArrayList<>(hs.trios.get(hs.trios.size() - 1));
        }

        // 4. 出顺子中最小的
        if (!hs.straights.isEmpty()) {
            List<Card> smallestStraight = hs.straights.get(hs.straights.size() - 1);
            return new ArrayList<>(smallestStraight);
        }

        // 5. 出连对
        if (!hs.straightPairs.isEmpty()) {
            return new ArrayList<>(hs.straightPairs.get(hs.straightPairs.size() - 1));
        }

        // 6. 出三带一对
        if (!hs.trioWithPair.isEmpty()) {
            return new ArrayList<>(hs.trioWithPair.get(hs.trioWithPair.size() - 1));
        }

        // 7. 最后出炸弹或王炸（迫不得已）
        if (!hs.bombs.isEmpty()) {
            return new ArrayList<>(hs.bombs.get(hs.bombs.size() - 1));
        }

        if (!hs.jokerBomb.isEmpty()) {
            return new ArrayList<>(hs.jokerBomb);
        }

        // 兜底：出任意一张牌
        if (!hs.originalHand.isEmpty()) {
            List<Card> sorted = new ArrayList<>(hs.originalHand);
            Collections.sort(sorted, (c1, c2) -> Integer.compare(c1.getWeight(), c2.getWeight()));
            return new ArrayList<>(Collections.singletonList(sorted.get(0)));
        }

        return null;
    }

    // ============ 接牌策略（融合战术）============

    /**
     * 接牌决策 - 融合角色战术
     */
    private static List<Card> decideFollowPlay(HandStructure hs, CardType previousType,
                                               int previousMainWeight, int previousCount,
                                               GameContext context) {
        // 特殊牌型处理
        if (previousType == CardType.JOKER_BOMB) {
            return null; // 上家王炸，无法接
        }

        if (previousType == CardType.BOMB) {
            return handleBombFollow(hs, previousMainWeight);
        }

        // ========== 战术3：单牌报警极限防守（最高优先级）==========
        // 如果地主只剩1张牌，且上家出的是单牌，农民必须出最大单牌
        if (needsOneCardDefense(context) && previousType == CardType.SINGLE) {
            Card topSingle = getLargestSingle(hs);
            if (topSingle != null && topSingle.getWeight() > previousMainWeight) {
                return new ArrayList<>(Collections.singletonList(topSingle));
            }
            // 如果打不过，返回null（选择不出）
            return null;
        }

        // 根据上家牌型处理
        switch (previousType) {
            case SINGLE:
                return handleSingleFollow(hs, previousMainWeight, context);
            case PAIR:
                return handlePairFollow(hs, previousMainWeight);
            case TRIO:
                return handleTrioFollow(hs, previousMainWeight);
            case TRIO_SINGLE:
                return handleTrioSingleFollow(hs, previousMainWeight);
            case TRIO_PAIR:
                return handleTrioPairFollow(hs, previousMainWeight);
            case STRAIGHT:
                return handleStraightFollow(hs, previousMainWeight, previousCount);
            case STRAIGHT_PAIRS:
                return handleStraightPairsFollow(hs, previousMainWeight, previousCount / 2);
            default:
                return null;
        }
    }

    /**
     * 处理炸弹接牌
     */
    private static List<Card> handleBombFollow(HandStructure hs, int previousWeight) {
        // 检查王炸
        if (!hs.jokerBomb.isEmpty()) {
            return new ArrayList<>(hs.jokerBomb);
        }

        // 找更大的炸弹
        for (List<Card> bomb : hs.bombs) {
            if (bomb.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(bomb);
            }
        }

        return null;
    }

    /**
     * 处理单牌接牌 - 融入战术
     */
    private static List<Card> handleSingleFollow(HandStructure hs, int previousWeight, GameContext context) {
        // ========== 战术3：极限防守 ==========
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

        // 尝试拆对子
        for (List<Card> pair : hs.pairs) {
            int weight = pair.get(0).getWeight();
            if (weight > previousWeight && !isHighValuePair(weight)) {
                List<Card> result = new ArrayList<>();
                result.add(pair.get(0));
                return result;
            }
        }

        // 尝试拆三条
        for (List<Card> trio : hs.trios) {
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
     * 处理对子接牌
     */
    private static List<Card> handlePairFollow(HandStructure hs, int previousWeight) {
        // 从对子中找更大的
        for (List<Card> pair : hs.pairs) {
            if (pair.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(pair);
            }
        }
        return null;
    }

    /**
     * 处理三条接牌
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
     * 处理三带一接牌
     */
    private static List<Card> handleTrioSingleFollow(HandStructure hs, int previousWeight) {
        for (List<Card> combo : hs.trioWithSingle) {
            if (combo.get(0).getWeight() > previousWeight) {
                return new ArrayList<>(combo);
            }
        }

        // 尝试用三条+单牌组合
        for (List<Card> trio : hs.trios) {
            if (trio.get(0).getWeight() > previousWeight && !hs.singles.isEmpty()) {
                List<Card> result = new ArrayList<>(trio);
                for (Card single : hs.singles) {
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
     * 处理三带一对接牌
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
     * 处理顺子接牌
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
     * 处理连对接牌
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
     * 判断对子是否为高价值（不轻易拆）
     */
    private static boolean isHighValuePair(int weight) {
        if (weight >= Rank.ACE.getWeight()) return true;
        if (weight == Rank.TWO.getWeight()) return true;
        return false;
    }

    private static boolean isJokerWeight(int weight) {
        return weight == Rank.SMALL_JOKER.getWeight() || weight == Rank.BIG_JOKER.getWeight();
    }

    private static boolean isTwoWeight(int weight) {
        return weight == Rank.TWO.getWeight();
    }

    private static List<List<Card>> findPossibleStraights(List<Card> singles) {
        List<List<Card>> straights = new ArrayList<>();
        if (singles.size() < 5) return straights;

        List<Card> validSingles = new ArrayList<>();
        for (Card card : singles) {
            if (!card.getRank().isJoker() && !card.getRank().isTwo()) {
                validSingles.add(card);
            }
        }

        if (validSingles.size() < 5) return straights;

        Collections.sort(validSingles, (c1, c2) -> Integer.compare(c1.getWeight(), c2.getWeight()));

        for (int start = 0; start <= validSingles.size() - 5; start++) {
            List<Card> straight = new ArrayList<>();
            straight.add(validSingles.get(start));

            for (int i = start + 1; i < validSingles.size(); i++) {
                Card current = validSingles.get(i);
                Card last = straight.get(straight.size() - 1);

                if (current.getWeight() == last.getWeight() + 1) {
                    straight.add(current);
                    if (straight.size() >= 5) {
                        List<Card> newStraight = new ArrayList<>(straight);
                        straights.add(newStraight);
                    }
                } else if (current.getWeight() > last.getWeight() + 1) {
                    break;
                }
            }
        }

        return straights;
    }

    private static List<List<Card>> findPossibleStraightPairs(List<List<Card>> pairs) {
        List<List<Card>> straightPairs = new ArrayList<>();
        if (pairs.size() < 3) return straightPairs;

        List<Integer> validWeights = new ArrayList<>();
        for (List<Card> pair : pairs) {
            int weight = pair.get(0).getWeight();
            if (!isJokerWeight(weight) && !isTwoWeight(weight)) {
                validWeights.add(weight);
            }
        }

        if (validWeights.size() < 3) return straightPairs;

        Collections.sort(validWeights);

        for (int start = 0; start <= validWeights.size() - 3; start++) {
            List<Integer> straightWeights = new ArrayList<>();
            straightWeights.add(validWeights.get(start));

            for (int i = start + 1; i < validWeights.size(); i++) {
                int currentWeight = validWeights.get(i);
                int lastWeight = straightWeights.get(straightWeights.size() - 1);

                if (currentWeight == lastWeight + 1) {
                    straightWeights.add(currentWeight);
                    if (straightWeights.size() >= 3) {
                        List<Card> straightPair = new ArrayList<>();
                        for (int w : straightWeights) {
                            for (List<Card> pair : pairs) {
                                if (!pair.isEmpty() && pair.get(0).getWeight() == w) {
                                    straightPair.addAll(pair);
                                    break;
                                }
                            }
                        }
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

    private static List<List<Card>> findPossibleTrioWithSingle(List<List<Card>> trios, List<Card> singles) {
        List<List<Card>> result = new ArrayList<>();

        for (List<Card> trio : trios) {
            if (singles.isEmpty()) break;
            int trioWeight = trio.get(0).getWeight();

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

    private static List<List<Card>> findPossibleTrioWithPair(List<List<Card>> trios, List<List<Card>> pairs) {
        List<List<Card>> result = new ArrayList<>();

        for (List<Card> trio : trios) {
            if (pairs.isEmpty()) break;
            int trioWeight = trio.get(0).getWeight();

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

    public static long getRandomThinkingDelay() {
        return THINKING_DELAY_MIN + (long) (Math.random() * (THINKING_DELAY_MAX - THINKING_DELAY_MIN));
    }
}
