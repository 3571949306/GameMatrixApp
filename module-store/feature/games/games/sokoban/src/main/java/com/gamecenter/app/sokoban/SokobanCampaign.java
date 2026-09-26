package com.gamecenter.app.sokoban;

/**
 * 推箱子战役模式关卡数据（纯 Java，无 Android 依赖）。
 *
 * <p>战役共 3 章 × 3 关，均为战役独享全新关卡，不复用
 * {@link SokobanGame} 内置 15 关。每关以 {@link SokobanLevelCodec}
 * 单行关卡码描述，并附带一份已知解（玩家每步位移），供回归测试
 * 逐关验证可解性，也可供 UI 层做演示或提示。</p>
 *
 * <p>解序列方向约定：{-1,0} 上、{1,0} 下、{0,-1} 左、{0,1} 右。
 * 可解性铁证见 tests/SokobanRegressionTest.java 的 T9 用例：
 * 逐步执行解序列并断言通关。全部关卡外墙全围、玩家/箱/目标均在
 * 墙内，箱数与目标数一致（1 或 2 个箱子）。</p>
 */
public final class SokobanCampaign {

    private SokobanCampaign() {
    }

    /** 战役关卡：章节归属、关卡码与已知解。 */
    public static final class CampaignLevel {
        /** 所属章节 id（1..3）。 */
        public final int chapter;
        /** 章内序号（1..3）。 */
        public final int indexInChapter;
        /** 关卡名（中文）。 */
        public final String name;
        /** {@link SokobanLevelCodec} 单行关卡码。 */
        public final String code;
        /** 已知解：每步 {dr, dc}，依次执行即通关。 */
        public final int[][] solution;

        public CampaignLevel(int chapter, int indexInChapter, String name,
                String code, int[][] solution) {
            this.chapter = chapter;
            this.indexInChapter = indexInChapter;
            this.name = name;
            this.code = code;
            this.solution = solution;
        }
    }

    /** 战役章节。 */
    public static final class Chapter {
        /** 章节 id（1..3）。 */
        public final int id;
        /** 章节名。 */
        public final String name;
        /** 章节全部通关的金币奖励。 */
        public final int rewardCoins;
        /** 章内关卡（3 关）。 */
        public final CampaignLevel[] levels;

        public Chapter(int id, String name, int rewardCoins, CampaignLevel[] levels) {
            this.id = id;
            this.name = name;
            this.rewardCoins = rewardCoins;
            this.levels = levels;
        }
    }

    /** 第一章「新手试炼」：单箱直推入门（6x6～7x7）。 */
    private static final CampaignLevel[] CHAPTER_ONE_LEVELS = {
        // 试炼一：玩家在箱后，向右连推两格入目标（2 步）。
        new CampaignLevel(1, 1, "试炼一",
                "######/#@$-.#/#----#/#----#/#----#/######",
                new int[][]{{0, 1}, {0, 1}}),
        // 试炼二：绕到箱子左侧，再向右连推两格入目标（3 步）。
        new CampaignLevel(1, 2, "试炼二",
                "######/#----#/#-$-.#/#@---#/#----#/######",
                new int[][]{{-1, 0}, {0, 1}, {0, 1}}),
        // 试炼三：玩家在箱顶，向下连推三格入目标（3 步）。
        new CampaignLevel(1, 3, "试炼三",
                "#######/#--@--#/#--$--#/#-----#/#-----#/#--.--#/#######",
                new int[][]{{1, 0}, {1, 0}, {1, 0}}),
    };

