package com.gamecenter.app.adb.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Streaming ADB sync v1. The caller owns source/sink streams; each operation owns its channel. */
public final class AdbSync {
    public interface Progress { void update(long bytes); }

    public static final class Entry {
        public final String name;
        public final int mode;
        public final long size;
        public final long mtime;
        public Entry(String name, int mode, long size, long mtime) {
            this.name = name; this.mode = mode; this.size = size; this.mtime = mtime;
        }
        public boolean isDirectory() { return (mode & 0170000) == 0040000; }
        public boolean isSymbolicLink() { return (mode & 0170000) == 0120000; }
    }

    private static final int CHUNK = 64 * 1024;
    private static final int MAX_PATH = 1024;
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_NAMES_BYTES = 8 * 1024 * 1024;
    private final AdbTransport transport;

    public AdbSync(AdbTransport transport) {
        if (transport == null) throw new IllegalArgumentException("transport is required");
        this.transport = transport;
    }

    public List<Entry> list(String path) throws IOException {
        byte[] request = path(path);
        try (AdbTransport.Channel channel = transport.open("sync:")) {
            request(channel.output(), "LIST", request);
            List<Entry> result = new ArrayList<>();
            int namesBytes = 0;
            while (true) {
                int id = readInt(channel.input());
                if (id == AdbWireConnection.id("FAIL")) throw failure(channel.input());
                if (id != AdbWireConnection.id("DENT") && id != AdbWireConnection.id("DONE")) {
                    throw new IOException("Unexpected sync LIST response");
                }
                byte[] body = new byte[16];
                AdbWireConnection.readFully(channel.input(), body, 0, body.length);
                if (id == AdbWireConnection.id("DONE")) return result;
                int mode = AdbWireConnection.get(body, 0);
                long size = Integer.toUnsignedLong(AdbWireConnection.get(body, 4));
                long mtime = Integer.toUnsignedLong(AdbWireConnection.get(body, 8));
                int nameLength = AdbWireConnection.get(body, 12);
                if (nameLength <= 0 || nameLength > MAX_PATH || namesBytes > MAX_NAMES_BYTES - nameLength) {
                    throw new IOException("Invalid or excessive sync directory names");
                }
                byte[] nameBytes = new byte[nameLength];
                AdbWireConnection.readFully(channel.input(), nameBytes, 0, nameLength);
                String name = utf8(nameBytes);
                namesBytes += nameLength;
                if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
                    throw new IOException("Unsafe sync directory name");
                }
                if (name.equals(".") || name.equals("..")) continue;
                if (result.size() >= MAX_ENTRIES) throw new IOException("Directory exceeds safe listing limit; use a narrower path");
                result.add(new Entry(name, mode, size, mtime));
            }
        }
    }

    /** size is the expected byte count, or -1 for a source of unknown size. */
    public void push(String remotePath, InputStream source, long size, Progress progress) throws IOException {
        path(remotePath);
        if (source == null || size < -1) throw new IOException("Invalid sync source/size");
        byte[] destination = path(remotePath + ",33188"); // regular file, 0644; no executable permission by default
        try (AdbTransport.Channel channel = transport.open("sync:")) {
            request(channel.output(), "SEND", destination);
            byte[] buffer = new byte[CHUNK];
            long total = 0;
            while (size < 0 || total < size) {
                cancelled();
                int limit = size < 0 ? buffer.length : (int) Math.min(buffer.length, size - total);
                int count = source.read(buffer, 0, limit);
                if (count < 0) {
                    if (size >= 0) throw new IOException("Source shorter than declared sync size");
                    break;
                }
                if (count == 0) throw new IOException("Sync source made no progress");
                header(channel.output(), "DATA", count);
                channel.output().write(buffer, 0, count);
                total = Math.addExact(total, count);
                if (progress != null) progress.update(total);
            }
            if (size >= 0 && source.read() != -1) throw new IOException("Source longer than declared sync size");
            cancelled();
            header(channel.output(), "DONE", (int) (System.currentTimeMillis() / 1000L));
            channel.output().flush();
            int status = readInt(channel.input());
            if (status == AdbWireConnection.id("FAIL")) throw failure(channel.input());
            if (status != AdbWireConnection.id("OKAY") || readInt(channel.input()) != 0) {
                throw new IOException("Invalid sync SEND status");
            }
        }
    }

    public void pull(String remotePath, OutputStream destination, Progress progress) throws IOException {
        byte[] request = path(remotePath);
        if (destination == null) throw new IOException("Sync destination is required");
        try (AdbTransport.Channel channel = transport.open("sync:")) {
            request(channel.output(), "RECV", request);
            byte[] buffer = new byte[CHUNK];
            long total = 0;
            while (true) {
                int id = readInt(channel.input());
                if (id == AdbWireConnection.id("FAIL")) throw failure(channel.input());
                int length = readInt(channel.input());
                if (id == AdbWireConnection.id("DONE")) {
                    if (length != 0) throw new IOException("Invalid sync RECV terminator");
                    destination.flush();
                    return;
                }
                if (id != AdbWireConnection.id("DATA") || length <= 0 || length > buffer.length) {
                    throw new IOException("Invalid sync DATA length/command");
                }
                AdbWireConnection.readFully(channel.input(), buffer, 0, length);
                cancelled();
                destination.write(buffer, 0, length);
                total = Math.addExact(total, length);
                if (progress != null) progress.update(total);
            }
        }
    }

    private static byte[] path(String path) throws IOException {
        if (path == null || !path.startsWith("/") || path.indexOf('\0') >= 0) throw new IOException("Absolute remote path required");
        byte[] bytes = path.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_PATH) throw new IOException("Remote path too long for sync v1");
        return bytes;
    }
    private static void request(OutputStream out, String id, byte[] data) throws IOException {
        header(out, id, data.length); out.write(data); out.flush();
    }
    private static void header(OutputStream out, String id, int length) throws IOException {
        byte[] bytes = new byte[8];
        AdbWireConnection.put(bytes, 0, AdbWireConnection.id(id));
        AdbWireConnection.put(bytes, 4, length);
        out.write(bytes);
    }
    private static int readInt(InputStream in) throws IOException {
        byte[] bytes = new byte[4];
        AdbWireConnection.readFully(in, bytes, 0, bytes.length);
        return AdbWireConnection.get(bytes, 0);
    }
    private static IOException failure(InputStream in) throws IOException {
        int length = readInt(in);
        if (length < 0 || length > 4096) return new IOException("Invalid sync error length");
        byte[] message = new byte[length];
        AdbWireConnection.readFully(in, message, 0, length);
        return new IOException("ADB sync rejected: " + utf8(message));
    }
    private static String utf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            throw new IOException("Malformed UTF-8 in sync response", error);
        }
    }
    private static void cancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("ADB sync cancelled");
    }
}
