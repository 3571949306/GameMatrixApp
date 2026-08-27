#!/usr/bin/env python3
"""Verify ADB process startup isolation without host Gradle or release-asset writes."""

from pathlib import Path
import re
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/gamecenter/app/process"
TEST = ROOT / "app/src/test/java/com/gamecenter/app/process/AppProcessPolicyRegression.java"


def verify_workbench_theme() -> None:
    """An AppCompatActivity with a platform Material parent crashes before onCreate completes."""
    manifest = (ROOT / "core/adb/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    styles = (ROOT / "core/adb/src/main/res/values/strings.xml").read_text(encoding="utf-8")
    if 'android:theme="@style/AdbWorkbenchTheme"' not in manifest:
        raise AssertionError("ADB workbench must declare its isolated theme")
    match = re.search(r'<style name="AdbWorkbenchTheme" parent="([^"]+)"', styles)
    if match is None or not match.group(1).startswith("Theme.AppCompat"):
        raise AssertionError("AppCompatActivity requires an AppCompat-descended AdbWorkbenchTheme")


def verify_workbench_lifetime() -> None:
    activity = (ROOT / "core/adb/src/main/java/com/gamecenter/app/adb/AdbWorkbenchActivity.java").read_text(encoding="utf-8")
    service = (ROOT / "core/adb/src/main/java/com/gamecenter/app/adb/AdbSessionService.java").read_text(encoding="utf-8")
    if "private final AdbSessionService.Listener engineListener = this::onEngineChanged;" not in activity:
        raise AssertionError("Workbench needs a stable service listener so it can be removed")
    on_stop = activity.split("protected void onStop()", 1)[1].split("protected void onDestroy()", 1)[0]
    if on_stop.index("service.remove(engineListener)") > on_stop.index("unbindService(binding)"):
        raise AssertionError("Workbench must remove its listener before unbinding")
    if "svc.observe(() ->" in activity:
        raise AssertionError("An anonymous service observer would retain a destroyed Activity")
    if "if (!foregroundUnavailable)" not in service:
        raise AssertionError("Foreground-service failure must be reported once, not rescheduled forever")


def verify_lazy_section_teardown() -> None:
    """All sections are torn down even when their view was never inflated."""
    screen = (ROOT / "core/adb/src/main/java/com/gamecenter/app/adb/ui/ScreenControlSection.java").read_text(encoding="utf-8")
    stop = screen.split("private void stopScrcpy()", 1)[1].split("private void takeScreenshot()", 1)[0]
    for view in ("toggleBtn", "surfaceView", "placeholder"):
        if f"if ({view} != null)" not in stop:
            raise AssertionError(f"Screen section teardown must tolerate an uncreated {view}")
    if "public void onDestroy()" not in screen or "stopScrcpy();" not in screen.split("public void onDestroy()", 1)[1]:
        raise AssertionError("Screen section destruction must always stop its session")


def verify_startup_gate(text: str) -> None:
    on_create = text.split("override fun onCreate() {", 1)[1]
    gate = re.search(
        r"if \(!hostInitializationEnabled\)\s*\{\s*"
        r"(?://[^\n]*\n\s*)*super\.onCreate\(\)\s*"
        r'Log\.i\("App", "ADB process initialized without host services"\)\s*return\s*}',
        on_create,
    )
    if gate is None:
        raise AssertionError("ADB startup must call super and return before host initialization")
    for call in (
        "CrashHandler.init(", "CrashDetector.markAppStart(", "applyLanguage()", "applyTheme()",
        "applicationScope.launch(", "FlutterStoreEngineManager.getOrCreate(",
        "DownloadMetricsCollector.init(", "registerActivityLifecycleCallbacks(",
        "ModulePreDownloadManager.init(",
    ):
        if on_create.index(call) <= gate.end():
            raise AssertionError(f"Host work precedes ADB return: {call}")
    if "private val applicationScope by lazy" not in text:
        raise AssertionError("Host coroutine scope must be lazy")
    attach = text.split("override fun attachBaseContext(base: Context) {", 1)[1].split("}", 1)[0]
    if attach.strip() != "super.attachBaseContext(base)":
        raise AssertionError("Review new attachBaseContext work for ADB isolation")
    terminate = text.split("override fun onTerminate() {", 1)[1]
    if terminate.index("if (!hostInitializationEnabled) return") > terminate.index("DownloadMetricsCollector.flush()"):
        raise AssertionError("ADB termination must not initialize host metrics")


def main() -> None:
    verify_workbench_theme()
    verify_workbench_lifetime()
    verify_lazy_section_teardown()
    text = (ROOT / "app/src/main/kotlin/com/gamecenter/app/App.kt").read_text(encoding="utf-8")
    verify_startup_gate(text)
    # Prove that the structural guard rejects the pre-gate ordering regression.
    unsafe = text.replace("override fun onCreate() {", "override fun onCreate() {\n        CrashDetector.markAppStart(this)", 1)
    try:
        verify_startup_gate(unsafe)
    except AssertionError:
        pass
    else:
        raise AssertionError("Startup guard failed to detect host initialization before ADB gate")
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise RuntimeError("JDK javac/java are required")
    output_root = ROOT / "build/agent-verification"
    output_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="adb-process-", dir=output_root) as output:
        subprocess.run([javac, "-encoding", "UTF-8", "-d", output,
                        str(SOURCE / "AppProcessPolicy.java"), str(TEST)], check=True, cwd=ROOT)
        subprocess.run([java, "-cp", output, "com.gamecenter.app.process.AppProcessPolicyRegression"],
                       check=True, cwd=ROOT)
    print("ADB PROCESS STARTUP GATE: PASS (including ordering regression)")


if __name__ == "__main__":
    main()
