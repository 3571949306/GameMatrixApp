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
 * 斗地主核心规则引擎，封装了游戏的所有规则判定逻辑。
 * <p>
 * 你可以把这个类想象成"规则百科全书"——它知道斗地主的所有规则：
 * 什么是顺子、什么是炸弹、谁出的牌更大、能出什么牌等等。
 * 它不记录游戏进度，只负责回答规则问题。
 * <p>
 * 职责：
 * <ul>
 *   <li>洗牌与发牌：创建一副牌、随机洗牌、分配手牌和底牌</li>
 *   <li>牌型判定：根据出牌组合判断其所属的合法牌型（单牌、对子、顺子、炸弹等）</li>
 *   <li>出牌合法性校验：判断当前出牌是否能打过上家出牌</li>
 *   <li>出牌提示：从手牌中找出所有能打过上家的牌组组合</li>
 *   <li>辅助工具方法：炸弹倍数计算、牌组有效性验证等</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>所有方法均为静态方法，无需实例化，属于纯工具类设计
 *       （就像查字典，不需要先创建一个字典对象）</li>
 *   <li>牌型判定采用"牌数→分布分析→具体判定"的三层策略，
 *       先按牌数分流，再分析各牌值出现次数，最后精确判定牌型
 *       （就像医生看病：先看症状数量，再分析各项指标，最后确诊）</li>
 *   <li>使用权重(weight)体系进行大小比较，而非直接比较牌值枚举，
 *       权重由 Rank 枚举定义，3最小(3)→大王最大(17)</li>
 *   <li>主权重(getMainWeight)概念：对于带牌牌型（如三带一），
 *       只比较核心牌（三张部分）的权重，附带牌不参与比较
 *       （就像比谁力气大，只比核心力量，不算附加装备）</li>
 *   <li>findPlayableCombos 方法用于 AI 决策和提示功能，
 *       当前实现为简化版本，顺子等复杂牌型的搜索待完善</li>
 * </ul>
 */
public class GameRuleUtil {

    /**
     * 斗地主总牌数：54张（4花色×13张 + 大小王）
     */
    public static final int TOTAL_CARDS = 54;

    /**
     * 每个玩家应得的手牌数：17张（54 - 3张底牌）/ 3人
     */
    public static final int CARDS_PER_PLAYER = 17;

    /**
     * 底牌数量：3张，由地主获得
     */
    public static final int BOTTOM_CARDS_COUNT = 3;

    /**
     * 顺子所需的最小牌数：5张。
     * 斗地主规则中，4张连续牌不算顺子。
     */
    private static final int MIN_STRAIGHT_LENGTH = 5;

    /**
     * 连对所需的最小对数：3对（6张牌）。
     * 斗地主规则中，2对连续对子不算连对。
     */
    private static final int MIN_STRAIGHT_PAIRS_COUNT = 3;

    /**
     * 飞机所需的最小三张组数：2组。
     * 单独一组三张不算飞机，只是三张或三带一/三带一对。
     */
    private static final int MIN_AIRPLANE_TRIO_COUNT = 2;

    /**
     * 顺子允许的最大牌值索引：对应 A 的权重(14)在 Rank 枚举中的位置(11)。
     * 顺子不能超过 A，即不能包含2和王。
     */
    private static final int MAX_STRAIGHT_VALUE_INDEX = 11;

    /**
     * 顺子允许的最小牌值索引：对应 3 的权重(3)在 Rank 枚举中的位置(0)。
     */
    private static final int MIN_VALID_RANK_INDEX = 0;

