package com.gamecenter.app.games.gomoku;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GomokuAI {

    private static final int[] LEVEL_TIME_MS = {
            500,
            1500,
            3000,
            5000,
            7000,
            10000
    };

    private static final int MAX_DEPTH = 10;
    private static final int TIME_CHECK_INTERVAL = 256;
    private static final int WIN_SCORE = 10_000_000;

    private final int maxTimeMs;
    private long searchStartMs;
    private boolean timedOut;
    private int nodesSearched;

    public GomokuAI(int level) {
        int idx = Math.max(0, Math.min(level - 1, LEVEL_TIME_MS.length - 1));
        this.maxTimeMs = LEVEL_TIME_MS[idx];
    }

    private boolean checkTimeout() {
        if (System.currentTimeMillis() - searchStartMs > maxTimeMs) {
            timedOut = true;
            return true;
        }
        return false;
    }

    private Threat evaluateMoveThreat(int x, int y, int player, int[][] board) {
        Threat threat = new Threat();
        if (player == GomokuGame.EMPTY) return threat;

        for (int[] dir : GomokuGame.DIRECTIONS) {
            for (int offset = -4; offset <= 0; offset++) {
                int startX = x + dir[0] * offset;
                int startY = y + dir[1] * offset;
                int stones = 0;
                int empty = 0;
                boolean blocked = false;

                for (int i = 0; i < 5; i++) {
                    int cx = startX + dir[0] * i;
                    int cy = startY + dir[1] * i;
                    if (!isInside(cx, cy)) {
                        blocked = true;
                        break;
                    }
                    int cell = board[cy][cx];
                    if (cell == player) {
                        stones++;
                    } else if (cell == GomokuGame.EMPTY) {
                        empty++;
                    } else {
                        blocked = true;
                        break;
                    }
                }

                if (blocked) continue;

                int beforeX = startX - dir[0];
                int beforeY = startY - dir[1];
                int afterX = startX + dir[0] * 5;
                int afterY = startY + dir[1] * 5;
                int openEnds = (isEmpty(board, beforeX, beforeY) ? 1 : 0)
                        + (isEmpty(board, afterX, afterY) ? 1 : 0);

                addWindowScore(threat, stones, empty, openEnds);
            }
        }

        if (threat.openFours > 0) threat.score += 1_500_000;
        if (threat.fours >= 2) threat.score += 1_200_000;
        if (threat.openThrees >= 2) threat.score += 120_000;
        return threat;
    }

    private void addWindowScore(Threat threat, int stones, int empty, int openEnds) {
        if (stones >= 5) {
            threat.wins++;
            threat.score += WIN_SCORE;
        } else if (stones == 4 && empty == 1) {
            threat.fours++;
            if (openEnds == 2) {
                threat.openFours++;
                threat.score += 900_000;
            } else {
                threat.score += 180_000;
            }
        } else if (stones == 3 && empty == 2) {
            if (openEnds == 2) {
                threat.openThrees++;
                threat.score += 35_000;
            } else if (openEnds == 1) {
                threat.score += 4_000;
            } else {
                threat.score += 800;
            }
        } else if (stones == 2 && empty == 3) {
            threat.score += openEnds == 2 ? 1_500 : 250;
        } else if (stones == 1 && empty == 4) {
            threat.score += openEnds == 2 ? 80 : 10;
        }
    }

    private int evaluatePosition(int x, int y, int player, int[][] board) {
        Threat threat = evaluateMoveThreat(x, y, player, board);
        return threat.score + centerBias(x, y);
    }

    private int evaluate(int[][] board, int aiPlayer) {
        int humanPlayer = getOpponent(aiPlayer);
        int score = 0;
        for (int y = 0; y < GomokuGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GomokuGame.BOARD_SIZE; x++) {
                if (board[y][x] == aiPlayer) {
                    score += evaluatePosition(x, y, aiPlayer, board);
                } else if (board[y][x] == humanPlayer) {
                    score -= (int) (evaluatePosition(x, y, humanPlayer, board) * 1.18);
                }
            }
        }
        return score;
    }

    private List<int[]> getCandidateMoves(int[][] board) {
        List<int[]> moves = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        boolean hasPiece = false;

        for (int y = 0; y < GomokuGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GomokuGame.BOARD_SIZE; x++) {
                if (board[y][x] == GomokuGame.EMPTY) continue;
                hasPiece = true;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (isInside(nx, ny) && board[ny][nx] == GomokuGame.EMPTY) {
                            long key = ((long) ny << 32) | (nx & 0xFFFFFFFFL);
                            if (seen.add(key)) {
                                moves.add(new int[]{nx, ny});
                            }
                        }
                    }
                }
            }
        }

        if (!hasPiece) {
            moves.add(new int[]{GomokuGame.BOARD_SIZE / 2, GomokuGame.BOARD_SIZE / 2});
        }
        return moves;
    }

    private boolean checkWinAt(int x, int y, int player, int[][] board) {
        if (player == GomokuGame.EMPTY) return false;
        for (int[] dir : GomokuGame.DIRECTIONS) {
            int count = 1;
            for (int step = 1; step < 5; step++) {
                int nx = x + dir[0] * step;
                int ny = y + dir[1] * step;
                if (isInside(nx, ny) && board[ny][nx] == player) {
                    count++;
                } else {
                    break;
                }
            }
            for (int step = 1; step < 5; step++) {
                int nx = x - dir[0] * step;
                int ny = y - dir[1] * step;
                if (isInside(nx, ny) && board[ny][nx] == player) {
                    count++;
                } else {
                    break;
                }
            }
            if (count >= 5) return true;
        }
        return false;
    }

    private double minimax(int[][] board, int depth, double alpha, double beta,
                           boolean isMaximizing, int aiPlayer, int[] lastMoveInfo) {
        nodesSearched++;
        if ((nodesSearched & (TIME_CHECK_INTERVAL - 1)) == 0 && checkTimeout()) {
            return evaluate(board, aiPlayer);
        }
        if (lastMoveInfo != null && checkWinAt(lastMoveInfo[0], lastMoveInfo[1], lastMoveInfo[2], board)) {
            return (lastMoveInfo[2] == aiPlayer ? 1 : -1) * WIN_SCORE * (depth + 1);
        }
        if (depth == 0) return evaluate(board, aiPlayer);

        int player = isMaximizing ? aiPlayer : getOpponent(aiPlayer);
        int limit = depth >= 4 ? 10 : 12;
        List<int[]> topMoves = scoreAndSortMoves(getCandidateMoves(board), board, player, limit);
        if (topMoves.isEmpty()) return evaluate(board, aiPlayer);

        if (isMaximizing) {
            double maxEval = -Double.MAX_VALUE;
            for (int[] move : topMoves) {
                if (timedOut) break;
                board[move[1]][move[0]] = player;
                double eval = minimax(board, depth - 1, alpha, beta, false, aiPlayer,
                        new int[]{move[0], move[1], player});
                board[move[1]][move[0]] = GomokuGame.EMPTY;
                maxEval = Math.max(maxEval, eval);
                alpha = Math.max(alpha, eval);
                if (beta <= alpha || timedOut) break;
            }
            return maxEval;
        }

        double minEval = Double.MAX_VALUE;
        for (int[] move : topMoves) {
            if (timedOut) break;
            board[move[1]][move[0]] = player;
            double eval = minimax(board, depth - 1, alpha, beta, true, aiPlayer,
                    new int[]{move[0], move[1], player});
            board[move[1]][move[0]] = GomokuGame.EMPTY;
            minEval = Math.min(minEval, eval);
            beta = Math.min(beta, eval);
            if (beta <= alpha || timedOut) break;
        }
        return minEval;
    }

    private List<int[]> scoreAndSortMoves(List<int[]> moves, int[][] board, int player, int limit) {
        moves.sort((a, b) -> Integer.compare(
                scoreMoveForPlayer(b[0], b[1], player, board),
                scoreMoveForPlayer(a[0], a[1], player, board)));

        List<int[]> result = new ArrayList<>();
        int count = Math.min(limit, moves.size());
        for (int i = 0; i < count; i++) {
            result.add(moves.get(i));
        }
        return result;
    }

    private int scoreMoveForPlayer(int x, int y, int player, int[][] board) {
        int opponent = getOpponent(player);

        board[y][x] = player;
        Threat attack = evaluateMoveThreat(x, y, player, board);
        boolean winsNow = checkWinAt(x, y, player, board);
        board[y][x] = GomokuGame.EMPTY;

        board[y][x] = opponent;
        Threat defense = evaluateMoveThreat(x, y, opponent, board);
        boolean blocksWin = checkWinAt(x, y, opponent, board);
        board[y][x] = GomokuGame.EMPTY;

        int score = attack.score + (int) (defense.score * 1.25) + centerBias(x, y);
        if (winsNow) score += WIN_SCORE;
        if (blocksWin) score += WIN_SCORE / 2;
        if (attack.openFours > 0 || attack.fours >= 2) score += 1_000_000;
        if (defense.openFours > 0 || defense.fours >= 2) score += 1_200_000;
        if (attack.openThrees >= 2) score += 90_000;
        if (defense.openThrees >= 2) score += 110_000;
        return score;
    }

    public int[] getBestMove(GomokuGame game, int aiPlayer) {
        nodesSearched = 0;
        timedOut = false;
        searchStartMs = System.currentTimeMillis();

        int[][] board = copyBoard(game.getBoard());
        List<int[]> moves = getCandidateMoves(board);
        if (moves.isEmpty()) return null;

        int humanPlayer = getOpponent(aiPlayer);
        int[] forcedMove = findImmediateWin(moves, board, aiPlayer);
        if (forcedMove != null) return forcedMove;

        forcedMove = findImmediateWin(moves, board, humanPlayer);
        if (forcedMove != null) return forcedMove;

        forcedMove = findMajorThreat(moves, board, humanPlayer);
        if (forcedMove != null) return forcedMove;

        forcedMove = findMajorThreat(moves, board, aiPlayer);
        if (forcedMove != null) return forcedMove;

        List<int[]> orderedMoves = scoreAndSortMoves(moves, board, aiPlayer, moves.size());
        int[] bestMove = orderedMoves.get(0);

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (timedOut || checkTimeout()) break;

            int[] depthBest = null;
            double depthBestScore = -Double.MAX_VALUE;
            int topCount = Math.min(depth >= 5 ? 8 : 10, orderedMoves.size());

            for (int i = 0; i < topCount; i++) {
                if (timedOut) break;
                int[] move = orderedMoves.get(i);
                board[move[1]][move[0]] = aiPlayer;
                double eval = minimax(board, depth - 1, -Double.MAX_VALUE, Double.MAX_VALUE,
                        false, aiPlayer, new int[]{move[0], move[1], aiPlayer});
                board[move[1]][move[0]] = GomokuGame.EMPTY;
                if (eval > depthBestScore) {
                    depthBestScore = eval;
                    depthBest = new int[]{move[0], move[1]};
                }
            }

            if (depthBest != null) {
                bestMove = depthBest;
            }
        }

        return bestMove;
    }

    private int[] findImmediateWin(List<int[]> moves, int[][] board, int player) {
        for (int[] move : scoreAndSortMoves(new ArrayList<>(moves), board, player, moves.size())) {
            board[move[1]][move[0]] = player;
            boolean wins = checkWinAt(move[0], move[1], player, board);
            board[move[1]][move[0]] = GomokuGame.EMPTY;
            if (wins) {
                return new int[]{move[0], move[1]};
            }
        }
        return null;
    }

    private int[] findMajorThreat(List<int[]> moves, int[][] board, int player) {
        int[] best = null;
        int bestScore = 0;
        for (int[] move : moves) {
            board[move[1]][move[0]] = player;
            Threat threat = evaluateMoveThreat(move[0], move[1], player, board);
            board[move[1]][move[0]] = GomokuGame.EMPTY;

            boolean major = threat.openFours > 0 || threat.fours >= 2 || threat.openThrees >= 2;
            if (major && threat.score > bestScore) {
                bestScore = threat.score;
                best = move;
            }
        }
        return best == null ? null : new int[]{best[0], best[1]};
    }

    private int centerBias(int x, int y) {
        int center = GomokuGame.BOARD_SIZE / 2;
        int distance = Math.abs(x - center) + Math.abs(y - center);
        return Math.max(0, 40 - distance * 3);
    }

    private boolean isInside(int x, int y) {
        return x >= 0 && x < GomokuGame.BOARD_SIZE && y >= 0 && y < GomokuGame.BOARD_SIZE;
    }

    private boolean isEmpty(int[][] board, int x, int y) {
        return isInside(x, y) && board[y][x] == GomokuGame.EMPTY;
    }

    private int getOpponent(int player) {
        return player == GomokuGame.BLACK ? GomokuGame.WHITE : GomokuGame.BLACK;
    }

    private int[][] copyBoard(int[][] board) {
        int[][] copy = new int[GomokuGame.BOARD_SIZE][GomokuGame.BOARD_SIZE];
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, GomokuGame.BOARD_SIZE);
        }
        return copy;
    }

    private static class Threat {
        int score;
        int wins;
        int fours;
        int openFours;
        int openThrees;
    }
}
