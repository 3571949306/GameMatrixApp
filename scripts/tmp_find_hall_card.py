import subprocess
import re

def sh(cmd):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return (r.stdout or "").strip()

# 从大厅顶部开始，小幅滚动，全局唯一识别大厅 td 卡："塔防：守住蛋蛋！"（大厅游戏描述）
# 商店描述是"塔防：固定路径建塔…"，可用于排除
for i in range(12):
    sh("adb shell input swipe 540 1600 540 1100 250")
    subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/zc.xml"], capture_output=True)
    x = sh("adb shell cat /sdcard/zc.xml")
    if "塔防：守住蛋蛋！" in x:
        # 找到大厅 td 卡所在 gameId 标签
        gm = re.search(r'text="tower_defense"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
        if gm:
            cx = (int(gm.group(1)) + int(gm.group(3))) // 2
            cy = (int(gm.group(2)) + int(gm.group(4))) // 2
            print(f"HALL CARD at scroll {i}: gameId tower_defense at {cx},{cy}")
            subprocess.run(["adb", "shell", "input", "tap", str(cx), str(cy)])
            break
        else:
            print(f"scroll {i}: 大厅td卡无 gameId 标签?")
    elif "保卫蛋蛋" in x:
        print(f"scroll {i}: 有保卫蛋蛋（需要判定）")
    else:
        print(f"scroll {i}: 无")
print("done")