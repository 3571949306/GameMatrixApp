import com.gamecenter.app.chinesechess.ChineseChessGame;
import com.gamecenter.app.chinesechess.ChineseChessAI;
import com.gamecenter.app.chinesechess.ChineseChessEndgames;
import com.gamecenter.app.chinesechess.ChineseChessReplay;
import com.gamecenter.app.chinesechess.ChineseChessReviewAnnotator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Method;

/**
 * 中国象棋 AI 逻辑回归测试（纯 Java，javac 即可运行，无需 Android 运行时）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>各棋子规则：蹩马腿、塞象眼、飞将（含"送将"被拒）；</li>
 *   <li>集中落子闸门 {@code isMoveLegal}/{@code commitMove}：非法着法一律拒绝（含原"蹩腿马吃将"bug 类）；</li>
 *   <li>终局分类：将死、困毙（象棋中困毙亦判负）；</li>
 *   <li>AI 着法合法性：随机对局上百手，AI 返回的每一步步都必须被中央闸门接受
 *       （同时作为 AI 规则实现与裁判规则实现的"交叉校验"，可捕获两套规则的分叉）。</li>
 * </ul>
 *
 * <p>运行：见同目录下 run_tests.sh / run_tests.bat。</p>
 */
public class ChessRegressionTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    // ---- 便捷别名 ----
    static final ChineseChessGame.PieceType GENERAL = ChineseChessGame.PieceType.GENERAL;
    static final ChineseChessGame.PieceType ADVISOR = ChineseChessGame.PieceType.ADVISOR;
    static final ChineseChessGame.PieceType ELEPHANT = ChineseChessGame.PieceType.ELEPHANT;
    static final ChineseChessGame.PieceType HORSE = ChineseChessGame.PieceType.HORSE;
    static final ChineseChessGame.PieceType CHARIOT = ChineseChessGame.PieceType.CHARIOT;
    static final ChineseChessGame.PieceType CANNON = ChineseChessGame.PieceType.CANNON;
    static final ChineseChessGame.PieceType SOLDIER = ChineseChessGame.PieceType.SOLDIER;
    static final ChineseChessGame.Side RED = ChineseChessGame.Side.RED;
    static final ChineseChessGame.Side BLACK = ChineseChessGame.Side.BLACK;

    /** 新建一局并清空棋盘，仅在 (9,3)/(0,5) 各放一个将帅（不同列，不触发飞将）。 */
    static ChineseChessGame newGame() {
        ChineseChessGame g = new ChineseChessGame();
        ChineseChessGame.Piece[][] b = g.getBoard();
        for (int y = 0; y < 10; y++)
            for (int x = 0; x < 9; x++)
                b[y][x] = null;
        b[9][3] = new ChineseChessGame.Piece(GENERAL, RED, 3, 9);
        b[0][5] = new ChineseChessGame.Piece(GENERAL, BLACK, 5, 0);
        return g;
    }

    static void place(ChineseChessGame g, ChineseChessGame.PieceType t, ChineseChessGame.Side s, int x, int y) {
        g.getBoard()[y][x] = new ChineseChessGame.Piece(t, s, x, y);
    }

    // ====================================================================
    // 1. 蹩马腿（Horse leg）
    // ====================================================================
    static void testHorseLeg() {
        System.out.println("[T1] 蹩马腿");
        ChineseChessGame g = newGame();
        place(g, HORSE, RED, 4, 5); // 红马在 (x=4,y=5)

        // 八条日字均无阻挡时全部合法
        int[][] moves = {{6,6},{6,4},{2,6},{2,4},{5,7},{3,7},{5,3},{3,3}};
        for (int[] m : moves) {
            check("马无阻挡可走 (" + m[0] + "," + m[1] + ")", g.isMoveLegal(4, 5, m[0], m[1]));
        }

        // 在马腿 (5,5)（朝 (6,6) 方向的直行第一格）放己方兵，则 (6,6) 不可走
        place(g, SOLDIER, RED, 5, 5);
        check("马腿被己方兵阻挡 => (6,6) 非法", !g.isMoveLegal(4, 5, 6, 6));
        // 其余未被阻挡的方向仍合法
        check("未阻挡方向 (2,6) 仍合法", g.isMoveLegal(4, 5, 2, 6));

        // commitMove 必须拒绝该非法着法
        check("commitMove 拒绝蹩腿马着法", g.commitMove(4, 5, 6, 6) == null);
    }

    // ====================================================================
    // 2. 塞象眼（Elephant eye）
    // ====================================================================
    static void testElephantEye() {
        System.out.println("[T2] 塞象眼");
        ChineseChessGame g = newGame();
        place(g, ELEPHANT, RED, 2, 7); // 红相在 (x=2,y=7)
        // 田字四角：(4,9),(4,5),(0,9),(0,5)
        check("相可走 (4,9)", g.isMoveLegal(2, 7, 4, 9));
        check("相可走 (0,5)", g.isMoveLegal(2, 7, 0, 5));
        // 在象眼 (3,8)（朝 (4,9) 方向的田字中心）放己方兵，则 (4,9) 不可走
        place(g, SOLDIER, RED, 3, 8);
        check("象眼被挡 => (4,9) 非法", !g.isMoveLegal(2, 7, 4, 9));
        check("commitMove 拒绝塞象眼着法", g.commitMove(2, 7, 4, 9) == null);
    }

    // ====================================================================
    // 3. 飞将（Flying general）含：吃将合法、送将（暴露将帅）非法
    // ====================================================================
    static void testFlyingGeneral() {
        System.out.println("[T3] 飞将");
        // 3a. 红帅(9,4) 与 黑将(0,4) 同列中间无子 => 红帅可飞将吃黑将（合法）
        ChineseChessGame g = newGame();
        g.getBoard()[9][3] = null;
        g.getBoard()[0][5] = null;
        place(g, GENERAL, RED, 4, 9);
        place(g, GENERAL, BLACK, 4, 0);
        check("飞将吃将 (4,9)->(4,0) 合法", g.isMoveLegal(4, 9, 4, 0));
        check("commitMove 接受飞将吃将", g.commitMove(4, 9, 4, 0) != null);

        // 3b. 红车在 (4,5) 挡住两将之间；红走动车离开第4列会暴露将帅 => 非法（送将）
        ChineseChessGame g2 = newGame();
        g2.getBoard()[9][3] = null;
        g2.getBoard()[0][5] = null;
        place(g2, GENERAL, RED, 4, 9);
        place(g2, GENERAL, BLACK, 4, 0);
        place(g2, CHARIOT, RED, 4, 5); // 挡在中间
        // 初始红帅未被将（红车挡住）
        check("挡车在位时红帅未被将", !g2.isInCheck(RED));
        // 红车横向离开第4列 -> 暴露将帅 => 非法
        check("动车暴露将帅 => (4,5)->(3,5) 非法", !g2.isMoveLegal(4, 5, 3, 5));
        // 红车沿第4列移动（仍挡住）=> 合法
        check("动车仍在第4列 => (4,5)->(4,6) 合法", g2.isMoveLegal(4, 5, 4, 6));
        check("commitMove 拒绝送将着法", g2.commitMove(4, 5, 3, 5) == null);
    }

    // ====================================================================
    // 4. 原 bug 回归：蹩腿马"吃将"必须被拒（核心防线）
    // ====================================================================
    static void testBlockedHorseCapturesGeneral() {
        System.out.println("[T4] 蹩腿马吃将 回归（原 bug 类）");
        ChineseChessGame g = newGame();
        g.getBoard()[0][5] = null; // 清除 newGame 默认黑将，避免双将并存
        // 黑将在 (x=4,y=0)；红马在 (x=2,y=1)，本可跳到 (4,0) 吃将，但马腿 (3,1) 被己方兵挡住
        place(g, GENERAL, BLACK, 4, 0);
        place(g, HORSE, RED, 2, 1);
        place(g, SOLDIER, RED, 3, 1); // 马腿阻挡
        check("蹩腿马吃将 (2,1)->(4,0) 非法", !g.isMoveLegal(2, 1, 4, 0));
        check("commitMove 拒绝蹩腿马吃将", g.commitMove(2, 1, 4, 0) == null);

        // 移除阻挡后该吃将合法（证明上面的"非法"是腿被挡所致，而非恒假）
        g.getBoard()[1][3] = null; // 移除 (3,1) 的兵
        check("马腿清除后 (2,1)->(4,0) 合法", g.isMoveLegal(2, 1, 4, 0));
    }

    // ====================================================================
    // 5. commitMove 成功路径与状态流转
    // ====================================================================
    static void testCommitMoveSuccess() {
        System.out.println("[T5] commitMove 成功路径");
        ChineseChessGame g = newGame();
        place(g, CHARIOT, RED, 0, 9);
        int before = g.getMoveHistory().size();
        ChineseChessGame.MoveRecord rec = g.commitMove(0, 9, 0, 8);
        check("成功落子返回非 null", rec != null);
        check("走棋方已切换为黑方", g.getCurrentSide() == BLACK);
        check("moveHistory 增加一条", g.getMoveHistory().size() == before + 1);
        check("目标格已有棋子", g.getBoard()[8][0] != null && g.getBoard()[8][0].type == CHARIOT);
        check("起点格已清空", g.getBoard()[9][0] == null);
    }

    // ====================================================================
    // 6. 将死（Checkmate）
    // ====================================================================
    static void testCheckmate() {
        System.out.println("[T6] 将死");
        ChineseChessGame g = newGame();
        g.getBoard()[0][5] = null; // 清除 newGame 默认黑将，避免双将并存
        // 黑将在 (4,0)；三红车分别控制 x3/x4/x5 三列 => 将死
        place(g, GENERAL, BLACK, 4, 0);
        place(g, CHARIOT, RED, 4, 2);
        place(g, CHARIOT, RED, 3, 2);
        place(g, CHARIOT, RED, 5, 2);
        g.switchSide(); // 轮到黑方
        g.checkGameOver();
        check("黑方被将死 => 游戏结束", g.isGameOver());
        check("将死判红方胜", g.getWinner() == RED);
        check("将死时被将（isInCheck 黑）", g.isInCheck(BLACK));
        check("将死时黑方无合法着法", !g.hasLegalMoves(BLACK));
    }

    // ====================================================================
    // 7. 困毙（Stalemate，象棋中亦判负）
    // ====================================================================
    static void testStalemate() {
        System.out.println("[T7] 困毙");
        ChineseChessGame g = newGame();
        g.getBoard()[0][5] = null; // 清除 newGame 默认黑将，避免双将并存
        // 黑将在 (4,0)，未被将；但所有逃生格均被红方控制 => 困毙
        place(g, GENERAL, BLACK, 4, 0);
        place(g, CHARIOT, RED, 3, 2); // 控制 (3,0),(3,1)
        place(g, CHARIOT, RED, 5, 2); // 控制 (5,0),(5,1)
        place(g, HORSE, RED, 2, 0);   // 控制 (4,1)（不经将帅列，不送将）
        g.switchSide(); // 轮到黑方
        check("困毙时黑方未被将", !g.isInCheck(BLACK));
        check("困毙时黑方无合法着法", !g.hasLegalMoves(BLACK));
        g.checkGameOver();
        check("困毙 => 游戏结束", g.isGameOver());
        check("困毙判红方胜（象棋困毙亦负）", g.getWinner() == RED);
    }

    // ====================================================================
    // 8. AI 着法合法性（随机对局交叉校验）
    // ====================================================================
    static void testAiLegality() {
        System.out.println("[T8] AI 着法合法性（随机对局交叉校验）");
        ChineseChessGame g = new ChineseChessGame(); // 初始局面
        ChineseChessAI ai = new ChineseChessAI(2);   // 浅层搜索足以做规则交叉校验，且适合 CI
        int aiSide = 1; // 红先
        int plies = 40;
        int illegalCount = 0;
        int appliedCount = 0;

        for (int ply = 0; ply < plies && !g.isGameOver(); ply++) {
            int[][] board = g.getBoardAsIntArray();
            ai.setPositionHistory(g.getPositionHistory());
            int[] mv = ai.getBestMove(board, 2, aiSide);
            ChineseChessGame.Side side = (aiSide == 1) ? RED : BLACK;

            if (mv == null) {
                // AI 认为无着法：必须确实无合法着法（否则是 AI 漏着，属缺陷）
                boolean noLegal = g.getAllMoves(side).isEmpty();
                if (!noLegal) illegalCount++; // 实为 AI 漏着
                check("AI 返回 null 时确实无合法着法 (ply=" + ply + ")", noLegal);
                break;
            }

            // 中央闸门二次校验（AI 规则实现 vs 裁判规则实现 交叉校验）
            // AI 返回 [fromRow,fromCol,toRow,toCol]；裁判使用 [fromX,fromY,toX,toY]=[col,row,col,row]
            ChineseChessGame.MoveRecord rec = g.commitMove(mv[1], mv[0], mv[3], mv[2]);
            if (rec == null) {
                illegalCount++;
                System.out.println("    !! AI 非法着法 ply=" + ply + " side=" + side
                        + " move=" + mv[1] + "," + mv[0] + "->" + mv[3] + "," + mv[2]);
            } else {
                appliedCount++;
            }
            aiSide = -aiSide;
        }

        System.out.println("    应用着法数=" + appliedCount + "，非法着法数=" + illegalCount);
        check("AI 全程未产出任何非法着法", illegalCount == 0);
        check("AI 至少完成一定手数对局", appliedCount >= 20);
    }

    // ====================================================================
    // 9. 开局库只能用于精确初始局面，且每个随机候选必须合法
    // ====================================================================
    static void testOpeningBookGuard() throws Exception {
        System.out.println("[T9] 开局库精确盘面与合法性闸门");
        ChineseChessGame g = new ChineseChessGame();
        ChineseChessAI ai = new ChineseChessAI(1);
        Method getOpeningMove = ChineseChessAI.class.getDeclaredMethod(
                "getOpeningMove", int[][].class, int.class);
        getOpeningMove.setAccessible(true);

        boolean allLegal = true;
        for (int i = 0; i < 200; i++) {
            int[] move = (int[]) getOpeningMove.invoke(ai, g.getBoardAsIntArray(), 1);
            if (move == null || !g.isMoveLegal(move[1], move[0], move[3], move[2])) {
                allLegal = false;
                break;
            }
        }
        check("初始盘面随机 200 次开局着法全部合法", allLegal);

        check("测试前置红兵着法合法", g.commitMove(0, 6, 0, 5) != null);
        int[] nonInitialOpening = (int[]) getOpeningMove.invoke(ai, g.getBoardAsIntArray(), -1);
        check("非精确初始盘面不使用开局库", nonInitialOpening == null);
    }

    // ====================================================================
    // 10. 游戏层与 AI 的局面哈希必须逐位一致
    // ====================================================================
    static void testPositionHashConsistency() throws Exception {
        System.out.println("[T10] 局面哈希一致性");
        ChineseChessGame g = new ChineseChessGame();
        ChineseChessAI ai = new ChineseChessAI(1);
        Method computeHash = ChineseChessAI.class.getDeclaredMethod(
                "computePositionHash", int[][].class, int.class);
        computeHash.setAccessible(true);

        long initialAiHash = (long) computeHash.invoke(ai, g.getBoardAsIntArray(), 1);
        List<Long> history = g.getPositionHistory();
        check("初始局面哈希一致", history.get(history.size() - 1) == initialAiHash);

        check("红兵前进一步可提交", g.commitMove(0, 6, 0, 5) != null);
        long movedAiHash = (long) computeHash.invoke(ai, g.getBoardAsIntArray(), -1);
        history = g.getPositionHistory();
        check("走子后（含下一走棋方）哈希一致", history.get(history.size() - 1) == movedAiHash);
    }

    // ====================================================================
    // 11. 普通重复判和；只有同一方每步连续将军才判长将负
    // ====================================================================
    static void testRepetitionClassification() {
        System.out.println("[T11] 重复局面与长将分类");

        ChineseChessGame drawGame = newGame();
        place(drawGame, CHARIOT, RED, 0, 8);
        place(drawGame, CHARIOT, BLACK, 8, 1);
        int[][] quietCycle = {
                {0,8,0,7}, {8,1,8,2}, {0,7,0,8}, {8,2,8,1}
        };
        boolean quietMovesAccepted = true;
        quietLoop:
        for (int cycle = 0; cycle < 3; cycle++) {
            for (int[] m : quietCycle) {
                if (drawGame.commitMove(m[0], m[1], m[2], m[3]) == null) {
                    quietMovesAccepted = false;
                    break;
                }
                // 任一循环相位第三次出现即可触发三次重复，不必强行走完整个第三圈。
                if (drawGame.isGameOver()) break quietLoop;
            }
        }
        check("无将军循环的全部着法合法", quietMovesAccepted);
        check("三次普通重复判和", drawGame.isGameOver() && drawGame.getWinner() == null);

        ChineseChessGame perpetual = newGame();
        perpetual.getBoard()[0][5] = null;
        place(perpetual, GENERAL, BLACK, 4, 0);
        place(perpetual, CHARIOT, RED, 3, 1);
        int[][] checkingSequence = {
                {3,1,4,1},
                {4,0,5,0}, {4,1,5,1}, {5,0,4,0}, {5,1,4,1},
                {4,0,5,0}, {4,1,5,1}, {5,0,4,0}, {5,1,4,1}
        };
        boolean checkingMovesAccepted = true;
        for (int[] m : checkingSequence) {
            if (perpetual.commitMove(m[0], m[1], m[2], m[3]) == null) {
                checkingMovesAccepted = false;
                break;
            }
        }
        check("连续将军循环的全部着法合法", checkingMovesAccepted);
        check("红方长将判负、黑方获胜",
                perpetual.isGameOver() && perpetual.getWinner() == BLACK);
    }

    // ====================================================================
    // 12. AI 规则边界：生成飞将吃将；己方将缺失时不得继续走棋
    // ====================================================================
    @SuppressWarnings("unchecked")
    static void testAiGeneralBoundaries() throws Exception {
        System.out.println("[T12] AI 将帅边界");
        ChineseChessAI ai = new ChineseChessAI(1);
        Method generateLegalMoves = ChineseChessAI.class.getDeclaredMethod(
                "generateLegalMoves", int[][].class, int.class);
        generateLegalMoves.setAccessible(true);

        int[][] facing = new int[10][9];
        facing[9][4] = 1;
        facing[0][4] = -1;
        List<int[]> moves = (List<int[]>) generateLegalMoves.invoke(ai, facing, 1);
        boolean hasFlyingCapture = false;
        for (int[] move : moves) {
            if (move[0] == 9 && move[1] == 4 && move[2] == 0 && move[3] == 4) {
                hasFlyingCapture = true;
                break;
            }
        }
        check("AI 能生成飞将吃将", hasFlyingCapture);

        int[][] missingRedGeneral = new int[10][9];
        missingRedGeneral[0][4] = -1;
        missingRedGeneral[5][0] = 5;
        moves = (List<int[]>) generateLegalMoves.invoke(ai, missingRedGeneral, 1);
        check("己方将缺失时 AI 无合法着法", moves.isEmpty());
    }

    // ====================================================================
    // 13. 高难度真实搜索档位、回摆抑制和将区安全评估
    // ====================================================================
    static void testAiStrengthProfile() throws Exception {
        System.out.println("[T13] AI 棋力配置与通用决策修复");
        ChineseChessAI ai = new ChineseChessAI(3);
        ChineseChessGame initial = new ChineseChessGame();

        Method resolveDepth = ChineseChessAI.class.getDeclaredMethod(
                "resolveSearchDepth", int.class, int[][].class);
        resolveDepth.setAccessible(true);
        int highDepth = (int) resolveDepth.invoke(ai, 3, initial.getBoardAsIntArray());
        int masterOpeningDepth = (int) resolveDepth.invoke(ai, 4, initial.getBoardAsIntArray());
        check("高难度使用 depth 4（不再是原 depth 3）", highDepth == 4);
        check("大师开中局保持 depth 4 控制响应时间", masterOpeningDepth == 4);

        int[][] endgame = new int[10][9];
        endgame[9][4] = 1;
        endgame[0][4] = -1;
        endgame[5][4] = 7; // 避免将帅照面
        int masterEndgameDepth = (int) resolveDepth.invoke(ai, 4, endgame);
        check("大师残局提升到 depth 5", masterEndgameDepth == 5);

        List<int[]> recentOwnMoves = new ArrayList<>();
        recentOwnMoves.add(new int[]{2, 6, 4, 6});
        ai.setRecentMoveHistory(recentOwnMoves);
        Method isImmediateReversal = ChineseChessAI.class.getDeclaredMethod(
                "isImmediateReversal", int[].class);
        isImmediateReversal.setAccessible(true);
        check("识别同一棋子的立即原路回摆",
                (boolean) isImmediateReversal.invoke(ai, (Object) new int[]{4, 6, 2, 6}));
        check("不会把其他走法误判为回摆",
                !(boolean) isImmediateReversal.invoke(ai, (Object) new int[]{4, 6, 5, 6}));

        Method kingSafety = ChineseChessAI.class.getDeclaredMethod(
                "evaluateKingSafety", int[][].class, int.class);
        kingSafety.setAccessible(true);
        int exposed = (int) kingSafety.invoke(ai, endgame, 1);
        endgame[9][3] = 2;
        endgame[9][5] = 2;
        endgame[7][2] = 3;
        int guarded = (int) kingSafety.invoke(ai, endgame, 1);
        check("完整仕相结构的将区安全分更高", guarded > exposed);
    }

    // ====================================================================
    // 14. 存在安全替代时，根节点不得选择会立即触发第三次重复的着法
    // ====================================================================
    static void testAiAvoidsThirdRepetition() throws Exception {
        System.out.println("[T14] AI 根节点规避第三次重复");
        ChineseChessGame game = new ChineseChessGame();
        check("重复测试前置红兵着法合法", game.commitMove(0, 6, 0, 5) != null);

        List<int[]> blackMoves = game.getAllMoves(BLACK);
        check("黑方存在多个合法替代", blackMoves.size() > 1);
        int[] forbiddenView = blackMoves.get(0);
        int[] forbiddenAi = {
                forbiddenView[1], forbiddenView[0], forbiddenView[3], forbiddenView[2]
        };

        int[][] repeatedBoard = game.getBoardAsIntArray();
        repeatedBoard[forbiddenAi[2]][forbiddenAi[3]] =
                repeatedBoard[forbiddenAi[0]][forbiddenAi[1]];
        repeatedBoard[forbiddenAi[0]][forbiddenAi[1]] = 0;

        ChineseChessAI ai = new ChineseChessAI(1);
        Method computeHash = ChineseChessAI.class.getDeclaredMethod(
                "computePositionHash", int[][].class, int.class);
        computeHash.setAccessible(true);
        long repeatedHash = (long) computeHash.invoke(ai, repeatedBoard, 1);
        List<Long> syntheticHistory = new ArrayList<>();
        syntheticHistory.add(repeatedHash);
        syntheticHistory.add(repeatedHash);
        ai.setPositionHistory(syntheticHistory);

        int[] chosen = ai.getBestMove(game.getBoardAsIntArray(), 1, -1);
        boolean choseForbidden = chosen != null
                && chosen[0] == forbiddenAi[0] && chosen[1] == forbiddenAi[1]
                && chosen[2] == forbiddenAi[2] && chosen[3] == forbiddenAi[3];
        check("有安全替代时不选择第三次重复着法", chosen != null && !choseForbidden);
    }

    // ====================================================================
    // 15. 残局装载：内置关卡合法、非法规格拒绝且无副作用、将杀可达成
    // ====================================================================
    static void testEndgameLoading() {
        System.out.println("[T15] 残局装载与守卫");
        ChineseChessGame g = new ChineseChessGame();

        // 15.1 内置关卡全部装载成功，红先，红方有合法着法
        for (ChineseChessEndgames.EndgameSpec spec : ChineseChessEndgames.LEVELS) {
            boolean loaded = g.loadEndgamePosition(spec.pieces, 0);
            check("残局《" + spec.name + "》装载成功", loaded);
            if (!loaded) continue;
            check("《" + spec.name + "》红方先行", g.getCurrentSide() == RED);
            check("《" + spec.name + "》红方存在合法着法",
                    g.getAllMoves(RED).size() > 0);
            check("《" + spec.name + "》初始未终局", !g.isGameOver());
        }

        // 15.2 非法规格一律拒绝
        check("拒绝：空规格", !g.loadEndgamePosition(new int[][]{}, 0));
        check("拒绝：缺黑将",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 3, 9}}, 0));
        check("拒绝：坐标越界",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 3, 9},
                        {GENERAL.ordinal(), 1, 9, 0}}, 0));
        check("拒绝：占位重叠",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 3, 9},
                        {CHARIOT.ordinal(), 0, 3, 9},
                        {GENERAL.ordinal(), 1, 4, 0}}, 0));
        check("拒绝：双将帅无阻挡照面",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 4, 9},
                        {GENERAL.ordinal(), 1, 4, 0}}, 0));
        check("拒绝：走子方编码非法",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 3, 9},
                        {GENERAL.ordinal(), 1, 4, 0}}, 2));
        check("拒绝：士出九宫",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 3, 9},
                        {ADVISOR.ordinal(), 0, 4, 5},
                        {GENERAL.ordinal(), 1, 4, 0}}, 0));
        check("拒绝：黑士出宫",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 3, 9},
                        {ADVISOR.ordinal(), 1, 4, 5},
                        {GENERAL.ordinal(), 1, 4, 0}}, 0));
        check("拒绝：红卒越起始行",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 3, 9},
                        {SOLDIER.ordinal(), 0, 0, 8},
                        {GENERAL.ordinal(), 1, 4, 0}}, 0));
        check("拒绝：非走子方被将军（红先但黑被车将军）",
                !g.loadEndgamePosition(new int[][]{
                        {GENERAL.ordinal(), 0, 3, 9},
                        {CHARIOT.ordinal(), 0, 4, 5},
                        {GENERAL.ordinal(), 1, 4, 0}}, 0));

        // 15.3 拒绝必须无副作用：装载 L1 后尝试非法装载，局面保持不变
        ChineseChessEndgames.EndgameSpec l1 = ChineseChessEndgames.findById(1);
        check("L1 查找存在", l1 != null);
        check("L1 装载成功", g.loadEndgamePosition(l1.pieces, 0));
        boolean badLoad = g.loadEndgamePosition(new int[][]{
                {GENERAL.ordinal(), 0, 4, 9},
                {GENERAL.ordinal(), 1, 4, 0}}, 0);
        check("非法装载被拒绝", !badLoad);
        check("拒绝后红车仍在 (2,5)",
                g.getBoard()[5][2] != null && g.getBoard()[5][2].type == CHARIOT);
        check("拒绝后仍为红先", g.getCurrentSide() == RED);
        check("拒绝后走子历史为空", g.getMoveHistory().isEmpty());
        check("拒绝后未终局", !g.isGameOver());

        // 15.4 残局将杀可达成：一步重炮/马车配合将死孤将
        //     黑将(4,0)；红帅(3,9)、车(8,3)、马(3,3)。
        //     红车 (8,3)->(8,0) 沿底线将军；马(3,3) 控制 (4,1)，
        //     (3,0)/(5,0) 被底线车控制 => 将杀。
        check("将杀局面装载成功", g.loadEndgamePosition(new int[][]{
                {GENERAL.ordinal(), 0, 3, 9},
                {CHARIOT.ordinal(), 0, 8, 3},
                {HORSE.ordinal(), 0, 3, 3},
                {GENERAL.ordinal(), 1, 4, 0}}, 0));
        check("杀着车(8,3)->(8,0) 合法", g.isMoveLegal(8, 3, 8, 0));
        check("杀着提交成功", g.commitMove(8, 3, 8, 0) != null);
        check("提交后终局", g.isGameOver());
        check("红方将杀获胜", g.getWinner() == RED);
    }

    // ====================================================================
    // 16. 对局回放记录器：深拷贝独立性、越界、pop/clear 与快照装载渲染管线
    // ====================================================================
    static void testReplayRecorder() {
        System.out.println("[T16] 对局回放记录器");
        ChineseChessReplay replay = new ChineseChessReplay();
        check("新记录器为空", replay.size() == 0);
        check("空表 snapshot(0) 越界返回 null", replay.snapshot(0) == null);
        check("空表 snapshotSide(0) 越界返回 -1", replay.snapshotSide(0) == -1);
        replay.pop();
        check("空表 pop 安全（size 仍为 0）", replay.size() == 0);

        // 深拷贝独立性：记录后修改原盘不影响快照；snapshot 返回防御性拷贝。
        ChineseChessGame g = new ChineseChessGame();
        int[][] live = g.getBoardAsIntArray();
        replay.recordSnapshot(live, 0);
        check("记录后 size=1", replay.size() == 1);
        live[9][4] = 0; // 模拟原盘继续变化（红帅位被清空）
        check("修改原盘不影响已存快照（深拷贝独立性）", replay.snapshot(0)[9][4] != 0);
        int[][] got = replay.snapshot(0);
        got[0][0] = 99; // 篡改返回值
        check("snapshot 返回防御性拷贝", replay.snapshot(0)[0][0] != 99);
        check("snapshotSide 记录走子方（红=0）", replay.snapshotSide(0) == 0);
        check("snapshot 负下标越界返回 null", replay.snapshot(-1) == null);

        replay.clear();
        check("clear 后为空", replay.size() == 0);

        // 模拟短对局：开局前 + 两步合法着，逐步记录快照。
        replay.recordSnapshot(g.getBoardAsIntArray(), 0);
        check("红兵前进一步可提交", g.commitMove(0, 6, 0, 5) != null);
        replay.recordSnapshot(g.getBoardAsIntArray(), 1);
        check("黑卒前进一步可提交", g.commitMove(8, 3, 8, 4) != null);
        replay.recordSnapshot(g.getBoardAsIntArray(), 0);
        check("三快照序列 size=3（开局前+两着）", replay.size() == 3);
        check("快照1红兵已到位", replay.snapshot(1)[5][0] == 7 && replay.snapshot(1)[6][0] == 0);
        check("快照1轮到黑方走", replay.snapshotSide(1) == 1);
        check("快照2黑卒已到位", replay.snapshot(2)[4][8] == -7 && replay.snapshot(2)[3][8] == 0);
        check("快照2轮到红方走", replay.snapshotSide(2) == 0);

        // 回放渲染管线：每个快照都能经 toEndgameSpec 装载回临时棋局且棋盘逐格一致。
        for (int i = 0; i < replay.size(); i++) {
            ChineseChessGame scratch = new ChineseChessGame();
            boolean loaded = scratch.loadEndgamePosition(
                    ChineseChessReplay.toEndgameSpec(replay.snapshot(i)),
                    replay.snapshotSide(i));
            check("快照 " + i + " 可经 toEndgameSpec 装载", loaded);
            if (loaded) {
                check("快照 " + i + " 装载后棋盘逐格一致",
                        Arrays.deepEquals(scratch.getBoardAsIntArray(), replay.snapshot(i)));
            }
        }

        // pop 回退：悔棋一轮撤销两着后，末尾快照应回到黑方走前局面。
        replay.pop();
        replay.pop();
        check("pop 两个后 size=1", replay.size() == 1);
        check("pop 后仅剩开局前快照",
                replay.snapshot(0)[6][0] == 7 && replay.snapshot(0)[3][8] == -7);
    }

    // ====================================================================
    // 17. 复盘 AI 标注器：吃子/反吃/将军启发式三档标注
    // ====================================================================
    static final ChineseChessReviewAnnotator.Grade R_GOOD = ChineseChessReviewAnnotator.Grade.GOOD;
    static final ChineseChessReviewAnnotator.Grade R_NEUTRAL = ChineseChessReviewAnnotator.Grade.NEUTRAL;
    static final ChineseChessReviewAnnotator.Grade R_BLUNDER = ChineseChessReviewAnnotator.Grade.BLUNDER;

    /** 构造最小合法盘面：红帅 (3,9)、黑将 (5,0)（不同列，不照面）。 */
    static int[][] reviewBoard() {
        int[][] b = new int[10][9];
        b[9][3] = 1;
        b[0][5] = -1;
        return b;
    }

    /** 复制盘面并应用 [fromX, fromY, toX, toY] 着法，返回走后盘面。 */
    static int[][] applyReviewMove(int[][] before, int[] move) {
        int[][] after = new int[before.length][];
        for (int y = 0; y < before.length; y++) after[y] = before[y].clone();
        after[move[3]][move[2]] = after[move[1]][move[0]];
        after[move[1]][move[0]] = 0;
        return after;
    }

    /** 对单步着法做标注：自动构造走后盘面。 */
    static ChineseChessReviewAnnotator.Annotation annotate(
            ChineseChessReviewAnnotator annotator, int[][] before, int[] move, int moverSide) {
        return annotator.annotateMove(before, applyReviewMove(before, move), move, moverSide);
    }

    static void testReviewAnnotator() {
        System.out.println("[T17] 复盘 AI 标注器");
        ChineseChessReviewAnnotator annotator = new ChineseChessReviewAnnotator();
        ChineseChessReviewAnnotator.Annotation a;

        // 17.1 白吃车：黑车无根、无反吃 => GOOD，短评含「吃」
        int[][] b1 = reviewBoard();
        b1[5][2] = 5;   // 红车 (2,5)
        b1[1][2] = -5;  // 黑车 (2,1)
        a = annotate(annotator, b1, new int[]{2, 5, 2, 1}, 0);
        check("白吃车判 GOOD", a.grade == R_GOOD);
        check("白吃车短评含「吃」", a.comment.contains("吃"));

        // 17.2 走子送车：红车走进黑炮口（黑卒作炮架）且无补偿 => BLUNDER
        int[][] b2 = reviewBoard();
        b2[7][8] = 5;   // 红车 (8,7)
        b2[0][0] = -6;  // 黑炮 (0,0)
        b2[3][0] = -7;  // 黑卒 (0,3)（炮架）
        a = annotate(annotator, b2, new int[]{8, 7, 0, 7}, 0);
        check("送车给炮判 BLUNDER", a.grade == R_BLUNDER);
        check("送车短评含「白吃」", a.comment.contains("白吃"));

        // 17.3 纯将军（无吃无险）=> GOOD，短评含「将军」
        int[][] b3 = reviewBoard();
        b3[5][1] = 5;   // 红车 (1,5) -> (5,5) 直攻黑将 (5,0)
        a = annotate(annotator, b3, new int[]{1, 5, 5, 5}, 0);
        check("将军判 GOOD", a.grade == R_GOOD);
        check("将军短评含「将军」", a.comment.contains("将军"));

        // 17.4 普通安静着（无吃无险无将军）=> NEUTRAL
        int[][] b4 = reviewBoard();
        b4[6][0] = 7;   // 红兵 (0,6)
        a = annotate(annotator, b4, new int[]{0, 6, 0, 5}, 0);
        check("普通移动判 NEUTRAL", a.grade == R_NEUTRAL);

        // 17.5 以马换车（马会被黑另一车吃回，净得 +5）=> GOOD
        int[][] b5 = reviewBoard();
        b5[5][3] = 4;   // 红马 (3,5)
        b5[3][4] = -5;  // 黑车 (4,3)
        b5[0][4] = -5;  // 黑车 (4,0)（吃回红马）
        a = annotate(annotator, b5, new int[]{3, 5, 4, 3}, 0);
        check("以马换车判 GOOD", a.grade == R_GOOD);

        // 17.6 以车换马（车会被黑炮吃回，净得 -5）=> BLUNDER
        int[][] b6 = reviewBoard();
        b6[9][4] = 5;   // 红车 (4,9)
        b6[4][4] = -4;  // 黑马 (4,4)
        b6[0][4] = -6;  // 黑炮 (4,0)
        b6[1][4] = -2;  // 黑士 (4,1)（炮架）
        a = annotate(annotator, b6, new int[]{4, 9, 4, 4}, 0);
        check("以车换马判 BLUNDER", a.grade == R_BLUNDER);

        // 17.7 擒将获胜：吃掉黑将 => GOOD（走后盘面缺将仍需稳定定档）
        int[][] b7 = reviewBoard();
        b7[9][5] = 5;   // 红车 (5,9)
        a = annotate(annotator, b7, new int[]{5, 9, 5, 0}, 0);
        check("擒将获胜判 GOOD", a.grade == R_GOOD);

        // 17.8 等价兑子（车换车，净差 0）=> NEUTRAL
        int[][] b8 = reviewBoard();
        b8[7][2] = 5;   // 红车 (2,7)
        b8[2][2] = -5;  // 黑车 (2,2)
        b8[2][0] = -5;  // 黑车 (0,2)（吃回红车）
        a = annotate(annotator, b8, new int[]{2, 7, 2, 2}, 0);
        check("车兑车判 NEUTRAL", a.grade == R_NEUTRAL);
        check("兑子短评含「兑」", a.comment.contains("兑"));

        // 17.9 结构异常输入兜底为 NEUTRAL，不抛异常
        a = annotator.annotateMove(null, null, null, 0);
        check("异常输入兜底 NEUTRAL", a.grade == R_NEUTRAL);

        // 17.10 整局管线：真实对局逐步记快照 => diffMove/annotateGame 全链路
        ChineseChessGame g = new ChineseChessGame();
        ChineseChessReplay replay = new ChineseChessReplay();
        replay.recordSnapshot(g.getBoardAsIntArray(), 0);
        check("管线前置红兵着法合法", g.commitMove(0, 6, 0, 5) != null);
        replay.recordSnapshot(g.getBoardAsIntArray(), 1);
        check("管线前置黑卒着法合法", g.commitMove(8, 3, 8, 4) != null);
        replay.recordSnapshot(g.getBoardAsIntArray(), 0);
        check("diffMove 推演首着坐标",
                Arrays.equals(ChineseChessReviewAnnotator.diffMove(
                                replay.snapshot(0), replay.snapshot(1)),
                        new int[]{0, 6, 0, 5}));
        List<ChineseChessReviewAnnotator.Annotation> all =
                new ChineseChessReviewAnnotator().annotateGame(replay);
        check("整局标注数 = 快照数-1", all.size() == replay.size() - 1 && all.size() == 2);
        check("开局安静着判 NEUTRAL", all.get(0).grade == R_NEUTRAL);
        check("第二着安静着判 NEUTRAL", all.get(1).grade == R_NEUTRAL);
    }

    // ====================================================================
    public static void main(String[] args) throws Exception {
        System.out.println("==== 中国象棋 AI 逻辑回归测试 ====");
        testHorseLeg();
        testElephantEye();
        testFlyingGeneral();
        testBlockedHorseCapturesGeneral();
        testCommitMoveSuccess();
        testCheckmate();
        testStalemate();
        testAiLegality();
        testOpeningBookGuard();
        testPositionHashConsistency();
        testRepetitionClassification();
        testAiGeneralBoundaries();
        testAiStrengthProfile();
        testAiAvoidsThirdRepetition();
        testEndgameLoading();
        testReplayRecorder();
        testReviewAnnotator();

        System.out.println("================================");
        System.out.println("通过=" + passed + "  失败=" + failed);
        if (failed > 0) {
            System.out.println("结论：存在失败用例，需修复后再上线！");
            System.exit(1);
        } else {
            System.out.println("结论：全部通过，中央闸门有效拦截非法着法，AI 全程合法。");
        }
    }
}
