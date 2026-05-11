package com.gamecenter.app.games.sokoban;

import java.util.Random;

public class SokobanGame {
    public static final int WALL = 1;
    public static final int FLOOR = 0;
    public static final int BOX = 2;
    public static final int TARGET = 3;
    public static final int BOX_ON_TARGET = 4;
    public static final int PLAYER = 5;
    public static final int PLAYER_ON_TARGET = 6;

    private int[][] map;
    private int playerX, playerY;
    private int rows, cols;
    private int moves = 0;
    private int levelsCompleted = 0;

    private static final int[][][] LEVELS = {
        {
            {1,1,1,1,1,1,1},
            {1,0,0,0,0,0,1},
            {1,0,2,0,2,0,1},
            {1,0,0,3,0,0,1},
            {1,0,2,0,0,0,1},
            {1,0,0,0,3,0,1},
            {1,1,1,1,1,1,1}
        },
        {
            {1,1,1,1,1,1,1,1},
            {1,0,0,0,0,0,0,1},
            {1,0,2,2,0,0,0,1},
            {1,0,0,0,0,3,0,1},
            {1,0,2,0,3,0,0,1},
            {1,0,0,0,0,3,0,1},
            {1,0,0,0,0,0,0,1},
            {1,1,1,1,1,1,1,1}
        },
        {
            {0,1,1,1,1,1,0},
            {1,1,0,0,0,1,1},
            {1,0,0,2,0,0,1},
            {1,0,3,0,3,0,1},
            {1,0,0,2,0,0,1},
            {1,1,0,0,0,1,1},
            {0,1,1,1,1,1,0}
        }
    };

    public SokobanGame() {
        loadLevel(0);
    }

    public void loadLevel(int levelIndex) {
        if (levelIndex >= LEVELS.length) {
            levelIndex = 0;
            levelsCompleted++;
        }
        int[][] level = LEVELS[levelIndex];
        rows = level.length;
        cols = level[0].length;
        map = new int[rows][cols];

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                map[y][x] = level[y][x];
                if (map[y][x] == PLAYER || map[y][x] == PLAYER_ON_TARGET) {
                    playerX = x;
                    playerY = y;
                    map[y][x] = (levelIndex == 2 && (y == 3 && x == 3)) ? TARGET : FLOOR;
                }
            }
        }

        int playerCount = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (level[y][x] == PLAYER || level[y][x] == PLAYER_ON_TARGET) {
                    playerX = x;
                    playerY = y;
                    playerCount++;
                }
            }
        }

        if (playerCount == 0) {
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (map[y][x] == FLOOR || map[y][x] == TARGET) {
                        playerX = x;
                        playerY = y;
                        break;
                    }
                }
                break;
            }
        }

        moves = 0;
    }

    public boolean move(int dx, int dy) {
        int newX = playerX + dx;
        int newY = playerY + dy;

        if (newX < 0 || newX >= cols || newY < 0 || newY >= rows) return false;
        if (map[newY][newX] == WALL) return false;

        int targetTile = map[newY][newX];
        if (targetTile == BOX || targetTile == BOX_ON_TARGET) {
            int boxNewX = newX + dx;
            int boxNewY = newY + dy;

            if (boxNewX < 0 || boxNewX >= cols || boxNewY < 0 || boxNewY >= rows) return false;
            if (map[boxNewY][boxNewX] == WALL || map[boxNewY][boxNewX] == BOX || map[boxNewY][boxNewX] == BOX_ON_TARGET) return false;

            if (targetTile == BOX) {
                map[newY][newX] = TARGET;
            } else {
                map[newY][newX] = TARGET;
            }

            if (map[boxNewY][boxNewX] == TARGET) {
                map[boxNewY][boxNewX] = BOX_ON_TARGET;
            } else {
                map[boxNewY][boxNewX] = BOX;
            }
        }

        playerX = newX;
        playerY = newY;
        moves++;
        return true;
    }

    public boolean isLevelComplete() {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (map[y][x] == BOX) return false;
            }
        }
        return true;
    }

    public int getTile(int x, int y) {
        if (x == playerX && y == playerY) {
            return (map[y][x] == TARGET) ? PLAYER_ON_TARGET : PLAYER;
        }
        return map[y][x];
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public int getPlayerX() { return playerX; }
    public int getPlayerY() { return playerY; }
    public int getMoves() { return moves; }
    public int getLevelsCompleted() { return levelsCompleted; }
    public int getCurrentLevel() { return levelsCompleted; }

    public void reset() {
        loadLevel(levelsCompleted);
    }

    public void nextLevel() {
        levelsCompleted++;
        loadLevel(levelsCompleted);
    }

    public void setMap(int[][] newMap, int px, int py) {
        this.map = newMap;
        this.playerX = px;
        this.playerY = py;
    }

    public int getLevelCount() {
        return LEVELS.length;
    }

    public String serializeState() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"level\":").append(levelsCompleted);
        sb.append(",\"moves\":").append(moves);
        sb.append(",\"playerX\":").append(playerX);
        sb.append(",\"playerY\":").append(playerY);
        sb.append(",\"rows\":").append(rows);
        sb.append(",\"cols\":").append(cols);
        sb.append(",\"map\":[");
        for (int y = 0; y < rows; y++) {
            if (y > 0) sb.append(",");
            sb.append("[");
            for (int x = 0; x < cols; x++) {
                if (x > 0) sb.append(",");
                sb.append(map[y][x]);
            }
            sb.append("]");
        }
        sb.append("]}");
        return sb.toString();
    }

    public boolean restoreState(String json) {
        if (json == null || json.trim().isEmpty()) return false;
        try {
            org.json.JSONObject obj = new org.json.JSONObject(json);
            int level = obj.getInt("level");
            int savedMoves = obj.getInt("moves");
            int px = obj.getInt("playerX");
            int py = obj.getInt("playerY");
            int savedRows = obj.getInt("rows");
            int savedCols = obj.getInt("cols");
            org.json.JSONArray mapArr = obj.getJSONArray("map");
            int[][] savedMap = new int[savedRows][savedCols];
            for (int y = 0; y < savedRows; y++) {
                org.json.JSONArray row = mapArr.getJSONArray(y);
                for (int x = 0; x < savedCols; x++) {
                    savedMap[y][x] = row.getInt(x);
                }
            }
            this.levelsCompleted = level;
            this.map = savedMap;
            this.rows = savedRows;
            this.cols = savedCols;
            this.playerX = px;
            this.playerY = py;
            this.moves = savedMoves;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}