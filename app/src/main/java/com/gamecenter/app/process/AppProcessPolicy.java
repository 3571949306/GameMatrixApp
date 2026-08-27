package com.gamecenter.app.process;

/** Pure process policy; only the explicitly identified ADB process opts out of host work. */
public final class AppProcessPolicy {
    public enum Role { MAIN, ADB, OTHER, UNKNOWN }

    private AppProcessPolicy() {}

    public static Role classify(String packageName, String processName) {
        if (packageName == null || packageName.isEmpty()
                || processName == null || processName.isEmpty()) {
            return Role.UNKNOWN;
        }
        if (packageName.equals(processName)) return Role.MAIN;
        if ((packageName + ":adb").equals(processName)) return Role.ADB;
        return Role.OTHER;
    }

    public static boolean shouldInitializeHost(Role role) {
        // Preserve existing behavior on OEM lookup failures and for unrelated processes.
        return role != Role.ADB;
    }
}
