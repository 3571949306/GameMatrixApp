package com.gamecenter.app.network.p2p.stun;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * STUN 属性封装 (RFC 5389 / RFC 3489 TLV 格式)。
 */
public class StunAttribute {

    // 常用属性类型定义
    public static final int TYPE_MAPPED_ADDRESS = 0x0001;
    public static final int TYPE_RESPONSE_ADDRESS = 0x0002;
    public static final int TYPE_CHANGE_REQUEST = 0x0003;
    public static final int TYPE_SOURCE_ADDRESS = 0x0004;
    public static final int TYPE_CHANGED_ADDRESS = 0x0005;
    public static final int TYPE_USERNAME = 0x0006;
    public static final int TYPE_ERROR_CODE = 0x0009;
    public static final int TYPE_UNKNOWN_ATTRIBUTES = 0x000A;
    public static final int TYPE_XOR_MAPPED_ADDRESS = 0x0020;
    public static final int TYPE_SOFTWARE = 0x8022;
    public static final int TYPE_ALTERNATE_SERVER = 0x8023;

    public static final int FAMILY_IPV4 = 0x01;
    public static final int FAMILY_IPV6 = 0x02;

    private final int type;
    private final byte[] value;

    public StunAttribute(int type, @NonNull byte[] value) {
        this.type = type;
        this.value = value;
    }

    public int getType() {
        return type;
    }

    @NonNull
    public byte[] getValue() {
        return value;
    }

    /**
     * 创建 CHANGE-REQUEST 属性。
     *
     * @param changeIp   是否请求从不同的 IP 回包
     * @param changePort 是否请求从不同的端口回包
     */
    @NonNull
    public static StunAttribute createChangeRequest(boolean changeIp, boolean changePort) {
        ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        int flags = 0;
        if (changeIp) flags |= 0x04;   // Change IP flag (bit 2)
        if (changePort) flags |= 0x02; // Change Port flag (bit 1)
        buf.putInt(flags);
        return new StunAttribute(TYPE_CHANGE_REQUEST, buf.array());
    }

    /**
     * 解析 MAPPED-ADDRESS 或 CHANGED-ADDRESS 或 SOURCE-ADDRESS。
     */
    @Nullable
    public StunAddress parseMappedAddress() {
        if (value.length < 8) return null;
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
        buf.get(); // 忽略保留字节 0x00
        int family = buf.get() & 0xFF;
        int port = buf.getShort() & 0xFFFF;

        byte[] addrBytes;
        if (family == FAMILY_IPV4) {
            if (value.length < 8) return null;
            addrBytes = new byte[4];
            buf.get(addrBytes);
        } else if (family == FAMILY_IPV6) {
            if (value.length < 20) return null;
            addrBytes = new byte[16];
            buf.get(addrBytes);
        } else {
            return null;
        }

        try {
            InetAddress address = InetAddress.getByAddress(addrBytes);
            return new StunAddress(address.getHostAddress(), port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * 解析 XOR-MAPPED-ADDRESS (RFC 5389 核心属性，混淆防 NAT ALG 篡改)。
     *
     * @param magicCookie   STUN 魔数 (0x2112A442)
     * @param transactionId 12字节或16字节事务ID
     */
    @Nullable
    public StunAddress parseXorMappedAddress(int magicCookie, @NonNull byte[] transactionId) {
        if (value.length < 8) return null;
        ByteBuffer buf = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
        buf.get(); // 0x00
        int family = buf.get() & 0xFF;
        int rawPort = buf.getShort() & 0xFFFF;

        // XOR 端口解密: Port = RawPort ^ (MagicCookie >> 16)
        int port = rawPort ^ ((magicCookie >>> 16) & 0xFFFF);

        byte[] addrBytes;
        if (family == FAMILY_IPV4) {
            if (value.length < 8) return null;
            addrBytes = new byte[4];
            buf.get(addrBytes);
            // XOR IPv4 解密: IP = RawIP ^ MagicCookie
            byte[] cookieBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(magicCookie).array();
            for (int i = 0; i < 4; i++) {
                addrBytes[i] ^= cookieBytes[i];
            }
        } else if (family == FAMILY_IPV6) {
            if (value.length < 20) return null;
            addrBytes = new byte[16];
            buf.get(addrBytes);
            // XOR IPv6 解密: IP = RawIP ^ (MagicCookie + TransactionID)
            byte[] xorKey = new byte[16];
            ByteBuffer keyBuf = ByteBuffer.wrap(xorKey).order(ByteOrder.BIG_ENDIAN);
            keyBuf.putInt(magicCookie);
            keyBuf.put(transactionId, 0, Math.min(12, transactionId.length));
            for (int i = 0; i < 16; i++) {
                addrBytes[i] ^= xorKey[i];
            }
        } else {
            return null;
        }

        try {
            InetAddress address = InetAddress.getByAddress(addrBytes);
            return new StunAddress(address.getHostAddress(), port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * 地址载荷数据类。
     */
    public static class StunAddress {
        private final String ip;
        private final int port;

        public StunAddress(@NonNull String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @NonNull
        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        @NonNull
        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }
}
