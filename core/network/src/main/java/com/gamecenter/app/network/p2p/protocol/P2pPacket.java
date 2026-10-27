package com.gamecenter.app.network.p2p.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * P2P v2.0 统一通信数据帧。
 * <p>
 * 报文总长严格受控于 KCP MTU <= 1200 字节，彻底杜绝国内家庭宽带 PPPoE / IPv6 分片黑洞。
 * <p>
 * 帧结构 (Header 18 字节，可作为 AEAD 附加认证数据 AAD 防篡改):
 * <pre>
 * +-------------------+----------------------+----------------------+------------+
 * | Magic (2 Bytes)   | ConnectionId (8B)    | PacketNumber (4B)    | Type (1B)  |
 * +-------------------+----------------------+----------------------+------------+
 * | Flags (1 Byte)    | PayloadLen (2 Bytes) | Payload (<= 1182 B)               |
 * +-------------------+----------------------+-----------------------------------+
 * </pre>
 */
public class P2pPacket {

    /** 专有游戏协议头 "GC" (Game Center) */
    public static final short MAGIC_CODE = (short) 0x4743;

    /** 严控 IPv6 安全 MTU 为 1200 字节，防止公网 PPPoE 分片丢失 */
    public static final int SAFE_IPV6_MTU = 1200;
    public static final int HEADER_SIZE = 18;

    // 报文类型
    public static final byte TYPE_KNOCK_PING = 0x01;
    public static final byte TYPE_KNOCK_PONG = 0x02;
    public static final byte TYPE_BIND_ACK = 0x03;
    public static final byte TYPE_GAME_DATA = 0x04;
    public static final byte TYPE_HEARTBEAT = 0x05;
    public static final byte TYPE_CLOSE = 0x06;

    private final long connectionId;
    private final int packetNumber;
    private final byte type;
    private final byte flags;
    @NonNull
    private final byte[] payload;

    public P2pPacket(long connectionId, int packetNumber, byte type, @Nullable byte[] payload) {
        this(connectionId, packetNumber, type, (byte) 0, payload);
    }

    public P2pPacket(long connectionId, int packetNumber, byte type, byte flags, @Nullable byte[] payload) {
        this.connectionId = connectionId;
        this.packetNumber = packetNumber;
        this.type = type;
        this.flags = flags;
        this.payload = payload != null ? payload : new byte[0];
    }

    public long getConnectionId() {
        return connectionId;
    }

    public int getPacketNumber() {
        return packetNumber;
    }

    public byte getType() {
        return type;
    }

    public byte getFlags() {
        return flags;
    }

    @NonNull
    public byte[] getPayload() {
        return payload;
    }

    /**
     * 序列化为字节流（校验并确保不超过 MTU=1200）。
     */
    @NonNull
    public byte[] encode() {
        int totalLen = HEADER_SIZE + payload.length;
        if (totalLen > SAFE_IPV6_MTU) {
            throw new IllegalArgumentException("Packet size " + totalLen + " exceeds SAFE_IPV6_MTU " + SAFE_IPV6_MTU);
        }

        ByteBuffer buf = ByteBuffer.allocate(totalLen).order(ByteOrder.BIG_ENDIAN);
        buf.putShort(MAGIC_CODE);
        buf.putLong(connectionId);
        buf.putInt(packetNumber);
        buf.put(type);
        buf.put(flags);
        buf.putShort((short) payload.length);
        if (payload.length > 0) {
            buf.put(payload);
        }
        return buf.array();
    }

    /**
     * 反序列化解析。
     */
    @Nullable
    public static P2pPacket decode(@NonNull byte[] data, int length) {
        if (length < HEADER_SIZE || length > SAFE_IPV6_MTU) return null;

        ByteBuffer buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN);
        short magic = buf.getShort();
        if (magic != MAGIC_CODE) return null;

        long cid = buf.getLong();
        int pktNum = buf.getInt();
        byte type = buf.get();
        byte flags = buf.get();
        int payloadLen = buf.getShort() & 0xFFFF;

        if (buf.remaining() < payloadLen) return null;

        byte[] payload = new byte[payloadLen];
        buf.get(payload);

        return new P2pPacket(cid, pktNum, type, flags, payload);
    }
}
