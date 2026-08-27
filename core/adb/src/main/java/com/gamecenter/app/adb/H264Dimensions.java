package com.gamecenter.app.adb;

import java.io.IOException;
import java.util.Arrays;

/** Bounded H.264 SPS parser, used to reconfigure non-adaptive decoders on device rotation. */
final class H264Dimensions {
    private H264Dimensions() {}

    static ScrcpyProtocol.Size fromConfig(byte[] annexB) throws IOException {
        if (annexB.length > 65536) throw new IOException("H.264 configuration too large");
        for (int index = 0; index + 3 < annexB.length; index++) {
            int prefix = prefix(annexB, index);
            if (prefix == 0) continue;
            int start = index + prefix;
            int end = start + 1;
            while (end < annexB.length && prefix(annexB, end) == 0) end++;
            if ((annexB[start] & 0x1f) == 7) return parse(Arrays.copyOfRange(annexB, start + 1, end));
            index = end - 1;
        }
        return null;
    }

    private static int prefix(byte[] data, int offset) {
        if (offset + 3 >= data.length || data[offset] != 0 || data[offset + 1] != 0) return 0;
        if (data[offset + 2] == 1) return 3;
        return offset + 4 < data.length && data[offset + 2] == 0 && data[offset + 3] == 1 ? 4 : 0;
    }

    private static ScrcpyProtocol.Size parse(byte[] escaped) throws IOException {
        byte[] rbsp = new byte[escaped.length];
        int length = 0, zeros = 0;
        for (byte value : escaped) {
            int unsigned = value & 255;
            if (zeros >= 2 && unsigned == 3) { zeros = 0; continue; }
            rbsp[length++] = value;
            zeros = unsigned == 0 ? zeros + 1 : 0;
        }
        Bits bits = new Bits(rbsp, length);
        int profile = bits.read(8);
        bits.read(8); bits.read(8); bits.ue();
        int chroma = 1;
        boolean separate = false;
        if (profile == 100 || profile == 110 || profile == 122 || profile == 244 || profile == 44
                || profile == 83 || profile == 86 || profile == 118 || profile == 128 || profile == 138
                || profile == 139 || profile == 134 || profile == 135) {
            chroma = bits.ue();
            if (chroma > 3) throw new IOException("Invalid H.264 chroma format");
            if (chroma == 3) separate = bits.read(1) != 0;
            if (bits.ue() > 6 || bits.ue() > 6) throw new IOException("Unsupported H.264 bit depth");
            bits.read(1);
            if (bits.read(1) != 0) {
                for (int index = 0; index < (chroma == 3 ? 12 : 8); index++) {
                    if (bits.read(1) != 0) skipScaling(bits, index < 6 ? 16 : 64);
                }
            }
        }
        bits.ue();
        int order = bits.ue();
        if (order == 0) bits.ue();
        else if (order == 1) {
            bits.read(1); bits.se(); bits.se();
            int cycle = bits.ue();
            if (cycle > 256) throw new IOException("Invalid H.264 reference cycle");
            for (int index = 0; index < cycle; index++) bits.se();
        } else if (order != 2) throw new IOException("Invalid H.264 picture order");
        bits.ue(); bits.read(1);
        int widthMbs = bits.ue() + 1, heightMap = bits.ue() + 1;
        int frameOnly = bits.read(1);
        if (frameOnly == 0) bits.read(1);
        bits.read(1);
        long left = 0, right = 0, top = 0, bottom = 0;
        if (bits.read(1) != 0) { left = bits.ue(); right = bits.ue(); top = bits.ue(); bottom = bits.ue(); }
        int cropX = chroma == 0 || separate || chroma == 3 ? 1 : 2;
        int cropY = (chroma == 1 && !separate ? 2 : 1) * (2 - frameOnly);
        long width = (long) widthMbs * 16 - (left + right) * cropX;
        long height = (long) heightMap * 16 * (2 - frameOnly) - (top + bottom) * cropY;
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192) throw new IOException("Invalid SPS dimensions");
        try { return new ScrcpyProtocol.Size((int) width, (int) height); }
        catch (IllegalArgumentException error) { throw new IOException("Oversized SPS dimensions", error); }
    }

    private static void skipScaling(Bits bits, int length) throws IOException {
        int last = 8, next = 8;
        for (int index = 0; index < length; index++) {
            if (next != 0) next = (last + bits.se()) & 255;
            if (next != 0) last = next;
        }
    }

    private static final class Bits {
        private final byte[] data;
        private final int limit;
        private int offset;
        Bits(byte[] data, int size) { this.data = data; limit = size * 8; }
        int read(int count) throws IOException {
            if (count < 0 || count > 30 || offset > limit - count) throw new IOException("Truncated SPS");
            int result = 0;
            for (int index = 0; index < count; index++, offset++) {
                result = (result << 1) | ((data[offset / 8] >> (7 - offset % 8)) & 1);
            }
            return result;
        }
        int ue() throws IOException {
            int zeros = 0;
            while (read(1) == 0) if (++zeros > 29) throw new IOException("Oversized SPS integer");
            return ((1 << zeros) - 1) + read(zeros);
        }
        int se() throws IOException { int code = ue(); return (code & 1) == 0 ? -(code / 2) : (code + 1) / 2; }
    }
}
