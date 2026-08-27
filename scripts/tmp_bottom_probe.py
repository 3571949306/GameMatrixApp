# -*- coding: utf-8 -*-
"""分析 fix3.png 底部区域：塔栏/控制栏/导航区可见性"""
from PIL import Image
from collections import Counter

im = Image.open("fix3.png").convert("RGB")
W, H = im.size

def seg_color(y0, y1, label):
    cnt = Counter()
    for y in range(y0, y1, 2):
        for x in range(0, W, 6):
            cnt[im.getpixel((x, y))] += 1
    top = cnt.most_common(3)
    print("%-10s y[%d-%d] top=%s" % (label, y0, y1, [("0x%06X" % ((r<<16)|(g<<8)|b), n) for (r,g,b), n in top[:3]]))

seg_color(1600, 1680, "消息条区")
seg_color(1680, 1760, "棋盘底")
seg_color(1760, 1830, "塔栏区")
seg_color(1830, 1890, "塔栏下")
seg_color(1890, 1920, "屏幕底30px")

# 底部是否全黑（手势条）或与塔栏同色
bot = Counter()
for y in range(1890, 1920):
    for x in range(0, W, 4):
        bot[im.getpixel((x, y))] += 1
print("bottom30 black:", bot.get((0,0,0), 0), " total:", sum(bot.values()))