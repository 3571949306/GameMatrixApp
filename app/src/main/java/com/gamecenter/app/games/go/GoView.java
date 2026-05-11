package com.gamecenter.app.games.go;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class GoView extends View {

    private GoGame game;
    private int cellSize;
    private float offsetX, offsetY;
    private OnCellClickListener onCellClickListener;

    private Paint bgPaint;
    private Paint boardPaint;
    private Paint linePaint;
    private Paint blackPaint;
    private Paint whitePaint;
    private Paint blackBorderPaint;
    private Paint whiteBorderPaint;
    private Paint lastMovePaint;
    private Paint starPaint;
    private Paint infoPaint;

    public GoView(Context context) {
        super(context);
        init();
    }

    public GoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.rgb(220, 179, 92));

        boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardPaint.setColor(Color.rgb(220, 179, 92));

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(1.5f);

        blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(Color.BLACK);

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);

        blackBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackBorderPaint.setColor(Color.rgb(50, 50, 50));
        blackBorderPaint.setStyle(Paint.Style.STROKE);
        blackBorderPaint.setStrokeWidth(1.5f);

        whiteBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whiteBorderPaint.setColor(Color.rgb(150, 150, 150));
        whiteBorderPaint.setStyle(Paint.Style.STROKE);
        whiteBorderPaint.setStrokeWidth(1.5f);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(Color.rgb(255, 50, 50));

        starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(Color.BLACK);

        infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        infoPaint.setColor(Color.BLACK);
        infoPaint.setTextSize(26);
        infoPaint.setFakeBoldText(true);
    }

    public void setGame(GoGame game) {
        this.game = game;
        invalidate();
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int padding = 40;
        int usableW = w - padding * 2;
        int usableH = (int) (h * 0.85f) - padding * 2;
        cellSize = Math.min(usableW, usableH) / (GoGame.BOARD_SIZE - 1);
        float boardW = cellSize * (GoGame.BOARD_SIZE - 1);
        float boardH = cellSize * (GoGame.BOARD_SIZE - 1);
        offsetX = (w - boardW) / 2f;
        offsetY = padding + (usableH - boardH) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize == 0) {
            onSizeChanged(getWidth(), getHeight(), 0, 0);
        }
        canvas.drawColor(Color.rgb(220, 179, 92));
        drawBoard(canvas);
        if (game != null) {
            drawStones(canvas);
            drawInfo(canvas);
        }
    }

    private void drawBoard(Canvas canvas) {
        for (int i = 0; i < GoGame.BOARD_SIZE; i++) {
            float x1 = offsetX;
            float y1 = offsetY + i * cellSize;
            float x2 = offsetX + (GoGame.BOARD_SIZE - 1) * cellSize;
            float y2 = y1;
            canvas.drawLine(x1, y1, x2, y2, linePaint);

            x1 = offsetX + i * cellSize;
            y1 = offsetY;
            x2 = x1;
            y2 = offsetY + (GoGame.BOARD_SIZE - 1) * cellSize;
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }

        int[][] starPoints = {{2,2},{6,2},{2,6},{6,6},{4,4}};
        for (int[] sp : starPoints) {
            float cx = offsetX + sp[0] * cellSize;
            float cy = offsetY + sp[1] * cellSize;
            canvas.drawCircle(cx, cy, 4, starPaint);
        }
    }

    private void drawStones(Canvas canvas) {
        int[][] board = game.getBoard();
        for (int y = 0; y < GoGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GoGame.BOARD_SIZE; x++) {
                if (board[y][x] != GoGame.EMPTY) {
                    float cx = offsetX + x * cellSize;
                    float cy = offsetY + y * cellSize;
                    float r = cellSize / 2f - 2;

                    if (board[y][x] == GoGame.BLACK) {
                        canvas.drawCircle(cx, cy, r, blackPaint);
                        canvas.drawCircle(cx, cy, r, blackBorderPaint);
                    } else {
                        canvas.drawCircle(cx, cy, r, whitePaint);
                        canvas.drawCircle(cx, cy, r, whiteBorderPaint);
                    }
                }
            }
        }

        int[] lastMove = game.getLastMove();
        if (lastMove != null) {
            float cx = offsetX + lastMove[0] * cellSize;
            float cy = offsetY + lastMove[1] * cellSize;
            canvas.drawCircle(cx, cy, 5, lastMovePaint);
        }
    }

    private void drawInfo(Canvas canvas) {
        float y = offsetY + (GoGame.BOARD_SIZE - 1) * cellSize + 30;
        infoPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("黑方吃子: " + game.getBlackCaptures(), offsetX, y, infoPaint);
        infoPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("白方吃子: " + game.getWhiteCaptures(),
                offsetX + (GoGame.BOARD_SIZE - 1) * cellSize, y, infoPaint);

        if (game.isGameOver()) {
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.argb(180, 0, 0, 0));
            canvas.drawRect(0, 0, getWidth(), getHeight(), overPaint);

            overPaint.setColor(Color.WHITE);
            overPaint.setTextSize(42);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            canvas.drawText("对局结束", getWidth() / 2f, getHeight() / 2f - 20, overPaint);
            overPaint.setTextSize(28);
            canvas.drawText("黑吃" + game.getBlackCaptures() + "子  白吃" + game.getWhiteCaptures() + "子",
                    getWidth() / 2f, getHeight() / 2f + 30, overPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null || game.isGameOver()) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = Math.round((event.getX() - offsetX) / cellSize);
            int y = Math.round((event.getY() - offsetY) / cellSize);
            if (x >= 0 && x < GoGame.BOARD_SIZE && y >= 0 && y < GoGame.BOARD_SIZE) {
                if (onCellClickListener != null) {
                    onCellClickListener.onCellClick(x, y);
                }
            }
        }
        return true;
    }

    public interface OnCellClickListener {
        void onCellClick(int x, int y);
    }
}
