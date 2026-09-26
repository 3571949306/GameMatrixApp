package com.gamecenter.app.chinesechess;

import java.util.ArrayList;
import java.util.List;

/**
 * 对局复盘 AI 标注器：对回放中的每一步做本地启发式评估，生成三档标注与中文短评。
 *
 * <p>设计约束：
 * <ul>
 *   <li>纯 Java、可脱离 Android 单测：输入输出均为纯 Java 类型（快照棋盘数组、
 *       走法坐标、阵营序号），复用回归测试的 javac 直跑管线；</li>
 *   <li>不联网、不引入引擎依赖、不做深层搜索：所有判定复用规则层
 *       {@link ChineseChessGame} 的公开接口（loadEndgamePosition 装载、
 *       getAllMoves 合法着法枚举、isInCheck 将军检测），不复制第三份规则实现；</li>
 *   <li>标注口径（1 层贪心启发式，保持简单可解释）：
 *       <ol>
 *         <li>吃子判定：落点在前一快照有对方棋子 => 记录被吃子价值；</li>
 *         <li>反吃风险：走后扫描对方全部合法着法，看落点子能否立即被吃回；
 *             净得子（吃子价值 - 被反吃子价值）&gt;0 为好棋、&lt;0 为疑问手、=0 为普通；</li>
 *         <li>将军判定：走后对方被将军；无吃无险的安静着若构成将军则升为好棋；</li>
 *         <li>擒将（吃掉将/帅）即终局获胜，直接判好棋。</li>
 *       </ol></li>
 * </ul>
 *
 * <p>棋子交换价值表（启发式定档用，非棋力评分）：车 9、炮 4.5、马 4、相/仕 2、
 * 兵/卒未过河 0.5、已过河 1（按所在行判定：红方过河即 y&le;4，黑方过河即 y&ge;5）；
 * 将/帅 100——将不参与常规交换计价，擒将即终局，由专门分支处理，
 * 100 仅作为表项保留以表明压倒终局的地位。
 *
 * <p>线程约定：实例内部复用同一临时棋局，非线程安全；整局标注
 * {@link #annotateGame(ChineseChessReplay)} 需在单一线程内完成
 * （Fragment 在后台线程一次性计算）。
 */
public final class ChineseChessReviewAnnotator {

    /** 标注三档：GOOD=好棋、NEUTRAL=普通、BLUNDER=疑问手。 */
    public enum Grade { GOOD, NEUTRAL, BLUNDER }

    /** 单步标注结果：档位 + 中文短评（不超过 10 字）。 */
    public static final class Annotation {
        /** 标注档位。 */
        public final Grade grade;
        /** 中文短评（不超过 10 字，如「吃马得子」「将军！」「车被白吃，亏」）。 */
        public final String comment;

        Annotation(Grade grade, String comment) {
            this.grade = grade;
            this.comment = comment;
        }
    }

    /** 结构异常输入的兜底标注（快照/走法数据不可评估时按普通处理，不抛异常）。 */
    private static final Annotation FALLBACK = new Annotation(Grade.NEUTRAL, "正常");

    /** 临时棋局：装载走后盘面做将军检测与对方着法扫描，不承载真实对局。 */
    private final ChineseChessGame scratch = new ChineseChessGame();

    /**
     * 对比相邻快照推演着法起点/终点（与回放渲染标注上一着共用同一实现）。
     *
     * @param before 走子前快照
     * @param after  走子后快照
     * @return [fromX, fromY, toX, toY]，无法推断时 null
     */
    public static int[] diffMove(int[][] before, int[][] after) {
        if (before == null || after == null) return null;
        int fromX = -1, fromY = -1, toX = -1, toY = -1;
        int rows = Math.min(before.length, after.length);
        for (int y = 0; y < rows; y++) {
            int cols = Math.min(before[y].length, after[y].length);
            for (int x = 0; x < cols; x++) {
                if (before[y][x] == after[y][x]) continue;
                if (after[y][x] == 0) { // 走子后清空 => 起点
                    fromX = x;
                    fromY = y;
                } else { // 走子后落位（含吃子）=> 终点
                    toX = x;
                    toY = y;
                }
            }
        }
        if (fromX < 0 || toX < 0) return null;
        return new int[]{fromX, fromY, toX, toY};
    }

    /**
     * 对整局回放逐步生成标注列表。
     *
     * <p>下标 i-1 对应第 i 步（快照 i-1 → i）；走子方取走子前快照的
     * snapshotSide（该局面的走子方即该步走子方）。
     *
     * @param replay 对局回放记录器
     * @return 与「快照数 - 1」等长的标注列表；replay 为 null 时返回空列表
     */
    public List<Annotation> annotateGame(ChineseChessReplay replay) {
        int total = replay == null ? 0 : replay.size() - 1;
        List<Annotation> result = new ArrayList<>(Math.max(0, total));
        for (int i = 1; i <= total; i++) {
            int[][] before = replay.snapshot(i - 1);
            int[][] after = replay.snapshot(i);
            result.add(annotateMove(before, after, diffMove(before, after),
                    replay.snapshotSide(i - 1)));
        }
        return result;
    }

