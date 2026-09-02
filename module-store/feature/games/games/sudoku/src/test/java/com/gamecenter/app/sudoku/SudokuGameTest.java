package com.gamecenter.app.sudoku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 数独规则层回归测试；不依赖 Android UI 或随机墙钟时间。 */
public class SudokuGameTest {

    @Test
    public void generatedPuzzleHasExpectedHolesAndValidSolution() {
        for (int difficulty = 0; difficulty < SudokuGame.HOLE_COUNTS.length; difficulty++) {
            SudokuGame game = new SudokuGame();
            game.startNewGame(difficulty, 20260830L + difficulty);

            assertEquals(SudokuGame.HOLE_COUNTS[difficulty], game.getRemainingCellCount());
            assertValidSolution(game.getSolution());
            assertFalse(game.isBoardComplete());
        }
    }

    @Test
    public void replacingSameCellValueDoesNotConflict() {
        SudokuGame game = new SudokuGame();
        game.startNewGame(0, 11L);
        int[] empty = findEmptyCell(game);
        int expected = game.getSolution()[empty[0]][empty[1]];

        assertTrue(game.isValidPlacement(game.getBoard(), empty[0], empty[1], expected));
        assertFalse(game.hasConflict(empty[0], empty[1], expected));
        assertEquals(SudokuGame.InputResult.PLACED,
                game.setValue(empty[0], empty[1], expected));
    }

    @Test
    public void conflictingInputIsRejectedWithoutMutatingBoard() {
        SudokuGame game = new SudokuGame();
        game.startNewGame(0, 22L);
        int[] empty = findEmptyCell(game);
        int conflictingNumber = 0;
        int[][] board = game.getBoard();
        for (int col = 0; col < SudokuGame.GRID_SIZE; col++) {
            if (board[empty[0]][col] != 0) {
                conflictingNumber = board[empty[0]][col];
                break;
            }
        }
        assertTrue(conflictingNumber > 0);

        assertEquals(SudokuGame.InputResult.CONFLICT,
                game.setValue(empty[0], empty[1], conflictingNumber));
        assertEquals(0, game.getValue(empty[0], empty[1]));
        assertEquals(0, game.getMistakes());
    }

    @Test
    public void wrongValueCanBeCorrectedAndUndoRedoRestoresState() {
        SudokuGame game = new SudokuGame();
        game.startNewGame(0, 33L);
        int[] empty = findCellWithValidAlternative(game);
        int solution = game.getSolution()[empty[0]][empty[1]];
        int wrong = findValidAlternative(game, empty[0], empty[1], solution);

        assertEquals(SudokuGame.InputResult.INCORRECT,
                game.setValue(empty[0], empty[1], wrong));
        assertEquals(wrong, game.getValue(empty[0], empty[1]));
        assertEquals(1, game.getMistakes());
        assertTrue(game.getErrors()[empty[0]][empty[1]]);

        assertTrue(game.undo());
        assertEquals(0, game.getValue(empty[0], empty[1]));
        assertEquals(0, game.getMistakes());
        assertTrue(game.redo());
        assertEquals(wrong, game.getValue(empty[0], empty[1]));
        assertEquals(1, game.getMistakes());
    }

    @Test
    public void notesAreStoredAndPeerNotesAreRemovedAfterPlacement() {
        SudokuGame game = new SudokuGame();
        game.startNewGame(0, 44L);
        int[] first = findEmptyCell(game);
        int[] peer = findEmptyPeer(game, first[0], first[1]);
        assertTrue(game.toggleNote(peer[0], peer[1], 1));
        assertTrue((game.getNoteMask(peer[0], peer[1]) & (1 << 1)) != 0);

        int solution = game.getSolution()[first[0]][first[1]];
        assertEquals(SudokuGame.InputResult.PLACED,
                game.setValue(first[0], first[1], solution));
        assertEquals(0, game.getNoteMask(peer[0], peer[1]) & (1 << solution));
    }

