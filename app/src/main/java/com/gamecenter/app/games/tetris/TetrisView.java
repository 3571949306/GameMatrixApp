package com.gamecenter.app.games.tetris;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.gamecenter.app.R;

public class TetrisView extends View {

    private TetrisGame game;
    private int cellSize;
    private float offsetX, offsetY;

    private Paint bgPaint;
    private Paint gridPaint;
    private Paint blockPaint;
    private Paint blockBorderPaint;
    private Paint textPaint;
    private Paint nextPiecePaint;

    public TetrisView(Context context) {
        super(context);
        init();
    }

    public TetrisView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint();
        bgPaint.setColor(Color.rgb(30, 30, 50));
        bgPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint();
        gridPaint.setColor(Color.rgb(50, 50, 80));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        blockPaint = new Paint();
        blockPaint.setStyle(Paint.Style.FILL);
        blockPaint.setAntiAlias(true);

        blockBorderPaint = new Paint();
        blockBorderPaint.setStyle(Paint.Style.STROKE);
        blockBorderPaint.setAntiAlias(true);
        blockBorderPaint.setStrokeWidth(2f);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        nextPiecePaint = new Paint();
        nextPiecePaint.setStyle(Paint.Style.FILL);
        nextPiecePaint.setAntiAlias(true);
    }

    public void setGame(TetrisGame game) {
        this.game = game;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    private void recalcDimensions(int w, int h) {
        int boardWidth = w * 3 / 5;
        int boardHeight = h * 9 / 10;
        cellSize = Math.min(boardWidth / TetrisGame.COLS, boardHeight / TetrisGame.ROWS);
        offsetX = (w - cellSize * TetrisGame.COLS) / 2f;
        offsetY = (h - cellSize * TetrisGame.ROWS) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        if (game == null) return;

        drawBoard(canvas);
        drawCurrentPiece(canvas);
        drawNextPiece(canvas);
        drawScore(canvas);

        if (game.isGameOver()) {
            drawGameOver(canvas);
        }
    }

    private void drawBoard(Canvas canvas) {
        int[][] board = game.getBoard();

        for (int row = 0; row < TetrisGame.ROWS; row++) {
            for (int col = 0; col < TetrisGame.COLS; col++) {
                float left = offsetX + col * cellSize;
                float top = offsetY + row * cellSize;
                float right = left + cellSize;
                float bottom = top + cellSize;

                canvas.drawRect(left, top, right, bottom, gridPaint);

                if (board[row][col] >= 0) {
                    drawBlock(canvas, left, top, right, bottom, board[row][col]);
                }
            }
        }
    }

    private void drawCurrentPiece(Canvas canvas) {
        int[][] piece = game.getCurrentPiece();
        int colorIndex = game.getCurrentColor();
        int px = game.getCurrentX();
        int py = game.getCurrentY();

        for (int row = 0; row < piece.length; row++) {
            for (int col = 0; col < piece[row].length; col++) {
                if (piece[row][col] != 0) {
                    int x = px + col;
                    int y = py + row;
                    if (y >= 0) {
                        float left = offsetX + x * cellSize;
                        float top = offsetY + y * cellSize;
                        float right = left + cellSize;
                        float bottom = top + cellSize;
                        drawBlock(canvas, left, top, right, bottom, colorIndex);
                    }
                }
            }
        }
    }

    private void drawBlock(Canvas canvas, float left, float top, float right, float bottom, int colorIndex) {
        int[] color = TetrisGame.COLORS[colorIndex];
        blockPaint.setColor(Color.rgb(color[0], color[1], color[2]));
        blockBorderPaint.setColor(Color.rgb(
                Math.max(0, color[0] - 60),
                Math.max(0, color[1] - 60),
                Math.max(0, color[2] - 60)));

        RectF rect = new RectF(left + 1, top + 1, right - 1, bottom - 1);
        canvas.drawRoundRect(rect, 4, 4, blockPaint);
        canvas.drawRoundRect(rect, 4, 4, blockBorderPaint);
    }

    private void drawNextPiece(Canvas canvas) {
        int panelX = (int) (offsetX + TetrisGame.COLS * cellSize + 20);
        int panelY = (int) offsetY;

        Paint labelPaint = new Paint();
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(24);
        labelPaint.setAntiAlias(true);
        canvas.drawText("NEXT", panelX + 50, panelY + 30, labelPaint);

        int[][] nextPiece = TetrisGame.TETROMINOS[game.getNextPieceType()];
        int nextColor = game.getNextColor();
        int[] color = TetrisGame.COLORS[nextColor];

        nextPiecePaint.setColor(Color.rgb(color[0], color[1], color[2]));

        float blockSize = cellSize * 0.7f;
        int pieceWidth = nextPiece[0].length;
        int pieceHeight = nextPiece.length;

        float startX = panelX + (4 - pieceWidth) * blockSize / 2;
        float startY = panelY + 50;

        for (int row = 0; row < pieceHeight; row++) {
            for (int col = 0; col < pieceWidth; col++) {
                if (nextPiece[row][col] != 0) {
                    float left = startX + col * blockSize;
                    float top = startY + row * blockSize;
                    RectF rect = new RectF(left, top, left + blockSize - 2, top + blockSize - 2);
                    canvas.drawRoundRect(rect, 3, 3, nextPiecePaint);
                }
            }
        }

        Paint scoreLabel = new Paint();
        scoreLabel.setColor(Color.WHITE);
        scoreLabel.setTextSize(24);
        scoreLabel.setAntiAlias(true);
        canvas.drawText("SCORE", panelX + 50, panelY + 180, scoreLabel);

        Paint scorePaint = new Paint();
        scorePaint.setColor(Color.rgb(255, 215, 0));
        scorePaint.setTextSize(32);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setAntiAlias(true);
        canvas.drawText(String.valueOf(game.getScore()), panelX + 50, panelY + 220, scorePaint);

        Paint levelPaint = new Paint();
        levelPaint.setColor(Color.WHITE);
        levelPaint.setTextSize(24);
        levelPaint.setAntiAlias(true);
        canvas.drawText("LEVEL", panelX + 50, panelY + 270, levelPaint);

        Paint levelNumPaint = new Paint();
        levelNumPaint.setColor(Color.rgb(0, 255, 255));
        levelNumPaint.setTextSize(32);
        levelNumPaint.setTextAlign(Paint.Align.CENTER);
        levelNumPaint.setAntiAlias(true);
        canvas.drawText(String.valueOf(game.getLevel()), panelX + 50, panelY + 310, levelNumPaint);
    }

    private void drawScore(Canvas canvas) {
    }

    private void drawGameOver(Canvas canvas) {
        Paint overlay = new Paint();
        overlay.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlay);

        canvas.drawText("GAME OVER", getWidth() / 2f, getHeight() / 2f - 30, textPaint);

        Paint restartPaint = new Paint();
        restartPaint.setColor(Color.WHITE);
        restartPaint.setTextSize(24);
        restartPaint.setTextAlign(Paint.Align.CENTER);
        restartPaint.setAntiAlias(true);
        canvas.drawText("Tap to restart", getWidth() / 2f, getHeight() / 2f + 20, restartPaint);
    }
}