    /**
     * 评估单步着法（启发式三档标注）。
     *
     * @param before    走子前盘面（约定同 getBoardAsIntArray）
     * @param after     走子后盘面
     * @param move      [fromX, fromY, toX, toY]
     * @param moverSide 走子方：0=红、1=黑
     * @return 标注（档位 + 短评）
     */
    public Annotation annotateMove(int[][] before, int[][] after, int[] move, int moverSide) {
        if (before == null || after == null || move == null || move.length < 4
                || !onBoard(before, move[0], move[1]) || !onBoard(before, move[2], move[3])
                || !onBoard(after, move[0], move[1]) || !onBoard(after, move[2], move[3])) {
            return FALLBACK;
        }
        int fromX = move[0], fromY = move[1], toX = move[2], toY = move[3];
        int movedCode = after[toY][toX];
        int capturedCode = before[toY][toX];
        if (movedCode == 0) return FALLBACK;

        // 擒将即终局：直接判好棋（走后盘面缺将无法装载，也不再需要反吃扫描）。
        if (capturedCode == 1 || capturedCode == -1) {
            return new Annotation(Grade.GOOD, "擒将获胜");
        }

        double capturedValue = capturedCode == 0 ? 0 : pieceValue(capturedCode, toX, toY);
        double movedValue = pieceValue(movedCode, toX, toY);

        // 1 层贪心反吃扫描 + 将军判定：装载走后盘面，枚举对方全部合法着法。
        // moverSide 非法或盘面异常装载失败时按无反吃/无将军降级，仅以吃子信息定档。
        boolean recapturable = false;
        boolean isCheck = false;
        int opponentOrdinal = 1 - moverSide;
        if (opponentOrdinal == 0 || opponentOrdinal == 1) {
            ChineseChessGame.Side opponent =
                    opponentOrdinal == 0 ? ChineseChessGame.Side.RED : ChineseChessGame.Side.BLACK;
            if (scratch.loadEndgamePosition(ChineseChessReplay.toEndgameSpec(after), opponentOrdinal)) {
                isCheck = scratch.isInCheck(opponent);
                for (int[] reply : scratch.getAllMoves(opponent)) {
                    if (reply[2] == toX && reply[3] == toY) {
                        recapturable = true;
                        break;
                    }
                }
            }
        }

        Grade grade;
        String comment;
        if (recapturable) {
            if (capturedValue > 0) {
                double net = capturedValue - movedValue;
                if (net > 0) {
                    grade = Grade.GOOD;
                    comment = "以" + pieceName(movedCode) + "换" + pieceName(capturedCode) + "，赚";
                } else if (net < 0) {
                    grade = Grade.BLUNDER;
                    comment = "以" + pieceName(movedCode) + "换" + pieceName(capturedCode) + "，亏";
                } else {
                    grade = Grade.NEUTRAL;
                    comment = "兑子";
                }
            } else {
                grade = Grade.BLUNDER;
                comment = pieceName(movedCode) + "被白吃，亏";
            }
        } else if (capturedValue > 0) {
            grade = Grade.GOOD;
            comment = "吃" + pieceName(capturedCode) + "得子";
        } else {
            grade = Grade.NEUTRAL;
            comment = "平稳";
        }
        if (isCheck && grade == Grade.NEUTRAL && "平稳".equals(comment)) {
            // 无吃无险的安静着若构成将军，升为好棋并改写短评。
            grade = Grade.GOOD;
            comment = "将军！";
        }
        return new Annotation(grade, comment);
    }

    /**
     * 棋子交换价值（启发式定档用）：车9、炮4.5、马4、相/仕2、
     * 兵/卒未过河0.5、已过河1；将/帅100，不参与常规交换（擒将走专门分支）。
     */
    private static double pieceValue(int code, int x, int y) {
        switch (Math.abs(code)) {
            case 1: return 100;
            case 2:
            case 3: return 2;
            case 4: return 4;
            case 5: return 9;
            case 6: return 4.5;
            case 7: {
                boolean crossed = code > 0 ? y <= 4 : y >= 5;
                return crossed ? 1 : 0.5;
            }
            default: return 0;
        }
    }

    /** 棋子单字简称（短评用，红黑同名）。 */
    private static String pieceName(int code) {
        int type = Math.abs(code);
        if (type < 1 || type > 7) return "子";
        String[] names = {"", "将", "仕", "相", "马", "车", "炮", "兵"};
        return names[type];
    }

    /** 坐标是否落在棋盘数组范围内。 */
    private static boolean onBoard(int[][] board, int x, int y) {
        return board != null && y >= 0 && y < board.length
                && x >= 0 && x < board[y].length;
    }
}
