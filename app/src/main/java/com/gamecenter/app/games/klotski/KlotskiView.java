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

/**
 * 华容道游戏自定义绘制视图
 *
 * <p>负责将 {@link KlotskiGame} 的棋盘状态渲染到屏幕上，
 * 处理触摸拖拽交互、移动动画和提示箭头显示。</p>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>方块使用线性渐变和阴影实现立体视觉效果</li>
 *   <li>拖拽移动带减速动画（DecelerateInterpolator），提升操作手感</li>
 *   <li>每次拖拽只允许移动一步，移动阈值约为单元格的 30%</li>
 *   <li>提示以绿色箭头 + 圆形高亮 + 步数文字的形式展示</li>
 *   <li>棋盘底部绘制出口标记，视觉引导玩家目标</li>
 * </ul>
 * </p>
 */
public class KlotskiView extends View {

    /** 游戏逻辑实例 */
    private KlotskiGame game;
    /** 棋盘边框画笔（深棕色） */
    private Paint wallPaint;
    /** 网格单元格画笔（浅棕色） */
    private Paint gridPaint;
    /** 出口标记画笔（金黄色） */
    private Paint exitPaint;
    /** 方块文字画笔（白色，加粗，带阴影） */
    private Paint textPaint;
    /** 提示箭头画笔（绿色，描边） */
    private Paint hintArrowPaint;
    /** 提示圆形高亮画笔（绿色，半透明填充） */
    private Paint hintCirclePaint;
    /** 单元格尺寸（像素） */
    private float cellSize;
    /** 棋盘绘制偏移量，用于居中显示 */
    private float offsetX, offsetY;
    /** 获胜回调监听器 */
    private OnWinListener listener;

    /** 当前正在拖拽的方块 */
    private KlotskiGame.Block draggingBlock;
    /** 触摸起始坐标 */
    private float touchStartX, touchStartY;
    /** 本次触摸是否已处理过移动（防止一次拖拽触发多步） */
    private boolean moveHandled;
    
    /** 提示箭头起始 X 坐标 */
    private float hintArrowX, hintArrowY;
    /** 提示箭头的方向偏移量 */
    private float hintArrowDx, hintArrowDy;
    /** 是否显示提示 */
    private boolean showHint = false;
    /** 提示显示的总步数 */
    private int hintTotalSteps = 0;
    
    /** 当前正在执行动画的方块 */
    private AnimatingBlock animatingBlock;
    /** 动画偏移量 */
    private float animOffsetX = 0f, animOffsetY = 0f;
    /** 当前动画器 */
    private ValueAnimator currentAnimator;
    /** 移动回调监听器 */
    private OnMoveListener moveListener;
    /** 出口脉冲动画相位（0~1） */
    private float exitPulsePhase = 0f;
    /** 出口脉冲动画器 */
    private ValueAnimator exitPulseAnimator;

    /** 方块颜色数组，按方块 ID 索引 */
    private static final int[] BLOCK_COLORS = {
        0xFFF44336,  // 曹操 - 鲜艳红色
        0xFF1E88E5,  // 张飞 - 蓝色
        0xFF43A047,  // 赵云 - 绿色
        0xFFFF9800,  // 马超 - 橙色
        0xFF8E24AA,  // 黄忠 - 紫色
        0xFF00BCD4,  // 关羽 - 鲜艳青色
        0xFF795548,  // 兵1 - 棕色
        0xFF0097A7,  // 兵2 - 深青
        0xFFD81B60,  // 兵3 - 粉色
        0xFF8D6E63   // 兵4 - 浅棕
    };

    /**
     * 获胜回调接口
     */
    public interface OnWinListener {
        /** 玩家获胜时调用 */
        void onWin();
    }

    /**
     * 移动回调接口
     */
    public interface OnMoveListener {
        /** 方块移动时调用 */
        void onMove();
    }

    /**
     * 单参数构造方法
     *
     * @param context 上下文
     */
    public KlotskiView(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造方法（XML 布局使用）
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public KlotskiView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔
     *
     * <p>为棋盘边框、网格、出口、文字和提示分别创建画笔对象。</p>
     */
    private void init() {
        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(0xFF3E2723);
        wallPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0xFF8D6E63);
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

        exitPulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        exitPulseAnimator.setDuration(1200);
        exitPulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        exitPulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        exitPulseAnimator.addUpdateListener(animation -> {
            exitPulsePhase = (float) animation.getAnimatedValue();
            invalidate();
        });
        exitPulseAnimator.start();
    }

    /**
     * 设置游戏逻辑实例
     *
     * @param game 华容道游戏逻辑对象
     */
    public void setGame(KlotskiGame game) {
        this.game = game;
    }

