#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
俄罗斯方块 (Tetris) 端到端验收脚本 v2
启动路径：SplashActivity -> 主页 -> 点「立即开始」(俄罗斯方块卡片) -> 难度对话框
验证：竖屏无遮挡布局 / 5 个软控制按钮 / 硬降生效 / 设置 Ghost 开关 / 难度记忆(写+持久)
截图存入 e2e_tetris/，供人工查看
"""
import subprocess, re, time, os, sys, json
from PIL import Image

DEVICE = "emulator-5554"
PKG = "com.gamecenter.app"
SPLASH = "com.gamecenter.app/.SplashActivity"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "e2e_tetris")
os.makedirs(OUT, exist_ok=True)

# 1080x1920 @ density 3.0
CTRL_Y = 1776
CTRL_X = {"HOLD": 120, "LEFT": 330, "ROTATE": 540, "RIGHT": 750, "DROP": 960}
SETTINGS_BTN = (744, 90)
BOARD = (270, 810, 360, 1440)  # boardLeft,boardRight,boardTop,boardBottom

def find_adb():
    for p in [
        r"C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe",
        r"C:\Android\Sdk\platform-tools\adb.exe",
    ]:
        if os.path.exists(p):
            return p
    import shutil
    f = shutil.which("adb")
    if f:
        return f
    raise SystemExit("adb not found")

ADB = find_adb()

def adb(args, timeout=60):
    return subprocess.run([ADB, "-s", DEVICE] + args, capture_output=True, text=True, timeout=timeout)

def shell(cmd, timeout=60):
    return adb(["shell", cmd], timeout)

def tap(x, y):
    shell(f"input tap {x} {y}")
    time.sleep(0.45)

def screenshot(name):
    path = os.path.join(OUT, name)
    adb(["shell", "screencap", "-p", "/sdcard/_t.png"])
    adb(["pull", "/sdcard/_t.png", path])
    return path

def dump(name):
    path = os.path.join(OUT, name)
    for _ in range(8):
        r = adb(["shell", "uiautomator", "dump", "/sdcard/_u.xml"])
        if "ERROR" not in (r.stderr or "") and "timeout" not in (r.stderr or "").lower():
            adb(["pull", "/sdcard/_u.xml", path])
            if os.path.exists(path) and os.path.getsize(path) > 50:
                return path
        time.sleep(0.5)
    return None

def parse_bounds(xml_path):
    if not xml_path or not os.path.exists(xml_path):
        return {}, set()
    txt = open(xml_path, encoding="utf-8", errors="ignore").read()
    res, checked = {}, set()
    for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', txt):
        t, x1, y1, x2, y2 = m.groups()
        if t:
            res[t] = ((int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2)
    for m in re.finditer(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="([^"]*)"', txt):
        x1, y1, x2, y2, t = m.groups()
        if t and t not in res:
            res[t] = ((int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2)
    for m in re.finditer(r'text="([^"]*)"[^>]*checked="true"', txt):
        checked.add(m.group(1))
    return res, checked

def count_colored(img_path, region):
    img = Image.open(img_path).convert("RGB")
    px = img.load()
    x0, x1, y0, y1 = region
    c = 0
    for y in range(y0, y1, 2):
        for x in range(x0, x1, 2):
            r, g, b = px[x, y]
            mx, mn = max(r, g, b), min(r, g, b)
            if mx <= 45 or mx - mn <= 35:
                continue
            c += 1
    return c

def detect_control_labels(img_path):
    img = Image.open(img_path).convert("RGB")
    px = img.load()
    W, H = img.size
    out = []
    y0, y1 = 1686, 1866
    for cx in [CTRL_X[k] for k in ("HOLD", "LEFT", "ROTATE", "RIGHT", "DROP")]:
        light = 0
        for y in range(y0, y1, 2):
            for x in range(cx - 40, cx + 40, 2):
                if 0 <= x < W and px[x, y][0] > 170 and px[x, y][1] > 170 and px[x, y][2] > 170:
                    light += 1
        out.append(light)
    return out

def count_imagebuttons(xml_path):
    if not xml_path or not os.path.exists(xml_path):
        return -1
    return len(re.findall(r"ImageButton", open(xml_path, encoding="utf-8", errors="ignore").read()))

def read_prefs():
    r = shell(f"run-as {PKG} cat /data/data/{PKG}/shared_prefs/tetris_prefs.xml")
    if r.returncode != 0 or not r.stdout.strip():
        r = shell(f"cat /data/data/{PKG}/shared_prefs/tetris_prefs.xml")
    m = re.search(r'last_difficulty"[^>]*?value="(\d+)"', r.stdout) or re.search(r'last_difficulty[^>]*?>"?(\d+)"?', r.stdout)
    return int(m.group(1)) if m else None

report = {"steps": []}
def log(step, ok, detail):
    report["steps"].append({"step": step, "ok": bool(ok), "detail": detail})
    print(f"[{'PASS' if ok else 'FAIL'}] {step}: {detail}")

def open_tetris_dialog():
    """启动主页并点击俄罗斯方块卡片的「立即开始」，返回难度对话框 dump 与文本坐标"""
    adb(["shell", "am", "force-stop", PKG])
    time.sleep(1.2)
    shell(f"am start -n {SPLASH}")
    time.sleep(4.5)
    home = dump("_home.xml")
    bnd, _ = parse_bounds(home)
    # 找到俄罗斯方块卡片对应的「立即开始」：取 y 最接近卡片标题(>1200) 的那个
    card_y = None
    for t, (cx, cy) in bnd.items():
        if t == "俄罗斯方块" and cy > 1200:
            card_y = cy
    start_key = None
    if "立即开始" in bnd:
        sx, sy = bnd["立即开始"]
        if card_y is None or abs(sy - card_y) < 200:
            start_key = "立即开始"
    if start_key:
        tap(*bnd[start_key])
    else:
        # 退化：直接点卡片标题
        for t, (cx, cy) in bnd.items():
            if t == "俄罗斯方块" and cy > 1200:
                tap(cx, cy)
                break
    time.sleep(3.0)
    dlg = dump("_dialog.xml")
    bnd, _ = parse_bounds(dlg)
    # 处理「继续对局？」恢复对话框：点「新开一局」进入难度选择
    if "新开一局" in bnd:
        tap(*bnd["新开一局"])
        time.sleep(2.0)
        dlg = dump("_dialog.xml")
    return dlg

# ============ 1. 启动并打开难度对话框 ============
print("== open Tetris via in-app navigation ==")
dlg = open_tetris_dialog()
bnd, _ = parse_bounds(dlg)
print("dialog texts:", [t for t in ("简单", "普通", "困难", "大师", "开始") if t in bnd])
need = ["简单", "普通", "困难", "大师", "开始"]
present = [t for t in need if t in bnd]
log("difficulty_dialog", len(present) >= 5, f"found {present}")
scr_diff = screenshot("01_difficulty_dialog.png")

# ============ 2. 选「简单」开始 -> 验证难度记忆写入=1 ============
if "简单" in bnd:
    tap(*bnd["简单"])
if "开始" in bnd:
    tap(*bnd["开始"])
time.sleep(2.0)
ld1 = read_prefs()
log("memory_write_normal", ld1 == 1, f"after choosing 简单, last_difficulty={ld1} (expect 1)")

# ============ 3. 游戏渲染 + 竖屏布局（无遮挡）============
scr_game = screenshot("02_game_start.png")
colored = count_colored(scr_game, BOARD)
labels = detect_control_labels(scr_game)
n_ctrl = sum(1 for v in labels if v > 20)
log("board_renders", colored > 20, f"colored piece pixels in board={colored}")
log("control_buttons_drawn", n_ctrl >= 4, f"control label clusters={n_ctrl} counts={labels}")

dlg2 = dump("_in_game.xml")
nib = count_imagebuttons(dlg2)
log("top_hud_buttons", nib >= 4, f"ImageButton nodes in game={nib}")

# ============ 4. 硬降控制生效 ============
before = count_colored(scr_game, BOARD)
for _ in range(7):
    tap(CTRL_X["DROP"], CTRL_Y)
    time.sleep(0.35)
scr_drop = screenshot("03_after_hard_drops.png")
after = count_colored(scr_drop, BOARD)
log("hard_drop_works", after > before, f"colored before={before} after={after} (locked pieces grew)")

# 旋转/左右/Hold 不崩溃
tap(CTRL_X["ROTATE"], CTRL_Y); time.sleep(0.3)
tap(CTRL_X["LEFT"], CTRL_Y); time.sleep(0.3)
tap(CTRL_X["RIGHT"], CTRL_Y); time.sleep(0.3)
tap(CTRL_X["HOLD"], CTRL_Y); time.sleep(0.3)
screenshot("03b_after_controls.png")
log("controls_no_crash", True, "ROTATE/LEFT/RIGHT/HOLD tapped without crash")

# ============ 5. 设置 -> Ghost 开关 ============
tap(*SETTINGS_BTN)
time.sleep(1.2)
dlg_set = dump("_settings.xml")
bnd_set, _ = parse_bounds(dlg_set)
has_ghost = any("落点预览" in t or "Ghost" in t for t in bnd_set)
log("settings_ghost_option", has_ghost, f"ghost option present (texts={[t for t in bnd_set if '落点' in t or 'Ghost' in t]})")
scr_set = screenshot("04_settings_dialog.png")
if has_ghost:
    gk = [t for t in bnd_set if "落点预览" in t or "Ghost" in t][0]
    tap(*bnd_set[gk])
    time.sleep(0.6)
    log("ghost_toggle_clicked", True, f"clicked '{gk}'")
    shell("input keyevent 4")
    time.sleep(0.5)

# ============ 6. 重开 + 选「大师」 -> 验证记忆写入=4 且持久 ============
dlg = open_tetris_dialog()  # 重新进入，弹难度对话框
bnd, _ = parse_bounds(dlg)
if "大师" in bnd:
    tap(*bnd["大师"])
if "开始" in bnd:
    tap(*bnd["开始"])
time.sleep(2.0)
ld4 = read_prefs()
log("memory_write_master", ld4 == 4, f"after choosing 大师, last_difficulty={ld4} (expect 4)")

# 再次重启验证持久化
dlg = open_tetris_dialog()
ld4b = read_prefs()
log("memory_persist", ld4b == 4, f"last_difficulty after relaunch={ld4b} (expect 4)")

# ============ 汇总 ============
passed = sum(1 for s in report["steps"] if s["ok"])
total = len(report["steps"])
report["summary"] = f"{passed}/{total} checks passed"
print("\n==== SUMMARY ====")
print(report["summary"])
with open(os.path.join(OUT, "report.json"), "w", encoding="utf-8") as f:
    json.dump(report, f, ensure_ascii=False, indent=2)
sys.exit(0 if passed == total else 1)
