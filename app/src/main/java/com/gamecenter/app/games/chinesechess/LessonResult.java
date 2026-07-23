package com.gamecenter.app.games.chinesechess;

/**
 * 课程学习结果。
 *
 * <p>保存用户完成一次课程练习后的结果，包括是否正确、得分和反馈信息。</p>
 */
public class LessonResult {

    private final boolean correct;
    private final int score;
    private final String feedback;
    private final int[] expectedMove;
    private final int puzzleIndex;

    public LessonResult(boolean correct, int score, String feedback,
                        int[] expectedMove, int puzzleIndex) {
        this.correct = correct;
        this.score = score;
        this.feedback = feedback;
        this.expectedMove = expectedMove;
        this.puzzleIndex = puzzleIndex;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }

    public int[] getExpectedMove() {
        return expectedMove;
    }

    public int getPuzzleIndex() {
        return puzzleIndex;
    }
}
