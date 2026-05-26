package com.gamecenter.app.games.flappy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Flappy Bird 风格游戏核心逻辑类
 *
 * <p>封装 Flappy Bird 的完整游戏状态和物理模拟，与 UI 完全解耦。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>模拟小鸟的重力下落和跳跃物理</li>
 *   <li>管理管道的生成、移动和回收</li>
 *   <li>处理碰撞检测（天花板、地面、管道）</li>
 *   <li>计算得分（通过管道中心线时加分）</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>所有尺寸和速度均基于游戏区域宽高的比例计算，自动适配不同屏幕</li>
 *   <li>小鸟固定在屏幕宽度20%处，仅上下移动</li>
 *   <li>管道间隔1.8秒生成，间隙高度为屏幕高度的32%</li>
 *   <li>碰撞检测使用圆形（小鸟）与矩形（管道）的AABB碰撞</li>
 *   <li>小鸟到达地面即判定游戏结束（无弹跳）</li>
 * </ul>
 */
public class FlappyGame {

    /** 小鸟的垂直位置（Y坐标） */
    private float birdY;

    /** 小鸟的垂直速度（正值向下，负值向上） */
    private float birdVelocity;

    /** 重力加速度，每帧施加到速度上 */
    private static final float GRAVITY = 0.6f;

    /** 跳跃时赋予的初始向上速度 */
    private static final float JUMP_VELOCITY = -8f;

    /** 小鸟半径占屏幕短边的比例 */
    private static final float BIRD_RADIUS_RATIO = 0.04f;

    /** 管道列表，存储当前屏幕上所有管道 */
    private List<Pipe> pipes;

    /** 随机数生成器，用于管道间隙位置随机化 */
    private Random random;

    /** 当前得分 */
    private int score;

    /** 游戏是否结束 */
    private boolean gameOver;

    /** 游戏是否已开始（玩家第一次点击后为 true） */
    private boolean started;

    /** 游戏区域宽度（像素） */
    private float gameWidth;

    /** 游戏区域高度（像素） */
    private float gameHeight;

    /** 管道宽度（像素） */
    private float pipeWidth;

    /** 管道间隙高度（像素） */
    private float pipeGap;

    /** 管道水平移动速度（像素/帧） */
    private float pipeSpeed;

    /** 上一次生成管道的时间戳 */
    private long lastPipeTime;

    /** 管道生成间隔（毫秒） */
    private static final long PIPE_INTERVAL = 1800;

    /** 小鸟半径（像素） */
    private float birdRadius;

    /** 地面Y坐标（像素），小鸟超过此位置即游戏结束 */
    private float groundY;

    /**
     * 管道数据类。
     *
     * <p>每根管道由 x 位置、间隙起始Y坐标和间隙高度定义。
     * 间隙上方和下方各有一段管道实体。</p>
     */
    public static class Pipe {
        /** 管道左边缘的X坐标 */
        float x;

        /** 间隙上边缘的Y坐标（上方管道的底边） */
        float gapY;

        /** 间隙的高度 */
        float gapHeight;

        /** 是否已经计分（防止重复加分） */
        boolean scored;

        /**
         * 构造管道实例。
         *
         * @param x         管道左边缘X坐标
         * @param gapY      间隙上边缘Y坐标
         * @param gapHeight 间隙高度
         */
        Pipe(float x, float gapY, float gapHeight) {
            this.x = x;
            this.gapY = gapY;
            this.gapHeight = gapHeight;
            this.scored = false;
        }
    }

    /**
     * 构造函数，初始化游戏状态。
     */
    public FlappyGame() {
        random = new Random();
        pipes = new ArrayList<>();
        reset();
    }

    /**
     * 设置游戏区域尺寸。
     *
     * <p>必须在游戏开始前调用，所有游戏参数（小鸟半径、管道尺寸、速度等）
     * 均基于此尺寸按比例计算，确保在不同屏幕上体验一致。</p>
     *
     * @param width  游戏区域宽度（像素）
     * @param height 游戏区域高度（像素）
     */
    public void setGameArea(float width, float height) {
        this.gameWidth = width;
        this.gameHeight = height;
        birdRadius = Math.min(width, height) * BIRD_RADIUS_RATIO;
        pipeWidth = width * 0.12f;
        pipeGap = height * 0.32f;
        pipeSpeed = width * 0.005f;
        groundY = height * 0.85f;
    }

    /**
     * 重置游戏状态。
     *
     * <p>将小鸟放回初始位置（屏幕高度40%处），清空所有管道，
     * 重置得分和状态标志。</p>
     */
    public void reset() {
        birdY = gameHeight > 0 ? gameHeight * 0.4f : 0;
        birdVelocity = 0;
        pipes.clear();
        score = 0;
        gameOver = false;
        started = false;
        lastPipeTime = System.currentTimeMillis();
    }

