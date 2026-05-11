package com.gamecenter.app.games.doudizhu.utils;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.CardType;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.model.Suit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 斗地主核心规则引擎 (Game Rule Utility)
 * 负责洗牌发牌、牌型判定、大小比较等核心游戏逻辑
 * 所有方法均为静态方法，属于工具类
 */
public class GameRuleUtil {

    // 斗地主总牌数
    public static final int TOTAL_CARDS = 54;
    // 每个玩家应得的手牌数
    public static final int CARDS_PER_PLAYER = 17;
    // 底牌数量
    public static final int BOTTOM_CARDS_COUNT = 3;

    // 顺子所需的最小牌数（5张）
    private static final int MIN_STRAIGHT_LENGTH = 5;
    // 连对所需的最小对数（3对）
    private static final int MIN_STRAIGHT_PAIRS_COUNT = 3;
    // 飞机所需的最小三张数量（2个）
    private static final int MIN_AIRPLANE_TRIO_COUNT = 2;

    // 顺子允许的最大牌值索引（A的索引）
    private static final int MAX_STRAIGHT_VALUE_INDEX = 11;
    // 顺子允许的最小牌值索引（3的索引）
    private static final int MIN_VALID_RANK_INDEX = 0;

    /**
     * 洗牌并发牌 (Shuffle and Deal)
     * 初始化一副54张牌，随机打乱后均分成3份（每份17张），保留3张底牌
     * @return 包含4个List的数组：[玩家1手牌, 玩家2手牌, 玩家3手牌, 底牌]
     */
    public static List<Card>[] shuffleAndDeal() {
        // 创建一副完整的54张牌
        List<Card> deck = new ArrayList<>(Arrays.asList(Card.createFullDeck()));

        // 使用 Collections.shuffle 进行随机洗牌
        Collections.shuffle(deck);

        // 创建返回结果数组
        @SuppressWarnings("unchecked")
        List<Card>[] result = new ArrayList[4];

        // 分配手牌：每人17张
        for (int i = 0; i < 3; i++) {
            result[i] = new ArrayList<>(deck.subList(i * CARDS_PER_PLAYER, (i + 1) * CARDS_PER_PLAYER));
            // 对手牌按照权重从大到小排序（斗地主习惯：大手牌在前）
            sortCardsByWeightAscending(result[i]);
        }

        result[3] = new ArrayList<>(deck.subList(3 * CARDS_PER_PLAYER, TOTAL_CARDS));
        sortCardsByWeightAscending(result[3]);

        return result;
    }

    /**
     * 按照权重从3到大王对手牌进行升序排序（3在左→大王在右）
     * 斗地主的排序规则：先按权重降序，权重相同则按花色排序
     * @param cards 待排序的手牌列表
     */
    public static void sortCardsByWeightAscending(List<Card> cards) {
        if (cards == null || cards.size() <= 1) {
            return;
        }
        Collections.sort(cards, new Comparator<Card>() {
            @Override
            public int compare(Card c1, Card c2) {
                // 升序：3最小在左，大王最大在右
                int weightCompare = Integer.compare(c1.getWeight(), c2.getWeight());
                if (weightCompare != 0) {
                    return weightCompare;
                }
                if (!c1.getRank().isJoker() && !c2.getRank().isJoker()) {
                    return c1.getSuit().ordinal() - c2.getSuit().ordinal();
                }
                return 0;
            }
        });
    }

    /**
     * 获取卡牌的牌型 (Get Card Type)
     * 通过分析传入的牌组，精准判断其对应的牌型
     * @param cards 牌组列表，不能为 null 或空
     * @return 对应的 CardType 枚举值
     */
    public static CardType getCardType(List<Card> cards) {
        // 空牌组或 null 视为错误牌型
        if (cards == null || cards.isEmpty()) {
            return CardType.ERROR;
        }

        int cardCount = cards.size();

        // 王炸：只有2张牌，且为大王+小王
        if (cardCount == 2 && isJokerBomb(cards)) {
            return CardType.JOKER_BOMB;
        }

        // 单牌：只有1张牌
        if (cardCount == 1) {
            return CardType.SINGLE;
        }

        // 分析牌组的牌值分布
        Map<Integer, Integer> rankCountMap = analyzeRankCounts(cards);

        // 根据牌数判断牌型
        switch (cardCount) {
            case 2:
                return analyzeTwoCards(rankCountMap);
            case 3:
                return analyzeThreeCards(rankCountMap);
            case 4:
                return analyzeFourCards(rankCountMap, cards);
            case 5:
                return analyzeFiveCards(rankCountMap, cards);
            default:
                return analyzeMultipleCards(cardCount, rankCountMap, cards);
        }
    }

