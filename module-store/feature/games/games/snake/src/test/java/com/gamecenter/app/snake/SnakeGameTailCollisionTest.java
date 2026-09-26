package com.gamecenter.app.snake;

import android.app.Application;
import android.graphics.Point;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the production game through reset, direction input, and tick.
 * Food placement is an explicit fixture using the public mutable getFood() Point;
 * every placement is inside the board and outside the current snake. No snake,
 * score, direction, or lifecycle state is written or reflected by this test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
public class SnakeGameTailCollisionTest {

    @Test
    public void nonGrowingMoveCanEnterTheTailCellVacatedByTheSameTick() {
        SnakeGame game = resetGame();
        eatToTheRight(game);
        assertBody(game, 11, 15, 10, 15, 9, 15, 8, 15);
        placeFood(game, 0, 0);

        move(game, SnakeGame.DIR_UP);
        move(game, SnakeGame.DIR_LEFT);
        assertBody(game, 10, 14, 11, 14, 11, 15, 10, 15);
        Point departingTail = new Point(game.getSnake().get(3));

        game.setNextDirection(SnakeGame.DIR_DOWN);
        assertEquals("A non-growing tick must allow its departing tail cell",
                SnakeGame.TICK_MOVED, game.tick());
        assertFalse(game.isGameOver());
        assertTrue(game.isRunning());
        assertEquals(10, game.getScore());
        assertEquals(departingTail, game.getSnake().get(0));
        assertBody(game, 10, 15, 10, 14, 11, 14, 11, 15);
    }

    @Test
    public void enteringABodyCellThatWillRemainStillEndsTheRound() {
        SnakeGame game = resetGame();
        eatToTheRight(game);
        eatToTheRight(game);
        placeFood(game, 0, 0);
        move(game, SnakeGame.DIR_UP);
        move(game, SnakeGame.DIR_LEFT);
        // New head (11,15) is the penultimate segment; tail (10,15) is different.
        assertBody(game, 11, 14, 12, 14, 12, 15, 11, 15, 10, 15);
        List<Point> before = copyBody(game);

        game.setNextDirection(SnakeGame.DIR_DOWN);
        assertEquals(SnakeGame.TICK_DIED, game.tick());
        assertTrue(game.isGameOver());
        assertFalse(game.isRunning());
        assertEquals(20, game.getScore());
        assertEquals(before, game.getSnake());
        game.resume();
        assertFalse("Resume cannot revive a collision-ended round", game.isRunning());
        game.tick();
        assertEquals(before, game.getSnake());
        assertEquals(20, game.getScore());
    }

    @Test
    public void eatingRetainsTheTailAndAddsExactlyOneSegmentAndTenPoints() {
        SnakeGame game = resetGame();
        Point originalTail = new Point(game.getSnake().get(2));
        eatToTheRight(game);

        assertEquals(10, game.getScore());
        assertBody(game, 11, 15, 10, 15, 9, 15, 8, 15);
        assertEquals(originalTail, game.getSnake().get(3));
        assertLegalFood(game);

        placeFood(game, 0, 0);
        move(game, SnakeGame.DIR_RIGHT);
        assertEquals(10, game.getScore());
        assertBody(game, 12, 15, 11, 15, 10, 15, 9, 15);
        assertFalse(game.getSnake().contains(originalTail));
    }

    @Test
    public void reverseInputsCannotTurnBackIntoTheNeckOrBypassTheTickBoundary() {
        SnakeGame game = resetGame();
        placeFood(game, 0, 0);

        game.setNextDirection(SnakeGame.DIR_LEFT);
        assertEquals(SnakeGame.TICK_MOVED, game.tick());
        assertBody(game, 11, 15, 10, 15, 9, 15);

        game.setNextDirection(SnakeGame.DIR_UP);
        game.setNextDirection(SnakeGame.DIR_LEFT);
        assertEquals(SnakeGame.TICK_MOVED, game.tick());
        assertBody(game, 11, 14, 11, 15, 10, 15);

        game.setNextDirection(SnakeGame.DIR_DOWN);
        assertEquals(SnakeGame.TICK_MOVED, game.tick());
        assertBody(game, 11, 13, 11, 14, 11, 15);
        assertFalse(game.isGameOver());
        assertTrue(game.isRunning());
        assertEquals(0, game.getScore());
    }

    private static SnakeGame resetGame() {
        SnakeGame game = new SnakeGame();
        game.reset();
        assertBody(game, 10, 15, 9, 15, 8, 15);
        return game;
    }

    private static void eatToTheRight(SnakeGame game) {
        Point head = game.getSnake().get(0);
        placeFood(game, head.x + 1, head.y);
        game.setNextDirection(SnakeGame.DIR_RIGHT);
        assertEquals(SnakeGame.TICK_ATE, game.tick());
        assertFalse(game.isGameOver());
    }

    private static void move(SnakeGame game, int direction) {
        game.setNextDirection(direction);
        assertEquals(SnakeGame.TICK_MOVED, game.tick());
        assertFalse(game.isGameOver());
    }

    private static void placeFood(SnakeGame game, int x, int y) {
        assertTrue(x >= 0 && x < SnakeGame.GRID_COLS);
        assertTrue(y >= 0 && y < SnakeGame.GRID_ROWS);
        assertFalse("Food fixtures must occupy an empty cell",
                game.getSnake().contains(new Point(x, y)));
        game.getFood().set(x, y);
        assertLegalFood(game);
    }

    private static void assertLegalFood(SnakeGame game) {
        Point food = game.getFood();
        assertTrue(food.x >= 0 && food.x < SnakeGame.GRID_COLS);
        assertTrue(food.y >= 0 && food.y < SnakeGame.GRID_ROWS);
        assertFalse(game.getSnake().contains(food));
    }

    private static List<Point> copyBody(SnakeGame game) {
        List<Point> result = new ArrayList<>();
        for (Point point : game.getSnake()) result.add(new Point(point));
        return result;
    }

    private static void assertBody(SnakeGame game, int... coordinates) {
        List<Point> expected = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            expected.add(new Point(coordinates[i], coordinates[i + 1]));
        }
        assertEquals(expected, game.getSnake());
    }
}
