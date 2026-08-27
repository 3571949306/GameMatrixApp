#!/usr/bin/env python3
"""
Automated Chinese Chess (Xiangqi) player for GameMatrixApp on a local emulator.

Pipeline
--------
1. Launch the (non-exported) DynamicGameActivity with gameId=chinesechess via
   `am start` as root (adb root), which loads the pre-installed module APK.
2. Select difficulty 低 (weakest AI) and tap 开始游戏.
3. Read chess_view bounds and replicate the app's onMeasure board geometry.
4. Play the game:
   - Engine (chess_engine.py, negamax + quiescence) computes RED's best move.
   - Tap from-square then to-square (device pixels).
   - Verify the move was accepted by detecting the gold "last move" markers
     (#FFD700) the app paints on both from/to intersections (no OCR needed).
   - Wait for AI; when it's our turn again, detect the AI's last-move squares
     and apply them to our mirror board (direction inferred from piece color).
5. On win, stop. On loss/draw, restart (up to MAX_GAMES).

Run with:  python auto_play.py            (full play)
           python auto_play.py --validate (one move + gold-marker diagnostic, then exit)
"""
import sys
import os
import time
import math

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from chess_engine import Board, get_best_move, RED, BLACK
import uiautomator2 as u2
from PIL import Image

PKG = 'com.gamecenter.app'
SERIAL = 'emulator-5556'
RID = lambda s: f'{PKG}:id/{s}'
LOW, START, STATUS, VIEW = 'btn_difficulty_1', 'btn_start_game', 'tv_status', 'chess_view'
PAD = 24
DEVICE_W, DEVICE_H = 1080, 2400

# board geometry (device pixels)
cell = 0.0
bl = 0.0
bt = 0.0

LOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'auto_play.log')


def log(msg):
    ts = time.strftime('%H:%M:%S')
    line = f"[{ts}] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_PATH, 'a', encoding='utf-8') as f:
            f.write(line + '\n')
    except Exception:
        pass


def connect():
    d = u2.connect()
    log(f"connected: {d.info.get('productName', '?')}")
    return d


def adb(args):
    os.system(f'adb -s {SERIAL} {args}')


def ensure_chess_screen(d):
    """Make sure we are on the Chinese Chess difficulty screen and start a game."""
    if not d(resourceId=RID(LOW)).exists(timeout=2):
        log("difficulty buttons not visible -> relaunching module")
        adb('root >/dev/null 2>&1')
        adb(f'shell am start -n {PKG}/.DynamicGameActivity --es gameId chinesechess')
        time.sleep(3)
    # select 低 (weakest)
    d(resourceId=RID(LOW)).click()
    time.sleep(0.4)
    # start
    d(resourceId=RID(START)).click()
    time.sleep(2.5)
    log("game started (低 difficulty)")


def get_geometry(d):
    global cell, bl, bt
    v = d(resourceId=RID(VIEW))
    if not v.exists(timeout=6):
        raise RuntimeError("chess_view not found")
    left, top, right, bottom = v.bounds
    vw, vh = right - left, bottom - top
    cell = min((vw - 2 * PAD) / 8.0, (vh - 2 * PAD) / 9.0)
    bl = left + (vw - cell * 8) / 2.0
    bt = top + (vh - cell * 9) / 2.0
    log(f"geometry cell={cell:.1f} bl={bl:.1f} bt={bt:.1f} view={vw}x{vh}")


def board_to_screen(row, col):
    return (bl + col * cell, bt + row * cell)


def tap(d, row, col):
    x, y = board_to_screen(row, col)
    d.click(int(x), int(y))


def get_status(d):
    el = d(resourceId=RID(STATUS))
    if el.exists(timeout=0.5):
        return el.text or ''
    return ''


def shot(d):
    return d.screenshot()


def is_gold(r, g, b):
    return (r > 180 and g > 140 and b < 150 and (r - g) > 15 and (g - b) > 25)


def gold_cells(img):
    W, H = img.size
    scale = W / DEVICE_W
    px = img.load()
    hs = 0.42 * cell * scale
    found = []
    for r in range(10):
        for c in range(9):
            cx, cy = board_to_screen(r, c)
            cx *= scale
            cy *= scale
            x1, y1 = int(cx - hs), int(cy - hs)
            x2, y2 = int(cx + hs), int(cy + hs)
            cnt = 0
            for yy in range(y1, y2 + 1, 2):
                for xx in range(x1, x2 + 1, 2):
                    if 0 <= xx < W and 0 <= yy < H:
                        pr, pg, pb = px[xx, yy][:3]
                        if is_gold(pr, pg, pb):
                            cnt += 1
            if cnt >= 5:
                found.append((r, c))
    return found


def diagnostic_colors(img, cells):
    """Print actual pixel colors around given intersections (for tuning)."""
    W, H = img.size
    scale = W / DEVICE_W
    px = img.load()
    for (r, c) in cells:
        cx, cy = board_to_screen(r, c)
        cx, cy = int(cx * scale), int(cy * scale)
        ring = []
        for ang in range(0, 360, 45):
            rx = int(cx + 0.30 * cell * scale * math.cos(math.radians(ang)))
            ry = int(cy + 0.30 * cell * scale * math.sin(math.radians(ang)))
            if 0 <= rx < W and 0 <= ry < H:
                ring.append(px[rx, ry][:3])
        log(f"  diag ({r},{c}) ring={ring}")


def resolve_ai(g, board):
    """Given the 2 gold intersections after AI moved, return (fr,fc,tr,tc).
    The source square is the one holding a BLACK piece in our (pre-AI) board."""
    black_cell = None
    other = None
    for (r, c) in g:
        p = board.grid[r][c]
        if p and p.side == BLACK:
            black_cell = (r, c)
        else:
            other = (r, c)
    if black_cell is None or other is None:
        return (None, None, None, None)
    return (black_cell[0], black_cell[1], other[0], other[1])


