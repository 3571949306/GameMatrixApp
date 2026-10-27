package com.gamecenter.app.gomoku;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 高级拟真现代五子棋棋盘视图。
 * <p>
 * 核心升级特性：
 * <ul>
 *   <li><b>防穿模坐标自适应系统</b>：动态边距预留，行号与列标置于木盘外衬槽，彻底杜绝与棋盘网格重叠</li>
 *   <li><b>沉浸式尊贵木纹棋盘</b>：圆角卡片面板，双层立体浮雕边框，质感经纬网格线与天元星位</li>
 *   <li><b>3D 球体环境光影棋子</b>：黑曜石冷光高光与汉白玉温润微透光，自然投影</li>
 *   <li><b>记谱手数模式</b>：支持在棋子中心标注入子序号（1..N），专业复盘清晰直观</li>
 *   <li><b>防误触十字瞄准准星</b>：按住滑动实时显示经纬贯穿参考线与虚影，松手精准落子</li>
 *   <li><b>最后一手高光标记</b>：动态金色双环与落子缩放弹性回弹动画</li>
 *   <li><b>大师提示呼吸波纹</b>：智能落子推荐位置带金色扩散光环</li>
 *   <li><b>绝杀五连金辉连珠</b>：胜利五子高亮连线与胜冠光晕</li>
 * </ul>
 */
public class GomokuView extends View {

    private GomokuGame game;

    private int cellSize;
    private float offsetX;
    private float offsetY;

    private int[] hoverPos;
    private int[] hintPos;

    private boolean interactive = false;
    private boolean aiThinking = false;
    private boolean showStepNumbers = false;
    private boolean crosshairEnabled = true;

    private float lastMoveAnimScale = 1.0f;
    private ValueAnimator pieceAnimator;
    private ValueAnimator hintPulseAnimator;
    private float hintPulseScale = 1.0f;

    private OnCellClickListener onCellClickListener;
    private OnControlActionListener onControlActionListener;
    private OnGameOverListener gameOverListener;

    // Paints
    private Paint boardBgPaint;
    private Paint boardBorderOuterPaint;
    private Paint boardBorderInnerPaint;
    private Paint linePaint;
    private Paint starPointPaint;
    private Paint coordinatePaint;

    private Paint blackPieceBorderPaint;
    private Paint whitePieceBorderPaint;
    private Paint pieceShadowPaint;
    private Paint stepNumberPaint;

    private Paint lastMoveRingPaint;
    private Paint lastMoveCenterPaint;

    private Paint crosshairPaint;
    private Paint hoverPreviewPaint;

    private Paint hintOuterPaint;
    private Paint hintInnerPaint;

    private Paint winLinePaint;
    private Paint winGlowPaint;

    private float density = 1f;

    private static final int[][] STAR_POINTS = {{3, 3}, {3, 11}, {7, 7}, {11, 3}, {11, 11}};

    private static final String[] COLUMN_LABELS = {
            "A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P"
    };

    public GomokuView(Context context) {
        super(context);
        init();
    }

    public GomokuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        boardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        boardBorderOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardBorderOuterPaint.setStyle(Paint.Style.STROKE);
        boardBorderOuterPaint.setColor(Color.parseColor("#4E342E"));
        boardBorderOuterPaint.setStrokeWidth(3f * density);

        boardBorderInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardBorderInnerPaint.setStyle(Paint.Style.STROKE);
        boardBorderInnerPaint.setColor(Color.parseColor("#8D6E63"));
        boardBorderInnerPaint.setStrokeWidth(1.2f * density);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#5D4037"));
        linePaint.setStrokeWidth(1.5f * density);

        starPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPointPaint.setColor(Color.parseColor("#4E342E"));
        starPointPaint.setStyle(Paint.Style.FILL);

        coordinatePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coordinatePaint.setColor(Color.parseColor("#5D4037"));
        coordinatePaint.setTextAlign(Paint.Align.CENTER);
        coordinatePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        blackPieceBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPieceBorderPaint.setStyle(Paint.Style.STROKE);
        blackPieceBorderPaint.setColor(Color.argb(160, 40, 40, 45));
        blackPieceBorderPaint.setStrokeWidth(1.2f * density);

        whitePieceBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePieceBorderPaint.setStyle(Paint.Style.STROKE);
        whitePieceBorderPaint.setColor(Color.argb(180, 180, 185, 190));
        whitePieceBorderPaint.setStrokeWidth(1.2f * density);

        pieceShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pieceShadowPaint.setColor(Color.argb(70, 0, 0, 0));

        stepNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stepNumberPaint.setTextAlign(Paint.Align.CENTER);
        stepNumberPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        lastMoveRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMoveRingPaint.setStyle(Paint.Style.STROKE);
        lastMoveRingPaint.setColor(Color.parseColor("#FFD700")); // 金色光环
        lastMoveRingPaint.setStrokeWidth(2.5f * density);

        lastMoveCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lastMoveCenterPaint.setStyle(Paint.Style.FILL);
        lastMoveCenterPaint.setColor(Color.parseColor("#E53935")); // 烈焰红心

        crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(1.8f * density);
        crosshairPaint.setColor(Color.argb(150, 255, 152, 0));
        crosshairPaint.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0));

        hoverPreviewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hoverPreviewPaint.setStyle(Paint.Style.FILL);

        hintOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintOuterPaint.setStyle(Paint.Style.STROKE);
        hintOuterPaint.setColor(Color.parseColor("#FFB300"));
        hintOuterPaint.setStrokeWidth(3f * density);

        hintInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintInnerPaint.setStyle(Paint.Style.FILL);
        hintInnerPaint.setColor(Color.argb(180, 255, 179, 0));

        winLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winLinePaint.setStyle(Paint.Style.STROKE);
        winLinePaint.setStrokeCap(Paint.Cap.ROUND);
        winLinePaint.setColor(Color.parseColor("#FF1744"));
        winLinePaint.setStrokeWidth(6.5f * density);

        winGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        winGlowPaint.setStyle(Paint.Style.STROKE);
        winGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        winGlowPaint.setColor(Color.argb(100, 255, 215, 0));
        winGlowPaint.setStrokeWidth(12f * density);

        startHintPulseAnimation();
    }

    private void startHintPulseAnimation() {
        hintPulseAnimator = ValueAnimator.ofFloat(0.85f, 1.25f);
        hintPulseAnimator.setDuration(900);
        hintPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        hintPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        hintPulseAnimator.addUpdateListener(animation -> {
            hintPulseScale = (float) animation.getAnimatedValue();
            if (hintPos != null) {
                invalidate();
            }
        });
        hintPulseAnimator.start();
    }

    public void setGame(GomokuGame game) {
        this.game = game;
        hoverPos = null;
        hintPos = null;
        lastMoveAnimScale = 1.0f;
        invalidate();
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            hoverPos = null;
        }
        invalidate();
    }

    public void setAiThinking(boolean thinking) {
        this.aiThinking = thinking;
        invalidate();
    }

    public void setShowStepNumbers(boolean show) {
        this.showStepNumbers = show;
        invalidate();
    }

    public boolean isShowStepNumbers() {
        return showStepNumbers;
    }

    public void setCrosshairEnabled(boolean enabled) {
        this.crosshairEnabled = enabled;
    }

    public boolean isCrosshairEnabled() {
        return crosshairEnabled;
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.onCellClickListener = listener;
    }

    public void setOnControlActionListener(OnControlActionListener listener) {
        this.onControlActionListener = listener;
    }

    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }

    public void clearHover() {
        hoverPos = null;
        invalidate();
    }

    public void showHint(int x, int y) {
        hintPos = new int[]{x, y};
        hoverPos = null;
        invalidate();
    }

    public void clearHint() {
        hintPos = null;
        invalidate();
    }

    public void animateLastMove() {
        if (pieceAnimator != null && pieceAnimator.isRunning()) {
            pieceAnimator.cancel();
        }
        lastMoveAnimScale = 0.0f;
        pieceAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        pieceAnimator.setDuration(180);
        pieceAnimator.setInterpolator(new OvershootInterpolator(1.8f));
        pieceAnimator.addUpdateListener(animation -> {
            lastMoveAnimScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        pieceAnimator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    private void recalcDimensions(int w, int h) {
        if (w <= 0 || h <= 0) return;
        // 关键自适应重构：为 15 路网格在四周预留出足够的木框凹槽与坐标位置
        // 14 个网格间距 + 左右各 1 个单元的内衬木槽 = 16.0f
        float minDim = Math.min(w, h);
        cellSize = (int) (minDim / (GomokuGame.BOARD_SIZE + 1.2f));
        if (cellSize < 16) cellSize = 16;

        int gridSpan = cellSize * (GomokuGame.BOARD_SIZE - 1);
        offsetX = (w - gridSpan) / 2f;
        offsetY = (h - gridSpan) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cellSize <= 0) {
            recalcDimensions(getWidth(), getHeight());
        }

        // 1. 绘制雅致木质底盘面板
        drawWoodenBoard(canvas);

        // 2. 绘制经纬网格线与星位
        drawGridLinesAndStars(canvas);

        // 3. 绘制外衬槽坐标（绝对防穿模）
        drawCoordinates(canvas);

        // 4. 绘制所有棋子与手数
        if (game != null) {
            drawPieces(canvas);
            drawCrosshairAndPreview(canvas);
            drawHint(canvas);
            drawWinningLine(canvas);
        }
    }

    /**
     * 绘制现代高端榧木纹理底盘面板
     */
    private void drawWoodenBoard(Canvas canvas) {
        float gridSpan = cellSize * (GomokuGame.BOARD_SIZE - 1);
        float boardPadding = cellSize * 0.95f;

        float bLeft = offsetX - boardPadding;
        float bTop = offsetY - boardPadding;
        float bRight = offsetX + gridSpan + boardPadding;
        float bBottom = offsetY + gridSpan + boardPadding;
        float corner = 18f * density;

        RectF boardRect = new RectF(bLeft, bTop, bRight, bBottom);

        // 底部柔和阴影
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(Color.argb(45, 0, 0, 0));
        RectF shadowRect = new RectF(bLeft + 3f * density, bTop + 5f * density,
                bRight + 3f * density, bBottom + 6f * density);
        canvas.drawRoundRect(shadowRect, corner, corner, shadowPaint);

        // 榧木双色温润渐变
        LinearGradient boardGradient = new LinearGradient(
                bLeft, bTop, bRight, bBottom,
                new int[]{Color.parseColor("#E4BE8B"), Color.parseColor("#D5A672"), Color.parseColor("#C8955F")},
                new float[]{0.0f, 0.55f, 1.0f},
                Shader.TileMode.CLAMP);
        boardBgPaint.setShader(boardGradient);
        canvas.drawRoundRect(boardRect, corner, corner, boardBgPaint);

        // 外层立体深色木边框
        canvas.drawRoundRect(boardRect, corner, corner, boardBorderOuterPaint);

        // 内层细金棕色阴影槽
        float innerMargin = 5f * density;
        RectF innerRect = new RectF(bLeft + innerMargin, bTop + innerMargin,
                bRight - innerMargin, bBottom - innerMargin);
        canvas.drawRoundRect(innerRect, corner - 2f * density, corner - 2f * density, boardBorderInnerPaint);
    }

    /**
     * 绘制网格线与天元、四角星位
     */
    private void drawGridLinesAndStars(Canvas canvas) {
        float gridSpan = cellSize * (GomokuGame.BOARD_SIZE - 1);

        // 横线与竖线
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            float y = offsetY + i * cellSize;
            canvas.drawLine(offsetX, y, offsetX + gridSpan, y, linePaint);

            float x = offsetX + i * cellSize;
            canvas.drawLine(x, offsetY, x, offsetY + gridSpan, linePaint);
        }

        // 外层边框加粗线
        Paint boldGridBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        boldGridBorder.setColor(Color.parseColor("#4E342E"));
        boldGridBorder.setStyle(Paint.Style.STROKE);
        boldGridBorder.setStrokeWidth(2.5f * density);
        canvas.drawRect(offsetX, offsetY, offsetX + gridSpan, offsetY + gridSpan, boldGridBorder);

        // 天元与四角星位圆点
        float starRadius = Math.max(3.5f * density, cellSize * 0.09f);
        for (int[] sp : STAR_POINTS) {
            float cx = offsetX + sp[0] * cellSize;
            float cy = offsetY + sp[1] * cellSize;
            canvas.drawCircle(cx, cy, starRadius, starPointPaint);

            // 星位微高光
            Paint starHighlight = new Paint(Paint.ANTI_ALIAS_FLAG);
            starHighlight.setColor(Color.argb(120, 255, 255, 255));
            canvas.drawCircle(cx - starRadius * 0.3f, cy - starRadius * 0.3f, starRadius * 0.35f, starHighlight);
        }
    }

    /**
     * 绘制棋盘四侧内衬凹槽中的坐标字母与数字（彻底根除重叠穿模）
     */
    private void drawCoordinates(Canvas canvas) {
        float textSize = Math.max(10f * density, cellSize * 0.32f);
        coordinatePaint.setTextSize(textSize);

        float gridSpan = cellSize * (GomokuGame.BOARD_SIZE - 1);
        float coordOffset = cellSize * 0.46f;

        // 列标 A..P
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            float cx = offsetX + i * cellSize;
            // 顶部列标
            canvas.drawText(COLUMN_LABELS[i], cx, offsetY - coordOffset + textSize * 0.3f, coordinatePaint);
            // 底部列标
            canvas.drawText(COLUMN_LABELS[i], cx, offsetY + gridSpan + coordOffset + textSize * 0.2f, coordinatePaint);
        }

        // 行标 1..15
        for (int i = 0; i < GomokuGame.BOARD_SIZE; i++) {
            float cy = offsetY + i * cellSize;
            String label = String.valueOf(i + 1);
            // 左侧行标
            canvas.drawText(label, offsetX - coordOffset, cy + textSize * 0.35f, coordinatePaint);
            // 右侧行标
            canvas.drawText(label, offsetX + gridSpan + coordOffset, cy + textSize * 0.35f, coordinatePaint);
        }
    }

    /**
     * 绘制所有落子、3D环境光感、最后一手标记以及记谱手数
     */
    private void drawPieces(Canvas canvas) {
        int[][] board = game.getBoard();
        int[] lastMove = game.getLastMove();
        float baseRadius = cellSize / 2f - 1.5f * density;

        for (int y = 0; y < GomokuGame.BOARD_SIZE; y++) {
            for (int x = 0; x < GomokuGame.BOARD_SIZE; x++) {
                int piece = board[y][x];
                if (piece == GomokuGame.EMPTY) continue;

                float cx = offsetX + x * cellSize;
                float cy = offsetY + y * cellSize;

                float scale = 1.0f;
                if (lastMove != null && lastMove[0] == x && lastMove[1] == y && lastMoveAnimScale < 1.0f) {
                    scale = lastMoveAnimScale;
                }
                float radius = baseRadius * scale;

                boolean isBlack = (piece == GomokuGame.BLACK);
                drawRealisticPiece(canvas, cx, cy, radius, isBlack);

                // 记谱手数显示
                if (showStepNumbers && scale >= 1.0f) {
                    int stepNum = game.getMoveStepNumber(x, y);
                    if (stepNum > 0) {
                        drawStepNumber(canvas, cx, cy, radius, stepNum, isBlack);
                    }
                }
            }
        }

        // 最后一手高亮金色圆环标记
        if (lastMove != null && lastMoveAnimScale >= 1.0f) {
            float cx = offsetX + lastMove[0] * cellSize;
            float cy = offsetY + lastMove[1] * cellSize;

            float ringRadius = baseRadius * 0.45f;
            canvas.drawCircle(cx, cy, ringRadius, lastMoveRingPaint);
            canvas.drawCircle(cx, cy, 3.5f * density, lastMoveCenterPaint);
        }
    }

    /**
     * 绘制带深度与光源反射的球体棋子
     */
    private void drawRealisticPiece(Canvas canvas, float cx, float cy, float radius, boolean isBlack) {
        if (radius <= 0) return;

        // 1. 棋子落盘柔和阴影
        float shadowOffset = 2.2f * density;
        canvas.drawCircle(cx + shadowOffset, cy + shadowOffset, radius * 0.96f, pieceShadowPaint);

        // 2. 3D 球体径向渐变
        RadialGradient pieceGradient;
        if (isBlack) {
            pieceGradient = new RadialGradient(
                    cx - radius * 0.32f, cy - radius * 0.32f, radius * 1.15f,
                    new int[]{Color.parseColor("#78909C"), Color.parseColor("#263238"), Color.parseColor("#101315")},
                    new float[]{0.0f, 0.48f, 1.0f},
                    Shader.TileMode.CLAMP);
        } else {
            pieceGradient = new RadialGradient(
                    cx - radius * 0.32f, cy - radius * 0.32f, radius * 1.15f,
                    new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#F5F7FA"), Color.parseColor("#CFD8DC")},
                    new float[]{0.0f, 0.55f, 1.0f},
                    Shader.TileMode.CLAMP);
        }

        Paint piecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        piecePaint.setShader(pieceGradient);
        canvas.drawCircle(cx, cy, radius, piecePaint);

        // 3. 边框线增强轮廓
        canvas.drawCircle(cx, cy, radius, isBlack ? blackPieceBorderPaint : whitePieceBorderPaint);
    }

    /**
     * 绘制记谱手数（棋子中心的落子序号）
     */
    private void drawStepNumber(Canvas canvas, float cx, float cy, float radius, int stepNum, boolean isBlack) {
        float numTextSize = Math.max(9f * density, radius * 0.9f);
        if (stepNum >= 100) {
            numTextSize *= 0.8f;
        }
        stepNumberPaint.setTextSize(numTextSize);
        stepNumberPaint.setColor(isBlack ? Color.WHITE : Color.parseColor("#263238"));

        Rect bounds = new Rect();
        String str = String.valueOf(stepNum);
        stepNumberPaint.getTextBounds(str, 0, str.length(), bounds);
        canvas.drawText(str, cx, cy + bounds.height() / 2f, stepNumberPaint);
    }

    /**
     * 绘制防误触十字瞄准经纬线与半透明虚影棋子
     */
    private void drawCrosshairAndPreview(Canvas canvas) {
        if (!interactive || hoverPos == null || game.isGameOver()) return;

        int hx = hoverPos[0];
        int hy = hoverPos[1];
        if (hx < 0 || hx >= GomokuGame.BOARD_SIZE || hy < 0 || hy >= GomokuGame.BOARD_SIZE) return;

        float cx = offsetX + hx * cellSize;
        float cy = offsetY + hy * cellSize;
        float gridSpan = cellSize * (GomokuGame.BOARD_SIZE - 1);

        // 贯穿整盘的精细经纬十字瞄准线
        if (crosshairEnabled) {
            canvas.drawLine(offsetX, cy, offsetX + gridSpan, cy, crosshairPaint);
            canvas.drawLine(cx, offsetY, cx, offsetY + gridSpan, crosshairPaint);
        }

        // 在交叉点绘制半透明悬停预览
        if (game.isValidMove(hx, hy)) {
            boolean isBlack = (game.getCurrentPlayer() == GomokuGame.BLACK);
            hoverPreviewPaint.setColor(isBlack ? Color.argb(120, 30, 30, 35) : Color.argb(140, 240, 245, 250));
            float radius = (cellSize / 2f - 2f * density);
            canvas.drawCircle(cx, cy, radius, hoverPreviewPaint);

            Paint previewBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
            previewBorder.setStyle(Paint.Style.STROKE);
            previewBorder.setColor(Color.parseColor("#FFA000"));
            previewBorder.setStrokeWidth(1.5f * density);
            canvas.drawCircle(cx, cy, radius, previewBorder);
        }
    }

    /**
     * 绘制大师建议落子点（带动态呼吸光环）
     */
    private void drawHint(Canvas canvas) {
        if (hintPos == null || game.isGameOver()) return;

        int hx = hintPos[0];
        int hy = hintPos[1];
        if (!game.isValidMove(hx, hy)) return;

        float cx = offsetX + hx * cellSize;
        float cy = offsetY + hy * cellSize;
        float outerRadius = (cellSize / 2f - 2f * density) * hintPulseScale;
        float innerRadius = Math.max(5f * density, cellSize * 0.16f);

        canvas.drawCircle(cx, cy, outerRadius, hintOuterPaint);
        canvas.drawCircle(cx, cy, innerRadius, hintInnerPaint);
    }

    /**
     * 绘制胜利五连线的炫彩光芒
     */
    private void drawWinningLine(Canvas canvas) {
        if (!game.isGameOver()) return;
        int[] line = game.getWinningLine();
        if (line == null || line.length < 4) return;

        float x1 = offsetX + line[0] * cellSize;
        float y1 = offsetY + line[1] * cellSize;
        float x2 = offsetX + line[2] * cellSize;
        float y2 = offsetY + line[3] * cellSize;

        // 底层金色弥散外发光
        canvas.drawLine(x1, y1, x2, y2, winGlowPaint);
        // 核心烈焰连线
        canvas.drawLine(x1, y1, x2, y2, winLinePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null || game.isGameOver() || !interactive) {
            return true;
        }

        int action = event.getActionMasked();
        float touchX = event.getX();
        float touchY = event.getY();

        int hx = Math.round((touchX - offsetX) / cellSize);
        int hy = Math.round((touchY - offsetY) / cellSize);
        boolean inBounds = (hx >= 0 && hx < GomokuGame.BOARD_SIZE && hy >= 0 && hy < GomokuGame.BOARD_SIZE);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                hintPos = null;
                if (inBounds) {
                    hoverPos = new int[]{hx, hy};
                } else {
                    hoverPos = null;
                }
                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                hintPos = null;
                if (inBounds && game.isValidMove(hx, hy)) {
                    if (onCellClickListener != null) {
                        onCellClickListener.onCellClick(hx, hy);
                    }
                }
                hoverPos = null;
                invalidate();
                break;

            case MotionEvent.ACTION_CANCEL:
                hoverPos = null;
                hintPos = null;
                invalidate();
                break;
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (hintPulseAnimator != null) {
            hintPulseAnimator.cancel();
        }
        if (pieceAnimator != null) {
            pieceAnimator.cancel();
        }
    }
}
