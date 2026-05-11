package com.gamecenter.app.games.tiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TilesGame {

    public static final int COLUMNS = 4;

    private int rows;
    private List<Row> tileRows;
    private Random random;
    private int score;
    private boolean gameOver;
    private boolean started;

    private float gameWidth;
    private float gameHeight;
    private float cellSize;
    private float totalScroll;
    private float scrollSpeed;

    public static class Row {
        boolean[] isBlack;
        int touchedCol;

        Row(boolean[] isBlack) {
            this.isBlack = isBlack;
            this.touchedCol = -1;
        }
    }

    public TilesGame() {
        random = new Random();
        tileRows = new ArrayList<>();
        reset();
    }

    public void setGameArea(float width, float height) {
        this.gameWidth = width;
        this.gameHeight = height;
        cellSize = width / COLUMNS;
        rows = Math.max(6, (int) (height / cellSize) + 2);
        scrollSpeed = cellSize * 0.04f;
    }

    public void reset() {
        tileRows.clear();
        score = 0;
        gameOver = false;
        started = false;
        totalScroll = 0;
        if (gameHeight > 0) {
            for (int i = 0; i < rows + 2; i++) {
                tileRows.add(generateRow());
            }
        }
    }

    private Row generateRow() {
        boolean[] isBlack = new boolean[COLUMNS];
        int blackCol = random.nextInt(COLUMNS);
        for (int i = 0; i < COLUMNS; i++) {
            isBlack[i] = (i == blackCol);
        }
        return new Row(isBlack);
    }

    public void touch(int colIndex) {
        if (gameOver) return;
        if (colIndex < 0 || colIndex >= COLUMNS) return;

        started = true;

        float topRowOffset = totalScroll % cellSize;
        int displayRows = (int) (gameHeight / cellSize) + 1;
        float topY = -topRowOffset + gameHeight - cellSize * 3;

        for (int r = 0; r < tileRows.size(); r++) {
            float rowY = topY - r * cellSize;
            if (rowY >= gameHeight - cellSize * 3 && rowY < gameHeight) {
                Row row = tileRows.get(r);
                if (!row.isBlack[colIndex]) {
                    gameOver = true;
                    return;
                }
                row.touchedCol = colIndex;
                score++;
                return;
            }
        }
        gameOver = true;
    }

    public void update() {
        if (gameOver || !started) return;
        totalScroll += scrollSpeed;
    }

    public List<Row> getTileRows() {
        return tileRows;
    }

    public float getTotalScroll() {
        return totalScroll;
    }

    public int getScore() {
        return score;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isStarted() {
        return started;
    }

    public float getCellSize() {
        return cellSize;
    }

    public int getColumns() {
        return COLUMNS;
    }
}
