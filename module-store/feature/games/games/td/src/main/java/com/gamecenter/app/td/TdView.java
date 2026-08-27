package com.gamecenter.app.td;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.gamecenter.app.td.engine.MonsterType;
import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TowerType;

import java.util.ArrayList;
import java.util.List;

/**
 * 塔防「保卫蛋蛋」棋盘渲染视图 — 只读引擎快照，程序化矢量绘制（圆角可爱风，成品级）。
 *
 * 视觉层次：
 *  1. 草地背景（径向渐变 + 确定性植被装饰：花/草丛/蘑菇/岩石/树木）
 *  2. 沙土路径（圆角 + 中部浅色带 + 入口木牌）
 *  3. 可放置格高亮引导 + 蛋蛋（草窝 + 表情 + 受击震屏红环）
 *  4. 塔（差异化造型 + 等级金圈 + 落成弹跳 + 开火炮口闪光）
 *  5. 光束（光晕 + 火箭尾焰）+ 怪物（八种差异化造型 + 弹跳 + 受击闪白 + 状态泡）
 *  6. 击杀冲击波/碎屑/金币飘字、波次横幅、结算光效
 */
public class TdView extends View {

    // ===== 主题色 =====
    private static final int C_BG_DARK = 0xFF2A9950;
    private static final int C_BG_LIGHT = 0xFF45C46F;
    private static final int C_GRID_LINE = 0x733FA75C;
    private static final int C_PATH = 0xFFE9C585;
    private static final int C_PATH_BORDER = 0xFFB98A4E;
    private static final int C_PATH_LIGHT = 0xFFFFE6B3;
    private static final int C_TEXT = 0xFF3B3327;
    private static final int C_TEXT_LIGHT = 0xFFFFF8E1;
    private static final int C_GOLD = 0xFFFFC107;
    private static final int C_GOLD_DARK = 0xFF8D6E20;

    private TdGame game;
    private TowerType selectedType;
    private TdGame.Tower hoverTower;
    private int waveBannerTicks = 0;
    private String waveBannerText = "";
    private final List<Particle> particles = new ArrayList<>();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap towerSprites;
    private Bitmap monsterSprites;
    private float cellSize;
    private float originX, originY;
    private long frameCount = 0;
    private int pressedRow = -1;
    private int pressedCol = -1;
    private int dragStartRow = -1;
    private int dragStartCol = -1;
    private boolean draggingTower;
    private float downX;
    private float downY;
    private int dragTargetRow = -1;
    private int dragTargetCol = -1;
    private boolean dragTargetValid;
    private int paletteDragRow = -1;
    private int paletteDragCol = -1;
    private TowerType paletteDragType;
    private boolean paletteDragValid;
    private final int touchSlop;

    public interface OnTowerActionListener {
        void onTowerPlaced(int row, int col, TowerType type);
        void onTowerSelected(int row, int col);
        void onTowerDeselected();

        /** 棋盘塔拖到另一座塔上时触发；默认空实现保持旧版门面兼容。 */
        default void onTowerDragged(int sourceRow, int sourceCol, int targetRow, int targetCol) { }

        /** 底部塔牌拖到棋盘时触发；默认空实现保持旧版门面兼容。 */
        default void onPaletteTowerDropped(int row, int col, TowerType type) { }
    }

    private OnTowerActionListener listener;

    public TdView(Context context) {
        super(context);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        textPaint.setColor(C_TEXT);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null); // 渐变需要软件层
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void bind(TdGame game) {
        this.game = game;
        particles.clear();
        waveBannerTicks = 0;
        updateBoardGeometry(getWidth(), getHeight());
        invalidate();
    }

    public void setSelectedType(TowerType type) {
        this.selectedType = type;
        invalidate();
    }

    public void setListener(OnTowerActionListener l) {
        this.listener = l;
    }

    /**
     * 动态模块资源由 Fragment 显式传入：TdView 的 Context 可能仍指向宿主，不能直接
     * 通过 getResources() 查找模块图片。缺图时保留下面的程序化绘制作为安全降级。
     */
    public void loadSpriteSheets(Resources moduleResources, int towerResourceId, int monsterResourceId) {
        if (moduleResources == null) return;
        try {
            if (towerResourceId != 0) towerSprites = BitmapFactory.decodeResource(moduleResources, towerResourceId);
            if (monsterResourceId != 0) monsterSprites = BitmapFactory.decodeResource(moduleResources, monsterResourceId);
        } catch (RuntimeException ignored) {
            // 动态模块资源不可用时，继续使用程序化角色，避免影响对局。
            towerSprites = null;
            monsterSprites = null;
        }
        invalidate();
    }

    /** 由 Fragment 在波次开始时调用，触发横幅动画 */
    public void showWaveBanner(int waveNo, int total) {
        waveBannerText = "第 " + waveNo + " 波 ⚔ 共 " + total + " 波";
        waveBannerTicks = 90; // 1.5 秒
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int desiredWidth = widthMode == MeasureSpec.UNSPECIFIED ? getSuggestedMinimumWidth() : widthSize;
        int cols = game == null ? 12 : game.getCols();
        int rows = game == null ? 8 : game.getRows();
        // 预留约一格高度给波次横幅/建塔说明，不再强行撑破父级的 weight 区域。
        int desiredHeight = Math.round(desiredWidth * (rows + 0.9f) / Math.max(1, cols));
        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int measuredHeight = heightMode == MeasureSpec.EXACTLY
                ? heightSize : resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBoardGeometry(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;
        frameCount++;
        updateBoardGeometry(getWidth(), getHeight());
        if (cellSize <= 0f) return;

        drawBackground(canvas);
        drawDecorations(canvas);
        drawPath(canvas);
        drawPlacementHint(canvas);
        drawDragTarget(canvas);
        drawEgg(canvas);
        drawSelectedRange(canvas);
        drawTowers(canvas);
        drawBeams(canvas);
        drawMonsters(canvas);
        drawParticles(canvas);
        drawWaveBanner(canvas);
        drawOverlay(canvas);
    }

    /** 将所有棋盘几何计算集中起来，绘制和触摸始终使用同一套坐标。 */
    private void updateBoardGeometry(int width, int height) {
        if (game == null || width <= 0 || height <= 0) {
            cellSize = 0f;
            return;
        }
        float headerCells = 0.9f;
        cellSize = Math.min(width / (float) game.getCols(),
                height / (game.getRows() + headerCells));
        originX = (width - cellSize * game.getCols()) / 2f;
        float drawingHeight = cellSize * (game.getRows() + headerCells);
        originY = (height - drawingHeight) / 2f + cellSize * headerCells;
    }

    // =====================================================================
    // 背景：多层草地
    // =====================================================================
    private void drawBackground(Canvas canvas) {
        // 径向渐变草地
        gradPaint.setShader(new RadialGradient(getWidth() / 2f, getHeight() / 2f,
                Math.max(getWidth(), getHeight()) * 0.75f, C_BG_LIGHT, C_BG_DARK, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, getWidth(), getHeight(), gradPaint);
        // 方格线
        paint.setColor(C_GRID_LINE);
        paint.setStrokeWidth(Math.max(1f, cellSize * 0.02f));
        int cols = game.getCols(), rows = game.getRows();
        for (int r = 0; r <= rows; r++) {
            float y = originY + r * cellSize;
            canvas.drawLine(originX, y, originX + cols * cellSize, y, paint);
        }
        for (int c = 0; c <= cols; c++) {
            float x = originX + c * cellSize;
            canvas.drawLine(x, originY, x, originY + rows * cellSize, paint);
        }
        // 大块草色斑驳（确定性）
        paint.setColor(0x1445A05A);
        for (int r = 0; r < rows; r += 2) {
            for (int c = 0; c < cols; c += 2) {
                if (game.isPathCell(r, c) || game.isEggCell(r, c)) continue;
                float cx = originX + (c + 0.5f) * cellSize;
                float cy = originY + (r + 0.5f) * cellSize;
                int seed = (r * 131 + c * 57) % 5;
                if (seed == 0) {
                    canvas.drawOval(cx - cellSize * 0.5f, cy - cellSize * 0.18f,
                            cx + cellSize * 0.5f, cy + cellSize * 0.5f, paint);
                }
            }
        }
    }

    // =====================================================================
    // 确定性植被装饰（花/草丛/蘑菇/岩石/树木）
    // =====================================================================
    private void drawDecorations(Canvas canvas) {
        int cols = game.getCols(), rows = game.getRows();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (game.isPathCell(r, c) || game.isEggCell(r, c)) continue;
                float cx = originX + (c + 0.5f) * cellSize;
                float cy = originY + (r + 0.5f) * cellSize;
                int seed = (r * 131 + c * 57) % 100;
                if (seed < 10) drawFlower(canvas, cx, cy);
                else if (seed < 22) drawGrass(canvas, cx, cy, seed);
                else if (seed < 29) drawMushroom(canvas, cx, cy, seed);
                else if (seed < 35) drawRock(canvas, cx, cy, seed);
                else if (seed < 40) drawBush(canvas, cx, cy, seed);
            }
        }
    }

