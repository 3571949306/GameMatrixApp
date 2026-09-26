package com.gamecenter.app.chinesechess;

/**
 * 内置残局挑战关卡数据（纯 Java，无 Android 依赖，可被回归测试直接编译运行）。
 * <p>
 * 关卡为精选的"红方优势残局定式"：红方先行，目标是将死黑方 AI。
 * 局面规格经 {@link ChineseChessGame#loadEndgamePosition(int[][], int)} 全量校验，
 * 非法规格会在装载时被拒绝，不会进入对局。
 * <p>
 * 棋子编码：typeOrdinal 对应 ChineseChessGame.PieceType（0=将/帅, 1=仕/士,
 * 2=相/象, 3=马, 4=车, 5=炮, 6=兵/卒）；sideOrdinal 0=红、1=黑；
 * 坐标 x 为列(0..8)、y 为行(0..9)，红方在下方。
 */
public final class ChineseChessEndgames {

    /** 残局关卡规格。 */
    public static final class EndgameSpec {
        /** 关卡稳定 id（用于通关进度记录）。 */
        public final int id;
        /** 关卡名称（残局定式名）。 */
        public final String name;
        /** 难度标签（入门/进阶/挑战）。 */
        public final String tag;
        /** 玩法说明。 */
        public final String description;
        /** 黑方防守 AI 难度（1~4）。 */
        public final int aiDifficulty;
        /** 棋子规格，每个元素 {typeOrdinal, sideOrdinal, x, y}。 */
        public final int[][] pieces;

        EndgameSpec(int id, String name, String tag, String description,
                    int aiDifficulty, int[][] pieces) {
            this.id = id;
            this.name = name;
            this.tag = tag;
            this.description = description;
            this.aiDifficulty = aiDifficulty;
            this.pieces = pieces;
        }
    }

    private static final int GENERAL = 0, ADVISOR = 1, ELEPHANT = 2,
            HORSE = 3, CHARIOT = 4, CANNON = 5, SOLDIER = 6;
    private static final int RED = 0, BLACK = 1;

    /** 全部关卡，按由浅入深排列。 */
    public static final EndgameSpec[] LEVELS = {
        new EndgameSpec(1, "双车错", "入门",
                "双车交错将军，黑方仅剩孤士，练习基本杀法。",
                2, new int[][]{
                    {GENERAL, RED, 3, 9},
                    {CHARIOT, RED, 2, 5},
                    {CHARIOT, RED, 6, 5},
                    {GENERAL, BLACK, 4, 0},
                    {ADVISOR, BLACK, 4, 1},
                    {SOLDIER, BLACK, 8, 3},
                }),
        new EndgameSpec(2, "重炮", "入门",
                "双炮必胜双士：一炮镇中路作架，一炮沉底将军。",
                2, new int[][]{
                    {GENERAL, RED, 3, 9},
                    {CANNON, RED, 2, 7},
                    {CANNON, RED, 2, 4},
                    {GENERAL, BLACK, 4, 0},
                    {ADVISOR, BLACK, 3, 0},
                    {ADVISOR, BLACK, 5, 0},
                }),
        new EndgameSpec(3, "马炮擒王", "进阶",
                "黑方只剩孤将，练习马后炮的组杀手法。",
                3, new int[][]{
                    {GENERAL, RED, 3, 9},
                    {HORSE, RED, 4, 3},
                    {CANNON, RED, 0, 8},
                    {GENERAL, BLACK, 4, 0},
                }),
        new EndgameSpec(4, "车马联攻", "进阶",
                "车马对士象全，练习用马控位、用车绝杀。",
                3, new int[][]{
                    {GENERAL, RED, 4, 9},
                    {ADVISOR, RED, 4, 8},
                    {CHARIOT, RED, 3, 5},
                    {HORSE, RED, 5, 5},
                    {GENERAL, BLACK, 4, 0},
                    {ADVISOR, BLACK, 4, 1},
                    {ELEPHANT, BLACK, 6, 0},
                    {SOLDIER, BLACK, 0, 3},
                }),
        new EndgameSpec(5, "车炮仕相", "进阶",
                "车炮对士象全，仕相完整无需顾虑防守，专注进攻。",
                3, new int[][]{
                    {GENERAL, RED, 4, 9},
                    {ADVISOR, RED, 4, 8},
                    {ELEPHANT, RED, 4, 7},
                    {CHARIOT, RED, 2, 5},
                    {CANNON, RED, 6, 5},
                    {GENERAL, BLACK, 4, 0},
                    {ADVISOR, BLACK, 4, 1},
                    {ELEPHANT, BLACK, 4, 2},
                    {SOLDIER, BLACK, 0, 3},
                    {SOLDIER, BLACK, 8, 3},
                }),
        new EndgameSpec(6, "双车破士象全", "挑战",
                "黑方士象齐全且防守 AI 达到大师档，需要扎实的基本功。",
                4, new int[][]{
                    {GENERAL, RED, 3, 9},
                    {CHARIOT, RED, 2, 5},
                    {CHARIOT, RED, 6, 5},
                    {ELEPHANT, RED, 4, 7},
                    {GENERAL, BLACK, 4, 0},
                    {ADVISOR, BLACK, 3, 0},
                    {ADVISOR, BLACK, 5, 0},
                    {ELEPHANT, BLACK, 2, 0},
                    {ELEPHANT, BLACK, 6, 0},
                    {SOLDIER, BLACK, 4, 3},
                }),
    };

    private ChineseChessEndgames() {
    }

    /** 关卡总数。 */
    public static int count() {
        return LEVELS.length;
    }

    /** 按 id 查找关卡，找不到返回 null。 */
    public static EndgameSpec findById(int id) {
        for (EndgameSpec spec : LEVELS) {
            if (spec.id == id) return spec;
        }
        return null;
    }
}
