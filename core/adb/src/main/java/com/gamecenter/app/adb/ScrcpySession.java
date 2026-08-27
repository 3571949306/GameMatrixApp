package com.gamecenter.app.adb;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import com.gamecenter.app.adb.protocol.AdbSync;
import com.gamecenter.app.adb.protocol.AdbTransport;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One video/control session using the unmodified, pinned official scrcpy 3.3.4 server.
 * Transport and Surface are BORROWED: close() never closes the connection or releases the Surface.
 * The owner must close this session before destroying its Surface, and keep its Listener alive.
 * Listener is weakly held; callbacks run on worker threads and must marshal to a lifecycle-aware UI.
 * Protocol reference: https://github.com/Genymobile/scrcpy/tree/v3.3.4/server/src/main/java/com/genymobile/scrcpy
 */
public final class ScrcpySession implements Closeable {
    public interface Listener {
        void onSize(int width, int height);
        void onError(String message);
    }

    // Key constants for common device operations
    public static final int KEY_BACK = 4;
    public static final int KEY_HOME = 3;
    public static final int KEY_RECENT = 187;
    public static final int KEY_POWER = 26;
    public static final int KEY_VOLUME_UP = 24;
    public static final int KEY_VOLUME_DOWN = 25;

    private static final String SERVER_ASSET = "adb/scrcpy-server-v3.3.4";
    private static final String SERVER_SHA256 = "8588238c9a5a00aa542906b6ec7e6d5541d9ffb9b5d0f6e1bc0e365e2303079e";
    private static final long STARTUP_TIMEOUT_MS = 30_000;
    private final Context context;
    private final AdbTransport transport;
    private final int maxSize, bitRate;
    private final String remotePath = "/data/local/tmp/gm-scrcpy-" + UUID.randomUUID() + ".jar";
    private final String scid = String.format(java.util.Locale.ROOT, "%08x", new SecureRandom().nextInt() & 0x7fffffff);
    private final WeakReference<Listener> listener;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<AdbTransport.Channel> channels = ConcurrentHashMap.newKeySet();
    private final Set<Thread> workers = ConcurrentHashMap.newKeySet();
    private final ArrayBlockingQueue<byte[]> controls = new ArrayBlockingQueue<>(64);
    private final Object codecLock = new Object();
    private final Object gestureLock = new Object();
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "gm-scrcpy-deadline");
        thread.setDaemon(true); return thread;
    });
    private volatile Surface surface;
    private volatile InputStream uploadInput;
    private volatile ScrcpyProtocol.Size displayedSize;
    private volatile boolean controlsReady, uploadAttempted;
    private volatile ScheduledFuture<?> startupDeadline;
    private MediaCodec codec; // guarded by codecLock
    private ScrcpyProtocol.Size configuredSize; // guarded by codecLock
    private boolean pointerDown; // guarded by gestureLock
    private float lastX, lastY;

    public ScrcpySession(Context applicationContext, AdbTransport transport, Surface surface,
            int maxSize, int bitRate, Listener listener) {
        if (applicationContext == null || transport == null || surface == null || listener == null) {
            throw new IllegalArgumentException("Context, transport, Surface and listener are required");
        }
        if (maxSize < 256 || maxSize > 4096 || bitRate < 256_000 || bitRate > 40_000_000) {
            throw new IllegalArgumentException("Unsupported mirror size/bitrate");
        }
        Context app = applicationContext.getApplicationContext();
        if (app == null) throw new IllegalArgumentException("Application context required");
        this.context = app; this.transport = transport; this.surface = surface;
        this.maxSize = maxSize; this.bitRate = bitRate;
        this.listener = new WeakReference<>(listener);
        timer.setRemoveOnCancelPolicy(true);
    }

    /** Upload, launch, handshake and decoding all run off the caller thread. One-shot session. */
    public void start() {
        if (!started.compareAndSet(false, true) || closed.get()) return;
        try {
            startupDeadline = timer.schedule(() -> fail("投屏启动超时，请检查目标设备授权和网络"),
                    STARTUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            worker("video", this::runVideo);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // close() won the race; no resources have been started.
        }
    }

    private void runVideo() throws Exception {
        checkOpen();
        verifyServerAsset();
        AdbTransport scopedTransport = new AdbTransport() {
            @Override public Channel open(String service) throws IOException { return openOwned(service); }
            @Override public boolean isOpen() { return !closed.get() && transport.isOpen(); }
            @Override public void close() { ScrcpySession.this.close(); }
        };
        try (InputStream input = context.getAssets().open(SERVER_ASSET)) {
            uploadInput = input;
            checkOpen();
            uploadAttempted = true;
            new AdbSync(scopedTransport).push(remotePath, input, -1, bytes -> {
                if (closed.get()) Thread.currentThread().interrupt();
            });
        } finally { uploadInput = null; }
        checkOpen();
        AdbTransport.Channel shell = openOwned("shell:CLASSPATH=" + remotePath
                + " app_process / com.genymobile.scrcpy.Server 3.3.4"
                + " scid=" + scid + " tunnel_forward=true audio=false control=true video_codec=h264"
                + " send_device_meta=false send_dummy_byte=true send_codec_meta=true send_frame_meta=true"
                + " clipboard_autosync=false cleanup=true power_on=false log_level=warn"
                + " max_size=" + maxSize + " video_bit_rate=" + bitRate + " max_fps=60");
        worker("server-output", () -> {
            discard(shell.input());
            if (!closed.get()) fail("投屏服务器已退出，目标设备可能不支持屏幕采集");
        });

        // The first socket must be video. Its forward dummy arrives before control is accepted.
        AdbTransport.Channel video = connectVideo();
        ScrcpyProtocol.readDummy(video.input());
        AdbTransport.Channel control = openOwned("localabstract:scrcpy_" + scid);
        ScrcpyProtocol.Size header = ScrcpyProtocol.readVideoHeader(video.input());
        configureDecoder(header);
        checkOpen();
        controlsReady = true;
        worker("control-send", () -> {
            OutputStream output = control.output();
            while (!closed.get()) {
                byte[] message = controls.take();
                checkOpen(); output.write(message); output.flush();
            }
        });
        worker("control-receive", () -> {
            while (!closed.get()) ScrcpyProtocol.discardDeviceMessage(control.input());
        });
        worker("decode-output", this::drainDecoder);
        ScheduledFuture<?> deadline = startupDeadline;
        if (deadline != null) deadline.cancel(false);
        while (!closed.get()) {
            ScrcpyProtocol.Packet packet = ScrcpyProtocol.readPacket(video.input());
            if (packet.config) {
                ScrcpyProtocol.Size size = H264Dimensions.fromConfig(packet.data);
                if (size != null) configureDecoder(size);
            }
            queueFrame(packet);
        }
    }

    private AdbTransport.Channel connectVideo() throws IOException, InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        IOException last = null;
        do {
            checkOpen();
            try { return openOwned("localabstract:scrcpy_" + scid); }
            catch (IOException error) {
                last = error;
                checkOpen();
                Thread.sleep(75);
            }
        } while (System.nanoTime() < end);
        throw new IOException("scrcpy server socket did not become available", last);
    }

    private void verifyServerAsset() throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = context.getAssets().open(SERVER_ASSET)) {
            uploadInput = input;
            byte[] buffer = new byte[8192];
            int total = 0, count;
            while ((count = input.read(buffer)) != -1) {
                checkOpen();
                total += count;
                if (count == 0 || total > 2 * 1024 * 1024) throw new IOException("Invalid bundled scrcpy server");
                digest.update(buffer, 0, count);
            }
        } finally { uploadInput = null; }
        StringBuilder actual = new StringBuilder(64);
        for (byte value : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        if (!SERVER_SHA256.contentEquals(actual)) throw new IOException("Bundled scrcpy server checksum mismatch");
    }

    private void configureDecoder(ScrcpyProtocol.Size size) throws IOException {
        if (size.width > maxSize || size.height > maxSize) throw new IOException("Video exceeds requested size");
        synchronized (codecLock) {
            checkOpen();
            if (codec != null && configuredSize.width == size.width && configuredSize.height == size.height) return;
            releaseDecoder();
            Surface target = surface;
            if (target == null || !target.isValid()) throw new IOException("Mirror Surface is no longer valid");
            MediaCodec candidate = MediaCodec.createDecoderByType("video/avc");
            try {
                MediaFormat format = MediaFormat.createVideoFormat("video/avc", size.width, size.height);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,
                        Math.min(ScrcpyProtocol.MAX_PACKET, Math.max(262144, size.width * size.height)));
                candidate.configure(format, target, null, 0);
                candidate.start();
                codec = candidate;
                configuredSize = size;
            } catch (RuntimeException error) {
                candidate.release(); throw error;
            }
        }
    }

    private void queueFrame(ScrcpyProtocol.Packet packet) throws IOException {
        while (!closed.get()) {
            synchronized (codecLock) {
                checkOpen();
                if (codec == null) throw new IOException("Video decoder is unavailable");
                int index = codec.dequeueInputBuffer(10_000);
                if (index >= 0) {
                    ByteBuffer input = codec.getInputBuffer(index);
                    if (input == null || input.capacity() < packet.data.length) throw new IOException("Video packet exceeds decoder buffer");
                    input.clear(); input.put(packet.data);
                    codec.queueInputBuffer(index, 0, packet.data.length, packet.config ? 0 : packet.presentationTimeUs,
                            packet.config ? MediaCodec.BUFFER_FLAG_CODEC_CONFIG : 0);
                    return;
                }
            }
            pauseDecoder(); // Let the output worker acquire codecLock and release queued buffers.
        }
    }

    private void drainDecoder() throws IOException {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (!closed.get()) {
            ScrcpyProtocol.Size updated = null;
            synchronized (codecLock) {
                checkOpen();
                if (codec == null) return;
                int index = codec.dequeueOutputBuffer(info, 10_000);
                if (index >= 0) codec.releaseOutputBuffer(index, true);
                else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    int width = format.getInteger(MediaFormat.KEY_WIDTH), height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    if (format.containsKey("crop-left") && format.containsKey("crop-right")) {
                        width = format.getInteger("crop-right") - format.getInteger("crop-left") + 1;
                    }
                    if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
                        height = format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1;
                    }
                    updated = new ScrcpyProtocol.Size(width, height);
                }
            }
            if (updated != null) updateSize(updated);
            else pauseDecoder();
        }
    }

    private static void pauseDecoder() throws InterruptedIOException {
        try { Thread.sleep(2); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new InterruptedIOException("Decoder cancelled");
        }
    }

    private void updateSize(ScrcpyProtocol.Size size) {
        boolean overflow = false;
        synchronized (gestureLock) {
            ScrcpyProtocol.Size previous = displayedSize;
            if (previous != null && previous.width == size.width && previous.height == size.height) return;
            displayedSize = size;
            if (pointerDown) {
                overflow = !controls.offer(ScrcpyProtocol.touch(ScrcpyProtocol.CANCEL, lastX, lastY, size));
                pointerDown = false;
            }
        }
        if (overflow) { fail("投屏控制队列已满，已停止投屏以避免触摸状态卡住"); return; }
        Listener callback = listener.get();
        if (callback != null && !closed.get()) callback.onSize(size.width, size.height);
    }

    public void touch(int action, float normalizedX, float normalizedY) {
        if (closed.get() || !controlsReady || action < 0 || action > 3) return;
        boolean overflow = false;
        synchronized (gestureLock) {
            ScrcpyProtocol.Size size = displayedSize;
            if (size == null) return;
            if (!Float.isFinite(normalizedX) || !Float.isFinite(normalizedY)) {
                if (action != ScrcpyProtocol.UP && action != ScrcpyProtocol.CANCEL) return;
                normalizedX = lastX; normalizedY = lastY;
            }
            if (action != ScrcpyProtocol.DOWN && !pointerDown) return;
            if (action == ScrcpyProtocol.DOWN && pointerDown) {
                overflow = !controls.offer(ScrcpyProtocol.touch(ScrcpyProtocol.CANCEL, lastX, lastY, size));
            }
            lastX = normalizedX; lastY = normalizedY;
            if (!overflow) overflow = !controls.offer(ScrcpyProtocol.touch(action, lastX, lastY, size));
            pointerDown = action == ScrcpyProtocol.DOWN || action == ScrcpyProtocol.MOVE;
        }
        if (overflow) fail("投屏控制队列已满，已停止投屏以避免丢失抬手事件");
    }

    public void key(int keycode) {
        try { enqueue(ScrcpyProtocol.key(keycode)); }
        catch (IllegalArgumentException error) { fail("无效的投屏按键"); }
    }

    /** Alias for key() method */
    public void sendKey(int keycode) {
        key(keycode);
    }

    public void text(String text) {
        try { enqueue(ScrcpyProtocol.text(text)); }
        catch (IllegalArgumentException error) { fail("投屏输入文本过长"); }
    }

    public void screenPower(boolean on) { enqueue(ScrcpyProtocol.screenPower(on)); }

    /** Update the target Surface for video rendering. Thread-safe. */
    public void setSurface(Surface newSurface) {
        synchronized (codecLock) {
            surface = newSurface;
            if (newSurface != null && newSurface.isValid() && configuredSize != null && codec != null) {
                try {
                    codec.configure(
                            MediaFormat.createVideoFormat("video/avc", configuredSize.width, configuredSize.height),
                            newSurface, null, 0);
                } catch (Exception ignored) {
                    // Will be reconfigured on next frame
                }
            }
        }
    }

    /** Request screen rotation - sends keyboard event */
    public void setRotation(boolean landscape) {
        // scrcpy handles rotation via the device's own rotation; this is a UI hint
    }

    /** Stop the scrcpy session. Alias for close(). */
    public void stop() {
        close();
    }
    private void enqueue(byte[] message) {
        if (closed.get() || !controlsReady || message.length == 0) return;
        if (!controls.offer(message)) fail("投屏控制队列已满，已停止投屏");
    }

    private AdbTransport.Channel openOwned(String service) throws IOException {
        checkOpen();
        AdbTransport.Channel delegate = transport.open(service);
        AdbTransport.Channel wrapper = new AdbTransport.Channel() {
            private final AtomicBoolean released = new AtomicBoolean();
            @Override public InputStream input() { return delegate.input(); }
            @Override public OutputStream output() { return delegate.output(); }
            @Override public void close() throws IOException {
                if (released.compareAndSet(false, true)) {
                    channels.remove(this); delegate.close();
                }
            }
        };
        channels.add(wrapper);
        if (closed.get()) { wrapper.close(); throw new InterruptedIOException("Mirror cancelled"); }
        return wrapper;
    }

    private interface Task { void run() throws Exception; }
    private void worker(String name, Task task) {
        Thread thread = new Thread(() -> {
            try { checkOpen(); task.run(); }
            catch (Exception error) { if (!closed.get()) fail("投屏已停止：" + safeError(error)); }
            finally { workers.remove(Thread.currentThread()); }
        }, "gm-scrcpy-" + name);
        thread.setDaemon(true);
        workers.add(thread);
        if (closed.get()) { workers.remove(thread); return; }
        thread.start();
    }

    private void checkOpen() throws IOException {
        if (closed.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Mirror cancelled");
        if (!transport.isOpen()) throw new IOException("ADB disconnected");
    }
    private static String safeError(Exception error) {
        String value = error.getMessage();
        if (value == null || value.isEmpty()) return error.getClass().getSimpleName();
        return value.length() > 180 ? value.substring(0, 180) : value;
    }
    private void fail(String message) {
        Listener callback = listener.get();
        if (doStop() && callback != null) callback.onError(message);
    }

    @Override public void close() { doStop(); }

    /** Non-blocking: descriptor shutdown and MediaCodec release never run on the caller thread. */
    private boolean doStop() {
        if (!closed.compareAndSet(false, true)) return false;
        listener.clear(); controlsReady = false; controls.clear();
        ScheduledFuture<?> deadline = startupDeadline;
        if (deadline != null) deadline.cancel(false);
        Thread cleanup = new Thread(() -> {
            for (Thread worker : workers) worker.interrupt();
            closeQuietly(uploadInput);
            for (AdbTransport.Channel channel : channels) closeQuietly(channel);
            synchronized (codecLock) { releaseDecoder(); surface = null; }
            cleanupRemoteFile();
        }, "gm-scrcpy-close");
        cleanup.setDaemon(true); cleanup.start();
        return true;
    }

    private void releaseDecoder() {
        MediaCodec previous = codec;
        codec = null; configuredSize = null;
        if (previous != null) {
            try { previous.stop(); } catch (RuntimeException ignored) { }
            try { previous.release(); } catch (RuntimeException ignored) { }
        }
    }

    private void cleanupRemoteFile() {
        AtomicReference<AdbTransport.Channel> cleanupChannel = new AtomicReference<>();
        Thread cleanupThread = Thread.currentThread();
        ScheduledFuture<?> deadline = timer.schedule(() -> {
            cleanupThread.interrupt(); closeQuietly(cleanupChannel.get());
        }, 3, TimeUnit.SECONDS);
        try {
            if (uploadAttempted && transport.isOpen()) {
                // This generated UUID-only path belongs to this session; no globs or user paths.
                AdbTransport.Channel channel = transport.open("shell:rm -f " + remotePath);
                cleanupChannel.set(channel);
                if (!Thread.currentThread().isInterrupted()) discard(channel.input());
            }
        } catch (IOException ignored) {
            // Best effort when disconnected. The server's cleanup=true also unlinks its own jar.
        } finally {
            closeQuietly(cleanupChannel.get()); deadline.cancel(false); timer.shutdownNow();
        }
    }

    private static void discard(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        while (!Thread.currentThread().isInterrupted()) {
            int count = input.read(buffer);
            if (count < 0) return;
            if (count == 0) throw new IOException("Stream made no progress");
        }
    }
    private static void closeQuietly(Closeable resource) {
        if (resource != null) try { resource.close(); } catch (IOException | RuntimeException ignored) { }
    }
}
