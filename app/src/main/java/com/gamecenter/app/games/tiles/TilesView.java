package com.gamecenter.app.games.tiles;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 别踩白块儿自定义视图
 *
 * <p>职责：负责"别踩白块儿"游戏的视觉渲染和触摸交互处理。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>方块行从底部向上滚动，底部3行为玩家操作区域</li>
 *   <li>黑块为黑色，白块为白色，已点击的格子为灰色</li>
 *   <li>得分显示在屏幕顶部，带阴影效果增强可读性</li>
 *   <li>游戏结束后点击屏幕可重新开始</li>
 * </ul>
 */
public class TilesView extends View {

    /** 游戏逻辑对象 */
    private TilesGame game;

    /** 黑块填充画笔 */
    private Paint blackPaint;

    /** 白块填充画笔 */
    private Paint whitePaint;

    /** 灰色背景画笔（用于游戏区域底色） */
    private Paint grayPaint;

    /** 已点击格子的填充画笔（灰色） */
    private Paint touchedPaint;

    /** 得分文字画笔（白色大号粗体） */
    private Paint scorePaint;

    /** 提示文字画笔（白色中号） */
    private Paint textPaint;

    /** 视图宽度 */
    private float viewWidth;

    /** 视图高度 */
    private float viewHeight;

    public TilesView(Context context) {
        super(context);
        init();
    }

    public TilesView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔
     *
     * <p>所有画笔均启用抗锯齿（ANTI_ALIAS_FLAG）。
     * blackPaint/whitePaint/touchedPaint 用于填充方块，
     * scorePaint/textPaint 用于绘制文字。</p>
     */
    private void init() {
        blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(Color.BLACK);

        whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        whitePaint.setColor(Color.WHITE);

        grayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        grayPaint.setColor(Color.parseColor("#424242"));

        touchedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        touchedPaint.setColor(Color.parseColor("#AAAAAA"));

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(60);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 设置游戏逻辑对象
     * @param game TilesGame 实例
     */
    public void setGame(TilesGame game) {
        this.game = game;
    }

    /**
     * 视图尺寸变化时通知游戏逻辑更新区域参数
     *
     * @param w 新宽度
     * @param h 新高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        if (game != null) {
            game.setGameArea(w, h);
        }
    }

    /**
     * 绘制方块、得分和游戏状态提示
     *
     * <p>绘制逻辑：</p>
     * <ol>
     *   <li>深色背景</li>
     *   <li>根据 totalScroll 计算可见行范围，逐行逐列绘制方块</li>
     *   <li>黑块用黑色填充，白块用白色填充，已点击格子用灰色覆盖</li>
     *   <li>每个格子绘制1px深色边框</li>
     *   <li>未开始时显示"点击黑块开始"提示</li>
     *   <li>顶部显示得分（带阴影）</li>
     *   <li>游戏结束时显示红色失败提示和得分</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#1A1A2E"));

        float cellSize = game.getCellSize();
        float totalScroll = game.getTotalScroll();
        float topRowOffset = totalScroll % cellSize;
        int startRow = (int) (totalScroll / cellSize);

        for (int visibleRow = -1; visibleRow <= (int) (viewHeight / cellSize) + 1; visibleRow++) {
            int dataRow = startRow + visibleRow;
            if (dataRow < 0 || dataRow >= game.getTileRows().size()) continue;

            TilesGame.Row row = game.getTileRows().get(dataRow);
            float top = viewHeight - (visibleRow + 1) * cellSize + topRowOffset;
            float bottom = top + cellSize;

            for (int col = 0; col < TilesGame.COLUMNS; col++) {
                float left = col * cellSize;
                float right = left + cellSize;

                Paint fillPaint = row.isBlack[col] ? blackPaint : whitePaint;
                if (row.touchedCol == col) {
                    fillPaint = touchedPaint;
                }
                canvas.drawRect(left + 1, top + 1, right - 1, bottom - 1, fillPaint);

                Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                borderPaint.setColor(Color.parseColor("#333333"));
                borderPaint.setStyle(Paint.Style.STROKE);
                borderPaint.setStrokeWidth(1);
                canvas.drawRect(left, top, right, bottom, borderPaint);
            }
        }

        if (!game.isStarted() && !game.isGameOver()) {
            canvas.drawText("点击黑块开始", viewWidth / 2, viewHeight * 0.4f, textPaint);
        }

        scorePaint.setShadowLayer(4, 2, 2, Color.BLACK);
        canvas.drawText("" + game.getScore(), viewWidth / 2, viewHeight * 0.1f, scorePaint);

        if (game.isGameOver() && game.isStarted()) {
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.parseColor("#E53935"));
            overPaint.setTextSize(50);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            canvas.drawText("点击了白块!", viewWidth / 2, viewHeight * 0.35f, overPaint);
            textPaint.setTextSize(34);
            canvas.drawText("得分: " + game.getScore() + "  点击重玩", viewWidth / 2, viewHeight * 0.35f + 46, textPaint);
        }
    }

    /**
     * 处理触摸事件
     *
     * <p>交互逻辑：</p>
     * <ul>
     *   <li>游戏结束时：点击任意位置重置游戏</li>
     *   <li>正常状态时：根据触摸X坐标计算列索引，调用 game.touch() 处理</li>
     * </ul>
     *
     * @param event 触摸事件
     * @return 始终返回 true，表示消费了触摸事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (game == null) return true;
            if (game.isGameOver()) {
                game.reset();
                game.setGameArea(viewWidth, viewHeight);
                invalidate();
                return true;
            }
            int col = (int) (event.getX() / game.getCellSize());
            game.touch(col);
            invalidate();
            performClick();
        }
        return true;
    }

    /**
     * 辅助方法，满足 Accessibility 要求
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
