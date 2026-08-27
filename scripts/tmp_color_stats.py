# -*- coding: utf-8 -*-
"""色族统计：验证塔/怪物真实渲染。v_ready=开局准备图, v_towers/v_battle=当前战斗图"""
from PIL import Image

FILES = {"ready": "v_ready.png", "towers": "v_towers.png", "battle": "v_battle.png"}

def is_blue(p):   # 瓶炮/雪花 蓝青
    r, g, b = p
    return r < 165 and g > 185 and b > 220

def is_gold(p):   # 太阳花/金币 金黄
    r, g, b = p
    return r > 230 and 120 < g < 205 and b < 130

def is_green(p):  # 草地
    r, g, b = p
    return 40 < r < 140 and 120 < g < 200 and 60 < b < 150

def is_yellow2(p):  # 怪物 FAST/弹道 橙黄
    r, g, b = p
    return r > 235 and g > 150 and g < 220 and b < 110

def is_purple(p):  # 毒泡/中毒
    r, g, b = p
    return r > 140 and g < 150 and b > 170

for name, path in FILES.items():
    im = Image.open(path).convert("RGB")
    W, H = im.size
    blue = gold = green = yel = pur = 0
    for y in range(0, H):
        if 1700 < y < 1920:  # 跳过 HUD/塔栏区近似的底部
            continue
        for x in range(0, W, 3):
            p = im.getpixel((x, y))
            if is_blue(p): blue += 1
            elif is_gold(p): gold += 1
            elif is_yellow2(p): yel += 1
            elif is_purple(p): pur += 1
            elif is_green(p): green += 1
    print("%-7s blue=%6d gold=%6d yell=%6d purple=%6d green=%8d" % (name, blue, gold, yel, pur, green))