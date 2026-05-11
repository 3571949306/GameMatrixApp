package com.gamecenter.app.games.chinesechess;

import java.util.ArrayList;
import java.util.List;

public class ChineseChessGame {

    public static final int COLS = 9;
    public static final int ROWS = 10;

    public enum PieceType {
        GENERAL, ADVISOR, ELEPHANT, HORSE, CHARIOT, CANNON, SOLDIER
    }

    public enum Side {
        RED, BLACK
    }

    public static class Piece {
        public PieceType type;
        public Side side;
        public int x, y;

        public Piece(PieceType type, Side side, int x, int y) {
            this.type = type;
            this.side = side;
            this.x = x;
            this.y = y;
        }

        public String getName() {
            String[] namesRed = {"帥", "仕", "相", "馬", "車", "炮", "兵"};
            String[] namesBlack = {"將", "士", "象", "馬", "車", "砲", "卒"};
            return side == Side.RED ? namesRed[type.ordinal()] : namesBlack[type.ordinal()];
        }
    }

    public static class MoveRecord {
        public int fromX, fromY, toX, toY;
        public Piece piece;
        public Piece captured;

        public MoveRecord(int fx, int fy, int tx, int ty, Piece piece, Piece captured) {
            this.fromX = fx; this.fromY = fy; this.toX = tx; this.toY = ty;
            this.piece = piece; this.captured = captured;
        }
    }

    private Piece[][] board;
    private Side currentSide;
    private boolean gameOver;
    private Side winner;
    private List<MoveRecord> moveHistory;

    public ChineseChessGame() {
        board = new Piece[ROWS][COLS];
        currentSide = Side.RED;
        gameOver = false;
        winner = null;
        moveHistory = new ArrayList<>();
        initBoard();
    }

    private void initBoard() {
        board[9][0] = new Piece(PieceType.CHARIOT, Side.RED, 0, 9);
        board[9][1] = new Piece(PieceType.HORSE, Side.RED, 1, 9);
        board[9][2] = new Piece(PieceType.ELEPHANT, Side.RED, 2, 9);
        board[9][3] = new Piece(PieceType.ADVISOR, Side.RED, 3, 9);
        board[9][4] = new Piece(PieceType.GENERAL, Side.RED, 4, 9);
        board[9][5] = new Piece(PieceType.ADVISOR, Side.RED, 5, 9);
        board[9][6] = new Piece(PieceType.ELEPHANT, Side.RED, 6, 9);
        board[9][7] = new Piece(PieceType.HORSE, Side.RED, 7, 9);
        board[9][8] = new Piece(PieceType.CHARIOT, Side.RED, 8, 9);
        board[7][1] = new Piece(PieceType.CANNON, Side.RED, 1, 7);
        board[7][7] = new Piece(PieceType.CANNON, Side.RED, 7, 7);
        board[6][0] = new Piece(PieceType.SOLDIER, Side.RED, 0, 6);
        board[6][2] = new Piece(PieceType.SOLDIER, Side.RED, 2, 6);
        board[6][4] = new Piece(PieceType.SOLDIER, Side.RED, 4, 6);
        board[6][6] = new Piece(PieceType.SOLDIER, Side.RED, 6, 6);
        board[6][8] = new Piece(PieceType.SOLDIER, Side.RED, 8, 6);

        board[0][0] = new Piece(PieceType.CHARIOT, Side.BLACK, 0, 0);
        board[0][1] = new Piece(PieceType.HORSE, Side.BLACK, 1, 0);
        board[0][2] = new Piece(PieceType.ELEPHANT, Side.BLACK, 2, 0);
        board[0][3] = new Piece(PieceType.ADVISOR, Side.BLACK, 3, 0);
        board[0][4] = new Piece(PieceType.GENERAL, Side.BLACK, 4, 0);
        board[0][5] = new Piece(PieceType.ADVISOR, Side.BLACK, 5, 0);
        board[0][6] = new Piece(PieceType.ELEPHANT, Side.BLACK, 6, 0);
        board[0][7] = new Piece(PieceType.HORSE, Side.BLACK, 7, 0);
        board[0][8] = new Piece(PieceType.CHARIOT, Side.BLACK, 8, 0);
        board[2][1] = new Piece(PieceType.CANNON, Side.BLACK, 1, 2);
        board[2][7] = new Piece(PieceType.CANNON, Side.BLACK, 7, 2);
        board[3][0] = new Piece(PieceType.SOLDIER, Side.BLACK, 0, 3);
        board[3][2] = new Piece(PieceType.SOLDIER, Side.BLACK, 2, 3);
        board[3][4] = new Piece(PieceType.SOLDIER, Side.BLACK, 4, 3);
        board[3][6] = new Piece(PieceType.SOLDIER, Side.BLACK, 6, 3);
        board[3][8] = new Piece(PieceType.SOLDIER, Side.BLACK, 8, 3);
    }