    private void drawFlower(Canvas canvas, float cx, float cy) {
        float s = cellSize * 0.11f;
        int[] petals = {0xFFFF8A80, 0xFFFFAB91, 0xFFFFF176, 0xFFF48FB1, 0xFFBA68C8};
        paint.setColor(petals[(int) ((cx + cy) / cellSize * 7) % petals.length]);
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * 2 * i / 5 + 0.4;
            canvas.drawCircle(cx + (float) Math.cos(a) * s, cy - s * 0.4f + (float) Math.sin(a) * s * 0.8f,
                    s * 0.55f, paint);
        }
        paint.setColor(0xFFFFD54F);
        canvas.drawCircle(cx, cy - s * 0.4f, s * 0.45f, paint);
    }

    private void drawGrass(Canvas canvas, float cx, float cy, int seed) {
        float s = cellSize * 0.12f;
        paint.setColor(0xFF2E7D4F);
        paint.setStrokeWidth(cellSize * 0.035f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        int n = 2 + seed % 2;
        for (int i = 0; i < n; i++) {
            float bx = cx + (i - (n - 1) / 2f) * s * 0.8f;
            float bend = (seed % 2 == 0) ? -0.35f : 0.35f;
            canvas.drawLine(bx, cy + s * 0.5f, bx + bend * s, cy - s * 0.6f, paint);
        }
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(1f);
    }

    private void drawMushroom(Canvas canvas, float cx, float cy, int seed) {
        float s = cellSize * (0.09f + (seed % 3) * 0.015f);
        paint.setColor(0xFFFFF8E1);
        canvas.drawRoundRect(cx - s * 0.3f, cy - s * 0.1f, cx + s * 0.3f, cy + s * 0.55f,
                s * 0.15f, s * 0.15f, paint);
        boolean red = seed % 2 == 0;
        paint.setColor(red ? 0xFFE57373 : 0xFFCE93D8);
        canvas.drawOval(cx - s * 0.62f, cy - s * 0.55f, cx + s * 0.62f, cy + s * 0.12f, paint);
        paint.setColor(0xCCFFFFFF);
        canvas.drawCircle(cx - s * 0.3f, cy - s * 0.3f, s * 0.1f, paint);
        canvas.drawCircle(cx + s * 0.25f, cy - s * 0.12f, s * 0.08f, paint);
    }

    private void drawRock(Canvas canvas, float cx, float cy, int seed) {
        float s = cellSize * (0.1f + (seed % 3) * 0.02f);
        paint.setColor(0xFF9E9E9E);
        canvas.drawOval(cx - s, cy - s * 0.55f, cx + s, cy + s * 0.55f, paint);
        paint.setColor(0xFFBDBDBD);
        canvas.drawOval(cx - s * 0.7f, cy - s * 0.45f, cx + s * 0.1f, cy + s * 0.2f, paint);
    }

    private void drawBush(Canvas canvas, float cx, float cy, int seed) {
        float s = cellSize * (0.13f + (seed % 2) * 0.02f);
        paint.setColor(0xFF2E7D32);
        canvas.drawCircle(cx - s * 0.5f, cy + s * 0.1f, s * 0.55f, paint);
        canvas.drawCircle(cx + s * 0.5f, cy + s * 0.1f, s * 0.55f, paint);
        canvas.drawCircle(cx, cy - s * 0.35f, s * 0.6f, paint);
        paint.setColor(0xFF43A047);
        canvas.drawCircle(cx - s * 0.2f, cy - s * 0.3f, s * 0.32f, paint);
    }

    // =====================================================================
    // 路径：圆角沙土 + 中部浅带 + 入口木牌
    // =====================================================================
    private void drawPath(Canvas canvas) {
        for (int[][] route : game.getPaths()) {
            drawRoute(canvas, route);
        }
    }

    private void drawRoute(Canvas canvas, int[][] path) {
        if (path.length < 2) return;
        float[] xs = new float[path.length];
        float[] ys = new float[path.length];
        for (int i = 0; i < path.length; i++) {
            xs[i] = originX + (path[i][1] + 0.5f) * cellSize;
            ys[i] = originY + (path[i][0] + 0.5f) * cellSize;
        }
        Path p = new Path();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < path.length; i++) p.lineTo(xs[i], ys[i]);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        // 外描边
        paint.setColor(C_PATH_BORDER);
        paint.setStrokeWidth(cellSize * 0.86f);
        canvas.drawPath(p, paint);
        // 沙土主体
        paint.setColor(C_PATH);
        paint.setStrokeWidth(cellSize * 0.7f);
        canvas.drawPath(p, paint);
        // 中部浅带
        paint.setColor(C_PATH_LIGHT);
        paint.setStrokeWidth(cellSize * 0.26f);
        canvas.drawPath(p, paint);
        paint.setStyle(Paint.Style.FILL);

        // 泥土斑点
        paint.setColor(0x30996B30);
        for (int i = 2; i < path.length; i += 3) {
            int c = path[i][1], r = path[i][0];
            float cx = originX + (c + 0.5f) * cellSize;
            float cy = originY + (r + 0.5f) * cellSize;
            int seed = (i * 7) % 3;
            canvas.drawCircle(cx - cellSize * 0.18f, cy + cellSize * 0.1f,
                    cellSize * (0.06f + seed * 0.012f), paint);
            canvas.drawCircle(cx + cellSize * 0.16f, cy - cellSize * 0.12f,
                    cellSize * (0.045f + seed * 0.008f), paint);
        }

        // 方向箭头（半透明）
        paint.setColor(0x59FFFFFF);
        for (int i = 1; i < path.length; i += Math.max(1, path.length / 8)) {
            float ax = (xs[i - 1] + xs[i]) / 2f, ay = (ys[i - 1] + ys[i]) / 2f;
            float dx = xs[i] - xs[i - 1], dy = ys[i] - ys[i - 1];
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.001f) continue;
            float ux = dx / len, uy = dy / len;
            float s = cellSize * 0.13f;
            Path arrow = new Path();
            arrow.moveTo(ax + ux * s, ay + uy * s);
            arrow.lineTo(ax - uy * s * 0.6f - ux * s * 0.5f, ay + ux * s * 0.6f - uy * s * 0.5f);
            arrow.lineTo(ax + uy * s * 0.6f - ux * s * 0.5f, ay - ux * s * 0.6f - uy * s * 0.5f);
            arrow.close();
            canvas.drawPath(arrow, paint);
        }

        // 入口木牌
        int c0 = path[0][1], r0 = path[0][0];
        float ex = originX + (c0 + 0.5f) * cellSize;
        float ey = originY + (r0 + 0.5f) * cellSize;
        paint.setColor(0xFF8D6E63);
        canvas.drawRoundRect(ex - cellSize * 0.22f, ey - cellSize * 0.34f,
                ex + cellSize * 0.22f, ey + cellSize * 0.42f, cellSize * 0.06f, cellSize * 0.06f, paint);
        paint.setColor(0xFFFFE0B2);
        canvas.drawRoundRect(ex - cellSize * 0.14f, ey - cellSize * 0.22f,
                ex + cellSize * 0.14f, ey + cellSize * 0.18f, cellSize * 0.04f, cellSize * 0.04f, paint);
        textPaint.setTextSize(cellSize * 0.26f);
        textPaint.setColor(0xFF6D4C41);
        textPaint.setFakeBoldText(true);
        canvas.drawText("入", ex, ey + cellSize * 0.03f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(C_TEXT);
    }

    // =====================================================================
    // 可放置格：常驻弱轮廓 + 选塔后的高对比状态
    // =====================================================================
    private void drawPlacementHint(Canvas canvas) {
        boolean selected = selectedType != null;
        boolean affordable = selected && game.getCoin() >= selectedType.baseCost;
        for (int r = 0; r < game.getRows(); r++) {
            for (int c = 0; c < game.getCols(); c++) {
                if (game.isPathCell(r, c) || game.isEggCell(r, c)
                        || game.getTowerAt(r, c) != null) continue;
                float x = originX + c * cellSize;
                float y = originY + r * cellSize;
                boolean pressed = r == pressedRow && c == pressedCol;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(!selected ? 0x1F7EE6C0
                        : pressed ? 0x9CFFD54F : affordable ? 0x7A38D9A9 : 0x70EF8A80);
                canvas.drawRoundRect(x + cellSize * 0.08f, y + cellSize * 0.08f,
                        x + cellSize * 0.92f, y + cellSize * 0.92f, cellSize * 0.1f, cellSize * 0.1f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, cellSize * (selected ? 0.045f : 0.025f)));
                paint.setColor(!selected ? 0x7AB7F7E4
                        : pressed ? 0xFFFFF3B0 : affordable ? 0xFF8DFFD5 : 0xFFFFAB91);
                canvas.drawRoundRect(x + cellSize * 0.11f, y + cellSize * 0.11f,
                        x + cellSize * 0.89f, y + cellSize * 0.89f, cellSize * 0.08f, cellSize * 0.08f, paint);
                if (selected && cellSize >= 22f) {
                    textPaint.setTextSize(cellSize * 0.29f);
                    textPaint.setColor(affordable ? 0xFFECFFF8 : 0xFFFFEDEA);
                    textPaint.setFakeBoldText(true);
                    canvas.drawText(affordable ? "+" : "×", x + cellSize * 0.5f,
                            y + cellSize * 0.61f, textPaint);
                    textPaint.setFakeBoldText(false);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
        // 选塔后将操作状态放进预留的顶部区域，避免被父布局裁切。
        paint.setColor(!selected ? 0xC0306F63 : affordable ? 0xD02E7D5B : 0xD0A0443E);
        canvas.drawRoundRect(originX + cellSize * 0.3f, originY - cellSize * 0.55f,
                originX + cellSize * (game.getCols() - 0.3f), originY - cellSize * 0.1f,
                cellSize * 0.18f, cellSize * 0.18f, paint);
        textPaint.setTextSize(cellSize * 0.32f);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);
        String hint = !selected ? "空格可建塔 · 先从下方选择防御塔"
                : affordable ? "点击发光格放置：" + selectedType.displayName
                : "金币不足：" + selectedType.displayName + " 需要 ₿" + selectedType.baseCost;
        canvas.drawText(hint,
                originX + cellSize * game.getCols() / 2f, originY - cellSize * 0.24f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(C_TEXT);
    }

    /** 拖拽时的落点预览，绿色表示可合成/建造，红色表示会被规则拒绝。 */
    private void drawDragTarget(Canvas canvas) {
        int row = draggingTower ? dragTargetRow : paletteDragRow;
        int col = draggingTower ? dragTargetCol : paletteDragCol;
        if (row < 0 || col < 0 || game == null) return;
        float x = originX + col * cellSize;
        float y = originY + row * cellSize;
        int color = (draggingTower ? dragTargetValid : paletteDragValid)
                ? 0x9A65E6A5 : 0x9AE57373;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRoundRect(x + cellSize * 0.06f, y + cellSize * 0.06f,
                x + cellSize * 0.94f, y + cellSize * 0.94f,
                cellSize * 0.14f, cellSize * 0.14f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(cellSize * 0.07f);
        paint.setColor((draggingTower ? dragTargetValid : paletteDragValid)
                ? 0xFFE8FFF0 : 0xFFFFD0D0);
        canvas.drawRoundRect(x + cellSize * 0.1f, y + cellSize * 0.1f,
                x + cellSize * 0.9f, y + cellSize * 0.9f,
                cellSize * 0.11f, cellSize * 0.11f, paint);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(cellSize * 0.32f);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);
        canvas.drawText((draggingTower ? dragTargetValid : paletteDragValid) ? "合" : "×",
                x + cellSize * 0.5f, y + cellSize * 0.62f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(C_TEXT);
    }

    // =====================================================================
    // 蛋蛋：草窝 + 大眼萌脸 + 受击震屏
    // =====================================================================
    private void drawEgg(Canvas canvas) {
        float cx = originX + (game.getEggCol() + 0.5f) * cellSize;
        float cy = originY + (game.getEggRow() + 0.5f) * cellSize;
        float r = cellSize * 0.46f;
        // 受击瞬时态与慢性表情分离：抖动只看 eggHitTimer 窗口；
        // 嘴形按剩余血量比例分级，不再用「累计掉过血」导致首伤后永远哭脸。
        boolean justHit = game.getEggHitTimer() > 0f;
        float hpRatio = game.getMaxMascotHp() <= 0 ? 1f
                : (float) game.getMascotHp() / game.getMaxMascotHp();
        float shake = justHit && (frameCount % 8 < 3) ? cellSize * 0.03f : 0f;
        // 草窝（底部）
        paint.setColor(0xFF7C8A38);
        for (int i = -2; i <= 2; i++) {
            canvas.drawCircle(cx + i * r * 0.3f, cy + r * 1.02f, r * 0.28f, paint);
        }
        paint.setColor(0xFF9CB44E);
        canvas.drawOval(cx - r * 1.05f, cy + r * 0.78f, cx + r * 1.05f, cy + r * 1.18f, paint);
        // 蛋身（渐变）
        gradPaint.setShader(new RadialGradient(cx + shake, cy - r * 0.3f, r * 1.7f,
                0xFFFFFFFF, 0xFFFFE0A0, Shader.TileMode.CLAMP));
        canvas.drawOval(cx - r + shake, cy - r * 1.18f, cx + r + shake, cy + r * 1.08f, gradPaint);
        gradPaint.setShader(null);
        // 蛋壳斑点
        paint.setColor(0x55D7A86E);
        canvas.drawCircle(cx - r * 0.5f + shake, cy + r * 0.1f, r * 0.1f, paint);
        canvas.drawCircle(cx + r * 0.45f + shake, cy - r * 0.15f, r * 0.12f, paint);
        canvas.drawCircle(cx + r * 0.05f + shake, cy + r * 0.55f, r * 0.08f, paint);
        // 腮红
        paint.setColor(0x66FF8A80);
        canvas.drawCircle(cx - r * 0.5f + shake, cy + r * 0.32f, r * 0.16f, paint);
        canvas.drawCircle(cx + r * 0.5f + shake, cy + r * 0.32f, r * 0.16f, paint);
        // 大眼（白底 + 黑瞳 + 高光）
        float eyeY = cy - r * 0.05f;
        float look = Math.min(1f, Math.max(-1f, (float) Math.sin(frameCount * 0.01)));
        for (int side = -1; side <= 1; side += 2) {
            float ex0 = cx + side * r * 0.32f + shake;
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(ex0, eyeY, r * 0.2f, paint);
            paint.setColor(C_TEXT);
            canvas.drawCircle(ex0 + look * r * 0.05f, eyeY, r * 0.11f, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(ex0 + look * r * 0.05f - r * 0.04f, eyeY - r * 0.04f, r * 0.04f, paint);
        }
        // 嘴
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(r * 0.07f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(C_TEXT);
        if (justHit || hpRatio < 0.5f) {
            // 正被攻击或重伤：o 形痛叫嘴
            canvas.drawOval(cx - r * 0.12f + shake, cy + r * 0.28f,
                    cx + r * 0.12f + shake, cy + r * 0.46f, paint);
        } else if (hpRatio < 0.999f) {
            // 中等伤情：抿平的小嘴
            Path flat = new Path();
            flat.moveTo(cx - r * 0.18f + shake, cy + r * 0.36f);
            flat.quadTo(cx + shake, cy + r * 0.40f, cx + r * 0.18f + shake, cy + r * 0.36f);
            canvas.drawPath(flat, paint);
        } else {
            Path mouth = new Path();
            mouth.moveTo(cx - r * 0.2f + shake, cy + r * 0.32f);
            mouth.quadTo(cx + shake, cy + r * 0.48f, cx + r * 0.2f + shake, cy + r * 0.32f);
            canvas.drawPath(mouth, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.BUTT);
        // 呆毛
        paint.setColor(0xFF6D4C41);
        paint.setStrokeWidth(r * 0.06f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        Path hair = new Path();
        float hx = cx + shake;
        hair.moveTo(hx, cy - r * 1.1f);
        hair.quadTo(hx + r * 0.18f, cy - r * 1.55f, hx + r * 0.1f, cy - r * 1.65f);
        hair.quadTo(hx + r * 0.34f, cy - r * 1.5f, hx + r * 0.2f, cy - r * 1.2f);
        canvas.drawPath(hair, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(1f);
        // 受击扩散红环（震屏）
        float hit = game.getEggHitTimer();
        if (hit > 0f) {
            float progress = (0.6f - hit) / 0.6f;
            paint.setColor((int) (190 * (1f - progress)) << 24 | 0x00E53935);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(cellSize * 0.07f * (1f + progress));
            canvas.drawCircle(cx, cy, r * (1.25f + progress * 1.6f), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    // =====================================================================
    // 塔：底座 + 差异化造型 + 等级金圈 + 落成弹跳 + 开火闪光
    // =====================================================================
    private void drawTowers(Canvas canvas) {
        for (TdGame.Tower t : game.getTowers()) {
            float cx = originX + (t.col + 0.5f) * cellSize;
            float cy = originY + (t.row + 0.5f) * cellSize;
            // 塔体留出明显轮廓，避免在高分辨率小格子上被压成“彩色小点”。
            float base = cellSize * 0.39f;
            // 等级体型放大
            if (t.level >= 2) base *= 1.06f;
            if (t.level >= 3) base *= 1.1f;
            // 落成弹跳动画
            float k = 1f;
            if (t.buildAge < 10) {
                float p = t.buildAge / 10f;
                k = 0.45f + 0.55f * (1f - (1f - p) * (1f - p)); // ease-out
            }
            if (k < 1f) {
                canvas.save();
                canvas.translate(cx, cy);
                canvas.scale(k, k);
                canvas.translate(-cx, -cy);
            }
            // 阴影
            paint.setColor(0x22000000);
            canvas.drawOval(cx - base * 0.95f + cellSize * 0.05f, cy + base * 0.5f,
                    cx + base * 0.95f + cellSize * 0.05f, cy + base * 0.85f, paint);
            // 选中光环
            if (t == hoverTower) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0x88FFFFFF);
                paint.setStrokeWidth(cellSize * 0.05f);
                canvas.drawCircle(cx, cy + base * 0.1f, base * 1.1f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            // 底座
            paint.setColor(0xFF9E8E72);
            canvas.drawCircle(cx, cy + base * 0.32f, base * 0.95f, paint);
            paint.setColor(0xFFB8A884);
            canvas.drawCircle(cx, cy + base * 0.28f, base * 0.72f, paint);
            // 等级金圈（Lv2+）
            if (t.level >= 2) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xCCFFC107);
                paint.setStrokeWidth(cellSize * 0.045f);
                canvas.drawCircle(cx, cy + base * 0.28f, base * 0.85f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            // 塔体
            boolean firing = isFiring(t);
            drawTowerBody(canvas, t, cx, cy, base, firing);
            // 等级星标
            if (t.level >= 3) {
                float sy = cy - base * 1.5f;
                paint.setColor(0xFFFFC107);
                for (int i = 0; i < 3; i++) {
                    canvas.drawCircle(cx - base * 0.5f + i * base * 0.5f, sy, base * 0.12f, paint);
                }
            } else if (t.level == 2) {
                paint.setColor(0xFFFFC107);
                canvas.drawCircle(cx, cy - base * 1.4f, base * 0.13f, paint);
            }
            if (k < 1f) {
                canvas.restore();
            }
        }
    }

    /** 该塔当前帧是否正在开火（存在以塔心为起点的光束） */
    private boolean isFiring(TdGame.Tower t) {
        float tx = t.col + 0.5f, ty = t.row + 0.5f;
        for (TdGame.Beam b : game.getBeams()) {
            if (Math.abs(b.x1 - tx) < 0.01f && Math.abs(b.y1 - ty) < 0.01f) return true;
        }
        return false;
    }

    private void drawTowerBody(Canvas canvas, TdGame.Tower t, float cx, float cy, float b, boolean firing) {
        // 开火闪光（围绕塔体质心的暖色光晕）
        if (firing) {
            paint.setColor(0x40FFF176);
            canvas.drawCircle(cx, cy - b * 0.2f, b * 1.25f, paint);
        }
        float spriteScale = 1f + .025f * (float) Math.sin(frameCount * .10f + t.row * 3 + t.col);
        if (firing) spriteScale += .055f;
        canvas.save();
        canvas.scale(spriteScale, spriteScale, cx, cy - b * .12f);
        boolean drewSprite = drawSprite(canvas, towerSprites, t.type.ordinal(), cx, cy - b * 0.12f, b * 1.48f);
        canvas.restore();
        if (drewSprite) {
            return;
        }
        switch (t.type) {
            case BOTTLE: {
                // 玻璃药水瓶：瓶身 + 液面 + 软木塞
                paint.setColor(0xFF81D4FA);
                canvas.drawOval(cx - b * 0.55f, cy - b * 0.55f, cx + b * 0.55f, cy + b * 0.5f, paint);
                paint.setColor(0xFF4FC3F7);
                canvas.drawRect(cx - b * 0.2f, cy - b * 1.1f, cx + b * 0.2f, cy - b * 0.4f, paint);
                paint.setColor(0xFFB3E5FC);
                canvas.drawOval(cx - b * 0.33f, cy - b * 0.42f, cx + b * 0.05f, cy - b * 0.02f, paint);
                paint.setColor(0xFF8D6E63);
                canvas.drawRoundRect(cx - b * 0.14f, cy - b * 1.28f, cx + b * 0.14f, cy - b * 1.06f,
                        b * 0.1f, b * 0.1f, paint);
                break;
            }
            case SUN: {
                // 向日葵：旋转花瓣 + 笑脸花蕊 + 叶子
                for (int i = 0; i < 9; i++) {
                    double a = Math.PI * 2 * i / 9 + frameCount * 0.03;
                    float px = cx + (float) Math.cos(a) * b * 0.95f;
                    float py = cy - b * 0.25f + (float) Math.sin(a) * b * 0.8f;
                    paint.setColor(0xFFFFB300);
                    canvas.drawOval(px - b * 0.22f, py - b * 0.34f, px + b * 0.22f, py + b * 0.34f, paint);
                }
                paint.setColor(0xFF795548);
                canvas.drawCircle(cx, cy - b * 0.3f, b * 0.44f, paint);
                paint.setColor(0xFFA1887F);
                canvas.drawCircle(cx, cy - b * 0.3f, b * 0.28f, paint);
                // 笑脸
                paint.setColor(C_TEXT);
                canvas.drawCircle(cx - b * 0.12f, cy - b * 0.36f, b * 0.05f, paint);
                canvas.drawCircle(cx + b * 0.12f, cy - b * 0.36f, b * 0.05f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(b * 0.05f);
                Path smile = new Path();
                smile.moveTo(cx - b * 0.1f, cy - b * 0.22f);
                smile.quadTo(cx, cy - b * 0.12f, cx + b * 0.1f, cy - b * 0.22f);
                canvas.drawPath(smile, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case SNOW: {
                // 雪花：六角冰晶 + 光核
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6 + frameCount * 0.008;
                    float px = cx + (float) Math.cos(a) * b * 1.05f;
                    float py = cy - b * 0.2f + (float) Math.sin(a) * b * 0.92f;
                    paint.setStrokeWidth(b * 0.13f);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(0xFFB3E5FC);
                    canvas.drawLine(cx, cy - b * 0.2f, px, py, paint);
                    // 分叉
                    float mx = (px + cx) / 2f, my = (py + cy - b * 0.2f) / 2f;
                    float dx = px - cx, dy = py - (cy - b * 0.2f);
                    float dl = (float) Math.hypot(dx, dy);
                    float ux = dx / dl, uy = dy / dl;
                    paint.setStrokeWidth(b * 0.08f);
                    canvas.drawLine(mx, my, mx - uy * b * 0.14f - ux * b * 0.22f,
                            my + ux * b * 0.14f - uy * b * 0.22f, paint);
                    canvas.drawLine(mx, my, mx + uy * b * 0.14f - ux * b * 0.22f,
                            my - ux * b * 0.14f - uy * b * 0.22f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(px, py, b * 0.14f, paint);
                }
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setStyle(Paint.Style.FILL);
                gradPaint.setShader(new RadialGradient(cx, cy - b * 0.2f, b * 0.5f,
                        0xFFE1F5FE, 0xFF4FC3F7, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy - b * 0.2f, b * 0.34f, gradPaint);
                gradPaint.setShader(null);
                break;
            }
            case FAN: {
                // 风扇：扇叶高速旋转 + 栅格外环
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0xFF90A4AE);
                paint.setStrokeWidth(b * 0.08f);
                canvas.drawCircle(cx, cy - b * 0.25f, b * 0.88f, paint);
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 3; i++) {
                    double a = Math.PI * 2 * i / 3 + frameCount * 0.09;
                    paint.setColor(0xFFCFD8DC);
                    Path blade = new Path();
                    float bx0 = cx + (float) Math.cos(a) * b * 0.2f;
                    float by0 = cy - b * 0.25f + (float) Math.sin(a) * b * 0.2f;
                    float bx1 = cx + (float) Math.cos(a + 0.55f) * b * 0.85f;
                    float by1 = cy - b * 0.25f + (float) Math.sin(a + 0.55f) * b * 0.8f;
                    float bx2 = cx + (float) Math.cos(a - 0.55f) * b * 0.85f;
                    float by2 = cy - b * 0.25f + (float) Math.sin(a - 0.55f) * b * 0.8f;
                    blade.moveTo(bx0, by0);
                    blade.lineTo(bx1, by1);
                    blade.lineTo(bx2, by2);
                    blade.close();
                    canvas.drawPath(blade, paint);
                }
                paint.setColor(0xFF607D8B);
                canvas.drawCircle(cx, cy - b * 0.25f, b * 0.26f, paint);
                paint.setColor(0xFFB0BEC5);
                canvas.drawCircle(cx, cy - b * 0.25f, b * 0.12f, paint);
                break;
            }
            case POISON: {
                // 毒泡泡：冒泡的特效池
                paint.setColor(0x5520A020);
                canvas.drawCircle(cx, cy - b * 0.15f, b * 0.8f, paint);
                paint.setColor(0x66AA40D0);
                canvas.drawCircle(cx, cy - b * 0.5f, b * 0.55f, paint);
                // 上浮小气泡
                float rise = (frameCount * 0.01f) % 1f;
                for (int i = 0; i < 2; i++) {
                    float tt = (rise + i * 0.5f) % 1f;
                    float bx = cx - b * 0.4f + i * b * 0.55f;
                    float by = cy + b * 0.35f - tt * b * 0.9f;
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(b * 0.04f);
                    paint.setColor(0x88E040FB);
                    canvas.drawCircle(bx, by, b * (0.09f + tt * 0.08f), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                paint.setColor(0xBBE040FB);
                canvas.drawCircle(cx - b * 0.16f, cy - b * 0.6f, b * 0.15f, paint);
                paint.setColor(0xFF9C27B0);
                canvas.drawCircle(cx, cy + b * 0.3f, b * 0.14f, paint);
                break;
            }
            case ROCKET: {
                // 火箭：红白机身 + 观察窗 + 尾焰
                paint.setColor(0xFFE53935);
                canvas.drawRoundRect(cx - b * 0.45f, cy - b * 1.25f, cx + b * 0.45f, cy + b * 0.3f,
                        b * 0.22f, b * 0.22f, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawOval(cx - b * 0.16f, cy - b * 0.25f, cx + b * 0.16f, cy + b * 0.18f, paint);
                paint.setColor(0xFF42A5F5);
                canvas.drawCircle(cx, cy - b * 0.04f, b * 0.1f, paint);
                paint.setColor(0xFFFF8A80);
                canvas.drawCircle(cx, cy - b * 1.15f, b * 0.35f, paint);
                // 尾焰闪烁
                float flame = 0.7f + 0.3f * (float) Math.sin(frameCount * 0.5f + t.row * 2 + t.col);
                paint.setColor(0xFFFFF176);
                canvas.drawOval(cx - b * 0.22f, cy + b * 0.3f,
                        cx + b * 0.22f, cy + b * (0.3f + 0.55f * flame), paint);
                paint.setColor(0xFFFFD54F);
                canvas.drawOval(cx - b * 0.12f, cy + b * 0.3f,
                        cx + b * 0.12f, cy + b * (0.32f + 0.35f * flame), paint);
                break;
            }
            case LIGHTNING: {
                float pulse = .78f + .22f * (float) Math.sin(frameCount * .22f + t.row);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(b * .11f);
                paint.setColor(0xFF7E57C2);
                canvas.drawCircle(cx, cy - b * .18f, b * .72f * pulse, paint);
                paint.setStrokeWidth(b * .05f);
                paint.setColor(0xFFE1BEE7);
                for (int i = 0; i < 4; i++) {
                    double angle = i * Math.PI / 2d + frameCount * .05d;
                    float ex = cx + (float) Math.cos(angle) * b * .92f;
                    float ey = cy - b * .18f + (float) Math.sin(angle) * b * .78f;
                    canvas.drawLine(cx, cy - b * .18f, ex, ey, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFB388FF);
                canvas.drawCircle(cx, cy - b * .18f, b * .32f, paint);
                break;
            }
            case SNIPER: {
                paint.setColor(0xFF37474F);
                canvas.drawRoundRect(cx - b * .2f, cy - b * 1.22f, cx + b * .2f, cy + b * .42f,
                        b * .12f, b * .12f, paint);
                paint.setColor(0xFF90A4AE);
                canvas.drawRoundRect(cx - b * .1f, cy - b * 1.55f, cx + b * .1f, cy - b * .7f,
                        b * .06f, b * .06f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(b * .09f);
                paint.setColor(0xFF80DEEA);
                canvas.drawCircle(cx, cy - b * .55f, b * .37f, paint);
                canvas.drawLine(cx - b * .22f, cy - b * .55f, cx + b * .22f, cy - b * .55f, paint);
                canvas.drawLine(cx, cy - b * .77f, cx, cy - b * .33f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case MINE: {
                float blink = .55f + .45f * (float) Math.sin(frameCount * .18f + t.row + t.col);
                paint.setColor(0xFF546E7A);
                canvas.drawCircle(cx, cy - b * .03f, b * .72f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(b * .09f);
                paint.setColor(0xFF90A4AE);
                canvas.drawCircle(cx, cy - b * .03f, b * .72f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blink > .72f ? 0xFFFF5252 : 0xFF8D1B1B);
                canvas.drawCircle(cx, cy - b * .03f, b * .17f, paint);
                for (int i = 0; i < 4; i++) {
                    double angle = i * Math.PI / 2d + Math.PI / 4d;
                    canvas.drawCircle(cx + (float) Math.cos(angle) * b * .92f,
                            cy - b * .03f + (float) Math.sin(angle) * b * .72f, b * .12f, paint);
                }
                break;
            }
            case AMPLIFIER: {
                float orbit = (float) (frameCount * .06f);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(b * .08f);
                paint.setColor(0xFF26C6DA);
                canvas.drawCircle(cx, cy - b * .2f, b * .72f, paint);
                paint.setColor(0x884DD0E1);
                canvas.drawCircle(cx, cy - b * .2f, b * 1.03f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF00695C);
                canvas.drawRoundRect(cx - b * .28f, cy - b * .86f, cx + b * .28f, cy + b * .32f,
                        b * .15f, b * .15f, paint);
                paint.setColor(0xFFB2EBF2);
                for (int i = 0; i < 3; i++) {
                    double angle = orbit + i * Math.PI * 2d / 3d;
                    canvas.drawCircle(cx + (float) Math.cos(angle) * b * .92f,
                            cy - b * .2f + (float) Math.sin(angle) * b * .7f, b * .12f, paint);
                }
                break;
            }
        }
    }

    private void drawSelectedRange(Canvas canvas) {
        if (selectedType == null && hoverTower == null) return;
        TdGame.Tower t = hoverTower;
        float r, cx, cy;
        if (t != null) {
            cx = originX + (t.col + 0.5f) * cellSize;
            cy = originY + (t.row + 0.5f) * cellSize;
            r = game.effectiveRangeAt(t);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x1881C784);
            canvas.drawCircle(cx, cy, r * cellSize, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0x66FFFFFF);
            paint.setStrokeWidth(cellSize * 0.04f);
            canvas.drawCircle(cx, cy, r * cellSize, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    // =====================================================================
    // 光束：光晕 + 火箭尾焰 + 命中火花
    // =====================================================================
    private void drawBeams(Canvas canvas) {
        for (TdGame.Beam b : game.getBeams()) {
            float x1 = originX + b.x1 * cellSize, y1 = originY + b.y1 * cellSize;
            float x2 = originX + b.x2 * cellSize, y2 = originY + b.y2 * cellSize;
            int col = beamColor(b.type);
            // 外光晕
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(col & 0x3CFFFFFF);
            paint.setStrokeWidth(cellSize * 0.16f);
            canvas.drawLine(x1, y1, x2, y2, paint);
            // 主光束
            paint.setColor(col);
            paint.setStrokeWidth(cellSize * 0.06f);
            canvas.drawLine(x1, y1, x2, y2, paint);
            paint.setStyle(Paint.Style.FILL);
            // 火箭：尾部写实火焰（三段递减）
            if (b.type == TowerType.ROCKET) {
                float dx = x2 - x1, dy = y2 - y1;
                float len = (float) Math.hypot(dx, dy);
                if (len > 0.001f) {
                    float ux = dx / len, uy = dy / len;
                    int[] flame = {0xFFFFF176, 0xFFFFB74D, 0xFFE64A19};
                    float[] w = {cellSize * 0.14f, cellSize * 0.1f, cellSize * 0.06f};
                    float t0 = 0f;
                    for (int i = 0; i < 3; i++) {
                        float t1 = t0 + len * 0.13f;
                        paint.setColor(flame[i]);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeCap(Paint.Cap.ROUND);
                        paint.setStrokeWidth(w[i]);
                        canvas.drawLine(x1 + ux * t0, y1 + uy * t0, x1 + ux * t1, y1 + uy * t1, paint);
                        t0 = t1;
                    }
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            // 命中火花
            paint.setColor(0xFFFFD54F);
            canvas.drawCircle(x2, y2, cellSize * 0.13f, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(x2, y2, cellSize * 0.06f, paint);
        }
    }

    // =====================================================================
    // 怪物：八种差异化造型 + 弹跳 + 受击闪白 + HP + 状态叠层
    // =====================================================================
    private void drawMonsters(Canvas canvas) {
        float hpBarW = cellSize * 0.62f;
        for (TdGame.Monster m : game.getMonsters()) {
            float cx = originX + m.x * cellSize;
            boolean boss = m.type == MonsterType.BOSS;
            // 行进小弹跳完全依赖帧数；避免静态精灵只随路径坐标变化而显得僵硬。
            float bob = 0f;
            if (m.type == MonsterType.FLY) {
                bob = (float) (Math.abs(Math.sin(frameCount * 0.12f + m.id)) * cellSize * 0.06);
            } else if (!boss && m.pathIndex < game.getRouteLength(m.routeIndex) - 1) {
                float motion = m.charging ? .11f : .075f;
                bob = (float) (Math.abs(Math.sin(frameCount * motion + m.id * 1.7)) * cellSize * 0.05);
            }
            float cy = originY + m.y * cellSize - bob;
            boolean heavy = m.type == MonsterType.TANK || m.type == MonsterType.RESISTANT
                    || m.type == MonsterType.SHIELD_GENERATOR;
            float r = cellSize * (boss ? 0.42f : (heavy ? 0.34f : 0.26f));
            float groundY = originY + m.y * cellSize;
            // 阴影（飞行兵阴影更淡更高）
            if (m.type == MonsterType.FLY) {
                paint.setColor(0x22000000);
                canvas.drawOval(cx - r * 0.5f, groundY + r * 0.5f, cx + r * 0.5f, groundY + r * 0.62f, paint);
            } else {
                paint.setColor(0x33000000);
                canvas.drawOval(cx - r * 0.9f, groundY + r * 0.55f, cx + r * 0.9f, groundY + r * 0.8f, paint);
            }

            // 身体（受击闪白）
            int body = monsterColor(m.type);
            if (m.hitFlash > 0f) {
                body = blendWhite(body, Math.min(1f, m.hitFlash / 0.18f));
            }
            drawMonsterBody(canvas, m, cx, cy, r, boss, body);

            // 状态叠层
            if (m.slowTimer > 0f) {
                paint.setColor(0x4D81D4FA);
                canvas.drawCircle(cx, cy, r * 1.12f, paint);
                paint.setColor(0xBBFFFFFF);
                canvas.drawCircle(cx + r * 0.72f, cy - r * 0.78f, cellSize * 0.05f, paint);
                canvas.drawCircle(cx - r * 0.6f, cy - r * 0.55f, cellSize * 0.04f, paint);
            }
            if (m.dotTimer > 0f) {
                paint.setColor(0x66BA68C8);
                canvas.drawCircle(cx, cy, r * (1.06f + 0.08f * (float) Math.sin(frameCount * 0.2f + m.id)), paint);
                paint.setColor(0xCCCE93D8);
                canvas.drawCircle(cx + r * 0.6f, cy - r * 0.72f, cellSize * 0.06f, paint);
            }
            if (m.shield > 0f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0x7FE0E0E0);
                paint.setStrokeWidth(cellSize * 0.045f);
                canvas.drawCircle(cx, cy, r * 1.35f, paint);
                paint.setColor(0x55FFFFFF);
                paint.setStrokeWidth(cellSize * 0.02f);
                canvas.drawCircle(cx, cy, r * 1.35f, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (m.healedFlash > 0f) {
                float pulse = 1f + (0.32f - m.healedFlash) * 1.8f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(cellSize * 0.04f);
                paint.setColor(0xCC69F0AE);
                canvas.drawCircle(cx, cy, r * pulse, paint);
                paint.setStyle(Paint.Style.FILL);
                textPaint.setTextSize(cellSize * 0.22f);
                textPaint.setColor(0xFFB8FFD8);
                textPaint.setFakeBoldText(true);
                canvas.drawText("+", cx, cy - r * 1.05f, textPaint);
                textPaint.setFakeBoldText(false);
            }
            if (m.shieldFlash > 0f) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(cellSize * .04f);
                paint.setColor(0xCC80DEEA);
                canvas.drawCircle(cx, cy, r * (1.15f + .5f * (0.38f - m.shieldFlash)), paint);
                paint.setStyle(Paint.Style.FILL);
            }
            if (m.charging || m.enraged) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(cellSize * .055f);
                paint.setColor(m.enraged ? 0xCCFF5252 : 0xCCFFCA28);
                canvas.drawCircle(cx, cy, r * 1.22f, paint);
                paint.setStyle(Paint.Style.FILL);
            }

            // BOSS 名牌
            if (boss) {
                textPaint.setTextSize(cellSize * 0.24f);
                textPaint.setColor(0xFFFF5252);
                textPaint.setFakeBoldText(true);
                canvas.drawText("BOSS", cx, cy - r - cellSize * 0.3f, textPaint);
                textPaint.setFakeBoldText(false);
                textPaint.setColor(C_TEXT);
            }
            // HP 条
            float ratio = Math.max(0f, Math.min(1f, m.hp / m.maxHp));
            paint.setColor(0xCC5D4037);
            canvas.drawRoundRect(cx - hpBarW / 2, cy - r - cellSize * 0.16f,
                    cx + hpBarW / 2, cy - r - cellSize * 0.04f,
                    cellSize * 0.04f, cellSize * 0.04f, paint);
            paint.setColor(hpColor(ratio));
            canvas.drawRoundRect(cx - hpBarW / 2 + cellSize * 0.01f, cy - r - cellSize * 0.14f,
                    cx - hpBarW / 2 + hpBarW * ratio - cellSize * 0.01f, cy - r - cellSize * 0.06f,
                    cellSize * 0.03f, cellSize * 0.03f, paint);
        }
    }

    private void drawMonsterBody(Canvas canvas, TdGame.Monster m, float cx, float cy,
                                 float r, boolean boss, int body) {
        float bodyScale = 1f + .025f * (float) Math.sin(frameCount * .12f + m.id);
        if (m.charging) bodyScale = 1.1f;
        if (m.enraged) bodyScale = 1.06f + .03f * (float) Math.sin(frameCount * .25f);
        canvas.save();
        canvas.scale(bodyScale, bodyScale, cx, cy);
        boolean drewSprite = drawSprite(canvas, monsterSprites, monsterSpriteIndex(m.type),
                cx, cy, r * (boss ? 1.28f : 1.38f));
        canvas.restore();
        if (drewSprite) {
            if (m.hitFlash > 0f) {
                paint.setColor(0x55FFFFFF);
                canvas.drawCircle(cx, cy, r * 0.92f, paint);
            }
            return;
        }
        switch (m.type) {
            case NORMAL: {
                // 圆头 + 尖耳（小狼）
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(body);
                Path ears = new Path();
                ears.moveTo(cx - r * 0.62f, cy - r * 0.28f);
                ears.lineTo(cx - r * 0.35f, cy - r * 0.02f);
                ears.lineTo(cx - r * 0.55f, cy - r * 0.5f);
                ears.close();
                Path ears2 = new Path();
                ears2.moveTo(cx + r * 0.62f, cy - r * 0.28f);
                ears2.lineTo(cx + r * 0.35f, cy - r * 0.02f);
                ears2.lineTo(cx + r * 0.55f, cy - r * 0.5f);
                ears2.close();
                canvas.drawPath(ears, paint);
                canvas.drawPath(ears2, paint);
                paint.setColor(0x66E0C0A0);
                canvas.drawCircle(cx - r * 0.45f, cy - r * 0.35f, r * 0.2f, paint);
                paint.setColor(0x44FFFFFF);
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.42f, r * 0.28f, paint);
                break;
            }
            case FAST: {
                // 黄色闪电
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0xFFFFF176);
                Path bolt = new Path();
                bolt.moveTo(cx + r * 0.1f, cy - r * 0.62f);
                bolt.lineTo(cx - r * 0.28f, cy + r * 0.05f);
                bolt.lineTo(cx + r * 0.02f, cy + r * 0.05f);
                bolt.lineTo(cx - r * 0.12f, cy + r * 0.62f);
                bolt.lineTo(cx + r * 0.3f, cy - r * 0.1f);
                bolt.lineTo(cx - r * 0.02f, cy - r * 0.1f);
                bolt.close();
                canvas.drawPath(bolt, paint);
                paint.setColor(0x44FFFFFF);
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.38f, r * 0.28f, paint);
                break;
            }
            case TANK: {
                // 甲壳胖子：身 + 背甲
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0xFF5D4037);
                canvas.drawOval(cx - r * 0.9f, cy - r * 0.75f, cx + r * 0.9f, cy + r * 0.05f, paint);
                paint.setColor(0xFF795548);
                for (int i = 0; i < 5; i++) {
                    canvas.drawOval(cx - r * 0.9f + i * r * 0.36f, cy - r * 0.65f,
                            cx - r * 0.6f + i * r * 0.36f, cy + r * 0.0f, paint);
                }
                paint.setColor(0x44FFFFFF);
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.5f, r * 0.2f, paint);
                break;
            }
            case FLY: {
                // 飞行兵：圆身 + 扇动双翅
                float flap = (float) Math.sin(frameCount * 0.25f + m.id);
                paint.setColor(0x7781C784);
                canvas.drawOval(cx - r * 0.85f, cy - r * 0.75f - Math.abs(flap) * r * 0.18f,
                        cx + r * 0.85f, cy - r * 0.35f + Math.abs(flap) * r * 0.18f, paint);
                canvas.drawOval(cx - r * 0.85f, cy + r * 0.35f - Math.abs(flap) * r * 0.18f,
                        cx + r * 0.85f, cy + r * 0.75f + Math.abs(flap) * r * 0.18f, paint);
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0x44FFFFFF);
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.38f, r * 0.28f, paint);
                break;
            }
            case SWARM: {
                // 喽罗：小圆 + 呆滞斜眼
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(C_TEXT);
                canvas.drawCircle(cx - r * 0.2f, cy - r * 0.1f, r * 0.09f, paint);
                canvas.drawCircle(cx + r * 0.28f, cy + r * 0.05f, r * 0.09f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * 0.06f);
                canvas.drawLine(cx - r * 0.2f, cy + r * 0.42f, cx + r * 0.18f, cy + r * 0.42f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case HEALER: {
                // 医生护士怪：粉身 + 白十字
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0xFFFFFFFF);
                canvas.drawRect(new RectF(cx - r * 0.12f, cy - r * 0.55f,
                        cx + r * 0.12f, cy + r * 0.18f), paint);
                canvas.drawRect(new RectF(cx - r * 0.36f, cy - r * 0.32f,
                        cx + r * 0.36f, cy - r * 0.08f), paint);
                paint.setColor(0x44FFFFFF);
                canvas.drawCircle(cx - r * 0.32f, cy - r * 0.34f, r * 0.22f, paint);
                break;
            }
            case SHIELD: {
                // 护盾兵：圆身 + 前方盾牌
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                float sx = cx + (float) Math.cos(frameCount * 0.01) * r * 0.0f;
                paint.setColor(0xFF78909C);
                RectF shield = new RectF(sx + r * 0.05f, cy - r * 0.62f,
                        sx + r * 0.72f, cy + r * 0.62f);
                canvas.drawRoundRect(shield, r * 0.2f, r * 0.2f, paint);
                paint.setColor(0xFFB0BEC5);
                Paint.Style old = paint.getStyle();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * 0.08f);
                canvas.drawRoundRect(shield, r * 0.2f, r * 0.2f, paint);
                paint.setStyle(old);
                paint.setColor(0x44FFFFFF);
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.34f, r * 0.22f, paint);
                break;
            }
            case BOSS: {
                // Boss 巨魔：大角 + 怒眉 + 獠牙
                gradPaint.setShader(new RadialGradient(cx, cy - r * 0.4f, r * 1.6f,
                        body, 0xFF8E0000, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, r, gradPaint);
                gradPaint.setShader(null);
                // 双角
                paint.setColor(0xFF5D4037);
                Path hornL = new Path();
                hornL.moveTo(cx - r * 0.6f, cy - r * 0.3f);
                hornL.lineTo(cx - r * 0.2f, cy - r * 0.15f);
                hornL.lineTo(cx - r * 0.42f, cy - r * 0.85f);
                hornL.close();
                Path hornR = new Path();
                hornR.moveTo(cx + r * 0.6f, cy - r * 0.3f);
                hornR.lineTo(cx + r * 0.2f, cy - r * 0.15f);
                hornR.lineTo(cx + r * 0.42f, cy - r * 0.85f);
                hornR.close();
                canvas.drawPath(hornL, paint);
                canvas.drawPath(hornR, paint);
                paint.setColor(0xFFFF8A80);
                canvas.drawCircle(cx - r * 0.05f, cy - r * 0.62f, r * 0.36f, paint); // 额头瘤
                break;
            }
            case SPLITTER: {
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * .1f);
                paint.setColor(0xFFD1C4E9);
                canvas.drawLine(cx - r * .55f, cy - r * .55f, cx + r * .05f, cy + r * .08f, paint);
                canvas.drawLine(cx + r * .05f, cy + r * .08f, cx + r * .5f, cy - r * .28f, paint);
                canvas.drawLine(cx + r * .05f, cy + r * .08f, cx + r * .32f, cy + r * .58f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case CHARGER: {
                paint.setColor(body);
                canvas.drawOval(cx - r * 1.05f, cy - r * .72f, cx + r * 1.05f, cy + r * .72f, paint);
                paint.setColor(0xFFFFD54F);
                Path horn = new Path();
                horn.moveTo(cx + r * .4f, cy - r * .35f);
                horn.lineTo(cx + r * 1.15f, cy - r * .7f);
                horn.lineTo(cx + r * .78f, cy - r * .05f);
                horn.close();
                canvas.drawPath(horn, paint);
                break;
            }
            case SHIELD_GENERATOR: {
                paint.setColor(body);
                canvas.drawRoundRect(cx - r * .72f, cy - r * .76f, cx + r * .72f, cy + r * .72f,
                        r * .25f, r * .25f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * .11f);
                paint.setColor(0xFF80DEEA);
                canvas.drawCircle(cx, cy - r * .12f, r * .45f, paint);
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 3; i++) {
                    double angle = frameCount * .08d + i * Math.PI * 2d / 3d;
                    canvas.drawCircle(cx + (float) Math.cos(angle) * r * .9f,
                            cy + (float) Math.sin(angle) * r * .72f, r * .12f, paint);
                }
                break;
            }
            case SUMMONER: {
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0xFFE1BEE7);
                Path hat = new Path();
                hat.moveTo(cx - r * .7f, cy - r * .35f);
                hat.lineTo(cx, cy - r * 1.15f);
                hat.lineTo(cx + r * .7f, cy - r * .35f);
                hat.close();
                canvas.drawPath(hat, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(0x99EA80FC);
                paint.setStrokeWidth(r * .08f);
                canvas.drawCircle(cx + r * .82f, cy + r * .45f, r * (.28f + .07f * (float) Math.sin(frameCount * .16f)), paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case RESISTANT: {
                paint.setColor(body);
                canvas.drawRoundRect(cx - r * .82f, cy - r * .82f, cx + r * .82f, cy + r * .82f,
                        r * .28f, r * .28f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * .12f);
                paint.setColor(0xFFB0BEC5);
                canvas.drawRoundRect(cx - r * .62f, cy - r * .64f, cx + r * .62f, cy + r * .58f,
                        r * .2f, r * .2f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case RAGER: {
                paint.setColor(m.enraged ? 0xFFE53935 : body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0xFFFFCC80);
                canvas.drawCircle(cx - r * .5f, cy - r * .55f, r * .18f, paint);
                canvas.drawCircle(cx + r * .5f, cy - r * .55f, r * .18f, paint);
                paint.setColor(0xFF4E342E);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(r * .1f);
                canvas.drawLine(cx - r * .45f, cy + r * .35f, cx + r * .45f, cy + r * .35f, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            default:
                paint.setColor(body);
                canvas.drawCircle(cx, cy, r, paint);
                paint.setColor(0x44FFFFFF);
                canvas.drawCircle(cx - r * 0.3f, cy - r * 0.4f, r * 0.3f, paint);
                break;
        }
        // 眼睛朝向移动方向
        drawMonsterEyes(canvas, m, cx, cy, r, boss, body);
    }

    private void drawMonsterEyes(Canvas canvas, TdGame.Monster m, float cx, float cy,
                                 float r, boolean boss, int body) {
        int[][][] paths = game.getPaths();
        int[][] path = m.routeIndex >= 0 && m.routeIndex < paths.length
                ? paths[m.routeIndex] : game.getPath();
        float ex = 0, ey = 0;
        if (m.pathIndex < path.length) {
            int c = path[m.pathIndex][1], rw = path[m.pathIndex][0];
            ex = (c + 0.5f) - m.x;
            ey = (rw + 0.5f) - m.y;
            float len = (float) Math.hypot(ex, ey);
            if (len > 0.001f) { ex /= len; ey /= len; } else { ex = 1; ey = 0; }
        }
        float ox = ex * r * 0.28f, oy = ey * r * 0.28f;
        paint.setColor(0xFFFFFFFF);
        for (int side = -1; side <= 1; side += 2) {
            float ex0 = cx + ox + side * r * 0.18f;
            float ey0 = cy + oy - r * 0.15f;
            float er = r * (boss ? 0.16f : 0.15f);
            canvas.drawCircle(ex0, ey0, er, paint);
            paint.setColor(C_TEXT);
            canvas.drawCircle(ex0, ey0, er * 0.55f, paint);
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(ex0 - er * 0.2f, ey0 - er * 0.2f, er * 0.22f, paint);
            paint.setColor(0xFFFFFFFF);
        }
        // Boss 怒眉
        if (boss) {
            paint.setColor(0xFF3E2723);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(r * 0.09f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx - r * 0.48f, cy - r * 0.38f, cx - r * 0.1f, cy - r * 0.27f, paint);
            canvas.drawLine(cx + r * 0.48f, cy - r * 0.38f, cx + r * 0.1f, cy - r * 0.27f, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** 两张精灵表均为 3×2：按类型稳定取格，未覆盖的变体复用同阵营模型。 */
    private static int monsterSpriteIndex(MonsterType type) {
        switch (type) {
            case FAST: return 1;
            case TANK:
            case SHIELD: return 2;
            case FLY: return 3;
            case HEALER: return 4;
            case BOSS: return 5;
            case SPLITTER:
            case CHARGER:
            case SHIELD_GENERATOR:
            case SUMMONER:
            case RESISTANT:
            case RAGER:
                return -1;
            case NORMAL:
            case SWARM:
            default: return 0;
        }
    }

    /**
     * 把精灵表中一个 3×2 格子按棋盘单元缩放绘制。所有资源均按 drawable-nodpi 加载，
     * 因而不会出现屏幕密度二次缩放；失败时调用方回退到原来的 Canvas 造型。
     */
    private boolean drawSprite(Canvas canvas, Bitmap sheet, int index, float cx, float cy, float radius) {
        if (sheet == null || sheet.isRecycled() || index < 0 || index >= 6 || radius <= 0f) return false;
        int sourceWidth = sheet.getWidth() / 3;
        int sourceHeight = sheet.getHeight() / 2;
        if (sourceWidth <= 0 || sourceHeight <= 0) return false;

        int column = index % 3;
        int row = index / 3;
        Rect source = new Rect(column * sourceWidth, row * sourceHeight,
                (column + 1) * sourceWidth, (row + 1) * sourceHeight);
        RectF destination = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        int oldAlpha = paint.getAlpha();
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        canvas.drawBitmap(sheet, source, destination, paint);
        paint.setAlpha(oldAlpha);
        return true;
    }

    // =====================================================================
    // 粒子：爆裂碎屑 / 冲击波环 / 金币飘字（带描边）
    // =====================================================================
    private void drawParticles(Canvas canvas) {
        for (Particle p : particles) {
            float a = p.life / (float) p.maxLife;
            if (p.isText) {
                p.y += p.vy;
                float size = p.r * 1.15f;
                textPaint.setTextSize(size);
                // 描边
                textPaint.setColor(0xB3252A1A);
                textPaint.setFakeBoldText(true);
                textPaint.setStyle(Paint.Style.STROKE);
                textPaint.setStrokeWidth(Math.max(1.5f, size * 0.12f));
                canvas.drawText(p.text, p.x, p.y - (1f - a) * p.r * 0.9f, textPaint);
                // 本体
                textPaint.setStyle(Paint.Style.FILL);
                textPaint.setColor(p.color);
                textPaint.setAlpha((int) (250 * a));
                canvas.drawText(p.text, p.x, p.y - (1f - a) * p.r * 0.9f, textPaint);
                textPaint.setAlpha(255);
                textPaint.setFakeBoldText(false);
                textPaint.setColor(C_TEXT);
                textPaint.setStyle(Paint.Style.FILL);
            } else if (p.isRing) {
                p.x += p.vx;
                p.y += p.vy;
                p.r += cellSize * 0.022f;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(0.5f, cellSize * 0.06f * a));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(p.color);
                paint.setAlpha((int) (230 * a));
                canvas.drawCircle(p.x, p.y, p.r, paint);
                paint.setAlpha(255);
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeCap(Paint.Cap.BUTT);
            } else {
                p.x += p.vx;
                p.y += p.vy;
                p.vy += cellSize * 0.012f;
                paint.setColor(p.color);
                paint.setAlpha((int) (255 * a));
                canvas.drawCircle(p.x, p.y, Math.max(0.5f, p.r * a), paint);
                // 火花拖尾
                paint.setColor(0x55FFF176);
                canvas.drawCircle(p.x - p.vx * 0.5f, p.y - p.vy * 0.5f, Math.max(0.3f, p.r * a * 0.6f), paint);
                paint.setAlpha(255);
            }
        }
        particles.removeIf(p -> --p.life <= 0);
    }

    /** 由 Fragment 每帧传入引擎击杀事件，播放特效 */
    public void drainKillEvents(List<TdGame.KillEvent> events) {
        if (events == null || events.isEmpty()) return;
        for (TdGame.KillEvent e : events) {
            addKillFx(e.x, e.y, e.value, e.boss);
        }
    }

    /** 击杀特效：冲击波 + 彩色碎屑 + 金币飘字 */
    public void addKillFx(float x, float y, int value, boolean boss) {
        float px = originX + x * cellSize;
        float py = originY + y * cellSize;
        int fragments = boss ? 12 : 7;
        int[] colors = {0xFFFFB74D, 0xFFFFF176, 0xFFFF8A65, 0xFFFFD54F, 0xFFFFAB91};
        for (int i = 0; i < fragments; i++) {
            double a = Math.PI * 2 * i / fragments;
            float spd = cellSize * (0.05f + (i % 3) * 0.03f);
            particles.add(new Particle(px, py,
                    cellSize * (0.05f + (i % 4) * 0.015f), colors[i % colors.length],
                    16 + i % 4, "_", false, false,
                    (float) Math.cos(a) * spd,
                    (float) Math.sin(a) * spd - cellSize * 0.04f));
        }
        // 冲击波环
        particles.add(new Particle(px, py, cellSize * 0.18f, 0xFFFFD54F,
                boss ? 22 : 16, "_", false, true, 0f, 0f));
        particles.add(new Particle(px, py, cellSize * 0.1f, 0xCCFFFFFF,
                boss ? 18 : 13, "_", false, true, 0f, 0f));
        // 金币飘字
        particles.add(new Particle(px, py - cellSize * 0.12f,
                cellSize * (boss ? 0.6f : 0.44f), 0xFFFFD54F,
                boss ? 28 : 22, "+" + value + "₿", true, false, 0f, -cellSize * 0.013f));
    }

    private static final class Particle {
        float x, y, r;
        int color;
        int life, maxLife;
        String text;
        boolean isText;
        boolean isRing;
        float vx, vy;
        Particle(float x, float y, float r, int color, int life, String text,
                 boolean isText, boolean isRing, float vx, float vy) {
            this.x = x; this.y = y; this.r = r; this.color = color;
            this.life = life; this.maxLife = life; this.text = text;
            this.isText = isText; this.isRing = isRing; this.vx = vx; this.vy = vy;
        }
    }

    // =====================================================================
    // 波次横幅（旗形）
    // =====================================================================
    private void drawWaveBanner(Canvas canvas) {
        if (waveBannerTicks <= 0) return;
        waveBannerTicks--;
        float alpha = waveBannerTicks > 70 ? 1f : Math.max(0.05f, waveBannerTicks / 20f);
        if (alpha < 0.1f) return;
        int w = getWidth();
        float cx = w / 2f;
        float top = originY - cellSize * 0.7f;
        float bw = cellSize * 4.4f, bh = cellSize * 0.85f;
        // 深色底条
        paint.setColor(0xCC3B3327);
        canvas.drawRoundRect(cx - bw, top, cx + bw, top + bh,
                cellSize * 0.22f, cellSize * 0.22f, paint);
        // 顶部金色三角旗檐
        paint.setColor(0xCCFFC107);
        Path flag = new Path();
        flag.moveTo(cx - bw, top);
        flag.lineTo(cx + bw, top);
        flag.lineTo(cx + bw + cellSize * 0.28f, top + bh * 0.42f);
        flag.lineTo(cx + bw, top + bh * 0.84f);
        flag.lineTo(cx - bw, top + bh * 0.84f);
        flag.close();
        canvas.drawPath(flag, paint);
        // 文字（描边加粗）
        textPaint.setTextSize(cellSize * 0.6f);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(0xB3252A1A);
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(cellSize * 0.08f);
        canvas.drawText(waveBannerText, cx, top + bh * 0.68f, textPaint);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(waveBannerText, cx, top + bh * 0.68f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(C_TEXT);
    }

    // =====================================================================
    // 结算
    // =====================================================================
    private void drawOverlay(Canvas canvas) {
        if (game.getState() == TdGame.State.WON) {
            drawResultOverlay(canvas, "守住了！蛋蛋安全了", 0xFF66BB6A, game.starsEarned(), true);
        } else if (game.getState() == TdGame.State.LOST) {
            drawResultOverlay(canvas, "蛋蛋被吃掉了…", 0xFFE57373, 0, false);
        }
    }

    private void drawResultOverlay(Canvas canvas, String msg, int color, int stars, boolean win) {
        int w = getWidth(), h = getHeight();
        paint.setColor(win ? 0xC0003010 : 0xC0201414);
        canvas.drawRect(0, 0, w, h, paint);
        // 光晕
        float oy = h / 2f - getHeight() * 0.14f;
        if (win) {
            gradPaint.setShader(new RadialGradient(w / 2f, oy, cellSize * 4f,
                    0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawCircle(w / 2f, oy, cellSize * 4f, gradPaint);
            gradPaint.setShader(null);
        }
        paint.setColor(color);
        canvas.drawCircle(w / 2f, oy, cellSize * 1.5f, paint);
        paint.setColor(0x66FFFFFF);
        canvas.drawCircle(w / 2f, oy, cellSize * 0.85f, paint);
        // 胜利彩星
        if (win) {
            paint.setColor(0xFFFFC107);
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6 + frameCount * 0.004;
                float sx = w / 2f + (float) Math.cos(a) * cellSize * 2.4f;
                float sy = oy + (float) Math.sin(a) * cellSize * 2.1f;
                drawStar(canvas, sx, sy, cellSize * 0.14f);
            }
        }
        textPaint.setTextSize(cellSize * 0.9f);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setStrokeWidth(cellSize * 0.06f);
        textPaint.setColor(0xB3252A1A);
        canvas.drawText(msg, w / 2f, oy + cellSize * 1.1f, textPaint);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFFFFFFFF);
        canvas.drawText(msg, w / 2f, oy + cellSize * 1.1f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(C_TEXT);
        if (stars > 0) {
            textPaint.setTextSize(cellSize * 0.9f);
            textPaint.setColor(0xFFFFC107);
            canvas.drawText(starsText(stars), w / 2f, oy + cellSize * 2.2f, textPaint);
            textPaint.setColor(C_TEXT);
        }
        // 聚光灯柱（胜）
        if (win) {
            paint.setColor(0x18FFF176);
            Path beam = new Path();
            beam.moveTo(w / 2f - cellSize * 1.4f, 0);
            beam.lineTo(w / 2f + cellSize * 1.4f, 0);
            beam.lineTo(w / 2f + cellSize * 2.6f, oy + cellSize * 2.6f);
            beam.lineTo(w / 2f - cellSize * 2.6f, oy + cellSize * 2.6f);
            beam.close();
            canvas.drawPath(beam, paint);
        }
    }

    private void drawStar(Canvas canvas, float cx, float cy, float r) {
        Path star = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 - Math.PI / 2;
            float rr = (i % 2 == 0) ? r : r * 0.45f;
            float px = cx + (float) Math.cos(a) * rr;
            float py = cy + (float) Math.sin(a) * rr;
            if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
        }
        star.close();
        canvas.drawPath(star, paint);
    }

    private static String starsText(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) sb.append('★');
        return sb.toString();
    }

    // =====================================================================
    // 颜色工具
    // =====================================================================
    private static int towerColor(TowerType t) {
        switch (t) {
            case BOTTLE: return 0xFF63B3ED;
            case SUN: return 0xFFFFD54F;
            case SNOW: return 0xFFB3E5FC;
            case FAN: return 0xFFB0BEC5;
            case POISON: return 0xFFCE93D8;
            case ROCKET: return 0xFFFF8A80;
            case LIGHTNING: return 0xFFB388FF;
            case SNIPER: return 0xFF80DEEA;
            case MINE: return 0xFF78909C;
            case AMPLIFIER: return 0xFF26C6DA;
            default: return 0xFF9E9E9E;
        }
    }

    private static int monsterColor(MonsterType t) {
        switch (t) {
            case NORMAL: return 0xFF8D6E63;
            case FAST: return 0xFFFFB74D;
            case TANK: return 0xFF6D4C41;
            case FLY: return 0xFF81C784;
            case SWARM: return 0xFFA1887F;
            case HEALER: return 0xFFF48FB1;
            case SHIELD: return 0xFF90A4AE;
            case BOSS: return 0xFFE53935;
            case SPLITTER: return 0xFF9575CD;
            case CHARGER: return 0xFFFFA726;
            case SHIELD_GENERATOR: return 0xFF26A69A;
            case SUMMONER: return 0xFFAB47BC;
            case RESISTANT: return 0xFF607D8B;
            case RAGER: return 0xFFEF5350;
            default: return 0xFF455A64;
        }
    }

    private static int hpColor(float ratio) {
        if (ratio > 0.5f) return 0xFF66BB6A;
        if (ratio > 0.25f) return 0xFFFFA726;
        return 0xFFE57373;
    }

    private static int beamColor(TowerType t) {
        switch (t) {
            case BOTTLE: return 0xFF64B5F6;
            case SUN: return 0xFFFFD54F;
            case SNOW: return 0xFF81D4FA;
            case FAN: return 0xFFCFD8DC;
            case POISON: return 0xFFBA68C8;
            case ROCKET: return 0xFFFF8A80;
            case LIGHTNING: return 0xFFE1BEE7;
            case SNIPER: return 0xFF80DEEA;
            case MINE: return 0xFFFF7043;
            default: return 0xFFFFEB3B;
        }
    }

    /** 将颜色向白色混合 k(0..1) 比例，用于受击闪白 */
    private static int blendWhite(int c, float k) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // =====================================================================
    // 触摸交互
    // =====================================================================
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (game == null || game.isEnded() || cellSize <= 0f) return true;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            int[] cell = cellAt(event.getX(), event.getY());
            pressedRow = cell == null ? -1 : cell[0];
            pressedCol = cell == null ? -1 : cell[1];
            dragStartRow = pressedRow;
            dragStartCol = pressedCol;
            downX = event.getX();
            downY = event.getY();
            draggingTower = false;
            dragTargetRow = dragTargetCol = -1;
            dragTargetValid = false;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (!draggingTower && dragStartRow >= 0 && dragStartCol >= 0
                    && game.getTowerAt(dragStartRow, dragStartCol) != null
                    && Math.hypot(event.getX() - downX, event.getY() - downY) >= touchSlop) {
                draggingTower = true;
                pressedRow = pressedCol = -1;
            }
            if (draggingTower) {
                int[] cell = cellAt(event.getX(), event.getY());
                dragTargetRow = cell == null ? -1 : cell[0];
                dragTargetCol = cell == null ? -1 : cell[1];
                TdGame.Tower source = game.getTowerAt(dragStartRow, dragStartCol);
                TdGame.Tower target = cell == null ? null : game.getTowerAt(cell[0], cell[1]);
                dragTargetValid = source != null && target != null && target != source
                        && source.type == target.type && source.level == target.level
                        && target.level < 3;
                invalidate();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pressedRow = -1;
            pressedCol = -1;
            resetDragState();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            int[] cell = cellAt(event.getX(), event.getY());
            if (draggingTower) {
                if (cell != null && listener != null) {
                    listener.onTowerDragged(dragStartRow, dragStartCol, cell[0], cell[1]);
                }
                resetDragState();
                invalidate();
                return true;
            }
            boolean sameCell = cell != null && cell[0] == pressedRow && cell[1] == pressedCol;
            pressedRow = -1;
            pressedCol = -1;
            if (!sameCell) {
                invalidate();
                return true;
            }
            int row = cell[0];
            int col = cell[1];
            TdGame.Tower existing = game.getTowerAt(row, col);
            if (existing != null) {
                hoverTower = existing;
                if (listener != null) listener.onTowerSelected(row, col);
            } else if (selectedType != null) {
                hoverTower = null;
                if (listener != null) listener.onTowerPlaced(row, col, selectedType);
            } else if (listener != null) {
                listener.onTowerDeselected();
            }
            invalidate();
        }
        return true;
    }

    /** 把 TdView 本地像素坐标转换成棋盘格，供塔牌拖拽落点使用。 */
    public int[] cellAt(float x, float y) {
        if (x < originX || y < originY
                || x >= originX + game.getCols() * cellSize
                || y >= originY + game.getRows() * cellSize) return null;
        int col = (int) ((x - originX) / cellSize);
        int row = (int) ((y - originY) / cellSize);
        if (row < 0 || row >= game.getRows() || col < 0 || col >= game.getCols()) return null;
        return new int[] { row, col };
    }

    public void clearSelection() {
        hoverTower = null;
        invalidate();
    }

    /** Fragment 的底部塔牌拖拽预览。 */
    public void setPaletteDragTarget(TowerType type, int row, int col, boolean valid) {
        paletteDragType = type;
        paletteDragRow = row;
        paletteDragCol = col;
        paletteDragValid = valid;
        invalidate();
    }

    public void clearPaletteDragTarget() {
        paletteDragType = null;
        paletteDragRow = paletteDragCol = -1;
        paletteDragValid = false;
        invalidate();
    }

    private void resetDragState() {
        draggingTower = false;
        dragStartRow = dragStartCol = -1;
        dragTargetRow = dragTargetCol = -1;
        dragTargetValid = false;
    }

    public TdGame.Tower getHoverTower() { return hoverTower; }
}
