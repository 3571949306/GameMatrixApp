package com.gamecenter.app.doudizhu;

public final class DouDiZhuSoundHelper {

    private static final int STATE_BIDDING = 1;
    private static final int STATE_PLAYING = 2;

    private int lastTurnSoundState = -1;
    private int lastTurnSoundSeat = -1;

    public DouDiZhuSoundHelper() {}

    public void playClickSound(DouDiZhuSoundManager soundManager) {
        if (soundManager != null) {
            soundManager.click();
        }
    }

    public void playTurnSoundIfNeeded(DouDiZhuSoundManager soundManager,
                                       int currentTurn, int gameState,
                                       int mode, int mySeatIndex) {
        if (soundManager == null) return;
        if (!DouDiZhuSeatNameHelper.isLocalSeat(currentTurn, mode, mySeatIndex)) return;
        if (gameState != STATE_BIDDING && gameState != STATE_PLAYING) return;
        if (lastTurnSoundState == gameState && lastTurnSoundSeat == currentTurn) return;
        lastTurnSoundState = gameState;
        lastTurnSoundSeat = currentTurn;
        soundManager.turn();
    }

    public void resetTurnSoundMarker() {
        lastTurnSoundState = -1;
        lastTurnSoundSeat = -1;
    }
}
