package com.gamecenter.app.games.tiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 别踩白块儿游戏逻辑类
 *
 * <p>职责：管理方块行的生成、滚动、触摸判定和游戏结束判定。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>固定4列，每行随机1个黑块，其余为白块</li>
 *   <li>方块行从底部向上滚动，玩家需点击底部可见的黑块</li>
 *   <li>点击白块或漏掉黑块均判定为游戏结束</li>
 *   <li>滚动速度固定为 cellSize * 0.04f/帧，约每秒 2.4 个格子</li>
 * </ul>
 *
 * <p>坐标系统：屏幕底部为"玩家操作区"，最下方3行被保留为安全区域，
 * 方块行从上方不断生成并向下滚动。</p>
 */
public class TilesGame {

    /** 每行方块列数（固定4列） */
    public static final int COLUMNS = 4;

    /** 可见行数（根据屏幕高度动态计算） */
    private int rows;

    /** 方块行数据列表 */
    private List<Row> tileRows;

    /** 随机数生成器，用于随机选择黑块位置 */
    private Random random;

    /** 当前得分（成功点击的黑块数） */
    private int score;

    /** 游戏是否结束 */
    private boolean gameOver;

    /** 游戏是否已开始（玩家第一次点击后设为 true） */
    private boolean started;

    /** 游戏区域宽度 */
    private float gameWidth;

    /** 游戏区域高度 */
    private float gameHeight;

    /** 每个格子的像素大小 */
    private float cellSize;

    /** 累计滚动偏移量（像素） */
    private float totalScroll;

    /** 每帧滚动速度（像素/帧） */
    private float scrollSpeed;

    /**
     * 方块行数据
     *
     * <p>每行包含4列，其中1列为黑块，其余为白块。
     * touchedCol 记录已被点击的列索引，-1 表示未点击。</p>
     */
    public static class Row {
        /** 每列是否为黑块 */
        boolean[] isBlack;

        /** 已被点击的列索引，-1 表示未点击 */
        int touchedCol;

        Row(boolean[] isBlack) {
            this.isBlack = isBlack;
            this.touchedCol = -1;
        }
    }

    /**
     * 构造方法：初始化随机数生成器和行列表，并重置游戏状态
     */
    public TilesGame() {
        random = new Random();
        tileRows = new ArrayList<>();
        reset();
    }

    /**
     * 设置游戏区域尺寸，计算格子大小和行数
     *
     * <p>cellSize = 宽度 / 4 列；行数 = 屏幕高度 / cellSize + 2（缓冲行）；
     * scrollSpeed = cellSize * 0.04f，即每帧滚动约4%个格子高度。</p>
     *
     * @param width  游戏区域宽度（像素）
     * @param height 游戏区域高度（像素）
     */
    public void setGameArea(float width, float height) {
        this.gameWidth = width;
        this.gameHeight = height;
        cellSize = width / COLUMNS;
        rows = Math.max(6, (int) (height / cellSize) + 2);
        scrollSpeed = cellSize * 0.04f;
    }

    /**
     * 重置游戏状态
     *
     * <p>清空所有行数据，重置得分和状态标志，
     * 若游戏区域已初始化则重新生成行数据。</p>
     */
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

    /**
     * 生成一行方块数据
     *
     * <p>随机选择一列作为黑块，其余列为白块。</p>
     *
     * @return 新生成的 Row 对象
     */
    private Row generateRow() {
        boolean[] isBlack = new boolean[COLUMNS];
        int blackCol = random.nextInt(COLUMNS);
        for (int i = 0; i < COLUMNS; i++) {
            isBlack[i] = (i == blackCol);
        }
        return new Row(isBlack);
    }

    /**
     * 处理玩家触摸操作
     *
     * <p>触摸判定逻辑：</p>
     * <ol>
     *   <li>根据 totalScroll 计算当前底部可见行的位置</li>
     *   <li>在底部3行范围内查找对应的行数据</li>
     *   <li>若点击的列是白块，则游戏结束</li>
     *   <li>若点击的列是黑块，则标记已点击并加分</li>
     *   <li>若未找到对应行（越界），也判定游戏结束</li>
     * </ol>
     *
     * @param colIndex 点击的列索引（0-3）
     */
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

    /**
     * 每帧更新游戏状态
     *
     * <p>仅在游戏已开始且未结束时推进滚动偏移量。
     * 滚动速度为 scrollSpeed 像素/帧。</p>
     */
    public void update() {
        if (gameOver || !started) return;
        totalScroll += scrollSpeed;
    }

    /**
     * @return 方块行数据列表
     */
    public List<Row> getTileRows() {
        return tileRows;
    }

    /**
     * @return 累计滚动偏移量（像素）
     */
    public float getTotalScroll() {
        return totalScroll;
    }

    /**
     * @return 当前得分
     */
    public int getScore() {
        return score;
    }

    /**
     * @return 游戏是否已结束
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * @return 游戏是否已开始（玩家已点击过）
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * @return 每个格子的像素大小
     */
    public float getCellSize() {
        return cellSize;
    }

    /**
     * @return 列数（固定为4）
     */
    public int getColumns() {
        return COLUMNS;
    }
}
