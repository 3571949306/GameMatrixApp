package com.gamecenter.app.games.guess;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 猜数字游戏的自定义绘制视图
 *
 * <p>负责游戏界面的绘制和触摸交互处理，包括：</p>
 * <ul>
 *   <li>顶部标题和范围提示</li>
 *   <li>输入显示区域</li>
 *   <li>提示文字（根据猜大/猜小/猜中显示不同颜色）</li>
 *   <li>3x4数字键盘（1-9、清除、0、确定）</li>
 *   <li>底部猜测次数显示</li>
 * </ul>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用StringBuilder作为输入缓冲区，限制最多3位数字</li>
 *   <li>提示文字颜色根据状态变化：猜中=绿色、猜大=橙色、猜小=蓝色</li>
 *   <li>游戏结束后点击任意位置重新开始</li>
 *   <li>键盘布局：前3行为数字1-9，第4行为功能键（清除/0/确定）</li>
 * </ul>
 */
public class GuessView extends View {

    /** 游戏逻辑对象 */
    private GuessGame game;
    /** 背景画笔 */
    private Paint bgPaint;
    /** 按钮背景画笔 */
    private Paint btnPaint;
    /** 按钮文字画笔 */
    private Paint btnTextPaint;
    /** 输入数字画笔 */
    private Paint inputPaint;
    /** 提示文字画笔 */
    private Paint hintPaint;
    /** 标题文字画笔 */
    private Paint titlePaint;

    /** 视图总宽度 */
    private float viewWidth;
    /** 视图总高度 */
    private float viewHeight;

    /** 输入缓冲区，存储玩家输入的数字字符串 */
    private StringBuilder inputBuffer;
    /** 最大输入位数（3位，支持1-500） */
    private static final int MAX_DIGITS = 3;

    public GuessView(Context context) {
        super(context);
        init();
    }

