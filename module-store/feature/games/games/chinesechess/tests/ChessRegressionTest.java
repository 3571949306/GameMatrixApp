import com.gamecenter.app.chinesechess.ChineseChessGame;
import com.gamecenter.app.chinesechess.ChineseChessAI;

import java.util.ArrayList;
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
