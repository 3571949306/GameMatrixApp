package com.gamecenter.app.games.chinesechess;

/**
 * 课程进度记录。
 *
 * <p>保存用户对某一课程的学习进度，包括完成状态、尝试次数和最佳得分。</p>
 */
public class LessonProgress {

    private final String lessonId;
    private boolean completed;
    private int attempts;
    private long completionTime;
    private int bestScore;

    public LessonProgress(String lessonId) {
        this.lessonId = lessonId;
        this.completed = false;
        this.attempts = 0;
        this.completionTime = 0;
        this.bestScore = 0;
    }

    public String getLessonId() {
        return lessonId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public long getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(long completionTime) {
        this.completionTime = completionTime;
    }

    public int getBestScore() {
        return bestScore;
    }

    public void setBestScore(int bestScore) {
        if (bestScore > this.bestScore) {
            this.bestScore = bestScore;
        }
    }

    public void updateScore(int score) {
        if (score > this.bestScore) {
            this.bestScore = score;
        }
    }
}
