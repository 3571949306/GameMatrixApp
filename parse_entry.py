import re, sys
path = sys.argv[1] if len(sys.argv) > 1 else 'ui_entry2.xml'
xml = open(path, encoding='utf-8').read()
targets = ('俄罗斯方块', '立即开始', '经典俄罗斯方块游戏', '简单', '普通', '困难', '大师')
for m in re.finditer(r'text="([^"]+)"[^>]*bounds="(\[[0-9,]+\]\[[0-9,]+\])"', xml):
    t, b = m.group(1), m.group(2)
    if t in targets:
        mm = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
        x1, y1, x2, y2 = map(int, mm.groups())
        print(repr(t), b, 'center', (x1 + x2) // 2, (y1 + y2) // 2)