    /**
     * 执行跳跃操作。
     *
     * <p>赋予小鸟一个向上的初始速度（JUMP_VELOCITY）。
     * 首次跳跃时将 started 标志设为 true，开始游戏。
     * 游戏结束后忽略跳跃输入。</p>
     */
    public void jump() {
        if (gameOver) return;
        started = true;
        birdVelocity = JUMP_VELOCITY;
    }

    /**
     * 更新一帧的游戏状态。
     *
     * <p>仅在游戏已开始且未结束时执行更新。更新逻辑：</p>
     * <ol>
     *   <li>施加重力加速度到小鸟速度</li>
     *   <li>更新小鸟Y坐标</li>
     *   <li>天花板碰撞：小鸟不能超出屏幕顶部</li>
     *   <li>地面碰撞：小鸟触地即游戏结束</li>
     *   <li>移动所有管道，计分（通过小鸟位置时加分），移除已离开屏幕的管道</li>
     *   <li>定时生成新管道</li>
     *   <li>管道碰撞检测：小鸟与管道的AABB碰撞</li>
     * </ol>
     *
     * @param now 当前时间戳（毫秒），用于管道生成间隔计算
     */
    public void update(long now) {
        if (gameOver || !started) return;

        // 物理模拟：重力加速 + 位置更新
        birdVelocity += GRAVITY;
        birdY += birdVelocity;

        // 天花板碰撞：限制小鸟不超出屏幕顶部
        if (birdY - birdRadius < 0) {
            birdY = birdRadius;
            birdVelocity = 0;
        }

        // 地面碰撞：触地即游戏结束
        if (birdY + birdRadius > groundY) {
            birdY = groundY - birdRadius;
            gameOver = true;
            return;
        }

        // 移动管道、计分、回收
        for (int i = pipes.size() - 1; i >= 0; i--) {
            Pipe pipe = pipes.get(i);
            pipe.x -= pipeSpeed;

            // 计分：当管道中心线通过小鸟位置时加分（仅一次）
            if (!pipe.scored && pipe.x + pipeWidth / 2 < gameWidth * 0.2f) {
                pipe.scored = true;
                score++;
            }

            // 回收已完全离开屏幕左侧的管道
            if (pipe.x + pipeWidth < 0) {
                pipes.remove(i);
            }
        }

        // 定时生成新管道
        if (now - lastPipeTime > PIPE_INTERVAL) {
            lastPipeTime = now;
            // 间隙起始位置在地面15%到(地面-间隙高度)之间随机
            float gapY = groundY * 0.15f + random.nextFloat() * (groundY - pipeGap - groundY * 0.15f);
            pipes.add(new Pipe(gameWidth, gapY, pipeGap));
        }

        // 管道碰撞检测：AABB碰撞
        for (Pipe pipe : pipes) {
            float birdLeft = gameWidth * 0.2f - birdRadius;
            float birdRight = gameWidth * 0.2f + birdRadius;
            float pipeLeft = pipe.x;
            float pipeRight = pipe.x + pipeWidth;

            // 水平方向重叠时，检查垂直方向是否在间隙外
            if (birdRight > pipeLeft && birdLeft < pipeRight) {
                if (birdY - birdRadius < pipe.gapY || birdY + birdRadius > pipe.gapY + pipe.gapHeight) {
                    gameOver = true;
                    return;
                }
            }
        }
    }

    /**
     * 获取管道列表。
     *
     * @return 当前屏幕上所有管道的列表
     */
    public List<Pipe> getPipes() {
        return pipes;
    }

    /**
     * 获取小鸟的Y坐标。
     *
     * @return 小鸟中心Y坐标（像素）
     */
    public float getBirdY() {
        return birdY;
    }

    /**
     * 获取小鸟半径。
     *
     * @return 小鸟半径（像素）
     */
    public float getBirdRadius() {
        return birdRadius;
    }

    /**
     * 获取小鸟的X坐标（固定在屏幕宽度20%处）。
     *
     * @return 小鸟中心X坐标（像素）
     */
    public float getBirdX() {
        return gameWidth * 0.2f;
    }

    /**
     * 获取管道宽度。
     *
     * @return 管道宽度（像素）
     */
    public float getPipeWidth() {
        return pipeWidth;
    }

    /**
     * 获取地面Y坐标。
     *
     * @return 地面Y坐标（像素）
     */
    public float getGroundY() {
        return groundY;
    }

    /**
     * 获取当前得分。
     *
     * @return 得分值
     */
    public int getScore() {
        return score;
    }

    /**
     * 判断游戏是否结束。
     *
     * @return 游戏结束返回 true
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * 判断游戏是否已开始。
     *
     * @return 玩家已点击屏幕开始游戏返回 true
     */
    public boolean isStarted() {
        return started;
    }
}
