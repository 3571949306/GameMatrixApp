package com.gamecenter.app.games.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MemoryGame {

    public static final int PAIRS = 8;
    public static final int COLS = 4;
    public static final int ROWS = PAIRS * 2 / COLS;

    private int[][] board;
    private boolean[][] revealed;
    private boolean[][] matched;
    private int score;
    private int matchCount;
    private boolean gameOver;
    private Random random;

    private int firstX = -1;
    private int firstY = -1;
    private int secondX = -1;
    private int secondY = -1;
    private boolean waiting;
    private boolean justMatched;

    public MemoryGame() {
        random = new Random();
        board = new int[ROWS][COLS];
        revealed = new boolean[ROWS][COLS];
        matched = new boolean[ROWS][COLS];
        reset();
    }

    public void reset() {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < PAIRS; i++) {
            values.add(i);
            values.add(i);
        }
        Collections.shuffle(values, random);

        int idx = 0;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                board[y][x] = values.get(idx++);
                revealed[y][x] = false;
                matched[y][x] = false;
            }
        }

        score = 0;
        matchCount = 0;
        gameOver = false;
        firstX = -1;
        firstY = -1;
        secondX = -1;
        secondY = -1;
        waiting = false;
        justMatched = false;
    }

    public boolean canFlip(int x, int y) {
        if (gameOver) return false;
        if (waiting) return false;
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return false;
        if (revealed[y][x]) return false;
        return true;
    }

    public boolean flipCard(int x, int y) {
        if (!canFlip(x, y)) return false;
        revealed[y][x] = true;
        justMatched = false;

        if (firstX < 0) {
            firstX = x;
            firstY = y;
            return true;
        }

        secondX = x;
        secondY = y;

        if (board[firstY][firstX] == board[y][x]) {
            justMatched = true;
            firstX = -1;
            firstY = -1;
            secondX = -1;
            secondY = -1;
        } else {
            waiting = true;
        }
        return true;
    }

    public boolean lastMatchSuccessful() {
        return justMatched;
    }

    public void confirmMatch() {
        if (!justMatched) return;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (revealed[y][x] && !matched[y][x]) {
                    matched[y][x] = true;
                    matchCount++;
                    score += 10;
                }
            }
        }
        justMatched = false;
        if (matchCount == PAIRS) {
            gameOver = true;
        }
    }

    public void hideMismatch() {
        if (firstX >= 0 && secondX >= 0) {
            revealed[firstY][firstX] = false;
            revealed[secondY][secondX] = false;
        }
        firstX = -1;
        firstY = -1;
        secondX = -1;
        secondY = -1;
        waiting = false;
    }

    public int getCardValue(int x, int y) {
        if (y < 0 || y >= ROWS || x < 0 || x >= COLS) return -1;
        return board[y][x];
    }

    public boolean isRevealed(int x, int y) {
        if (y < 0 || y >= ROWS || x < 0 || x >= COLS) return false;
        return revealed[y][x] || matched[y][x];
    }

    public boolean isMatched(int x, int y) {
        if (y < 0 || y >= ROWS || x < 0 || x >= COLS) return false;
        return matched[y][x];
    }

    public boolean isWaiting() { return waiting; }
    public int getScore() { return score; }
    public int getMatched() { return matchCount; }
    public boolean isGameOver() { return gameOver; }
    public int getRows() { return ROWS; }
    public int getCols() { return COLS; }
}