    /**
     * 洗牌并发牌。
     * <p>
     * 流程：
     * <ol>
     *   <li>创建一副完整的54张牌</li>
     *   <li>使用 Collections.shuffle 随机打乱顺序</li>
     *   <li>按顺序分配：前17张给玩家1，接下来17张给玩家2，再17张给玩家3，最后3张为底牌</li>
     *   <li>对每份手牌按权重升序排序（3在左→大王在右）</li>
     * </ol>
     *
     * @return 包含4个List的数组：[0]玩家1手牌、[1]玩家2手牌、[2]玩家3手牌、[3]底牌；
     *         每个列表中的牌已按权重升序排列
     */
    public static List<Card>[] shuffleAndDeal() {
        List<Card> deck = new ArrayList<>(Arrays.asList(Card.createFullDeck()));

        Collections.shuffle(deck);

        @SuppressWarnings("unchecked")
        List<Card>[] result = new ArrayList[4];

        for (int i = 0; i < 3; i++) {
            result[i] = new ArrayList<>(deck.subList(i * CARDS_PER_PLAYER, (i + 1) * CARDS_PER_PLAYER));
            sortCardsByWeightAscending(result[i]);
        }

        result[3] = new ArrayList<>(deck.subList(3 * CARDS_PER_PLAYER, TOTAL_CARDS));
        sortCardsByWeightAscending(result[3]);

        return result;
    }

