package com.gamecenter.app.games.memory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 记忆翻牌游戏的自定义绘制视图
 *
 * <p>负责4x4卡牌网格的绘制和触摸交互处理。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用自定义View直接绘制卡牌，而非RecyclerView/GridView，
 *       以便精确控制翻牌动画和布局</li>
 *   <li>卡牌背面显示"?"，正面显示emoji图案</li>
 *   <li>不匹配时延迟800ms翻回，匹配时延迟400ms确认，给玩家视觉反馈时间</li>
 *   <li>通过OnCardFlipListener回调通知Activity更新分数等信息</li>
 * </ul>
 */
public class MemoryView extends View {

    /** 游戏逻辑对象 */
    private MemoryGame game;
    /** 卡牌背面画笔（深蓝色） */
    private Paint cardBackPaint;
    /** 卡牌正面画笔（浅黄色） */
    private Paint cardFrontPaint;
    /** 已匹配卡牌画笔（浅绿色） */
    private Paint cardMatchedPaint;
    /** 底部信息文字画笔 */
    private Paint textPaint;
    /** emoji图案画笔 */
    private Paint emojiPaint;
    /** 卡牌背面"?"文字画笔 */
    private Paint qPaint;
    /** 游戏结束提示文字画笔 */
    private Paint infoPaint;

    /** 视图总宽度 */
    private float viewWidth;
    /** 视图总高度 */
    private float viewHeight;
    /** 单张卡牌尺寸（正方形边长） */
    private float cardSize;
    /** 卡牌间距 */
    private float cardSpacing;
    /** 网格水平偏移量（用于居中） */
    private float offsetX;
    /** 网格垂直偏移量（用于居中） */
    private float offsetY;

    /** emoji图案数组，用于显示卡牌正面图案，索引对应board中的值 */
    private static final String[] EMOJIS = {"🐶","🐱","🐼","🐨","🐰","🦊","🐸","🐵","🦁","🐯","🐮","🐷","🐭","🐹","🐻","🐔"};

    public MemoryView(Context context) {
        super(context);
        init();
    }