    /**
     * 判断是否为王炸（大王+小王）
     */
    private static boolean isJokerBomb(List<Card> cards) {
        if (cards.size() != 2) return false;
        boolean hasSmallJoker = false;
        boolean hasBigJoker = false;
        for (Card card : cards) {
            if (card.getRank() == Rank.SMALL_JOKER) hasSmallJoker = true;
            if (card.getRank() == Rank.BIG_JOKER) hasBigJoker = true;
        }
        return hasSmallJoker && hasBigJoker;
    }

    /**
     * 分析牌组中各牌值的出现次数
     * @return Map<权重, 出现次数>
     */
    private static Map<Integer, Integer> analyzeRankCounts(List<Card> cards) {
        Map<Integer, Integer> rankCountMap = new HashMap<>();
        for (Card card : cards) {
            int weight = card.getWeight();
            rankCountMap.put(weight, rankCountMap.getOrDefault(weight, 0) + 1);
        }
        return rankCountMap;
    }

    /**
     * 分析两张牌的牌型
     */
    private static CardType analyzeTwoCards(Map<Integer, Integer> rankCountMap) {
        if (rankCountMap.size() == 1) {
            // 两张相同牌值 = 对子
            return CardType.PAIR;
        }
        return CardType.ERROR;
    }

    /**
     * 分析三张牌的牌型
     */
    private static CardType analyzeThreeCards(Map<Integer, Integer> rankCountMap) {
        if (rankCountMap.size() == 1) {
            // 三张相同牌值 = 三张
            return CardType.TRIO;
        }
        return CardType.ERROR;
    }

    /**
     * 分析四张牌的牌型
     */
    private static CardType analyzeFourCards(Map<Integer, Integer> rankCountMap, List<Card> cards) {
        if (rankCountMap.size() == 1) {
            // 四张相同牌值 = 炸弹
            return CardType.BOMB;
        } else if (rankCountMap.size() == 2) {
            boolean hasTrio = false;
            boolean hasSingle = false;
            for (int count : rankCountMap.values()) {
                if (count == 3) hasTrio = true;
                if (count == 1) hasSingle = true;
            }
            if (hasTrio && hasSingle) {
                return CardType.TRIO_SINGLE;
            }
        }
        return CardType.ERROR;
    }

    /**
     * 分析四带牌型（四带两单或四带两对）
     */
    private static CardType analyzeQuadCards(Map<Integer, Integer> rankCountMap) {
        int threeCount = 0;
        int twoCount = 0;
        int oneCount = 0;

        for (int count : rankCountMap.values()) {
            if (count == 3) threeCount++;
            else if (count == 2) twoCount++;
            else if (count == 1) oneCount++;
        }

        if (threeCount == 1 && oneCount == 1) {
            return CardType.QUAD_SINGLE; // 四带两单
        } else if (twoCount == 2) {
            return CardType.QUAD_PAIR; // 四带两对
        }

        return CardType.BOMB; // 默认当作炸弹
    }

    /**
     * 分析五张牌的牌型
     */
    private static CardType analyzeFiveCards(Map<Integer, Integer> rankCountMap, List<Card> cards) {
        if (rankCountMap.size() == 1) {
            // 只有一种牌值，不可能是顺子（五张单牌需要不同牌值）
            return CardType.ERROR;
        }

        // 检查是否为三带二（三带一对）
        if (rankCountMap.size() == 2) {
            boolean hasTrio = false;
            boolean hasPair = false;
            for (int count : rankCountMap.values()) {
                if (count == 3) hasTrio = true;
                if (count == 2) hasPair = true;
            }
            if (hasTrio && hasPair) {
                return CardType.TRIO_PAIR;
            }
        }

        // 检查是否为顺子
        if (isStraight(cards)) {
            return CardType.STRAIGHT;
        }

        return CardType.ERROR;
    }

