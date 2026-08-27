package com.gamecenter.app.adb;

import com.gamecenter.app.adb.protocol.AdbSync;
import com.gamecenter.app.adb.protocol.AdbTransport;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One remote pm session for a base APK and all selected splits. Never invokes host pm. */
public final class AdbPackageInstaller {
    public interface Source {
        String name();
        long size();
        InputStream open() throws IOException;
    }
    public interface Progress { void update(long bytes); }
    private AdbPackageInstaller() {}

    /** Sources need known nonnegative sizes; stage SAF sources of unknown size before calling. */
    public static String install(AdbTransport transport, ResourceScope scope,
                                 List<Source> sources, Progress progress) throws IOException {
        if (transport == null || scope == null || sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("请选择 base APK 及其全部 split APK");
        }
        List<Source> inputs = new ArrayList<>(sources);
        long[] sizes = new long[inputs.size()];
        long totalSize = 0;
        for (int index = 0; index < inputs.size(); index++) {
            Source source = inputs.get(index);
            if (source == null || (sizes[index] = source.size()) < 0) {
                throw new IllegalArgumentException("APK 大小未知，请先暂存文件并确定长度");
            }
            try { totalSize = Math.addExact(totalSize, sizes[index]); }
            catch (ArithmeticException error) { throw new IllegalArgumentException("APK 总长度溢出", error); }
        }
        scope.check();
        String directory = "/data/local/tmp/gm-adb-" + UUID.randomUUID();
        int session = -1;
        boolean directoryCreated = false;
        boolean commitAttempted = false;
        boolean committed = false;
        String result = null;
        IOException failure = null;
        try {
            AdbShell.exec(transport, scope, "umask 077; " + AdbCommands.mkdir(directory)).requireSuccess();
            directoryCreated = true;
            AdbShell.Result create = AdbShell.exec(transport, scope, "pm install-create -r --user current -S " + totalSize);
            create.requireSuccess();
            Matcher match = Pattern.compile("(?m)^Success: created install session \\[([0-9]+)\\]\\r?$").matcher(create.output);
            if (!match.find()) throw new IOException("安装会话创建回复不可识别；远端可能遗留会话，请检查 pm 会话");
            try { session = Integer.parseInt(match.group(1)); }
            catch (NumberFormatException error) { throw new IOException("Invalid package install session", error); }
            AdbSync sync = new AdbSync(scopedTransport(transport, scope));
            long transferred = 0;
            for (int index = 0; index < inputs.size(); index++) {
                scope.check();
                String part = "part-" + index + ".apk";
                String remote = AdbCommands.child(directory, part);
                InputStream input = inputs.get(index).open();
                if (input == null) throw new IOException("APK source returned no stream");
                scope.own(input);
                final long previous = transferred;
                try (InputStream owned = input) {
                    sync.push(remote, input, sizes[index], bytes -> {
                        if (progress != null) progress.update(Math.addExact(previous, bytes));
                    });
                } finally { scope.release(input); }
                transferred += sizes[index];
                AdbShell.Result write = AdbShell.exec(transport, scope, "pm install-write -S " + sizes[index] + " " + session
                        + " " + AdbCommands.quote(part) + " " + AdbCommands.quote(remote));
                write.requireSuccess();
                if (!write.output.trim().startsWith("Success: streamed ")) {
                    throw new IOException("APK 写入未确认成功: " + write.output);
                }
            }
            scope.check();
            commitAttempted = true;
            AdbShell.Result commit = AdbShell.exec(transport, scope, "pm install-commit " + session);
            commit.requireSuccess();
            if (!commit.output.trim().equals("Success")) throw new IOException("安装提交回复不可识别");
            committed = true;
            result = commit.output;
        } catch (IOException | RuntimeException error) {
            failure = new IOException(commitAttempted
                    ? "安装提交未确认成功；请检查目标设备，不会自动重试提交"
                    : "安装未完成: " + error.getMessage(), error);
        } finally {
            if (session >= 0 && !committed) {
                failure = cleanup(transport, scope, "pm install-abandon " + session,
                        "远端安装会话 " + session + " 可能需要手动 abandon", failure);
            }
            if (directoryCreated) {
                IOException cleanupError = cleanup(transport, scope, AdbCommands.remove(directory, true),
                        "远端临时目录可能残留: " + directory, null);
                if (cleanupError != null) {
                    if (committed) result += "\n安装已成功，但" + cleanupError.getMessage();
                    else if (failure == null) failure = cleanupError;
                    else failure.addSuppressed(cleanupError);
                }
            }
        }
        if (failure != null) throw failure;
        return result;
    }

    private static IOException cleanup(AdbTransport transport, ResourceScope scope, String command,
                                       String warning, IOException failure) {
        try {
            // Keep caller cancellation/deadline effective: do not spawn unbounded cleanup work.
            scope.check();
            AdbShell.exec(transport, scope, command).requireSuccess();
        } catch (IOException | RuntimeException error) {
            IOException cleanupError = new IOException(warning, error);
            if (failure == null) return cleanupError;
            failure.addSuppressed(cleanupError);
        }
        return failure;
    }

    /** Sync owns the wrapper, while cancellation owns the underlying channel. */
    private static AdbTransport scopedTransport(AdbTransport transport, ResourceScope scope) {
        return new AdbTransport() {
            @Override public Channel open(String service) throws IOException {
                scope.check();
                Channel channel = scope.own(transport.open(service));
                return new Channel() {
                    @Override public InputStream input() { return channel.input(); }
                    @Override public OutputStream output() { return channel.output(); }
                    @Override public void close() throws IOException {
                        try { channel.close(); } finally { scope.release(channel); }
                    }
                };
            }
            @Override public boolean isOpen() { return transport.isOpen() && !scope.isClosed(); }
            @Override public void close() { /* Borrowed transport: only per-operation channels are owned. */ }
        };
    }
}