    /**
     * 对手牌按权重升序排序（3在左→大王在右）。
     * <p>
     * 排序规则：
     * <ol>
     *   <li>首先按权重升序排列（权重小的在前）</li>
     *   <li>权重相同时，按花色的 ordinal 值排序作为次级依据</li>
     * </ol>
     * 这是斗地主手牌的标准显示顺序。
     *
     * @param cards 待排序的手牌列表，如果为 null 或元素不超过1个则不做处理
     */
    public static void sortCardsByWeightAscending(List<Card> cards) {
        if (cards == null || cards.size() <= 1) {
            return;
        }
        Collections.sort(cards, new Comparator<Card>() {
            @Override
            public int compare(Card c1, Card c2) {
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
     * 判断给定牌组的牌型。
     * <p>
     * 判定策略采用三层分流：
     * <ol>
     *   <li>特殊快速判定：2张牌先检查王炸，1张牌直接返回单牌</li>
     *   <li>按牌数分流：2/3/4/5张牌各有专门的判定方法，6张及以上走通用判定</li>
     *   <li>分布分析：统计各牌值出现次数，根据次数分布精确判定牌型</li>
     * </ol>
     *
     * @param cards 牌组列表，不能为 null 或空
     * @return 对应的 CardType 枚举值；如果无法组成合法牌型返回 ERROR
     */
    public static CardType getCardType(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return CardType.ERROR;
        }

        int cardCount = cards.size();

        if (cardCount == 2 && isJokerBomb(cards)) {
            return CardType.JOKER_BOMB;
        }

        if (cardCount == 1) {
            return CardType.SINGLE;
        }

        Map<Integer, Integer> rankCountMap = analyzeRankCounts(cards);

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
     * 判断是否为王炸（小王+大王）。
     * <p>
     * 王炸的判定条件：恰好2张牌，且同时包含小王和大王。
     *
     * @param cards 牌组，必须恰好2张
     * @return 如果是小王+大王组合返回 true，否则返回 false
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
     * 分析牌组中各牌值（权重）的出现次数。
     * <p>
     * 这是牌型判定的基础方法，将牌组转换为"权重→出现次数"的映射。
     * 例如：[黑桃3, 红桃3, 梅花5] → {3:2, 5:1}
     *
     * @param cards 待分析的牌组
     * @return Map，key 为牌的权重值，value 为该权重在牌组中出现的次数
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
     * 分析两张牌的牌型。
     * <p>
     * 两张牌只有两种可能：对子（牌值相同）或错误牌型。
     * 注意：王炸已在 getCardType 中提前判定，此处不会遇到王炸情况。
     *
     * @param rankCountMap 牌值分布映射
     * @return PAIR（对子）或 ERROR
     */
    private static CardType analyzeTwoCards(Map<Integer, Integer> rankCountMap) {
        if (rankCountMap.size() == 1) {
            return CardType.PAIR;
        }
        return CardType.ERROR;
    }

    /**
     * 分析三张牌的牌型。
     * <p>
     * 三张牌只有一种合法牌型：三张（牌值完全相同）。
     * 三带一和三带一对需要4张和5张牌，不在此处判定。
     *
     * @param rankCountMap 牌值分布映射
     * @return TRIO（三张）或 ERROR
     */
    private static CardType analyzeThreeCards(Map<Integer, Integer> rankCountMap) {
        if (rankCountMap.size() == 1) {
            return CardType.TRIO;
        }
        return CardType.ERROR;
    }

    /**
     * 分析四张牌的牌型。
     * <p>
     * 四张牌可能组成：
     * <ul>
     *   <li>炸弹：4张牌值完全相同（rankCountMap.size()==1）</li>
     *   <li>三带一：3张相同 + 1张不同（rankCountMap.size()==2，且包含3次和1次）</li>
     * </ul>
     * 注意：四张牌不可能组成四带二（四带二需要6张），也不可能是顺子（顺子最少5张）。
     *
     * @param rankCountMap 牌值分布映射
     * @param cards        原始牌组（当前方法未直接使用，保留供扩展）
     * @return BOMB、TRIO_SINGLE 或 ERROR
     */
    private static CardType analyzeFourCards(Map<Integer, Integer> rankCountMap, List<Card> cards) {
        if (rankCountMap.size() == 1) {
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
     * 分析四带牌型（四带两单或四带两对）。
     * <p>
     * 当牌组中存在4张相同牌值时，判断附带的是两张单牌还是两对：
     * <ul>
     *   <li>四带两单：4张相同 + 2张不同单牌（分布为 4+1+1）</li>
     *   <li>四带两对：4张相同 + 2对（分布为 4+2+2）</li>
     *   <li>如果不符合以上模式，默认当作炸弹处理</li>
     * </ul>
     *
     * @param rankCountMap 牌值分布映射
     * @return QUAD_SINGLE、QUAD_PAIR 或 BOMB
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
            return CardType.QUAD_SINGLE;
        } else if (twoCount == 2) {
            return CardType.QUAD_PAIR;
        }

        return CardType.BOMB;
    }

    /**
     * 分析五张牌的牌型。
     * <p>
     * 五张牌可能组成：
     * <ul>
     *   <li>三带一对：3张相同 + 1对（rankCountMap.size()==2，分布为3+2）</li>
     *   <li>顺子：5张连续单牌（如3-4-5-6-7）</li>
     * </ul>
     * 注意：五张牌不可能只有一种牌值（5张相同不存在于标准扑克中），
     * 也不可能是三带一（三带一只需4张）。
     *
     * @param rankCountMap 牌值分布映射
     * @param cards        原始牌组，用于顺子判定
     * @return TRIO_PAIR、STRAIGHT 或 ERROR
     */
    private static CardType analyzeFiveCards(Map<Integer, Integer> rankCountMap, List<Card> cards) {
        if (rankCountMap.size() == 1) {
            return CardType.ERROR;
        }

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

        if (isStraight(cards)) {
            return CardType.STRAIGHT;
        }

        return CardType.ERROR;
    }

    /**
     * 分析多张牌（6张及以上）的牌型。
     * <p>
     * 按优先级依次检查：
     * <ol>
     *   <li>顺子：5张或更多连续单牌</li>
     *   <li>连对：3对或更多连续对子</li>
     *   <li>飞机/飞机带翅膀：2组或更多连续三张（可带单牌或对子）</li>
     * </ol>
     * 注意：四带二（6张）和炸弹（4张）的判定在牌数分流时已处理，
     * 此处主要处理顺子、连对、飞机等长牌型。
     *
     * @param cardCount    牌的总数
     * @param rankCountMap 牌值分布映射
     * @param cards        原始牌组
     * @return 匹配的牌型，或 ERROR
     */
    private static CardType analyzeMultipleCards(int cardCount, Map<Integer, Integer> rankCountMap, List<Card> cards) {
        List<Integer> uniqueWeights = new ArrayList<>(rankCountMap.keySet());
        Collections.sort(uniqueWeights);

        if (isStraight(cards) && cardCount >= MIN_STRAIGHT_LENGTH) {
            return CardType.STRAIGHT;
        }

        if (isStraightPairs(uniqueWeights, rankCountMap)) {
            return CardType.STRAIGHT_PAIRS;
        }

        CardType airplaneResult = analyzeAirplane(cardCount, rankCountMap, cards);
        if (airplaneResult != CardType.ERROR) {
            return airplaneResult;
        }

        return CardType.ERROR;
    }

    /**
     * 判断牌组是否为顺子（5张或更多连续单牌）。
     * <p>
     * 顺子的判定规则：
     * <ol>
     *   <li>牌数不少于5张</li>
     *   <li>不能包含2和王（它们不能参与顺子）</li>
     *   <li>所有牌的牌值必须互不相同（无重复牌值）</li>
     *   <li>牌值必须连续（相邻权重差为1）</li>
     *   <li>最小牌值不能小于3，最大牌值不能超过A</li>
     * </ol>
     *
     * @param cards 待判定的牌组
     * @return 如果满足顺子条件返回 true，否则返回 false
     */
    private static boolean isStraight(List<Card> cards) {
        if (cards.size() < MIN_STRAIGHT_LENGTH) {
            return false;
        }

        List<Integer> weights = new ArrayList<>();
        for (Card card : cards) {
            if (card.getRank().isJoker() || card.getRank().isTwo()) {
                return false;
            }
            weights.add(card.getWeight());
        }

        if (weights.size() != cards.size()) {
            return false;
        }

        Collections.sort(weights);
        int minWeight = weights.get(0);

        if (minWeight < Rank.THREE.getWeight() || minWeight > Rank.ACE.getWeight() - cards.size() + 1) {
            return false;
        }

        for (int i = 1; i < weights.size(); i++) {
            if (weights.get(i) - weights.get(i - 1) != 1) {
                return false;
            }
        }

        return true;
    }

    /**
     * 判断是否为连对（3对或更多连续对子）。
     * <p>
     * 连对的判定规则：
     * <ol>
     *   <li>至少3对不同的牌值</li>
     *   <li>每个牌值必须恰好出现2次（对子）</li>
     *   <li>牌值必须连续（相邻权重差为1）</li>
     *   <li>最大牌值不能超过A（权重14），不能包含2</li>
     *   <li>最小牌值不能太小，确保顺子长度合法</li>
     * </ol>
     *
     * @param uniqueWeights 排序后的不重复权重列表
     * @param rankCountMap  牌值分布映射
     * @return 如果满足连对条件返回 true，否则返回 false
     */
    private static boolean isStraightPairs(List<Integer> uniqueWeights, Map<Integer, Integer> rankCountMap) {
        if (uniqueWeights.size() < MIN_STRAIGHT_PAIRS_COUNT) {
            return false;
        }

        for (int weight : uniqueWeights) {
            if (rankCountMap.get(weight) != 2) {
                return false;
            }
        }

        Collections.sort(uniqueWeights);
        for (int i = 1; i < uniqueWeights.size(); i++) {
            if (uniqueWeights.get(i) - uniqueWeights.get(i - 1) != 1) {
                return false;
            }
        }

        int maxWeight = uniqueWeights.get(uniqueWeights.size() - 1);
        if (maxWeight > Rank.ACE.getWeight()) {
            return false;
        }

        int minWeight = uniqueWeights.get(0);
        if (minWeight < Rank.THREE.getWeight() || minWeight > Rank.ACE.getWeight() - uniqueWeights.size() + 1) {
            return false;
        }

        return true;
    }

    /**
     * 分析飞机相关牌型。
     * <p>
     * 飞机是斗地主中较复杂的牌型，判定逻辑如下：
     * <ol>
     *   <li>统计三张(trioWeights)、对子(pairWeights)、单牌(singleWeights)的权重列表</li>
     *   <li>如果没有三张，直接排除飞机</li>
     *   <li>检查三张是否连续（相邻权重差为1）</li>
     *   <li>检查三张序列是否合法（不能包含2和王）</li>
     *   <li>根据附带牌的情况判定：纯飞机、飞机带单牌、飞机带对子</li>
     *   <li>如果只有1组三张，退化为三带一或三带一对</li>
     * </ol>
     * <p>
     * 注意：当前实现中，飞机的连续性判定存在一定的简化，
     * 对于四张拆分为三张+单牌的情况未做处理。
     *
     * @param cardCount    牌的总数
     * @param rankCountMap 牌值分布映射
     * @param cards        原始牌组
     * @return AIRPLANE、AIRPLANE_WITH_WINGS、TRIO_SINGLE、TRIO_PAIR 或 ERROR
     */
    private static CardType analyzeAirplane(int cardCount, Map<Integer, Integer> rankCountMap, List<Card> cards) {
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

        if (trioWeights.isEmpty()) {
            return CardType.ERROR;
        }

        Collections.sort(trioWeights);
        boolean isContinuous = true;
        for (int i = 1; i < trioWeights.size(); i++) {
            if (trioWeights.get(i) - trioWeights.get(i - 1) != 1) {
                isContinuous = false;
                break;
            }
        }

        for (int weight : trioWeights) {
            if (weight >= Rank.TWO.getWeight() || Rank.fromSymbol(String.valueOf(weight)) == null) {
                if (weight == Rank.TWO.getWeight()) {
                    return CardType.ERROR;
                }
            }
        }

        if (!isContinuous && trioWeights.size() >= MIN_AIRPLANE_TRIO_COUNT) {
            int minTrioWeight = trioWeights.get(0);
            int maxTrioWeight = trioWeights.get(trioWeights.size() - 1);
            if (maxTrioWeight - minTrioWeight != trioWeights.size() - 1) {
                isContinuous = false;
            }
        }

        if (isContinuous && trioWeights.size() >= MIN_AIRPLANE_TRIO_COUNT) {
            int trioCount = trioWeights.size();

            if (singleWeights.size() == trioCount) {
                return CardType.AIRPLANE_WITH_WINGS;
            } else if (pairWeights.size() == trioCount) {
                return CardType.AIRPLANE_WITH_WINGS;
            } else if (singleWeights.isEmpty() && pairWeights.isEmpty()) {
                return CardType.AIRPLANE;
            }
        }

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
     * 判断当前出牌是否能打过上家出牌。
     * <p>
     * 出牌规则（按优先级）：
     * <ol>
     *   <li>上家未出牌（null或空）：当前出牌只要合法即可</li>
     *   <li>当前牌型无效（ERROR）：不能出</li>
     *   <li>王炸可以打任意牌型</li>
     *   <li>炸弹可以打任意非炸弹牌型</li>
     *   <li>炸弹对炸弹：比较主权重，大的胜</li>
     *   <li>同牌型比较：比较主权重，大的胜</li>
     *   <li>不同牌型（非炸弹）：不能打</li>
     * </ol>
     *
     * @param currentCards  当前玩家要出的牌组
     * @param previousCards 上家打出的牌组；null 或空列表表示上家未出牌（自由出牌回合）
     * @return 如果可以打出返回 true，否则返回 false
     */
    public static boolean canPlayPass(List<Card> currentCards, List<Card> previousCards) {
        if (previousCards == null || previousCards.isEmpty()) {
            return getCardType(currentCards) != CardType.ERROR;
        }

        CardType currentType = getCardType(currentCards);
        CardType previousType = getCardType(previousCards);

        if (currentType == CardType.ERROR) {
            return false;
        }

        if (currentType == CardType.JOKER_BOMB) {
            return true;
        }

        if (currentType == CardType.BOMB && !previousType.isBomb()) {
            return true;
        }

        if (currentType == CardType.JOKER_BOMB && previousType == CardType.BOMB) {
            return true;
        }

        if (currentType == CardType.BOMB && previousType == CardType.BOMB) {
            return getMainWeight(currentCards) > getMainWeight(previousCards);
        }

        if (currentType != previousType) {
            return false;
        }

        return getMainWeight(currentCards) > getMainWeight(previousCards);
    }

    /**
     * 获取牌组的主权重。
     * <p>
     * 主权重是同牌型比较大小的核心依据：
     * <ul>
     *   <li>单牌/对子/三张/顺子/连对/炸弹：直接取第一张牌的权重（已排序，第一张即代表牌值）</li>
     *   <li>三带一/三带一对/四带二/飞机：取出现3次或4次的牌值权重（核心牌的权重）</li>
     *   <li>王炸：返回大王的权重（17），是最大权重</li>
     * </ul>
     * <p>
     * 关键点：对于带牌牌型，只比较核心牌的权重，附带牌不参与比较。
     * 例如：333+5 和 444+K 比较，主权重分别是3和4，4>3所以后者更大。
     *
     * @param cards 牌组
     * @return 主权重值；如果牌组为空返回0
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
                return cards.get(0).getWeight();

            case TRIO_SINGLE:
            case TRIO_PAIR:
            case QUAD_SINGLE:
            case QUAD_PAIR:
            case AIRPLANE:
            case AIRPLANE_WITH_WINGS:
                for (Map.Entry<Integer, Integer> entry : rankCountMap.entrySet()) {
                    if (entry.getValue() == 3 || entry.getValue() == 4) {
                        return entry.getKey();
                    }
                }
                break;

            case JOKER_BOMB:
                return Rank.BIG_JOKER.getWeight();

            default:
                break;
        }

        int maxWeight = 0;
        for (Card card : cards) {
            if (card.getWeight() > maxWeight) {
                maxWeight = card.getWeight();
            }
        }
        return maxWeight;
    }

    /**
     * 从手牌中找出所有能打过上家牌型的牌组组合。
     * <p>
     * 此方法用于 AI 决策和玩家提示功能，搜索策略如下：
     * <ol>
     *   <li>如果上家未出牌：返回所有单牌作为最基本的选择</li>
     *   <li>优先搜索炸弹和王炸（可以压制任意牌型）</li>
     *   <li>根据上家牌型分类搜索：单牌、对子、三带、炸弹等</li>
     *   <li>顺子的搜索较复杂，当前版本简化处理</li>
     * </ol>
     * <p>
     * 注意：当前实现为简化版本，以下牌型的搜索尚未完善：
     * <ul>
     *   <li>顺子：需要枚举所有可能的连续牌值组合，算法复杂度较高</li>
     *   <li>连对：同上</li>
     *   <li>飞机：组合情况更多，搜索空间大</li>
     * </ul>
     *
     * @param handCards     玩家当前手牌
     * @param previousCards 上家打出的牌组；null 或空列表表示上家未出牌
     * @return 能打过上家的所有合法牌组列表；如果没有能打过的牌返回空列表
     */
    public static List<List<Card>> findPlayableCombos(List<Card> handCards, List<Card> previousCards) {
        List<List<Card>> result = new ArrayList<>();

        if (handCards == null || handCards.isEmpty()) {
            return result;
        }

        if (previousCards == null || previousCards.isEmpty()) {
            for (Card card : handCards) {
                List<Card> combo = new ArrayList<>();
                combo.add(card);
                result.add(combo);
            }
            return result;
        }

        CardType previousType = getCardType(previousCards);
        int previousMainWeight = getMainWeight(previousCards);

        Map<Integer, Integer> handRankCounts = analyzeRankCounts(handCards);
        List<Card> sortedHand = new ArrayList<>(handCards);
        Collections.sort(sortedHand, (c1, c2) -> Integer.compare(c2.getWeight(), c1.getWeight()));

        for (Card card : sortedHand) {
            List<Card> bombCombo = findBombWithWeight(handCards, handRankCounts, card.getWeight());
            if (bombCombo != null && canPlayPass(bombCombo, previousCards)) {
                result.add(bombCombo);
            }
        }

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
                        if (previousType == CardType.TRIO) {
                            result.add(trioCombo);
                        } else if (previousType == CardType.TRIO_SINGLE) {
                            Card smallestSingle = findSmallestSingle(handCards, card.getWeight());
                            if (smallestSingle != null) {
                                trioCombo.add(smallestSingle);
                                result.add(trioCombo);
                            }
                        } else if (previousType == CardType.TRIO_PAIR) {
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
                break;

            case BOMB:
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
     * 从手牌中查找指定权重且数量足够的牌。
     * <p>
     * 例如：查找权重为3的2张牌，会返回黑桃3和红桃3（如果存在）。
     *
     * @param handCards 手牌列表
     * @param weight    目标权重值
     * @param count     需要的牌数
     * @return 找到的牌列表；如果手牌中该权重牌数不足，返回 null
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
     * 查找手牌中能组成炸弹的牌组。
     * <p>
     * 炸弹需要4张相同牌值的牌，此方法检查指定权重是否有4张。
     *
     * @param handCards  手牌列表
     * @param rankCounts 牌值分布映射
     * @param weight     目标权重值
     * @return 如果该权重有4张牌返回对应的牌列表，否则返回 null
     */
    private static List<Card> findBombWithWeight(List<Card> handCards, Map<Integer, Integer> rankCounts, int weight) {
        if (rankCounts.getOrDefault(weight, 0) == 4) {
            return findSameRankCards(handCards, weight, 4);
        }
        return null;
    }

    /**
     * 从手牌中找到权重最小的单牌（排除指定权重）。
     * <p>
     * 用于三带一的附带牌选择：优先出最小的单牌，保留大牌。
     * 排除指定权重是为了避免附带牌与三张牌值相同。
     *
     * @param handCards     手牌列表
     * @param excludeWeight 需要排除的权重值（通常是三张的权重）
     * @return 权重最小的单牌；如果没有可用单牌返回 null
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
     * 从手牌中找到权重最小的一对（排除指定权重）。
     * <p>
     * 用于三带一对的附带牌选择：优先出最小的对子，保留大牌。
     * 排除指定权重是为了避免附带对子与三张牌值相同。
     *
     * @param handCards     手牌列表
     * @param excludeWeight 需要排除的权重值（通常是三张的权重）
     * @return 权重最小的一对牌（恰好2张）；如果没有可用对子返回 null
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
     * 辅助方法：从多个列表中查找指定权重的牌。
     * <p>
     * 当前实现直接委托给 findSameRankCards，保留此方法供未来扩展使用
     * （例如需要从多个来源合并搜索时）。
     *
     * @param handCards 手牌列表
     * @param weight    目标权重值
     * @param count     需要的牌数
     * @return 找到的牌列表；如果手牌为 null 或牌数不足返回 null
     */
    private static List<Card> findSameRankCardsFromMultipleLists(List<Card> handCards, int weight, int count) {
        if (handCards == null) return null;
        return findSameRankCards(handCards, weight, count);
    }

    /**
     * 计算炸弹的倍数（用于游戏计分）。
     * <p>
     * 倍数规则：
     * <ul>
     *   <li>普通炸弹：2倍</li>
     *   <li>王炸（火箭）：4倍</li>
     *   <li>非炸弹牌型：1倍（不翻倍）</li>
     * </ul>
     * 每出一个炸弹，游戏底分乘以对应倍数。
     *
     * @param cards 牌组
     * @return 倍数值：普通炸弹返回2，王炸返回4，其他返回1，非法牌型返回1
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
     * 验证牌组是否完整且有效。
     * <p>
     * 检查条件：
     * <ul>
     *   <li>牌组不为 null 且不为空</li>
     *   <li>牌组中不包含 null 元素</li>
     * </ul>
     *
     * @param cards 待验证的牌组
     * @return 如果牌组有效返回 true，否则返回 false
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
     * 检查牌组中是否包含大王。
     *
     * @param cards 牌组列表
     * @return 如果包含大王返回 true，否则返回 false；牌组为 null 时返回 false
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
     * 检查牌组中是否包含小王。
     *
     * @param cards 牌组列表
     * @return 如果包含小王返回 true，否则返回 false；牌组为 null 时返回 false
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
