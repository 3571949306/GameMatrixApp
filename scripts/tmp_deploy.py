import subprocess

APK = r"module-store\feature\games\games\td\build\outputs\apk\debug\td-debug.apk"

def sh(cmd):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    if r.stdout.strip():
        print(r.stdout.strip())
    return r

# 1. force-stop
sh("adb shell am force-stop com.gamecenter.app")
# 2. push
sh("adb push %s /data/local/tmp/td.apk" % APK)
# 3. 放入 current + legacy（文件名与 catalog 完全一致）
sh("adb shell run-as com.gamecenter.app mkdir -p files/modules/current")
sh("adb shell \"run-as com.gamecenter.app cp /data/local/tmp/td.apk files/modules/current/game_td_v100.apk\"")
sh("adb shell \"run-as com.gamecenter.app cp /data/local/tmp/td.apk files/modules/game_td_v100.apk\"")
sh("adb shell run-as com.gamecenter.app ls -la files/modules/current")
sh("adb shell rm /data/local/tmp/td.apk")
# 4. 启动 App
sh("adb shell monkey -p com.gamecenter.app -c android.intent.category.LAUNCHER 1")
print("launched")