package com.gamecenter.app.td.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 内置关卡定义。
 *
 * <p>每张地图均通过 {@link TdGame} 的构造校验：路径必须连续、无重复、且最终回到蛋蛋。
 * 前三关逐步引入混编、防空、护盾和治疗；第四关启用双入口；第五关以高压 Boss 收束。
 */
public final class TdLevels {

    private TdLevels() {}

    /** 返回关卡 ID 列表，供存档/选关 UI 使用。 */
    public static List<String> levelIds() {
        return Arrays.asList("level_01", "level_02", "level_03", "level_04", "level_05");
    }

    /** 关卡展示名（选关界面）。 */
    public static String levelDisplayName(int idx, String id) {
        switch (id) {
            case "level_01": return "晨露花园";
            case "level_02": return "荆棘林地";
            case "level_03": return "水晶回廊";
            case "level_04": return "双岔溪谷";
            case "level_05": return "风暴巢穴";
            default: return "第 " + (idx + 1) + " 关";
        }
    }

    /** 关卡一句话描述，明确这一关的策略重点。 */
    public static String levelSub(int idx, String id) {
        switch (id) {
            case "level_01": return "双回廊教学 · 单体与减速协同";
            case "level_02": return "错位窄口 · 首次应对混编和空袭";
            case "level_03": return "水晶折线 · 护盾兵与医生怪登场";
            case "level_04": return "双入口围攻 · 根据路线预告布防";
            case "level_05": return "终局攻城 · 清群、集火与 Boss 决战";
            default: return "";
        }
    }

    public static TdGame buildLevel(String id) {
        switch (id) {
            case "level_01": return level01();
            case "level_02": return level02();
            case "level_03": return level03();
            case "level_04": return level04();
            case "level_05": return level05();
            default: throw new IllegalArgumentException("unknown level: " + id);
        }
    }

    /** 第 1 关：四段折返形成两组可同时覆盖的走廊，教学“拐点集中火力”。 */
    private static TdGame level01() {
        int[][] path = p(
                0,0, 0,1, 0,2, 0,3, 0,4, 0,5, 0,6, 0,7, 0,8, 0,9, 0,10, 0,11,
                1,11, 2,11, 2,10, 2,9, 2,8, 2,7, 2,6, 2,5, 2,4, 2,3, 2,2,
                3,2, 4,2, 4,3, 4,4, 4,5, 4,6, 4,7, 4,8, 4,9,
                5,9, 6,9, 6,8, 6,7, 6,6, 6,5, 6,4, 6,3, 6,2, 6,1, 6,0, 7,0);
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(w(MonsterType.NORMAL, 6, .82f, .4f, 1f, 1f));
        waves.add(w(MonsterType.FAST, 5, .58f, .45f, .92f, 1f));
        waves.add(mix(0, 8, .62f, .45f, 1f, 1f, MonsterType.NORMAL, MonsterType.FAST));
        waves.add(w(MonsterType.TANK, 2, 1.8f, .5f, .92f, 1f));
        waves.add(w(MonsterType.SWARM, 10, .35f, .45f, 1f, 1f));
        waves.add(mix(0, 7, .52f, .45f, 1.08f, 1f, MonsterType.TANK, MonsterType.FAST));
        waves.add(w(MonsterType.SHIELD, 3, 1.2f, .5f, 1f, 1f));
        waves.add(mix(0, 6, .65f, .5f, 1.08f, 1f, MonsterType.HEALER, MonsterType.NORMAL));
        return new TdGame(12, 8, path, 7, 0, 220, 5, waves);
    }

