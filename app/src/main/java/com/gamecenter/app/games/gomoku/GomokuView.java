package com.gamecenter.app.games.gomoku;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.gamecenter.app.R;

public class GomokuView extends View {

    private GomokuGame game;
    private int cellSize;
    private int boardPadding;
    private float offsetX, offsetY;
    private int[] hoverPos;
    private OnCellClickListener onCellClickListener;
    private OnControlActionListener onControlActionListener;
    private OnGameOverListener gameOverListener;

    private Paint bgPaint, linePaint, blackPiecePaint, whitePiecePaint;
    private Paint blackPieceBorderPaint, whitePieceBorderPaint;
    private Paint lastMovePaint, hoverPaint, starPointPaint, textPaint;

    private int highlightEdgeColor;
    private static final int[][] STAR_POINTS = {{3, 3}, {3, 11}, {7, 7}, {11, 3}, {11, 11}};

    public GomokuView(Context context) {
        super(context);
        init();
    }

    public GomokuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        cellSize = 0;
        boardPadding = 20;

        Resources res = getResources();
        int bg = res.getColor(R.color.gomoku_bg, null);
        int line = res.getColor(R.color.gomoku_line, null);
        int blackP = res.getColor(R.color.gomoku_black_piece, null);
        int whiteP = res.getColor(R.color.gomoku_white_piece, null);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bg);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(line);
        linePaint.setStrokeWidth(1.5f);

        blackPiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPiecePaint.setColor(blackP);

        blackPieceBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPieceBorderPaint.setStyle(Paint.Style.STROKE);
        blackPieceBorderPaint.setColor(Color.rgb(60, 60, 60));
        blackPieceBorderPaint.setStrokeWidth(1);

        whitePiecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePiecePaint.setColor(whiteP);

        whitePieceBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePieceBorderPaint.setStyle(Paint.Style.STROKE);
        whitePieceBorderPaint.setColor(Color.rgb(180, 180, 180));
        whitePieceBorderPaint.setStrokeWidth(1);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(Color.rgb(255, 50, 50));

        hoverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hoverPaint.setColor(Color.argb(100, 200, 200, 200));
        hoverPaint.setStyle(Paint.Style.STROKE);
        hoverPaint.setStrokeWidth(1);

        starPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPointPaint.setColor(line);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(line);
        textPaint.setTextSize(28);
        textPaint.setTextAlign(Paint.Align.CENTER);

        highlightEdgeColor = Color.rgb(255, 50, 50);

        hoverPos = null;
    }

    public void setGame(GomokuGame game) {
        this.game = game;
        invalidate();
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    public void setOnControlActionListener(OnControlActionListener listener) {
        this.onControlActionListener = listener;
    }

    public void clearHover() {
        hoverPos = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    private void recalcDimensions(int w, int h) {
        int usableWidth = w - boardPadding * 2;
        int usableHeight = h - boardPadding * 2;
        cellSize = Math.min(usableWidth, usableHeight) / (GomokuGame.BOARD_SIZE - 1);
        int totalWidth = cellSize * (GomokuGame.BOARD_SIZE - 1);
        int totalHeight = cellSize * (GomokuGame.BOARD_SIZE - 1);
        offsetX = (w - totalWidth) / 2f;
        offsetY = (h - totalHeight) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize == 0) {
            recalcDimensions(getWidth(), getHeight());
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        drawBoard(canvas);
        if (game != null) {
            drawPieces(canvas);
            drawGameInfo(canvas);
        }
    }

    private void drawGameInfo(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        textPaint.setTextSize(24);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);

        int currentTurn = (game.getMoveCount() + 1) / 2;
        String turnText = "第 " + currentTurn + " 回合";
        canvas.drawText(turnText, 20, 40, textPaint);

        int currentPlayer = game.getCurrentPlayer();
        String playerText = currentPlayer == GomokuGame.BLACK ? "黑方回合" : "白方回合";
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(playerText, w - 20, 40, textPaint);

        if (game.isGameOver()) {
            Paint overlayPaint = new Paint();
            overlayPaint.setColor(Color.argb(180, 0, 0, 0));
            canvas.drawRect(0, 0, w, h, overlayPaint);

            textPaint.setTextSize(48);
            textPaint.setTextAlign(Paint.Align.CENTER);

            Integer winner = game.getWinner();
            String resultText;
            if (winner == null) {
                resultText = "平局!";
            } else if (winner == GomokuGame.BLACK) {
                resultText = "黑方胜利!";
            } else {
                resultText = "白方胜利!";
            }

            textPaint.setColor(Color.WHITE);
            canvas.drawText(resultText, w / 2f, h / 2f - 30, textPaint);

            textPaint.setTextSize(24);
            canvas.drawText("最终回合数: " + game.getMoveCount(), w / 2f, h / 2f + 30, textPaint);

            if (gameOverListener != null) {
                gameOverListener.onGameOver(winner);
            }
        }
    }

    private void drawBoard(Canvas canvas) {
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            float x1 = offsetX;
            float y1 = offsetY + i * cellSize;
            float x2 = offsetX + (GomokuGame.BOARD_SIZE - 1) * cellSize;
            float y2 = y1;
            canvas.drawLine(x1, y1, x2, y2, linePaint);

            x1 = offsetX + i * cellSize;
            y1 = offsetY;
            x2 = x1;
            y2 = offsetY + (GomokuGame.BOARD_SIZE - 1) * cellSize;
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }

        for (int[] sp : STAR_POINTS) {
            float cx = offsetX + sp[0] * cellSize;
            float cy = offsetY + sp[1] * cellSize;
            canvas.drawCircle(cx, cy, 4, starPointPaint);
        }
    }

    private void drawPieces(Canvas canvas) {
        int[][] board = game.getBoard();
        for (int y = 0; y < GomokuGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GomokuGame.BOARD_SIZE; x++) {
                if (board[y][x] != GomokuGame.EMPTY) {
                    float cx = offsetX + x * cellSize;
                    float cy = offsetY + y * cellSize;
                    float radius = cellSize / 2f - 2;

                    if (board[y][x] == GomokuGame.BLACK) {
                        drawPiece3D(canvas, cx, cy, radius, blackPiecePaint, blackPieceBorderPaint, true);
                    } else {
                        drawPiece3D(canvas, cx, cy, radius, whitePiecePaint, whitePieceBorderPaint, false);
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

        if (hoverPos != null && !game.isGameOver()) {
            int hx = hoverPos[0], hy = hoverPos[1];
            if (game.isValidMove(hx, hy)) {
                float cx = offsetX + hx * cellSize;
                float cy = offsetY + hy * cellSize;
                float r = cellSize / 2f - 2;
                canvas.drawCircle(cx, cy, r, hoverPaint);
            }
        }
    }

    private void drawPiece3D(Canvas canvas, float cx, float cy, float radius, Paint fill, Paint border, boolean isBlack) {
        int baseColor = isBlack ? Color.rgb(20, 20, 20) : Color.rgb(240, 240, 240);
        int highlightColor = isBlack ? Color.rgb(80, 80, 80) : Color.rgb(255, 255, 255);
        RadialGradient gradient = new RadialGradient(cx - radius * 0.3f, cy - radius * 0.3f, radius,
                new int[]{highlightColor, baseColor}, null, Shader.TileMode.CLAMP);
        Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradPaint.setShader(gradient);
        canvas.drawCircle(cx, cy, radius, gradPaint);
        canvas.drawCircle(cx, cy, radius, border);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null || game.isGameOver()) return true;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = Math.round((event.getX() - offsetX) / cellSize);
            int y = Math.round((event.getY() - offsetY) / cellSize);
            if (x >= 0 && x < GomokuGame.BOARD_SIZE && y >= 0 && y < GomokuGame.BOARD_SIZE) {
                if (onCellClickListener != null) {
                    onCellClickListener.onCellClick(x, y);
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            int hx = Math.round((event.getX() - offsetX) / cellSize);
            int hy = Math.round((event.getY() - offsetY) / cellSize);
            if (hx >= 0 && hx < GomokuGame.BOARD_SIZE && hy >= 0 && hy < GomokuGame.BOARD_SIZE) {
                hoverPos = new int[]{hx, hy};
            } else {
                hoverPos = null;
            }
            invalidate();
        }
        return true;
    }

    public interface OnCellClickListener {
        void onCellClick(int x, int y);
    }

    public interface OnControlActionListener {
        void onUndo();
        void onRestart();
    }

    public interface OnGameOverListener {
        void onGameOver(Integer winner);
    }

    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }
}
