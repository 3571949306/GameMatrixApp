package com.gamecenter.app.network.p2p.stun;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * STUN 协议报文编解码器 (RFC 5389 / RFC 3489)。
 * <p>
 * 报文头部结构 (20 字节):
 * <pre>
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |0 0|     STUN Message Type     |         Message Length        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         Magic Cookie                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                                                               |
 * |                     Transaction ID (96 bits)                  |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 */
public class StunMessage {

    public static final int MAGIC_COOKIE = 0x2112A442;
    public static final int HEADER_LENGTH = 20;

    // 消息类型
    public static final int TYPE_BINDING_REQUEST = 0x0001;
    public static final int TYPE_BINDING_RESPONSE = 0x0101;
    public static final int TYPE_BINDING_ERROR_RESPONSE = 0x0111;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final int messageType;
    private final int magicCookie;
    private final byte[] transactionId;
    private final List<StunAttribute> attributes = new ArrayList<>();

    public StunMessage(int messageType, int magicCookie, @NonNull byte[] transactionId) {
        this.messageType = messageType;
        this.magicCookie = magicCookie;
        this.transactionId = Arrays.copyOf(transactionId, transactionId.length);
    }

    /**
     * 生成一个标准的 RFC 5389 Binding Request 报文。
     */
    @NonNull
    public static StunMessage createBindingRequest() {
        byte[] txId = new byte[12];
        RANDOM.nextBytes(txId);
        return new StunMessage(TYPE_BINDING_REQUEST, MAGIC_COOKIE, txId);
    }

    /**
     * 生成一个带 CHANGE-REQUEST 属性的 Binding Request 报文 (用于 RFC 3489 NAT 分类测试)。
     */
    @NonNull
    public static StunMessage createBindingRequestWithChangeRequest(boolean changeIp, boolean changePort) {
        StunMessage msg = createBindingRequest();
        msg.addAttribute(StunAttribute.createChangeRequest(changeIp, changePort));
        return msg;
    }

    public int getMessageType() {
        return messageType;
    }

    public int getMagicCookie() {
        return magicCookie;
    }

    @NonNull
    public byte[] getTransactionId() {
        return Arrays.copyOf(transactionId, transactionId.length);
    }

    public void addAttribute(@NonNull StunAttribute attribute) {
        attributes.add(attribute);
    }

    @NonNull
    public List<StunAttribute> getAttributes() {
        return attributes;
    }

    @Nullable
    public StunAttribute getAttribute(int type) {
        for (StunAttribute attr : attributes) {
            if (attr.getType() == type) {
                return attr;
            }
        }
        return null;
    }

    /**
     * 获取经过 NAT 映射的外网地址 (优先解析 XOR-MAPPED-ADDRESS，降级 MAPPED-ADDRESS)。
     */
    @Nullable
    public StunAttribute.StunAddress getReflexiveAddress() {
        StunAttribute xorAttr = getAttribute(StunAttribute.TYPE_XOR_MAPPED_ADDRESS);
        if (xorAttr != null) {
            StunAttribute.StunAddress addr = xorAttr.parseXorMappedAddress(magicCookie, transactionId);
            if (addr != null) return addr;
        }
        StunAttribute mappedAttr = getAttribute(StunAttribute.TYPE_MAPPED_ADDRESS);
        if (mappedAttr != null) {
            return mappedAttr.parseMappedAddress();
        }
        return null;
    }

    /**
     * 获取 CHANGED-ADDRESS (STUN 服务器告知的对端备用 IP 和端口)。
     */
    @Nullable
    public StunAttribute.StunAddress getChangedAddress() {
        StunAttribute changedAttr = getAttribute(StunAttribute.TYPE_CHANGED_ADDRESS);
        if (changedAttr != null) {
            return changedAttr.parseMappedAddress();
        }
        return null;
    }

    /**
     * 序列化为字节流。
     */
    @NonNull
    public byte[] encode() {
        // 计算属性载荷总长度（每个属性需4字节对齐）
        int payloadLength = 0;
        for (StunAttribute attr : attributes) {
            payloadLength += 4 + attr.getValue().length;
            int padding = (4 - (attr.getValue().length % 4)) % 4;
            payloadLength += padding;
        }

        ByteBuffer buf = ByteBuffer.allocate(HEADER_LENGTH + payloadLength).order(ByteOrder.BIG_ENDIAN);
        // Header
        buf.putShort((short) messageType);
        buf.putShort((short) payloadLength);
        buf.putInt(magicCookie);
        buf.put(transactionId, 0, Math.min(12, transactionId.length));

        // Attributes
        for (StunAttribute attr : attributes) {
            buf.putShort((short) attr.getType());
            buf.putShort((short) attr.getValue().length);
            buf.put(attr.getValue());
            int padding = (4 - (attr.getValue().length % 4)) % 4;
            for (int i = 0; i < padding; i++) {
                buf.put((byte) 0);
            }
        }

        return buf.array();
    }

    /**
     * 反序列化解析接收到的 STUN 字节流。
     */
    @Nullable
    public static StunMessage decode(@NonNull byte[] data, int length) {
        if (length < HEADER_LENGTH) return null;

        ByteBuffer buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN);
        int messageType = buf.getShort() & 0xFFFF;
        int messageLength = buf.getShort() & 0xFFFF;
        int magicCookie = buf.getInt();

        byte[] txId = new byte[12];
        buf.get(txId);

        if (length < HEADER_LENGTH + messageLength) {
            return null; // 报文不完整
        }

        StunMessage msg = new StunMessage(messageType, magicCookie, txId);

        int bytesRead = 0;
        while (bytesRead < messageLength && buf.remaining() >= 4) {
            int attrType = buf.getShort() & 0xFFFF;
            int attrLength = buf.getShort() & 0xFFFF;
            bytesRead += 4;

            if (buf.remaining() < attrLength) break;
            byte[] attrVal = new byte[attrLength];
            buf.get(attrVal);
            bytesRead += attrLength;

            // 4字节对齐填充
            int padding = (4 - (attrLength % 4)) % 4;
            if (buf.remaining() >= padding) {
                buf.position(buf.position() + padding);
                bytesRead += padding;
            }

            msg.addAttribute(new StunAttribute(attrType, attrVal));
        }

        return msg;
    }
}