    /** 第 2 关：不等距折线留下三处窄口，范围塔和对空塔开始各有明确职责。 */
    private static TdGame level02() {
        int[][] path = p(
                0,0, 0,1, 0,2, 0,3, 0,4, 0,5, 0,6, 0,7, 0,8, 0,9, 0,10,
                1,10, 2,10, 2,9, 2,8, 2,7, 2,6, 2,5, 2,4, 2,3,
                3,3, 4,3, 4,4, 4,5, 4,6, 4,7, 4,8, 4,9,
                5,9, 6,9, 6,8, 6,7, 6,6, 6,5, 7,5, 7,4, 7,3, 7,2, 7,1, 7,0);
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(w(MonsterType.NORMAL, 8, .75f, .45f, 1f, 1f));
        waves.add(mix(0, 8, .55f, .45f, 1f, 1f, MonsterType.FAST, MonsterType.NORMAL));
        waves.add(w(MonsterType.FLY, 6, .65f, .45f, 1f, 1f));
        waves.add(w(MonsterType.SWARM, 12, .3f, .45f, 1.05f, 1f));
        waves.add(mix(0, 7, .65f, .5f, 1.12f, 1f, MonsterType.TANK, MonsterType.FAST));
        waves.add(w(MonsterType.SHIELD, 4, 1f, .45f, 1.05f, 1f));
        waves.add(mix(0, 8, .58f, .5f, 1.1f, 1f, MonsterType.HEALER, MonsterType.FLY));
        waves.add(w(MonsterType.TANK, 3, 1.5f, .55f, 1.2f, .95f));
        waves.add(mix(0, 10, .45f, .45f, 1.15f, 1.05f, MonsterType.FAST, MonsterType.SHIELD));
        waves.add(w(MonsterType.BOSS, 1, 0f, .8f, .42f, .9f));
        return new TdGame(12, 8, path, 7, 0, 235, 5, waves);
    }

    /** 第 3 关：更长的水晶折线让中间高地可以同时覆盖三段路。 */
    private static TdGame level03() {
        int[][] path = p(
                0,0, 0,1, 0,2, 0,3, 0,4, 0,5, 0,6, 0,7, 0,8, 0,9, 0,10, 0,11,
                1,11, 2,11, 2,10, 2,9, 2,8, 2,7, 2,6, 2,5, 2,4,
                3,4, 4,4, 4,5, 4,6, 4,7, 4,8, 4,9,
                5,9, 6,9, 6,8, 6,7, 6,6, 6,5, 6,4, 6,3, 6,2,
                7,2, 8,2, 8,1, 8,0);
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(w(MonsterType.FAST, 7, .52f, .45f, 1f, 1f));
        waves.add(w(MonsterType.SWARM, 13, .28f, .4f, 1.05f, 1f));
        waves.add(mix(0, 8, .58f, .45f, 1.08f, 1f, MonsterType.NORMAL, MonsterType.FLY));
        waves.add(w(MonsterType.SHIELD, 5, .85f, .5f, 1.08f, 1f));
        waves.add(mix(0, 8, .6f, .5f, 1.12f, 1f, MonsterType.HEALER, MonsterType.TANK));
        waves.add(w(MonsterType.FLY, 7, .55f, .45f, 1.15f, 1.05f));
        waves.add(mix(0, 12, .36f, .45f, 1.15f, 1.05f, MonsterType.SWARM, MonsterType.FAST));
        waves.add(w(MonsterType.TANK, 4, 1.3f, .55f, 1.25f, .95f));
        waves.add(mix(0, 9, .5f, .5f, 1.2f, 1.05f, MonsterType.SHIELD, MonsterType.HEALER, MonsterType.FAST));
        waves.add(w(MonsterType.BOSS, 1, 0f, .8f, .55f, .88f));
        return new TdGame(12, 9, path, 8, 0, 260, 6, waves);
    }

    /** 第 4 关：两条入口在蛋蛋前汇合。波次明确标记路线，奖励观察和预布防。 */
    private static TdGame level04() {
        int[][] north = p(
                0,0, 0,1, 0,2, 0,3, 0,4, 0,5,
                1,5, 2,5, 3,5, 3,6, 3,7, 3,8, 3,9,
                4,9, 4,8, 4,7, 4,6);
        int[][] south = p(
                8,11, 8,10, 8,9, 8,8, 8,7, 7,7, 6,7,
                6,6, 6,5, 6,4, 6,3, 5,3, 5,4, 5,5, 5,6, 4,6);
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(w(0, MonsterType.NORMAL, 7, .7f, .4f, 1f, 1f));
        waves.add(w(1, MonsterType.FAST, 7, .5f, .45f, 1f, 1f));
        waves.add(mix(0, 9, .5f, .45f, 1.08f, 1f, MonsterType.FLY, MonsterType.NORMAL));
        waves.add(mix(1, 10, .34f, .45f, 1.08f, 1f, MonsterType.SWARM, MonsterType.FAST));
        waves.add(w(0, MonsterType.SHIELD, 5, .8f, .5f, 1.1f, 1f));
        waves.add(mix(1, 7, .65f, .5f, 1.15f, 1f, MonsterType.HEALER, MonsterType.TANK));
        waves.add(mix(0, 10, .45f, .45f, 1.18f, 1.05f, MonsterType.FAST, MonsterType.SHIELD));
        waves.add(mix(1, 9, .55f, .5f, 1.22f, 1f, MonsterType.FLY, MonsterType.HEALER, MonsterType.NORMAL));
        waves.add(w(0, MonsterType.TANK, 4, 1.25f, .55f, 1.28f, .92f));
        waves.add(w(1, MonsterType.BOSS, 1, 0f, .8f, .62f, .85f));
        return new TdGame(12, 9, new int[][][] { north, south }, 4, 6, 285, 6, waves);
    }