def play_game(d, board, my_last_gold, validate=False):
    move_no = 0
    while True:
        status = get_status(d)
        if '获胜' in status or '和棋' in status:
            if 'AI获胜' in status:
                log("RESULT: LOSS"); return 'loss'
            if '和棋' in status:
                log("RESULT: DRAW"); return 'draw'
            log("RESULT: WIN"); return 'win'

        if '你的回合' not in status:
            time.sleep(1.0)
            continue

        # ----- my turn -----
        move_no += 1
        t0 = time.time()
        mv = get_best_move(board, depth=4, time_limit=4.0)
        if mv is None:
            log("engine: no legal move -> loss"); return 'loss'
        fr, fc, tr, tc = mv
        piece = board.grid[fr][fc]
        pname = piece.name() if piece else '?'
        log(f"move {move_no}: RED ({fc},{fr})->({tc},{tr}) [{pname}] ({time.time() - t0:.1f}s)")

        # execute with retry on rejection
        excluded = set()
        accepted = False
        for attempt in range(4):
            fr, fc, tr, tc = mv
            tap(d, fr, fc)
            time.sleep(0.45)
            tap(d, tr, tc)
            time.sleep(1.3)
            img = shot(d)
            g = gold_cells(img)
            if set(g) == {(fr, fc), (tr, tc)}:
                board.make_move(fr, fc, tr, tc)
                my_last_gold.clear()
                my_last_gold.update({(fr, fc), (tr, tc)})
                accepted = True
                break
            log(f"  move not confirmed by gold (detected={g}); retry {attempt + 1}")
            excluded.add((fr, fc, tr, tc))
            st = get_status(d)
            if '获胜' in st or '和棋' in st:
                if 'AI获胜' in st:
                    return 'loss'
                if '和棋' in st:
                    return 'draw'
                return 'win'
            mv = get_best_move(board, depth=4, time_limit=4.0, excluded=excluded)
            if mv is None:
                break
        if not accepted:
            log("FAILED to get a confirmed move -> desync"); return 'desync'

        if validate:
            log("VALIDATE: first move confirmed by gold markers. Diagnostic:")
            diagnostic_colors(img, list(my_last_gold))
            return 'validate'

        # ----- wait for AI -----
        ai_done = False
        for _ in range(45):
            st = get_status(d)
            if '获胜' in st or '和棋' in st:
                if 'AI获胜' in st:
                    return 'loss'
                if '和棋' in st:
                    return 'draw'
                return 'win'
            if '你的回合' in st:
                img = shot(d)
                g = gold_cells(img)
                if len(g) == 2 and set(g) != my_last_gold:
                    afr, afc, atr, atc = resolve_ai(g, board)
                    if afr is None:
                        log(f"WARN cannot resolve AI move from {g}; resync failed")
                        return 'desync'
                    board.make_move(afr, afc, atr, atc)
                    my_last_gold.clear()
                    my_last_gold.update(g)
                    log(f"AI moved: BLACK ({afc},{afr})->({atc},{atr})")
                    ai_done = True
                    break
                # else still showing our move; keep waiting for redraw
            time.sleep(1.0)
        if not ai_done:
            log("WARN: AI move not detected in time; re-checking status")
            st = get_status(d)
            if '获胜' in st or '和棋' in st:
                if 'AI获胜' in st:
                    return 'loss'
                if '和棋' in st:
                    return 'draw'
                return 'win'
            # give it one more shot
            img = shot(d)
            g = gold_cells(img)
            if len(g) == 2 and set(g) != my_last_gold:
                afr, afc, atr, atc = resolve_ai(g, board)
                if afr is not None:
                    board.make_move(afr, afc, atr, atc)
                    my_last_gold.clear()
                    my_last_gold.update(g)
                    log(f"AI moved (late): BLACK ({afc},{afr})->({atc},{atr})")
                    ai_done = True
            if not ai_done:
                log("AI move truly undetected -> desync"); return 'desync'


def restart_after_end(d):
    """After a game ends, try to start another. Look for a restart button,
    else relaunch the module activity."""
    log("attempting restart...")
    # look for a restart button by common Chinese keywords
    for kw in ['再来', '重新', '重玩', '再玩', '重开']:
        el = d(textContains=kw)
        if el.exists(timeout=1):
            log(f"tapping restart button containing '{kw}'")
            el.click()
            time.sleep(2.5)
            return True
    # fallback: relaunch
    adb('root >/dev/null 2>&1')
    adb(f'shell am start -n {PKG}/.DynamicGameActivity --es gameId chinesechess')
    time.sleep(3)
    return True


def main():
    validate = '--validate' in sys.argv
    if os.path.exists(LOG_PATH):
        try:
            os.remove(LOG_PATH)
        except Exception:
            pass
    d = connect()
    max_games = 6
    for gnum in range(1, max_games + 1):
        log(f"=== GAME {gnum} ===")
        ensure_chess_screen(d)
        get_geometry(d)
        board = Board()
        my_last_gold = set()
        res = play_game(d, board, my_last_gold, validate=validate)
        if validate:
            log("VALIDATE mode complete.")
            return
        if res == 'win':
            try:
                d.screenshot(os.path.join(os.path.dirname(__file__), 'victory.png'))
            except Exception:
                pass
            log("*** VICTORY ACHIEVED ***")
            return
        log(f"game {gnum} ended: {res}; restarting")
        restart_after_end(d)
        time.sleep(2)
    log("exhausted games without a win")


if __name__ == '__main__':
    main()
