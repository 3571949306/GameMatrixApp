package com.gamecenter.app.games.coin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 称号商品目录（纯 Java，无 Android 依赖）。
 *
 * <p>称号分两类，id 全局唯一：
 * <ul>
 *   <li>金币购买型：{@link #all()} 的 4 个称号，按 id/价格升序排列，花金币兑换；</li>
 *   <li>成就解锁型：{@link #achievementTitles()} 的 3 个称号（id 101-103），
 *       解锁成就总数达到里程碑后免费授予，cost 恒为 0、不可购买。</li>
 * </ul>
 * 目录为纯静态集合，禁止实例化。
 */
public class TitleCatalog {

    /** 一条不可变的称号商品。 */
    public static final class Title {
        /** 称号唯一 id。 */
        public final int id;
        /** 称号名（中文）。 */
        public final String name;
        /** 兑换价格（金币）。 */
        public final int cost;
        /** 解锁所需成就数：0=金币购买型；&gt;0=成就解锁型（此时 cost 恒 0）。 */
        public final int requiredAchievements;

        public Title(int id, String name, int cost) {
            this(id, name, cost, 0);
        }

        public Title(int id, String name, int cost, int requiredAchievements) {
            this.id = id;
            this.name = name;
            this.cost = cost;
            this.requiredAchievements = requiredAchievements;
        }
    }

    private static final List<Title> TITLES = Arrays.asList(
            new Title(1, "青铜玩家", 50),
            new Title(2, "白银高手", 150),
            new Title(3, "黄金战神", 400),
            new Title(4, "传奇殿堂", 1000));

    /** 成就解锁型称号（id 101-103，按所需成就数升序）。 */
    private static final List<Title> ACHIEVEMENT_TITLES = Arrays.asList(
            new Title(101, "初露锋芒", 0, 3),
            new Title(102, "成就猎人", 0, 8),
            new Title(103, "全收集大师", 0, 15));

    /** 私有构造器：纯静态目录，禁止实例化。 */
    private TitleCatalog() {
    }

    /** 按 id 查找称号（购买型 + 成就解锁型）；找不到返回 null。 */
    public static Title find(int id) {
        for (Title t : TITLES) {
            if (t.id == id) {
                return t;
            }
        }
        for (Title t : ACHIEVEMENT_TITLES) {
            if (t.id == id) {
                return t;
            }
        }
        return null;
    }

    /** 全部金币可购买称号（按 id/价格升序，返回防御性副本）。 */
    public static List<Title> all() {
        return new ArrayList<>(TITLES);
    }

    /** 全部成就解锁型称号（按所需成就数升序，返回防御性副本）。 */
    public static List<Title> achievementTitles() {
        return new ArrayList<>(ACHIEVEMENT_TITLES);
    }
}
