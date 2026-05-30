package com.gamecenter.app.games.reaction;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 反应力挑战游戏的自定义视图
 * <p>
 * 根据游戏状态（ReactionGame.State）渲染不同颜色的全屏背景和提示文字：
 * <ul>
 *   <li>IDLE - 蓝色背景，提示"点击开始"</li>
 *   <li>WAITING - 红色背景，提示"等待变绿"</li>
 *   <li>READY - 绿色背景，提示"点击!"</li>
 *   <li>TAPPED - 蓝色背景，显示反应时间</li>
 *   <li>TOO_SOON - 橙色背景，提示"太早了"</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * - 视图仅负责渲染和触摸事件转发，不包含游戏逻辑
 * - 触摸事件直接调用 game.onTap()，通过监听器通知 Activity 更新 UI
 */
public class ReactionView extends View {

    /** 等待状态背景色（红色） */
    private static final int COLOR_WAITING = 0xFFE53935;
    /** 就绪状态背景色（绿色） */
    private static final int COLOR_READY = 0xFF43A047;
    /** 空闲状态背景色（蓝色） */
    private static final int COLOR_IDLE = 0xFF1E88E5;
    /** 已点击状态背景色（蓝色） */
    private static final int COLOR_TAPPED = 0xFF1E88E5;
    /** 过早点击状态背景色（橙色） */
    private static final int COLOR_TOO_SOON = 0xFFFF8F00;

    private ReactionGame game;
    /** 背景填充画笔 */
    private Paint bgPaint;
    /** 主文字画笔（大号加粗） */
    private Paint textPaint;
    /** 副文字画笔（小号常规） */
    private Paint subTextPaint;

    /**
     * 单参数构造函数，用于代码动态创建视图
     *
     * @param context 上下文
     */
    public ReactionView(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造函数，用于 XML 布局中声明视图
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public ReactionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化画笔，设置文字大小和对齐方式
     */
    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(72f);
        textPaint.setFakeBoldText(true);

        subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTextSize(36f);
    }

    /**
     * 绑定游戏逻辑对象
     *
     * @param game ReactionGame 实例
     */
    public void setGame(ReactionGame game) {
        this.game = game;
    }

    /**
     * 绘制游戏画面
     * <p>
     * 根据当前游戏状态选择背景颜色和提示文字，
     * 主文字居中显示，副文字在主文字下方偏移显示。
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        int color;
        String mainText = "";
        String subText = "";

        switch (game.getState()) {
            case IDLE:
                color = COLOR_IDLE;
                mainText = "反应力挑战";
                subText = "点击屏幕开始";
                break;
            case WAITING:
                color = COLOR_WAITING;
                mainText = "等待...";
                subText = "变绿后立即点击!";
                break;
            case READY:
                color = COLOR_READY;
                mainText = "点击!";
                subText = "";
                break;
            case TAPPED:
                color = COLOR_TAPPED;
                long ms = game.getCurrentResult();
                mainText = ms + " ms";
                // 显示当前轮次信息（round 从 0 开始计数，显示时 +1）
                subText = "点击继续下一轮 (第" + (game.getRound() + 1) + "轮)";
                break;
            case TOO_SOON:
                color = COLOR_TOO_SOON;
                mainText = "太早了!";
                subText = "点击重新等待";
                break;
            default:
                color = COLOR_IDLE;
                break;
        }

        // 绘制全屏背景色
        bgPaint.setColor(color);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);

        // 主文字垂直居中（减去 textSize/3 是为了视觉居中校正）
        float mainY = getHeight() / 2 - textPaint.getTextSize() / 3;
        canvas.drawText(mainText, getWidth() / 2, mainY, textPaint);

        // 副文字在主文字下方偏移显示
        if (!subText.isEmpty()) {
            float subY = mainY + textPaint.getTextSize() * 0.7f;
            canvas.drawText(subText, getWidth() / 2, subY, subTextPaint);
        }
    }

    /**
     * 处理触摸事件
     * <p>
     * 仅响应 ACTION_DOWN 事件，调用 game.onTap() 并刷新视图，
     * 同时通过监听器通知 Activity 更新得分文字。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && game != null) {
            game.onTap();
            invalidate();
            if (listener != null) listener.onStateChange();
        }
        return true;
    }

    /**
     * 请求重绘视图
     */
    public void refresh() {
        invalidate();
    }

    private OnStateChangeListener listener;

    /**
     * 视图内部的状态变化监听器接口，用于通知 Activity 触摸事件后的状态变化
     */
    public interface OnStateChangeListener {
        void onStateChange();
    }

    /**
     * 设置状态变化监听器
     *
     * @param l 监听器实例
     */
    public void setOnStateChangeListener(OnStateChangeListener l) {
        this.listener = l;
    }
}
