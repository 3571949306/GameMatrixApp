package com.gamecenter.app.games.go;

import java.util.ArrayList;
import java.util.List;

public class GoGame {

    public static final int BOARD_SIZE = 9;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final long AI_TIME_LIMIT_MS = 1800;
    private static final int MAX_ROOT_SIMULATIONS = 22000;
    private static final int PLAYOUT_STEP_LIMIT = BOARD_SIZE * BOARD_SIZE * 2;
    private static final double KOMI = 6.5;

    private int[][] board;
    private int currentPlayer;
    private boolean gameOver;
    private int blackCaptures;
    private int whiteCaptures;
    private int passes;
    private List<MoveRecord> moveHistory;
    private int moveCount;
    private int[] lastMove;
    private boolean passedLast;

    public GoGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        blackCaptures = 0;
        whiteCaptures = 0;
        passes = 0;
        passedLast = false;
        moveHistory = new ArrayList<>();
        moveCount = 0;
        lastMove = null;
    }

    public int[][] getBoard() { return board; }
    public int getCurrentPlayer() { return currentPlayer; }
    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean over) { this.gameOver = over; }
    public int getBlackCaptures() { return blackCaptures; }
    public int getWhiteCaptures() { return whiteCaptures; }
    public int[] getLastMove() { return lastMove; }
    public void setLastMove(int x, int y) { this.lastMove = new int[]{x, y}; }
    public void clearLastMove() { this.lastMove = null; }
    public int getMoveCount() { return moveCount; }

    public boolean isValidMove(int x, int y) {
        return isLegalMoveOnBoard(x, y, currentPlayer, board);
    }

    public MoveRecord makeMove(int x, int y) {
        if (!isValidMove(x, y)) return null;

        int captured = applyMoveOnBoard(board, x, y, currentPlayer);
        if (currentPlayer == BLACK) {
            blackCaptures += captured;
        } else {
            whiteCaptures += captured;
        }

        MoveRecord record = new MoveRecord(x, y, currentPlayer, captured);
        moveHistory.add(record);
        moveCount++;
        lastMove = new int[]{x, y};
        passes = 0;
        passedLast = false;
        return record;
    }

    public void pass() {
        passedLast = true;
        passes++;
        lastMove = null;
        if (passes >= 2) {
            gameOver = true;
        }
    }

    private boolean isLegalMoveOnBoard(int x, int y, int player, int[][] b) {
        if (!isInside(x, y) || b[y][x] != EMPTY) return false;
        int[][] testBoard = copyBoard(b);
        int captured = applyMoveOnBoard(testBoard, x, y, player);
        return captured > 0 || hasLiberty(x, y, testBoard);
    }

    private int applyMoveOnBoard(int[][] b, int x, int y, int player) {
        b[y][x] = player;
        return removeCaptured(player, b);
    }

    private int removeCaptured(int player, int[][] b) {
        int total = 0;
        int opponent = opponentOf(player);
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (b[y][x] == opponent && !visited[y][x]) {
                    GroupInfo group = collectGroup(x, y, b, visited);
                    if (group.liberties == 0) {
                        total += group.stones.size();
                        for (int[] stone : group.stones) {
                            b[stone[1]][stone[0]] = EMPTY;
                        }
                    }
                }
            }
        }
        return total;
    }

    private GroupInfo collectGroup(int x, int y, int[][] b, boolean[][] visitedStones) {
        GroupInfo info = new GroupInfo();
        if (!isInside(x, y) || b[y][x] == EMPTY) return info;
        boolean[][] visitedLiberties = new boolean[BOARD_SIZE][BOARD_SIZE];
        collectGroupDfs(x, y, b[y][x], b, visitedStones, visitedLiberties, info);
        return info;
    }

    private void collectGroupDfs(int x, int y, int color, int[][] b,
                                 boolean[][] visitedStones, boolean[][] visitedLiberties,
                                 GroupInfo info) {
        if (!isInside(x, y)) return;
        if (b[y][x] == EMPTY) {
            if (!visitedLiberties[y][x]) {
                visitedLiberties[y][x] = true;
                info.liberties++;
            }
            return;
        }
        if (b[y][x] != color || visitedStones[y][x]) return;

        visitedStones[y][x] = true;
        info.stones.add(new int[]{x, y});
        for (int[] d : DIRS) {
            collectGroupDfs(x + d[0], y + d[1], color, b, visitedStones, visitedLiberties, info);
        }
    }

    private int countLiberties(int x, int y, int[][] b) {
        return collectGroup(x, y, b, new boolean[BOARD_SIZE][BOARD_SIZE]).liberties;
    }

    private boolean hasLiberty(int x, int y, int[][] b) {
        return countLiberties(x, y, b) > 0;
    }

    public void switchPlayer() {
        currentPlayer = opponentOf(currentPlayer);
    }

    public void reset() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        blackCaptures = 0;
        whiteCaptures = 0;
        passes = 0;
        passedLast = false;
        moveHistory.clear();
        moveCount = 0;
        lastMove = null;
    }

    public List<int[]> getLegalMoves() {
        return getLegalMovesOnBoard(board, currentPlayer);
    }

    private List<int[]> getLegalMovesOnBoard(int[][] b, int player) {
        List<int[]> moves = new ArrayList<>();
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (isLegalMoveOnBoard(x, y, player, b)) {
                    moves.add(new int[]{x, y});
                }
            }
        }
        return moves;
    }

    public int[] getRandomMove() {
        List<int[]> moves = getLegalMoves();
        if (moves.isEmpty()) return null;
        return moves.get((int) (Math.random() * moves.size()));
    }

    public int[] getBestMove() {
        List<int[]> legalMoves = getLegalMoves();
        if (legalMoves.isEmpty()) return null;
        if (legalMoves.size() == 1) return legalMoves.get(0);

        List<ScoredMove> candidates = new ArrayList<>();
        for (int[] move : legalMoves) {
            candidates.add(new ScoredMove(move, scoreRootMove(move)));
        }
        candidates.sort((a, b) -> Double.compare(b.prior, a.prior));

        int candidateCount = Math.min(28, candidates.size());
        long deadline = System.currentTimeMillis() + AI_TIME_LIMIT_MS;
        double[] values = new double[candidateCount];
        int[] visits = new int[candidateCount];
        int simulations = 0;

        for (int i = 0; i < candidateCount && System.currentTimeMillis() < deadline; i++) {
            values[i] += simulateRootMove(candidates.get(i).move, deadline);
            visits[i]++;
            simulations++;
        }

        while (System.currentTimeMillis() < deadline && simulations < MAX_ROOT_SIMULATIONS) {
            int idx = selectRootCandidate(candidates, values, visits, candidateCount, simulations);
            values[idx] += simulateRootMove(candidates.get(idx).move, deadline);
            visits[idx]++;
            simulations++;
        }

        int bestIdx = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < candidateCount; i++) {
            double average = visits[i] == 0 ? 0.5 : values[i] / visits[i];
            double finalScore = average * 100.0 + candidates.get(i).prior * 0.03;
            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestIdx = i;
            }
        }
        return candidates.get(bestIdx).move;
    }

    private int selectRootCandidate(List<ScoredMove> candidates, double[] values, int[] visits,
                                    int candidateCount, int totalVisits) {
        int bestIdx = 0;
        double bestValue = -Double.MAX_VALUE;
        double logVisits = Math.log(totalVisits + 1.0);

        for (int i = 0; i < candidateCount; i++) {
            if (visits[i] == 0) return i;
            double average = values[i] / visits[i];
            double exploration = 0.45 * Math.sqrt(logVisits / visits[i]);
            double priorBias = Math.tanh(candidates.get(i).prior / 14.0) * 0.08;
            double value = average + exploration + priorBias;
            if (value > bestValue) {
                bestValue = value;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private double simulateRootMove(int[] move, long deadline) {
        int rootPlayer = currentPlayer;
        int[][] simBoard = copyBoard(board);
        int simBlackCaptures = blackCaptures;
        int simWhiteCaptures = whiteCaptures;

        int captured = applyMoveOnBoard(simBoard, move[0], move[1], rootPlayer);
        if (rootPlayer == BLACK) {
            simBlackCaptures += captured;
        } else {
            simWhiteCaptures += captured;
        }

        double margin = randomPlayout(simBoard, opponentOf(rootPlayer),
                simBlackCaptures, simWhiteCaptures, deadline);
        double perspective = rootPlayer == BLACK ? margin : -margin;
        return 0.5 + Math.tanh(perspective / 10.0) * 0.5;
    }

    private double randomPlayout(int[][] simBoard, int player, int simBlackCaptures,
                                 int simWhiteCaptures, long deadline) {
        int current = player;
        int consecutivePasses = 0;

        for (int step = 0; step < PLAYOUT_STEP_LIMIT; step++) {
            if (System.currentTimeMillis() > deadline) break;

            List<SimMove> moves = buildPlayoutMoves(simBoard, current, false);
            if (moves.isEmpty()) {
                moves = buildPlayoutMoves(simBoard, current, true);
            }

            if (moves.isEmpty()) {
                consecutivePasses++;
                if (consecutivePasses >= 2) break;
            } else {
                consecutivePasses = 0;
                SimMove move = chooseWeightedMove(moves);
                int captured = applyMoveOnBoard(simBoard, move.x, move.y, current);
                if (current == BLACK) {
                    simBlackCaptures += captured;
                } else {
                    simWhiteCaptures += captured;
                }
            }
            current = opponentOf(current);
        }

        return scoreBoardMargin(simBoard, simBlackCaptures, simWhiteCaptures);
    }

    private List<SimMove> buildPlayoutMoves(int[][] simBoard, int player, boolean allowEyes) {
        List<SimMove> moves = new ArrayList<>();
        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                MoveAssessment assessment = assessMoveOnBoard(x, y, player, simBoard);
                if (!assessment.legal) continue;
                if (!allowEyes && assessment.ownEye && assessment.captured == 0) continue;

                int weight = 1 + assessment.captured * 12;
                weight += countAdjacent(x, y, opponentOf(player), simBoard) * 3;
                weight += countAdjacent(x, y, player, simBoard);
                if (isGoodOpeningPoint(x, y)) weight += 2;
                if (isEdge(x, y)) weight = Math.max(1, weight - 1);
                if (assessment.liberties <= 1 && assessment.captured == 0) {
                    weight = Math.max(1, weight / 4);
                }
                moves.add(new SimMove(x, y, weight));
            }
        }
        return moves;
    }

    private SimMove chooseWeightedMove(List<SimMove> moves) {
        int total = 0;
        for (SimMove move : moves) total += move.weight;
        int pick = (int) (Math.random() * total);
        for (SimMove move : moves) {
            pick -= move.weight;
            if (pick < 0) return move;
        }
        return moves.get(moves.size() - 1);
    }

    private double scoreRootMove(int[] move) {
        int player = currentPlayer;
        int[][] testBoard = copyBoard(board);
        int captured = applyMoveOnBoard(testBoard, move[0], move[1], player);
        int testBlackCaptures = blackCaptures + (player == BLACK ? captured : 0);
        int testWhiteCaptures = whiteCaptures + (player == WHITE ? captured : 0);

        double score = evaluateBoardForPlayer(testBoard, testBlackCaptures, testWhiteCaptures, player);
        score += captured * 9.0;
        score += countAdjacent(move[0], move[1], opponentOf(player), board) * 2.5;
        score += countAdjacent(move[0], move[1], player, board) * 1.2;
        if (isGoodOpeningPoint(move[0], move[1])) score += moveCount < 12 ? 5.0 : 1.5;
        if (isTrueCorner(move[0], move[1])) score -= 3.0;

        int liberties = countLiberties(move[0], move[1], testBoard);
        if (liberties <= 1 && captured == 0) score -= 9.0;
        if (liberties >= 3) score += 1.5;
        return score;
    }

    private MoveAssessment assessMoveOnBoard(int x, int y, int player, int[][] b) {
        MoveAssessment assessment = new MoveAssessment();
        if (!isInside(x, y) || b[y][x] != EMPTY) return assessment;

        int[][] testBoard = copyBoard(b);
        int captured = applyMoveOnBoard(testBoard, x, y, player);
        int liberties = countLiberties(x, y, testBoard);
        if (captured == 0 && liberties == 0) return assessment;

        assessment.legal = true;
        assessment.captured = captured;
        assessment.liberties = liberties;
        assessment.ownEye = isOwnEye(x, y, player, b);
        return assessment;
    }

    private double evaluateBoardForPlayer(int[][] b, int blackCaptured, int whiteCaptured, int player) {
        double margin = scoreBoardMargin(b, blackCaptured, whiteCaptured);
        return player == BLACK ? margin : -margin;
    }

    private double scoreBoardMargin(int[][] b, int blackCaptured, int whiteCaptured) {
        double blackScore = blackCaptured;
        double whiteScore = whiteCaptured + KOMI;
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int y = 0; y < BOARD_SIZE; y++) {
            for (int x = 0; x < BOARD_SIZE; x++) {
                if (b[y][x] == BLACK) {
                    blackScore++;
                } else if (b[y][x] == WHITE) {
                    whiteScore++;
                } else if (!visited[y][x]) {
                    Territory territory = countTerritory(x, y, b, visited);
                    if (territory.owner == BLACK) {
                        blackScore += territory.size;
                    } else if (territory.owner == WHITE) {
                        whiteScore += territory.size;
                    }
                }
            }
        }
        return blackScore - whiteScore;
    }

    private Territory countTerritory(int x, int y, int[][] b, boolean[][] visited) {
        Territory territory = new Territory();
        exploreRegion(x, y, b, visited, territory);
        if (territory.touchesBlack && !territory.touchesWhite) {
            territory.owner = BLACK;
        } else if (!territory.touchesBlack && territory.touchesWhite) {
            territory.owner = WHITE;
        }
        return territory;
    }

    private void exploreRegion(int x, int y, int[][] b, boolean[][] visited, Territory territory) {
        if (!isInside(x, y)) return;
        if (b[y][x] == BLACK) {
            territory.touchesBlack = true;
            return;
        }
        if (b[y][x] == WHITE) {
            territory.touchesWhite = true;
            return;
        }
        if (visited[y][x]) return;

        visited[y][x] = true;
        territory.size++;
        for (int[] d : DIRS) {
            exploreRegion(x + d[0], y + d[1], b, visited, territory);
        }
    }

    private boolean isOwnEye(int x, int y, int player, int[][] b) {
        for (int[] d : DIRS) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (isInside(nx, ny) && b[ny][nx] != player) {
                return false;
            }
        }
        return true;
    }

    private int countAdjacent(int x, int y, int player, int[][] b) {
        int count = 0;
        for (int[] d : DIRS) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (isInside(nx, ny) && b[ny][nx] == player) count++;
        }
        return count;
    }

    private boolean isGoodOpeningPoint(int x, int y) {
        int center = BOARD_SIZE / 2;
        if (x == center && y == center) return true;
        int low = 2;
        int high = BOARD_SIZE - 3;
        return (x == low || x == high) && (y == low || y == high);
    }

    private boolean isTrueCorner(int x, int y) {
        return (x == 0 || x == BOARD_SIZE - 1) && (y == 0 || y == BOARD_SIZE - 1);
    }

    private boolean isEdge(int x, int y) {
        return x == 0 || y == 0 || x == BOARD_SIZE - 1 || y == BOARD_SIZE - 1;
    }

    private boolean isInside(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
    }

    private int opponentOf(int player) {
        return player == BLACK ? WHITE : BLACK;
    }

    private int[][] copyBoard(int[][] src) {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            System.arraycopy(src[i], 0, copy[i], 0, BOARD_SIZE);
        }
        return copy;
    }

    public static class MoveRecord {
        public int x, y;
        public int player;
        public int captured;

        public MoveRecord(int x, int y, int player, int captured) {
            this.x = x;
            this.y = y;
            this.player = player;
            this.captured = captured;
        }
    }

    private static class GroupInfo {
        final List<int[]> stones = new ArrayList<>();
        int liberties;
    }

    private static class Territory {
        int size;
        int owner;
        boolean touchesBlack;
        boolean touchesWhite;
    }

    private static class ScoredMove {
        final int[] move;
        final double prior;

        ScoredMove(int[] move, double prior) {
            this.move = move;
            this.prior = prior;
        }
    }

    private static class SimMove {
        final int x;
        final int y;
        final int weight;

        SimMove(int x, int y, int weight) {
            this.x = x;
            this.y = y;
            this.weight = weight;
        }
    }

    private static class MoveAssessment {
        boolean legal;
        int captured;
        int liberties;
        boolean ownEye;
    }
}