    /**
     * 分析多张牌（5张以上）的牌型
     */
    private static CardType analyzeMultipleCards(int cardCount, Map<Integer, Integer> rankCountMap, List<Card> cards) {
        // 获取所有不重复的牌值
        List<Integer> uniqueWeights = new ArrayList<>(rankCountMap.keySet());
        Collections.sort(uniqueWeights);

        // 检查是否为顺子（5张或更多连续单牌）
        if (isStraight(cards) && cardCount >= MIN_STRAIGHT_LENGTH) {
            return CardType.STRAIGHT;
        }

        // 检查是否为连对
        if (isStraightPairs(uniqueWeights, rankCountMap)) {
            return CardType.STRAIGHT_PAIRS;
        }

        // 检查是否为飞机或飞机带翅膀
        CardType airplaneResult = analyzeAirplane(cardCount, rankCountMap, cards);
        if (airplaneResult != CardType.ERROR) {
            return airplaneResult;
        }

        return CardType.ERROR;
    }

    /**
     * 判断牌组是否为顺子（5张或更多连续单牌）
     * 顺子的规则：所有牌都是单张，且牌值连续（3到A，不含2和王）
     */
    private static boolean isStraight(List<Card> cards) {
        if (cards.size() < MIN_STRAIGHT_LENGTH) {
            return false;
        }

        // 提取所有权重值并排序
        List<Integer> weights = new ArrayList<>();
        for (Card card : cards) {
            // 排除大小王和2，它们不能出现在顺子中
            if (card.getRank().isJoker() || card.getRank().isTwo()) {
                return false;
            }
            weights.add(card.getWeight());
        }

        // 检查是否所有牌都是单张（无重复牌值）
        if (weights.size() != cards.size()) {
            return false;
        }

        // 排序并检查是否连续
        Collections.sort(weights);
        int minWeight = weights.get(0);

        // 顺子最小必须是3，权重为3
        if (minWeight < Rank.THREE.getWeight() || minWeight > Rank.ACE.getWeight() - cards.size() + 1) {
            return false;
        }

        // 检查相邻权重差是否为1
        for (int i = 1; i < weights.size(); i++) {
            if (weights.get(i) - weights.get(i - 1) != 1) {
                return false;
            }
        }

        return true;
    }

    /**
     * 判断是否为连对（3对或更多连续对子）
     */
    private static boolean isStraightPairs(List<Integer> uniqueWeights, Map<Integer, Integer> rankCountMap) {
        // 连对至少需要3对
        if (uniqueWeights.size() < MIN_STRAIGHT_PAIRS_COUNT) {
            return false;
        }

        // 检查每个牌值是否都是对子
        for (int weight : uniqueWeights) {
            if (rankCountMap.get(weight) != 2) {
                return false;
            }
        }

        // 检查是否连续
        Collections.sort(uniqueWeights);
        for (int i = 1; i < uniqueWeights.size(); i++) {
            if (uniqueWeights.get(i) - uniqueWeights.get(i - 1) != 1) {
                return false;
            }
        }

        // 检查最大牌值不能超过A（权重14）
        int maxWeight = uniqueWeights.get(uniqueWeights.size() - 1);
        if (maxWeight > Rank.ACE.getWeight()) {
            return false;
        }

        // 检查最小牌值不能太小（不能包含2或王牌）
        int minWeight = uniqueWeights.get(0);
        if (minWeight < Rank.THREE.getWeight() || minWeight > Rank.ACE.getWeight() - uniqueWeights.size() + 1) {
            return false;
        }

        return true;
    }

    /**
     * 分析飞机相关牌型
     * 飞机：两个或更多连续三张
     * 飞机带翅膀：飞机 + 相应的单牌或对子
     */
    private static CardType analyzeAirplane(int cardCount, Map<Integer, Integer> rankCountMap, List<Card> cards) {
        // 统计三张的数量
        List<Integer> trioWeights = new ArrayList<>();
        List<Integer> pairWeights = new ArrayList<>();
        List<Integer> singleWeights = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : rankCountMap.entrySet()) {
            int weight = entry.getKey();
            int count = entry.getValue();
            if (count == 3) {
                trioWeights.add(weight);
            } else if (count == 2) {
                pairWeights.add(weight);
            } else if (count == 1) {
                singleWeights.add(weight);
            }
        }

        // 如果没有三张，不是飞机
        if (trioWeights.isEmpty()) {
            return CardType.ERROR;
        }

