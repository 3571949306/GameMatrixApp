package com.gamecenter.app.sokoban;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 推箱子地图自定义 View（独立 APK 模块版本）。
 *
 * <p>由宿主 com.gamecenter.app.games.sokoban.SokobanView 迁移而来。
 * 纯渲染组件，无宿主 R 资源依赖，颜色使用 {@link Color#parseColor(String)} 硬编码。</p>
 *
 * <p>绘制推箱子游戏的地图，包括墙壁、地板、箱子、目标点和玩家。</p>
 */
public class SokobanView extends View {

    /** 地图元素类型 */
    public static final int EMPTY = 0;
    public static final int WALL = 1;
    public static final int FLOOR = 2;
    public static final int TARGET = 3;
    public static final int BOX = 4;
    public static final int BOX_ON_TARGET = 5;
    public static final int PLAYER = 6;
    public static final int PLAYER_ON_TARGET = 7;

    private static final int COLOR_BG = Color.parseColor("#F5F0E8");
    private static final int COLOR_WALL = Color.parseColor("#5B8A72");
    private static final int COLOR_FLOOR = Color.parseColor("#E8E0D0");
    private static final int COLOR_TARGET = Color.parseColor("#C44536");
    private static final int COLOR_BOX = Color.parseColor("#8B7355");
    private static final int COLOR_BOX_ON_TARGET = Color.parseColor("#5B8A72");
    private static final int COLOR_PLAYER = Color.parseColor("#2D6A4F");
    private static final int COLOR_BORDER = Color.parseColor("#2D2D2D");

    private Paint wallPaint;
    private Paint floorPaint;
    private Paint targetPaint;
    private Paint boxPaint;
    private Paint boxOnTargetPaint;
    private Paint playerPaint;
    private Paint borderPaint;

    private float cellSize;
    private float boardOffsetX;
    private float boardOffsetY;

    /** 地图数据 */
    private int[][] map;
    private int mapRows;
    private int mapCols;

    public SokobanView(@NonNull Context context) {
        super(context);
        init();
    }

    public SokobanView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SokobanView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        wallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wallPaint.setColor(COLOR_WALL);
        wallPaint.setStyle(Paint.Style.FILL);

        floorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        floorPaint.setColor(COLOR_FLOOR);
        floorPaint.setStyle(Paint.Style.FILL);

        targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        targetPaint.setColor(COLOR_TARGET);
        targetPaint.setStyle(Paint.Style.FILL);

        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setColor(COLOR_BOX);
        boxPaint.setStyle(Paint.Style.FILL);

        boxOnTargetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxOnTargetPaint.setColor(COLOR_BOX_ON_TARGET);
        boxOnTargetPaint.setStyle(Paint.Style.FILL);

        playerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerPaint.setColor(COLOR_PLAYER);
        playerPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(COLOR_BORDER);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
    }

    /**
     * 设置地图数据
     */
    public void setMap(int[][] map) {
        this.map = map;
        this.mapRows = map.length;
        this.mapCols = map[0].length;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (mapRows > 0 && mapCols > 0) {
            int height = (int) (width * ((float) mapRows / mapCols));
            setMeasuredDimension(width, Math.min(height, width));
        } else {
            setMeasuredDimension(width, width);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (map == null || mapRows == 0 || mapCols == 0) return;

        canvas.drawColor(COLOR_BG);

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        float maxCellW = (viewWidth - 20f) / mapCols;
        float maxCellH = (viewHeight - 20f) / mapRows;
        cellSize = Math.min(maxCellW, maxCellH);
        boardOffsetX = (viewWidth - cellSize * mapCols) / 2f;
        boardOffsetY = (viewHeight - cellSize * mapRows) / 2f;

        for (int r = 0; r < mapRows; r++) {
            for (int c = 0; c < mapCols; c++) {
                float left = boardOffsetX + c * cellSize;
                float top = boardOffsetY + r * cellSize;
                RectF rect = new RectF(left + 1, top + 1, left + cellSize - 1, top + cellSize - 1);

                switch (map[r][c]) {
                    case WALL:
                        canvas.drawRect(rect, wallPaint);
                        canvas.drawRect(rect, borderPaint);
                        break;
                    case FLOOR:
                    case PLAYER:
                    case BOX:
                        canvas.drawRect(rect, floorPaint);
                        break;
                    case TARGET:
                    case PLAYER_ON_TARGET:
                        canvas.drawRect(rect, floorPaint);
                        drawTarget(canvas, left, top);
                        break;
                    case BOX_ON_TARGET:
                        canvas.drawRect(rect, floorPaint);
                        drawBox(canvas, left, top, true);
                        break;
                }

                // 绘制箱子
                if (map[r][c] == BOX) {
                    drawBox(canvas, left, top, false);
                }

                // 绘制玩家
                if (map[r][c] == PLAYER || map[r][c] == PLAYER_ON_TARGET) {
                    drawPlayer(canvas, left, top);
                }
            }
        }
    }

    /**
     * 绘制目标点
     */
    private void drawTarget(@NonNull Canvas canvas, float left, float top) {
        float cx = left + cellSize / 2f;
        float cy = top + cellSize / 2f;
        float radius = cellSize * 0.15f;
        targetPaint.setAlpha(180);
        canvas.drawCircle(cx, cy, radius, targetPaint);
        targetPaint.setAlpha(255);
    }

    /**
     * 绘制箱子
     */
    private void drawBox(@NonNull Canvas canvas, float left, float top, boolean onTarget) {
        float margin = cellSize * 0.1f;
        RectF boxRect = new RectF(left + margin, top + margin,
                left + cellSize - margin, top + cellSize - margin);
        Paint paint = onTarget ? boxOnTargetPaint : boxPaint;
        canvas.drawRoundRect(boxRect, 4f, 4f, paint);
        canvas.drawRoundRect(boxRect, 4f, 4f, borderPaint);

        // 绘制 X 装饰
        float cx = left + cellSize / 2f;
        float cy = top + cellSize / 2f;
        float half = cellSize * 0.2f;
        Paint xPaint = new Paint(borderPaint);
        xPaint.setStrokeWidth(2f);
        xPaint.setAlpha(120);
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, xPaint);
        canvas.drawLine(cx + half, cy - half, cx - half, cy + half, xPaint);
    }

    /**
     * 绘制玩家
     */
    private void drawPlayer(@NonNull Canvas canvas, float left, float top) {
        float cx = left + cellSize / 2f;
        float cy = top + cellSize / 2f;
        float radius = cellSize * 0.35f;
        canvas.drawCircle(cx, cy, radius, playerPaint);
        canvas.drawCircle(cx, cy, radius, borderPaint);

        // 眼睛
        Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eyePaint.setColor(Color.WHITE);
        eyePaint.setStyle(Paint.Style.FILL);
        float eyeR = cellSize * 0.06f;
        canvas.drawCircle(cx - radius * 0.3f, cy - radius * 0.15f, eyeR, eyePaint);
        canvas.drawCircle(cx + radius * 0.3f, cy - radius * 0.15f, eyeR, eyePaint);
    }
}
