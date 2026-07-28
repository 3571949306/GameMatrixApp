package com.gamecenter.app.go;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 围棋 AI（模块化版本）。
 *
 * <p>从 app 模块复制并改包名为 com.gamecenter.app.go。
 * 4 级难度：随机、贪心、Minimax、MCTS。</p>
 */
public class GoAI {

    private static final int MAX_NODES = 80000;
    private static final long MCTS_TIME_LIMIT_MS = 1500;
    private static final int MCTS_PLAYOUT_MAX_MOVES = 162;

    private int difficulty = 2; // 1=简单, 2=普通, 3=困难, 4=大师
    private Random random = new Random();

    public void setDifficulty(int difficulty) {
        if (difficulty >= 1 && difficulty <= 4) {
            this.difficulty = difficulty;
        }
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int[] findBestAiMove(GoGame game) {
        return switch (difficulty) {
            case 1 -> findRandomAiMove(game);
            case 3 -> findMinimaxAiMove(game, 2);
            case 4 -> mctsMove(game);
            default -> findGreedyAiMove(game);
        };
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
        return whiteScore - blackScore;
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

    private int[] mctsMove(GoGame game) {
        int[][] rootState = GoGame.copyBoard(game.getBoard());
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

        while (System.currentTimeMillis() - startMs < MCTS_TIME_LIMIT_MS) {
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

        MctsNode bestChild = null;
        int bestVisits = -1;
        for (MctsNode child : root.children) {
            if (child.visits > bestVisits) {
                bestVisits = child.visits;
                bestChild = child;
            }
        }
        return (bestChild != null) ? new int[]{bestChild.moveRow, bestChild.moveCol} : null;
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
