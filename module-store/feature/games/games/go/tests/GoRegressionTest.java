package com.gamecenter.app.go;

import java.util.Arrays;

/** Pure-Java regression suite for the runtime Go module. */
public final class GoRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        testMoveBoundariesAndErrors();
        testCaptureAndSuicideException();
        testSuicideRejected();
        testUniqueLibertyCounting();
        testSimpleKoAndDelayedRecapture();
        testTryMoveAndSnapshotsAreDefensive();
        testFullAndLegacyRestore();
        testChineseAreaScoreWithKomi();
        testMediumCapturesAtari();
        testMediumSavesAtariGroup();
        testMediumNeverRandomlyPasses();
        testAiAcceptsOnlyWinningScoringProposal();
        testAllDifficultiesReturnRawLegalMoveWithoutMutation();
        testAiRespectsRootKo();
        testMctsIsDeterministicForFixedSeedAndBudget();
        testShortValidatedGamesHaveNoRawIllegalMove();
        testAiReturnsNullOnlyWhenNoMoveOrWrongTurn();
        System.out.println("PASS GoRegressionTest: " + assertions + " assertions");
    }

    private static void testMoveBoundariesAndErrors() {
        GoGame game = new GoGame();
        game.startNewGame();
        assertEquals(GoGame.MoveError.OUT_OF_BOUNDS,
                game.tryMove(-1, 0, GoGame.BLACK).getError(), "negative row");
        assertEquals(GoGame.MoveError.OUT_OF_BOUNDS,
                game.tryMove(0, GoGame.BOARD_SIZE, GoGame.BLACK).getError(), "large col");
        assertEquals(GoGame.MoveError.INVALID_COLOR,
                game.tryMove(0, 0, 7).getError(), "invalid color");
        assertTrue(game.playMove(0, 0), "first move legal");
        assertEquals(GoGame.MoveError.OCCUPIED,
                game.tryMove(0, 0, GoGame.WHITE).getError(), "occupied point");
        game.setGameOver(true);
        assertEquals(GoGame.MoveError.GAME_OVER,
                game.tryMove(0, 1, GoGame.WHITE).getError(), "game-over guard");
    }

    private static void testCaptureAndSuicideException() {
        int[][] beforeKoCapture = koPositionBeforeBlackCapture();
        int[][] unchanged = GoGame.copyBoard(beforeKoCapture);
        GoGame.MoveResult capture = GoGame.tryMove(
                beforeKoCapture, null, 1, 1, GoGame.BLACK);
        assertTrue(capture.isLegal(), "capture into apparent suicide is legal");
        assertEquals(1, capture.getCapturedStones(), "one white stone captured");
        assertEquals(GoGame.EMPTY, capture.getBoard()[1][2], "captured stone removed");
        assertBoardEquals(unchanged, beforeKoCapture, "tryMove does not mutate input");

        GoGame game = new GoGame();
        game.restoreState(beforeKoCapture, null, GoGame.BLACK, 0, 0, 0, false);
        assertTrue(game.playMove(1, 1), "committed capture succeeds");
        assertEquals(1, game.getCapturedByBlack(), "capture counter committed");
        assertEquals(GoGame.WHITE, game.getCurrentPlayer(), "turn switched after capture");
    }

    private static void testSuicideRejected() {
        int[][] board = emptyBoard();
        board[0][1] = GoGame.BLACK;
        board[1][0] = GoGame.BLACK;
        GoGame.MoveResult result = GoGame.tryMove(board, null, 0, 0, GoGame.WHITE);
        assertFalse(result.isLegal(), "corner suicide rejected");
        assertEquals(GoGame.MoveError.SUICIDE, result.getError(), "suicide reason");
    }

    private static void testUniqueLibertyCounting() {
        int[][] board = emptyBoard();
        board[0][0] = GoGame.BLACK;
        board[0][1] = GoGame.BLACK;
        board[1][0] = GoGame.BLACK;
        // Unique liberties are (0,2), (1,1), (2,0). (1,1) touches two stones.
        assertEquals(3, GoGame.countLiberties(board, 0, 0), "shared liberty counted once");
    }

    private static void testSimpleKoAndDelayedRecapture() {
        int[][] before = koPositionBeforeBlackCapture();
        GoGame.MoveResult blackCapture = GoGame.tryMove(before, null, 1, 1, GoGame.BLACK);
        int[][] afterCapture = blackCapture.getBoard();

        GoGame.MoveResult immediate = GoGame.tryMove(
                afterCapture, before, 1, 2, GoGame.WHITE);
        assertFalse(immediate.isLegal(), "immediate ko recapture rejected");
        assertEquals(GoGame.MoveError.KO, immediate.getError(), "ko reason");

        GoGame.MoveResult whiteThreat = GoGame.tryMove(
                afterCapture, before, 8, 8, GoGame.WHITE);
        assertTrue(whiteThreat.isLegal(), "ko threat legal");
        GoGame.MoveResult blackReply = GoGame.tryMove(
                whiteThreat.getBoard(), afterCapture, 8, 7, GoGame.BLACK);
        assertTrue(blackReply.isLegal(), "ko threat reply legal");
        GoGame.MoveResult delayed = GoGame.tryMove(
                blackReply.getBoard(), whiteThreat.getBoard(), 1, 2, GoGame.WHITE);
        assertTrue(delayed.isLegal(), "recapture legal after intervening moves");
    }

    private static void testTryMoveAndSnapshotsAreDefensive() {
        GoGame game = new GoGame();
        game.startNewGame();
        assertTrue(game.playMove(4, 4), "setup move");
        int[][] boardSnapshot = game.getBoardSnapshot();
        int[][] previousSnapshot = game.getPreviousBoardSnapshot();
        boardSnapshot[4][4] = GoGame.EMPTY;
        previousSnapshot[0][0] = GoGame.WHITE;
        assertEquals(GoGame.BLACK, game.getBoard()[4][4], "board snapshot is defensive");
        assertEquals(GoGame.EMPTY, game.getPreviousBoardSnapshot()[0][0],
                "previous-board snapshot is defensive");

        GoGame.MoveResult result = game.tryMove(3, 3, GoGame.WHITE);
        int[][] first = result.getBoard();
        first[3][3] = GoGame.EMPTY;
        assertEquals(GoGame.WHITE, result.getBoard()[3][3], "MoveResult board is defensive");

        GoGame.PositionSnapshot snapshot = game.snapshot();
        int[][] fromSnapshot = snapshot.getBoard();
        fromSnapshot[4][4] = GoGame.EMPTY;
        assertEquals(GoGame.BLACK, snapshot.getBoard()[4][4], "position snapshot is immutable");
    }

    private static void testFullAndLegacyRestore() {
        int[][] before = koPositionBeforeBlackCapture();
        int[][] after = GoGame.tryMove(before, null, 1, 1, GoGame.BLACK).getBoard();
        GoGame restored = new GoGame();
        restored.restoreState(after, before, GoGame.WHITE, 3, 4, 0, false);
        assertEquals(3, restored.getCapturedByBlack(), "full restore black captures");
        assertEquals(4, restored.getCapturedByWhite(), "full restore white captures");
        assertEquals(GoGame.MoveError.KO,
                restored.tryMove(1, 2, GoGame.WHITE).getError(), "full restore preserves ko");

        GoGame.PositionSnapshot snapshot = restored.snapshot();
        GoGame copied = new GoGame();
        copied.restoreState(snapshot);
        assertEquals(GoGame.MoveError.KO,
                copied.tryMove(1, 2, GoGame.WHITE).getError(), "snapshot restore preserves ko");

        GoGame passState = new GoGame();
        passState.restoreState(emptyBoard(), null, GoGame.WHITE, 0, 0, 1, false);
        passState.passMove();
        assertTrue(passState.isGameOver(), "full restore preserves consecutive pass");

        GoGame legacy = new GoGame();
        legacy.restoreState(after, GoGame.WHITE, 7, 8);
        assertEquals(7, legacy.getCapturedByBlack(), "legacy restore remains available");
        assertEquals(0, legacy.getConsecutivePasses(), "legacy restore defaults pass count");
    }

    private static void testChineseAreaScoreWithKomi() {
        int[][] board = emptyBoard();
        board[3][4] = GoGame.BLACK;
        board[4][3] = GoGame.BLACK;
        board[4][5] = GoGame.BLACK;
        board[5][4] = GoGame.BLACK;
        board[0][0] = GoGame.WHITE;
        GoGame game = new GoGame();
        game.restoreState(board, null, GoGame.BLACK, 99, 88, 0, false);

        GoGame.Score score = game.calculateScore();
        assertDoubleEquals(5.0, score.getBlackScore(), "four stones plus enclosed point");
        assertDoubleEquals(7.5, score.getWhiteScore(), "one stone plus 6.5 komi");
        assertDoubleEquals(6.5, score.getKomi(), "komi retained as half point");
        assertFalse(score.isBlackWinner(), "white wins scored fixture");
        assertTrue(score.isWhiteWinner(), "white winner helper");
        assertDoubleEquals(-1.0, game.calculateTerritory()[4][4], "black territory marked");
        assertDoubleEquals(0.0, game.calculateTerritory()[8][8], "mixed outside is neutral");
        // Capture counters intentionally do not alter Chinese area score.
        assertDoubleEquals(5.0, game.calculateScore().getBlackScore(), "captures not double-counted");
    }

    private static void testMediumCapturesAtari() {
        int[][] board = emptyBoard();
        board[1][1] = GoGame.BLACK;
        board[0][1] = GoGame.WHITE;
        board[1][0] = GoGame.WHITE;
        board[2][1] = GoGame.WHITE;
        GoGame game = gameAtWhiteTurn(board, null);
        GoAI ai = new GoAI(1L);
        ai.setDifficulty(2);
        int[] move = ai.findBestAiMove(game);
        assertMove(1, 2, move, "medium takes one-liberty black stone");
        GoGame.MoveResult result = game.tryMove(move[0], move[1], GoGame.WHITE);
        assertEquals(1, result.getCapturedStones(), "medium capture is real");
    }

    private static void testMediumSavesAtariGroup() {
        int[][] board = emptyBoard();
        board[1][1] = GoGame.WHITE;
        board[0][1] = GoGame.BLACK;
        board[1][0] = GoGame.BLACK;
        board[2][1] = GoGame.BLACK;
        GoGame game = gameAtWhiteTurn(board, null);
        GoAI ai = new GoAI(2L);
        ai.setDifficulty(2);
        assertMove(1, 2, ai.findBestAiMove(game), "medium saves its one-liberty group");
    }

    private static void testMediumNeverRandomlyPasses() {
        for (int seed = 0; seed < 24; seed++) {
            GoGame game = new GoGame();
            game.startNewGame();
            assertTrue(game.playMove(4, 4), "opening setup " + seed);
            GoAI ai = new GoAI(seed);
            ai.setDifficulty(2);
            int[] move = ai.findBestAiMove(game);
            assertNotNull(move, "medium does not randomly pass " + seed);
            assertTrue(game.isValidMove(move[0], move[1], GoGame.WHITE),
                    "medium opening move legal " + seed);
        }
    }

    private static void testAiAcceptsOnlyWinningScoringProposal() {
        GoAI ai = new GoAI(25L);
        ai.setDifficulty(2);

        GoGame winning = new GoGame();
        winning.restoreState(emptyBoard(), null, GoGame.WHITE, 0, 0, 1, false);
        assertNull(ai.findBestAiMove(winning),
                "AI accepts opponent pass when official score has white ahead");

        int[][] losingBoard = emptyBoard();
        for (int col = 0; col < 8; col++) losingBoard[0][col] = GoGame.BLACK;
        losingBoard[0][8] = GoGame.WHITE;
        GoGame losing = new GoGame();
        losing.restoreState(losingBoard, null, GoGame.WHITE, 0, 0, 1, false);
        assertTrue(losing.calculateScore().isBlackWinner(), "losing fixture has black ahead");
        int[] move = ai.findBestAiMove(losing);
        assertNotNull(move, "AI rejects opponent pass while behind");
        assertTrue(losing.isValidMove(move[0], move[1], GoGame.WHITE),
                "continued move after rejected scoring proposal is legal");
    }

    private static void testAllDifficultiesReturnRawLegalMoveWithoutMutation() {
        GoGame game = new GoGame();
        game.startNewGame();
        assertTrue(game.playMove(4, 4), "AI input setup");
        GoGame.PositionSnapshot before = game.snapshot();

        for (int difficulty = 1; difficulty <= 4; difficulty++) {
            GoAI ai = new GoAI(100L + difficulty);
            ai.configureSearchForTests(12, 18, 5_000L, 8);
            ai.setDifficulty(difficulty);
            int[] move = ai.findBestAiMove(game);
            assertNotNull(move, "difficulty " + difficulty + " returns move");
            assertTrue(game.isValidMove(move[0], move[1], GoGame.WHITE),
                    "difficulty " + difficulty + " raw move legal");
            assertSnapshotEquals(before, game.snapshot(),
                    "difficulty " + difficulty + " does not mutate game");
        }
    }

    private static void testAiRespectsRootKo() {
        int[][] before = koPositionBeforeBlackCapture();
        int[][] after = GoGame.tryMove(before, null, 1, 1, GoGame.BLACK).getBoard();
        GoGame game = gameAtWhiteTurn(after, before);
        for (int difficulty = 1; difficulty <= 4; difficulty++) {
            GoAI ai = new GoAI(200L + difficulty);
            ai.configureSearchForTests(10, 14, 5_000L, 6);
            ai.setDifficulty(difficulty);
            int[] move = ai.findBestAiMove(game);
            assertNotNull(move, "ko fixture has alternative at difficulty " + difficulty);
            assertFalse(move[0] == 1 && move[1] == 2,
                    "difficulty " + difficulty + " does not recapture ko");
            assertTrue(game.isValidMove(move[0], move[1], GoGame.WHITE),
                    "ko-root move legal at difficulty " + difficulty);
        }
    }

    private static void testMctsIsDeterministicForFixedSeedAndBudget() {
        GoGame firstGame = new GoGame();
        firstGame.startNewGame();
        firstGame.playMove(4, 4);
        GoGame secondGame = new GoGame();
        secondGame.startNewGame();
        secondGame.playMove(4, 4);

        GoAI first = new GoAI(314159L);
        GoAI second = new GoAI(314159L);
        first.setDifficulty(3);
        second.setDifficulty(3);
        first.configureSearchForTests(24, 24, 5_000L, 10);
        second.configureSearchForTests(24, 24, 5_000L, 10);
        int[] firstMove = first.findBestAiMove(firstGame);
        int[] secondMove = second.findBestAiMove(secondGame);
        assertTrue(Arrays.equals(firstMove, secondMove), "fixed-seed MCTS final choice deterministic");
    }

    private static void testShortValidatedGamesHaveNoRawIllegalMove() {
        for (int difficulty = 1; difficulty <= 4; difficulty++) {
            GoGame game = new GoGame();
            game.startNewGame();
            GoAI ai = new GoAI(900L + difficulty);
            ai.configureSearchForTests(8, 12, 5_000L, 6);
            ai.setDifficulty(difficulty);

            for (int round = 0; round < 5 && !game.isGameOver(); round++) {
                int[] blackMove = firstLegalMove(game, GoGame.BLACK);
                if (blackMove == null) game.passMove();
                else assertTrue(game.playMove(blackMove[0], blackMove[1]), "black setup commit");
                if (game.isGameOver()) break;

                GoGame.PositionSnapshot beforeAi = game.snapshot();
                int[] whiteMove = ai.findBestAiMove(game);
                if (whiteMove == null) {
                    assertFalse(hasLegalMove(game, GoGame.WHITE),
                            "null only when no white legal move at difficulty " + difficulty);
                    game.passMove();
                } else {
                    assertTrue(game.isValidMove(whiteMove[0], whiteMove[1], GoGame.WHITE),
                            "raw AI move legal at difficulty " + difficulty + " round " + round);
                    assertSnapshotEquals(beforeAi, game.snapshot(), "search input unchanged");
                    assertTrue(game.playMove(whiteMove[0], whiteMove[1]),
                            "AI commit succeeds without fallback");
                }
            }
        }
    }

    private static void testAiReturnsNullOnlyWhenNoMoveOrWrongTurn() {
        GoGame wrongTurn = new GoGame();
        wrongTurn.startNewGame();
        GoAI ai = new GoAI(42L);
        assertNull(ai.findBestAiMove(wrongTurn), "AI refuses black turn");

        int[][] full = emptyBoard();
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) full[r][c] = GoGame.BLACK;
        }
        GoGame noMove = gameAtWhiteTurn(full, null);
        for (int difficulty = 1; difficulty <= 4; difficulty++) {
            ai.setDifficulty(difficulty);
            ai.configureSearchForTests(4, 4, 5_000L, 2);
            assertNull(ai.findBestAiMove(noMove), "full board null at difficulty " + difficulty);
        }
    }

    private static int[][] koPositionBeforeBlackCapture() {
        int[][] board = emptyBoard();
        board[1][2] = GoGame.WHITE;
        board[0][1] = GoGame.WHITE;
        board[2][1] = GoGame.WHITE;
        board[1][0] = GoGame.WHITE;
        board[0][2] = GoGame.BLACK;
        board[2][2] = GoGame.BLACK;
        board[1][3] = GoGame.BLACK;
        return board;
    }

    private static GoGame gameAtWhiteTurn(int[][] board, int[][] previousBoard) {
        GoGame game = new GoGame();
        game.restoreState(board, previousBoard, GoGame.WHITE, 0, 0, 0, false);
        return game;
    }

    private static int[][] emptyBoard() {
        return new int[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
    }

    private static int[] firstLegalMove(GoGame game, int color) {
        for (int r = 0; r < GoGame.BOARD_SIZE; r++) {
            for (int c = 0; c < GoGame.BOARD_SIZE; c++) {
                if (game.isValidMove(r, c, color)) return new int[]{r, c};
            }
        }
        return null;
    }

    private static boolean hasLegalMove(GoGame game, int color) {
        return firstLegalMove(game, color) != null;
    }

    private static void assertSnapshotEquals(GoGame.PositionSnapshot expected,
                                             GoGame.PositionSnapshot actual,
                                             String message) {
        assertBoardEquals(expected.getBoard(), actual.getBoard(), message + " board");
        assertBoardEqualsNullable(expected.getPreviousBoard(), actual.getPreviousBoard(),
                message + " previousBoard");
        assertEquals(expected.getCurrentPlayer(), actual.getCurrentPlayer(), message + " player");
        assertEquals(expected.getCapturedByBlack(), actual.getCapturedByBlack(), message + " capB");
        assertEquals(expected.getCapturedByWhite(), actual.getCapturedByWhite(), message + " capW");
        assertEquals(expected.getConsecutivePasses(), actual.getConsecutivePasses(),
                message + " passes");
        assertEquals(expected.isGameOver(), actual.isGameOver(), message + " gameOver");
    }

    private static void assertBoardEqualsNullable(int[][] expected, int[][] actual, String message) {
        if (expected == null || actual == null) {
            assertTrue(expected == actual, message);
        } else {
            assertBoardEquals(expected, actual, message);
        }
    }

    private static void assertBoardEquals(int[][] expected, int[][] actual, String message) {
        assertions++;
        if (!GoGame.boardsEqual(expected, actual)) {
            throw new AssertionError(message);
        }
    }

    private static void assertMove(int row, int col, int[] actual, String message) {
        assertNotNull(actual, message + " non-null");
        assertions++;
        if (actual[0] != row || actual[1] != col) {
            throw new AssertionError(message + ": expected [" + row + "," + col
                    + "] but was [" + actual[0] + "," + actual[1] + "]");
        }
    }

    private static void assertDoubleEquals(double expected, double actual, String message) {
        assertions++;
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        assertions++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertNotNull(Object value, String message) {
        assertTrue(value != null, message);
    }

    private static void assertNull(Object value, String message) {
        assertTrue(value == null, message);
    }
}
