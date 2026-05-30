package com.gamecenter.app.games.sokoban;

import java.util.Random;

/**
 * 推箱子游戏核心逻辑类
 *
 * <p>管理推箱子的地图状态、玩家移动、箱子推动和关卡完成判定。
 * 地图使用二维整数数组表示，每种元素（墙壁、地板、箱子、目标点等）
 * 用不同的常量值区分。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>地图元素使用整数常量编码，支持组合状态（如箱子在目标点上 = BOX_ON_TARGET）</li>
 *   <li>移动逻辑中，玩家原位置需要根据是否为目标点来恢复为 TARGET 或 FLOOR</li>
 *   <li>关卡完成条件：地图上不存在不在目标点上的箱子（即所有 BOX 都已变为 BOX_ON_TARGET）</li>
 *   <li>序列化/反序列化使用手动拼接 JSON，避免引入额外依赖</li>
 * </ul>
 * </p>
 */
public class SokobanGame {
    /** 墙壁 */
    public static final int WALL = 1;
    /** 空地板 */
    public static final int FLOOR = 0;
    /** 箱子（不在目标点上） */
    public static final int BOX = 2;
    /** 目标点（无箱子无玩家） */
    public static final int TARGET = 3;
    /** 箱子在目标点上 */
    public static final int BOX_ON_TARGET = 4;
    /** 玩家（不在目标点上） */
    public static final int PLAYER = 5;
    /** 玩家在目标点上 */
    public static final int PLAYER_ON_TARGET = 6;

    /** 当前地图数据 */
    private int[][] map;
    /** 玩家当前列坐标 */
    private int playerX, playerY;
    /** 地图行数和列数 */
    private int rows, cols;
    /** 当前关卡已走步数 */
    private int moves = 0;
    /** 已完成的关卡轮次数（循环通关计数） */
    private int levelsCompleted = 0;

    /**
     * 预定义关卡数据
     *
     * <p>每个关卡是一个二维数组，使用上述常量编码地图元素。
     * 目前包含 3 个关卡，难度递增。</p>
     */
    private static final int[][][] LEVELS = {
        {
            {1,1,1,1,1,1,1},
            {1,5,0,0,0,0,1},
            {1,0,2,0,2,0,1},
            {1,0,0,3,0,3,1},
            {1,0,2,0,0,0,1},
            {1,0,0,0,3,0,1},
            {1,1,1,1,1,1,1}
        },
        {
            {1,1,1,1,1,1,1,1},
            {1,5,0,0,0,0,0,1},
            {1,0,2,2,0,0,0,1},
            {1,0,0,0,0,3,0,1},
            {1,0,2,0,3,0,0,1},
            {1,0,0,0,0,3,0,1},
            {1,0,0,0,0,0,0,1},
            {1,1,1,1,1,1,1,1}
        },
        {
            {0,1,1,1,1,1,0},
            {1,1,5,0,0,1,1},
            {1,0,0,2,0,0,1},
            {1,0,3,0,3,0,1},
            {1,0,0,2,0,0,1},
            {1,1,0,0,0,1,1},
            {0,1,1,1,1,1,0}
        }
    };

    /**
     * 构造方法，加载第一关
     */
    public SokobanGame() {
        loadLevel(0);
    }

