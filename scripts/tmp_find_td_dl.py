import subprocess
import re

def sh(cmd):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return (r.stdout or "").strip()

def dump():
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/pk.xml"], capture_output=True)
    return sh("adb shell cat /sdcard/pk.xml")

# 快速滚到底再上来，找 td 卡（未安装+下载按钮形式）
for i in range(14):
    subprocess.run(["adb", "shell", "input", "swipe", "540", "1500", "540", "500", "300"], capture_output=True)
    x = dump()
    if "保卫蛋蛋" in x:
        m = re.search(r'text="下载"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
        if m:
            cx = (int(m.group(1)) + int(m.group(3))) // 2
            cy = (int(m.group(2)) + int(m.group(4))) // 2
            print(f"TD CARD at scroll {i}, 下载 button at {cx},{cy}")
            subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)])
            break
        else:
            # td 可见但无下载按钮，可能此屏是 hero 区
            print(f"scroll {i}: td visible, no 下载 btn")
    else:
        print(f"scroll {i}: no td")
print("done")