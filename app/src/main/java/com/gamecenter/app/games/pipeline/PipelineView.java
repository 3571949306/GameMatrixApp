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
 * 玩法：
 * - 5x5 网格，每格有管道
 * - 点击管道旋转方向（90度）
 * - 接通水源（左边缘）到出口（右边缘）
 * - 接通后显示蓝色水流
 *
 * 管道类型：
 * - EMPTY=0        空
 * - HORIZONTAL=1   水平 ─
 * - VERTICAL=2     垂直 │
 * - CORNER_TL=3    左上角 ┐
 * - CORNER_TR=4    右上角 ┌
 * - CORNER_BR=5    右下角 ┘
 * - CORNER_BL=6    左下角 └
 * - CROSS=7        十字 ─┼─
 * - T_UP/T_RIGHT/T_DOWN/T_LEFT=8-11  T形
 *
 * 颜色：
 * - 管道：灰色
 * - 已通水：蓝色
 */
public class PipelineView extends View {

    private PipelineGame game;
    private Paint pipePaint;
    private Paint emptyPaint;
    private Paint waterPaint;
    private float cellSize;
    private float offsetX, offsetY;
    private OnLevelCompleteListener listener;

    /** 关卡完成回调接口 */
    public interface OnLevelCompleteListener {
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

    public void setGame(PipelineGame game) {
        this.game = game;
    }

    public void setOnLevelCompleteListener(OnLevelCompleteListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (game == null) return;
        int size = Math.min(w, h) - 32;
        cellSize = size / (float) game.getSize();
        offsetX = (w - cellSize * game.getSize()) / 2;
        offsetY = (h - cellSize * game.getSize()) / 2;
    }

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

                // 绘制格子边框
                canvas.drawRect(left, top, right, bottom, emptyPaint);

                int pipe = game.getPipe(x, y);
                if (pipe == PipelineGame.EMPTY) continue;

                // 根据是否有水流选择颜色
                boolean hasWater = game.hasWater(x, y);
                Paint fillPaint = hasWater ? waterPaint : pipePaint;

                // 绘制不同类型的管道
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
                        canvas.drawArc(new RectF(left, top, right, bottom), 0, 90, true, fillPaint);
                        break;
                    case PipelineGame.CORNER_TR:
                        canvas.drawArc(new RectF(left, top, right, bottom), 90, 90, true, fillPaint);
                        break;
                    case PipelineGame.CORNER_BR:
                        canvas.drawArc(new RectF(left, top, right, bottom), 180, 90, true, fillPaint);
                        break;
                    case PipelineGame.CORNER_BL:
                        canvas.drawArc(new RectF(left, top, right, bottom), 270, 90, true, fillPaint);
                        break;
                    case PipelineGame.CROSS:
                        canvas.drawRect(centerX - cellSize / 2, centerY - pipeWidth / 2,
                                centerX + cellSize / 2, centerY + pipeWidth / 2, fillPaint);
                        canvas.drawRect(centerX - pipeWidth / 2, centerY - cellSize / 2,
                                centerX + pipeWidth / 2, centerY + cellSize / 2, fillPaint);
                        break;
                    case PipelineGame.T_UP:
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX() - offsetX;
            float y = event.getY() - offsetY;
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