        // 检查三张是否连续
        Collections.sort(trioWeights);
        boolean isContinuous = true;
        for (int i = 1; i < trioWeights.size(); i++) {
            if (trioWeights.get(i) - trioWeights.get(i - 1) != 1) {
                isContinuous = false;
                break;
            }
        }

        // 检查三张序列是否合法（不能含2和王）
        for (int weight : trioWeights) {
            if (weight >= Rank.TWO.getWeight() || Rank.fromSymbol(String.valueOf(weight)) == null) {
                if (weight == Rank.TWO.getWeight()) {
                    return CardType.ERROR; // 2不能出现在飞机中
                }
            }
        }

        // 如果三张不连续，可能整体牌型不是飞机
        if (!isContinuous && trioWeights.size() >= MIN_AIRPLANE_TRIO_COUNT) {
            // 验证三张序列的连续性
            int minTrioWeight = trioWeights.get(0);
            int maxTrioWeight = trioWeights.get(trioWeights.size() - 1);
            if (maxTrioWeight - minTrioWeight != trioWeights.size() - 1) {
                isContinuous = false;
            }
        }

        if (isContinuous && trioWeights.size() >= MIN_AIRPLANE_TRIO_COUNT) {
            // 这是一个飞机
            int trioCount = trioWeights.size();

            // 检查是否为飞机带翅膀
            if (singleWeights.size() == trioCount) {
                // 飞机带单牌
                return CardType.AIRPLANE_WITH_WINGS;
            } else if (pairWeights.size() == trioCount) {
                // 飞机带对子
                return CardType.AIRPLANE_WITH_WINGS;
            } else if (singleWeights.isEmpty() && pairWeights.isEmpty()) {
                // 纯飞机（无翅膀）
                return CardType.AIRPLANE;
            }
        }

        // 如果只有单个三张，检查是否为三带一或三带一对
        if (trioWeights.size() == 1) {
            int singleCount = singleWeights.size();
            int pairCount = pairWeights.size();
            if (singleCount == 1) {
                return CardType.TRIO_SINGLE;
            } else if (pairCount == 1) {
                return CardType.TRIO_PAIR;
            }
        }

