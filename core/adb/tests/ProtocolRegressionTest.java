package com.gamecenter.app.adb.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Deterministic protocol peers; deadlines only prevent a broken test from hanging, never assert elapsed time. */
public final class ProtocolRegressionTest {
    private static int assertions;
    private static final byte[] EMPTY = new byte[0];
    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "protocol-test"); thread.setDaemon(true); return thread;
    });

    public static void main(String[] args) throws Exception {
        try {
            handshakeTests();
            streamTests();
            streamFailureTests();
            syncTests();
            fastbootTests();
            System.out.println("ADB protocol regression PASS: " + assertions + " assertions");
        } finally { WORKERS.shutdownNow(); }
    }

    private static void handshakeTests() throws Exception {
        try (Peer peer = new Peer()) {
            peer.offer(packet("CNXN", 0x01000000, 4096, ascii("device::\0")));
            try (AdbWireConnection connection = AdbWireConnection.connect(peer, null, false)) {
                Packet sent = peer.sent();
                check(sent.is("CNXN") && !new String(sent.data, StandardCharsets.UTF_8).contains("shell_v2"), "no unsupported features advertised");
                check(connection.isOpen(), "legacy CNXN succeeds");
            }
            check(peer.closed.getCount() == 0, "connection closes owned link");
        }
        try (Peer peer = new Peer()) {
            peer.offer(packet("AUTH", 1, 0, new byte[20]));
            peer.offer(packet("AUTH", 1, 0, new byte[20]));
            peer.offer(packet("CNXN", 0x01000000, 4096, EMPTY));
            int[] called = {0, 0};
            AdbWireConnection.Auth auth = new AdbWireConnection.Auth() {
                @Override public byte[] sign(byte[] token) { called[0]++; return new byte[]{1, 2}; }
                @Override public byte[] publicKey() { called[1]++; return ascii("key host\0"); }
            };
            try (AdbWireConnection ignored = AdbWireConnection.connect(peer, auth, false)) {
                peer.sent();
                check(peer.sent().arg0 == 2 && peer.sent().arg0 == 3, "AUTH signature then public key");
                check(Arrays.equals(called, new int[]{1, 1}), "each auth response generated once");
            }
        }
        try (Peer peer = new Peer()) {
            peer.offer(packet("STLS", 0x01000000, 0, EMPTY));
            peer.offer(packet("CNXN", 0x01000001, 4096, EMPTY));
            try (AdbWireConnection connection = AdbWireConnection.connect(peer, null, true)) {
                peer.sent();
                check(peer.sent().is("STLS") && peer.tls, "TLS upgrade occurs before connection ready");
                check(connection.isOpen(), "TLS connection ready");
            }
        }
        try (Peer peer = new Peer()) {
            peer.offer(packet("CNXN", 0x01000000, 4096, EMPTY));
            expectIo(() -> AdbWireConnection.connect(peer, null, true), "plaintext downgrade refused");
            check(peer.closed.getCount() == 0, "downgrade closes link");
        }
        try (Peer peer = new Peer()) {
            peer.offer(packet("AUTH", 1, 0, new byte[20]));
            expectIo(() -> AdbWireConnection.connect(peer, null, true), "plaintext AUTH refused");
        }
        try (Peer peer = new Peer()) {
            byte[] malformed = packet("CNXN", 0x01000000, 4096, EMPTY);
            AdbWireConnection.put(malformed, 12, Integer.MAX_VALUE);
            peer.offer(malformed);
            expectIo(() -> AdbWireConnection.connect(peer, null, false), "oversized packet refused before allocation");
        }
        try (Peer peer = new Peer()) {
            byte[] malformed = packet("AUTH", 1, 0, new byte[20]);
            malformed[16] = 1;
            peer.offer(malformed);
            expectIo(() -> AdbWireConnection.connect(peer, null, false), "bad checksum rejected");
        }
        try (Peer peer = new Peer()) {
            byte[] malformed = packet("CNXN", 0x01000000, 4096, EMPTY);
            malformed[20] ^= 1;
            peer.offer(malformed);
            expectIo(() -> AdbWireConnection.connect(peer, null, false), "bad magic rejected");
        }
        try (Peer peer = new Peer()) {
            Future<?> handshake = WORKERS.submit(() -> AdbWireConnection.connect(peer, null, false));
            peer.sent();
            peer.close();
            futureIo(handshake, "link cancellation unblocks handshake");
        }
    }

    private static void streamTests() throws Exception {
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            AdbTransport.Channel first = open(peer, connection, "shell:one", 100);
            AdbTransport.Channel second = open(peer, connection, "shell:two", 101);
            peer.offer(packet("WRTE", 100, 1, ascii("first")));
            peer.offer(packet("WRTE", 101, 2, ascii("second")));
            byte[] secondData = new byte[6];
            AdbWireConnection.readFully(second.input(), secondData, 0, 6);
            check(Arrays.equals(secondData, ascii("second")), "unconsumed stream does not block another");
            Packet ack = peer.sent();
            check(ack.is("OKAY") && ack.arg0 == 2 && ack.arg1 == 101, "ACK routed to consumed stream");
            check(peer.outputPackets.isEmpty(), "unconsumed stream not acknowledged");
            check(first.input().read() == 'f', "partial read");
            check(peer.outputPackets.isEmpty(), "partial read does not expand receive window");
            byte[] remainder = new byte[4];
            AdbWireConnection.readFully(first.input(), remainder, 0, 4);
            check(peer.sent().arg0 == 1, "ACK after complete consumption");

            peer.offer(packet("WRTE", 100, 1, ascii("tail")));
            peer.offer(packet("CLSE", 100, 1, EMPTY));
            Packet close = peer.sent();
            check(close.is("CLSE"), "remote close acknowledged");
            byte[] tail = new byte[4];
            AdbWireConnection.readFully(first.input(), tail, 0, 4);
            check(Arrays.equals(tail, ascii("tail")) && first.input().read() == -1, "remote EOF preserves buffered bytes");
            second.close();
            check(peer.sent().is("CLSE"), "local close sent");
            second.close();
            check(peer.outputPackets.isEmpty(), "channel close idempotent");
        }
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4)) {
            AdbTransport.Channel stream = open(peer, connection, "x", 90);
            Future<?> writing = WORKERS.submit(() -> { stream.output().write(ascii("abcdefghij")); return null; });
            ByteArrayOutputStream actual = new ByteArrayOutputStream();
            for (int expected : new int[]{4, 4, 2}) {
                Packet write = peer.sent();
                check(write.is("WRTE") && write.data.length == expected, "write honors negotiated chunk limit");
                actual.write(write.data);
                peer.offer(packet("OKAY", 90, write.arg0, EMPTY));
            }
            writing.get(5, TimeUnit.SECONDS);
            check(Arrays.equals(actual.toByteArray(), ascii("abcdefghij")), "stream payload preserved");
            stream.close();
        }
    }

    private static void streamFailureTests() throws Exception {
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            AdbTransport.Channel stream = open(peer, connection, "shell:", 80);
            Future<?> read = WORKERS.submit(() -> stream.input().read());
            connection.close(); connection.close();
            futureIo(read, "connection close unblocks stream reader");
            check(!connection.isOpen(), "closed connection unavailable");
        }
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            AdbTransport.Channel stream = open(peer, connection, "shell:", 80);
            Future<?> write = WORKERS.submit(() -> { stream.output().write(5); return null; });
            check(peer.sent().is("WRTE"), "write awaiting ACK");
            connection.close();
            futureIo(write, "connection close unblocks ACK wait");
        }
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            Future<?> opening = WORKERS.submit(() -> connection.open("shell:"));
            peer.sent();
            connection.close();
            futureIo(opening, "connection close unblocks OPEN wait");
        }
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            AdbTransport.Channel stream = open(peer, connection, "shell:", 80);
            peer.offer(packet("WRTE", 80, 1, ascii("one")));
            peer.offer(packet("WRTE", 80, 1, ascii("two")));
            check(peer.closed.await(5, TimeUnit.SECONDS), "peer exceeding one-packet window disconnected");
            expectIo(() -> stream.input().read(), "protocol failure reaches consumers");
        }
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            open(peer, connection, "shell:", 80);
            peer.offer(packet("WRTE", 81, 1, ascii("wrong peer")));
            check(peer.closed.await(5, TimeUnit.SECONDS), "cross-stream identity mismatch disconnected");
        }
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            expectIo(() -> connection.open("shell:\0bad"), "NUL service refused");
            check(connection.isOpen(), "invalid caller input does not damage connection");
            AdbTransport.Channel stream = open(peer, connection, "shell:", 80);
            Thread.currentThread().interrupt();
            try { expectIo(() -> stream.output().write(1), "interrupted writer cancelled"); }
            finally { Thread.interrupted(); }
            check(connection.isOpen() && peer.sent().is("CLSE"), "interrupted stream does not invalidate other channels");
        }
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            Future<?> refused = WORKERS.submit(() -> connection.open("localabstract:not-ready"));
            Packet request = peer.sent();
            peer.offer(packet("CLSE", 0, request.arg0, EMPTY));
            futureIo(refused, "remote OPEN rejection surfaced");
            check(connection.isOpen(), "service refusal preserves connection");
            AdbTransport.Channel next = open(peer, connection, "shell:echo ok", 91);
            next.close();
            check(peer.sent().is("CLSE"), "subsequent OPEN works after rejected service");
        }
        try (Peer peer = new Peer(); AdbWireConnection connection = connect(peer, 4096)) {
            CountDownLatch cancelled = new CountDownLatch(1);
            Thread opener = new Thread(() -> {
                try { connection.open("shell:cancelled"); }
                catch (IOException expected) { cancelled.countDown(); }
            });
            opener.setDaemon(true); opener.start();
            Packet request = peer.sent();
            opener.interrupt();
            check(cancelled.await(5, TimeUnit.SECONDS), "pending OPEN cancellation returns");
            peer.offer(packet("OKAY", 92, request.arg0, EMPTY));
            Packet cleanup = peer.sent();
            check(cleanup.is("CLSE") && cleanup.arg1 == 92 && connection.isOpen(), "late OPEN acknowledgment cleaned without closing connection");
        }
    }

    private static void syncTests() throws Exception {
        byte[] listing = concat(dent(".", 0040755, 0), dent("notes.txt", 0100644, 0xffffffffL), syncHeader("DONE", 0), new byte[12]);
        SyncPeer peer = new SyncPeer(listing);
        List<AdbSync.Entry> entries = new AdbSync(peer).list("/sdcard");
        check(entries.size() == 1 && entries.get(0).name.equals("notes.txt") && entries.get(0).size == 0xffffffffL, "sync directory unsigned fields and dot filtering");
        check(peer.closed && peer.service.equals("sync:"), "LIST closes channel");
        for (String name : new String[]{"../secret", "a/b", "a\\b", "a\0b"}) {
            SyncPeer invalid = new SyncPeer(dent(name, 0, 0));
            expectIo(() -> new AdbSync(invalid).list("/"), "unsafe directory name rejected");
            check(invalid.closed, "invalid directory closes channel");
        }
        SyncPeer oversized = new SyncPeer(concat(ascii("DENT"), new byte[12], integer(Integer.MAX_VALUE)));
        expectIo(() -> new AdbSync(oversized).list("/"), "oversized sync name refused");
        SyncPeer relative = new SyncPeer(EMPTY);
        expectIo(() -> new AdbSync(relative).list("relative"), "relative sync path refused");
        check(relative.service == null, "invalid path does not open a channel");

        byte[] file = new byte[150_000];
        for (int i = 0; i < file.length; i++) file[i] = (byte) (i * 17);
        SyncPeer push = new SyncPeer(syncHeader("OKAY", 0));
        long[] progress = {0};
        new AdbSync(push).push("/sdcard/test.bin", new ByteArrayInputStream(file), file.length, bytes -> progress[0] = bytes);
        ByteArrayInputStream written = new ByteArrayInputStream(push.output.toByteArray());
        int send = intFrom(written), length = intFrom(written);
        check(send == AdbWireConnection.id("SEND"), "push begins SEND");
        byte[] path = new byte[length]; AdbWireConnection.readFully(written, path, 0, length);
        check(new String(path, StandardCharsets.UTF_8).equals("/sdcard/test.bin,33188"), "push specifies regular 0644 permissions");
        ByteArrayOutputStream copied = new ByteArrayOutputStream();
        while (true) {
            int id = intFrom(written), count = intFrom(written);
            if (id == AdbWireConnection.id("DONE")) break;
            check(id == AdbWireConnection.id("DATA") && count <= 65536, "bounded push chunks");
            byte[] chunk = new byte[count]; AdbWireConnection.readFully(written, chunk, 0, count); copied.write(chunk);
        }
        check(Arrays.equals(file, copied.toByteArray()) && progress[0] == file.length && push.closed, "streamed push byte equality and final progress");
        SyncPeer pull = new SyncPeer(concat(syncHeader("DATA", 65536), Arrays.copyOfRange(file, 0, 65536), syncHeader("DATA", 65536), Arrays.copyOfRange(file, 65536, 131072), syncHeader("DATA", file.length - 131072), Arrays.copyOfRange(file, 131072, file.length), syncHeader("DONE", 0)));
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        new AdbSync(pull).pull("/file", received, bytes -> progress[0] = bytes);
        check(Arrays.equals(file, received.toByteArray()) && pull.closed, "streamed pull exact content");
        SyncPeer huge = new SyncPeer(syncHeader("DATA", 65537));
        expectIo(() -> new AdbSync(huge).pull("/file", new ByteArrayOutputStream(), null), "oversized pull DATA refused");
        SyncPeer truncated = new SyncPeer(concat(syncHeader("DATA", 8), new byte[2]));
        expectIo(() -> new AdbSync(truncated).pull("/file", new ByteArrayOutputStream(), null), "truncated pull rejected");
        SyncPeer shortPush = new SyncPeer(syncHeader("OKAY", 0));
        expectIo(() -> new AdbSync(shortPush).push("/file", new ByteArrayInputStream(new byte[2]), 3, null), "short push source rejected");
        SyncPeer longPush = new SyncPeer(syncHeader("OKAY", 0));
        expectIo(() -> new AdbSync(longPush).push("/file", new ByteArrayInputStream(new byte[2]), 1, null), "long push source rejected");
        SyncPeer fail = new SyncPeer(concat(syncHeader("FAIL", 6), ascii("denied")));
        expectIo(() -> new AdbSync(fail).list("/"), "sync failure surfaced");
    }

    private static void fastbootTests() throws Exception {
        BootPeer peer = new BootPeer("INFOstarting", "TEXTdetails", "OKAY0.4");
        try (FastbootClient boot = new FastbootClient(peer)) {
            check(boot.command("getvar:version").equals("starting\ndetails0.4"), "fastboot info/text and result preserved");
        }
        byte[] image = new byte[140_000];
        BootPeer flash = new BootPeer(String.format("DATA%08x", image.length), "OKAY", "OKAYflashed");
        long[] progress = {0};
        try (FastbootClient boot = new FastbootClient(flash)) {
            boot.flash("boot_a", new ByteArrayInputStream(image), image.length, count -> progress[0] = count);
            check(new String(flash.writes.get(0), StandardCharsets.US_ASCII).equals(String.format("download:%08x", image.length)), "fastboot explicit size");
            check(new String(flash.writes.get(flash.writes.size() - 1), StandardCharsets.US_ASCII).equals("flash:boot_a"), "flash only after complete accepted download");
            check(progress[0] == image.length, "fastboot download progress");
            check(flash.writes.get(1).length == 65536, "fastboot data memory bounded");
        }
        BootPeer bootPeer = new BootPeer("DATA00000001", "OKAY", "OKAY");
        try (FastbootClient boot = new FastbootClient(bootPeer)) {
            boot.boot(new ByteArrayInputStream(new byte[1]), 1, null);
            check(Arrays.equals(bootPeer.writes.get(2), ascii("boot")), "boot follows download");
        }
        for (String response : new String[]{"DATA00000002", "DATA0000000z", "OKAY", "FAILdenied", "WHATbad"}) {
            BootPeer invalid = new BootPeer(response);
            FastbootClient boot = new FastbootClient(invalid);
            expectIo(() -> boot.flash("boot", new ByteArrayInputStream(new byte[1]), 1, null), "bad fastboot DATA response rejected");
            check(invalid.writes.size() == 1 && invalid.closed, "failed download never sends image or flash/retry");
        }
        BootPeer shortImage = new BootPeer("DATA00000002", "OKAY");
        expectIo(() -> new FastbootClient(shortImage).flash("boot", new ByteArrayInputStream(new byte[1]), 2, null), "truncated image aborts flash");
        check(shortImage.writes.size() == 2 && shortImage.closed, "no flash after truncated image");
        BootPeer longImage = new BootPeer("DATA00000001", "OKAY");
        expectIo(() -> new FastbootClient(longImage).flash("boot", new ByteArrayInputStream(new byte[2]), 1, null), "extra image bytes abort flash");
        check(longImage.writes.size() == 2 && longImage.closed, "no flash after excessive image");
        BootPeer unknown = new BootPeer("DATA00000001");
        expectIo(() -> new FastbootClient(unknown).command("getvar:x"), "unexpected command DATA refused");
        BootPeer invalidCommand = new BootPeer();
        expectIo(() -> new FastbootClient(invalidCommand).command("reboot\nboot"), "command control characters refused");
        check(invalidCommand.writes.isEmpty(), "invalid fastboot command not sent");
        BootPeer informational = new BootPeer();
        for (int i = 0; i < 400; i++) informational.responses.add(ascii("INFO" + "x".repeat(252)));
        expectIo(() -> new FastbootClient(informational).command("getvar:all"), "fastboot INFO flood bounded");
        BootPeer tooLong = new BootPeer("OKAY" + "x".repeat(253));
        expectIo(() -> new FastbootClient(tooLong).command("getvar:x"), "fastboot oversized response refused");

        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        FastbootClient.Link blocked = new FastbootClient.Link() {
            @Override public int read(byte[] b) throws IOException {
                entered.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                throw new IOException("USB closed");
            }
            @Override public void write(byte[] b, int off, int len) {}
            @Override public void close() { release.countDown(); }
        };
        FastbootClient cancellable = new FastbootClient(blocked);
        Future<?> querying = WORKERS.submit(() -> cancellable.command("getvar:x"));
        check(entered.await(5, TimeUnit.SECONDS), "fastboot read entered");
        cancellable.close();
        futureIo(querying, "fastboot close does not wait behind operation lock");
    }

    private static AdbWireConnection connect(Peer peer, int max) throws Exception {
        peer.offer(packet("CNXN", 0x01000000, max, EMPTY));
        AdbWireConnection connection = AdbWireConnection.connect(peer, null, false);
        peer.sent();
        return connection;
    }
    private static AdbTransport.Channel open(Peer peer, AdbWireConnection connection, String service, int remote) throws Exception {
        Future<AdbTransport.Channel> opening = WORKERS.submit(() -> connection.open(service));
        Packet request = peer.sent();
        check(request.is("OPEN") && request.arg1 == 0, "OPEN outbound");
        peer.offer(packet("OKAY", remote, request.arg0, EMPTY));
        return opening.get(5, TimeUnit.SECONDS);
    }
    private interface IoAction { void run() throws Exception; }
    private static void expectIo(IoAction action, String message) throws Exception {
        try { action.run(); } catch (IOException expected) { assertions++; return; }
        throw new AssertionError(message);
    }
    private static void futureIo(Future<?> future, String message) throws Exception {
        try { future.get(5, TimeUnit.SECONDS); }
        catch (ExecutionException error) { check(error.getCause() instanceof IOException, message); return; }
        throw new AssertionError(message);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); assertions++; }
    private static byte[] ascii(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static byte[] integer(int value) { byte[] b = new byte[4]; AdbWireConnection.put(b, 0, value); return b; }
    private static int intFrom(InputStream in) throws IOException { byte[] b = new byte[4]; AdbWireConnection.readFully(in, b, 0, 4); return AdbWireConnection.get(b, 0); }
    private static byte[] syncHeader(String id, int value) { return concat(ascii(id), integer(value)); }
    private static byte[] dent(String name, int mode, long size) {
        byte[] bytes = ascii(name);
        return concat(ascii("DENT"), integer(mode), integer((int) size), integer(1234), integer(bytes.length), bytes);
    }
    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] value : values) out.write(value, 0, value.length);
        return out.toByteArray();
    }
    private static byte[] packet(String id, int arg0, int arg1, byte[] data) {
        int command = AdbWireConnection.id(id), checksum = 0;
        for (byte value : data) checksum += value & 255;
        return concat(integer(command), integer(arg0), integer(arg1), integer(data.length), integer(checksum), integer(command ^ -1), data);
    }
    private static final class Packet {
        final int command, arg0, arg1;
        final byte[] data;
        Packet(byte[] bytes) {
            command = AdbWireConnection.get(bytes, 0); arg0 = AdbWireConnection.get(bytes, 4); arg1 = AdbWireConnection.get(bytes, 8);
            data = Arrays.copyOfRange(bytes, 24, bytes.length);
        }
        boolean is(String value) { return command == AdbWireConnection.id(value); }
    }
    private static final class Peer implements AdbWireConnection.Link {
        final BlockingQueue<byte[]> inputPackets = new LinkedBlockingQueue<>();
        final BlockingQueue<Packet> outputPackets = new LinkedBlockingQueue<>();
        final CountDownLatch closed = new CountDownLatch(1);
        boolean tls;
        final InputStream input = new InputStream() {
            byte[] current = EMPTY;
            int offset;
            boolean ended;
            @Override public int read() throws IOException { byte[] b = new byte[1]; return read(b, 0, 1) < 0 ? -1 : b[0] & 255; }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) return 0;
                if (ended) return -1;
                if (offset == current.length) {
                    try { current = inputPackets.take(); offset = 0; }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
                    if (current.length == 0) { ended = true; return -1; }
                }
                int count = Math.min(len, current.length - offset);
                System.arraycopy(current, offset, b, off, count); offset += count; return count;
            }
        };
        final OutputStream output = new OutputStream() {
            final ByteArrayOutputStream pending = new ByteArrayOutputStream();
            @Override public synchronized void write(int value) throws IOException { write(new byte[]{(byte) value}, 0, 1); }
            @Override public synchronized void write(byte[] b, int off, int len) throws IOException {
                if (closed.getCount() == 0) throw new IOException("closed");
                pending.write(b, off, len);
                byte[] bytes = pending.toByteArray();
                if (bytes.length >= 24 && bytes.length == 24 + AdbWireConnection.get(bytes, 12)) {
                    outputPackets.add(new Packet(bytes)); pending.reset();
                }
            }
        };
        void offer(byte[] data) { inputPackets.add(data); }
        Packet sent() throws Exception {
            Packet packet = outputPackets.poll(5, TimeUnit.SECONDS);
            if (packet == null) throw new AssertionError("Expected outbound packet");
            return packet;
        }
        @Override public InputStream input() { return input; }
        @Override public OutputStream output() { return output; }
        @Override public void upgradeTls() { tls = true; }
        @Override public void setReadTimeout(int timeout) {}
        @Override public void close() { closed.countDown(); inputPackets.offer(EMPTY); }
    }
    private static final class SyncPeer implements AdbTransport {
        final byte[] response;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean closed;
        String service;
        SyncPeer(byte[] response) { this.response = response; }
        @Override public Channel open(String service) {
            this.service = service;
            return new Channel() {
                final InputStream input = new ByteArrayInputStream(response);
                @Override public InputStream input() { return input; }
                @Override public OutputStream output() { return output; }
                @Override public void close() { closed = true; }
            };
        }
        @Override public boolean isOpen() { return true; }
        @Override public void close() { closed = true; }
    }
    private static final class BootPeer implements FastbootClient.Link {
        final BlockingQueue<byte[]> responses = new LinkedBlockingQueue<>();
        final List<byte[]> writes = new ArrayList<>();
        boolean closed;
        BootPeer(String... responses) { for (String response : responses) this.responses.add(ascii(response)); }
        @Override public int read(byte[] b) throws IOException {
            byte[] response = responses.poll();
            if (response == null) throw new IOException("No fastboot response");
            int count = Math.min(b.length, response.length); System.arraycopy(response, 0, b, 0, count); return count;
        }
        @Override public void write(byte[] b, int off, int len) throws IOException {
            if (closed) throw new IOException("closed");
            writes.add(Arrays.copyOfRange(b, off, off + len));
        }
        @Override public void close() { closed = true; }
    }
}
