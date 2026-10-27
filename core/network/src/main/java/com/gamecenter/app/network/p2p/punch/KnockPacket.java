package com.gamecenter.app.network.p2p.punch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * P2P 打洞、保活与传输统一数据帧封装。
 * <p>
 * 头部格式 (23 字节):
 * <ul>
 *   <li>Magic: 4 字节 (0x50325001 = "P2P\1")</li>
 *   <li>Type: 1 字节 (PING / PONG / BIND_ACK / KEEP_ALIVE / DATA)</li>
 *   <li>SessionHash: 4 字节 (房间码哈希，校验防串线)</li>
 *   <li>Sequence: 4 字节 (递增序号)</li>
 *   <li>Timestamp: 8 字节 (发送时间戳，用于高精度 RTT 计算)</li>
 *   <li>PayloadLen: 2 字节 (载荷长度)</li>
 * </ul>
 */
public class KnockPacket {

    public static final int MAGIC = 0x50325001;
    public static final int HEADER_SIZE = 23;

    // 报文类型
    public static final byte TYPE_KNOCK_PING = 0x01;
    public static final byte TYPE_KNOCK_PONG = 0x02;
    public static final byte TYPE_KNOCK_BIND_ACK = 0x03;
    public static final byte TYPE_KEEP_ALIVE = 0x04;
    public static final byte TYPE_DATA_RELIABLE = 0x05;
    public static final byte TYPE_DATA_UNRELIABLE = 0x06;

    private final byte type;
    private final int sessionHash;
    private final int sequence;
    private final long timestamp;
    @NonNull
    private final byte[] payload;

    public KnockPacket(byte type, int sessionHash, int sequence, long timestamp, @Nullable byte[] payload) {
        this.type = type;
        this.sessionHash = sessionHash;
        this.sequence = sequence;
        this.timestamp = timestamp;
        this.payload = payload != null ? payload : new byte[0];
    }

    public byte getType() {
        return type;
    }

    public int getSessionHash() {
        return sessionHash;
    }

    public int getSequence() {
        return sequence;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @NonNull
    public byte[] getPayload() {
        return payload;
    }

    /**
     * 编码为二进制数据包。
     */
    @NonNull
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + payload.length).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(MAGIC);
        buf.put(type);
        buf.putInt(sessionHash);
        buf.putInt(sequence);
        buf.putLong(timestamp);
        buf.putShort((short) payload.length);
        if (payload.length > 0) {
            buf.put(payload);
        }
        return buf.array();
    }

    /**
     * 反序列化接收到的二进制数据包。
     */
    @Nullable
    public static KnockPacket decode(@NonNull byte[] data, int length) {
        if (length < HEADER_SIZE) return null;

        ByteBuffer buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN);
        int magic = buf.getInt();
        if (magic != MAGIC) return null;

        byte type = buf.get();
        int sessionHash = buf.getInt();
        int sequence = buf.getInt();
        long timestamp = buf.getLong();
        int payloadLen = buf.getShort() & 0xFFFF;

        if (buf.remaining() < payloadLen) return null;

        byte[] payload = new byte[payloadLen];
        buf.get(payload);

        return new KnockPacket(type, sessionHash, sequence, timestamp, payload);
    }
}
