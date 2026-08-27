package com.gamecenter.app.process;

/** Standalone harness, run by scripts/verify_adb_process.py without host Gradle tasks. */
public final class AppProcessPolicyRegression {
    public static void main(String[] args) {
        check("com.gamecenter.app", "com.gamecenter.app", AppProcessPolicy.Role.MAIN, true);
        check("com.gamecenter.app", "com.gamecenter.app:adb", AppProcessPolicy.Role.ADB, false);
        check("com.gamecenter.app.debug", "com.gamecenter.app.debug:adb", AppProcessPolicy.Role.ADB, false);
        check("com.gamecenter.app", "com.gamecenter.app:adb_extra", AppProcessPolicy.Role.OTHER, true);
        check("com.gamecenter.app", "another.package:adb", AppProcessPolicy.Role.OTHER, true);
        check("com.gamecenter.app", "com.gamecenter.app:worker", AppProcessPolicy.Role.OTHER, true);
        check("com.gamecenter.app", null, AppProcessPolicy.Role.UNKNOWN, true);
        check("com.gamecenter.app", "", AppProcessPolicy.Role.UNKNOWN, true);
        check(null, "com.gamecenter.app:adb", AppProcessPolicy.Role.UNKNOWN, true);
        check("", "com.gamecenter.app:adb", AppProcessPolicy.Role.UNKNOWN, true);
        System.out.println("ADB PROCESS POLICY: PASS (10 role/startup cases)");
    }

    private static void check(String packageName, String processName,
                              AppProcessPolicy.Role expectedRole, boolean expectedHost) {
        AppProcessPolicy.Role actual = AppProcessPolicy.classify(packageName, processName);
        if (actual != expectedRole || AppProcessPolicy.shouldInitializeHost(actual) != expectedHost) {
            throw new AssertionError("Unexpected startup policy: " + packageName + "/" + processName);
        }
    }
}
