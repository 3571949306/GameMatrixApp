package com.gamecenter.app.adb;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Independent implementation of the fixed scrcpy 3.3.4 video/control wire format. */
public final class ScrcpyProtocol {
    public static final int MAX_PACKET = 8 * 1024 * 1024;
    public static final int H264 = 0x68323634;
    public static final int DOWN = 0, UP = 1, MOVE = 2, CANCEL = 3;
    private ScrcpyProtocol() {}

    public static final class Size {
        public final int width, height;
        public Size(int width, int height) {
            if (width <= 0 || height <= 0 || width > 8192 || height > 8192
                    || (long) width * height > 16_777_216L) {
                throw new IllegalArgumentException("Invalid video dimensions");
            }
            this.width = width; this.height = height;
        }
    }

    public static final class Packet {
        public final boolean config, keyFrame;
        public final long presentationTimeUs;
        public final byte[] data;
        private Packet(long flags, byte[] data) {
            config = (flags & Long.MIN_VALUE) != 0;
            keyFrame = (flags & (1L << 62)) != 0;
            presentationTimeUs = flags & 0x3fffffffffffffffL;
            this.data = data;
        }
    }

    public static void readDummy(InputStream input) throws IOException {
        if (input.read() != 0) throw new IOException("Invalid scrcpy forward handshake");
    }

    public static Size readVideoHeader(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        if (in.readInt() != H264) throw new IOException("Expected scrcpy H.264 video stream");
        try { return new Size(in.readInt(), in.readInt()); }
        catch (IllegalArgumentException error) { throw new IOException("Invalid scrcpy video size", error); }
    }

    public static Packet readPacket(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        long flags = in.readLong();
        int size = in.readInt();
        if (size <= 0 || size > MAX_PACKET || ((flags & Long.MIN_VALUE) != 0 && size > 65536)) {
            throw new IOException("Invalid scrcpy frame length");
        }
        byte[] data = new byte[size];
        in.readFully(data);
        return new Packet(flags, data);
    }

    public static byte[] touch(int action, float normalizedX, float normalizedY, Size size) {
        if (action < DOWN || action > CANCEL || !Float.isFinite(normalizedX) || !Float.isFinite(normalizedY)) {
            throw new IllegalArgumentException("Invalid touch event");
        }
        int x = Math.min(size.width - 1, Math.max(0, (int) (normalizedX * size.width)));
        int y = Math.min(size.height - 1, Math.max(0, (int) (normalizedY * size.height)));
        return buffer(32).put((byte) 2).put((byte) action).putLong(0L)
                .putInt(x).putInt(y).putShort((short) size.width).putShort((short) size.height)
                .putShort((short) ((action == UP || action == CANCEL) ? 0 : 0xffff))
                .putInt(0).putInt(0).array();
    }

    /** A down/up pair is one queue entry, so overload cannot drop just the key release. */
    public static byte[] key(int keyCode) {
        if (keyCode < 0 || keyCode > 1000) throw new IllegalArgumentException("Invalid key code");
        ByteBuffer message = buffer(28);
        for (int action = 0; action <= 1; action++) {
            message.put((byte) 0).put((byte) action).putInt(keyCode).putInt(0).putInt(0);
        }
        return message.array();
    }

    /** Preserve UTF-8 code points while splitting into the server's 300-byte text messages. */
    public static byte[] text(String text) {
        if (text == null || text.length() > 16384) throw new IllegalArgumentException("Text is too long");
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        if (data.length > 16384) throw new IllegalArgumentException("UTF-8 text is too long");
        ByteArrayOutputStream output = new ByteArrayOutputStream(data.length + 300);
        for (int offset = 0; offset < data.length;) {
            int end = Math.min(offset + 300, data.length);
            while (end < data.length && (data[end] & 0xc0) == 0x80) end--;
            int count = end - offset;
            byte[] header = buffer(5).put((byte) 1).putInt(count).array();
            output.write(header, 0, header.length);
            output.write(data, offset, count);
            offset = end;
        }
        return output.toByteArray();
    }

    public static byte[] screenPower(boolean on) { return new byte[] {10, (byte) (on ? 1 : 0)}; }

    /** Drain permitted server-to-client messages without retaining clipboard/UHID contents. */
    public static void discardDeviceMessage(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        int type = in.readUnsignedByte();
        int remaining;
        if (type == 0) {
            remaining = in.readInt();
            if (remaining < 0 || remaining > (1 << 18) - 5) throw new IOException("Invalid clipboard message length");
        } else if (type == 1) {
            in.readLong(); return;
        } else if (type == 2) {
            in.readUnsignedShort();
            remaining = in.readUnsignedShort();
        } else {
            throw new IOException("Unknown scrcpy device message");
        }
        byte[] discard = new byte[Math.min(4096, remaining)];
        while (remaining > 0) {
            int count = in.read(discard, 0, Math.min(discard.length, remaining));
            if (count < 0) throw new EOFException("Truncated scrcpy device message");
            if (count == 0) throw new IOException("Device stream made no progress");
            remaining -= count;
        }
    }

    private static ByteBuffer buffer(int size) { return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN); }
}
