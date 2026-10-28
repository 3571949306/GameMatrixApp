package com.gamecenter.app.snake;

import android.app.Application;
import android.graphics.Point;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 贪吃蛇难度等级、速度因子、得分与吃苹果统计的单元测试。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
public class SnakeDifficultyTest {

    @Test
    public void testDifficultySettingsAndClamping() {
        SnakeGame game = new SnakeGame();
        
        // 默认普通
        assertEquals(SnakeGame.DIFFICULTY_NORMAL, game.getDifficulty());
        assertEquals(0.50f, game.getSpeedFactor(), 0.001f);

        // 设置简单
        game.setDifficulty(SnakeGame.DIFFICULTY_EASY);
        assertEquals(SnakeGame.DIFFICULTY_EASY, game.getDifficulty());
        assertEquals(0.20f, game.getSpeedFactor(), 0.001f);

        // 设置困难
        game.setDifficulty(SnakeGame.DIFFICULTY_HARD);
        assertEquals(SnakeGame.DIFFICULTY_HARD, game.getDifficulty());
        assertEquals(0.85f, game.getSpeedFactor(), 0.001f);

        // 下界钳制
        game.setDifficulty(-10);
        assertEquals(SnakeGame.DIFFICULTY_EASY, game.getDifficulty());

        // 上界钳制
        game.setDifficulty(99);
        assertEquals(SnakeGame.DIFFICULTY_HARD, game.getDifficulty());
    }

    @Test
    public void testResetAndInitialState() {
        SnakeGame game = new SnakeGame();
        game.reset();

        assertEquals(0, game.getScore());
        assertEquals(0, game.getApplesEaten());
        assertTrue(game.isRunning());
        assertFalse(game.isGameOver());
        assertEquals(3, game.getSnake().size());
    }

    @Test
    public void testAteFoodIncrementsApplesAndScore() {
        SnakeGame game = new SnakeGame();
        game.reset();

        // 强行将食物放置在蛇头正前方 (当前向右走)
        Point head = game.getSnake().get(0);
        Point food = game.getFood();
        food.x = head.x + 1;
        food.y = head.y;

        int res = game.tick();
        assertEquals(SnakeGame.TICK_ATE, res);
        assertEquals(10, game.getScore());
        assertEquals(1, game.getApplesEaten());
        assertEquals(4, game.getSnake().size());
    }

    @Test
    public void testWallCollisionCausesDeath() {
        SnakeGame game = new SnakeGame();
        game.reset();

        // 把蛇头挪到最右边缘
        Point head = game.getSnake().get(0);
        head.x = SnakeGame.GRID_COLS - 1;
        game.setNextDirection(SnakeGame.DIR_RIGHT);

        int res = game.tick();
        assertEquals(SnakeGame.TICK_DIED, res);
        assertTrue(game.isGameOver());
        assertFalse(game.isRunning());
    }

    @Test
    public void testPauseResumeStop() {
        SnakeGame game = new SnakeGame();
        game.reset();
        assertTrue(game.isRunning());

        game.pause();
        assertFalse(game.isRunning());

        game.resume();
        assertTrue(game.isRunning());

        game.stop();
        assertFalse(game.isRunning());
    }
}
