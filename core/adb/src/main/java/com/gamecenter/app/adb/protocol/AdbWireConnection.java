package com.gamecenter.app.adb.protocol;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ADB wire protocol with one reader and independent, flow-controlled streams.
 * Only legacy stream flow control is advertised (no shell_v2 or delayed_ack).
 * Link.close MUST interrupt blocked reads/writes/TLS upgrades without taking their locks.
 * Based on AOSP packages/modules/adb/protocol.txt; no third-party APK code is used.
 */
public final class AdbWireConnection implements AdbTransport {
    public interface Link extends Closeable {
        InputStream input() throws IOException;
        OutputStream output() throws IOException;
        void upgradeTls() throws IOException;
        void setReadTimeout(int ms) throws IOException;
    }

    public interface Auth {
        byte[] sign(byte[] token) throws IOException;
        /** UTF-8 ADB public-key line, including its trailing NUL. */
        byte[] publicKey() throws IOException;
    }

    private static final int CNXN = id("CNXN"), AUTH = id("AUTH"), STLS = id("STLS");
    private static final int OPEN = id("OPEN"), WRTE = id("WRTE"), OKAY = id("OKAY"), CLSE = id("CLSE");
    // Negotiate the checksum version, including AUTH before the server's CNXN.
    // Advertising 1.0.1 then requiring a checksum on its AUTH would reject modern adbd.
    private static final int VERSION = 0x01000000;
    private static final int SKIP_CHECKSUM_VERSION = 0x01000001;
    private static final int MAX_PAYLOAD = 64 * 1024;
    private static final long TIMEOUT_MS = 15_000;
    private static final byte[] EMPTY = new byte[0];
    private final Link link;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Map<Integer, WireChannel> channels = new ConcurrentHashMap<>();
    private final ReentrantLock writer = new ReentrantLock();
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1, task -> {
        Thread thread = new Thread(task, "adb-deadline");
        thread.setDaemon(true);
        return thread;
    });
    private volatile IOException failure;
    private volatile boolean ready;
    private boolean skipChecksum;
    private int remoteMax = 4096;

    private AdbWireConnection(Link link) {
        if (link == null) throw new IllegalArgumentException("link is required");
        this.link = link;
        deadlines.setRemoveOnCancelPolicy(true);
    }

    /** The supplied Link is already connected. This method owns it even on failure. */
    public static AdbWireConnection connect(Link link, Auth auth, boolean requireTls) throws IOException {
        AdbWireConnection connection = new AdbWireConnection(link);
        ScheduledFuture<?> timeout = connection.deadlines.schedule(
                () -> connection.fail(new SocketTimeoutException("ADB handshake timed out")),
                TIMEOUT_MS, TimeUnit.MILLISECONDS);
        try {
            link.setReadTimeout((int) TIMEOUT_MS);
            connection.handshake(auth, requireTls);
            connection.checkOpen();
            link.setReadTimeout(0);
            connection.ready = true;
            Thread reader = new Thread(connection::readLoop, "adb-reader");
            reader.setDaemon(true);
            reader.start();
            return connection;
        } catch (IOException | RuntimeException error) {
            connection.fail(error instanceof IOException ? (IOException) error : new IOException(error));
            if (error instanceof IOException) throw connection.failure;
            throw error;
        } finally {
            timeout.cancel(false);
        }
    }

    private void handshake(Auth auth, boolean requireTls) throws IOException {
        send(CNXN, VERSION, MAX_PAYLOAD, "host::\0".getBytes(StandardCharsets.UTF_8));
        boolean tls = false;
        int authRound = 0;
        for (int round = 0; round < 8; round++) {
            Packet packet = readPacket();
            if (packet.command == STLS) {
                if (tls || packet.arg0 != 0x01000000 || packet.data.length != 0) {
                    throw new IOException("Invalid ADB TLS negotiation");
                }
                send(STLS, 0x01000000, 0, EMPTY);
                link.upgradeTls();
                tls = true;
            } else if (packet.command == AUTH) {
                if (requireTls && !tls) throw new IOException("Refusing plaintext ADB authentication");
                if (auth == null || packet.arg0 != 1 || packet.data.length != 20 || authRound >= 2) {
                    throw new IOException("ADB authentication rejected or malformed");
                }
                byte[] response = authRound++ == 0 ? auth.sign(packet.data) : auth.publicKey();
                if (response == null || response.length == 0 || response.length > 4096) {
                    throw new IOException("Invalid ADB authentication response");
                }
                send(AUTH, authRound == 1 ? 2 : 3, 0, response);
            } else if (packet.command == CNXN) {
                if (requireTls && !tls) throw new IOException("Refusing plaintext ADB downgrade");
                if (packet.arg0 < 0x01000000 || packet.arg1 <= 0) throw new IOException("Invalid ADB peer version/limit");
                remoteMax = Math.min(MAX_PAYLOAD, packet.arg1);
                skipChecksum = packet.arg0 >= SKIP_CHECKSUM_VERSION;
                return;
            } else {
                throw new IOException("Unexpected ADB handshake packet");
            }
        }
        throw new IOException("Too many ADB handshake messages");
    }

    @Override public boolean isOpen() { return ready && !closed.get(); }

    @Override public Channel open(String service) throws IOException {
        if (service == null || service.isEmpty() || service.indexOf('\0') >= 0) {
            throw new IOException("Invalid ADB service");
        }
        byte[] destination = (service + '\0').getBytes(StandardCharsets.UTF_8);
        if (destination.length > remoteMax) throw new IOException("ADB service name too long");
        checkOpen();
        if (!ready) throw new IOException("ADB handshake incomplete");
        int localId = nextId.getAndIncrement();
        if (localId <= 0) throw new IOException("ADB stream identifiers exhausted; reconnect");
        WireChannel channel = new WireChannel(localId);
        synchronized (channels) {
            if (channels.size() >= 256) throw new IOException("ADB active/pending channel safety limit reached");
            channels.put(localId, channel);
        }
        try {
            checkOpen();
            send(OPEN, localId, 0, destination);
            long end = deadline();
            synchronized (channel) {
                while (channel.remoteId == 0 && !channel.ended) channel.await(end);
                channel.checkActive();
            }
            return channel;
        } catch (IOException error) {
            channel.cancelStream(error);
            // A rejected service (e.g. scrcpy not listening yet) is not a failed transport.
            // Keep an unacknowledged cancelled OPEN only until its late OKAY/CLSE arrives;
            // dispatch then closes the remote stream. The channel limit bounds such tombstones.
            if (closed.get()) channels.remove(localId, channel);
            throw error;
        }
    }

    private void readLoop() {
        try {
            while (!closed.get()) dispatch(readPacket());
        } catch (IOException | RuntimeException error) {
            fail(error instanceof IOException ? (IOException) error : new IOException(error));
        }
    }

    private void dispatch(Packet packet) throws IOException {
        if (packet.command == OPEN) {
            send(CLSE, 0, packet.arg0, EMPTY); // This client exposes no services to the peer.
            return;
        }
        if (packet.command != WRTE && packet.command != OKAY && packet.command != CLSE) {
            throw new IOException("Unexpected ADB stream command");
        }
        if (packet.arg1 <= 0 || (packet.arg0 <= 0 && packet.command != CLSE)) {
            throw new IOException("Invalid ADB stream identifier");
        }
        if (packet.command != WRTE && packet.data.length != 0) throw new IOException("Malformed ADB control message");
        WireChannel channel = channels.get(packet.arg1);
        if (channel == null) return; // Late ACK/CLSE after local close.
        boolean replyClose = false;
        boolean emptyWrite = false;
        synchronized (channel) {
            if (channel.ended) {
                if (packet.command == OKAY) {
                    channels.remove(channel.localId, channel);
                    replyClose = true;
                } else if (packet.command == CLSE) {
                    channels.remove(channel.localId, channel);
                }
            } else {
            if (channel.remoteId != 0 && channel.remoteId != packet.arg0) throw new IOException("ADB stream identity mismatch");
            if (packet.command == OKAY) {
                if (channel.remoteId == 0) channel.remoteId = packet.arg0;
                else if (!channel.waitingAck) throw new IOException("Unsolicited ADB acknowledgment");
                channel.waitingAck = false;
            } else if (packet.command == WRTE) {
                if (channel.remoteId == 0 || channel.pending != null) throw new IOException("ADB receive window exceeded");
                if (packet.data.length == 0) emptyWrite = true;
                else {
                    channel.pending = packet.data;
                    channel.offset = 0;
                }
            } else {
                channel.ended = true;
                replyClose = channel.remoteId != 0;
                channels.remove(channel.localId, channel);
            }
            channel.notifyAll();
            }
        }
        if (replyClose) send(CLSE, channel.localId, packet.arg0, EMPTY);
        if (emptyWrite) send(OKAY, channel.localId, packet.arg0, EMPTY);
    }

    private void checkOpen() throws IOException {
        if (closed.get()) throw failure != null ? failure : new IOException("ADB connection closed");
    }

    private void send(int command, int arg0, int arg1, byte[] data) throws IOException {
        boolean locked = false;
        ScheduledFuture<?> timeout = null;
        try {
            checkOpen();
            locked = writer.tryLock(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!locked) throw new SocketTimeoutException("ADB writer busy");
            checkOpen();
            timeout = deadlines.schedule(() -> fail(new SocketTimeoutException("ADB write timed out")), TIMEOUT_MS, TimeUnit.MILLISECONDS);
            byte[] header = new byte[24];
            put(header, 0, command); put(header, 4, arg0); put(header, 8, arg1);
            put(header, 12, data.length); put(header, 16, checksum(data)); put(header, 20, command ^ -1);
            OutputStream out = link.output();
            out.write(header);
            out.write(data);
            out.flush();
            checkOpen();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("ADB write cancelled");
            fail(interrupted);
            throw interrupted;
        } catch (IOException error) {
            fail(error);
            throw failure != null ? failure : error;
        } catch (java.util.concurrent.RejectedExecutionException error) {
            throw new IOException("ADB connection closed", error);
        } finally {
            if (timeout != null) timeout.cancel(false);
            if (locked) writer.unlock();
        }
    }

    private Packet readPacket() throws IOException {
        byte[] header = new byte[24];
        readFully(link.input(), header, 0, header.length);
        int command = get(header, 0), arg0 = get(header, 4), length = get(header, 12);
        if (get(header, 20) != (command ^ -1) || length < 0 || length > MAX_PAYLOAD) {
            throw new IOException("Invalid ADB packet header/length");
        }
        byte[] data = new byte[length];
        readFully(link.input(), data, 0, length);
        if (!skipChecksum && !(command == CNXN && arg0 >= SKIP_CHECKSUM_VERSION) && get(header, 16) != checksum(data)) {
            throw new IOException("Invalid ADB payload checksum");
        }
        return new Packet(command, arg0, get(header, 8), data);
    }

    @Override public void close() { fail(new IOException("ADB connection closed")); }

    private void fail(IOException error) {
        if (!closed.compareAndSet(false, true)) return;
        failure = error;
        ready = false;
        for (WireChannel channel : channels.values()) channel.abort(error);
        channels.clear();
        deadlines.shutdownNow();
        try { link.close(); } catch (IOException ignored) { /* Original failure is retained. */ }
    }

    private final class WireChannel implements Channel {
        final int localId;
        int remoteId;
        boolean ended;
        boolean waitingAck;
        IOException error;
        byte[] pending;
        int offset;
        final ReentrantLock outputLock = new ReentrantLock();
        final InputStream input = new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) < 0 ? -1 : one[0] & 255;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                bounds(b, off, len);
                if (len == 0) return 0;
                boolean ack;
                int count;
                synchronized (WireChannel.this) {
                    while (pending == null && !ended) await(0);
                    if (error != null) throw error;
                    if (pending == null) return -1;
                    count = Math.min(len, pending.length - offset);
                    System.arraycopy(pending, offset, b, off, count);
                    offset += count;
                    ack = offset == pending.length;
                    if (ack) pending = null;
                    ack = ack && !ended;
                }
                // Delay ACK until consumption: each stream retains at most one 64KiB packet.
                if (ack) send(OKAY, localId, remoteId, EMPTY);
                return count;
            }
            @Override public void close() throws IOException { WireChannel.this.close(); }
        };
        final OutputStream output = new OutputStream() {
            @Override public void write(int b) throws IOException { write(new byte[]{(byte) b}); }
            @Override public void write(byte[] b, int off, int len) throws IOException {
                bounds(b, off, len);
                boolean locked = false;
                try {
                    locked = outputLock.tryLock(TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (!locked) throw new SocketTimeoutException("ADB stream writer busy");
                    while (len > 0) {
                        int count = Math.min(remoteMax, len);
                        synchronized (WireChannel.this) {
                            checkActive();
                            waitingAck = true;
                        }
                        send(WRTE, localId, remoteId, Arrays.copyOfRange(b, off, off + count));
                        long end = deadline();
                        synchronized (WireChannel.this) {
                            while (waitingAck && !ended) await(end);
                            checkActive();
                        }
                        off += count;
                        len -= count;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException error = new InterruptedIOException("ADB stream write cancelled");
                    cancelStream(error);
                    throw error;
                } catch (IOException error) {
                    cancelStream(error);
                    throw error;
                } finally {
                    if (locked) outputLock.unlock();
                }
            }
            @Override public void close() throws IOException { WireChannel.this.close(); }
        };
        WireChannel(int localId) { this.localId = localId; }
        @Override public InputStream input() { return input; }
        @Override public OutputStream output() { return output; }
        synchronized void checkActive() throws IOException {
            if (error != null) throw error;
            checkOpen();
            if (ended) throw new IOException("ADB stream closed/rejected");
        }
        synchronized void abort(IOException reason) {
            error = reason; ended = true; pending = null; notifyAll();
        }
        void cancelStream(IOException reason) {
            int peer;
            synchronized (this) {
                if (ended) return;
                abort(reason);
                peer = remoteId;
            }
            if (peer == 0) return; // dispatch closes it if its pending OPEN is acknowledged later.
            channels.remove(localId, this);
            if (closed.get()) return;
            // Cleanup is bounded and must still run for an interrupted worker.
            boolean interrupted = Thread.interrupted();
            try { send(CLSE, localId, peer, EMPTY); }
            catch (IOException cleanup) { reason.addSuppressed(cleanup); }
            finally { if (interrupted) Thread.currentThread().interrupt(); }
        }
        void await(long end) throws IOException {
            try {
                if (end == 0) wait();
                else {
                    long remaining = end - System.nanoTime();
                    if (remaining <= 0) throw new SocketTimeoutException("ADB stream operation timed out");
                    TimeUnit.NANOSECONDS.timedWait(this, remaining);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("ADB stream operation cancelled");
            }
        }
        @Override public void close() throws IOException {
            int peer;
            synchronized (this) {
                if (ended) return;
                ended = true; pending = null; peer = remoteId; notifyAll();
            }
            channels.remove(localId, this);
            if (!closed.get() && peer != 0) send(CLSE, localId, peer, EMPTY);
        }
    }

    static int id(String value) {
        byte[] b = value.getBytes(StandardCharsets.US_ASCII);
        return get(b, 0);
    }
    static int get(byte[] b, int o) {
        return (b[o] & 255) | ((b[o + 1] & 255) << 8) | ((b[o + 2] & 255) << 16) | ((b[o + 3] & 255) << 24);
    }
    static void put(byte[] b, int o, int v) {
        for (int i = 0; i < 4; i++) b[o + i] = (byte) (v >>> (8 * i));
    }
    static void readFully(InputStream in, byte[] b, int off, int length) throws IOException {
        while (length > 0) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("ADB read cancelled");
            int n = in.read(b, off, length);
            if (n < 0) throw new EOFException("Truncated ADB message");
            if (n == 0) throw new IOException("ADB input made no progress");
            off += n; length -= n;
        }
    }
    private static void bounds(byte[] b, int off, int len) {
        if (off < 0 || len < 0 || off > b.length - len) throw new IndexOutOfBoundsException();
    }
    private static int checksum(byte[] b) {
        int sum = 0; for (byte value : b) sum += value & 255; return sum;
    }
    private static long deadline() { return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS); }
    private static final class Packet {
        final int command, arg0, arg1;
        final byte[] data;
        Packet(int command, int arg0, int arg1, byte[] data) {
            this.command = command; this.arg0 = arg0; this.arg1 = arg1; this.data = data;
        }
    }
}