    /** 第 5 关：长折返终局，要求用范围伤害清群并在后段集火 Boss。 */
    private static TdGame level05() {
        int[][] path = p(
                0,0, 0,1, 0,2, 0,3, 0,4, 0,5, 0,6, 0,7, 0,8, 0,9, 0,10, 0,11, 0,12,
                1,12, 2,12, 2,11, 2,10, 2,9, 2,8, 2,7,
                3,7, 4,7, 4,8, 4,9, 4,10, 4,11,
                5,11, 6,11, 6,10, 6,9, 6,8, 6,7, 6,6, 6,5, 6,4,
                7,4, 8,4, 8,3, 8,2, 8,1, 8,0);
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(mix(0, 10, .48f, .4f, 1f, 1f, MonsterType.NORMAL, MonsterType.FAST));
        waves.add(w(MonsterType.SWARM, 16, .25f, .4f, 1.06f, 1f));
        waves.add(mix(0, 10, .48f, .45f, 1.1f, 1f, MonsterType.FLY, MonsterType.FAST));
        waves.add(mix(0, 8, .62f, .5f, 1.12f, 1f, MonsterType.SHIELD, MonsterType.HEALER));
        waves.add(w(MonsterType.TANK, 5, 1.1f, .55f, 1.2f, .95f));
        waves.add(mix(0, 14, .32f, .45f, 1.14f, 1.05f, MonsterType.SWARM, MonsterType.FAST, MonsterType.FLY));
        waves.add(mix(0, 9, .56f, .5f, 1.2f, 1f, MonsterType.TANK, MonsterType.HEALER, MonsterType.SHIELD));
        waves.add(w(MonsterType.FLY, 9, .5f, .45f, 1.25f, 1.05f));
        waves.add(mix(0, 12, .42f, .5f, 1.25f, 1.05f, MonsterType.SHIELD, MonsterType.FAST));
        waves.add(w(MonsterType.TANK, 5, 1.0f, .55f, 1.35f, .92f));
        waves.add(mix(0, 12, .34f, .5f, 1.3f, 1.08f, MonsterType.SWARM, MonsterType.FLY, MonsterType.HEALER));
        waves.add(w(MonsterType.BOSS, 1, 0f, .9f, .78f, .84f));
        return new TdGame(13, 9, path, 8, 0, 310, 7, waves);
    }

    private static TdGame.Wave w(MonsterType type, int count, float interval, float delay,
                                 float hpMul, float speedMul) {
        return new TdGame.Wave(type, count, interval, delay, hpMul, speedMul);
    }

    private static TdGame.Wave w(int route, MonsterType type, int count, float interval, float delay,
                                 float hpMul, float speedMul) {
        return mix(route, count, interval, delay, hpMul, speedMul, type);
    }

    private static TdGame.Wave mix(int route, int count, float interval, float delay,
                                   float hpMul, float speedMul, MonsterType... types) {
        return new TdGame.Wave(types, route, count, interval, delay, hpMul, speedMul);
    }

    private static int[][] p(int... coordinates) {
        if (coordinates.length == 0 || coordinates.length % 2 != 0) {
            throw new IllegalArgumentException("path coordinates must be row/col pairs");
        }
        int[][] path = new int[coordinates.length / 2][2];
        for (int i = 0; i < coordinates.length; i += 2) {
            path[i / 2][0] = coordinates[i];
            path[i / 2][1] = coordinates[i + 1];
        }
        return path;
    }
}