    @Test
    public void hintLocksCellAndCompleteRequiresTheSolution() {
        SudokuGame game = new SudokuGame();
        game.startNewGame(0, 55L);
        int[] empty = findEmptyCell(game);
        int solution = game.getSolution()[empty[0]][empty[1]];

        assertFalse(game.isCellLocked(empty[0], empty[1]));
        assertFalse(game.useHint(empty[0], empty[1]));
        assertEquals(solution, game.getValue(empty[0], empty[1]));
        assertTrue(game.isCellLocked(empty[0], empty[1]));
        assertEquals(1, game.getHintsUsed());
    }

    @Test
    public void stateSnapshotIsDefensiveAndCanRestore() {
        SudokuGame original = new SudokuGame();
        original.startNewGame(1, 66L);
        int[] empty = findEmptyCell(original);
        int solution = original.getSolution()[empty[0]][empty[1]];
        original.setValue(empty[0], empty[1], solution);
        SudokuGame.State state = original.getState();

        int[][] boardCopy = state.getBoard();
        boardCopy[empty[0]][empty[1]] = 9;
        assertEquals(solution, original.getValue(empty[0], empty[1]));

        SudokuGame restored = new SudokuGame();
        assertTrue(restored.restoreState(state));
        assertEquals(solution, restored.getValue(empty[0], empty[1]));
        assertEquals(original.getRemainingCellCount(), restored.getRemainingCellCount());
        assertEquals(original.getMistakes(), restored.getMistakes());
    }

    private static int[] findEmptyCell(SudokuGame game) {
        int[][] board = game.getBoard();
        for (int row = 0; row < SudokuGame.GRID_SIZE; row++) {
            for (int col = 0; col < SudokuGame.GRID_SIZE; col++) {
                if (board[row][col] == 0) return new int[]{row, col};
            }
        }
        throw new AssertionError("generated board has no empty cell");
    }

    private static int[] findEmptyPeer(SudokuGame game, int row, int col) {
        int[][] board = game.getBoard();
        for (int candidate = 0; candidate < SudokuGame.GRID_SIZE; candidate++) {
            if (candidate != col && board[row][candidate] == 0) return new int[]{row, candidate};
        }
        for (int candidate = 0; candidate < SudokuGame.GRID_SIZE; candidate++) {
            if (candidate != row && board[candidate][col] == 0) return new int[]{candidate, col};
        }
        throw new AssertionError("generated board has no empty peer");
    }

    private static int findValidAlternative(SudokuGame game, int row, int col, int solution) {
        for (int number = 1; number <= SudokuGame.GRID_SIZE; number++) {
            if (number != solution && game.canPlace(row, col, number)) return number;
        }
        throw new AssertionError("empty cell has no valid alternative");
    }

    private static int[] findCellWithValidAlternative(SudokuGame game) {
        int[][] board = game.getBoard();
        int[][] solution = game.getSolution();
        for (int row = 0; row < SudokuGame.GRID_SIZE; row++) {
            for (int col = 0; col < SudokuGame.GRID_SIZE; col++) {
                if (board[row][col] != 0) continue;
                for (int number = 1; number <= SudokuGame.GRID_SIZE; number++) {
                    if (number != solution[row][col] && game.canPlace(row, col, number)) {
                        return new int[]{row, col};
                    }
                }
            }
        }
        throw new AssertionError("generated board has no cell with a valid alternative");
    }

    private static void assertValidSolution(int[][] solution) {
        SudokuGame validator = new SudokuGame();
        for (int row = 0; row < SudokuGame.GRID_SIZE; row++) {
            for (int col = 0; col < SudokuGame.GRID_SIZE; col++) {
                assertTrue(solution[row][col] >= 1 && solution[row][col] <= 9);
                assertTrue(validator.isValidPlacement(solution, row, col, solution[row][col]));
            }
        }
    }
}
