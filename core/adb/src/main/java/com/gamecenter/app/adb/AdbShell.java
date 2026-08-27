package com.gamecenter.app.adb;

import com.gamecenter.app.adb.protocol.AdbTransport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Legacy shell with explicit exit status; the caller supplies the total task deadline. */
public final class AdbShell {
    static final int MAX_OUTPUT_BYTES = 1024 * 1024;
    private AdbShell() {}

    public static final class Result {
        public final String output;
        public final int exitCode;
        private Result(String output, int exitCode) { this.output = output; this.exitCode = exitCode; }
        public void requireSuccess() throws IOException {
            if (exitCode != 0) throw new IOException("远端命令失败 (exit=" + exitCode + "): " + output);
        }
    }

    public static Result exec(AdbTransport transport, ResourceScope scope, String command) throws IOException {
        if (transport == null || scope == null || command == null || command.isEmpty()
                || command.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid shell request");
        scope.check();
        String marker = "__GM_ADB_EXIT_" + UUID.randomUUID().toString().replace("-", "") + "__";
        // A nested shell confines exit/exec/redirection in the user command. Stdin is not interactive.
        String service = "shell:sh -c " + AdbCommands.quote(command)
                + " </dev/null; printf '\\n" + marker + ":%s\\n' \"$?\"";
        AdbTransport.Channel channel = scope.own(transport.open(service));
        try (AdbTransport.Channel owned = channel) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (true) {
                scope.check();
                int count = channel.input().read(buffer);
                if (count < 0) break;
                if (count == 0) throw new IOException("Shell stream made no progress");
                if (count > MAX_OUTPUT_BYTES - output.size()) {
                    throw new IOException("命令输出超过 1 MiB，已停止读取；执行结果未确认，请缩小查询范围");
                }
                output.write(buffer, 0, count);
            }
            scope.check();
            String text = output.toString(StandardCharsets.UTF_8.name());
            Matcher status = Pattern.compile("\\r?\\n" + marker + ":([0-9]{1,3})\\r?\\n\\z").matcher(text);
            if (!status.find()) throw new IOException("远端命令结束标记丢失，执行结果未知；不会自动重试");
            int exitCode = Integer.parseInt(status.group(1));
            if (exitCode > 255) throw new IOException("Invalid remote shell exit status");
            return new Result(text.substring(0, status.start()), exitCode);
        } finally {
            scope.release(channel);
        }
    }
}
