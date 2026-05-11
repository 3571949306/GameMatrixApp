package com.gamecenter.app.games.klotski;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.animation.ValueAnimator;
import androidx.annotation.Nullable;

public class KlotskiView extends View {

    private KlotskiGame game;
    private Paint wallPaint;
    private Paint gridPaint;
    private Paint exitPaint;
    private Paint textPaint;
    private Paint hintArrowPaint;
    private Paint hintCirclePaint;
    private float cellSize;
    private float offsetX, offsetY;
    private OnWinListener listener;

    private KlotskiGame.Block draggingBlock;
    private float touchStartX, touchStartY;
    private boolean moveHandled;
    
    private float hintArrowX, hintArrowY;
    private float hintArrowDx, hintArrowDy;
    private boolean showHint = false;
    private int hintTotalSteps = 0;
    
    private AnimatingBlock animatingBlock;
    private float animOffsetX = 0f, animOffsetY = 0f;
    private ValueAnimator currentAnimator;
    private OnMoveListener moveListener;

    private static final int[] BLOCK_COLORS = {
        0xFFE53935,
        0xFF1E88E5,
        0xFF43A047,
        0xFFFF9800,
        0xFF8E24AA,
        0xFF00ACC1,
        0xFF795548,
        0xFF0097A7,
        0xFFD81B60,
        0xFF8D6E63
    };

    public interface OnWinListener {
        void onWin();
    }

    public interface OnMoveListener {
        void onMove();
    }

    public KlotskiView(Context context) {
        super(context);
        init();
    }

