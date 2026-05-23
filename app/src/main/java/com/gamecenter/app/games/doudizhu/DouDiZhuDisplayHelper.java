package com.gamecenter.app.games.doudizhu;

public final class DouDiZhuDisplayHelper {

    static final int TOTAL_SEATS = 3;

    private DouDiZhuDisplayHelper() {}

    public static int getPlayerDisplaySeat(int mode, int mySeatIndex) {
        return mode == 1 && mySeatIndex >= 0 ? mySeatIndex : 0;
    }

    public static int getLeftDisplaySeat(int mode, int mySeatIndex) {
        return mode == 1 && mySeatIndex >= 0 ? (mySeatIndex + 1) % TOTAL_SEATS : 1;
    }

    public static int getRightDisplaySeat(int mode, int mySeatIndex) {
        return mode == 1 && mySeatIndex >= 0 ? (mySeatIndex + 2) % TOTAL_SEATS : 2;
    }

    public static int getDisplaySlotForSeat(int seatIndex, int mode, int mySeatIndex) {
        if (seatIndex == getPlayerDisplaySeat(mode, mySeatIndex)) return 0;
        if (seatIndex == getLeftDisplaySeat(mode, mySeatIndex)) return 1;
        if (seatIndex == getRightDisplaySeat(mode, mySeatIndex)) return 2;
        return -1;
    }

    public static int getLandlordStatusForSeat(int seatIndex, int landlordIndex) {
        if (landlordIndex < 0) return 0;
        return landlordIndex == seatIndex ? 2 : 1;
    }

    public static String getLandlordIndicatorText(int landlordIndex, int[] seatTypes) {
        if (landlordIndex < 0) return "待定";
        return DouDiZhuSeatNameHelper.getSeatName(landlordIndex, seatTypes, landlordIndex);
    }

    public static String getTurnIndicatorText(int currentTurn, int gameState,
                                               int mode, int mySeatIndex, int[] seatTypes) {
        String seatName = DouDiZhuSeatNameHelper.getTurnSeatName(currentTurn, mode, mySeatIndex, seatTypes);
        if (gameState == 1) {
            return seatName + "叫地主";
        }
        return seatName + "出牌";
    }

    public static String getGameOverResult(int winnerIndex, int mode, int mySeatIndex, int landlordIndex) {
        if (mode == 0) {
            return (winnerIndex == 0) ? "你赢了！" : "你输了！";
        }
        boolean winnerIsLandlord = (winnerIndex == landlordIndex);
        boolean iAmLandlord = (mySeatIndex == landlordIndex);
        if (winnerIsLandlord == iAmLandlord) return "你赢了！";
        return "你输了！";
    }

    public static String getClientGameOverResult(int winnerIndex, int mySeatIndex, int landlordIndex) {
        if (winnerIndex == mySeatIndex) return "你赢了！";
        boolean winnerIsLandlord = (winnerIndex == landlordIndex);
        boolean iAmLandlord = (mySeatIndex == landlordIndex);
        if (winnerIsLandlord == iAmLandlord) return "你赢了！";
        return "你输了！";
    }

    public static int getGameOverScore(int winnerIndex, int landlordIndex) {
        return (winnerIndex == landlordIndex) ? 100 : 50;
    }
}
