package com.gamecenter.app.network;

/** 房间码生成工具存根（实际运行时由宿主提供） */
public class RoomCodeHelper {
    public static String generateRoomCode() {
        return String.valueOf((int)(Math.random() * 900000) + 100000);
    }
}
