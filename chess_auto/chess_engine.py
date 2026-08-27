#!/usr/bin/env python3
"""
Chinese Chess (Xiangqi) Engine for automated play vs GameMatrixApp AI.
Conventions match the app's ChineseChessGame.java:
  - board[ROW][COL], ROW=0..9 (0=top/BLACK back rank, 9=bottom/RED back rank)
  - COL=0..8 (left to right)
  - RED = player (bottom, moves up / decreasing ROW)
  - BLACK = AI opponent (top, moves down / increasing ROW)
"""

from __future__ import annotations
import copy
import math
import sys
import time
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

# ─── Constants ──────────────────────────────────────────────
ROWS, COLS = 10, 9

# Piece types – ordinal matches app's PieceType enum + 1
GENERAL = 1
ADVISOR = 2
ELEPHANT = 3
HORSE = 4
CHARIOT = 5
CANNON = 6
SOLDIER = 7

# Sides
RED = 'R'
BLACK = 'B'

# Piece names for display/debug
NAMES_RED = {1: '帥', 2: '仕', 3: '相', 4: '馬', 5: '車', 6: '炮', 7: '兵'}
NAMES_BLACK = {1: '將', 2: '士', 3: '象', 4: '馬', 5: '車', 6: '砲', 7: '卒'}

# Material values (centipawns)
PIECE_VALUES = {
    GENERAL: 10000,
    ADVISOR: 20,
    ELEPHANT: 20,
    HORSE: 40,
    CHARIOT: 90,
    CANNON: 45,
    SOLDIER: 10,
}

