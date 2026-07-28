package com.gamecenter.app.doudizhu;

/**
 * 斗地主座位名称与角色判断工具类。
 *
 * <p>从 {@link DouDiZhuOnlineActivity} 中提取的无状态辅助方法，
 * 负责座位名称格式化、角色判断和本地座位检测。</p>
 *
 * <p>打个比方：这个类就像"名牌打印机"，只负责根据座位号和类型
 * 生成显示用的名称标签，不关心游戏状态。</p>
 */
public final class DouDiZhuSeatNameHelper {

    static final int TOTAL_SEATS = 3;
    static final int SEAT_TYPE_AI = 2;
    static final int SEAT_TYPE_REMOTE = 1;

    private DouDiZhuSeatNameHelper() {}

    /**
     * 获取固定座位名称（P1/P2/P3）。
     *
     * @param seatIndex 座位索引 0-2
     * @return 固定名称，无效索引返回"未知"
     */
    public static String getFixedSeatName(int seatIndex) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return "未知";
        return "P" + (seatIndex + 1);
    }

    /**
     * 获取座位参与者名称（含AI标记）。
     *
     * @param seatIndex 座位索引
     * @param seatTypes 座位类型数组
     * @return 参与者名称，AI座位带"（人机）"后缀
     */
    public static String getSeatActorName(int seatIndex, int[] seatTypes) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return "未知";
        if (seatTypes != null && seatIndex < seatTypes.length && seatTypes[seatIndex] == SEAT_TYPE_AI) {
            return getFixedSeatName(seatIndex) + "（人机）";
        }
        return getFixedSeatName(seatIndex);
    }

    /**
     * 获取角色名称（地主/农民）。
     *
     * @param seatIndex    座位索引
     * @param landlordIndex 地主座位索引，-1表示未确定
     * @return 角色名称
     */
    public static String getRoleName(int seatIndex, int landlordIndex) {
        if (landlordIndex < 0) return "待定";
        return landlordIndex == seatIndex ? "地主" : "农民";
    }

    /**
     * 获取简短座位名称。
     *
     * @param seatIndex 座位索引
     * @param seatTypes 座位类型数组
     * @return AI返回"人机"，其他返回P1/P2/P3
     */
    public static String getShortSeatName(int seatIndex, int[] seatTypes) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return "未知";
        if (seatTypes != null && seatIndex < seatTypes.length && seatTypes[seatIndex] == SEAT_TYPE_AI) {
            return "人机";
        }
        return "P" + (seatIndex + 1);
    }

    /**
     * 判断是否为本地玩家座位。
     *
     * @param seatIndex  待判断的座位索引
     * @param mode       游戏模式（0=单机/房主，1=客户端）
     * @param mySeatIndex 客户端模式下的本机座位索引
     * @return true 表示该座位是本地玩家
     */
    public static boolean isLocalSeat(int seatIndex, int mode, int mySeatIndex) {
        return (mode == 0 && seatIndex == 0)
                || (mode == 1 && mySeatIndex >= 0 && seatIndex == mySeatIndex);
    }

    /**
     * 获取回合中的座位名称（本地玩家显示"你"）。
     *
     * @param seatIndex 座位索引
     * @param mode      游戏模式
     * @param mySeatIndex 本机座位索引
     * @param seatTypes 座位类型数组
     * @return 显示用名称
     */
    public static String getTurnSeatName(int seatIndex, int mode, int mySeatIndex, int[] seatTypes) {
        return isLocalSeat(seatIndex, mode, mySeatIndex) ? "你" : getShortSeatName(seatIndex, seatTypes);
    }

    public static String getSeatName(int seatIndex, int[] seatTypes, int landlordIndex) {
        if (seatIndex < 0 || seatIndex >= TOTAL_SEATS) return "未知";
        String role = getRoleName(seatIndex, landlordIndex);
        if (seatTypes != null && seatIndex < seatTypes.length && seatTypes[seatIndex] == SEAT_TYPE_AI) {
            return "人机（" + role + "）";
        }
        return "P" + (seatIndex + 1) + "（" + role + "）";
    }

    public static boolean hasDisconnectedRemoteSeat(int[] seatTypes, int[] seatClientIds) {
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE && seatClientIds[i] < 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnyRemoteSeat(int[] seatTypes) {
        for (int i = 1; i < TOTAL_SEATS; i++) {
            if (seatTypes[i] == SEAT_TYPE_REMOTE) {
                return true;
            }
        }
        return false;
    }
}
