package com.gamecenter.app.games.flappy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FlappyGame {

    private float birdY;
    private float birdVelocity;
    private static final float GRAVITY = 0.6f;
    private static final float JUMP_VELOCITY = -8f;
    private static final float BIRD_RADIUS_RATIO = 0.04f;

    private List<Pipe> pipes;
    private Random random;
    private int score;
    private boolean gameOver;
    private boolean started;

    private float gameWidth;
    private float gameHeight;
    private float pipeWidth;
    private float pipeGap;
    private float pipeSpeed;
    private long lastPipeTime;
    private static final long PIPE_INTERVAL = 1800;

    private float birdRadius;
    private float groundY;

    public static class Pipe {
        float x;
        float gapY;
        float gapHeight;
        boolean scored;

        Pipe(float x, float gapY, float gapHeight) {
            this.x = x;
            this.gapY = gapY;
            this.gapHeight = gapHeight;
            this.scored = false;
        }
    }

    public FlappyGame() {
        random = new Random();
        pipes = new ArrayList<>();
        reset();
    }

    public void setGameArea(float width, float height) {
        this.gameWidth = width;
        this.gameHeight = height;
        birdRadius = Math.min(width, height) * BIRD_RADIUS_RATIO;
        pipeWidth = width * 0.12f;
        pipeGap = height * 0.32f;
        pipeSpeed = width * 0.005f;
        groundY = height * 0.85f;
    }

    public void reset() {
        birdY = gameHeight > 0 ? gameHeight * 0.4f : 0;
        birdVelocity = 0;
        pipes.clear();
        score = 0;
        gameOver = false;
        started = false;
        lastPipeTime = System.currentTimeMillis();
    }

    public void jump() {
        if (gameOver) return;
        started = true;
        birdVelocity = JUMP_VELOCITY;
    }

    public void update(long now) {
        if (gameOver || !started) return;

        birdVelocity += GRAVITY;
        birdY += birdVelocity;

        if (birdY - birdRadius < 0) {
            birdY = birdRadius;
            birdVelocity = 0;
        }
        if (birdY + birdRadius > groundY) {
            birdY = groundY - birdRadius;
            gameOver = true;
            return;
        }

        for (int i = pipes.size() - 1; i >= 0; i--) {
            Pipe pipe = pipes.get(i);
            pipe.x -= pipeSpeed;

            if (!pipe.scored && pipe.x + pipeWidth / 2 < gameWidth * 0.2f) {
                pipe.scored = true;
                score++;
            }

            if (pipe.x + pipeWidth < 0) {
                pipes.remove(i);
            }
        }

        if (now - lastPipeTime > PIPE_INTERVAL) {
            lastPipeTime = now;
            float gapY = groundY * 0.15f + random.nextFloat() * (groundY - pipeGap - groundY * 0.15f);
            pipes.add(new Pipe(gameWidth, gapY, pipeGap));
        }

        for (Pipe pipe : pipes) {
            float birdLeft = gameWidth * 0.2f - birdRadius;
            float birdRight = gameWidth * 0.2f + birdRadius;
            float pipeLeft = pipe.x;
            float pipeRight = pipe.x + pipeWidth;

            if (birdRight > pipeLeft && birdLeft < pipeRight) {
                if (birdY - birdRadius < pipe.gapY || birdY + birdRadius > pipe.gapY + pipe.gapHeight) {
                    gameOver = true;
                    return;
                }
            }
        }
    }

    public List<Pipe> getPipes() {
        return pipes;
    }

    public float getBirdY() {
        return birdY;
    }

    public float getBirdRadius() {
        return birdRadius;
    }

    public float getBirdX() {
        return gameWidth * 0.2f;
    }

    public float getPipeWidth() {
        return pipeWidth;
    }

    public float getGroundY() {
        return groundY;
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
}
