package com.gamecenter.core.online;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 房间管理器。
 *
 * <p>负责房间的创建、加入、查询等操作：
 * <ul>
 *   <li>生成房间码</li>
 *   <li>管理当前房间状态</li>
 *   <li>维护房间历史记录</li>
 *   <li>持久化房间信息</li>
 * </ul>
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-27
 */
public class RoomManager {

    private static final String TAG = "RoomManager";

    /** SharedPreferences 文件名 */
    private static final String PREFS_NAME = "online_rooms";

    /** 房间码长度 */
    private static final int ROOM_CODE_LENGTH = 6;

    /** 房间码字符集（排除易混淆字符：0/O, 1/I/L） */
    private static final String ROOM_CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /** 上下文 */
    private final Context context;

    /** 当前房间码 */
    @Nullable
    private String currentRoomCode;

    /** 当前房间游戏类型 */
    @Nullable
    private String currentGameType;

    /** 当前是否为房主 */
    private boolean isHost = false;

    /** 房间内玩家列表 */
    private final List<String> playerList = new ArrayList<>();

    /** 随机数生成器 */
    private final Random random = new Random();

    /**
     * 构造函数。
     *
     * @param context Android Context
     */
    public RoomManager(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    /**
     * 生成随机房间码。
     *
     * <p>房间码格式：6 位大写字母+数字（排除易混淆字符）。
     * 示例：A3K9M7
     *
     * @return 房间码
     */
    @NonNull
    public static String generateRoomCode() {
        StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
        Random rng = new Random();
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            int index = rng.nextInt(ROOM_CODE_CHARS.length());
            sb.append(ROOM_CODE_CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 设置当前房间。
     *
     * @param roomCode 房间码
     * @param gameType 游戏类型
     * @param isHost   是否为房主
     */
    public void setCurrentRoom(@NonNull String roomCode,
                               @Nullable String gameType,
                               boolean isHost) {
        this.currentRoomCode = roomCode;
        this.currentGameType = gameType;
        this.isHost = isHost;
        this.playerList.clear();

        // 保存到历史记录
        saveRoomToHistory(roomCode, gameType);

        Log.d(TAG, "当前房间已设置: " + roomCode + " (房主: " + isHost + ")");
    }

    /**
     * 清除当前房间。
     */
    public void clearCurrentRoom() {
        this.currentRoomCode = null;
        this.currentGameType = null;
        this.isHost = false;
        this.playerList.clear();
        Log.d(TAG, "当前房间已清除");
    }

    /**
     * 添加玩家到当前房间。
     *
     * @param playerId 玩家 ID
     */
    public void addPlayer(@NonNull String playerId) {
        if (!playerList.contains(playerId)) {
            playerList.add(playerId);
            Log.d(TAG, "玩家加入房间: " + playerId);
        }
    }

    /**
     * 从当前房间移除玩家。
     *
     * @param playerId 玩家 ID
     */
    public void removePlayer(@NonNull String playerId) {
        playerList.remove(playerId);
        Log.d(TAG, "玩家离开房间: " + playerId);
    }

    /**
     * 获取当前房间内的玩家列表。
     *
     * @return 玩家 ID 列表（不可修改）
     */
    @NonNull
    public List<String> getPlayers() {
        return Collections.unmodifiableList(new ArrayList<>(playerList));
    }

    /**
     * 获取当前房间内的玩家数量。
     *
     * @return 玩家数量
     */
    public int getPlayerCount() {
        return playerList.size();
    }

    /**
     * 获取最近的房间历史记录。
     *
     * @return 房间码列表（最近在前）
     */
    @NonNull
    public List<String> getRecentRooms() {
        if (context == null) {
            return new ArrayList<>();
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String history = prefs.getString("recent_rooms", "");
        if (history.isEmpty()) {
            return new ArrayList<>();
        }

        String[] rooms = history.split(",");
        List<String> result = new ArrayList<>();
        for (String room : rooms) {
            if (!room.trim().isEmpty()) {
                result.add(room.trim());
            }
        }

        return result;
    }

    /**
     * 保存房间到历史记录。
     */
    private void saveRoomToHistory(@NonNull String roomCode,
                                   @Nullable String gameType) {
        if (context == null) return;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String history = prefs.getString("recent_rooms", "");

            // 去重：如果已存在则移到最前面
            List<String> rooms = new ArrayList<>();
            for (String r : history.split(",")) {
                if (!r.trim().isEmpty() && !r.trim().equals(roomCode)) {
                    rooms.add(r.trim());
                }
            }
            rooms.add(0, roomCode);

            // 最多保留 20 条
            if (rooms.size() > 20) {
                rooms = rooms.subList(0, 20);
            }

            prefs.edit().putString("recent_rooms", String.join(",", rooms)).apply();
            Log.d(TAG, "房间已保存到历史: " + roomCode);
        } catch (Exception e) {
            Log.w(TAG, "保存房间历史失败", e);
        }
    }

    // ========== Getter ==========

    @Nullable
    public String getCurrentRoomCode() {
        return currentRoomCode;
    }

    @Nullable
    public String getCurrentGameType() {
        return currentGameType;
    }

    public boolean isHost() {
        return isHost;
    }

    public boolean isInRoom() {
        return currentRoomCode != null;
    }
}