    public KlotskiView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(0xFF3E2723);
        wallPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0xFFD7CCC8);
        gridPaint.setStyle(Paint.Style.FILL);

        exitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitPaint.setColor(0xFFFFD54F);
        exitPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setShadowLayer(2, 0, 1, 0x40000000);

        hintArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintArrowPaint.setColor(0xFF4CAF50);
        hintArrowPaint.setStyle(Paint.Style.STROKE);
        hintArrowPaint.setStrokeWidth(6);
        hintArrowPaint.setStrokeCap(Paint.Cap.ROUND);

        hintCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintCirclePaint.setColor(0x664CAF50);
        hintCirclePaint.setStyle(Paint.Style.FILL);
    }

    public void setGame(KlotskiGame game) {
        this.game = game;
    }

    public void setOnWinListener(OnWinListener listener) {
        this.listener = listener;
    }

    public void setOnMoveListener(OnMoveListener moveListener) {
        this.moveListener = moveListener;
    }

    public void showHint(KlotskiGame.HintResult hint) {
        if (game == null || hint == null) {
            showHint = false;
            invalidate();
            return;
        }

        KlotskiGame.Block block = game.getBlocks().get(hint.blockId);
        float cx = offsetX + (block.x + block.width / 2f) * cellSize;
        float cy = offsetY + (block.y + block.height / 2f) * cellSize;
        hintArrowX = cx;
        hintArrowY = cy;
        hintArrowDx = hint.dx * cellSize * 0.65f;
        hintArrowDy = hint.dy * cellSize * 0.65f;
        hintTotalSteps = hint.totalSteps;
        showHint = true;
        invalidate();
    }

    public void showHint() {
        if (game == null) {
            showHint = false;
            return;
        }

        KlotskiGame.HintResult hint = game.getHint();
        if (hint != null) {
            showHint(hint);
        } else {
            showHint = false;
            invalidate();
        }
    }

    public void clearHint() {
        showHint = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (game == null) return;
        float cellW = w / 4f;
        float cellH = h / 5.8f;
        cellSize = Math.min(cellW, cellH);
        offsetX = (w - cellSize * 4) / 2;
        offsetY = (h - cellSize * 5.8f) / 2 + cellSize * 0.4f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(0xFFF5F5F5);

        float boardPadding = cellSize * 0.15f;
        float boardLeft = offsetX - boardPadding;
        float boardTop = offsetY - boardPadding;
        float boardRight = offsetX + cellSize * 4 + boardPadding;
        float boardBottom = offsetY + cellSize * 5 + boardPadding;

        Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardPaint.setColor(0xFF3E2723);
        boardPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(boardLeft, boardTop, boardRight, boardBottom), 16, 16, boardPaint);

        Paint boardInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardInnerPaint.setColor(0xFF4E342E);
        boardInnerPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(boardLeft + 6, boardTop + 6, boardRight - 6, boardBottom - 6), 12, 12, boardInnerPaint);

        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 4; x++) {
                float left = offsetX + x * cellSize + 3;
                float top = offsetY + y * cellSize + 3;
                float right = left + cellSize - 6;
                float bottom = top + cellSize - 6;
                canvas.drawRoundRect(new RectF(left, top, right, bottom), 8, 8, gridPaint);
            }
        }

        Paint exitBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitBorderPaint.setColor(0xFFFFC107);
        exitBorderPaint.setStyle(Paint.Style.FILL);
        float exitTop = offsetY + 5 * cellSize + 8;
        float exitLeft = offsetX + cellSize + 8;
        float exitRight = offsetX + cellSize * 3 - 8;
        float exitBottom = exitTop + cellSize * 0.6f;
        canvas.drawRoundRect(new RectF(exitLeft, exitTop, exitRight, exitBottom), 10, 10, exitBorderPaint);

        Paint exitTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitTextPaint.setColor(0xFF3E2723);
        exitTextPaint.setTextSize(cellSize * 0.28f);
        exitTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        exitTextPaint.setTextAlign(Paint.Align.CENTER);
        exitTextPaint.setFakeBoldText(true);
        float exitTextY = (exitTop + exitBottom) / 2f - (exitTextPaint.ascent() + exitTextPaint.descent()) / 2f;
        canvas.drawText("出口", offsetX + cellSize * 2, exitTextY, exitTextPaint);

        for (KlotskiGame.Block block : game.getBlocks()) {
            float left = offsetX + block.x * cellSize + 4;
            float top = offsetY + block.y * cellSize + 4;
            
            if (animatingBlock != null && block.id == animatingBlock.id) {
                left += animOffsetX;
                top += animOffsetY;
            }
            
            float right = left + block.width * cellSize - 8;
            float bottom = top + block.height * cellSize - 8;

            int baseColor = BLOCK_COLORS[block.id % BLOCK_COLORS.length];
            Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            LinearGradient gradient = new LinearGradient(
                left, top, left, bottom,
                baseColor,
                darkenColor(baseColor, 0.75f),
                Shader.TileMode.CLAMP
            );
            blockPaint.setShader(gradient);
            blockPaint.setStyle(Paint.Style.FILL);
            
            float cornerRadius = cellSize * 0.12f;
            canvas.drawRoundRect(new RectF(left, top, right, bottom), cornerRadius, cornerRadius, blockPaint);

            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(0x40FFFFFF);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(2);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), cornerRadius, cornerRadius, borderPaint);

            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(0x30000000);
            shadowPaint.setStyle(Paint.Style.FILL);
            RectF shadowRect = new RectF(left + 2, top + 3, right + 2, bottom + 3);
            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint);

            textPaint.setTextSize(block.type == KlotskiGame.BLOCK_CAOCAO ? cellSize * 0.42f : 
                                  block.type == KlotskiGame.BLOCK_HORIZONTAL ? cellSize * 0.38f : 
                                  cellSize * 0.35f);
            float textX = left + (right - left) / 2;
            float textY = top + (bottom - top) / 2 - (textPaint.ascent() + textPaint.descent()) / 2;
            canvas.drawText(block.name, textX, textY, textPaint);
        }

        if (showHint) {
            canvas.drawCircle(hintArrowX, hintArrowY, cellSize * 0.5f, hintCirclePaint);
            
            hintArrowPaint.setStyle(Paint.Style.STROKE);
            hintArrowPaint.setStrokeWidth(8);
            hintArrowPaint.setColor(0xFF4CAF50);
            canvas.drawLine(hintArrowX, hintArrowY, hintArrowX + hintArrowDx, hintArrowY + hintArrowDy, hintArrowPaint);
            
            hintArrowPaint.setStyle(Paint.Style.FILL);
            float arrowHeadLen = cellSize * 0.18f;
            float angle = (float) Math.atan2(hintArrowDy, hintArrowDx);
            Path arrowHead = new Path();
            float tipX = hintArrowX + hintArrowDx;
            float tipY = hintArrowY + hintArrowDy;
            arrowHead.moveTo(tipX, tipY);
            arrowHead.lineTo(
                tipX - arrowHeadLen * (float) Math.cos(angle - Math.PI / 6),
                tipY - arrowHeadLen * (float) Math.sin(angle - Math.PI / 6)
            );
            arrowHead.lineTo(
                tipX - arrowHeadLen * (float) Math.cos(angle + Math.PI / 6),
                tipY - arrowHeadLen * (float) Math.sin(angle + Math.PI / 6)
            );
            arrowHead.close();
            canvas.drawPath(arrowHead, hintArrowPaint);
            
            Paint tipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            tipTextPaint.setColor(0xFF2E7D32);
            tipTextPaint.setStyle(Paint.Style.FILL);
            tipTextPaint.setTextSize(cellSize * 0.28f);
            tipTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            tipTextPaint.setTextAlign(Paint.Align.CENTER);
            tipTextPaint.setShadowLayer(2, 0, 1, 0x30FFFFFF);
            String hintStepText = hintTotalSteps + "步到出口";
            canvas.drawText(hintStepText, tipX, tipY - cellSize * 0.35f, tipTextPaint);
        }
    }

    private int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return android.graphics.Color.HSVToColor(hsv);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null || cellSize <= 0) return true;

        float ex = event.getX();
        float ey = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = ex;
                touchStartY = ey;
                moveHandled = false;
                int gx = (int) ((ex - offsetX) / cellSize);
                int gy = (int) ((ey - offsetY) / cellSize);
                if (gx >= 0 && gx < 4 && gy >= 0 && gy < 5) {
                    draggingBlock = game.getBlockAt(gx, gy);
                } else {
                    draggingBlock = null;
                }
                clearHint();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (draggingBlock == null || moveHandled) return true;
                if (currentAnimator != null && currentAnimator.isRunning()) return true;
                
                float dx = ex - touchStartX;
                float dy = ey - touchStartY;
                float threshold = cellSize * 0.3f;

                if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy)) {
                    int ix = dx > 0 ? 1 : -1;
                    if (game.moveBlock(draggingBlock, ix, 0)) {
                        moveHandled = true;
                        startMoveAnimation(draggingBlock, ix * cellSize, 0);
                        if (moveListener != null) moveListener.onMove();
                        if (game.isWon() && listener != null) listener.onWin();
                    }
                } else if (Math.abs(dy) > threshold) {
                    int iy = dy > 0 ? 1 : -1;
                    if (game.moveBlock(draggingBlock, 0, iy)) {
                        moveHandled = true;
                        startMoveAnimation(draggingBlock, 0, iy * cellSize);
                        if (moveListener != null) moveListener.onMove();
                        if (game.isWon() && listener != null) listener.onWin();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                draggingBlock = null;
                moveHandled = false;
                return true;
        }
        return true;
    }
    
    private void startMoveAnimation(KlotskiGame.Block block, float targetDx, float targetDy) {
        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
        
        animatingBlock = new AnimatingBlock(block.id);
        animOffsetX = -targetDx;
        animOffsetY = -targetDy;
        
        float distance = (float) Math.sqrt(targetDx * targetDx + targetDy * targetDy);
        int duration = Math.max(100, (int) (distance / cellSize * 120));
        
        currentAnimator = ValueAnimator.ofFloat(0f, 1f);
        currentAnimator.setDuration(duration);
        currentAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        currentAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            animOffsetX = -targetDx * (1f - progress);
            animOffsetY = -targetDy * (1f - progress);
            invalidate();
        });
        currentAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                animOffsetX = 0f;
                animOffsetY = 0f;
                animatingBlock = null;
                invalidate();
            }
        });
        currentAnimator.start();
    }
    
    private static class AnimatingBlock {
        int id;

        AnimatingBlock(int id) {
            this.id = id;
        }
    }
}