        return CardType.ERROR;
    }

    /**
     * 判断当前玩家是否可以打过上家打出的牌
     * 规则：
     * 1. 必须出相同牌型（炸弹和王炸除外）
     * 2. 主牌权重必须更大
     * 3. 炸弹可以打任意非炸弹牌型
     * 4. 王炸可以打任意牌型
     *
     * @param currentCards 当前玩家要出的牌组
     * @param previousCards 上家打出的牌组（null表示上家选择过）
     * @return 是否可以打出
     */
    public static boolean canPlayPass(List<Card> currentCards, List<Card> previousCards) {
        // 如果上家没有出牌（选择过），当前玩家可以出任意合法牌型
        if (previousCards == null || previousCards.isEmpty()) {
            return getCardType(currentCards) != CardType.ERROR;
        }

        // 获取当前牌组和上家牌组的类型
        CardType currentType = getCardType(currentCards);
        CardType previousType = getCardType(previousCards);

        // 如果当前牌型无效，不能出
        if (currentType == CardType.ERROR) {
            return false;
        }

        // 王炸可以打任意牌
        if (currentType == CardType.JOKER_BOMB) {
            return true;
        }

        // 炸弹可以打任意非炸弹牌型
        if (currentType == CardType.BOMB && !previousType.isBomb()) {
            return true;
        }

        // 王炸可以打炸弹
        if (currentType == CardType.JOKER_BOMB && previousType == CardType.BOMB) {
            return true;
        }

        // 炸弹之间比较
        if (currentType == CardType.BOMB && previousType == CardType.BOMB) {
            return getMainWeight(currentCards) > getMainWeight(previousCards);
        }

        // 必须为相同牌型才能比较
        if (currentType != previousType) {
            return false;
        }

        // 比较主牌权重
        return getMainWeight(currentCards) > getMainWeight(previousCards);
    }

    /**
     * 获取牌组的主权重
     * 主权重用于比较同牌型的大小
     * 对于多数牌型，主权重是牌值的权重
     * 对于带牌牌型，主权重是核心牌（三张/四张等）的权重
     *
     * @param cards 牌组
     * @return 主权重值
     */
    public static int getMainWeight(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }

        CardType type = getCardType(cards);
        Map<Integer, Integer> rankCountMap = analyzeRankCounts(cards);

        switch (type) {
            case SINGLE:
            case PAIR:
            case TRIO:
            case STRAIGHT:
            case STRAIGHT_PAIRS:
            case BOMB:
                // 这些牌型直接返回唯一牌值的权重
                return cards.get(0).getWeight();

            case TRIO_SINGLE:
            case TRIO_PAIR:
            case QUAD_SINGLE:
            case QUAD_PAIR:
            case AIRPLANE:
            case AIRPLANE_WITH_WINGS:
                // 找到数量为3或4的牌值的权重
                for (Map.Entry<Integer, Integer> entry : rankCountMap.entrySet()) {
                    if (entry.getValue() == 3 || entry.getValue() == 4) {
                        return entry.getKey();
                    }
                }
                break;

            case JOKER_BOMB:
                // 王炸返回最大权重
                return Rank.BIG_JOKER.getWeight();

            default:
                break;
        }

        // 默认返回最大牌的权重
        int maxWeight = 0;
        for (Card card : cards) {
            if (card.getWeight() > maxWeight) {
                maxWeight = card.getWeight();
            }
        }
        return maxWeight;
    }

    /**
     * 从手牌中找出所有能打过上家牌型的牌组
     * 用于 AI 决策和提示功能
     *
     * @param handCards 玩家手牌
     * @param previousCards 上家打出的牌组（null表示上家过）
     * @return 能打过的所有牌组列表
     */
    public static List<List<Card>> findPlayableCombos(List<Card> handCards, List<Card> previousCards) {
        List<List<Card>> result = new ArrayList<>();

        if (handCards == null || handCards.isEmpty()) {
            return result;
        }

        // 如果上家没有出牌，返回所有合法牌型
        if (previousCards == null || previousCards.isEmpty()) {
            // 返回所有单牌作为最基本的选择
            for (Card card : handCards) {
                List<Card> combo = new ArrayList<>();
                combo.add(card);
                result.add(combo);
            }
            return result;
        }

        CardType previousType = getCardType(previousCards);
        int previousMainWeight = getMainWeight(previousCards);

        // 分析手牌中各牌值的分布
        Map<Integer, Integer> handRankCounts = analyzeRankCounts(handCards);
        List<Card> sortedHand = new ArrayList<>(handCards);
        Collections.sort(sortedHand, (c1, c2) -> Integer.compare(c2.getWeight(), c1.getWeight()));

        // 检查是否能出炸弹或王炸
        for (Card card : sortedHand) {
            List<Card> bombCombo = findBombWithWeight(handCards, handRankCounts, card.getWeight());
            if (bombCombo != null && canPlayPass(bombCombo, previousCards)) {
                result.add(bombCombo);
            }
        }

        // 检查王炸
        if (handRankCounts.containsKey(Rank.SMALL_JOKER.getWeight()) &&
            handRankCounts.containsKey(Rank.BIG_JOKER.getWeight())) {
            List<Card> jokerBomb = new ArrayList<>();
            for (Card card : handCards) {
                if (card.getRank() == Rank.SMALL_JOKER || card.getRank() == Rank.BIG_JOKER) {
                    jokerBomb.add(card);
                }
            }
            if (canPlayPass(jokerBomb, previousCards)) {
                result.add(jokerBomb);
            }
        }

        // 根据上家牌型查找能打过的组合
        switch (previousType) {
            case SINGLE:
                for (Card card : sortedHand) {
                    if (card.getWeight() > previousMainWeight) {
                        List<Card> combo = new ArrayList<>();
                        combo.add(card);
                        result.add(combo);
                    }
                }
                break;

            case PAIR:
                for (Card card : sortedHand) {
                    List<Card> pairCombo = findSameRankCards(handCards, card.getWeight(), 2);
                    if (pairCombo != null && canPlayPass(pairCombo, previousCards)) {
                        result.add(pairCombo);
                    }
                }
                break;

            case TRIO:
            case TRIO_SINGLE:
            case TRIO_PAIR:
                for (Card card : sortedHand) {
                    List<Card> trioCombo = findSameRankCards(handCards, card.getWeight(), 3);
                    if (trioCombo != null) {
                        // 找到三张后，尝试带牌
                        if (previousType == CardType.TRIO) {
                            result.add(trioCombo);
                        } else if (previousType == CardType.TRIO_SINGLE) {
                            // 三带一：找到最小的单牌（不能和三张同权重）
                            Card smallestSingle = findSmallestSingle(handCards, card.getWeight());
                            if (smallestSingle != null) {
                                trioCombo.add(smallestSingle);
                                result.add(trioCombo);
                            }
                        } else if (previousType == CardType.TRIO_PAIR) {
                            // 三带一对
                            List<Card> smallestPair = findSmallestPair(handCards, card.getWeight());
                            if (smallestPair != null) {
                                trioCombo.addAll(smallestPair);
                                result.add(trioCombo);
                            }
                        }
                    }
                }
                break;

            case STRAIGHT:
                // 顺子较复杂，这里简化处理
                break;

            case BOMB:
                // 炸弹需要更大的炸弹
                for (Map.Entry<Integer, Integer> entry : handRankCounts.entrySet()) {
                    if (entry.getValue() == 4 && entry.getKey() > previousMainWeight) {
                        List<Card> bombCombo = findSameRankCards(handCards, entry.getKey(), 4);
                        if (bombCombo != null) {
                            result.add(bombCombo);
                        }
                    }
                }
                break;

            default:
                break;
        }

        return result;
    }

    /**
     * 查找指定权重的牌
     */
    private static List<Card> findSameRankCards(List<Card> handCards, int weight, int count) {
        List<Card> result = new ArrayList<>();
        for (Card card : handCards) {
            if (card.getWeight() == weight) {
                result.add(card);
                if (result.size() == count) {
                    return result;
                }
            }
        }
        return result.size() == count ? result : null;
    }

    /**
     * 查找能组成炸弹的牌
     */
    private static List<Card> findBombWithWeight(List<Card> handCards, Map<Integer, Integer> rankCounts, int weight) {
        if (rankCounts.getOrDefault(weight, 0) == 4) {
            return findSameRankCards(handCards, weight, 4);
        }
        return null;
    }

    /**
     * 从手牌中找到最小的单牌（排除指定权重）
     */
    private static Card findSmallestSingle(List<Card> handCards, int excludeWeight) {
        Card smallest = null;
        for (Card card : handCards) {
            if (card.getWeight() != excludeWeight) {
                if (smallest == null || card.getWeight() < smallest.getWeight()) {
                    smallest = card;
                }
            }
        }
        return smallest;
    }

    /**
     * 从手牌中找到最小的一对
     */
    private static List<Card> findSmallestPair(List<Card> handCards, int excludeWeight) {
        Map<Integer, List<Card>> pairs = new HashMap<>();
        for (Card card : handCards) {
            if (card.getWeight() != excludeWeight) {
                int weight = card.getWeight();
                if (!pairs.containsKey(weight)) {
                    pairs.put(weight, new ArrayList<>());
                }
                pairs.get(weight).add(card);
            }
        }

        for (Map.Entry<Integer, List<Card>> entry : pairs.entrySet()) {
            if (entry.getValue().size() >= 2) {
                return entry.getValue().subList(0, 2);
            }
        }
        return null;
    }

    /**
     * 辅助方法：从多个列表中查找指定权重的牌
     */
    private static List<Card> findSameRankCardsFromMultipleLists(List<Card> handCards, int weight, int count) {
        if (handCards == null) return null;
        return findSameRankCards(handCards, weight, count);
    }

    /**
     * 计算炸弹的倍数（用于计分）
     * @param cards 牌组
     * @return 炸弹的倍数，非法牌型返回0
     */
    public static int getBombMultiplier(List<Card> cards) {
        if (getCardType(cards) == CardType.BOMB) {
            return 2;
        } else if (getCardType(cards) == CardType.JOKER_BOMB) {
            return 4;
        }
        return 1;
    }

    /**
     * 验证牌组是否完整且有效
     * @param cards 待验证的牌组
     * @return 是否有效
     */
    public static boolean isValidCardList(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        for (Card card : cards) {
            if (card == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查是否包含大王
     */
    public static boolean hasBigJoker(List<Card> cards) {
        if (cards == null) return false;
        for (Card card : cards) {
            if (card.getRank() == Rank.BIG_JOKER) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否包含小王
     */
    public static boolean hasSmallJoker(List<Card> cards) {
        if (cards == null) return false;
        for (Card card : cards) {
            if (card.getRank() == Rank.SMALL_JOKER) {
                return true;
            }
        }
        return false;
    }
}
