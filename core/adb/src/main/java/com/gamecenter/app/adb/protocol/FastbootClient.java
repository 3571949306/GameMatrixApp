package com.gamecenter.app.adb.protocol;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Synchronous USB fastboot. No operation is automatically retried, including after disconnect. */
public final class FastbootClient implements Closeable {
    public interface Link extends Closeable {
        /** Read ONE response packet, not a concatenated byte stream. Must be interruptible by close. */
        int read(byte[] destination) throws IOException;
        /** Write the entire given range or throw. Link must bound blocked USB I/O. */
        void write(byte[] source, int offset, int length) throws IOException;
    }
    public interface Progress { void update(long bytes); }
    private static final int MAX_RESPONSE = 256;
    private static final int MAX_INFO = 64 * 1024;
    private final Link link;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object operations = new Object();

    public FastbootClient(Link link) {
        if (link == null) throw new IllegalArgumentException("link is required");
        this.link = link;
    }

    public String command(String command) throws IOException {
        byte[] bytes = commandBytes(command);
        synchronized (operations) {
            try {
                checkOpen();
                link.write(bytes, 0, bytes.length);
                return response(false).text;
            } catch (IOException error) { invalidate(error); throw error; }
        }
    }

    public void download(InputStream source, long size, Progress progress) throws IOException {
        if (source == null || size <= 0 || size > 0xffffffffL) throw new IOException("Fastboot image size must be 1..4294967295");
        synchronized (operations) {
            try {
                checkOpen();
                byte[] command = commandBytes(String.format(Locale.ROOT, "download:%08x", size));
                link.write(command, 0, command.length);
                Response begin = response(true);
                if (begin.dataSize != size) throw new IOException("Fastboot DATA size differs from requested image");
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                while (total < size) {
                    checkOpen();
                    int count = source.read(buffer, 0, (int) Math.min(buffer.length, size - total));
                    if (count <= 0) throw new IOException("Fastboot image truncated or stalled");
                    link.write(buffer, 0, count);
                    total += count;
                    if (progress != null) progress.update(total);
                }
                if (source.read() != -1) throw new IOException("Fastboot image exceeds declared size");
                response(false);
            } catch (IOException error) { invalidate(error); throw error; }
            catch (RuntimeException error) { invalidate(error); throw error; }
        }
    }

    public void flash(String partition, InputStream source, long size, Progress progress) throws IOException {
        if (partition == null || !partition.matches("[A-Za-z0-9_.-]{1,128}")) throw new IOException("Invalid fastboot partition");
        synchronized (operations) {
            download(source, size, progress);
            command("flash:" + partition);
        }
    }

    public void boot(InputStream source, long size, Progress progress) throws IOException {
        synchronized (operations) {
            download(source, size, progress);
            command("boot");
        }
    }

    private Response response(boolean expectData) throws IOException {
        byte[] bytes = new byte[MAX_RESPONSE + 1];
        StringBuilder info = new StringBuilder();
        for (int packets = 0; packets < 1024; packets++) {
            checkOpen();
            int count = link.read(bytes);
            if (count < 4 || count > MAX_RESPONSE) throw new IOException("Malformed fastboot response length");
            String status = new String(bytes, 0, 4, StandardCharsets.US_ASCII);
            String text = new String(bytes, 4, count - 4, StandardCharsets.UTF_8);
            if (status.equals("INFO") || status.equals("TEXT")) {
                if (info.length() + text.length() + 1 > MAX_INFO) throw new IOException("Fastboot diagnostic output exceeds limit");
                info.append(text);
                if (status.equals("INFO")) info.append('\n');
            } else if (status.equals("FAIL")) {
                throw new IOException("Fastboot rejected: " + info + text);
            } else if (status.equals("OKAY")) {
                if (expectData) throw new IOException("Fastboot did not enter DATA phase");
                return new Response(-1, info + text);
            } else if (status.equals("DATA")) {
                if (!expectData || count != 12 || !text.matches("[0-9a-fA-F]{8}")) throw new IOException("Unexpected or malformed fastboot DATA");
                return new Response(Long.parseLong(text, 16), info.toString());
            } else {
                throw new IOException("Unknown fastboot status");
            }
        }
        throw new IOException("Too many fastboot informational responses");
    }

    private static byte[] commandBytes(String command) throws IOException {
        if (command == null || command.isEmpty() || command.length() > 4096) throw new IOException("Invalid fastboot command length");
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c < 32 || c > 126) throw new IOException("Fastboot command must be printable ASCII");
        }
        return command.getBytes(StandardCharsets.US_ASCII);
    }
    private void checkOpen() throws IOException {
        if (closed.get()) throw new IOException("Fastboot connection closed");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Fastboot operation cancelled");
    }
    private void invalidate(Throwable error) {
        try { close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
    }
    @Override public void close() throws IOException {
        // Never take operations: close must cancel a blocked USB transfer from another thread.
        if (closed.compareAndSet(false, true)) link.close();
    }
    private static final class Response {
        final long dataSize;
        final String text;
        Response(long dataSize, String text) { this.dataSize = dataSize; this.text = text; }
    }
}
