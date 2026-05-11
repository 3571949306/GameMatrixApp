package com.gamecenter.app.games.chinesechess;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import com.gamecenter.app.R;
import java.util.List;

public class ChineseChessView extends View {

    // ============ 回调接口 ============

    public interface OnCellClickListener {
        void onCellClick(int col, int row);
    }

    // ============ 成员变量 ============

    private ChineseChessGame game;
    private float cellSize;
    private float offsetX, offsetY;
    private int[] selectedPos;
    private List<int[]> validMoves;
    private OnCellClickListener onCellClickListener;
    private boolean isLocked = false;

    private Paint bgPaint;
    private Paint linePaint;
    private Paint pieceFillPaint;
    private Paint pieceStrokePaint;
    private Paint redTextPaint;
    private Paint blackTextPaint;
    private Paint selectPaint;
    private Paint glowPaint;
    private Paint validDotPaint;
    private Paint capturePaint;
    private Paint riverPaint;
    private Paint lastMovePaint;
    private Paint highlightPaint;

    private ValueAnimator moveAnimator;
    private int animFromX, animFromY, animToX, animToY;
    private float animCurrentX, animCurrentY;
    private ChineseChessGame.Piece animPiece;
    private boolean isAnimating = false;

    private int lastFromX = -1, lastFromY = -1, lastToX = -1, lastToY = -1;

    private float density;
    private int viewWidth, viewHeight;

    public ChineseChessView(Context context) {
        super(context);
        init();
    }

    public ChineseChessView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        density = metrics.density;

