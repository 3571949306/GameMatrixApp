package com.gamecenter.app.games.plane;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 飞机大战游戏的核心逻辑类
 * <p>
 * 负责管理飞机位置、子弹发射、敌机生成、碰撞检测和得分计算。
 * 采用 MVC 模式，本类为 Model 层，与视图（PlaneView）解耦。
 * <p>
 * 关键设计决策：
 * - 飞机尺寸按游戏区域宽高的比例计算（PLANE_WIDTH_RATIO / PLANE_HEIGHT_RATIO），确保不同屏幕尺寸下视觉一致
 * - 碰撞检测使用中心点距离判定，阈值系数 2.5f 比严格矩形碰撞更宽松，提升游戏手感
 * - 子弹和敌机列表采用倒序遍历删除，避免 ConcurrentModificationException
 */
public class PlaneGame {

    /** 飞机宽度占游戏区域宽度的比例分母（宽度 = gameWidth / 12） */
    private static final int PLANE_WIDTH_RATIO = 12;
    /** 飞机高度占游戏区域高度的比例分母（高度 = gameHeight / 18） */
    private static final int PLANE_HEIGHT_RATIO = 18;
    /** 子弹每帧向上移动的像素数 */
    private static final int BULLET_SPEED = 12;
    /** 敌机每帧向下移动的像素数 */
    private static final int ENEMY_SPEED = 4;
    /** 自动发射子弹的最小时间间隔（毫秒） */
    private static final long SHOOT_INTERVAL = 250;
    /** 生成敌机的最小时间间隔（毫秒） */
    private static final long ENEMY_INTERVAL = 1200;

    private float gameWidth;
    private float gameHeight;

    /** 玩家飞机中心 X 坐标 */
    private float planeX;
    /** 玩家飞机中心 Y 坐标 */
    private float planeY;
    /** 玩家飞机宽度 */
    private float planeW;
    /** 玩家飞机高度 */
    private float planeH;

    private List<Bullet> bullets;
    private List<Enemy> enemies;
    private Random random;
    private int score;
    private boolean gameOver;
    private boolean started;
    /** 上次发射子弹的时间戳，用于控制射击频率 */
    private long lastShootTime;
    /** 上次生成敌机的时间戳，用于控制敌机生成频率 */
    private long lastEnemyTime;

