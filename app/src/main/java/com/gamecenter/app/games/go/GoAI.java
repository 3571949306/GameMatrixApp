// 同步声明：此文件与 module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoAI.java 保持同步，修改时请同步修改对方文件
package com.gamecenter.app.games.go;

import com.gamecenter.app.core.common.GameAI;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 9x9 围棋 AI。
 *
 * <p>四档共用 {@link GoGame#tryMove(int[][], int[][], int, int, int)} 生成合法着，
 * 因而提子、自杀和简单劫与实战规则保持一致。AI 只读取 {@link GoGame.PositionSnapshot}
 * 并且不会修改传入游戏。null 只表示当前不是白方回合、游戏已结束或确实没有合法着，
 * 不再用随机停着来伪造难度。</p>
 */
public class GoAI implements GameAI {
    private static final int HARD_ITERATIONS = 360;
    private static final int MASTER_ITERATIONS = 960;
    private static final long HARD_TIME_LIMIT_MS = 500L;
    private static final long MASTER_TIME_LIMIT_MS = 1500L;
    private static final int DEFAULT_PLAYOUT_MAX_MOVES = 64;
    private static final int DEEP_NODE_CANDIDATE_LIMIT = 28;
    private static final double UCT_EXPLORATION = 1.25;

    private static final int[][] DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] STRATEGIC_POINTS = {
            {2, 2}, {2, 6}, {6, 2}, {6, 6}, {4, 4},
            {2, 4}, {4, 2}, {4, 6}, {6, 4}
    };

    private int difficulty = 2; // 1=入门, 2=普通, 3=困难, 4=大师
    private final Random random;
    private volatile boolean cancelled;
    private volatile boolean thinking;

    // 仅用于快速且确定的纯 Java 回归；生产默认值见上方常量。
    private int hardIterations = HARD_ITERATIONS;
    private int masterIterations = MASTER_ITERATIONS;
    private long hardTimeLimitMs = HARD_TIME_LIMIT_MS;
    private long masterTimeLimitMs = MASTER_TIME_LIMIT_MS;
    private int playoutMaxMoves = DEFAULT_PLAYOUT_MAX_MOVES;

    public GoAI() {
        this(new Random());
    }

    public GoAI(long randomSeed) {
        this(new Random(randomSeed));
    }

    private GoAI(Random random) {
        this.random = random;
    }

    public void setDifficulty(int difficulty) {
        if (difficulty >= 1 && difficulty <= 4) this.difficulty = difficulty;
    }

    public int getDifficulty() {
        return difficulty;
    }

    /** 供验收日志记录真实引擎档位。 */
    public String getSearchProfileName() {
        return switch (difficulty) {
            case 1 -> "safe-random";
            case 2 -> "tactical-heuristic";
            case 3 -> "mcts-" + hardIterations;
            case 4 -> "mcts-" + masterIterations;
            default -> "unknown";
        };
    }

    /** 包内测试入口，避免纯 Java 回归依赖墙钟速度。 */
    void configureSearchForTests(int hardIterations, int masterIterations,
                                 long timeLimitMs, int playoutMaxMoves) {
        if (hardIterations <= 0 || masterIterations <= 0
                || timeLimitMs <= 0 || playoutMaxMoves <= 0) {
            throw new IllegalArgumentException("search limits must be positive");
        }
        this.hardIterations = hardIterations;
        this.masterIterations = masterIterations;
        this.hardTimeLimitMs = timeLimitMs;
        this.masterTimeLimitMs = timeLimitMs;
        this.playoutMaxMoves = playoutMaxMoves;
    }

    public int[] findBestAiMove(GoGame game) {
        if (game == null) return null;
        cancelled = false;
        thinking = true;
        try {
            GoGame.PositionSnapshot snapshot = game.snapshot();
            if (snapshot.isGameOver() || snapshot.getCurrentPlayer() != GoGame.WHITE) return null;
            // A pass is a scoring proposal. Accept it only when the opponent has just passed and
            // the official area score already has White ahead; never use pass as random variety.
            if (snapshot.getConsecutivePasses() == 1
                    && GoGame.calculateScore(snapshot.getBoard()).isWhiteWinner()) {
                return null;
            }
            SearchState root = new SearchState(snapshot.getBoard(), snapshot.getPreviousBoard(),
                    GoGame.WHITE, snapshot.getConsecutivePasses());
            return switch (difficulty) {
                case 1 -> findSafeRandomMove(root);
                case 3 -> mctsMove(root, hardIterations, hardTimeLimitMs);
                case 4 -> mctsMove(root, masterIterations, masterTimeLimitMs);
                default -> findTacticalMove(root);
            };
        } finally {
            thinking = false;
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public boolean isThinking() {
        return thinking;
    }

    private int[] findSafeRandomMove(SearchState state) {
        List<Candidate> moves = generateCandidates(state, GoGame.WHITE);
        if (moves.isEmpty()) return null;
        List<Candidate> safe = new ArrayList<>();
        for (Candidate candidate : moves) {
            int liberties = GoGame.countLiberties(candidate.board, candidate.row, candidate.col);
            if (liberties > 1 || candidate.captured > 0) safe.add(candidate);
        }
        List<Candidate> pool = safe.isEmpty() ? moves : safe;
        Candidate selected = pool.get(random.nextInt(pool.size()));
        return selected.coordinates();
    }

    /**
     * 普通档：一层战术选择。优先提子、救一气棋和打吃，避免自打吃与可被立即提取，
     * 同时奖励连接、切断及 9 路棋盘角边效率。
     */
    private int[] findTacticalMove(SearchState state) {
        List<Candidate> moves = generateCandidates(state, GoGame.WHITE);
        if (moves.isEmpty()) return null;

        // 只对静态评价最好的候选做一层反击检查，控制普通档耗时。
        int replyChecked = Math.min(16, moves.size());
        for (int i = 0; i < replyChecked; i++) {
            Candidate candidate = moves.get(i);
            int maxReplyCapture = maxImmediateCapture(
                    candidate.board, state.board, GoGame.BLACK);
            candidate.score -= maxReplyCapture * 72.0;
        }
        sortCandidates(moves);
        return moves.get(0).coordinates();
    }

    private List<Candidate> generateCandidates(SearchState state, int color) {
        List<Candidate> candidates = new ArrayList<>();
        for (int row = 0; row < GoGame.BOARD_SIZE; row++) {
            for (int col = 0; col < GoGame.BOARD_SIZE; col++) {
                if (state.board[row][col] != GoGame.EMPTY) continue;
                GoGame.MoveResult result = GoGame.tryMove(
                        state.board, state.previousBoard, row, col, color);
                if (!result.isLegal()) continue;
                int[][] nextBoard = result.getBoard();
                double score = evaluateMove(state.board, nextBoard, row, col, color,
                        result.getCapturedStones());
                candidates.add(new Candidate(row, col, nextBoard,
                        result.getCapturedStones(), score));
            }
        }
        sortCandidates(candidates);
        return candidates;
    }

    private static void sortCandidates(List<Candidate> candidates) {
        candidates.sort(Comparator
                .comparingDouble((Candidate candidate) -> candidate.score).reversed()
                .thenComparing(Comparator.comparingInt(
                        (Candidate candidate) -> candidate.captured).reversed())
                .thenComparingInt(candidate -> candidate.row)
                .thenComparingInt(candidate -> candidate.col));
    }

    private double evaluateMove(int[][] before, int[][] after, int row, int col,
                                int color, int captured) {
        double score = captured * 125.0;
        int opponent = GoGame.opposite(color);
        boolean[][] seenOwn = new boolean[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
        boolean[][] seenOpponent = new boolean[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
        int connectedOwnGroups = 0;
        int adjacentOpponentGroups = 0;

        for (int[] dir : DIRS) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (!onBoard(nr, nc)) continue;
            if (before[nr][nc] == color && !seenOwn[nr][nc]) {
                connectedOwnGroups++;
                int oldLiberties = GoGame.countLiberties(before, nr, nc);
                if (oldLiberties == 1) score += 105.0; // 救一气棋
                markGroup(before, nr, nc, color, seenOwn);
            } else if (before[nr][nc] == opponent && !seenOpponent[nr][nc]) {
                adjacentOpponentGroups++;
                int oldLiberties = GoGame.countLiberties(before, nr, nc);
                if (oldLiberties == 2) score += 34.0; // 制造打吃
                markGroup(before, nr, nc, opponent, seenOpponent);
            }
        }

        score += connectedOwnGroups * 15.0;
        if (connectedOwnGroups >= 2) score += 24.0;
        score += adjacentOpponentGroups * 6.0;

        int liberties = GoGame.countLiberties(after, row, col);
        score += Math.min(liberties, 6) * 5.0;
        if (liberties == 1) score -= captured > 0 ? 24.0 : 150.0;
        else if (liberties == 2) score -= 8.0;

        if (isLikelyOwnEye(before, row, col, color) && captured == 0) score -= 85.0;
        if (row == 0 || row == GoGame.BOARD_SIZE - 1) score -= 10.0;
        if (col == 0 || col == GoGame.BOARD_SIZE - 1) score -= 10.0;
        score += strategicPointBonus(row, col);
        return score;
    }

    private int maxImmediateCapture(int[][] board, int[][] koForbiddenBoard, int color) {
        int maxCapture = 0;
        for (int row = 0; row < GoGame.BOARD_SIZE; row++) {
            for (int col = 0; col < GoGame.BOARD_SIZE; col++) {
                if (board[row][col] != GoGame.EMPTY) continue;
                GoGame.MoveResult reply = GoGame.tryMove(
                        board, koForbiddenBoard, row, col, color);
                if (reply.isLegal()) maxCapture = Math.max(maxCapture, reply.getCapturedStones());
            }
        }
        return maxCapture;
    }

    private static double strategicPointBonus(int row, int col) {
        int bestDistance = Integer.MAX_VALUE;
        for (int[] point : STRATEGIC_POINTS) {
            bestDistance = Math.min(bestDistance,
                    Math.abs(row - point[0]) + Math.abs(col - point[1]));
        }
        return 18.0 - bestDistance * 3.0;
    }

    private static boolean isLikelyOwnEye(int[][] board, int row, int col, int color) {
        int neighbors = 0;
        for (int[] dir : DIRS) {
            int nr = row + dir[0];
            int nc = col + dir[1];
            if (!onBoard(nr, nc)) continue;
            neighbors++;
            if (board[nr][nc] != color) return false;
        }
        return neighbors >= 2;
    }

    private static void markGroup(int[][] board, int startRow, int startCol,
                                  int color, boolean[][] seen) {
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startRow, startCol});
        seen[startRow][startCol] = true;
        while (!queue.isEmpty()) {
            int[] cell = queue.removeFirst();
            for (int[] dir : DIRS) {
                int nr = cell[0] + dir[0];
                int nc = cell[1] + dir[1];
                if (onBoard(nr, nc) && !seen[nr][nc] && board[nr][nc] == color) {
                    seen[nr][nc] = true;
                    queue.addLast(new int[]{nr, nc});
                }
            }
        }
    }

    private int[] mctsMove(SearchState rootState, int iterationBudget, long timeLimitMs) {
        MctsNode root = new MctsNode(null, rootState, -1, -1);
        ensureMoves(root, true);
        if (root.untriedMoves.isEmpty()) return null;

        long deadlineNanos = System.nanoTime() + timeLimitMs * 1_000_000L;
        int iterations = 0;
        while (iterations < iterationBudget && !shouldStop()
                && (iterations == 0 || System.nanoTime() < deadlineNanos)) {
            MctsNode node = root;

            // Selection + expansion. Node states are immutable and never handed to a mutating playout.
            while (!shouldStop()) {
                ensureMoves(node, node == root);
                if (!node.untriedMoves.isEmpty()) {
                    Candidate move = node.untriedMoves.remove(0);
                    SearchState childState = new SearchState(move.board, node.state.board,
                            GoGame.opposite(node.state.playerToMove), 0);
                    MctsNode child = new MctsNode(node, childState, move.row, move.col);
                    node.children.add(child);
                    node = child;
                    break;
                }
                if (node.children.isEmpty()) break;
                node = selectChild(node);
            }

            double whiteReward = playout(node.state);
            for (MctsNode current = node; current != null; current = current.parent) {
                current.visits++;
                current.totalWhiteReward += whiteReward;
            }
            iterations++;
        }

        if (root.children.isEmpty()) {
            return root.untriedMoves.isEmpty() ? null : root.untriedMoves.get(0).coordinates();
        }
        // 高档最终确定选择访问最多的合法着，不再在前三名间随机抽取。
        MctsNode best = null;
        for (MctsNode child : root.children) {
            if (best == null || child.visits > best.visits
                    || (child.visits == best.visits && child.meanWhiteReward() > best.meanWhiteReward())
                    || (child.visits == best.visits
                    && Double.compare(child.meanWhiteReward(), best.meanWhiteReward()) == 0
                    && coordinatesBefore(child, best))) {
                best = child;
            }
        }
        return new int[]{best.moveRow, best.moveCol};
    }

    private void ensureMoves(MctsNode node, boolean root) {
        if (node.movesInitialized) return;
        node.movesInitialized = true;
        node.untriedMoves = generateCandidates(node.state, node.state.playerToMove);
        if (!root && node.untriedMoves.size() > DEEP_NODE_CANDIDATE_LIMIT) {
            node.untriedMoves = new ArrayList<>(
                    node.untriedMoves.subList(0, DEEP_NODE_CANDIDATE_LIMIT));
        }
    }

    private MctsNode selectChild(MctsNode parent) {
        MctsNode best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        double parentLog = Math.log(Math.max(1, parent.visits));
        for (MctsNode child : parent.children) {
            double whiteMean = child.meanWhiteReward();
            // 当前层执白就最大化白胜率，执黑就最大化黑胜率，避免奖励视角反转。
            double exploitation = parent.state.playerToMove == GoGame.WHITE
                    ? whiteMean : 1.0 - whiteMean;
            double exploration = UCT_EXPLORATION
                    * Math.sqrt(parentLog / Math.max(1, child.visits));
            double value = exploitation + exploration;
            if (value > bestValue) {
                bestValue = value;
                best = child;
            }
        }
        return best;
    }

    private double playout(SearchState start) {
        int[][] board = start.board;
        int[][] previousBoard = start.previousBoard;
        int player = start.playerToMove;
        int passes = start.consecutivePasses;

        for (int ply = 0; ply < playoutMaxMoves && passes < 2 && !shouldStop(); ply++) {
            SearchState state = new SearchState(board, previousBoard, player, passes);
            List<Candidate> moves = generateCandidates(state, player);
            if (moves.isEmpty()) {
                // Passing lifts the immediately preceding simple-ko prohibition.
                previousBoard = board;
                passes++;
                player = GoGame.opposite(player);
                continue;
            }

            int topCount = Math.min(6, moves.size());
            Candidate selected;
            if (moves.get(0).captured > 0) {
                selected = moves.get(0);
            } else {
                selected = moves.get(random.nextInt(topCount));
            }
            int[][] boardBeforeMove = board;
            board = selected.board;
            previousBoard = boardBeforeMove;
            player = GoGame.opposite(player);
            passes = 0;
        }
        return whiteReward(board);
    }

    private static double whiteReward(int[][] board) {
        GoGame.Score score = GoGame.calculateScore(board);
        double difference = score.getWhiteScore() - score.getBlackScore();
        return 0.5 + 0.5 * Math.tanh(difference / 12.0);
    }

    private boolean shouldStop() {
        return cancelled || Thread.currentThread().isInterrupted();
    }

    private static boolean coordinatesBefore(MctsNode a, MctsNode b) {
        return a.moveRow < b.moveRow || (a.moveRow == b.moveRow && a.moveCol < b.moveCol);
    }

    private static boolean onBoard(int row, int col) {
        return row >= 0 && row < GoGame.BOARD_SIZE
                && col >= 0 && col < GoGame.BOARD_SIZE;
    }

    private static final class SearchState {
        final int[][] board;
        final int[][] previousBoard;
        final int playerToMove;
        final int consecutivePasses;

        SearchState(int[][] board, int[][] previousBoard, int playerToMove,
                    int consecutivePasses) {
            this.board = board;
            this.previousBoard = previousBoard;
            this.playerToMove = playerToMove;
            this.consecutivePasses = consecutivePasses;
        }
    }

    private static final class Candidate {
        final int row;
        final int col;
        final int[][] board;
        final int captured;
        double score;

        Candidate(int row, int col, int[][] board, int captured, double score) {
            this.row = row;
            this.col = col;
            this.board = board;
            this.captured = captured;
            this.score = score;
        }

        int[] coordinates() {
            return new int[]{row, col};
        }
    }

    private static final class MctsNode {
        final MctsNode parent;
        final SearchState state;
        final int moveRow;
        final int moveCol;
        final List<MctsNode> children = new ArrayList<>();
        List<Candidate> untriedMoves = new ArrayList<>();
        boolean movesInitialized;
        int visits;
        double totalWhiteReward;

        MctsNode(MctsNode parent, SearchState state, int moveRow, int moveCol) {
            this.parent = parent;
            this.state = state;
            this.moveRow = moveRow;
            this.moveCol = moveCol;
        }

        double meanWhiteReward() {
            return visits == 0 ? 0.5 : totalWhiteReward / visits;
        }
    }
}