    public Piece[][] getBoard() { return board; }
    public Side getCurrentSide() { return currentSide; }
    public boolean isGameOver() { return gameOver; }
    public Side getWinner() { return winner; }
    public List<MoveRecord> getMoveHistory() { return moveHistory; }

    private boolean isValidPosition(int x, int y) {
        return x >= 0 && x < COLS && y >= 0 && y < ROWS;
    }

    private boolean inPalace(int x, int y, Side side) {
        if (side == Side.RED) return x >= 3 && x <= 5 && y >= 7 && y <= 9;
        else return x >= 3 && x <= 5 && y >= 0 && y <= 2;
    }

    public List<int[]> getMoves(Piece piece) {
        List<int[]> moves = new ArrayList<>();
        if (piece == null) return moves;
        int x = piece.x, y = piece.y;

        switch (piece.type) {
            case GENERAL: {
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (inPalace(nx, ny, piece.side) && (board[ny][nx] == null || board[ny][nx].side != piece.side))
                        moves.add(new int[]{nx, ny});
                }
                Piece enemyGen = findGeneral(piece.side == Side.RED ? Side.BLACK : Side.RED);
                if (enemyGen != null && enemyGen.x == x) {
                    boolean blocked = false;
                    for (int ty = Math.min(y, enemyGen.y) + 1; ty < Math.max(y, enemyGen.y); ty++) {
                        if (board[ty][x] != null) { blocked = true; break; }
                    }
                    if (!blocked) moves.add(new int[]{enemyGen.x, enemyGen.y});
                }
                break;
            }
            case ADVISOR: {
                int[][] dirs = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (inPalace(nx, ny, piece.side) && (board[ny][nx] == null || board[ny][nx].side != piece.side))
                        moves.add(new int[]{nx, ny});
                }
                break;
            }
            case ELEPHANT: {
                int[][] dirs = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
                int[][] eyes = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int i = 0; i < dirs.length; i++) {
                    int nx = x + dirs[i][0], ny = y + dirs[i][1];
                    if (isValidPosition(nx, ny)) {
                        if (piece.side == Side.RED && ny < 5) continue;
                        if (piece.side == Side.BLACK && ny > 4) continue;
                        if (board[y + eyes[i][1]][x + eyes[i][0]] == null
                                && (board[ny][nx] == null || board[ny][nx].side != piece.side))
                            moves.add(new int[]{nx, ny});
                    }
                }
                break;
            }
            case HORSE: {
                int[][] dirs = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};
                int[][] legs = {{1, 0}, {1, 0}, {-1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 1}, {0, -1}};
                for (int i = 0; i < dirs.length; i++) {
                    int nx = x + dirs[i][0], ny = y + dirs[i][1];
                    if (isValidPosition(nx, ny) && board[y + legs[i][1]][x + legs[i][0]] == null
                            && (board[ny][nx] == null || board[ny][nx].side != piece.side))
                        moves.add(new int[]{nx, ny});
                }
                break;
            }
            case CHARIOT: {
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    while (isValidPosition(nx, ny)) {
                        if (board[ny][nx] == null) {
                            moves.add(new int[]{nx, ny});
                        } else {
                            if (board[ny][nx].side != piece.side) moves.add(new int[]{nx, ny});
                            break;
                        }
                        nx += d[0]; ny += d[1];
                    }
                }
                break;
            }
            case CANNON: {
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    boolean jumped = false;
                    while (isValidPosition(nx, ny)) {
                        if (board[ny][nx] == null) {
                            if (!jumped) moves.add(new int[]{nx, ny});
                        } else {
                            if (!jumped) jumped = true;
                            else {
                                if (board[ny][nx].side != piece.side) moves.add(new int[]{nx, ny});
                                break;
                            }
                        }
                        nx += d[0]; ny += d[1];
                    }
                }
                break;
            }
            case SOLDIER: {
                if (piece.side == Side.RED) {
                    moves.add(new int[]{x, y - 1});
                    if (y < 5) {
                        moves.add(new int[]{x + 1, y});
                        moves.add(new int[]{x - 1, y});
                    }
                } else {
                    moves.add(new int[]{x, y + 1});
                    if (y > 4) {
                        moves.add(new int[]{x + 1, y});
                        moves.add(new int[]{x - 1, y});
                    }
                }
                List<int[]> filtered = new ArrayList<>();
                for (int[] m : moves) {
                    if (isValidPosition(m[0], m[1])
                            && (board[m[1]][m[0]] == null || board[m[1]][m[0]].side != piece.side))
                        filtered.add(m);
                }
                return filtered;
            }
        }
        return moves;
    }

    private Piece findGeneral(Side side) {
        for (int y = 0; y < ROWS; y++)
            for (int x = 0; x < COLS; x++)
                if (board[y][x] != null && board[y][x].type == PieceType.GENERAL && board[y][x].side == side)
                    return board[y][x];
        return null;
    }

    public MoveRecord makeMoveSafe(int fx, int fy, int tx, int ty) {
        Piece piece = board[fy][fx];
        if (piece == null) return null;
        Piece captured = board[ty][tx];
        MoveRecord record = new MoveRecord(fx, fy, tx, ty, piece, captured);
        board[ty][tx] = piece;
        board[fy][fx] = null;
        piece.x = tx;
        piece.y = ty;
        return record;
    }

    public void undoMove(MoveRecord record) {
        if (record == null || record.piece == null) return;
        board[record.toY][record.toX] = record.captured;
        board[record.fromY][record.fromX] = record.piece;
        record.piece.x = record.fromX;
        record.piece.y = record.fromY;
    }

    public int undoLastMoves(int count) {
        int undoCount = Math.min(count, moveHistory.size() / 2);
        for (int i = 0; i < undoCount; i++) {
            if (moveHistory.size() >= 2) {
                MoveRecord aiR = moveHistory.remove(moveHistory.size() - 1);
                undoMove(aiR);
                MoveRecord playerR = moveHistory.remove(moveHistory.size() - 1);
                undoMove(playerR);
            }
        }
        currentSide = Side.RED;
        gameOver = false;
        winner = null;
        return undoCount;
    }

    public boolean isInCheck(Side side) {
        Piece general = findGeneral(side);
        if (general == null) return true;
        Side enemy = side == Side.RED ? Side.BLACK : Side.RED;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Piece p = board[y][x];
                if (p != null && p.side == enemy) {
                    for (int[] m : getMoves(p))
                        if (m[0] == general.x && m[1] == general.y) return true;
                }
            }
        }
        return false;
    }

    public boolean hasLegalMoves(Side side) {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Piece p = board[y][x];
                if (p != null && p.side == side) {
                    for (int[] m : getMoves(p)) {
                        MoveRecord r = makeMoveSafe(x, y, m[0], m[1]);
                        if (!isInCheck(side)) { undoMove(r); return true; }
                        undoMove(r);
                    }
                }
            }
        }
        return false;
    }

    public void switchSide() {
        currentSide = currentSide == Side.RED ? Side.BLACK : Side.RED;
    }

    public void checkGameOver() {
        if (isInCheck(currentSide)) {
            if (!hasLegalMoves(currentSide)) {
                gameOver = true;
                winner = currentSide == Side.RED ? Side.BLACK : Side.RED;
            }
        } else if (!hasLegalMoves(currentSide)) {
            gameOver = true;
            winner = currentSide == Side.RED ? Side.BLACK : Side.RED;
        }
    }

    public void setGameOver(Side winnerSide) {
        gameOver = true;
        winner = winnerSide;
    }

    public List<int[]> getAllMoves(Side side) {
        List<int[]> all = new ArrayList<>();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Piece p = board[y][x];
                if (p != null && p.side == side) {
                    for (int[] m : getMoves(p)) {
                        MoveRecord r = makeMoveSafe(x, y, m[0], m[1]);
                        if (!isInCheck(side)) all.add(new int[]{x, y, m[0], m[1]});
                        undoMove(r);
                    }
                }
            }
        }
        return all;
    }

    public List<int[]> getLegalMoves(int x, int y) {
        List<int[]> legal = new ArrayList<>();
        Piece piece = board[y][x];
        if (piece == null) return legal;
        for (int[] m : getMoves(piece)) {
            MoveRecord r = makeMoveSafe(x, y, m[0], m[1]);
            if (!isInCheck(piece.side)) legal.add(new int[]{m[0], m[1]});
            undoMove(r);
        }
        return legal;
    }

    public void reset() {
        board = new Piece[ROWS][COLS];
        currentSide = Side.RED;
        gameOver = false;
        winner = null;
        moveHistory.clear();
        initBoard();
    }

    /**
     * 深拷贝整个游戏状态——AI搜索时使用副本，避免污染View渲染的真实棋盘
     */
    public ChineseChessGame deepCopy() {
        ChineseChessGame copy = new ChineseChessGame();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                copy.board[y][x] = null;
            }
        }
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Piece orig = board[y][x];
                if (orig != null) {
                    Piece p = new Piece(orig.type, orig.side, orig.x, orig.y);
                    copy.board[y][x] = p;
                }
            }
        }
        copy.currentSide = this.currentSide;
        copy.gameOver = this.gameOver;
        copy.winner = this.winner;
        return copy;
    }
}
