package com.gamecenter.app.games.rock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 石头剪刀布游戏的自定义视图（单机模式）
 * <p>
 * 负责渲染游戏界面，包括：
 * - 顶部得分栏（玩家 vs 电脑）
 * - 三个出拳按钮（石头/剪刀/布），使用圆角矩形 + Emoji 绘制
 * - 对局结果展示（双方选择和胜负文字）
 * <p>
 * 关键设计决策：
 * - 按钮布局根据视图尺寸动态计算，确保不同屏幕尺寸下布局合理
 * - 触摸事件通过坐标碰撞检测判断点击了哪个按钮
 * - 按钮高度取 min(h*0.22, w*0.28*1.4) 防止在宽屏设备上按钮过高
 */
public class RockView extends View {

    private RockGame game;
    /** 背景画笔（深紫色） */
    private Paint bgPaint;
    /** 出拳选项文字画笔 */
    private Paint choicePaint;
    /** 结果文字画笔（金色加粗） */
    private Paint resultPaint;
    /** 得分文字画笔 */
    private Paint scorePaint;
    /** 标签文字画笔（浅紫色） */
    private Paint labelPaint;

    private float viewWidth;
    private float viewHeight;
    /** 出拳按钮宽度 */
    private float buttonW;
    /** 出拳按钮高度 */
    private float buttonH;
    /** 按钮之间的间距 */
    private float buttonSpacing;

    /**
     * 单参数构造函数，用于代码动态创建视图
     *
     * @param context 上下文
     */
    public RockView(Context context) {
        super(context);
        init();
    }

    /**
     * 双参数构造函数，用于 XML 布局中声明视图
     *
     * @param context 上下文
     * @param attrs   XML 属性集
     */
    public RockView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔，设置颜色、文字大小和对齐方式
     */
    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#1E1E32"));

        choicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        choicePaint.setColor(Color.WHITE);
        choicePaint.setTextSize(50);
        choicePaint.setTextAlign(Paint.Align.CENTER);

        resultPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        resultPaint.setColor(Color.parseColor("#FFD700"));
        resultPaint.setTextSize(44);
        resultPaint.setTextAlign(Paint.Align.CENTER);
        resultPaint.setFakeBoldText(true);

        scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(40);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        scorePaint.setFakeBoldText(true);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#AAAACC"));
        labelPaint.setTextSize(32);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 绑定游戏逻辑对象
     *
     * @param game RockGame 实例
     */
    public void setGame(RockGame game) {
        this.game = game;
    }

    /**
     * 视图尺寸变化时计算按钮布局参数
     * <p>
     * 按钮宽度为视图宽度的 28%，三个按钮等间距排列。
     * 按钮高度取宽度和高度约束的较小值，防止比例失调。
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        buttonW = w * 0.28f;
        // 按钮高度不超过宽度的 1.4 倍，防止在横屏时按钮过高
        buttonH = Math.min(h * 0.22f, buttonW * 1.4f);
        // 间距 = (总宽度 - 3个按钮宽度) / 4（左右和中间各一份间距）
        buttonSpacing = (w - buttonW * 3) / 4;
    }

    /**
     * 绘制游戏画面
     * <p>
     * 绘制顺序：背景 → 得分栏 → 出拳按钮 → 对局结果
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#1E1E32"));

        // 绘制顶部得分栏
        float titleY = viewHeight * 0.06f + 40;
        scorePaint.setTextSize(36);
        canvas.drawText("你: " + game.getPlayerScore()
                + "  |  电脑: " + game.getComputerScore(), viewWidth / 2, titleY, scorePaint);

        // 绘制三个出拳按钮（石头=红色, 剪刀=蓝色, 布=绿色）
        float btnStartY = viewHeight * 0.18f;
        for (int i = 0; i < 3; i++) {
            float left = buttonSpacing + i * (buttonW + buttonSpacing);
            float top = btnStartY;

            int color;
            switch (i) {
                case RockGame.ROCK: color = Color.parseColor("#E53935"); break;
                case RockGame.SCISSORS: color = Color.parseColor("#1E88E5"); break;
                default: color = Color.parseColor("#43A047"); break;
            }

            // 绘制圆角矩形按钮背景
            Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnPaint.setColor(color);
            btnPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(left, top, left + buttonW, top + buttonH, 20, 20, btnPaint);

            // 绘制按钮上的 Emoji
            Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            emojiPaint.setColor(Color.WHITE);
            emojiPaint.setTextSize(buttonW * 0.45f);
            emojiPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(RockGame.getChoiceEmoji(i), left + buttonW / 2, top + buttonH * 0.55f, emojiPaint);

            // 绘制按钮下方的名称文字
            Paint namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            namePaint.setColor(Color.WHITE);
            namePaint.setTextSize(28);
            namePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(RockGame.getChoiceName(i), left + buttonW / 2, top + buttonH + 34, namePaint);
        }

        // 玩家已出拳后，显示双方选择和结果
        if (game.getPlayerChoice() >= 0) {
            float resultY = viewHeight * 0.6f;

            Paint vsPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            vsPaint.setColor(Color.WHITE);
            vsPaint.setTextSize(36);
            vsPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("你的选择: " + RockGame.getChoiceEmoji(game.getPlayerChoice())
                    + " " + RockGame.getChoiceName(game.getPlayerChoice()),
                    viewWidth / 2, resultY, vsPaint);

            float compY = resultY + 50;
            canvas.drawText("电脑选择: " + RockGame.getChoiceEmoji(game.getComputerChoice())
                    + " " + RockGame.getChoiceName(game.getComputerChoice()),
                    viewWidth / 2, compY, vsPaint);

            // 根据胜负结果设置不同颜色
            float resY = compY + 70;
            if (game.getLastResult() == RockGame.WIN) {
                resultPaint.setColor(Color.parseColor("#4CAF50"));
            } else if (game.getLastResult() == RockGame.LOSE) {
                resultPaint.setColor(Color.parseColor("#E53935"));
            } else {
                resultPaint.setColor(Color.parseColor("#FF9800"));
            }
            canvas.drawText(game.getLastResultText(), viewWidth / 2, resY, resultPaint);
        }
    }

    /**
     * 处理触摸事件
     * <p>
     * 通过坐标碰撞检测判断玩家点击了哪个出拳按钮，
     * 然后调用 game.choose() 执行出拳逻辑。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (game == null) return true;
            float x = event.getX();
            float y = event.getY();
            float btnStartY = viewHeight * 0.18f;

            // 遍历三个按钮区域，检测点击位置是否在某个按钮内
            for (int i = 0; i < 3; i++) {
                float left = buttonSpacing + i * (buttonW + buttonSpacing);
                if (x >= left && x <= left + buttonW && y >= btnStartY && y <= btnStartY + buttonH) {
                    game.choose(i);
                    invalidate();
                    performClick();
                    return true;
                }
            }
        }
        return true;
    }

    /**
     * 无障碍访问支持方法，必须由 onTouchEvent 调用
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
