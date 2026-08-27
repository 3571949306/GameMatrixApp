# -*- coding: utf-8 -*-
"""分析塔防截图：验证草地/路径/塔/怪物是否真实渲染（颜色抽样）"""
import sys
from PIL import Image

def classify(rgb):
    r, g, b = rgb
    if r > 200 and g > 200 and b > 200:
        return "白/高光"
    if r > 180 and 140 < g < 220 and b < 120:
        return "黄(金币/太阳花)"
    if 40 < r < 130 and 120 < g < 190 and 70 < b < 140:
        return "绿(草地)"
    if r > 190 and 160 < g < 210 and b < 150:
        return "沙土(路径)"
    if b > 150 and r < 140 and g > 160:
        return "蓝青(瓶子炮/雪花)"
    if r < 80 and g < 80 and b < 80:
        return "深色(UI/暗)"
    if r > 150 and g < 110 and b < 110:
        return "红(怪物/Boss)"
    if r > 120 and g > 120 and b > 120:
        return "浅灰"
    return "其他"

def probe(path, points):
    print("=== %s ===" % path)
    im = Image.open(path).convert("RGB")
    w, h = im.size
    print("size=%dx%d" % (w, h))
    for name, x, y in points:
        xx = int(x * w), int(y * h)
        px = im.getpixel(xx)
        print("  %-14s (%.3f,%.3f)px=%s %s" % (name, x, y, xx, classify(px)))

# 假定棋盘横向铺满(12列)，cell≈90，棋盘顶=425
W, H = 1080, 1920
def cell(c, r):
    return ((c + 0.5) * 90) / W, (425 + (r + 0.5) * 90) / H

probe("v_ready.png", [
    ("路径row0", *cell(3, 0)),
    ("草地(4,4)装饰区", *cell(4, 4)),
    ("草地(2,2)", *cell(2, 2)),
    ("底部非棋盘", 0.5, 0.95),
])
probe("v_towers.png", [
    ("瓶炮位(2,2)", *cell(2, 2)),
    ("太阳花位(4,2)", *cell(4, 2)),
    ("雪花位(2,5)", *cell(2, 5)),
    ("对比草地(6,5)", *cell(6, 5)),
])
probe("v_battle.png", [
    ("蛋蛋(7,0)", *cell(0, 7)),
    ("路上(4,0)", *cell(0, 4)),
    ("路上(0,8)", *cell(8, 0)),
])