# Piece-Square Tables (from RED's perspective; invert for BLACK)
# Each table is 10×9, indexed [row][col]. Positive = good for RED.
PST_RED = {
    GENERAL: [
        [  0,   0,   0,   0,   3,   0,   0,   0,   0],
        [  0,   0,   0,   0,   6,   0,   0,   0,   0],
        [  0,   0,   0,   0,  12,   0,   0,   0,   0],
        [  0,   0,   0,   0,  20,   0,   0,   0,   0],
        [  0,   0,   0,   0,  30,   0,   0,   0,   0],
        [  0,   0,   0,   0,  36,   0,   0,   0,   0],
        [  0,   0,   0,   0,  42,   0,   0,   0,   0],
        [  0,   0,   0,   0,  48,   0,   0,   0,   0],
        [  0,   0,   0,   0,  54,   0,   0,   0,   0],
        [  0,   0,   0,   0,  60,   0,   0,   0,   0],
    ],
    ADVISOR: [
        [  0,   0,   0,   2,   0,   2,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   4,   0,   4,   0,   0,   0],
        [  0,   0,   0,   8,   0,   8,   0,   0,   0],
    ],
    ELEPHANT: [
        [  0,   0, -4,   0,   0,   0,  -4,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [-2,   0,   0,   0,   0,   0,   0,   0,  -2],
        [  0,   0,   2,   0,   0,   0,   2,   0,   0],
    ],
    HORSE: [
        [  0,  -4,   0,   2,   4,   2,   0,  -4,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [-2,   0,   4,   4,   6,   4,   4,   0,  -2],
        [  0,   0,   2,   4,   8,   4,   2,   0,   0],
        [  0,   0,   4,   6,  10,   6,   4,   0,   0],
        [  0,   0,   6,   8,  12,   8,   6,   0,   0],
        [  0,   0,   8,  10, 14,  10,   8,   0,   0],
        [  0,   0,   6,  10,  14,  10,   6,   0,   0],
        [  0,   4,   8,  12,  16,  12,   8,   4,   0],
        [  0,   0,   2,   8,   8,   8,   2,   0,   0],
    ],
    CHARIOT: [
        [  0,   2,   4,   6,  10,   6,   4,   2,   0],
        [  2,   4,   6,   8,  12,   8,   6,   4,   2],
        [  4,   6,   8,  10,  14,  10,   8,   6,   4],
        [  6,   8,  10,  12,  16,  12,  10,   8,   6],
        [  8,  10,  12,  14,  18,  14,  12,  10,   8],
        [ 10,  12,  14,  16,  20,  16,  14,  12,  10],
        [ 12,  14,  16,  18,  22,  18,  16,  14,  12],
        [ 14,  16,  18,  20,  24,  20,  18,  16,  14],
        [ 16,  18,  20,  22,  26,  22,  20,  18,  16],
        [  0,   4,   8,  12,  18,  12,   8,   4,   0],
    ],
    CANNON: [
        [  0,   0,   2,   4,   6,   4,   2,   0,   0],
        [  0,   2,   4,   6,   8,   6,   4,   2,   0],
        [  2,   4,   6,   8,  10,   8,   6,   4,   2],
        [  0,   2,   6,   8,  12,   8,   6,   2,   0],
        [  0,   4,   8,  10,  14,  10,   8,   4,   0],
        [  0,   6,  10,  12,  16,  12,  10,   6,   0],
        [  0,   8,  12,  14,  18,  14,  12,   8,   0],
        [  0,  10,  14,  16,  20,  16,  14,  10,   0],
        [  0,  12,  16,  18,  22,  18,  16,  12,   0],
        [  0,   0,   4,   8,  14,   8,   4,   0,   0],
    ],
    SOLDIER: [
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
        [  2,   0,   4,   0,   6,   0,   4,   0,   2],
        [  4,   0,   8,   0,  12,   0,   8,   0,   4],
        [  6,   0,  12,   0,  18,   0,  12,   0,   6],
        [  8,   0,  16,   0,  24,   0,  16,   0,   8],
        [ 10,   0,  20,   0,  30,   0,  20,   0,  10],
        [ 12,   0,  24,   0,  36,   0,  24,   0,  12],
        [  0,   0,   0,   0,   0,   0,   0,   0,   0],
    ],
}


@dataclass
class Piece:
    type: int       # 1-7
    side: str      # 'R' or 'B'
    row: int
    col: int

    def name(self) -> str:
        return NAMES_RED[self.type] if self.side == RED else NAMES_BLACK[self.type]

    def copy(self) -> 'Piece':
        return Piece(self.type, self.side, self.row, self.col)


class Board:
    """Xiangqi board state with full rule implementation."""

    __slots__ = ('grid', 'side_to_move', 'move_count')

    def __init__(self):
        self.grid: List[List[Optional[Piece]]] = [[None] * COLS for _ in range(ROWS)]
        self.side_to_move: str = RED
        self.move_count: int = 0
        self._init_standard()

    def _init_standard(self):
        """Standard opening position matching ChineseChessGame.initBoard()."""
        # Red pieces (bottom, rows 6-9)
        self.grid[9][0] = Piece(CHARIOT, RED, 9, 0)
        self.grid[9][1] = Piece(HORSE, RED, 9, 1)
        self.grid[9][2] = Piece(ELEPHANT, RED, 9, 2)
        self.grid[9][3] = Piece(ADVISOR, RED, 9, 3)
        self.grid[9][4] = Piece(GENERAL, RED, 9, 4)
        self.grid[9][5] = Piece(ADVISOR, RED, 9, 5)
        self.grid[9][6] = Piece(ELEPHANT, RED, 9, 6)
        self.grid[9][7] = Piece(HORSE, RED, 9, 7)
        self.grid[9][8] = Piece(CHARIOT, RED, 9, 8)
        self.grid[7][1] = Piece(CANNON, RED, 7, 1)
        self.grid[7][7] = Piece(CANNON, RED, 7, 7)
        for c in [0, 2, 4, 6, 8]:
            self.grid[6][c] = Piece(SOLDIER, RED, 6, c)

        # Black pieces (top, rows 0-3)
        self.grid[0][0] = Piece(CHARIOT, BLACK, 0, 0)
        self.grid[0][1] = Piece(HORSE, BLACK, 0, 1)
        self.grid[0][2] = Piece(ELEPHANT, BLACK, 0, 2)
        self.grid[0][3] = Piece(ADVISOR, BLACK, 0, 3)
        self.grid[0][4] = Piece(GENERAL, BLACK, 0, 4)
        self.grid[0][5] = Piece(ADVISOR, BLACK, 0, 5)
        self.grid[0][6] = Piece(ELEPHANT, BLACK, 0, 6)
        self.grid[0][7] = Piece(HORSE, BLACK, 0, 7)
        self.grid[0][8] = Piece(CHARIOT, BLACK, 0, 8)
        self.grid[2][1] = Piece(CANNON, BLACK, 2, 1)
        self.grid[2][7] = Piece(CANNON, BLACK, 2, 7)
        for c in [0, 2, 4, 6, 8]:
            self.grid[3][c] = Piece(SOLDIER, BLACK, 3, c)

    def copy(self) -> 'Board':
        b = Board.__new__(Board)
        b.grid = [[self.grid[r][c].copy() if self.grid[r][c] else None for c in range(COLS)] for r in range(ROWS)]
        b.side_to_move = self.side_to_move
        b.move_count = self.move_count
        return b

    def in_bounds(self, r: int, c: int) -> bool:
        return 0 <= r < ROWS and 0 <= c < COLS

    def in_palace(self, r: int, c: int, side: str) -> bool:
        if side == RED:
            return 3 <= c <= 5 and 7 <= r <= 9
        return 3 <= c <= 5 and 0 <= r <= 2

    def find_general(self, side: str) -> Optional[Tuple[int, int]]:
        for r in range(ROWS):
            for c in range(COLS):
                p = self.grid[r][c]
                if p and p.type == GENERAL and p.side == side:
                    return (r, c)
        return None

    def is_in_check(self, side: str) -> bool:
        """Check if `side`'s general is under attack."""
        gen_pos = self.find_general(side)
        if not gen_pos:
            return True  # General captured = in check
        enemy = BLACK if side == RED else RED
        gr, gc = gen_pos
        for r in range(ROWS):
            for c in range(COLS):
                p = self.grid[r][c]
                if p and p.side == enemy:
                    for mr, mc in self._pseudo_moves(p):
                        if (mr, mc) == (gr, gc):
                            return True
        return False

    def _pseudo_moves(self, piece: Piece) -> List[Tuple[int, int]]:
        """Generate pseudo-legal moves (may leave own king in check)."""
        r, c = piece.row, piece.col
        moves = []
        t = piece.type
        s = piece.side

        if t == GENERAL:
            for dr, dc in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
                nr, nc = r + dr, c + dc
                if self.in_palace(nr, nc, s):
                    target = self.grid[nr][nc]
                    if target is None or target.side != s:
                        moves.append((nr, nc))
            # Flying general (facing generals)
            enemy_side = BLACK if s == RED else RED
            epos = self.find_general(enemy_side)
            if epos and epos[1] == c:  # same column
                er = epos[0]
                blocked = False
                for y in range(min(r, er) + 1, max(r, er)):
                    if self.grid[y][c] is not None:
                        blocked = True
                        break
                if not blocked:
                    moves.append(epos)

        elif t == ADVISOR:
            for dr, dc in [(1, 1), (1, -1), (-1, 1), (-1, -1)]:
                nr, nc = r + dr, c + dc
                if self.in_palace(nr, nc, s):
                    target = self.grid[nr][nc]
                    if target is None or target.side != s:
                        moves.append((nr, nc))

        elif t == ELEPHANT:
            for dr, dc, er, ec in [(2, 2, 1, 1), (2, -2, 1, -1),
                                     (-2, 2, -1, 1), (-2, -2, -1, -1)]:
                nr, nc = r + dr, c + dc
                if self.in_bounds(nr, nc):
                    # Cannot cross river
                    if s == RED and nr < 5:
                        continue
                    if s == BLACK and nr > 4:
                        continue
                    # Elephant eye must be empty
                    if self.grid[r + er][c + ec] is None:
                        target = self.grid[nr][nc]
                        if target is None or target.side != s:
                            moves.append((nr, nc))

        elif t == HORSE:
            for dr, dc, lr, lc in [(2, 1, 1, 0), (2, -1, 1, 0),
                                      (-2, 1, -1, 0), (-2, -1, -1, 0),
                                      (1, 2, 0, 1), (1, -2, 0, -1),
                                      (-1, 2, 0, 1), (-1, -2, 0, -1)]:
                nr, nc = r + dr, c + dc
                if self.in_bounds(nr, nc):
                    # Horse leg must be empty
                    if self.grid[r + lr][c + lc] is None:
                        target = self.grid[nr][nc]
                        if target is None or target.side != s:
                            moves.append((nr, nc))

        elif t == CHARIOT:
            for dr, dc in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
                nr, nc = r + dr, c + dc
                while self.in_bounds(nr, nc):
                    target = self.grid[nr][nc]
                    if target is None:
                        moves.append((nr, nc))
                    else:
                        if target.side != s:
                            moves.append((nr, nc))
                        break
                    nr += dr
                    nc += dc

        elif t == CANNON:
            for dr, dc in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
                nr, nc = r + dr, c + dc
                jumped = False
                while self.in_bounds(nr, nc):
                    target = self.grid[nr][nc]
                    if target is None:
                        if not jumped:
                            moves.append((nr, nc))
                    else:
                        if not jumped:
                            jumped = True
                        else:
                            if target.side != s:
                                moves.append((nr, nc))
                            break
                    nr += dr
                    nc += dc

        elif t == SOLDIER:
            if s == RED:
                # Forward (up)
                nr = r - 1
                if self.in_bounds(nr, c):
                    target = self.grid[nr][c]
                    if target is None or target.side != s:
                        moves.append((nr, c))
                # After crossing river (row < 5): sideways
                if r < 5:
                    for dc in [-1, 1]:
                        nc = c + dc
                        if self.in_bounds(r, nc):
                            target = self.grid[r][nc]
                            if target is None or target.side != s:
                                moves.append((r, nc))
            else:
                # Forward (down)
                nr = r + 1
                if self.in_bounds(nr, c):
                    target = self.grid[nr][c]
                    if target is None or target.side != s:
                        moves.append((nr, c))
                # After crossing river (row > 4): sideways
                if r > 4:
                    for dc in [-1, 1]:
                        nc = c + dc
                        if self.in_bounds(r, nc):
                            target = self.grid[r][nc]
                            if target is None or target.side != s:
                                moves.append((r, nc))

        return moves

    def generate_legal_moves(self, side: str) -> List[Tuple[int, int, int, int]]:
        """Generate all legal moves for `side`. Returns list of (fr, fc, tr, tc)."""
        legal = []
        for r in range(ROWS):
            for c in range(COLS):
                p = self.grid[r][c]
                if p and p.side == side:
                    for tr, tc in self._pseudo_moves(p):
                        # Try the move
                        captured = self.grid[tr][tc]
                        self.grid[tr][tc] = p
                        self.grid[r][c] = None
                        old_r, old_c = p.row, p.col
                        p.row, p.col = tr, tc

                        if not self.is_in_check(side):
                            legal.append((r, c, tr, tc))

                        # Undo
                        p.row, p.col = old_r, old_c
                        self.grid[r][c] = p
                        self.grid[tr][tc] = captured
        return legal

    def make_move(self, fr: int, fc: int, tr: int, tc: int) -> Optional[Piece]:
        """Execute a move. Returns captured piece (or None). Updates internal state."""
        piece = self.grid[fr][fc]
        if piece is None:
            return None
        captured = self.grid[tr][tc]
        self.grid[tr][tc] = piece
        self.grid[fr][fc] = None
        piece.row, piece.col = tr, tc
        self.side_to_move = BLACK if self.side_to_move == RED else RED
        self.move_count += 1
        return captured

    def unmake_move(self, fr: int, fc: int, tr: int, tc: int, captured: Optional[Piece]):
        """Undo a move."""
        piece = self.grid[tr][tc]
        if piece is None:
            return
        self.grid[fr][fc] = piece
        piece.row, piece.col = fr, fc
        self.grid[tr][tc] = captured
        self.side_to_move = BLACK if self.side_to_move == RED else RED
        self.move_count -= 1

    def has_legal_moves(self, side: str) -> bool:
        """Check if `side` has any legal move."""
        for r in range(ROWS):
            for c in range(COLS):
                p = self.grid[r][c]
                if p and p.side == side:
                    for tr, tc in self._pseudo_moves(p):
                        captured = self.grid[tr][tc]
                        self.grid[tr][tc] = p
                        self.grid[r][c] = None
                        old_r, old_c = p.row, p.col
                        p.row, p.col = tr, tc
                        ok = not self.is_in_check(side)
                        p.row, p.col = old_r, old_c
                        self.grid[r][c] = p
                        self.grid[tr][tc] = captured
                        if ok:
                            return True
        return False

    def is_game_over(self) -> Tuple[bool, Optional[str]]:
        """Returns (is_over, winner). Winner is 'R', 'B', or None (draw)."""
        side = self.side_to_move
        if self.is_in_check(side):
            if not self.has_legal_moves(side):
                winner = BLACK if side == RED else RED
                return True, winner
        else:
            if not self.has_legal_moves(side):
                winner = BLACK if side == RED else RED
                return True, winner
        return False, None

    def evaluate(self) -> int:
        """Static evaluation from RED's perspective (positive = good for RED)."""
        score = 0
        for r in range(ROWS):
            for c in range(COLS):
                p = self.grid[r][c]
                if p is None:
                    continue
                val = PIECE_VALUES.get(p.type, 0)
                pst = PST_RED.get(p.type)
                if pst:
                    if p.side == RED:
                        val += pst[r][c]
                    else:
                        # Mirror PST for black (invert row)
                        val -= pst[ROWS - 1 - r][c]

                if p.side == RED:
                    score += val
                else:
                    score -= val
        return score


# ─── Search Engine (negamax) ───────────────────────────────
MATE_SCORE = 100000
INF = 10_000_000

# History heuristic table
_history: dict = {}

# Time-bounded iterative deepening
_DEADLINE = None


class _TimeUp(Exception):
    pass


def order_moves(board: Board, moves: list) -> list:
    """Sort moves: captures first, then by history heuristic score."""
    def score(m):
        fr, fc, tr, tc = m
        s = 0
        # Capture bonus
        target = board.grid[tr][tc]
        if target:
            s += 10000 + PIECE_VALUES.get(target.type, 0)
        # History heuristic
        s += _history.get(m, 0)
        return -s  # negative for descending sort
    return sorted(moves, key=score)


def quiescence(board: Board, alpha: int, beta: int, depth: int = 0, max_depth: int = 4) -> int:
    """Quiescence search: only consider captures to avoid horizon effect.
    Returns the position value from the perspective of board.side_to_move."""
    side = board.side_to_move
    # Static eval from the side-to-move's perspective
    stand_pat = board.evaluate() if side == RED else -board.evaluate()

    if stand_pat >= beta:
        return beta
    if alpha < stand_pat:
        alpha = stand_pat
    if depth >= max_depth:
        return alpha

    captures = []
    for r in range(ROWS):
        for c in range(COLS):
            p = board.grid[r][c]
            if p and p.side == side:
                for tr, tc in board._pseudo_moves(p):
                    if board.grid[tr][tc] is not None:  # capture only
                        captures.append((r, c, tr, tc))

    for m in captures:
        fr, fc, tr, tc = m
        cap = board.make_move(fr, fc, tr, tc)
        try:
            score = -quiescence(board, -beta, -alpha, depth + 1, max_depth)
        finally:
            board.unmake_move(fr, fc, tr, tc, cap)
        if score >= beta:
            return beta
        if score > alpha:
            alpha = score
    return alpha


def alphabeta(board: Board, depth: int, alpha: int, beta: int) -> int:
    """Negamax alpha-beta search. Returns value from side_to_move's perspective."""
    global _DEADLINE
    if _DEADLINE is not None and time.time() > _DEADLINE:
        raise _TimeUp()

    side = board.side_to_move
    if depth <= 0:
        return quiescence(board, alpha, beta)

    moves = board.generate_legal_moves(side)
    if not moves:
        # In Xiangqi, having no legal move (checkmate or 困毙/stalemate) is a loss.
        return -(MATE_SCORE + depth)

    moves = order_moves(board, moves)

    for m in moves:
        fr, fc, tr, tc = m
        cap = board.make_move(fr, fc, tr, tc)
        try:
            score = -alphabeta(board, depth - 1, -beta, -alpha)
        finally:
            board.unmake_move(fr, fc, tr, tc, cap)

        if score >= beta:
            _history[m] = _history.get(m, 0) + depth * depth
            return beta
        if score > alpha:
            alpha = score
    return alpha


def get_best_move(board: Board, depth: int = 4, time_limit: float = 6.0,
                  excluded: Optional[set] = None) -> Optional[Tuple[int,int,int,int]]:
    """
    Find the best move for the current side using iterative deepening with a
    wall-clock budget. Returns (fr, fc, tr, tc) or None if no legal moves.
    `excluded` is a set of moves to skip (used when retrying after a rejected move).
    """
    global _DEADLINE, _history
    start = time.time()
    _DEADLINE = start + time_limit
    side = board.side_to_move
    best_move = None
    best_score = None

    for d in range(1, depth + 1):
        try:
            moves = [m for m in board.generate_legal_moves(side)
                     if not (excluded and m in excluded)]
            if not moves:
                return None
            cur_best = None
            cur_score = -INF
            for m in moves:
                fr, fc, tr, tc = m
                cap = board.make_move(fr, fc, tr, tc)
                try:
                    score = -alphabeta(board, d - 1, -INF, INF)
                finally:
                    board.unmake_move(fr, fc, tr, tc, cap)
                if score > cur_score:
                    cur_score = score
                    cur_best = m
            best_move = cur_best
            best_score = cur_score
            elapsed = time.time() - start
            pname = board.grid[best_move[0]][best_move[1]].name() if best_move else '?'
            print(f"  Depth {d}: best={cur_score} ({elapsed:.1f}s) move={best_move} [{pname}]", file=sys.stderr)
            if time.time() - start > time_limit * 0.85:
                break
        except _TimeUp:
            print(f"  Time-up at depth {d}, keeping previous best", file=sys.stderr)
            break

    if best_move:
        fr, fc, tr, tc = best_move
        piece = board.grid[fr][fc]
        pname = piece.name() if piece else '?'
        print(f"Best move: ({fr},{fc})->({tr},{tc}) [{pname}] score={best_score}", file=sys.stderr)

    return best_move


def board_to_str(board: Board) -> str:
    """Pretty-print the board for debugging."""
    lines = []
    for r in range(ROWS):
        row = ''
        for c in range(COLS):
            p = board.grid[r][c]
            if p:
                row += f'{p.name():>2} '
            else:
                row += ' . '
        lines.append(f'{r}: {row}')
    return '\n'.join(lines)


if __name__ == '__main__':
    b = Board()
    print("Initial position:")
    print(board_to_str(b))
    print(f"\nSide to move: {b.side_to_move}")
    print(f"Legal moves count: {len(b.generate_legal_moves(RED))}")

    # Quick test: compute best move at depth 3
    print("\nSearching depth 3...")
    t0 = time.time()
    mv = get_best_move(b, depth=3, time_limit=10.0)
    elapsed = time.time() - t0
    if mv:
        print(f"Result: {mv} in {elapsed:.1f}s")
    else:
        print("No legal moves!")
