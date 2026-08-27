import subprocess
import re

def sh(cmd):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return (r.stdout or "").strip()

def dump():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/pd.xml"], capture_output=True)
    return sh("adb shell cat /sdcard/pd.xml")

# 滑动 hero 直到显示保卫蛋蛋，点图标进详情
for i in range(10):
    x = dump()
    m = re.search(r'text="保卫蛋蛋" resource-id="com.gamecenter.app:id/heroTitle"', x)
    if m:
        ic = re.search(r'resource-id="com.gamecenter.app:id/heroIcon"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
        if ic:
            cx = (int(ic.group(1)) + int(ic.group(3))) // 2
            cy = (int(ic.group(2)) + int(ic.group(4))) // 2
            print(f"TD hero found, tap icon {cx},{cy}")
            subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)])
            break
    subprocess.run(["adb", "shell", "input", "swipe", "950", "470", "400", "470", "200"], capture_output=True)
else:
    print("TD hero not found in 10 slides")