    public GuessView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化画笔和输入缓冲区
     *
     * <p>背景色为深紫蓝(#1E1E32)，按钮默认为靛蓝(#3949AB)，
     * 确定按钮为绿色，清除按钮为红色。</p>
     */
    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#1E1E32"));

        btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnPaint.setColor(Color.parseColor("#3949AB"));
        btnPaint.setStyle(Paint.Style.FILL);

        btnTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnTextPaint.setColor(Color.WHITE);
        btnTextPaint.setTextSize(48);
        btnTextPaint.setTextAlign(Paint.Align.CENTER);

        inputPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inputPaint.setColor(Color.WHITE);
        inputPaint.setTextSize(60);
        inputPaint.setTextAlign(Paint.Align.CENTER);
        inputPaint.setFakeBoldText(true);

        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.parseColor("#FFD700"));
        hintPaint.setTextSize(40);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setFakeBoldText(true);

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.parseColor("#AAAAFF"));
        titlePaint.setTextSize(32);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        inputBuffer = new StringBuilder();
    }

    /**
     * 设置游戏逻辑对象
     *
     * @param game GuessGame实例
     */
    public void setGame(GuessGame game) {
        this.game = game;
    }

    /**
     * 重置输入缓冲区
     *
     * <p>清空玩家输入的数字，通常在重新开始游戏时调用。</p>
     */
    public void resetInput() {
        inputBuffer.setLength(0);
    }

    /**
     * 视图尺寸变化时记录宽高
     *
     * @param w 新宽度
     * @param h 新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
    }

    /**
     * 绘制游戏界面
     *
     * <p>绘制流程：</p>
     * <ol>
     *   <li>绘制深色背景</li>
     *   <li>绘制标题（范围提示）</li>
     *   <li>绘制输入区域（当前输入的数字或"___"占位符）</li>
     *   <li>绘制提示文字（根据猜大/猜小/猜中显示不同颜色）</li>
     *   <li>绘制3x4数字键盘</li>
     *   <li>绘制底部猜测次数</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#1E1E32"));

        // 绘制标题：范围提示
        float topY = viewHeight * 0.08f + 20;
        canvas.drawText("猜一个 " + game.getMinRange() + "-" + game.getMaxRange() + " 的数字",
                viewWidth / 2, topY, titlePaint);

        // 绘制输入区域：显示当前输入或"___"占位符
        float inputY = topY + 80;
        String display = inputBuffer.length() == 0 ? "___" : inputBuffer.toString();
        canvas.drawText(display, viewWidth / 2, inputY, inputPaint);

        // 绘制提示文字，根据状态切换颜色
        if (game.getLastHint().length() > 0) {
            float hintY = inputY + 70;
            if (game.isGameOver()) {
                // 猜中：绿色
                hintPaint.setColor(Color.parseColor("#4CAF50"));
            } else if (game.getLastHint().contains("大")) {
                // 猜大：橙色
                hintPaint.setColor(Color.parseColor("#FF9800"));
            } else {
                // 猜小：蓝色
                hintPaint.setColor(Color.parseColor("#2196F3"));
            }
            canvas.drawText(game.getLastHint(), viewWidth / 2, hintY, hintPaint);
        }

        // 绘制底部猜测次数
        Paint attemptPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        attemptPaint.setColor(Color.parseColor("#AAAAAA"));
        attemptPaint.setTextSize(28);
        attemptPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("已猜 " + game.getAttempts() + " 次", viewWidth / 2, viewHeight * 0.92f, attemptPaint);

        // 绘制3x4数字键盘
        float keypadY = viewHeight * 0.4f;
        float keySize = viewWidth * 0.22f;
        float keySpacing = keySize * 0.15f;
        float totalW = 3 * keySize + 2 * keySpacing;
        float padOffsetX = (viewWidth - totalW) / 2;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                float left = padOffsetX + col * (keySize + keySpacing);
                float top = keypadY + row * (keySize + keySpacing);
                float right = left + keySize;
                float bottom = top + keySize;

                // 确定按键标签：前3行为1-9，第4行为功能键
                String label;
                if (row < 3) {
                    int num = row * 3 + col + 1;
                    label = String.valueOf(num);
                } else if (col == 0) {
                    label = "清除";
                } else if (col == 1) {
                    label = "0";
                } else {
                    label = "确定";
                }

                // 根据按键类型设置不同颜色
                if (label.equals("确定")) {
                    btnPaint.setColor(Color.parseColor("#4CAF50"));
                } else if (label.equals("清除")) {
                    btnPaint.setColor(Color.parseColor("#E53935"));
                } else {
                    btnPaint.setColor(Color.parseColor("#3949AB"));
                }

                canvas.drawRoundRect(left, top, right, bottom, 16, 16, btnPaint);
                canvas.drawText(label, left + keySize / 2, top + keySize / 2 + 16, btnTextPaint);
            }
        }
    }

    /**
     * 处理触摸事件
     *
     * <p>交互逻辑：</p>
     * <ol>
     *   <li>游戏结束后点击任意位置重新开始</li>
     *   <li>检测触摸点是否在键盘区域内</li>
     *   <li>匹配到按键后调用handleKeyPress处理</li>
     * </ol>
     *
     * @param event 触摸事件
     * @return 始终返回true表示消费了事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (game == null) return true;
            // 游戏结束后点击任意位置重新开始
            if (game.isGameOver()) {
                game.reset();
                resetInput();
                invalidate();
                performClick();
                return true;
            }

            float x = event.getX();
            float y = event.getY();

            // 计算键盘区域参数（与onDraw中一致）
            float keypadY = viewHeight * 0.4f;
            float keySize = viewWidth * 0.22f;
            float keySpacing = keySize * 0.15f;
            float totalW = 3 * keySize + 2 * keySpacing;
            float padOffsetX = (viewWidth - totalW) / 2;

            // 遍历键盘区域，检测触摸点是否在某个按键内
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 3; col++) {
                    float left = padOffsetX + col * (keySize + keySpacing);
                    float top = keypadY + row * (keySize + keySpacing);
                    float right = left + keySize;
                    float bottom = top + keySize;

                    if (x >= left && x <= right && y >= top && y <= bottom) {
                        handleKeyPress(row, col);
                        performClick();
                        return true;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 处理键盘按键事件
     *
     * <p>按键映射：</p>
     * <ul>
     *   <li>前3行（row 0-2）：数字1-9，追加到输入缓冲区（不超过MAX_DIGITS位）</li>
     *   <li>第4行第0列：清除键，删除输入缓冲区最后一个字符</li>
     *   <li>第4行第1列：数字0，追加到输入缓冲区</li>
     *   <li>第4行第2列：确定键，将输入缓冲区转为数字提交猜测，然后清空缓冲区</li>
     * </ul>
     *
     * @param row 按键行号（0-3）
     * @param col 按键列号（0-2）
     */
    private void handleKeyPress(int row, int col) {
        if (row < 3) {
            // 数字键1-9
            int num = row * 3 + col + 1;
            if (inputBuffer.length() < MAX_DIGITS) {
                inputBuffer.append(num);
                invalidate();
            }
        } else if (col == 0) {
            // 清除键：删除最后一个字符
            if (inputBuffer.length() > 0) {
                inputBuffer.setLength(inputBuffer.length() - 1);
            }
            invalidate();
        } else if (col == 1) {
            // 数字0
            if (inputBuffer.length() < MAX_DIGITS) {
                inputBuffer.append('0');
                invalidate();
            }
        } else {
            // 确定键：提交猜测
            if (inputBuffer.length() > 0) {
                int guess = Integer.parseInt(inputBuffer.toString());
                game.makeGuess(guess);
                inputBuffer.setLength(0);
                invalidate();
            }
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
