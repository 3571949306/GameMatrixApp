package com.gamecenter.app.games.pipeline;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.Random;

/**
 * 接水管游戏绘制 View
 *
 * <p>玩法：</p>
 * <ul>
 *   <li>5x5 网格，每格有管道</li>
 *   <li>点击管道旋转方向（90度）</li>
 *   <li>接通水源（左边缘）到出口（右边缘）</li>
 *   <li>接通后显示蓝色水流</li>
 * </ul>
 *
 * <p>管道类型：</p>
 * <ul>
 *   <li>EMPTY=0        空</li>
 *   <li>HORIZONTAL=1   水平 ─</li>
 *   <li>VERTICAL=2     垂直 │</li>
 *   <li>CORNER_TL=3    左上角 ┐</li>
 *   <li>CORNER_TR=4    右上角 ┌</li>
 *   <li>CORNER_BR=5    右下角 ┘</li>
 *   <li>CORNER_BL=6    左下角 └</li>
 *   <li>CROSS=7        十字 ─┼─</li>
 *   <li>T_UP/T_RIGHT/T_DOWN/T_LEFT=8-11  T形</li>
 * </ul>
 *
 * <p>颜色方案：</p>
 * <ul>
 *   <li>管道：灰色（#607D8B）</li>
 *   <li>已通水：蓝色（#03A9F4）</li>
 *   <li>空格边框：浅灰色（#B0BEC5）</li>
 * </ul>
 */
public class PipelineView extends View {

    /** 游戏逻辑对象 */
    private PipelineGame game;
    /** 管道画笔（灰色，未通水） */
    private Paint pipePaint;
    /** 空格边框画笔 */
    private Paint emptyPaint;
    /** 水流画笔（蓝色，已通水） */
    private Paint waterPaint;
    /** 单元格尺寸 */
    private float cellSize;
    /** 网格水平偏移量（居中） */
    private float offsetX, offsetY;
    /** 关卡完成回调监听器 */
    private OnLevelCompleteListener listener;

    /**
     * 关卡完成回调接口
     */
    public interface OnLevelCompleteListener {
        /** 关卡完成时回调 */
        void onComplete();
    }

    public PipelineView(Context context) {
        super(context);
        init();
    }

