package com.gamecenter.app.sokoban;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 推箱子游戏逻辑类（独立 APK 模块版本）。
 *
 * <p>由宿主 SokobanActivity 的游戏逻辑提取而来。持有地图、玩家位置、
 * 移动/推动计数、撤销栈与关卡数据，不依赖任何 UI 组件。地图元素常量
 * 与 {@link SokobanView} 保持一致。</p>
 */
public class SokobanGame {

    /** 地图元素常量（真源在此逻辑层；SokobanView 渲染时引用本类保持单一真相源） */
    public static final int EMPTY = 0;
    public static final int WALL = 1;
    public static final int FLOOR = 2;
    public static final int TARGET = 3;
    public static final int BOX = 4;
    public static final int BOX_ON_TARGET = 5;
    public static final int PLAYER = 6;
    public static final int PLAYER_ON_TARGET = 7;

    /** 总关卡数 */
    public static final int TOTAL_LEVELS = 15;

    /** 每关最多撤销次数 */
    public static final int MAX_UNDO = 10;

    // 游戏状态
    private int[][] map;
    private int[][] originalMap;
    private int playerRow;
    private int playerCol;
    private int moveCount;
    private int pushCount;
    private int currentLevel;
    private int levelsCleared;

    // 撤销历史栈：每条记录 [fromRow, fromCol, toRow, toCol, pushedBox, boxToRow, boxToCol]
    private Deque<int[]> undoStack;
    private int undoCount;

    private boolean running;

    // ==================== 状态访问 ====================

    public int[][] getMap() { return map; }
    public int getPlayerRow() { return playerRow; }
    public int getPlayerCol() { return playerCol; }
    public int getMoveCount() { return moveCount; }
    public int getPushCount() { return pushCount; }
    public int getCurrentLevel() { return currentLevel; }
    public int getLevelsCleared() { return levelsCleared; }
    public boolean isRunning() { return running; }

    // ==================== 关卡管理 ====================

    /**
     * 开始指定关卡：加载地图、重置计数、定位玩家。
     */
    public void startLevel(int level) {
        currentLevel = level;
        moveCount = 0;
        pushCount = 0;
        undoStack = new ArrayDeque<>();
        undoCount = 0;
        running = true;

        map = loadLevel(level);
        originalMap = copyMap(map);
        findPlayer();
    }

    /**
     * 自定义关卡标记：{@link #currentLevel} 为此值时表示自定义关卡。
     * {@link #onLevelComplete()} 中 {@code Math.max(levelsCleared, currentLevel)}
     * 对负值天然不推进内置通关进度，无需特殊分支。
     */
    public static final int CUSTOM_LEVEL = -1;

    /** 当前是否为自定义关卡。 */
    public boolean isCustomLevel() {
        return currentLevel == CUSTOM_LEVEL;
    }

    /**
     * 装载自定义关卡（关卡编辑器 / 关卡码导入）。
     *
     * <p>输入按不可信数据校验：地图为空、非矩形、尺寸超限、格子值非法、
     * 玩家数量 != 1、无箱子、箱子数与目标数不一致，一律返回 {@code false}
     * 且当前对局状态保持不变（先校验后装载，拒绝无副作用）。</p>
     *
     * @param customMap 地图二维数组，元素取值为本类常量
     * @return true 装载成功并开局；false 校验拒绝
     */
    public boolean startCustomLevel(int[][] customMap) {
        if (customMap == null || customMap.length == 0 || customMap[0].length == 0) return false;
        int rows = customMap.length;
        int cols = customMap[0].length;
        if (rows > 20 || cols > 20) return false;

        int players = 0;
        int boxes = 0;
        int targets = 0;
        for (int r = 0; r < rows; r++) {
            if (customMap[r] == null || customMap[r].length != cols) return false;
            for (int c = 0; c < cols; c++) {
                int cell = customMap[r][c];
                if (cell < EMPTY || cell > PLAYER_ON_TARGET) return false;
                if (cell == PLAYER || cell == PLAYER_ON_TARGET) players++;
                if (cell == BOX || cell == BOX_ON_TARGET) boxes++;
                if (cell == TARGET || cell == BOX_ON_TARGET || cell == PLAYER_ON_TARGET) targets++;
            }
        }
        if (players != 1) return false;
        if (boxes < 1) return false;
        if (boxes != targets) return false;

        // 全部校验通过，原子提交
        currentLevel = CUSTOM_LEVEL;
        moveCount = 0;
        pushCount = 0;
        undoStack = new ArrayDeque<>();
        undoCount = 0;
        running = true;
        map = copyMap(customMap);
        originalMap = copyMap(customMap);
        findPlayer();
        return true;
    }