    /**
     * 2026-08-23 P2-2：从序列化状态恢复棋盘（中断续玩）。
     *
     * <p>内部新建 {@link KlotskiGame} 并从序列化数据恢复滑块布局与步数
     * （布局类型由 KlotskiGame.reset() 的经典布局定义，存档仅含位置与步数），
     * 恢复成功后绑定到本视图并重绘。</p>
     *
     * @param data {@link KlotskiGame#serializeState()} 输出的状态字符串
     * @return 恢复成功的游戏实例；数据无效时返回 null（调用方回退新开一局）
     */
    public KlotskiGame restoreState(String data) {
        KlotskiGame restored = new KlotskiGame();
        if (!restored.restoreState(data)) {
            return null;
        }
        this.game = restored;
        clearHint();
        invalidate();
        return restored;
    }

    /**
     * 设置获胜回调监听器
     *
     * @param listener 获胜回调
     */
    public void setOnWinListener(OnWinListener listener) {
        this.listener = listener;
    }

    /**
     * 设置移动回调监听器
     *
     * @param moveListener 移动回调
     */
    public void setOnMoveListener(OnMoveListener moveListener) {
        this.moveListener = moveListener;
    }

    /**
     * 显示提示箭头
     *
     * <p>根据提示结果计算箭头的起始位置和方向偏移，
     * 箭头从方块中心指向移动方向。</p>
     *
     * @param hint 提示结果
     */
    public void showHint(KlotskiGame.HintResult hint) {
        if (game == null || hint == null) {
            showHint = false;
            invalidate();
            return;
        }

        KlotskiGame.Block block = game.getBlocks().get(hint.blockId);
        // 计算方块中心坐标
        float cx = offsetX + (block.x + block.width / 2f) * cellSize;
        float cy = offsetY + (block.y + block.height / 2f) * cellSize;
        hintArrowX = cx;
        hintArrowY = cy;
        // 箭头长度约为单元格的 65%
        hintArrowDx = hint.dx * cellSize * 0.65f;
        hintArrowDy = hint.dy * cellSize * 0.65f;
        hintTotalSteps = hint.totalSteps;
        showHint = true;
        invalidate();
    }

    /**
     * 自动计算并显示提示（同步版本，可能耗时较长）
     */
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

    /**
     * 清除提示显示
     */
    public void clearHint() {
        showHint = false;
        invalidate();
    }