    public PipelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * 初始化画笔
     *
     * <p>管道画笔为灰色填充，空格画笔为浅灰描边，
     * 水流画笔为蓝色填充。</p>
     */
    private void init() {
        pipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pipePaint.setColor(0xFF607D8B);
        pipePaint.setStyle(Paint.Style.FILL);

        emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emptyPaint.setColor(0xFFB0BEC5);
        emptyPaint.setStyle(Paint.Style.STROKE);
        emptyPaint.setStrokeWidth(4);

        waterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        waterPaint.setColor(0xFF03A9F4);
        waterPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 设置游戏逻辑对象
     *
     * @param game PipelineGame实例
     */
    public void setGame(PipelineGame game) {
        this.game = game;
    }

    /**
     * 设置关卡完成监听器
     *
     * @param listener 监听器实例
     */
    public void setOnLevelCompleteListener(OnLevelCompleteListener listener) {
        this.listener = listener;
    }

    /**
     * 视图尺寸变化时重新计算布局
     *
     * <p>根据视图宽高计算单元格大小，使网格居中显示。</p>
     *
     * @param w 新宽度
     * @param h 新高度
     * @param oldw 旧宽度
     * @param oldh 旧高度
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (game == null) return;
        int size = Math.min(w, h) - 32;
        cellSize = size / (float) game.getSize();
        offsetX = (w - cellSize * game.getSize()) / 2;
        offsetY = (h - cellSize * game.getSize()) / 2;
    }

    /**
     * 绘制游戏界面
     *
     * <p>绘制流程：</p>
     * <ol>
     *   <li>遍历5x5网格</li>
     *   <li>绘制每个格子的边框</li>
     *   <li>根据管道类型绘制对应形状（直管用矩形，弯管用扇形，十字/T型用组合矩形）</li>
     *   <li>根据是否有水流选择灰色或蓝色画笔</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        int size = game.getSize();
        float pipeWidth = cellSize * 0.3f;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float left = offsetX + x * cellSize;
                float top = offsetY + y * cellSize;
                float right = left + cellSize;
                float bottom = top + cellSize;
                float centerX = left + cellSize / 2;
                float centerY = top + cellSize / 2;

                canvas.drawRect(left, top, right, bottom, emptyPaint);

                int pipe = game.getPipe(x, y);
                if (pipe == PipelineGame.EMPTY) continue;

                // 根据是否有水流选择颜色
                boolean hasWater = game.hasWater(x, y);
                Paint fillPaint = hasWater ? waterPaint : pipePaint;

                // 根据管道类型绘制不同形状
                switch (pipe) {
                    case PipelineGame.HORIZONTAL:
                        canvas.drawRect(centerX - cellSize / 2, centerY - pipeWidth / 2,
                                centerX + cellSize / 2, centerY + pipeWidth / 2, fillPaint);
                        break;
                    case PipelineGame.VERTICAL:
                        canvas.drawRect(centerX - pipeWidth / 2, centerY - cellSize / 2,
                                centerX + pipeWidth / 2, centerY + cellSize / 2, fillPaint);
                        break;
                    case PipelineGame.CORNER_TL:
                        // 左上角弯管：从中心向右和向下的扇形
                        canvas.drawArc(new RectF(left, top, right, bottom), 0, 90, true, fillPaint);
                        break;
                    case PipelineGame.CORNER_TR:
                        // 右上角弯管：从中心向左和向下的扇形
                        canvas.drawArc(new RectF(left, top, right, bottom), 90, 90, true, fillPaint);
                        break;
                    case PipelineGame.CORNER_BR:
                        // 右下角弯管：从中心向左和向上的扇形
                        canvas.drawArc(new RectF(left, top, right, bottom), 180, 90, true, fillPaint);
                        break;
                    case PipelineGame.CORNER_BL:
                        // 左下角弯管：从中心向右和向上的扇形
                        canvas.drawArc(new RectF(left, top, right, bottom), 270, 90, true, fillPaint);
                        break;
                    case PipelineGame.CROSS:
                        // 十字管：水平+垂直矩形叠加
                        canvas.drawRect(centerX - cellSize / 2, centerY - pipeWidth / 2,
                                centerX + cellSize / 2, centerY + pipeWidth / 2, fillPaint);
                        canvas.drawRect(centerX - pipeWidth / 2, centerY - cellSize / 2,
                                centerX + pipeWidth / 2, centerY + cellSize / 2, fillPaint);
                        break;
                    case PipelineGame.T_UP:
                        // T型管（向上）：垂直+水平矩形叠加
                        canvas.drawRect(centerX - pipeWidth / 2, centerY - cellSize / 2,
                                centerX + pipeWidth / 2, centerY + cellSize / 2, fillPaint);
                        canvas.drawRect(centerX - cellSize / 2, centerY - pipeWidth / 2,
                                centerX + cellSize / 2, centerY + pipeWidth / 2, fillPaint);
                        break;
                    case PipelineGame.T_RIGHT:
                        canvas.drawRect(centerX - cellSize / 2, centerY - pipeWidth / 2,
                                centerX + cellSize / 2, centerY + pipeWidth / 2, fillPaint);
                        canvas.drawRect(centerX - pipeWidth / 2, centerY - cellSize / 2,
                                centerX + pipeWidth / 2, centerY + cellSize / 2, fillPaint);
                        break;
                    case PipelineGame.T_DOWN:
                        canvas.drawRect(centerX - cellSize / 2, centerY - pipeWidth / 2,
                                centerX + cellSize / 2, centerY + pipeWidth / 2, fillPaint);
                        canvas.drawRect(centerX - pipeWidth / 2, centerY - cellSize / 2,
                                centerX + pipeWidth / 2, centerY + cellSize / 2, fillPaint);
                        break;
                    case PipelineGame.T_LEFT:
                        canvas.drawRect(centerX - cellSize / 2, centerY - pipeWidth / 2,
                                centerX + cellSize / 2, centerY + pipeWidth / 2, fillPaint);
                        canvas.drawRect(centerX - pipeWidth / 2, centerY - cellSize / 2,
                                centerX + pipeWidth / 2, centerY + cellSize / 2, fillPaint);
                        break;
                }
            }
        }
    }

    /**
     * 处理触摸事件
     *
     * <p>点击网格中的格子时旋转该管道，并检测水流是否接通。
     * 如果接通则触发关卡完成回调。</p>
     *
     * @param event 触摸事件
     * @return 始终返回true表示消费了事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX() - offsetX;
            float y = event.getY() - offsetY;
            // 判断触摸点是否在网格范围内
            if (x >= 0 && x < cellSize * game.getSize() && y >= 0 && y < cellSize * game.getSize()) {
                int tx = (int) (x / cellSize);
                int ty = (int) (y / cellSize);
                // 旋转管道并检测是否连通
                game.rotatePipe(tx, ty);
                boolean complete = game.checkWaterFlow();
                if (complete && listener != null) {
                    listener.onComplete();
                }
                invalidate();
            }
        }
        return true;
    }
}
