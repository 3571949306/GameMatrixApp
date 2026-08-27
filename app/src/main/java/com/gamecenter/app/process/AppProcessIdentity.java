package com.gamecenter.app.process;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Process lookup available before Application.onCreate, including API 24-27. */
public final class AppProcessIdentity {
    private AppProcessIdentity() {}

    public static String currentName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String name = Application.getProcessName();
            if (name != null && !name.isEmpty()) return name;
        }

        // /proc/self avoids depending on ActivityManager's visibility of other processes.
        // Bound the read and always close the descriptor, including failed lookups.
        try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] bytes = new byte[256];
            int length = 0;
            int value;
            while (length < bytes.length && (value = input.read()) > 0) {
                bytes[length++] = (byte) value;
            }
            if (length > 0 && length < bytes.length) {
                return new String(bytes, 0, length, StandardCharsets.UTF_8);
            }
        } catch (IOException | SecurityException ignored) {
            // Some OEMs restrict procfs. Fall through to the public API.
        }

        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes = manager == null
                    ? null : manager.getRunningAppProcesses();
            if (processes != null) {
                int pid = Process.myPid();
                for (ActivityManager.RunningAppProcessInfo process : processes) {
                    if (process.pid == pid && process.processName != null
                            && !process.processName.isEmpty()) return process.processName;
                }
            }
        } catch (SecurityException ignored) {
            // The caller logs UNKNOWN and retains the pre-existing startup behavior.
        }
        return null;
    }
}