    /**
     * 视图尺寸变化时重新计算布局参数
     *
     * <p>棋盘为 4 列 × 5 行，底部留出 0.8 个单元格的空间用于显示出口。
     * 棋盘整体垂直偏移 0.4 个单元格以在出口上方居中。</p>
     *
     * @param w    新宽度
     * @param h    新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (game == null) return;
        float cellW = w / 4f;
        float cellH = h / 5.8f;  // 5.8 = 5行 + 0.8行出口空间
        cellSize = Math.min(cellW, cellH);
        offsetX = (w - cellSize * 4) / 2;
        offsetY = (h - cellSize * 5.8f) / 2 + cellSize * 0.4f;  // 向上偏移半个出口高度
    }

    /**
     * 绘制华容道棋盘
     *
     * <p>绘制流程：
     * <ol>
     *   <li>绘制棋盘外框（深棕色圆角矩形）和内框</li>
     *   <li>绘制 4×5 网格背景</li>
     *   <li>绘制底部出口标记</li>
     *   <li>绘制所有方块（渐变背景 + 边框 + 阴影 + 名称文字）</li>
     *   <li>如果显示提示，绘制圆形高亮 + 箭头 + 步数文字</li>
     * </ol>
     * </p>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient bgGradient = new LinearGradient(0, 0, 0, getHeight(), 0xFF3E2723, 0xFF4E342E, Shader.TileMode.CLAMP);
        bgPaint.setShader(bgGradient);
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // 绘制棋盘外框
        float boardPadding = cellSize * 0.15f;
        float boardLeft = offsetX - boardPadding;
        float boardTop = offsetY - boardPadding;
        float boardRight = offsetX + cellSize * 4 + boardPadding;
        float boardBottom = offsetY + cellSize * 5 + boardPadding;

        Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardPaint.setColor(0xFF3E2723);
        boardPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(boardLeft, boardTop, boardRight, boardBottom), 20, 20, boardPaint);

        Paint boardInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boardInnerPaint.setColor(0xFF5D4037);
        boardInnerPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(boardLeft + 6, boardTop + 6, boardRight - 6, boardBottom - 6), 16, 16, boardInnerPaint);

        // 绘制网格背景
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 4; x++) {
                float left = offsetX + x * cellSize + 3;
                float top = offsetY + y * cellSize + 3;
                float right = left + cellSize - 6;
                float bottom = top + cellSize - 6;
                canvas.drawRoundRect(new RectF(left, top, right, bottom), 8, 8, gridPaint);
                Paint innerShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                innerShadowPaint.setColor(0x40000000);
                innerShadowPaint.setStyle(Paint.Style.STROKE);
                innerShadowPaint.setStrokeWidth(2);
                canvas.drawLine(left + 4, top + 2, right - 4, top + 2, innerShadowPaint);
                Paint innerHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                innerHighlightPaint.setColor(0x20FFFFFF);
                innerHighlightPaint.setStyle(Paint.Style.STROKE);
                innerHighlightPaint.setStrokeWidth(1);
                canvas.drawLine(left + 4, bottom - 2, right - 4, bottom - 2, innerHighlightPaint);
            }
        }

        // 绘制出口标记（棋盘底部中央）
        Paint exitBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitBorderPaint.setColor(0xFFFFC107);
        exitBorderPaint.setStyle(Paint.Style.FILL);
        float exitTop = offsetY + 5 * cellSize + 8;
        float exitLeft = offsetX + cellSize + 8;
        float exitRight = offsetX + cellSize * 3 - 8;
        float exitBottom = exitTop + cellSize * 0.6f;
        float pulseScale = 1f + exitPulsePhase * 0.05f;
        float exitCenterX = (exitLeft + exitRight) / 2f;
        float exitCenterY = (exitTop + exitBottom) / 2f;
        float pulseLeft = exitCenterX - (exitCenterX - exitLeft) * pulseScale;
        float pulseRight = exitCenterX + (exitRight - exitCenterX) * pulseScale;
        float pulseTop = exitCenterY - (exitCenterY - exitTop) * pulseScale;
        float pulseBottom = exitCenterY + (exitBottom - exitCenterY) * pulseScale;
        int pulseAlpha = (int) (0xFF * (0.85f + exitPulsePhase * 0.15f));
        exitBorderPaint.setAlpha(pulseAlpha);
        canvas.drawRoundRect(new RectF(pulseLeft, pulseTop, pulseRight, pulseBottom), 10, 10, exitBorderPaint);

        Paint exitTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitTextPaint.setColor(0xFFFFFFFF);
        exitTextPaint.setTextSize(cellSize * 0.28f);
        exitTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        exitTextPaint.setTextAlign(Paint.Align.CENTER);
        exitTextPaint.setFakeBoldText(true);
        float exitTextY = (pulseTop + pulseBottom) / 2f - (exitTextPaint.ascent() + exitTextPaint.descent()) / 2f;
        canvas.drawText("出口", exitCenterX, exitTextY, exitTextPaint);

        Paint exitArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        exitArrowPaint.setColor(0xFFFFFFFF);
        exitArrowPaint.setTextSize(cellSize * 0.22f);
        exitArrowPaint.setTypeface(Typeface.DEFAULT_BOLD);
        exitArrowPaint.setTextAlign(Paint.Align.CENTER);
        float arrowY = (pulseTop + pulseBottom) / 2f - (exitArrowPaint.ascent() + exitArrowPaint.descent()) / 2f;
        canvas.drawText("▲", pulseLeft - cellSize * 0.15f, arrowY, exitArrowPaint);
        canvas.drawText("▲", pulseRight + cellSize * 0.15f, arrowY, exitArrowPaint);

        // 绘制所有方块
        for (KlotskiGame.Block block : game.getBlocks()) {
            float left = offsetX + block.x * cellSize + 4;
            float top = offsetY + block.y * cellSize + 4;
            
            // 如果方块正在动画中，添加偏移量
            if (animatingBlock != null && block.id == animatingBlock.id) {
                left += animOffsetX;
                top += animOffsetY;
            }
            
            float right = left + block.width * cellSize - 8;
            float bottom = top + block.height * cellSize - 8;

            // 方块渐变背景
            int baseColor = BLOCK_COLORS[block.id % BLOCK_COLORS.length];
            Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            LinearGradient gradient = new LinearGradient(
                left, top, left, bottom,
                baseColor,
                darkenColor(baseColor, 0.6f),
                Shader.TileMode.CLAMP
            );
            blockPaint.setShader(gradient);
            blockPaint.setStyle(Paint.Style.FILL);
            
            float cornerRadius = cellSize * 0.12f;

            Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(0x40000000);
            shadowPaint.setStyle(Paint.Style.FILL);
            RectF shadowRect = new RectF(left + 3, top + 3, right + 3, bottom + 3);
            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint);

            if (block.type == KlotskiGame.BLOCK_CAOCAO) {
                Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                glowPaint.setColor(0x40FFD700);
                glowPaint.setStyle(Paint.Style.FILL);
                canvas.drawRoundRect(new RectF(left - 4, top - 4, right + 4, bottom + 4), cornerRadius + 4, cornerRadius + 4, glowPaint);
            }

            canvas.drawRoundRect(new RectF(left, top, right, bottom), cornerRadius, cornerRadius, blockPaint);

            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(0xFFFFD700);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(2);
            canvas.drawRoundRect(new RectF(left, top, right, bottom), cornerRadius, cornerRadius, borderPaint);

            // 方块名称文字（字号根据方块类型调整）
            textPaint.setTextSize(block.type == KlotskiGame.BLOCK_CAOCAO ? cellSize * 0.42f : 
                                  block.type == KlotskiGame.BLOCK_HORIZONTAL ? cellSize * 0.38f : 
                                  cellSize * 0.35f);
            float textX = left + (right - left) / 2;
            float textY = top + (bottom - top) / 2 - (textPaint.ascent() + textPaint.descent()) / 2;
            canvas.drawText(block.name, textX, textY, textPaint);
        }

        // 绘制提示箭头和高亮
        if (showHint) {
            // 圆形高亮
            canvas.drawCircle(hintArrowX, hintArrowY, cellSize * 0.5f, hintCirclePaint);
            
            // 箭头线段
            hintArrowPaint.setStyle(Paint.Style.STROKE);
            hintArrowPaint.setStrokeWidth(8);
            hintArrowPaint.setColor(0xFF4CAF50);
            canvas.drawLine(hintArrowX, hintArrowY, hintArrowX + hintArrowDx, hintArrowY + hintArrowDy, hintArrowPaint);
            
            // 箭头头部（三角形）
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
            
            // 步数文字
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

    /**
     * 将颜色变暗
     *
     * <p>通过降低 HSV 色彩空间中的明度值（V）实现变暗效果。</p>
     *
     * @param color  原始颜色
     * @param factor 明度乘数（0-1，越小越暗）
     * @return 变暗后的颜色
     */
    private int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return android.graphics.Color.HSVToColor(hsv);
    }

    /**
     * 处理触摸事件，实现方块拖拽移动
     *
     * <p>交互流程：
     * <ol>
     *   <li>ACTION_DOWN：记录起始位置，确定拖拽的方块</li>
     *   <li>ACTION_MOVE：当拖拽距离超过阈值时，执行一步移动并播放动画</li>
     *   <li>ACTION_UP/CANCEL：重置拖拽状态</li>
     * </ol>
     * 每次拖拽只允许移动一步（moveHandled 标志），移动后检查是否获胜。</p>
     *
     * @param event 触摸事件
     * @return 是否消费了该事件
     */
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
                // 根据触摸坐标确定拖拽的方块
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
                // 如果有动画正在播放，忽略新的移动
                if (currentAnimator != null && currentAnimator.isRunning()) return true;
                
                float dx = ex - touchStartX;
                float dy = ey - touchStartY;
                float threshold = cellSize * 0.3f;  // 移动阈值为单元格的 30%

                // 水平方向移动
                if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy)) {
                    int ix = dx > 0 ? 1 : -1;
                    if (game.moveBlock(draggingBlock, ix, 0)) {
                        moveHandled = true;
                        startMoveAnimation(draggingBlock, ix * cellSize, 0);
                        if (moveListener != null) moveListener.onMove();
                        if (game.isWon() && listener != null) listener.onWin();
                    }
                } else if (Math.abs(dy) > threshold) {
                    // 垂直方向移动
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
    
    /**
     * 启动方块移动动画
     *
     * <p>动画效果：方块从旧位置滑动到新位置。
     * 动画起始偏移量为负的移动距离（方块在旧位置），随进度逐渐减小到 0（方块到达新位置）。
     * 使用减速插值器（DecelerateInterpolator）实现自然减速效果。</p>
     *
     * @param block    移动的方块
     * @param targetDx 目标水平偏移量（像素）
     * @param targetDy 目标垂直偏移量（像素）
     */
    private void startMoveAnimation(KlotskiGame.Block block, float targetDx, float targetDy) {
        // 取消正在进行的动画
        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
        
        animatingBlock = new AnimatingBlock(block.id);
        // 初始偏移为负值（方块还在旧位置）
        animOffsetX = -targetDx;
        animOffsetY = -targetDy;
        
        // 动画时长与移动距离成正比，最短 100ms
        float distance = (float) Math.sqrt(targetDx * targetDx + targetDy * targetDy);
        int duration = Math.max(100, (int) (distance / cellSize * 120));
        
        currentAnimator = ValueAnimator.ofFloat(0f, 1f);
        currentAnimator.setDuration(duration);
        currentAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        currentAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            // 偏移量从负值渐变到 0
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
    
    /**
     * 动画方块数据类，记录正在执行动画的方块 ID
     */
    private static class AnimatingBlock {
        int id;

        AnimatingBlock(int id) {
            this.id = id;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (exitPulseAnimator != null && exitPulseAnimator.isRunning()) {
            exitPulseAnimator.cancel();
        }
        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }
    }
}