    /**
     * 加载指定关卡
     *
     * <p>从预定义关卡数据中深拷贝地图，定位玩家位置，
     * 并将玩家所在格恢复为地板或目标点。</p>
     *
     * @param levelIndex 关卡索引，若超出范围则循环回第一关
     */
    public void loadLevel(int levelIndex) {
        // 关卡循环：超出范围时回到第一关
        if (levelIndex >= LEVELS.length) {
            levelIndex = 0;
            levelsCompleted++;
        }
        int[][] level = LEVELS[levelIndex];
        rows = level.length;
        cols = level[0].length;
        map = new int[rows][cols];

        // 第一遍：深拷贝地图并定位玩家
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                map[y][x] = level[y][x];
                if (map[y][x] == PLAYER || map[y][x] == PLAYER_ON_TARGET) {
                    playerX = x;
                    playerY = y;
                    // 将玩家所在格恢复为目标点或地板
                    map[y][x] = (levelIndex == 2 && (y == 3 && x == 3)) ? TARGET : FLOOR;
                }
            }
        }

        // 第二遍：验证玩家位置，确保至少有一个玩家
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

        // 防御性处理：如果关卡数据中没有玩家，选择第一个空地作为玩家位置
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

    /**
     * 移动玩家
     *
     * <p>移动逻辑：
     * <ol>
     *   <li>检查目标位置是否越界或为墙壁</li>
     *   <li>如果目标位置是箱子，检查箱子能否被推动（箱子前方不能是墙/箱子）</li>
     *   <li>推动箱子：更新箱子新位置和旧位置的状态</li>
     *   <li>更新玩家位置，恢复玩家旧位置为目标点或地板</li>
     * </ol>
     * </p>
     *
     * @param dx 水平移动量（-1 左移，1 右移，0 不移动）
     * @param dy 垂直移动量（-1 上移，1 下移，0 不移动）
     * @return 移动是否成功
     */
    public boolean move(int dx, int dy) {
        int newX = playerX + dx;
        int newY = playerY + dy;

        // 边界检查
        if (newX < 0 || newX >= cols || newY < 0 || newY >= rows) return false;
        // 墙壁不可进入
        if (map[newY][newX] == WALL) return false;

        // 处理推箱子逻辑
        int targetTile = map[newY][newX];
        if (targetTile == BOX || targetTile == BOX_ON_TARGET) {
            int boxNewX = newX + dx;
            int boxNewY = newY + dy;

            // 箱子目标位置越界检查
            if (boxNewX < 0 || boxNewX >= cols || boxNewY < 0 || boxNewY >= rows) return false;
            // 箱子前方不能是墙或另一个箱子
            if (map[boxNewY][boxNewX] == WALL || map[boxNewY][boxNewX] == BOX || map[boxNewY][boxNewX] == BOX_ON_TARGET) return false;

            // 更新箱子新位置：目标点上为 BOX_ON_TARGET，否则为 BOX
            if (map[boxNewY][boxNewX] == TARGET) {
                map[boxNewY][boxNewX] = BOX_ON_TARGET;
            } else {
                map[boxNewY][boxNewX] = BOX;
            }

            // 恢复箱子旧位置：如果箱子原来在目标点上则恢复为 TARGET
            if (targetTile == BOX_ON_TARGET) {
                map[newY][newX] = TARGET;
            } else {
                map[newY][newX] = FLOOR;
            }
        }

        // 恢复玩家旧位置：如果原来在目标点上则保留 TARGET
        int oldTile = map[playerY][playerX];
        if (oldTile == TARGET || oldTile == BOX_ON_TARGET) {
            map[playerY][playerX] = TARGET;
        } else {
            map[playerY][playerX] = FLOOR;
        }

        // 更新玩家坐标并增加步数
        playerX = newX;
        playerY = newY;
        moves++;
        return true;
    }

    /**
     * 判断当前关卡是否完成
     *
     * <p>关卡完成条件：地图上不存在不在目标点上的箱子（即没有 BOX 常量的格子）。
     * 所有箱子都已在目标点上（变为 BOX_ON_TARGET）。</p>
     *
     * @return 关卡是否完成
     */
    public boolean isLevelComplete() {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                // 只要还有箱子不在目标点上，关卡未完成
                if (map[y][x] == BOX) return false;
            }
        }
        return true;
    }

    /**
     * 获取指定位置的地图元素
     *
     * <p>特殊处理玩家位置：如果坐标与玩家位置重合，
     * 根据该位置是否为目标点返回 PLAYER_ON_TARGET 或 PLAYER。</p>
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return 该位置的元素类型常量
     */
    public int getTile(int x, int y) {
        if (x == playerX && y == playerY) {
            return (map[y][x] == TARGET) ? PLAYER_ON_TARGET : PLAYER;
        }
        return map[y][x];
    }

    /**
     * 获取地图行数
     * @return 行数
     */
    public int getRows() { return rows; }
    /**
     * 获取地图列数
     * @return 列数
     */
    public int getCols() { return cols; }
    /**
     * 获取玩家列坐标
     * @return 列坐标
     */
    public int getPlayerX() { return playerX; }
    /**
     * 获取玩家行坐标
     * @return 行坐标
     */
    public int getPlayerY() { return playerY; }
    /**
     * 获取当前关卡已走步数
     * @return 步数
     */
    public int getMoves() { return moves; }
    /**
     * 获取已完成的关卡轮次数
     * @return 轮次数
     */
    public int getLevelsCompleted() { return levelsCompleted; }
    /**
     * 获取当前关卡索引（等于已完成轮次数）
     * @return 关卡索引
     */
    public int getCurrentLevel() { return levelsCompleted; }

    /**
     * 重置当前关卡（步数归零，地图恢复初始状态）
     */
    public void reset() {
        loadLevel(levelsCompleted);
    }

    /**
     * 进入下一关
     */
    public void nextLevel() {
        levelsCompleted++;
        loadLevel(levelsCompleted);
    }

    /**
     * 外部设置地图和玩家位置（用于存档恢复等场景）
     *
     * @param newMap 新的地图数据
     * @param px     玩家列坐标
     * @param py     玩家行坐标
     */
    public void setMap(int[][] newMap, int px, int py) {
        this.map = newMap;
        this.playerX = px;
        this.playerY = py;
    }

    /**
     * 获取关卡总数
     *
     * @return 预定义关卡数量
     */
    public int getLevelCount() {
        return LEVELS.length;
    }

    /**
     * 将当前游戏状态序列化为 JSON 字符串
     *
     * <p>保存关卡索引、步数、玩家位置、地图尺寸和完整地图数据，
     * 用于自动存档和恢复。</p>
     *
     * @return JSON 格式的状态字符串
     */
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

    /**
     * 从 JSON 字符串恢复游戏状态
     *
     * <p>解析序列化的状态数据，恢复关卡、步数、玩家位置和地图。
     * 如果解析失败则返回 false，游戏状态不变。</p>
     *
     * @param json 序列化的状态字符串
     * @return 恢复是否成功
     */
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