    public MemoryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔
     *
     * <p>分别设置卡牌背面（深蓝#1565C0）、正面（浅黄#FFF9C4）、
     * 已匹配（浅绿#A5D6A7）以及各种文字的画笔属性。</p>
     */
    private void init() {
        cardBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBackPaint.setColor(Color.parseColor("#1565C0"));
        cardBackPaint.setStyle(Paint.Style.FILL);

        cardFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardFrontPaint.setColor(Color.parseColor("#FFF9C4"));
        cardFrontPaint.setStyle(Paint.Style.FILL);

        cardMatchedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardMatchedPaint.setColor(Color.parseColor("#A5D6A7"));
        cardMatchedPaint.setStyle(Paint.Style.FILL);

        qPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        qPaint.setColor(Color.WHITE);
        qPaint.setTextAlign(Paint.Align.CENTER);
        qPaint.setFakeBoldText(true);

        emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emojiPaint.setTextAlign(Paint.Align.CENTER);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        infoPaint.setColor(Color.parseColor("#AAAAAA"));
        infoPaint.setTextSize(30);
        infoPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 设置游戏逻辑对象
     *
     * @param game MemoryGame实例
     */
    public void setGame(MemoryGame game) {
        this.game = game;
    }

    /** 卡牌翻转事件监听器 */
    private OnCardFlipListener flipListener;

    /**
     * 卡牌翻转事件回调接口
     */
    public interface OnCardFlipListener {
        /** 当卡牌翻转时回调 */
        void onCardFlipped();
    }

    /**
     * 设置卡牌翻转事件监听器
     *
     * @param l 监听器实例
     */
    public void setOnCardFlipListener(OnCardFlipListener l) {
        this.flipListener = l;
    }

    /**
     * 视图尺寸变化时重新计算布局参数
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
        if (game == null) return;
        recalcLayout();
    }

    /**
     * 重新计算卡牌布局参数
     *
     * <p>根据视图尺寸计算每张卡牌的大小和间距，
     * 确保网格在视图中居中显示。卡牌区域占视图高度的82%，
     * 剩余空间用于显示分数等信息。</p>
     */
    private void recalcLayout() {
        int cols = MemoryGame.COLS;
        int rows = MemoryGame.ROWS;
        float margin = 16;
        float cardW = (viewWidth - margin * 2) / cols;
        float cardH = (viewHeight * 0.82f - margin * 2) / rows;
        // 取宽高方向上较小的卡牌尺寸，保证正方形卡牌不超出视图
        cardSize = Math.min(cardW, cardH) * 0.94f;
        cardSpacing = cardSize * 0.06f;
        float totalW = cols * (cardSize + cardSpacing) - cardSpacing;
        float totalH = rows * (cardSize + cardSpacing) - cardSpacing;
        // 计算居中偏移量
        offsetX = (viewWidth - totalW) / 2;
        offsetY = (viewHeight * 0.82f - totalH) / 2 + 8;

        float emojiSize = cardSize * 0.62f;
        emojiPaint.setTextSize(emojiSize);
        qPaint.setTextSize(cardSize * 0.35f);
    }

    /**
     * 绘制游戏界面
     *
     * <p>绘制流程：</p>
     * <ol>
     *   <li>绘制深色背景（#1A1A2E）</li>
     *   <li>遍历网格，根据卡牌状态绘制背面（蓝色+?）、正面（浅黄+emoji）或已匹配（浅绿+emoji）</li>
     *   <li>在网格下方绘制匹配进度</li>
     *   <li>如果游戏结束，绘制完成提示和得分</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#1A1A2E"));

        int cols = MemoryGame.COLS;
        int rows = MemoryGame.ROWS;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float left = offsetX + x * (cardSize + cardSpacing);
                float top = offsetY + y * (cardSize + cardSpacing);
                float right = left + cardSize;

                if (game.isRevealed(x, y)) {
                    // 已翻开：根据是否匹配选择背景色
                    Paint bg = game.isMatched(x, y) ? cardMatchedPaint : cardFrontPaint;
                    canvas.drawRoundRect(left, top, right, top + cardSize, 12, 12, bg);

                    int val = game.getCardValue(x, y);
                    String emoji = EMOJIS[val % EMOJIS.length];
                    float cx = left + cardSize / 2;
                    // 垂直居中：通过ascent/descent计算基线偏移
                    float cy = top + cardSize / 2 - (emojiPaint.descent() + emojiPaint.ascent()) / 2;
                    canvas.drawText(emoji, cx, cy, emojiPaint);
                } else {
                    // 未翻开：绘制蓝色背面和"?"标记
                    canvas.drawRoundRect(left, top, right, top + cardSize, 12, 12, cardBackPaint);
                    float cx = left + cardSize / 2;
                    float cy = top + cardSize / 2 - (qPaint.descent() + qPaint.ascent()) / 2;
                    canvas.drawText("?", cx, cy, qPaint);
                }
            }
        }

        // 绘制底部匹配进度信息
        float infoY = offsetY + rows * (cardSize + cardSpacing) + 32;
        canvas.drawText("已匹配: " + game.getMatched() + "/" + MemoryGame.PAIRS,
                viewWidth / 2, infoY, textPaint);

        if (game.isGameOver()) {
            // 游戏结束：绘制完成提示和最终得分
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.parseColor("#4CAF50"));
            overPaint.setTextSize(44);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            canvas.drawText("🎉 全部找到!", viewWidth / 2, infoY + 42, overPaint);
            infoPaint.setTextSize(32);
            canvas.drawText("得分: " + game.getScore() + "  点击重新开始", viewWidth / 2, infoY + 78, infoPaint);
        }
    }

    /**
     * 处理触摸事件
     *
     * <p>交互逻辑：</p>
     * <ol>
     *   <li>游戏结束后点击任意位置重新开始</li>
     *   <li>等待状态（不匹配牌未翻回）时忽略触摸</li>
     *   <li>将触摸坐标转换为网格坐标，判断是否在有效卡牌上</li>
     *   <li>翻牌后根据结果：
     *     <ul>
     *       <li>不匹配：延迟800ms后翻回</li>
     *       <li>匹配成功：延迟400ms后确认匹配</li>
     *       <li>第一张牌：立即显示</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param event 触摸事件
     * @return 始终返回true表示消费了事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
        if (game == null) return true;

        if (game.isWaiting()) return true;

        if (game.isGameOver()) {
            game.reset();
            recalcLayout();
            invalidate();
            performClick();
            if (flipListener != null) flipListener.onCardFlipped();
            return true;
        }

        // 将触摸坐标转换为相对于网格的坐标
        float x = event.getX() - offsetX;
        float y = event.getY() - offsetY;
        int col = (int) (x / (cardSize + cardSpacing));
        int row = (int) (y / (cardSize + cardSpacing));

        if (col < 0 || col >= MemoryGame.COLS || row < 0 || row >= MemoryGame.ROWS) return true;

        if (!game.canFlip(col, row)) return true;

        game.flipCard(col, row);
        invalidate();

        if (game.isWaiting()) {
            // 不匹配：延迟800ms后翻回卡牌
            postDelayed(() -> {
                game.hideMismatch();
                invalidate();
            }, 800);
        } else if (game.lastMatchSuccessful()) {
            // 匹配成功：延迟400ms后确认匹配
            postDelayed(() -> {
                game.confirmMatch();
                invalidate();
                if (flipListener != null) flipListener.onCardFlipped();
            }, 400);
        }

        // 第一张牌翻开后通知监听器
        if (!game.isWaiting() && !game.lastMatchSuccessful()) {
            if (flipListener != null) flipListener.onCardFlipped();
        }

        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