    /**
     * 重开当前关卡（内置/自定义通用）：用 {@code originalMap} 恢复初始局面。
     *
     * <p>自定义关卡的 {@code currentLevel} 为 {@link #CUSTOM_LEVEL}，不能经
     * {@code startLevel(currentLevel)} 重开（会触发 {@code loadLevel(-1)} 返回 null）。</p>
     */
    public void restartLevel() {
        if (originalMap == null) return; // 尚未开局
        moveCount = 0;
        pushCount = 0;
        undoStack = new ArrayDeque<>();
        undoCount = 0;
        running = true;
        map = copyMap(originalMap);
        findPlayer();
    }

    /**
     * 查找玩家位置。
     */
    private void findPlayer() {
        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[0].length; c++) {
                if (map[r][c] == PLAYER || map[r][c] == PLAYER_ON_TARGET) {
                    playerRow = r;
                    playerCol = c;
                    return;
                }
            }
        }
    }

    /**
     * 移动玩家。如果推动了箱子，pushCount 自增。
     * @return true 表示发生了移动
     */
    public boolean movePlayer(int dr, int dc) {
        if (!running) return false;

        int newRow = playerRow + dr;
        int newCol = playerCol + dc;

        // 边界检查
        if (newRow < 0 || newRow >= map.length || newCol < 0 || newCol >= map[0].length) return false;

        int targetCell = map[newRow][newCol];

        // 空地或目标点：直接移动
        if (targetCell == FLOOR || targetCell == TARGET) {
            undoStack.push(new int[]{playerRow, playerCol, newRow, newCol, 0, -1, -1});
            movePlayerTo(newRow, newCol);
            return true;
        }

        // 箱子：尝试推动
        if (targetCell == BOX || targetCell == BOX_ON_TARGET) {
            int boxNewRow = newRow + dr;
            int boxNewCol = newCol + dc;

            if (boxNewRow < 0 || boxNewRow >= map.length
                    || boxNewCol < 0 || boxNewCol >= map[0].length) return false;

            int behindBox = map[boxNewRow][boxNewCol];
            if (behindBox == FLOOR || behindBox == TARGET) {
                undoStack.push(new int[]{playerRow, playerCol, newRow, newCol, 1, boxNewRow, boxNewCol});
                pushBox(newRow, newCol, boxNewRow, boxNewCol);
                movePlayerTo(newRow, newCol);
                pushCount++;
                return true;
            }
        }
        return false;
    }

    /**
     * 移动玩家到指定位置。
     */
    private void movePlayerTo(int newRow, int newCol) {
        // 清除原位置
        if (map[playerRow][playerCol] == PLAYER_ON_TARGET) {
            map[playerRow][playerCol] = TARGET;
        } else {
            map[playerRow][playerCol] = FLOOR;
        }

        // 设置新位置
        if (map[newRow][newCol] == TARGET) {
            map[newRow][newCol] = PLAYER_ON_TARGET;
        } else {
            map[newRow][newCol] = PLAYER;
        }

        playerRow = newRow;
        playerCol = newCol;
        moveCount++;
    }

    /**
     * 推动箱子。
     */
    private void pushBox(int boxRow, int boxCol, int newRow, int newCol) {
        // 清除箱子原位置
        if (map[boxRow][boxCol] == BOX_ON_TARGET) {
            map[boxRow][boxCol] = TARGET;
        } else {
            map[boxRow][boxCol] = FLOOR;
        }

        // 设置箱子新位置
        if (map[newRow][newCol] == TARGET) {
            map[newRow][newCol] = BOX_ON_TARGET;
        } else {
            map[newRow][newCol] = BOX;
        }
    }

    /**
     * 撤销最近一次移动，恢复玩家与被推箱子的位置。
     * 每关最多撤销 {@link #MAX_UNDO} 次。
     * @return true 表示撤销成功
     */
    public boolean undoMove() {
        if (!running) return false;
        if (undoStack.isEmpty() || undoCount >= MAX_UNDO) return false;

        int[] last = undoStack.pop();
        int fromR = last[0];
        int fromC = last[1];
        int toR = last[2];
        int toC = last[3];
        int pushed = last[4];
        int boxToR = last[5];
        int boxToC = last[6];

        if (pushed == 1) {
            // 箱子从 boxToR/boxToC 移回 toR/toC（玩家当前位置即箱子原位）
            map[boxToR][boxToC] = (map[boxToR][boxToC] == BOX_ON_TARGET) ? TARGET : FLOOR;
            int toBase = (map[toR][toC] == PLAYER_ON_TARGET) ? TARGET : FLOOR;
            map[toR][toC] = (toBase == TARGET) ? BOX_ON_TARGET : BOX;
            pushCount--;
        } else {
            // 未推箱子，仅清除玩家
            map[toR][toC] = (map[toR][toC] == PLAYER_ON_TARGET) ? TARGET : FLOOR;
        }

        // 玩家回到原位置
        map[fromR][fromC] = (map[fromR][fromC] == TARGET) ? PLAYER_ON_TARGET : PLAYER;
        playerRow = fromR;
        playerCol = fromC;
        moveCount--;
        undoCount++;
        return true;
    }

    /**
     * 检查关卡是否完成（所有目标点被箱子覆盖，无裸露 TARGET/PLAYER_ON_TARGET）。
     */
    public boolean isLevelComplete() {
        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[0].length; c++) {
                if (map[r][c] == TARGET || map[r][c] == PLAYER_ON_TARGET) {
                    return false; // 还有未覆盖的目标点
                }
            }
        }
        return true;
    }

    /**
     * 关卡通关处理：更新已通关数、停止运行。
     */
    public void onLevelComplete() {
        running = false;
        levelsCleared = Math.max(levelsCleared, currentLevel);
    }

    /**
     * 停止游戏。
     */
    public void stop() {
        running = false;
    }

    /**
     * 复制地图
     */
    private int[][] copyMap(int[][] src) {
        int[][] dst = new int[src.length][src[0].length];
        for (int r = 0; r < src.length; r++) {
            System.arraycopy(src[r], 0, dst[r], 0, src[0].length);
        }
        return dst;
    }

    /**
     * 加载关卡地图
     */
    private int[][] loadLevel(int level) {
        switch (level) {
            case 1: return new int[][] {
                {0, WALL, WALL, WALL, 0},
                {WALL, WALL, FLOOR, WALL, 0},
                {WALL, TARGET, PLAYER, WALL, WALL},
                {WALL, BOX, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL},
                {0, WALL, TARGET, FLOOR, WALL},
                {0, WALL, WALL, WALL, WALL}
            };
            case 2: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, 0},
                {WALL, FLOOR, PLAYER, FLOOR, WALL, 0},
                {WALL, FLOOR, BOX, FLOOR, WALL, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, WALL, FLOOR, BOX, FLOOR, WALL},
                {0, WALL, TARGET, TARGET, FLOOR, WALL},
                {0, WALL, TARGET, FLOOR, FLOOR, WALL},
                {0, WALL, WALL, WALL, WALL, WALL}
            };
            case 3: return new int[][] {
                {0, WALL, WALL, WALL, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, PLAYER, BOX, FLOOR, WALL},
                {WALL, FLOOR, BOX, BOX, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, TARGET, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL}
            };
            case 4: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 5: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, FLOOR, TARGET, FLOOR, FLOOR, TARGET, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, TARGET, FLOOR, FLOOR, TARGET, FLOOR, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 6: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, TARGET, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 7: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 8: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, WALL, WALL, FLOOR, WALL, FLOOR, WALL, WALL, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, TARGET, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 9: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, BOX, FLOOR, FLOOR, PLAYER, FLOOR, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            case 10: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, BOX, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, TARGET, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, BOX, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, TARGET, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            // Route around offset pillars to reach two target areas.
            case 11: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, TARGET, TARGET, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, TARGET, WALL},
                {WALL, FLOOR, FLOOR, PLAYER, FLOOR, FLOOR, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            // Fill the deep storage targets while keeping the doorway accessible.
            case 12: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL, TARGET, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, FLOOR, WALL, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, BOX, WALL, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            // Move the doorway goal box aside, then return it after passing through.
            case 13: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, FLOOR, FLOOR, WALL, FLOOR, FLOOR, TARGET, WALL},
                {WALL, FLOOR, BOX, BOX_ON_TARGET, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, WALL, FLOOR, BOX, FLOOR, WALL},
                {WALL, FLOOR, TARGET, WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, PLAYER, WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            // Change pushing sides between staggered walls to reach three target areas.
            case 14: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, FLOOR, FLOOR, TARGET, TARGET, WALL, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, BOX, FLOOR, WALL, FLOOR, TARGET, WALL},
                {WALL, FLOOR, WALL, BOX, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, WALL, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, BOX, FLOOR, PLAYER, FLOOR, TARGET, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            // Use the side routes while preserving access behind the vertical goals.
            case 15: return new int[][] {
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL},
                {WALL, FLOOR, FLOOR, TARGET, TARGET, TARGET, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, WALL, TARGET, WALL, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, BOX, BOX, FLOOR, BOX, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, WALL, FLOOR, WALL, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, BOX, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, FLOOR, WALL, FLOOR, FLOOR, FLOOR, WALL, FLOOR, WALL},
                {WALL, FLOOR, FLOOR, FLOOR, PLAYER, FLOOR, FLOOR, FLOOR, WALL},
                {WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL}
            };
            default:
                return loadLevel(1);
        }
    }
}