    /**
     * 子弹数据类，记录子弹的中心坐标
     */
    public static class Bullet {
        float x, y;
        Bullet(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * 敌机数据类，记录敌机的中心坐标和尺寸
     */
    public static class Enemy {
        float x, y;
        float w, h;
        Enemy(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    /**
     * 构造函数，初始化随机数生成器和列表，并重置游戏状态
     */
    public PlaneGame() {
        random = new Random();
        bullets = new ArrayList<>();
        enemies = new ArrayList<>();
        reset();
    }

    /**
     * 设置游戏区域尺寸，并根据比例计算飞机大小
     *
     * @param width  游戏区域宽度（像素）
     * @param height 游戏区域高度（像素）
     */
    public void setGameArea(float width, float height) {
        this.gameWidth = width;
        this.gameHeight = height;
        planeW = width / PLANE_WIDTH_RATIO;
        planeH = height / PLANE_HEIGHT_RATIO;
    }

    /**
     * 重置游戏状态，清空子弹和敌机列表，分数归零
     * 如果游戏区域已初始化，将飞机重置到屏幕底部中央位置
     */
    public void reset() {
        bullets.clear();
        enemies.clear();
        score = 0;
        gameOver = false;
        started = false;
        if (gameWidth > 0) {
            // 飞机初始位置：水平居中，垂直方向在屏幕 75% 处
            planeX = gameWidth / 2;
            planeY = gameHeight * 0.75f;
        }
        lastShootTime = System.currentTimeMillis();
        lastEnemyTime = System.currentTimeMillis();
    }

    /**
     * 设置玩家飞机的水平位置，同时限制在游戏区域边界内
     *
     * @param x 目标 X 坐标（会被 clamp 到 [planeW/2, gameWidth - planeW/2] 范围）
     */
    public void setPlaneX(float x) {
        if (gameOver) return;
        started = true;
        // 限制飞机不超出左右边界
        planeX = Math.max(planeW / 2, Math.min(gameWidth - planeW / 2, x));
    }

    /**
     * 更新游戏逻辑，每帧调用一次
     * <p>
     * 执行顺序：自动射击 → 移动子弹 → 生成敌机 → 移动敌机 → 碰撞检测
     *
     * @param now 当前时间戳（System.currentTimeMillis()），用于控制射击和敌机生成间隔
     */
    public void update(long now) {
        if (gameOver || !started) return;

        // 按射击间隔自动发射子弹
        if (now - lastShootTime > SHOOT_INTERVAL) {
            lastShootTime = now;
            bullets.add(new Bullet(planeX, planeY - planeH / 2));
        }

        // 倒序遍历移动子弹，飞出屏幕顶部的子弹直接移除
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.y -= BULLET_SPEED;
            if (b.y < 0) {
                bullets.remove(i);
            }
        }

        // 按敌机生成间隔创建新敌机
        if (now - lastEnemyTime > ENEMY_INTERVAL) {
            lastEnemyTime = now;
            float ew = planeW * 1.2f;
            float eh = planeH * 1.2f;
            // 敌机水平位置随机，确保不超出屏幕边界
            float ex = ew / 2 + random.nextFloat() * (gameWidth - ew);
            enemies.add(new Enemy(ex, -eh, ew, eh));
        }

        // 倒序遍历移动敌机，同时进行碰撞检测
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.y += ENEMY_SPEED;
            // 敌机飞出屏幕底部则移除
            if (e.y > gameHeight + e.h) {
                enemies.remove(i);
                continue;
            }

            // 检测敌机与玩家飞机的碰撞（使用中心点距离判定，系数 2.5f 使碰撞框略小于视觉框）
            if (Math.abs(e.x - planeX) < (planeW + e.w) / 2.5f
                    && Math.abs(e.y - planeY) < (planeH + e.h) / 2.5f) {
                gameOver = true;
                return;
            }

            // 检测子弹与敌机的碰撞（子弹中心点是否在敌机矩形范围内）
            for (int j = bullets.size() - 1; j >= 0; j--) {
                Bullet b = bullets.get(j);
                if (Math.abs(b.x - e.x) < e.w / 2 && Math.abs(b.y - e.y) < e.h / 2) {
                    bullets.remove(j);
                    enemies.remove(i);
                    score += 10;
                    break;
                }
            }
        }
    }

    /**
     * 获取玩家飞机中心 X 坐标
     * @return 飞机 X 坐标
     */
    public float getPlaneX() { return planeX; }
    /**
     * 获取玩家飞机中心 Y 坐标
     * @return 飞机 Y 坐标
     */
    public float getPlaneY() { return planeY; }
    /**
     * 获取玩家飞机宽度
     * @return 飞机宽度（像素）
     */
    public float getPlaneW() { return planeW; }
    /**
     * 获取玩家飞机高度
     * @return 飞机高度（像素）
     */
    public float getPlaneH() { return planeH; }
    /**
     * 获取当前所有子弹列表
     * @return 子弹列表
     */
    public List<Bullet> getBullets() { return bullets; }
    /**
     * 获取当前所有敌机列表
     * @return 敌机列表
     */
    public List<Enemy> getEnemies() { return enemies; }
    /**
     * 获取当前得分
     * @return 分数值
     */
    public int getScore() { return score; }
    /**
     * 判断游戏是否结束
     * @return true 表示游戏已结束
     */
    public boolean isGameOver() { return gameOver; }
    /**
     * 判断游戏是否已开始
     * @return true 表示玩家已开始操作
     */
    public boolean isStarted() { return started; }
}
