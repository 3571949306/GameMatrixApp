package com.gamecenter.app.doudizhu.logic;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.Rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * 出牌候选生成器（提示引擎 / AI 候选来源）。
 *
 * <p>替代旧 {@code GameRuleUtil.findPlayableCombos} 的半成品实现（R4：
 * 顺子分支为空、无连对/飞机/四带二、自由出牌只返回单牌）。
 * 本实现覆盖全部 13 种牌型，输出按"代价"升序（先小牌后炸弹），
 * 供提示按钮循环展示，也供 P2 的 AI 作为候选集。</p>
 *
 * <p>纯函数：不修改手牌，返回的每个 Combo 都通过 {@link Combo#of} 自校验。</p>
 */
public final class HintFinder {

    private static final int MIN_WEIGHT = Rank.THREE.getWeight();
    private static final int MAX_SEQUENCE_WEIGHT = Rank.ACE.getWeight();
    private static final int SMALL_JOKER = Rank.SMALL_JOKER.getWeight();
    private static final int BIG_JOKER = Rank.BIG_JOKER.getWeight();

    private HintFinder() {}

    /**
     * 生成可出组合。
     *
     * @param hand      当前手牌
     * @param previous  要压过的组合；null 表示自由出牌
     * @return 按代价升序的候选列表；无解时为空列表
     */
    public static List<Combo> findPlayable(List<Card> hand, Combo previous) {
        List<Combo> out = new ArrayList<>();
        if (hand == null || hand.isEmpty()) return out;

        TreeMap<Integer, List<Card>> byWeight = new TreeMap<>();
        for (Card c : hand) {
            if (c == null) continue;
            byWeight.computeIfAbsent(c.getWeight(), k -> new ArrayList<>()).add(c);
        }
        if (byWeight.isEmpty()) return out;

        if (previous == null) {
            collectLeads(byWeight, out);
        } else {
            collectResponses(byWeight, previous, out);
        }
        out.sort(Comparator
                .comparingInt((Combo c) -> c.isBomb() ? 1 : 0)
                .thenComparingInt(Combo::getMainWeight)
                .thenComparingInt(Combo::size));
        return out;
    }

    // ============ 自由出牌 ============

    private static void collectLeads(TreeMap<Integer, List<Card>> byWeight, List<Combo> out) {
        for (int w : byWeight.keySet()) {
            addIfValid(out, take(byWeight, w, 1));
        }
        for (int w : byWeight.keySet()) {
            if (count(byWeight, w) >= 2) addIfValid(out, take(byWeight, w, 2));
        }
        for (int w : byWeight.keySet()) {
            if (count(byWeight, w) >= 3) {
                addIfValid(out, take(byWeight, w, 3));
                List<Card> trioSingle = new ArrayList<>(take(byWeight, w, 3));
                Card kicker = smallestOther(byWeight, w, 1);
                if (kicker != null) {
                    trioSingle.add(kicker);
                    addIfValid(out, trioSingle);
                }
                List<Card> trioPair = new ArrayList<>(take(byWeight, w, 3));
                List<Card> pairKicker = smallestOtherPair(byWeight, w);
                if (pairKicker != null) {
                    trioPair.addAll(pairKicker);
                    addIfValid(out, trioPair);
                }
            }
        }
        collectStraightLeads(byWeight, out);
        collectStraightPairsLeads(byWeight, out);
        collectAirplaneLeads(byWeight, out);
        for (int w : byWeight.keySet()) {
            if (count(byWeight, w) == 4) {
                addIfValid(out, take(byWeight, w, 4));
                List<Card> quadSingle = new ArrayList<>(take(byWeight, w, 4));
                List<Card> kickers = smallestOthers(byWeight, w, 2);
                if (kickers != null) {
                    quadSingle.addAll(kickers);
                    addIfValid(out, quadSingle);
                }
                List<Card> quadPair = new ArrayList<>(take(byWeight, w, 4));
                List<Card> pairs = smallestOtherPairs(byWeight, w, 2);
                if (pairs != null) {
                    quadPair.addAll(pairs);
                    addIfValid(out, quadPair);
                }
            }
        }
        addRocketIfPresent(byWeight, out);
    }

    private static void collectStraightLeads(TreeMap<Integer, List<Card>> byWeight,
                                             List<Combo> out) {
        for (int len = 5; len <= MAX_SEQUENCE_WEIGHT - MIN_WEIGHT + 1; len++) {
            for (int start = MIN_WEIGHT; start + len - 1 <= MAX_SEQUENCE_WEIGHT; start++) {
                List<Card> cards = window(byWeight, start, len, 1);
                if (cards != null) addIfValid(out, cards);
            }
        }
    }

    private static void collectStraightPairsLeads(TreeMap<Integer, List<Card>> byWeight,
                                                  List<Combo> out) {
        for (int len = 3; len <= (MAX_SEQUENCE_WEIGHT - MIN_WEIGHT + 1); len++) {
            for (int start = MIN_WEIGHT; start + len - 1 <= MAX_SEQUENCE_WEIGHT; start++) {
                List<Card> cards = window(byWeight, start, len, 2);
                if (cards != null) addIfValid(out, cards);
            }
        }
    }

    private static void collectAirplaneLeads(TreeMap<Integer, List<Card>> byWeight,
                                             List<Combo> out) {
        for (int len = 2; len <= MAX_SEQUENCE_WEIGHT - MIN_WEIGHT + 1; len++) {
            for (int start = MIN_WEIGHT; start + len - 1 <= MAX_SEQUENCE_WEIGHT; start++) {
                List<Card> pure = window(byWeight, start, len, 3);
                if (pure == null) continue;
                addIfValid(out, pure);
                // 带单翅膀
                List<Card> withSingles = new ArrayList<>(pure);
                List<Card> wingsS = airplaneWings(byWeight, start, len, 1);
                if (wingsS != null) {
                    withSingles.addAll(wingsS);
                    addIfValid(out, withSingles);
                }
                // 带对翅膀
                List<Card> withPairs = new ArrayList<>(pure);
                List<Card> wingsP = airplaneWings(byWeight, start, len, 2);
                if (wingsP != null) {
                    withPairs.addAll(wingsP);
                    addIfValid(out, withPairs);
                }
            }
        }
    }

    // ============ 跟牌 ============

    private static void collectResponses(TreeMap<Integer, List<Card>> byWeight, Combo previous,
                                         List<Combo> out) {
        if (previous.getType() == com.gamecenter.app.doudizhu.model.CardType.JOKER_BOMB) {
            return;
        }
        int prevMain = previous.getMainWeight();
        boolean prevIsBomb = previous.isBomb();

        switch (previous.getType()) {
            case SINGLE:
                for (int w : byWeight.keySet()) {
                    if (w > prevMain) addIfValid(out, take(byWeight, w, 1));
                }
                break;
            case PAIR:
                for (int w : byWeight.keySet()) {
                    if (w > prevMain && count(byWeight, w) >= 2) {
                        addIfValid(out, take(byWeight, w, 2));
                    }
                }
                break;
            case TRIO:
                for (int w : byWeight.keySet()) {
                    if (w > prevMain && count(byWeight, w) >= 3) {
                        addIfValid(out, take(byWeight, w, 3));
                    }
                }
                break;
            case TRIO_SINGLE:
                for (int w : byWeight.keySet()) {
                    if (w > prevMain && count(byWeight, w) >= 3) {
                        List<Card> cards = new ArrayList<>(take(byWeight, w, 3));
                        Card kicker = smallestOther(byWeight, w, 1);
                        if (kicker != null) {
                            cards.add(kicker);
                            addIfValid(out, cards);
                        }
                    }
                }
                break;
            case TRIO_PAIR:
                for (int w : byWeight.keySet()) {
                    if (w > prevMain && count(byWeight, w) >= 3) {
                        List<Card> cards = new ArrayList<>(take(byWeight, w, 3));
                        List<Card> pairKicker = smallestOtherPair(byWeight, w);
                        if (pairKicker != null) {
                            cards.addAll(pairKicker);
                            addIfValid(out, cards);
                        }
                    }
                }
                break;
            case STRAIGHT:
                collectStraightResponses(byWeight, previous, out);
                break;
            case STRAIGHT_PAIRS:
                collectStraightPairsResponses(byWeight, previous, out);
                break;
            case AIRPLANE:
                collectAirplaneResponses(byWeight, previous, out, Combo.WINGS_NONE);
                break;
            case AIRPLANE_WITH_WINGS:
                collectAirplaneResponses(byWeight, previous, out, previous.getWingsKind());
                break;
            case QUAD_SINGLE:
                for (int w : byWeight.keySet()) {
                    if (w > prevMain && count(byWeight, w) == 4) {
                        List<Card> cards = new ArrayList<>(take(byWeight, w, 4));
                        List<Card> kickers = smallestOthers(byWeight, w, 2);
                        if (kickers != null) {
                            cards.addAll(kickers);
                            addIfValid(out, cards);
                        }
                    }
                }
                break;
            case QUAD_PAIR:
                for (int w : byWeight.keySet()) {
                    if (w > prevMain && count(byWeight, w) == 4) {
                        List<Card> cards = new ArrayList<>(take(byWeight, w, 4));
                        List<Card> pairs = smallestOtherPairs(byWeight, w, 2);
                        if (pairs != null) {
                            cards.addAll(pairs);
                            addIfValid(out, cards);
                        }
                    }
                }
                break;
            default:
                break;
        }

        // 炸弹与王炸（非炸弹牌型一律可炸；炸弹对炸弹须更大）
        for (int w : byWeight.keySet()) {
            if (count(byWeight, w) == 4 && (!prevIsBomb || w > prevMain)) {
                addIfValid(out, take(byWeight, w, 4));
            }
        }
        addRocketIfPresent(byWeight, out);
    }

    private static void collectStraightResponses(TreeMap<Integer, List<Card>> byWeight,
                                                 Combo previous, List<Combo> out) {
        int len = previous.getLength();
        for (int start = MIN_WEIGHT; start + len - 1 <= MAX_SEQUENCE_WEIGHT; start++) {
            if (start + len - 1 <= previous.getMainWeight()) continue;
            List<Card> cards = window(byWeight, start, len, 1);
            if (cards != null) addIfValid(out, cards);
        }
    }

    private static void collectStraightPairsResponses(TreeMap<Integer, List<Card>> byWeight,
                                                      Combo previous, List<Combo> out) {
        int len = previous.getLength();
        for (int start = MIN_WEIGHT; start + len - 1 <= MAX_SEQUENCE_WEIGHT; start++) {
            if (start + len - 1 <= previous.getMainWeight()) continue;
            List<Card> cards = window(byWeight, start, len, 2);
            if (cards != null) addIfValid(out, cards);
        }
    }

    private static void collectAirplaneResponses(TreeMap<Integer, List<Card>> byWeight,
                                                 Combo previous, List<Combo> out, int wingsKind) {
        int len = previous.getLength();
        for (int start = MIN_WEIGHT; start + len - 1 <= MAX_SEQUENCE_WEIGHT; start++) {
            if (start + len - 1 <= previous.getMainWeight()) continue;
            List<Card> pure = window(byWeight, start, len, 3);
            if (pure == null) continue;
            if (wingsKind == Combo.WINGS_NONE) {
                addIfValid(out, pure);
                continue;
            }
            List<Card> cards = new ArrayList<>(pure);
            List<Card> wings = airplaneWings(byWeight, start, len,
                    wingsKind == Combo.WINGS_SINGLES ? 1 : 2);
            if (wings != null) {
                cards.addAll(wings);
                addIfValid(out, cards);
            }
        }
    }

    // ============ 手牌取牌工具 ============

    private static int count(TreeMap<Integer, List<Card>> byWeight, int w) {
        List<Card> list = byWeight.get(w);
        return list == null ? 0 : list.size();
    }

    private static List<Card> take(TreeMap<Integer, List<Card>> byWeight, int w, int k) {
        List<Card> list = byWeight.get(w);
        if (list == null || list.size() < k) return new ArrayList<>();
        return new ArrayList<>(list.subList(0, k));
    }

    /** [start, start+len) 每个权重取 perRank 张；不足返回 null。 */
    private static List<Card> window(TreeMap<Integer, List<Card>> byWeight, int start, int len,
                                     int perRank) {
        List<Card> cards = new ArrayList<>();
        for (int w = start; w < start + len; w++) {
            if (count(byWeight, w) < perRank) return null;
            cards.addAll(take(byWeight, w, perRank));
        }
        return cards;
    }

    /** 最小的、权重不等于 exclude 的单张（excludeCount 表示该权重需保留的张数）。 */
    private static Card smallestOther(TreeMap<Integer, List<Card>> byWeight, int exclude,
                                      int keepFromExclude) {
        for (int w : byWeight.keySet()) {
            if (w == exclude) continue;
            List<Card> list = byWeight.get(w);
            if (!list.isEmpty()) return list.get(0);
        }
        return null;
    }

    /** 最小的、权重不等于 exclude 的对子。 */
    private static List<Card> smallestOtherPair(TreeMap<Integer, List<Card>> byWeight,
                                                int exclude) {
        for (int w : byWeight.keySet()) {
            if (w == exclude) continue;
            if (count(byWeight, w) >= 2) return take(byWeight, w, 2);
        }
        return null;
    }

    /** 最小的 k 张单牌（跳过 exclude 权重）。 */
    private static List<Card> smallestOthers(TreeMap<Integer, List<Card>> byWeight, int exclude,
                                             int k) {
        List<Card> out = new ArrayList<>();
        for (int w : byWeight.keySet()) {
            if (w == exclude) continue;
            for (Card c : byWeight.get(w)) {
                out.add(c);
                if (out.size() == k) return out;
            }
        }
        return out.size() == k ? out : null;
    }

    /** 最小的 k 个对子（跳过 exclude 权重）。 */
    private static List<Card> smallestOtherPairs(TreeMap<Integer, List<Card>> byWeight,
                                                 int exclude, int k) {
        List<Card> out = new ArrayList<>();
        int pairs = 0;
        for (int w : byWeight.keySet()) {
            if (w == exclude) continue;
            if (count(byWeight, w) >= 2) {
                out.addAll(take(byWeight, w, 2));
                pairs++;
                if (pairs == k) return out;
            }
        }
        return pairs == k ? out : null;
    }

    /**
     * 飞机翅膀：从 run 之外的剩余牌取 len 个单张（wingKind=1）或 len 个对子（wingKind=2）。
     * run 内权重的前 3 张已被三张段占用，翅膀从其余牌取；非 run 权重从第 0 张起可取。
     * 剩余不足返回 null。
     */
    private static List<Card> airplaneWings(TreeMap<Integer, List<Card>> byWeight, int start,
                                            int len, int wingKind) {
        List<Card> out = new ArrayList<>();
        if (wingKind == 1) {
            for (int w : byWeight.keySet()) {
                List<Card> list = byWeight.get(w);
                int skip = (w >= start && w < start + len) ? 3 : 0;
                for (int i = skip; i < list.size() && out.size() < len; i++) {
                    out.add(list.get(i));
                }
            }
            return out.size() == len ? out : null;
        }
        int pairs = 0;
        for (int w : byWeight.keySet()) {
            List<Card> list = byWeight.get(w);
            int skip = (w >= start && w < start + len) ? 3 : 0;
            int usablePairs = (list.size() - skip) / 2;
            for (int i = 0; i < usablePairs && pairs < len; i++) {
                out.add(list.get(skip + i * 2));
                out.add(list.get(skip + i * 2 + 1));
                pairs++;
            }
        }
        return pairs == len ? out : null;
    }

    private static void addRocketIfPresent(TreeMap<Integer, List<Card>> byWeight,
                                           List<Combo> out) {
        if (count(byWeight, SMALL_JOKER) >= 1 && count(byWeight, BIG_JOKER) >= 1) {
            List<Card> rocket = new ArrayList<>();
            rocket.add(byWeight.get(SMALL_JOKER).get(0));
            rocket.add(byWeight.get(BIG_JOKER).get(0));
            addIfValid(out, rocket);
        }
    }

    private static void addIfValid(List<Combo> out, List<Card> cards) {
        Combo combo = Combo.of(cards);
        if (combo != null) out.add(combo);
    }
}
