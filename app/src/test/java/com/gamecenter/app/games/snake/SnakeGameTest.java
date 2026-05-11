package com.gamecenter.app.games.snake;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class SnakeGameTest {

    private SnakeGame game;

    @Before
    public void setUp() {
        game = new SnakeGame();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertEquals(0, game.getScore());
        assertEquals(SnakeGame.Direction.RIGHT, game.getDirection());
        assertEquals(20, SnakeGame.COLS);
        assertEquals(20, SnakeGame.ROWS);
    }

    @Test
    public void testInitialSnakeLength() {
        assertEquals(3, game.getSnake().size());
    }

    @Test
    public void testFoodExists() {
        assertNotNull(game.getFood());
        int[] food = game.getFood();
        assertTrue(food[0] >= 0 && food[0] < SnakeGame.COLS);
        assertTrue(food[1] >= 0 && food[1] < SnakeGame.ROWS);
    }

    @Test
    public void testMoveRight() {
        int[] oldHead = game.getSnake().get(0).clone();
        game.setDirection(SnakeGame.Direction.RIGHT);
        game.move();
        int[] newHead = game.getSnake().get(0);
        assertEquals(oldHead[0] + 1, newHead[0]);
        assertEquals(oldHead[1], newHead[1]);
    }

    @Test
    public void testMoveLeft() {
        game.setDirection(SnakeGame.Direction.UP);
        game.move();
        game.setDirection(SnakeGame.Direction.LEFT);
        int[] oldHead = game.getSnake().get(0).clone();
        game.move();
        int[] newHead = game.getSnake().get(0);
        assertEquals(oldHead[0] - 1, newHead[0]);
        assertEquals(oldHead[1], newHead[1]);
    }

    @Test
    public void testMoveUp() {
        game.setDirection(SnakeGame.Direction.UP);
        int[] oldHead = game.getSnake().get(0).clone();
        game.move();
        int[] newHead = game.getSnake().get(0);
        assertEquals(oldHead[0], newHead[0]);
        assertEquals(oldHead[1] - 1, newHead[1]);
    }

    @Test
    public void testMoveDown() {
        game.setDirection(SnakeGame.Direction.DOWN);
        int[] oldHead = game.getSnake().get(0).clone();
        game.move();
        int[] newHead = game.getSnake().get(0);
        assertEquals(oldHead[0], newHead[0]);
        assertEquals(oldHead[1] + 1, newHead[1]);
    }

    @Test
    public void testCannotReverseDirection() {
        game.setDirection(SnakeGame.Direction.LEFT);
        assertEquals(SnakeGame.Direction.RIGHT, game.getDirection());
    }

    @Test
    public void testGameOverWhenHitWall() {
        for (int i = 0; i < 20; i++) {
            game.move();
            if (game.isGameOver()) break;
        }
        assertTrue(game.isGameOver());
    }

    @Test
    public void testReset() {
        game.move();
        game.move();
        game.reset();
        
        assertFalse(game.isGameOver());
        assertEquals(0, game.getScore());
        assertEquals(3, game.getSnake().size());
    }

    @Test
    public void testBoardExists() {
        assertNotNull(game.getBoard());
        assertEquals(20, game.getBoard().length);
        assertEquals(20, game.getBoard()[0].length);
    }
}
