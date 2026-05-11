package com.gamecenter.app.games.checkers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CheckersView extends View {

    private static final int SIZE = 8;
    private static final int DARK = 0;
    private static final int LIGHT = 1;
    private static final int DARK_KING = 2;
    private static final int LIGHT_KING = 3;

    private Paint darkPaint;
    private Paint lightPaint;
    private Paint darkPiecePaint;
    private Paint lightPiecePaint;
    private Paint selectedPaint;
    private Paint validMovePaint;

    private int[][] board;
    private int currentPlayer;
    private int selectedX = -1, selectedY = -1;
    private List<int[]> validMoves;
    private OnGameStateListener listener;

    public interface OnGameStateListener {
        void onPlayerChanged(int player);
        void onGameOver(int winner);
    }

    public CheckersView(Context context) {
        super(context);
        init();
    }

    public CheckersView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPaint.setColor(0xFF5D4037);
        darkPaint.setStyle(Paint.Style.FILL);

        lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lightPaint.setColor(0xFFBCAAA4);
        lightPaint.setStyle(Paint.Style.FILL);

        darkPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPiecePaint.setColor(0xFF212121);
        darkPiecePaint.setStyle(Paint.Style.FILL);

        lightPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lightPiecePaint.setColor(0xFFFFCDD2);
        lightPiecePaint.setStyle(Paint.Style.FILL);

        selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectedPaint.setColor(0x804CAF50);
        selectedPaint.setStyle(Paint.Style.FILL);

        validMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        validMovePaint.setColor(0x8000FF00);
        validMovePaint.setStyle(Paint.Style.FILL);

        board = new int[SIZE][SIZE];
        validMoves = new ArrayList<>();
        initBoard();
    }

    private void initBoard() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    if (r < 3) board[r][c] = LIGHT;
                    else if (r > 4) board[r][c] = DARK;
                    else board[r][c] = -1;
                } else {
                    board[r][c] = -1;
                }
            }
        }
        currentPlayer = DARK;
    }

    public void setOnGameStateListener(OnGameStateListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cellSize = Math.min(getWidth(), getHeight()) / (float) SIZE;

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                float left = c * cellSize;
                float top = r * cellSize;
                float right = left + cellSize;
                float bottom = top + cellSize;

                Paint paint = ((r + c) % 2 == 0) ? lightPaint : darkPaint;
                canvas.drawRect(left, top, right, bottom, paint);

                if (r == selectedY && c == selectedX) {
                    canvas.drawRect(left, top, right, bottom, selectedPaint);
                }

                for (int[] move : validMoves) {
                    if (move[0] == r && move[1] == c) {
                        canvas.drawCircle(left + cellSize / 2, top + cellSize / 2, cellSize / 4, validMovePaint);
                    }
                }
            }
        }

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int piece = board[r][c];
                if (piece >= 0) {
                    float cx = c * cellSize + cellSize / 2;
                    float cy = r * cellSize + cellSize / 2;
                    float radius = cellSize * 0.35f;

                    Paint piecePaint = (piece == DARK || piece == DARK_KING) ? darkPiecePaint : lightPiecePaint;
                    canvas.drawCircle(cx, cy, radius, piecePaint);

                    if (piece == DARK_KING || piece == LIGHT_KING) {
                        Paint crownPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        crownPaint.setColor(0xFFFFD700);
                        crownPaint.setTextSize(cellSize * 0.3f);
                        crownPaint.setTextAlign(Paint.Align.CENTER);
                        canvas.drawText("♔", cx, cy + cellSize * 0.1f, crownPaint);
                    }
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float cellSize = Math.min(getWidth(), getHeight()) / (float) SIZE;
            int c = (int) (event.getX() / cellSize);
            int r = (int) (event.getY() / cellSize);

            if (c >= 0 && c < SIZE && r >= 0 && r < SIZE) {
                if (selectedX >= 0 && selectedY >= 0) {
                    for (int[] move : validMoves) {
                        if (move[0] == r && move[1] == c) {
                            makeMove(selectedX, selectedY, c, r, move.length > 2 && move[2] == 1);
                            selectedX = selectedY = -1;
                            validMoves.clear();
                            invalidate();
                            return true;
                        }
                    }
                }

                if (isValidPiece(r, c)) {
                    selectedX = c;
                    selectedY = r;
                    calculateValidMoves(c, r);
                    invalidate();
                } else {
                    selectedX = selectedY = -1;
                    validMoves.clear();
                    invalidate();
                }
            }
        }
        return true;
    }

    private boolean isValidPiece(int r, int c) {
        int piece = board[r][c];
        if (currentPlayer == DARK && (piece == DARK || piece == DARK_KING)) return true;
        if (currentPlayer == LIGHT && (piece == LIGHT || piece == LIGHT_KING)) return true;
        return false;
    }

    private void calculateValidMoves(int c, int r) {
        validMoves.clear();
        int piece = board[r][c];
        boolean isKing = (piece == DARK_KING || piece == LIGHT_KING);
        boolean isDark = (piece == DARK || piece == DARK_KING);

        int[][] dirs = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        if (isKing) {
            dirs = new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        } else if (isDark) {
            dirs = new int[][]{{-1, -1}, {-1, 1}};
        } else {
            dirs = new int[][]{{1, -1}, {1, 1}};
        }

        for (int[] dir : dirs) {
            int nr = r + dir[0];
            int nc = c + dir[1];
            if (nc >= 0 && nc < SIZE && nr >= 0 && nr < SIZE) {
                if (board[nr][nc] == -1) {
                    validMoves.add(new int[]{nr, nc});
                } else {
                    int captured = board[nr][nc];
                    boolean isEnemy = (currentPlayer == DARK && (captured == LIGHT || captured == LIGHT_KING)) ||
                                     (currentPlayer == LIGHT && (captured == DARK || captured == DARK_KING));
                    if (isEnemy) {
                        int jr = nr + dir[0];
                        int jc = nc + dir[1];
                        if (jc >= 0 && jc < SIZE && jr >= 0 && jr < SIZE && board[jr][jc] == -1) {
                            validMoves.add(new int[]{jr, jc, 1});
                        }
                    }
                }
            }
        }
    }

    private void makeMove(int fromC, int fromR, int toC, int toR, boolean jumped) {
        int piece = board[fromR][fromC];
        board[toR][toC] = piece;
        board[fromR][fromC] = -1;

        if (jumped) {
            int jr = (fromR + toR) / 2;
            int jc = (fromC + toC) / 2;
            board[jr][jc] = -1;
        }

        if ((piece == DARK && toR == 0) || (piece == LIGHT && toR == SIZE - 1)) {
            board[toR][toC] = (piece == DARK) ? DARK_KING : LIGHT_KING;
        }

        if (!jumped || !canJump(toC, toR)) {
            currentPlayer = 1 - currentPlayer;
            if (listener != null) listener.onPlayerChanged(currentPlayer);
        }

        checkGameOver();
    }

    private boolean canJump(int c, int r) {
        int piece = board[r][c];
        boolean isKing = (piece == DARK_KING || piece == LIGHT_KING);
        boolean isDark = (piece == DARK || piece == DARK_KING);

        int[][] dirs;
        if (isKing) {
            dirs = new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        } else if (isDark) {
            dirs = new int[][]{{-1, -1}, {-1, 1}};
        } else {
            dirs = new int[][]{{1, -1}, {1, 1}};
        }

        for (int[] dir : dirs) {
            int nr = r + dir[0];
            int nc = c + dir[1];
            if (nc >= 0 && nc < SIZE && nr >= 0 && nr < SIZE) {
                int captured = board[nr][nc];
                boolean isEnemy = (isDark && (captured == LIGHT || captured == LIGHT_KING)) ||
                                 (!isDark && (captured == DARK || captured == DARK_KING));
                if (isEnemy) {
                    int jr = nr + dir[0];
                    int jc = nc + dir[1];
                    if (jc >= 0 && jc < SIZE && jr >= 0 && jr < SIZE && board[jr][jc] == -1) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void checkGameOver() {
        boolean darkHasPiece = false;
        boolean lightHasPiece = false;
        boolean darkCanMove = false;
        boolean lightCanMove = false;

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int piece = board[r][c];
                if (piece == DARK || piece == DARK_KING) {
                    darkHasPiece = true;
                    selectedX = c;
                    selectedY = r;
                    calculateValidMoves(c, r);
                    if (!validMoves.isEmpty()) darkCanMove = true;
                } else if (piece == LIGHT || piece == LIGHT_KING) {
                    lightHasPiece = true;
                }
            }
        }

        validMoves.clear();
        selectedX = selectedY = -1;

        if (!darkHasPiece) {
            if (listener != null) listener.onGameOver(LIGHT);
        } else if (!lightHasPiece) {
            if (listener != null) listener.onGameOver(DARK);
        } else if (!darkCanMove && currentPlayer == DARK) {
            if (listener != null) listener.onGameOver(LIGHT);
        } else if (!lightCanMove && currentPlayer == LIGHT) {
            if (listener != null) listener.onGameOver(DARK);
        }
    }

    public void reset() {
        initBoard();
        if (listener != null) listener.onPlayerChanged(currentPlayer);
        invalidate();
    }
}