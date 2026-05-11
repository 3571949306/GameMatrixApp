package com.gamecenter.app.games.tic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class TicView extends View {

    public interface OnGameOverListener {
        void onGameOver(int winner);
    }

    private TicGame game;
    private Paint gridPaint;
    private Paint xPaint;
    private Paint oPaint;
    private Paint textPaint;
    private OnGameOverListener gameOverListener;

    private float viewWidth;
    private float viewHeight;
    private float cellSize;
    private float offsetX;
    private float offsetY;

    public TicView(Context context) {
        super(context);
        init();
    }

    public TicView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.WHITE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(8);
        gridPaint.setStrokeCap(Paint.Cap.ROUND);

        xPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xPaint.setColor(Color.parseColor("#2196F3"));
        xPaint.setStyle(Paint.Style.STROKE);
        xPaint.setStrokeWidth(10);
        xPaint.setStrokeCap(Paint.Cap.ROUND);

        oPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        oPaint.setColor(Color.parseColor("#E53935"));
        oPaint.setStyle(Paint.Style.STROKE);
        oPaint.setStrokeWidth(10);
        oPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setGame(TicGame game) {
        this.game = game;
    }

    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        float size = Math.min(w, h) * 0.85f;
        cellSize = size / 3;
        offsetX = (w - size) / 2;
        offsetY = (h - size) / 2 - cellSize * 0.1f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#1E1E32"));

        for (int i = 1; i < 3; i++) {
            float x = offsetX + i * cellSize;
            canvas.drawLine(x, offsetY, x, offsetY + cellSize * 3, gridPaint);
            float y = offsetY + i * cellSize;
            canvas.drawLine(offsetX, y, offsetX + cellSize * 3, y, gridPaint);
        }

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(12);
        borderPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawRect(offsetX, offsetY, offsetX + cellSize * 3, offsetY + cellSize * 3, borderPaint);

        int[][] board = game.getBoard();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                float cx = offsetX + x * cellSize + cellSize / 2;
                float cy = offsetY + y * cellSize + cellSize / 2;
                float padding = cellSize * 0.25f;

                if (board[y][x] == TicGame.PLAYER) {
                    canvas.drawLine(cx - padding, cy - padding, cx + padding, cy + padding, xPaint);
                    canvas.drawLine(cx + padding, cy - padding, cx - padding, cy + padding, xPaint);
                } else if (board[y][x] == TicGame.COMPUTER) {
                    canvas.drawCircle(cx, cy, padding, oPaint);
                }
            }
        }

        if (game.isGameOver()) {
            String msg;
            int winner = game.getWinner();
            if (winner == TicGame.PLAYER) {
                msg = "你赢了!";
                textPaint.setColor(Color.parseColor("#4CAF50"));
            } else if (winner == TicGame.COMPUTER) {
                msg = "电脑赢了!";
                textPaint.setColor(Color.parseColor("#E53935"));
            } else {
                msg = "平局!";
                textPaint.setColor(Color.parseColor("#FF9800"));
            }
            canvas.drawText(msg, viewWidth / 2, offsetY + cellSize * 3 + cellSize * 0.6f, textPaint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(30);
            canvas.drawText("点击重玩", viewWidth / 2, offsetY + cellSize * 3 + cellSize * 0.9f, textPaint);
            if (gameOverListener != null) {
                gameOverListener.onGameOver(winner);
            }
        } else if (game.getCurrentTurn() == TicGame.COMPUTER) {
            textPaint.setColor(Color.WHITE);
            canvas.drawText("电脑思考中...", viewWidth / 2, offsetY + cellSize * 3 + cellSize * 0.6f, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (game == null) return true;

            if (game.isGameOver()) {
                game.reset();
                invalidate();
                performClick();
                return true;
            }

            if (game.getCurrentTurn() != TicGame.PLAYER) return true;

            float x = event.getX() - offsetX;
            float y = event.getY() - offsetY;
            int col = (int) (x / cellSize);
            int row = (int) (y / cellSize);

            if (col >= 0 && col < 3 && row >= 0 && row < 3) {
                if (game.placePiece(col, row)) {
                    invalidate();
                    postDelayed(() -> {
                        game.computerMove();
                        invalidate();
                    }, 400);
                }
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
