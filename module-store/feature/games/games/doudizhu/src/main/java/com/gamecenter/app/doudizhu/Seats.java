package com.gamecenter.app.doudizhu;

/**
 * 斗地主座位常量。
 *
 * <p>联机裁剪说明（2026-08-30）：原 {@code DouDiZhuSeatManager} 的 HOST/REMOTE/AI 座位类型、
 * P2P Peer Token 与断线重连管理已随联机模块移除，可经 {@code git tag pre-ddz-online-cut} 找回。
 * 单机版固定三座位：0=本地玩家，1/2=AI。未来回归联机时，在此扩展 REMOTE 座位类型，
 * 并为对局控制器新增"远端消息动作源"，规则层与 UI 层无需改动。</p>
 */
public final class Seats {

    /** 总座位数（斗地主固定 3 人） */
    public static final int TOTAL_SEATS = 3;

    /** 座位类型：本地人类玩家 */
    public static final int TYPE_HUMAN = 0;
    /** 座位类型：AI 机器人 */
    public static final int TYPE_AI = 1;

    /** 单机模式的固定座位配置：[玩家, 左AI, 右AI] */
    public static int[] singlePlayerSeatTypes() {
        return new int[]{TYPE_HUMAN, TYPE_AI, TYPE_AI};
    }

    /** 座位 0：本地玩家 */
    public static final int SEAT_PLAYER = 0;
    /** 座位 1：左家 AI */
    public static final int SEAT_LEFT_AI = 1;
    /** 座位 2：右家 AI */
    public static final int SEAT_RIGHT_AI = 2;

    private Seats() {}
}
