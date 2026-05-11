package com.gamecenter.app.games.plane;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlaneGame {

    private static final int PLANE_WIDTH_RATIO = 12;
    private static final int PLANE_HEIGHT_RATIO = 18;
    private static final int BULLET_SPEED = 12;
    private static final int ENEMY_SPEED = 4;
    private static final long SHOOT_INTERVAL = 250;
    private static final long ENEMY_INTERVAL = 1200;

    private float gameWidth;
    private float gameHeight;

    private float planeX;
    private float planeY;
    private float planeW;
    private float planeH;

    private List<Bullet> bullets;
    private List<Enemy> enemies;
    private Random random;
    private int score;
    private boolean gameOver;
    private boolean started;
    private long lastShootTime;
    private long lastEnemyTime;

    public static class Bullet {
        float x, y;
        Bullet(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

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

    public PlaneGame() {
        random = new Random();
        bullets = new ArrayList<>();
        enemies = new ArrayList<>();
        reset();
    }

    public void setGameArea(float width, float height) {
        this.gameWidth = width;
        this.gameHeight = height;
        planeW = width / PLANE_WIDTH_RATIO;
        planeH = height / PLANE_HEIGHT_RATIO;
    }

    public void reset() {
        bullets.clear();
        enemies.clear();
        score = 0;
        gameOver = false;
        started = false;
        if (gameWidth > 0) {
            planeX = gameWidth / 2;
            planeY = gameHeight * 0.75f;
        }
        lastShootTime = System.currentTimeMillis();
        lastEnemyTime = System.currentTimeMillis();
    }

    public void setPlaneX(float x) {
        if (gameOver) return;
        started = true;
        planeX = Math.max(planeW / 2, Math.min(gameWidth - planeW / 2, x));
    }

    public void update(long now) {
        if (gameOver || !started) return;

        if (now - lastShootTime > SHOOT_INTERVAL) {
            lastShootTime = now;
            bullets.add(new Bullet(planeX, planeY - planeH / 2));
        }

        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.y -= BULLET_SPEED;
            if (b.y < 0) {
                bullets.remove(i);
            }
        }

        if (now - lastEnemyTime > ENEMY_INTERVAL) {
            lastEnemyTime = now;
            float ew = planeW * 1.2f;
            float eh = planeH * 1.2f;
            float ex = ew / 2 + random.nextFloat() * (gameWidth - ew);
            enemies.add(new Enemy(ex, -eh, ew, eh));
        }

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.y += ENEMY_SPEED;
            if (e.y > gameHeight + e.h) {
                enemies.remove(i);
                continue;
            }

            if (Math.abs(e.x - planeX) < (planeW + e.w) / 2.5f
                    && Math.abs(e.y - planeY) < (planeH + e.h) / 2.5f) {
                gameOver = true;
                return;
            }

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

    public float getPlaneX() { return planeX; }
    public float getPlaneY() { return planeY; }
    public float getPlaneW() { return planeW; }
    public float getPlaneH() { return planeH; }
    public List<Bullet> getBullets() { return bullets; }
    public List<Enemy> getEnemies() { return enemies; }
    public int getScore() { return score; }
    public boolean isGameOver() { return gameOver; }
    public boolean isStarted() { return started; }
}
