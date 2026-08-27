# -*- coding: utf-8 -*-
"""扫描截图中沙土路径色目标位置，反推棋盘 originY/cell"""
from PIL import Image

im = Image.open("v_ready.png").convert("RGB")
W, H = im.size

def is_path(p):
    r, g, b = p
    return r > 200 and 150 < g < 220 and b < 170

ys = []
for y in range(H):
    cnt = sum(1 for x in range(0, W, 4) if is_path(im.getpixel((x, y))))
    if cnt > 8:
        ys.append(y)
if ys:
    print("path rows y range:", ys[0], "-", ys[-1], "count", len(ys))
    # 找第一段(顶部横向run)
    # 顶部连续run
    runs = []
    start = prev = ys[0]
    for y in ys[1:]:
        if y - prev > 6:
            runs.append((start, prev)); start = y
        prev = y
    runs.append((start, prev))
    for s, e in runs[:3]:
        print("run y:", s, "-", e)
# 扫描行内沙土 x 分布（取其中一行）
if ys:
    y0 = ys[0]
    xs = [x for x in range(W) if is_path(im.getpixel((x, y0)))]
    if xs:
        print("top run x:", xs[0], "-", xs[-1], "n=", len(xs))