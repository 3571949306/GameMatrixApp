package com.gamecenter.app.adb;

import com.gamecenter.app.adb.protocol.AdbTransport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic protocol fakes: no device, host shell, Android framework or wall-clock assertions. */
public final class OperationsRegressionTest {
    private static int cases;
    public static void main(String[] args) throws Exception {
        shellSuccessAndExitFailure();
        shellRejectsUnconfirmedOutput();
        scopeCancellationClosesBlockedShell();
        commandsQuoteAndValidate();
        splitInstallationTransaction();
        installationFailuresCleanUp();
        installationCancellationDoesNotRestartWork();
        installerValidatesLengths();
        System.out.println("ADB OPERATIONS: PASS (" + cases + " scenarios)");
    }

    private static void shellSuccessAndExitFailure() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.shellOutput = "hello\nworld\n";
        ResourceScope scope = new ResourceScope();
        AdbShell.Result result = AdbShell.exec(transport, scope, "printf 'hello\\nworld\\n'");
        eq("hello\nworld\n", result.output);
        eq(0, result.exitCode);
        result.requireSuccess();
        transport.exitCode = 17;
        AdbShell.Result failed = AdbShell.exec(transport, scope, "exit 17");
        eq(17, failed.exitCode);
        ioFailure(failed::requireSuccess);
        eq("exit 17", transport.commands.get(1));
        check(!transport.markers.get(0).equals(transport.markers.get(1)), "markers must be per invocation");
        transport.assertReleased();
        scope.close();
        cases++;
    }

    private static void shellRejectsUnconfirmedOutput() throws Exception {
        FakeTransport missing = new FakeTransport(); missing.omitMarker = true;
        ioFailure(() -> AdbShell.exec(missing, new ResourceScope(), "true"));
        missing.assertReleased();
        FakeTransport oversized = new FakeTransport();
        oversized.shellOutput = new String(new char[AdbShell.MAX_OUTPUT_BYTES + 1]).replace('\0', 'x');
        ioFailure(() -> AdbShell.exec(oversized, new ResourceScope(), "true"));
        oversized.assertReleased();
        FakeTransport trailing = new FakeTransport(); trailing.afterMarker = "unexpected";
        ioFailure(() -> AdbShell.exec(trailing, new ResourceScope(), "true"));
        trailing.assertReleased();
        FakeTransport badExit = new FakeTransport(); badExit.exitCode = 999;
        ioFailure(() -> AdbShell.exec(badExit, new ResourceScope(), "true"));
        badExit.assertReleased();
        cases += 4;
    }

    private static void scopeCancellationClosesBlockedShell() throws Exception {
        ResourceScope scope = new ResourceScope();
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> result = new AtomicReference<>();
        final class BlockingInput extends InputStream {
            boolean closed;
            @Override public synchronized int read() throws IOException {
                reading.countDown();
                while (!closed) {
                    try { wait(); } catch (InterruptedException error) { throw new IOException(error); }
                }
                throw new IOException("closed");
            }
            @Override public synchronized void close() { closed = true; notifyAll(); }
        }
        BlockingInput input = new BlockingInput();
        AdbTransport transport = new AdbTransport() {
            @Override public Channel open(String service) {
                return new Channel() {
                    public InputStream input() { return input; }
                    public ByteArrayOutputStream output() { return new ByteArrayOutputStream(); }
                    public void close() { input.close(); }
                };
            }
            public boolean isOpen() { return true; }
            public void close() { throw new AssertionError("borrowed transport must remain open"); }
        };
        Thread task = new Thread(() -> {
            try { AdbShell.exec(transport, scope, "cat"); }
            catch (Throwable error) { result.set(error); }
            finally { finished.countDown(); }
        }, "adb-operations-cancel-test");
        task.setDaemon(true);
        task.start();
        check(reading.await(5, TimeUnit.SECONDS), "shell did not reach blocking read");
        scope.close(); scope.close();
        check(finished.await(5, TimeUnit.SECONDS), "closing scope did not wake shell read");
        check(result.get() instanceof IOException && input.closed, "cancel must fail and close channel");
        cases++;
    }

    private static void commandsQuoteAndValidate() throws Exception {
        eq("'a'\"'\"'b'", AdbCommands.quote("a'b"));
        eq("''", AdbCommands.quote(""));
        String tricky = "a'; $(reboot) `id`\n-f.apk";
        String remote = AdbCommands.child("/sdcard/Download", tricky);
        eq("/sdcard/Download/" + tricky, remote);
        eq("rm -f -- '/sdcard/Download/a'\"'\"'; $(reboot) `id`\n-f.apk'", AdbCommands.remove(remote, false));
        eq("/sdcard/Download/a", AdbCommands.path("//sdcard/./Download//a/"));
        invalid(() -> AdbCommands.quote("a\0b"));
        invalid(() -> AdbCommands.path("relative"));
        invalid(() -> AdbCommands.path("/sdcard/../data"));
        invalid(() -> AdbCommands.child("/sdcard", "../data"));
        invalid(() -> AdbCommands.child("/sdcard", ".."));
        invalid(() -> AdbCommands.remove("//./", true));
        invalid(() -> AdbCommands.remove("/data", true));
        invalid(() -> AdbCommands.move("/storage/emulated/0", "/sdcard/bak"));
        invalid(() -> AdbCommands.packageName("com.app;reboot"));
        invalid(() -> AdbCommands.packageName("-r"));
        eq("com.valid_app.beta2", AdbCommands.packageName("com.valid_app.beta2"));
        eq("android", AdbCommands.packageName("android"));
        eq("wm size 1080x1920", AdbCommands.resolution(1080, 1920));
        eq("wm density 420", AdbCommands.density(420));
        invalid(() -> AdbCommands.resolution(0, 1080));
        invalid(() -> AdbCommands.density(Integer.MAX_VALUE));
        invalid(() -> AdbCommands.animationScale(Double.NaN));
        invalid(() -> AdbCommands.animationScale(Double.POSITIVE_INFINITY));
        invalid(() -> AdbCommands.animationScale(-1));
        eq("0.5", AdbCommands.animationScale(0.5));
        eq("pm clear --user current 'com.test'", AdbCommands.appAction("clear", "com.test"));
        invalid(() -> AdbCommands.appAction("clear; reboot", "com.test"));
        cases++;
    }

    private static void splitInstallationTransaction() throws Exception {
        FakeTransport transport = new FakeTransport();
        FakeSource base = new FakeSource("evil';reboot.apk", new byte[] {1, 2, 3});
        FakeSource split = new FakeSource("../../split.apk", new byte[] {4, 5});
        List<Long> progress = new ArrayList<>();
        String result = AdbPackageInstaller.install(transport, new ResourceScope(), Arrays.asList(base, split), progress::add);
        eq("Success\n", result);
        eq(Arrays.asList("mkdir", "create", "sync", "write", "sync", "write", "commit", "remove"), transport.events);
        eq(Arrays.asList(3L, 5L), progress);
        check(base.closed && split.closed, "installer must close all source streams");
        eq(2, transport.uploads.size());
        check(Arrays.equals(base.data, transport.uploads.get(0)), "base upload bytes differ");
        check(Arrays.equals(split.data, transport.uploads.get(1)), "split upload bytes differ");
        check(transport.remotePaths.get(0).endsWith("/part-0.apk,33188"), "opaque base filename missing");
        check(transport.remotePaths.get(1).endsWith("/part-1.apk,33188"), "opaque split filename missing");
        String commands = String.join("\n", transport.commands);
        check(commands.contains("install-create -r --user current -S 5"), "total install size incorrect");
        check(commands.contains("install-write -S 3 42 'part-0.apk'"), "base write did not use same session");
        check(commands.contains("install-write -S 2 42 'part-1.apk'"), "split write did not use same session");
        check(!commands.contains("reboot") && !commands.contains("../../"), "source names escaped into shell");
        transport.assertReleased();
        cases++;
    }

    private static void installationFailuresCleanUp() throws Exception {
        FakeTransport writeFailure = new FakeTransport(); writeFailure.failWrite = 2;
        FakeSource first = new FakeSource("base.apk", new byte[] {1});
        FakeSource second = new FakeSource("split.apk", new byte[] {2});
        ioFailure(() -> AdbPackageInstaller.install(writeFailure, new ResourceScope(), Arrays.asList(first, second), null));
        eq(Arrays.asList("mkdir", "create", "sync", "write", "sync", "write", "abandon", "remove"), writeFailure.events);
        check(first.closed && second.closed, "failure leaked source stream");
        writeFailure.assertReleased();

        FakeTransport textFailure = new FakeTransport(); textFailure.failWrite = 1; textFailure.zeroExitWriteFailure = true;
        ioFailure(() -> AdbPackageInstaller.install(textFailure, new ResourceScope(),
                Arrays.asList(new FakeSource("base.apk", new byte[] {1})), null));
        eq(Arrays.asList("mkdir", "create", "sync", "write", "abandon", "remove"), textFailure.events);
        textFailure.assertReleased();

        FakeTransport uploadFailure = new FakeTransport(); uploadFailure.failSync = true;
        FakeSource source = new FakeSource("base.apk", new byte[] {1});
        ioFailure(() -> AdbPackageInstaller.install(uploadFailure, new ResourceScope(), Arrays.asList(source), null));
        eq(Arrays.asList("mkdir", "create", "sync", "abandon", "remove"), uploadFailure.events);
        check(source.closed, "failed sync leaked source");
        uploadFailure.assertReleased();

        FakeTransport lostCommit = new FakeTransport(); lostCommit.omitCommitMarker = true;
        IOException unknown = ioFailure(() -> AdbPackageInstaller.install(lostCommit, new ResourceScope(),
                Arrays.asList(new FakeSource("base.apk", new byte[] {1})), null));
        eq(1L, lostCommit.events.stream().filter("commit"::equals).count());
        check(unknown.getMessage().contains("不会自动重试"), "lost commit must be unknown, not retried");
        lostCommit.assertReleased();

        FakeTransport cleanupFailure = new FakeTransport(); cleanupFailure.failRemove = true;
        String successWithWarning = AdbPackageInstaller.install(cleanupFailure, new ResourceScope(),
                Arrays.asList(new FakeSource("base.apk", new byte[] {1})), null);
        check(successWithWarning.contains("安装已成功") && successWithWarning.contains("/data/local/tmp/gm-adb-"),
                "cleanup failure must not misreport confirmed install as failed");
        cleanupFailure.assertReleased();
        cases += 5;
    }

    private static void installationCancellationDoesNotRestartWork() throws Exception {
        ResourceScope scope = new ResourceScope();
        FakeTransport transport = new FakeTransport();
        FakeSource source = new FakeSource("base.apk", new byte[] {1, 2});
        IOException failure = ioFailure(() -> AdbPackageInstaller.install(transport, scope, Arrays.asList(source), bytes -> scope.close()));
        eq(Arrays.asList("mkdir", "create", "sync"), transport.events);
        check(failure.getSuppressed().length == 2, "cancel must retain session and temporary directory cleanup warnings");
        check(source.closed, "cancelled upload leaked source");
        transport.assertReleased();
        cases++;
    }

    private static void installerValidatesLengths() throws Exception {
        FakeTransport transport = new FakeTransport();
        FakeSource unknown = new FakeSource("unknown.apk", new byte[] {1}); unknown.declared = -1;
        invalid(() -> AdbPackageInstaller.install(transport, new ResourceScope(), Arrays.asList(unknown), null));
        FakeSource huge = new FakeSource("huge.apk", new byte[0]); huge.declared = Long.MAX_VALUE;
        invalid(() -> AdbPackageInstaller.install(transport, new ResourceScope(), Arrays.asList(huge, huge), null));
        eq(0, transport.opened);
        FakeSource shortSource = new FakeSource("short.apk", new byte[] {1}); shortSource.declared = 2;
        ioFailure(() -> AdbPackageInstaller.install(transport, new ResourceScope(), Arrays.asList(shortSource), null));
        check(shortSource.closed, "length mismatch leaked input");
        eq(Arrays.asList("mkdir", "create", "sync", "abandon", "remove"), transport.events);
        transport.assertReleased();
        cases++;
    }

    private interface Action { void run() throws Exception; }
    private static IOException ioFailure(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { return expected; }
        throw new AssertionError("Expected IOException");
    }
    private static void invalid(Action action) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid input rejection");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void eq(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static final class FakeSource implements AdbPackageInstaller.Source {
        final String name;
        final byte[] data;
        long declared;
        boolean closed;
        FakeSource(String name, byte[] data) { this.name = name; this.data = data; this.declared = data.length; }
        public String name() { return name; }
        public long size() { return declared; }
        public InputStream open() {
            return new ByteArrayInputStream(data) {
                @Override public void close() throws IOException { closed = true; super.close(); }
            };
        }
    }

    private static final class FakeTransport implements AdbTransport {
        final List<String> commands = new ArrayList<>(), markers = new ArrayList<>(), events = new ArrayList<>(), remotePaths = new ArrayList<>();
        final List<byte[]> uploads = new ArrayList<>();
        int opened, closed, exitCode, writes, failWrite;
        boolean omitMarker, omitCommitMarker, failSync, failRemove, zeroExitWriteFailure;
        String shellOutput = "", afterMarker = "";
        public Channel open(String service) throws IOException {
            opened++;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean sync = service.equals("sync:");
            byte[] response;
            if (sync) {
                events.add("sync");
                response = failSync ? new byte[] {'F','A','I','L',3,0,0,0,'b','a','d'}
                        : new byte[] {'O','K','A','Y',0,0,0,0};
            } else {
                int end = service.indexOf(" </dev/null; printf ");
                check(service.startsWith("shell:sh -c ") && end > 0, "shell wrapper missing");
                String word = service.substring("shell:sh -c ".length(), end);
                String command = word.substring(1, word.length() - 1).replace("'\"'\"'", "'");
                commands.add(command);
                Matcher marker = Pattern.compile("__GM_ADB_EXIT_[0-9a-f]{32}__").matcher(service);
                check(marker.find(), "random marker missing");
                markers.add(marker.group());
                int code = exitCode;
                String output = shellOutput;
                if (command.startsWith("umask 077; mkdir")) { events.add("mkdir"); output = ""; }
                if (command.startsWith("pm install-create")) { events.add("create"); output = "Success: created install session [42]\n"; }
                if (command.startsWith("pm install-write")) {
                    events.add("write");
                    if (++writes == failWrite) { code = zeroExitWriteFailure ? 0 : 1; output = "Failure [invalid split]\n"; }
                    else output = "Success: streamed bytes\n";
                }
                if (command.startsWith("pm install-commit")) { events.add("commit"); output = "Success\n"; }
                if (command.startsWith("pm install-abandon")) { events.add("abandon"); output = "Success\n"; }
                if (command.startsWith("rm -rf --")) { events.add("remove"); output = ""; if (failRemove) code = 1; }
                response = (output + (omitMarker || (omitCommitMarker && command.startsWith("pm install-commit"))
                        ? "" : "\n" + marker.group() + ":" + code + "\n") + afterMarker).getBytes(StandardCharsets.UTF_8);
            }
            InputStream in = new ByteArrayInputStream(response);
            return new Channel() {
                boolean released;
                public InputStream input() { return in; }
                public ByteArrayOutputStream output() { return out; }
                public void close() throws IOException {
                    if (released) return;
                    released = true; closed++;
                    in.close();
                    if (sync) captureUpload(out.toByteArray());
                }
            };
        }
        private void captureUpload(byte[] data) {
            ByteArrayOutputStream uploaded = new ByteArrayOutputStream();
            for (int offset = 0; offset + 8 <= data.length;) {
                String tag = new String(data, offset, 4, StandardCharsets.US_ASCII);
                int length = (data[offset+4] & 255) | ((data[offset+5] & 255) << 8)
                        | ((data[offset+6] & 255) << 16) | ((data[offset+7] & 255) << 24);
                offset += 8;
                if (tag.equals("DONE")) break;
                check(length >= 0 && offset + length <= data.length, "malformed sync upload");
                if (tag.equals("SEND")) remotePaths.add(new String(data, offset, length, StandardCharsets.UTF_8));
                else if (tag.equals("DATA")) uploaded.write(data, offset, length);
                else throw new AssertionError("Unexpected sync tag: " + tag);
                offset += length;
            }
            uploads.add(uploaded.toByteArray());
        }
        public boolean isOpen() { return true; }
        public void close() { throw new AssertionError("Installer must not close borrowed connection"); }
        void assertReleased() { eq(opened, closed); }
    }
}
