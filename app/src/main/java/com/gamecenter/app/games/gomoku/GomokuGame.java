package com.gamecenter.app.games.gomoku;

import java.util.ArrayList;
import java.util.List;

public class GomokuGame {

    public static final int BOARD_SIZE = 15;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    public static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private int[][] board;
    private int currentPlayer;
    private boolean gameOver;
    private Integer winner;
    private List<MoveRecord> moveHistory;
    private int moveCount;
    private int[] lastMove;

    public GomokuGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        winner = null;
        moveHistory = new ArrayList<>();
        moveCount = 0;
        lastMove = null;
    }

    public int[][] getBoard() {
        return board;
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public void setCurrentPlayer(int player) {
        this.currentPlayer = player;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public Integer getWinner() {
        return winner;
    }

    public void setGameOver(int winnerPlayer) {
        this.gameOver = true;
        this.winner = winnerPlayer;
    }

    public int[] getLastMove() {
        return lastMove;
    }

    public List<MoveRecord> getMoveHistory() {
        return moveHistory;
    }

    public int getMoveCount() {
        return moveCount;
    }

    public boolean isValidMove(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE && board[y][x] == EMPTY;
    }

    public MoveRecord makeMove(int x, int y, int player) {
        if (!isValidMove(x, y)) return null;
        board[y][x] = player;
        MoveRecord record = new MoveRecord(x, y, player);
        moveHistory.add(record);
        moveCount++;
        lastMove = new int[]{x, y};
        return record;
    }

    private void undoMove(MoveRecord record) {
        board[record.y][record.x] = EMPTY;
        moveCount--;
        if (!moveHistory.isEmpty()) {
            MoveRecord last = moveHistory.get(moveHistory.size() - 1);
            lastMove = new int[]{last.x, last.y};
        } else {
            lastMove = null;
        }
    }

    public int undoLastMoves(int count) {
        int undoCount = Math.min(count, moveCount / 2);
        for (int i = 0; i < undoCount; i++) {
            if (moveHistory.size() >= 2) {
                MoveRecord aiRecord = moveHistory.remove(moveHistory.size() - 1);
                undoMove(aiRecord);
                MoveRecord playerRecord = moveHistory.remove(moveHistory.size() - 1);
                undoMove(playerRecord);
            }
        }
        return undoCount;
    }

    public boolean checkWinAt(int x, int y, int player) {
        if (player == EMPTY) return false;
        for (int[] dir : DIRECTIONS) {
            int count = 1;
            for (int step = 1; step < 5; step++) {
                int nx = x + dir[0] * step;
                int ny = y + dir[1] * step;
                if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE && board[ny][nx] == player) {
                    count++;
                } else break;
            }
            for (int step = 1; step < 5; step++) {
                int nx = x - dir[0] * step;
                int ny = y - dir[1] * step;
                if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE && board[ny][nx] == player) {
                    count++;
                } else break;
            }
            if (count >= 5) return true;
        }
        return false;
    }

    public boolean checkGameOver() {
        if (lastMove != null) {
            int x = lastMove[0];
            int y = lastMove[1];
            int player = board[y][x];
            if (checkWinAt(x, y, player)) {
                gameOver = true;
                winner = player;
                return true;
            }
        }
        if (moveCount >= BOARD_SIZE * BOARD_SIZE) {
            gameOver = true;
            winner = null;
            return true;
        }
        return false;
    }

    public void switchPlayer() {
        currentPlayer = (currentPlayer == BLACK) ? WHITE : BLACK;
    }

    public void reset() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        winner = null;
        moveHistory.clear();
        moveCount = 0;
        lastMove = null;
    }

    public static class MoveRecord {
        public int x, y;
        public int player;

        public MoveRecord(int x, int y, int player) {
            this.x = x;
            this.y = y;
            this.player = player;
        }
    }
}