    /** 第二章「稳步推进」：单箱转折与两箱短序（7x7～8x8）。 */
    private static final CampaignLevel[] CHAPTER_TWO_LEVELS = {
        // 试炼四：绕到箱顶向下连推两格，再绕到箱左向右连推两格（L 形，8 步）。
        new CampaignLevel(2, 1, "试炼四",
                "#######/#@----#/#--$--#/#-----#/#----.#/#-----#/#######",
                new int[][]{{0, 1}, {0, 1}, {1, 0}, {1, 0}, {0, -1}, {1, 0}, {0, 1}, {0, 1}}),
        // 试炼五：上排箱子向右连推三格，再绕到中央箱顶向下连推两格（两箱，7 步）。
        new CampaignLevel(2, 2, "试炼五",
                "#######/#@$--.#/#-----#/#--$--#/#-----#/#--.--#/#######",
                new int[][]{{0, 1}, {0, 1}, {0, 1}, {1, 0}, {0, -1}, {1, 0}, {1, 0}}),
        // 试炼六：向右连推三格后绕到顶部，沿右侧通道向下连推四格（双段内墙绕行，11 步）。
        new CampaignLevel(2, 3, "试炼六",
                "########/#@-----#/#--$---#/#---#--#/#---#--#/#------#/#-----.#/########",
                new int[][]{{1, 0}, {0, 1}, {0, 1}, {0, 1}, {0, 1}, {-1, 0}, {0, 1},
                        {1, 0}, {1, 0}, {1, 0}, {1, 0}}),
    };

    /** 第三章「大师之路」：两箱协同 + 绕行（8x8）。 */
    private static final CampaignLevel[] CHAPTER_THREE_LEVELS = {
        // 试炼七：左箱沿左侧通道向下连推四格，再绕右侧通道把另一箱向下连推两格（13 步）。
        new CampaignLevel(3, 1, "试炼七",
                "########/#@-----#/#-$----#/#--#-$-#/#--#---#/#----.-#/#-.----#/########",
                new int[][]{{0, 1}, {1, 0}, {1, 0}, {1, 0}, {1, 0}, {0, 1}, {0, 1},
                        {-1, 0}, {-1, 0}, {-1, 0}, {0, 1}, {1, 0}, {1, 0}}),
        // 试炼八：先推右箱向下三格入位，再绕左侧通道回顶推左箱向下三格（16 步）。
        new CampaignLevel(3, 2, "试炼八",
                "########/#--@---#/#-$--$-#/#--##--#/#------#/#-.--.-#/#------#/########",
                new int[][]{{0, 1}, {0, 1}, {1, 0}, {1, 0}, {1, 0}, {0, -1}, {0, -1},
                        {0, -1}, {-1, 0}, {0, -1}, {-1, 0}, {-1, 0}, {0, 1},
                        {1, 0}, {1, 0}, {1, 0}}),
        // 试炼九：右箱右推两格后绕顶向下三格入位，回身把左箱横推一格再向下连推两格（18 步）。
        new CampaignLevel(3, 3, "试炼九",
                "########/#@-----#/#---$--#/#--##--#/#-$----#/#-----.#/#.-----#/########",
                new int[][]{{1, 0}, {0, 1}, {0, 1}, {0, 1}, {0, 1}, {-1, 0}, {0, 1},
                        {1, 0}, {1, 0}, {1, 0}, {0, -1}, {0, -1}, {0, -1}, {0, -1},
                        {-1, 0}, {0, -1}, {1, 0}, {1, 0}}),
    };

    /** 战役章节表（3 章，奖励 30/40/50 金币）。 */
    public static final Chapter[] CHAPTERS = {
        new Chapter(1, "新手试炼", 30, CHAPTER_ONE_LEVELS),
        new Chapter(2, "稳步推进", 40, CHAPTER_TWO_LEVELS),
        new Chapter(3, "大师之路", 50, CHAPTER_THREE_LEVELS),
    };

    /** 战役总关数。 */
    public static int totalLevels() {
        int total = 0;
        for (Chapter ch : CHAPTERS) {
            total += ch.levels.length;
        }
        return total;
    }

    /**
     * 按章节 id 取章节。
     *
     * @param id 章节 id（1..3）
     * @return 对应章节；id 不存在时返回 {@code null}
     */
    public static Chapter chapter(int id) {
        for (Chapter ch : CHAPTERS) {
            if (ch.id == id) {
                return ch;
            }
        }
        return null;
    }
}
