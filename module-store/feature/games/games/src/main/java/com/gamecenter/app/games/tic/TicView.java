package com.gamecenter.app.games.tic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 井字棋自定义视图
 *
 * <p>职责：负责井字棋的视觉渲染和触摸交互处理。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>棋盘自动居中，尺寸取 View 宽高的 85%，确保在各种屏幕尺寸下美观</li>
 *   <li>玩家棋子为蓝色X，电脑棋子为红色O，通过不同 Paint 区分</li>
 *   <li>触摸后延迟 400ms 触发电脑落子，模拟"思考"效果</li>
 *   <li>游戏结束后点击棋盘可自动重开，无需按重启按钮</li>
 * </ul>
 */
public class TicView extends View {

    /**
     * 游戏结束回调接口
     */
    public interface OnGameOverListener {
        /**
         * 游戏结束时调用
         * @param winner 获胜方（TicGame.PLAYER / TicGame.COMPUTER / TicGame.EMPTY 平局）
         */
        void onGameOver(int winner);
    }

    /** 游戏逻辑对象 */
    private TicGame game;

    /** 棋盘网格线画笔（白色） */
    private Paint gridPaint;

    /** 玩家X棋子画笔（蓝色） */
    private Paint xPaint;

    /** 电脑O棋子画笔（红色） */
    private Paint oPaint;

    /** 提示文字画笔（白色，居中粗体） */
    private Paint textPaint;

    /** 游戏结束监听器 */
    private OnGameOverListener gameOverListener;

    /** 视图宽度 */
    private float viewWidth;

    /** 视图高度 */
    private float viewHeight;

    /** 每个格子的大小 */
    private float cellSize;

    /** 棋盘水平偏移量，用于居中显示 */
    private float offsetX;

    /** 棋盘垂直偏移量，用于居中显示 */
    private float offsetY;

    public TicView(Context context) {
        super(context);
        init();
    }

    public TicView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化所有画笔
     *
     * <p>所有画笔均启用抗锯齿（ANTI_ALIAS_FLAG），确保线条和文字边缘平滑。</p>
     */
    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.WHITE);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(8);
        gridPaint.setStrokeCap(Paint.Cap.ROUND);

        xPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xPaint.setColor(Color.parseColor("#2196F3"));
        xPaint.setStyle(Paint.Style.STROKE);
        xPaint.setStrokeWidth(10);
        xPaint.setStrokeCap(Paint.Cap.ROUND);

        oPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        oPaint.setColor(Color.parseColor("#E53935"));
        oPaint.setStyle(Paint.Style.STROKE);
        oPaint.setStrokeWidth(10);
        oPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    /**
     * 设置游戏逻辑对象
     * @param game TicGame 实例
     */
    public void setGame(TicGame game) {
        this.game = game;
    }

    /**
     * 设置游戏结束监听器
     * @param listener 游戏结束回调
     */
    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }

    /**
     * 视图尺寸变化时重新计算棋盘布局
     *
     * <p>棋盘大小取宽高的较小值的 85%，并居中偏移。
     * offsetY 额外上移 cellSize * 0.1f，为底部提示文字留出空间。</p>
     *
     * @param w 新宽度
     * @param h 新高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        float size = Math.min(w, h) * 0.85f;
        cellSize = size / 3;
        offsetX = (w - size) / 2;
        offsetY = (h - size) / 2 - cellSize * 0.1f;
    }

    /**
     * 绘制棋盘、棋子和游戏状态提示
     *
     * <p>绘制顺序：</p>
     * <ol>
     *   <li>深色背景</li>
     *   <li>内部网格线（2条竖线 + 2条横线）</li>
     *   <li>外边框（较粗）</li>
     *   <li>棋子（X 或 O）</li>
     *   <li>游戏状态文字（胜负/平局提示、电脑思考提示）</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#1E1E32"));

        for (int i = 1; i < 3; i++) {
            float x = offsetX + i * cellSize;
            canvas.drawLine(x, offsetY, x, offsetY + cellSize * 3, gridPaint);
            float y = offsetY + i * cellSize;
            canvas.drawLine(offsetX, y, offsetX + cellSize * 3, y, gridPaint);
        }

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(12);
        borderPaint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawRect(offsetX, offsetY, offsetX + cellSize * 3, offsetY + cellSize * 3, borderPaint);

        int[][] board = game.getBoard();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                float cx = offsetX + x * cellSize + cellSize / 2;
                float cy = offsetY + y * cellSize + cellSize / 2;
                float padding = cellSize * 0.25f;

                if (board[y][x] == TicGame.PLAYER) {
                    canvas.drawLine(cx - padding, cy - padding, cx + padding, cy + padding, xPaint);
                    canvas.drawLine(cx + padding, cy - padding, cx - padding, cy + padding, xPaint);
                } else if (board[y][x] == TicGame.COMPUTER) {
                    canvas.drawCircle(cx, cy, padding, oPaint);
                }
            }
        }

        if (game.isGameOver()) {
            String msg;
            int winner = game.getWinner();
            if (winner == TicGame.PLAYER) {
                msg = "你赢了!";
                textPaint.setColor(Color.parseColor("#4CAF50"));
            } else if (winner == TicGame.COMPUTER) {
                msg = "电脑赢了!";
                textPaint.setColor(Color.parseColor("#E53935"));
            } else {
                msg = "平局!";
                textPaint.setColor(Color.parseColor("#FF9800"));
            }
            canvas.drawText(msg, viewWidth / 2, offsetY + cellSize * 3 + cellSize * 0.6f, textPaint);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(30);
            canvas.drawText("点击重玩", viewWidth / 2, offsetY + cellSize * 3 + cellSize * 0.9f, textPaint);
            if (gameOverListener != null) {
                gameOverListener.onGameOver(winner);
            }
        } else if (game.getCurrentTurn() == TicGame.COMPUTER) {
            textPaint.setColor(Color.WHITE);
            canvas.drawText("电脑思考中...", viewWidth / 2, offsetY + cellSize * 3 + cellSize * 0.6f, textPaint);
        }
    }

    /**
     * 处理触摸事件
     *
     * <p>交互逻辑：</p>
     * <ul>
     *   <li>游戏结束时：点击任意位置重置游戏</li>
     *   <li>非玩家回合时：忽略触摸</li>
     *   <li>玩家回合时：根据触摸坐标计算格子位置，调用 placePiece 落子，
     *       然后延迟 400ms 触发电脑落子（模拟思考时间）</li>
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
                invalidate();
                performClick();
                return true;
            }

            if (game.getCurrentTurn() != TicGame.PLAYER) return true;

            float x = event.getX() - offsetX;
            float y = event.getY() - offsetY;
            int col = (int) (x / cellSize);
            int row = (int) (y / cellSize);

            if (col >= 0 && col < 3 && row >= 0 && row < 3) {
                if (game.placePiece(col, row)) {
                    invalidate();
                    postDelayed(() -> {
                        game.computerMove();
                        invalidate();
                    }, 400);
                }
            }
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
