package com.gamecenter.app.games;

public class GameStats {
    public String gameId;
    public int highScore;
    public int totalWins;
    public int totalLosses;
    public int totalPlays;
    public long bestTimeMs;
    public long totalPlayTimeMs;
    public long lastPlayedAt;

    public GameStats() {}

    public GameStats(String gameId) {
        this.gameId = gameId;
        this.highScore = 0;
        this.totalWins = 0;
        this.totalLosses = 0;
        this.totalPlays = 0;
        this.bestTimeMs = 0;
        this.totalPlayTimeMs = 0;
        this.lastPlayedAt = 0;
    }

    public float getWinRate() {
        int total = totalWins + totalLosses;
        if (total == 0) return 0f;
        return (float) totalWins / total * 100f;
    }

    public String getWinRateText() {
        int total = totalWins + totalLosses;
        if (total == 0) return "无记录";
        return String.format("%.1f%%", getWinRate());
    }

    public String getBestTimeText() {
        if (bestTimeMs <= 0) return "无记录";
        long seconds = bestTimeMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes > 0) {
            return String.format("%d分%d秒", minutes, seconds);
        }
        return seconds + "秒";
    }

    public String getTotalPlayTimeText() {
        if (totalPlayTimeMs <= 0) return "无记录";
        long totalSeconds = totalPlayTimeMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d小时%d分", hours, minutes);
        }
        if (minutes > 0) {
            return String.format("%d分%d秒", minutes, seconds);
        }
        return seconds + "秒";
    }
}
