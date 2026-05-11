package com.gamecenter.app.games.tic;

import java.util.Random;

public class TicGame {

    public static final int EMPTY = 0;
    public static final int PLAYER = 1;
    public static final int COMPUTER = 2;

    private int[][] board;
    private int currentTurn;
    private boolean gameOver;
    private int winner;

    private Random random;

    public TicGame() {
        board = new int[3][3];
        random = new Random();
        reset();
    }

    public void reset() {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                board[y][x] = EMPTY;
            }
        }
        currentTurn = PLAYER;
        gameOver = false;
        winner = EMPTY;
    }

    public boolean placePiece(int x, int y) {
        if (gameOver) return false;
        if (x < 0 || x > 2 || y < 0 || y > 2) return false;
        if (board[y][x] != EMPTY) return false;
        if (currentTurn != PLAYER) return false;

        board[y][x] = PLAYER;
        checkWin();
        if (!gameOver) {
            currentTurn = COMPUTER;
        }
        return true;
    }

    public void computerMove() {
        if (gameOver || currentTurn != COMPUTER) return;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (board[y][x] == EMPTY) {
                    board[y][x] = COMPUTER;
                    if (checkWinImmediate(COMPUTER)) {
                        checkWin();
                        currentTurn = PLAYER;
                        return;
                    }
                    board[y][x] = EMPTY;
                }
            }
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (board[y][x] == EMPTY) {
                    board[y][x] = PLAYER;
                    if (checkWinImmediate(PLAYER)) {
                        board[y][x] = COMPUTER;
                        checkWin();
                        currentTurn = PLAYER;
                        return;
                    }
                    board[y][x] = EMPTY;
                }
            }
        }

        if (board[1][1] == EMPTY) {
            board[1][1] = COMPUTER;
        } else {
            int[][] corners = {{0, 0}, {2, 0}, {0, 2}, {2, 2}};
            for (int[] c : corners) {
                if (board[c[1]][c[0]] == EMPTY) {
                    board[c[1]][c[0]] = COMPUTER;
                    checkWin();
                    currentTurn = PLAYER;
                    return;
                }
            }
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    if (board[y][x] == EMPTY) {
                        board[y][x] = COMPUTER;
                        checkWin();
                        currentTurn = PLAYER;
                        return;
                    }
                }
            }
        }

        checkWin();
        currentTurn = PLAYER;
    }

    private boolean checkWinImmediate(int player) {
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == player && board[i][1] == player && board[i][2] == player) return true;
            if (board[0][i] == player && board[1][i] == player && board[2][i] == player) return true;
        }
        if (board[0][0] == player && board[1][1] == player && board[2][2] == player) return true;
        if (board[0][2] == player && board[1][1] == player && board[2][0] == player) return true;
        return false;
    }

    private void checkWin() {
        for (int player : new int[]{PLAYER, COMPUTER}) {
            if (checkWinImmediate(player)) {
                gameOver = true;
                winner = player;
                return;
            }
        }
        boolean full = true;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (board[y][x] == EMPTY) {
                    full = false;
                    break;
                }
            }
        }
        if (full) {
            gameOver = true;
            winner = EMPTY;
        }
    }

    public int[][] getBoard() { return board; }
    public int getCurrentTurn() { return currentTurn; }
    public boolean isGameOver() { return gameOver; }
    public int getWinner() { return winner; }
}
