package com.gamecenter.app.games.chinesechess;

/**
 * 中国象棋对局结果枚举。
 */
public enum GameResult {
    WIN("胜"),
    LOSE("负"),
    DRAW("和"),
    TIMEOUT("超时");

    private final String label;

    GameResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