        Resources res = getResources();
        int boardBg = res.getColor(R.color.chess_bg, null);
        int lineColor = res.getColor(R.color.chess_line, null);
        int redColor = res.getColor(R.color.chess_red, null);
        int blackColor = res.getColor(R.color.chess_black, null);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(boardBg);
        bgPaint.setStyle(Paint.Style.FILL);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(lineColor);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dpToPx(1.5f));

        pieceFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pieceFillPaint.setColor(Color.rgb(255, 250, 235));
        pieceFillPaint.setStyle(Paint.Style.FILL);

        pieceStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pieceStrokePaint.setColor(lineColor);
        pieceStrokePaint.setStyle(Paint.Style.STROKE);
        pieceStrokePaint.setStrokeWidth(dpToPx(2.0f));

        redTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        redTextPaint.setColor(redColor);
        redTextPaint.setTextAlign(Paint.Align.CENTER);
        redTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        blackTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackTextPaint.setColor(blackColor);
        blackTextPaint.setTextAlign(Paint.Align.CENTER);
        blackTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectPaint.setColor(Color.rgb(255, 215, 0));
        selectPaint.setStyle(Paint.Style.FILL);
        selectPaint.setAlpha(120);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(Color.rgb(255, 200, 0));
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setAlpha(80);
        glowPaint.setMaskFilter(new BlurMaskFilter(dpToPx(8), BlurMaskFilter.Blur.NORMAL));

        highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setColor(Color.rgb(255, 180, 0));
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dpToPx(3.0f));

        validDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        validDotPaint.setColor(Color.rgb(50, 200, 50));
        validDotPaint.setStyle(Paint.Style.FILL);
        validDotPaint.setAlpha(180);

        capturePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        capturePaint.setColor(Color.rgb(220, 50, 50));
        capturePaint.setStyle(Paint.Style.STROKE);
        capturePaint.setStrokeWidth(dpToPx(3.0f));
        capturePaint.setAlpha(220);

        riverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        riverPaint.setColor(lineColor);
        riverPaint.setTextAlign(Paint.Align.CENTER);

        lastMovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMovePaint.setColor(Color.rgb(100, 150, 255));
        lastMovePaint.setStyle(Paint.Style.FILL);
        lastMovePaint.setAlpha(60);
    }

    private float dpToPx(float dp) {
        return dp * density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        computeBoardMetrics(w, h);
    }

    private void computeBoardMetrics(int w, int h) {
        cellSize = w / 9.0f;

        float boardWidth = 8 * cellSize;
        float boardHeight = 9 * cellSize;

        offsetX = (w - boardWidth) / 2.0f;
        offsetY = (h - boardHeight) / 2.0f;

        float textScale = cellSize / dpToPx(45);
        redTextPaint.setTextSize(cellSize * 0.50f * textScale);
        blackTextPaint.setTextSize(cellSize * 0.50f * textScale);
        riverPaint.setTextSize(cellSize * 0.35f * textScale);

        linePaint.setStrokeWidth(cellSize * 0.02f);
        pieceStrokePaint.setStrokeWidth(cellSize * 0.03f);
        highlightPaint.setStrokeWidth(cellSize * 0.04f);
        capturePaint.setStrokeWidth(cellSize * 0.04f);

        if (glowPaint.getMaskFilter() != null) {
            glowPaint.setMaskFilter(new BlurMaskFilter(cellSize * 0.15f, BlurMaskFilter.Blur.NORMAL));
        }
    }

    public void bindGame(ChineseChessGame game) {
        this.game = game;
        this.selectedPos = null;
        this.validMoves = null;
        this.isLocked = false;
        this.lastFromX = -1;
        this.lastFromY = -1;
        this.lastToX = -1;
        this.lastToY = -1;
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    public void setLocked(boolean locked) {
        this.isLocked = locked;
        dispatchDraw();
    }

    public void setSelected(int x, int y, List<int[]> moves) {
        this.selectedPos = new int[]{x, y};
        this.validMoves = moves;
        dispatchDraw();
    }

    public void clearSelected() {
        this.selectedPos = null;
        this.validMoves = null;
        dispatchDraw();
    }

    public void setLastMove(int fromX, int fromY, int toX, int toY) {
        this.lastFromX = fromX;
        this.lastFromY = fromY;
        this.lastToX = toX;
        this.lastToY = toY;
        dispatchDraw();
    }

    public void clearLastMove() {
        this.lastFromX = -1;
        this.lastFromY = -1;
        this.lastToX = -1;
        this.lastToY = -1;
        dispatchDraw();
    }

    private void dispatchDraw() {
        if (isAttachedToWindow()) {
            postInvalidateOnAnimation();
        }
    }

    public void animateMove(int fromX, int fromY, int toX, int toY, final Runnable onComplete) {
        if (game == null) return;

        ChineseChessGame.Piece piece = game.getBoard()[fromY][fromX];
        if (piece == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        isAnimating = true;
        animFromX = fromX;
        animFromY = fromY;
        animToX = toX;
        animToY = toY;
        animPiece = piece;

        float startX = offsetX + fromX * cellSize;
        float startY = offsetY + fromY * cellSize;
        float endX = offsetX + toX * cellSize;
        float endY = offsetY + toY * cellSize;

        moveAnimator = ValueAnimator.ofFloat(0f, 1f);
        moveAnimator.setDuration(300);
        moveAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            animCurrentX = startX + (endX - startX) * fraction;
            animCurrentY = startY + (endY - startY) * fraction;
            invalidate();
        });
        moveAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
                animPiece = null;
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
        moveAnimator.start();
    }

    public void cancelAnimation() {
        if (moveAnimator != null && moveAnimator.isRunning()) {
            moveAnimator.cancel();
        }
        isAnimating = false;
        animPiece = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        drawBoardLines(canvas);
        drawStarPoints(canvas);
        drawRiverText(canvas);
        drawLastMoveOverlay(canvas);
        drawPieces(canvas);
        drawAnimatingPiece(canvas);
        drawSelectionOverlay(canvas);
    }

    private void drawBoardLines(Canvas canvas) {
        for (int i = 0; i < ChineseChessGame.ROWS; i++) {
            float y = offsetY + i * cellSize;
            canvas.drawLine(offsetX, y, offsetX + 8 * cellSize, y, linePaint);
        }

        for (int i = 0; i < ChineseChessGame.COLS; i++) {
            float x = offsetX + i * cellSize;
            if (i == 0 || i == 8) {
                canvas.drawLine(x, offsetY, x, offsetY + 9 * cellSize, linePaint);
            } else {
                canvas.drawLine(x, offsetY, x, offsetY + 4 * cellSize, linePaint);
                canvas.drawLine(x, offsetY + 5 * cellSize, x, offsetY + 9 * cellSize, linePaint);
            }
        }

        drawPalaceDiagonals(canvas);
    }

    private void drawPalaceDiagonals(Canvas canvas) {
        canvas.drawLine(offsetX + 3 * cellSize, offsetY, offsetX + 5 * cellSize, offsetY + 2 * cellSize, linePaint);
        canvas.drawLine(offsetX + 5 * cellSize, offsetY, offsetX + 3 * cellSize, offsetY + 2 * cellSize, linePaint);
        canvas.drawLine(offsetX + 3 * cellSize, offsetY + 7 * cellSize, offsetX + 5 * cellSize, offsetY + 9 * cellSize, linePaint);
        canvas.drawLine(offsetX + 5 * cellSize, offsetY + 7 * cellSize, offsetX + 3 * cellSize, offsetY + 9 * cellSize, linePaint);
    }

    private void drawStarPoints(Canvas canvas) {
        int[][] starPositions = {
            {1, 2}, {7, 2},
            {0, 3}, {2, 3}, {4, 3}, {6, 3}, {8, 3},
            {0, 6}, {2, 6}, {4, 6}, {6, 6}, {8, 6},
            {1, 7}, {7, 7}
        };

        float starSize = cellSize * 0.08f;
        float gap = cellSize * 0.06f;

        Paint starPaint = new Paint(linePaint);
        starPaint.setStrokeWidth(cellSize * 0.02f);

        for (int[] pos : starPositions) {
            float cx = offsetX + pos[0] * cellSize;
            float cy = offsetY + pos[1] * cellSize;

            if (pos[0] > 0) {
                canvas.drawLine(cx - gap - starSize, cy - gap, cx - gap, cy - gap, starPaint);
                canvas.drawLine(cx - gap, cy - gap, cx - gap, cy - gap - starSize, starPaint);
                canvas.drawLine(cx - gap - starSize, cy + gap, cx - gap, cy + gap, starPaint);
                canvas.drawLine(cx - gap, cy + gap, cx - gap, cy + gap + starSize, starPaint);
            }

            if (pos[0] < 8) {
                canvas.drawLine(cx + gap + starSize, cy - gap, cx + gap, cy - gap, starPaint);
                canvas.drawLine(cx + gap, cy - gap, cx + gap, cy - gap - starSize, starPaint);
                canvas.drawLine(cx + gap + starSize, cy + gap, cx + gap, cy + gap, starPaint);
                canvas.drawLine(cx + gap, cy + gap, cx + gap, cy + gap + starSize, starPaint);
            }
        }
    }

    private void drawRiverText(Canvas canvas) {
        float centerX = offsetX + 4 * cellSize;
        float riverY = offsetY + 4.5f * cellSize + riverPaint.getTextSize() / 3;
        canvas.drawText("楚 河", centerX - cellSize * 1.5f, riverY, riverPaint);
        canvas.drawText("汉 界", centerX + cellSize * 1.5f, riverY, riverPaint);
    }

    private void drawLastMoveOverlay(Canvas canvas) {
        if (lastFromX < 0 || lastFromY < 0 || lastToX < 0 || lastToY < 0) return;

        float fromCx = offsetX + lastFromX * cellSize;
        float fromCy = offsetY + lastFromY * cellSize;
        float toCx = offsetX + lastToX * cellSize;
        float toCy = offsetY + lastToY * cellSize;

        float halfSize = cellSize * 0.46f;
        RectF fromRect = new RectF(fromCx - halfSize, fromCy - halfSize, fromCx + halfSize, fromCy + halfSize);
        RectF toRect = new RectF(toCx - halfSize, toCy - halfSize, toCx + halfSize, toCy + halfSize);

        canvas.drawRect(fromRect, lastMovePaint);
        canvas.drawRect(toRect, lastMovePaint);

        Paint cornerPaint = new Paint(lastMovePaint);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(cellSize * 0.025f);
        cornerPaint.setAlpha(180);

        float cornerLen = cellSize * 0.2f;

        Path fromPath = new Path();
        fromPath.moveTo(fromRect.left, fromRect.top + cornerLen);
        fromPath.lineTo(fromRect.left, fromRect.top);
        fromPath.lineTo(fromRect.left + cornerLen, fromRect.top);
        fromPath.moveTo(fromRect.right - cornerLen, fromRect.top);
        fromPath.lineTo(fromRect.right, fromRect.top);
        fromPath.lineTo(fromRect.right, fromRect.top + cornerLen);
        fromPath.moveTo(fromRect.right, fromRect.bottom - cornerLen);
        fromPath.lineTo(fromRect.right, fromRect.bottom);
        fromPath.lineTo(fromRect.right - cornerLen, fromRect.bottom);
        fromPath.moveTo(fromRect.left + cornerLen, fromRect.bottom);
        fromPath.lineTo(fromRect.left, fromRect.bottom);
        fromPath.lineTo(fromRect.left, fromRect.bottom - cornerLen);
        canvas.drawPath(fromPath, cornerPaint);

        Path toPath = new Path();
        toPath.moveTo(toRect.left, toRect.top + cornerLen);
        toPath.lineTo(toRect.left, toRect.top);
        toPath.lineTo(toRect.left + cornerLen, toRect.top);
        toPath.moveTo(toRect.right - cornerLen, toRect.top);
        toPath.lineTo(toRect.right, toRect.top);
        toPath.lineTo(toRect.right, toRect.top + cornerLen);
        toPath.moveTo(toRect.right, toRect.bottom - cornerLen);
        toPath.lineTo(toRect.right, toRect.bottom);
        toPath.lineTo(toRect.right - cornerLen, toRect.bottom);
        toPath.moveTo(toRect.left + cornerLen, toRect.bottom);
        toPath.lineTo(toRect.left, toRect.bottom);
        toPath.lineTo(toRect.left, toRect.bottom - cornerLen);
        canvas.drawPath(toPath, cornerPaint);
    }

    private void drawPieces(Canvas canvas) {
        if (game == null) return;

        ChineseChessGame.Piece[][] board = game.getBoard();
        for (int row = 0; row < ChineseChessGame.ROWS; row++) {
            for (int col = 0; col < ChineseChessGame.COLS; col++) {
                if (isAnimating && col == animFromX && row == animFromY) {
                    continue;
                }
                ChineseChessGame.Piece piece = board[row][col];
                if (piece != null) {
                    drawSinglePiece(canvas, col, row, piece);
                }
            }
        }
    }

    private void drawAnimatingPiece(Canvas canvas) {
        if (!isAnimating || animPiece == null) return;

        float cx = animCurrentX;
        float cy = animCurrentY;
        float radius = cellSize * 0.42f;

        canvas.drawCircle(cx, cy, radius, pieceFillPaint);
        canvas.drawCircle(cx, cy, radius, pieceStrokePaint);

        Paint textPaint = (animPiece.side == ChineseChessGame.Side.RED) ? redTextPaint : blackTextPaint;
        float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2.0f;
        canvas.drawText(animPiece.getName(), cx, textY, textPaint);
    }

    private void drawSinglePiece(Canvas canvas, int col, int row, ChineseChessGame.Piece piece) {
        float cx = offsetX + col * cellSize;
        float cy = offsetY + row * cellSize;
        float radius = cellSize * 0.42f;

        canvas.drawCircle(cx, cy, radius, pieceFillPaint);
        canvas.drawCircle(cx, cy, radius, pieceStrokePaint);

        Paint textPaint = (piece.side == ChineseChessGame.Side.RED) ? redTextPaint : blackTextPaint;
        float textY = cy - (textPaint.descent() + textPaint.ascent()) / 2.0f;
        canvas.drawText(piece.getName(), cx, textY, textPaint);
    }

    private void drawSelectionOverlay(Canvas canvas) {
        if (isLocked || selectedPos == null) return;

        float cx = offsetX + selectedPos[0] * cellSize;
        float cy = offsetY + selectedPos[1] * cellSize;
        float radius = cellSize * 0.46f;

        canvas.drawCircle(cx, cy, radius * 1.3f, glowPaint);
        canvas.drawCircle(cx, cy, radius, selectPaint);
        canvas.drawCircle(cx, cy, radius, highlightPaint);

        if (validMoves != null) {
            for (int[] mv : validMoves) {
                float mx = offsetX + mv[0] * cellSize;
                float my = offsetY + mv[1] * cellSize;
                if (game != null && game.getBoard()[mv[1]][mv[0]] != null) {
                    canvas.drawCircle(mx, my, cellSize * 0.42f, capturePaint);
                } else {
                    canvas.drawCircle(mx, my, cellSize * 0.15f, validDotPaint);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null || game.isGameOver() || isLocked || isAnimating) {
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int col = Math.round((event.getX() - offsetX) / cellSize);
            int row = Math.round((event.getY() - offsetY) / cellSize);

            if (col >= 0 && col < ChineseChessGame.COLS && row >= 0 && row < ChineseChessGame.ROWS) {

                if (onCellClickListener != null) {

                    onCellClickListener.onCellClick(col, row);

                }

            }
        }
        return true;
    }
}
