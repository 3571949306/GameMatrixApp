// 同步声明：此文件与 module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoAI.java 保持同步，修改时请同步修改对方文件
package com.gamecenter.app.games.go;

import com.gamecenter.app.core.common.GameAI;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GoAI implements GameAI {

    private static final int MAX_NODES = 80000;
    private static final long MCTS_TIME_LIMIT_MS = 1500;
    private static final int MCTS_PLAYOUT_MAX_MOVES = 162;

    private int difficulty = 2; // 1=简单, 2=普通, 3=困难, 4=大师
    private Random random = new Random();

    /** 取消标志：cancel() 置位后，搜索循环应尽快退出。 */
    private volatile boolean cancelled = false;
    /** 当前是否正在思考（GameAI 契约）。 */
    private volatile boolean thinking = false;

    public void setDifficulty(int difficulty) {
        if (difficulty >= 1 && difficulty <= 4) {
            this.difficulty = difficulty;
        }
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int[] findBestAiMove(GoGame game) {
        cancelled = false;
        thinking = true;
        try {
            return switch (difficulty) {
                case 1 -> findRandomAiMove(game);
                case 3 -> mctsMove(game, 500);  // 弱化版 MCTS，500ms，平滑过渡到档4
                case 4 -> mctsMove(game, MCTS_TIME_LIMIT_MS);
                default -> findGreedyAiMove(game);
            };
        } finally {
            thinking = false;
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
        thinking = false;
    }

    @Override
    public boolean isThinking() {
        return thinking;
    }

    private int[] findRandomAiMove(GoGame game) {
        int[][] board = game.getBoard();
        List<int[]> candidates = new ArrayList<>();
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (board[r][c] == GoGame.EMPTY && game.isValidMove(r, c, GoGame.WHITE)) {
                    candidates.add(new int[]{r, c});
                }
            }
        }
        if (candidates.isEmpty()) return null;
        if (random.nextInt(10) == 0 && game.getConsecutivePasses() == 0) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private int[] findGreedyAiMove(GoGame game) {
        int[][] board = game.getBoard();
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (board[r][c] == GoGame.EMPTY && game.isValidMove(r, c, GoGame.WHITE)) {
                    int[][] simulated = GoGame.copyBoard(board);
                    simulated[r][c] = GoGame.WHITE;
                    int captured = GoGame.simulateCapture(simulated, GoGame.BLACK, r, c);
                    int score = captured * 10 + evaluatePosition(board, r, c) + random.nextInt(3);
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = new int[]{r, c};
                    }
                }
            }
        }
        if (bestMove != null && random.nextInt(10) < 2 && game.getConsecutivePasses() == 0) return null;
        return bestMove;
    }

    private int[] findMinimaxAiMove(GoGame game, int depth) {
        int[][] board = game.getBoard();
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (board[r][c] != GoGame.EMPTY || !game.isValidMove(r, c, GoGame.WHITE)) continue;
                int[][] simulated = GoGame.copyBoard(board);
                simulated[r][c] = GoGame.WHITE;
                int captured = GoGame.simulateCapture(simulated, GoGame.BLACK, r, c);
                int score = captured * 10 + evaluatePosition(board, r, c)
                        + minimax(simulated, depth - 1, false, Integer.MIN_VALUE, Integer.MAX_VALUE, new int[]{0});
                
                if (difficulty < 4) score += random.nextInt(2);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = new int[]{r, c};
                }
            }
        }
        if (bestMove != null && difficulty < 4 && random.nextInt(10) < 2 && game.getConsecutivePasses() == 0) {
            return null;
        }
        return bestMove;
    }

    private int minimax(int[][] state, int depth, boolean isMax, int alpha, int beta, int[] nodeCount) {
        if (cancelled) return evaluateBoard(state);
        if (++nodeCount[0] > MAX_NODES) return evaluateBoard(state);
        if (depth == 0) return evaluateBoard(state);
        int best = isMax ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int color = isMax ? GoGame.WHITE : GoGame.BLACK;
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (state[r][c] != GoGame.EMPTY) continue;
                if (!GoGame.isValidMove(state, r, c, color)) continue;
                int[][] sim = GoGame.copyBoard(state);
                sim[r][c] = color;
                GoGame.simulateCapture(sim, color == GoGame.WHITE ? GoGame.BLACK : GoGame.WHITE, r, c);
                int val = minimax(sim, depth - 1, !isMax, alpha, beta, nodeCount);
                if (isMax) {
                    best = Math.max(best, val);
                    alpha = Math.max(alpha, best);
                } else {
                    best = Math.min(best, val);
                    beta = Math.min(beta, best);
                }
                if (beta <= alpha) return best;
            }
        }
        return best;
    }

    private int evaluateBoard(int[][] state) {
        int whiteScore = 0, blackScore = 0;
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (state[r][c] == GoGame.WHITE) whiteScore += 10;
                else if (state[r][c] == GoGame.BLACK) blackScore += 10;
            }
        }
        // 领地估算：空点周围只有一方棋子时，算作该方领地（简化版目数）
        int whiteTerritory = 0, blackTerritory = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (state[r][c] != GoGame.EMPTY) continue;
                int whiteNeighbors = 0, blackNeighbors = 0;
                for (int[] d : dirs) {
                    int nr = r + d[0], nc = c + d[1];
                    if (nr >= 0 && nr < GoGame.BOARD_SIZE && nc >= 0 && nc < GoGame.BOARD_SIZE) {
                        if (state[nr][nc] == GoGame.WHITE) whiteNeighbors++;
                        else if (state[nr][nc] == GoGame.BLACK) blackNeighbors++;
                    }
                }
                if (whiteNeighbors > 0 && blackNeighbors == 0) whiteTerritory += 3;
                else if (blackNeighbors > 0 && whiteNeighbors == 0) blackTerritory += 3;
            }
        }
        return (whiteScore + whiteTerritory) - (blackScore + blackTerritory);
    }

    private int evaluatePosition(int[][] board, int row, int col) {
        int score = 0;
        int centerDist = Math.abs(row - 4) + Math.abs(col - 4);
        score += (8 - centerDist) * 2;
        if (row == 0 || row == GoGame.BOARD_SIZE - 1) score -= 3;
        if (col == 0 || col == GoGame.BOARD_SIZE - 1) score -= 3;

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr >= 0 && nr < GoGame.BOARD_SIZE && nc >= 0 && nc < GoGame.BOARD_SIZE) {
                if (board[nr][nc] == GoGame.WHITE) score += 2;
            }
        }
        return score;
    }

    private static class MctsNode {
        final int[][] state;
        final int player;
        int visits = 0;
        double totalReward = 0.0;
        int moveRow = -1, moveCol = -1;
        final List<MctsNode> children = new ArrayList<>();
        final List<int[]> untriedMoves;

        MctsNode(int[][] state, int player, List<int[]> untriedMoves) {
            this.state = state;
            this.player = player;
            this.untriedMoves = untriedMoves;
        }
    }

    private int[] mctsMove(GoGame game, long timeLimitMs) {
        int[][] rootState = GoGame.copyBoard(game.getBoard());

        // 开局库：棋盘为空或只有少量棋子时，从开局候选位置中加权随机选
        int stoneCount = 0;
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (rootState[r][c] != GoGame.EMPTY) stoneCount++;
            }
        }
        if (stoneCount <= 1) {
            int[] opening = getOpeningMove(rootState);
            if (opening != null) return opening;
        }

        List<int[]> rootMoves = new ArrayList<>();
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (rootState[r][c] == GoGame.EMPTY && GoGame.isValidMove(rootState, r, c, GoGame.WHITE)) {
                    rootMoves.add(new int[]{r, c});
                }
            }
        }
        if (rootMoves.isEmpty()) return null;

        MctsNode root = new MctsNode(rootState, GoGame.WHITE, rootMoves);
        long startMs = System.currentTimeMillis();

        while (!cancelled && System.currentTimeMillis() - startMs < timeLimitMs) {
            MctsNode node = root;
            while (node.untriedMoves.isEmpty() && !node.children.isEmpty()) {
                node = selectChild(node);
            }
            if (!node.untriedMoves.isEmpty()) {
                int[] move = node.untriedMoves.remove(random.nextInt(node.untriedMoves.size()));
                int[][] childState = GoGame.copyBoard(node.state);
                int childPlayer = node.player == GoGame.WHITE ? GoGame.BLACK : GoGame.WHITE;
                childState[move[0]][move[1]] = node.player;
                List<int[]> childMoves = getValidMoves(childState, childPlayer);
                MctsNode child = new MctsNode(childState, childPlayer, childMoves);
                child.moveRow = move[0];
                child.moveCol = move[1];
                node.children.add(child);
                node = child;
            }
            double result = playout(node.state, node.player);
            while (node != null) {
                node.visits++;
                node.totalReward += (node.player == GoGame.WHITE) ? result : (1.0 - result);
                node = getParent(root, node);
            }
        }

        // 最终决策：从访问次数前 3 的子节点中加权随机选（增加对局多样性）
        List<MctsNode> topChildren = new ArrayList<>(root.children);
        topChildren.sort((a, b) -> Integer.compare(b.visits, a.visits));
        int pickCount = Math.min(3, topChildren.size());
        if (pickCount == 0) return null;
        // 权重：第1名权重3，第2名权重2，第3名权重1
        int[] weights = {3, 2, 1};
        int totalWeight = 0;
        for (int i = 0; i < pickCount; i++) totalWeight += weights[i];
        int r = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < pickCount; i++) {
            cumulative += weights[i];
            if (r < cumulative) {
                return new int[]{topChildren.get(i).moveRow, topChildren.get(i).moveCol};
            }
        }
        return new int[]{topChildren.get(0).moveRow, topChildren.get(0).moveCol};
    }

    /**
     * 围棋开局库：9路棋盘开局从星位/三三/小目等位置加权随机选
     * <p>
     * 候选位置：
     * <ul>
     *   <li>星位（4个角）：(2,2), (2,6), (6,2), (6,6) - 权重3</li>
     *   <li>边星：(2,4), (4,2), (4,6), (6,4) - 权重2</li>
     *   <li>天元：(4,4) - 权重2</li>
     *   <li>小目：(3,2), (2,3), (3,6), (6,3), (5,2), (2,5), (5,6), (6,5) - 权重1</li>
     * </ul>
     *
     * @param board 当前棋盘
     * @return 开局走法 [row, col]，无可用位置返回 null
     */
    private int[] getOpeningMove(int[][] board) {
        int[][] openPoints = {
            {2, 2}, {2, 6}, {6, 2}, {6, 6},        // 星位（4个角）
            {2, 4}, {4, 2}, {4, 6}, {6, 4},        // 边星
            {4, 4},                                  // 天元
            {3, 2}, {2, 3}, {3, 6}, {6, 3},         // 小目变体
            {5, 2}, {2, 5}, {5, 6}, {6, 5}
        };
        int[] weights = {3, 3, 3, 3, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1};

        List<int[]> available = new ArrayList<>();
        List<Integer> availableWeights = new ArrayList<>();
        int totalWeight = 0;
        for (int i = 0; i < openPoints.length; i++) {
            int[] pt = openPoints[i];
            if (board[pt[0]][pt[1]] == GoGame.EMPTY && GoGame.isValidMove(board, pt[0], pt[1], GoGame.WHITE)) {
                available.add(pt);
                availableWeights.add(weights[i]);
                totalWeight += weights[i];
            }
        }
        if (available.isEmpty()) return null;
        int r = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < available.size(); i++) {
            cumulative += availableWeights.get(i);
            if (r < cumulative) {
                return available.get(i);
            }
        }
        return available.get(0);
    }

    private MctsNode selectChild(MctsNode node) {
        MctsNode best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (MctsNode child : node.children) {
            if (child.visits == 0) {
                best = child;
                break;
            }
            double uct = child.totalReward / child.visits
                    + 1.41 * Math.sqrt(Math.log(node.visits) / child.visits);
            if (uct > bestValue) {
                bestValue = uct;
                best = child;
            }
        }
        return best;
    }

    private double playout(int[][] state, int currentPlayer) {
        int passCount = 0;
        int moveCount = 0;
        while (moveCount < MCTS_PLAYOUT_MAX_MOVES && passCount < 2) {
            List<int[]> moves = getValidMoves(state, currentPlayer);
            if (moves.isEmpty()) {
                passCount++;
                currentPlayer = (currentPlayer == GoGame.WHITE) ? GoGame.BLACK : GoGame.WHITE;
                moveCount++;
                continue;
            }
            int[] move = moves.get(random.nextInt(moves.size()));
            state[move[0]][move[1]] = currentPlayer;
            GoGame.simulateCapture(state, currentPlayer == GoGame.WHITE ? GoGame.BLACK : GoGame.WHITE, move[0], move[1]);
            passCount = 0;
            currentPlayer = (currentPlayer == GoGame.WHITE) ? GoGame.BLACK : GoGame.WHITE;
            moveCount++;
        }
        int white = 0, black = 0;
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (state[r][c] == GoGame.WHITE) white++;
                else if (state[r][c] == GoGame.BLACK) black++;
            }
        }
        if (white + GoGame.KOMI > black) return 1.0;
        if (white + GoGame.KOMI < black) return 0.0;
        return 0.5;
    }

    private List<int[]> getValidMoves(int[][] state, int color) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (state[r][c] == GoGame.EMPTY && GoGame.isValidMove(state, r, c, color)) {
                    moves.add(new int[]{r, c});
                }
            }
        }
        return moves;
    }

    private MctsNode getParent(MctsNode root, MctsNode target) {
        return findParentDfs(root, target);
    }

    private MctsNode findParentDfs(MctsNode node, MctsNode target) {
        for (MctsNode child : node.children) {
            if (child == target) return node;
            MctsNode found = findParentDfs(child, target);
            if (found != null) return found;
        }
        return null;
    }
}
