package com.gamecenter.app.games.brotato;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Brotato 风格射击生存游戏的自定义渲染视图。
 * <p>
 * 职责：
 * <ul>
 *   <li>将游戏逻辑层的数据（坐标、状态）映射为屏幕像素并绘制</ul>
 *   <li>绘制游戏场景：竞技场网格、拾取物、子弹、敌人、玩家、武器、HUD</li>
 *   <li>绘制虚拟摇杆的视觉反馈</li>
 *   <li>绘制游戏暂停/结束的覆盖层</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>摄像机始终以玩家为中心，通过 toScreenX/toScreenY 实现坐标转换</li>
 *   <li>所有游戏坐标（BrotatoGame 中的单位）通过 cellSize 缩放为屏幕像素</li>
 *   <li>角色使用"人形"绘制（头+身体+四肢），而非简单圆形</li>
 *   <li>每种武器有独特的绘制外观，通过 canvas.save/restore + 旋转实现方向对齐</li>
 * </ul>
 */
public class BrotatoView extends View {

    /** 游戏逻辑对象引用 */
    private BrotatoGame game;

    /** 每个游戏单位对应的像素数，用于坐标缩放 */
    private int cellSize;

    /** 屏幕中心 X 偏移量，用于以玩家为中心的摄像机 */
    private float offsetX;

    /** 屏幕中心 Y 偏移量，用于以玩家为中心的摄像机 */
    private float offsetY;

    /** 摇杆是否激活 */
    private boolean joystickActive;

    /** 摇杆圆心 X（屏幕像素） */
    private float joystickBaseX;

    /** 摇杆圆心 Y（屏幕像素） */
    private float joystickBaseY;

    /** 摇杆方向输入 X（归一化） */
    private float joystickInputX;

    /** 摇杆方向输入 Y（归一化） */
    private float joystickInputY;

    /** 背景画笔 */
    private Paint bgPaint;

    /** 网格线画笔 */
    private Paint gridPaint;

    /** 玩家身体画笔 */
    private Paint playerPaint;

    /** 玩家轮廓画笔（白色描边） */
    private Paint playerOutlinePaint;

    /** 敌人画笔（颜色动态设置） */
    private Paint enemyPaint;

    /** 子弹画笔（颜色动态设置） */
    private Paint bulletPaint;

    /** 经验拾取物画笔 */
    private Paint expPaint;

    /** 金币拾取物画笔 */
    private Paint goldPaint;

    /** 生命拾取物画笔 */
    private Paint hpPickupPaint;

    /** HUD 主文本画笔 */
    private Paint textPaint;

    /** HUD 小文本画笔 */
    private Paint smallTextPaint;

    /** 血条/经验条背景画笔 */
    private Paint barBgPaint;

    /** 生命条画笔 */
    private Paint hpBarPaint;

    /** 经验条画笔 */
    private Paint expBarPaint;

    public BrotatoView(Context context) {
        super(context);
        init();
    }

    public BrotatoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BrotatoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化所有画笔，设置颜色、样式和尺寸。
     * <p>
     * 所有画笔均启用抗锯齿（ANTI_ALIAS_FLAG），确保绘制边缘平滑。
     */
    private void init() {
        setWillNotDraw(false);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.rgb(17, 24, 39));
        bgPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.argb(60, 148, 163, 184));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        playerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerPaint.setColor(Color.rgb(74, 222, 128));
        playerPaint.setStyle(Paint.Style.FILL);

        playerOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        playerOutlinePaint.setColor(Color.WHITE);
        playerOutlinePaint.setStyle(Paint.Style.STROKE);
        playerOutlinePaint.setStrokeWidth(3f);

        enemyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        enemyPaint.setStyle(Paint.Style.FILL);

        bulletPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bulletPaint.setStyle(Paint.Style.FILL);

        expPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        expPaint.setColor(Color.rgb(96, 165, 250));
        expPaint.setStyle(Paint.Style.FILL);

        goldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldPaint.setColor(Color.rgb(250, 204, 21));
        goldPaint.setStyle(Paint.Style.FILL);

        hpPickupPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hpPickupPaint.setColor(Color.rgb(248, 113, 113));
        hpPickupPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28);

        smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smallTextPaint.setColor(Color.rgb(226, 232, 240));
        smallTextPaint.setTextSize(20);

        barBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barBgPaint.setColor(Color.rgb(51, 65, 85));
        barBgPaint.setStyle(Paint.Style.FILL);

        hpBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hpBarPaint.setColor(Color.rgb(239, 68, 68));
        hpBarPaint.setStyle(Paint.Style.FILL);

        expBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        expBarPaint.setColor(Color.rgb(59, 130, 246));
        expBarPaint.setStyle(Paint.Style.FILL);
    }

    /**
     * 设置游戏逻辑对象引用，并重新计算视图尺寸。
     *
     * @param game 游戏逻辑对象
     */
    public void setGame(BrotatoGame game) {
        this.game = game;
        recalcDimensions(getWidth(), getHeight());
        invalidate();
    }

    /**
     * 根据视图尺寸重新计算 cellSize 和偏移量。
     * <p>
     * cellSize 取宽高比限制下的较小值，确保整个棋盘可见。
     * 偏移量设为视图中心，配合 toScreenX/Y 实现以玩家为中心的摄像机。
     *
     * @param w 视图宽度
     * @param h 视图高度
     */
    private void recalcDimensions(int w, int h) {
        if (w <= 0 || h <= 0) return;
        cellSize = Math.min(w / BrotatoGame.BOARD_WIDTH, h / BrotatoGame.BOARD_HEIGHT);
        cellSize = Math.max(1, cellSize);
        offsetX = w / 2f;
        offsetY = h / 2f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalcDimensions(w, h);
    }

    /**
     * 主绘制方法，按层次依次绘制所有游戏元素。
     * <p>
     * 绘制顺序（从底到顶）：竞技场背景 → 拾取物 → 子弹 → 敌人 → 玩家 → 摇杆 → HUD → 覆盖层。
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bgPaint);
        if (game == null || cellSize <= 0) return;

        drawArena(canvas);
        drawPickups(canvas);
        drawBullets(canvas);
        drawEnemies(canvas);
        drawPlayer(canvas);
        drawJoystick(canvas);
        drawHud(canvas);

        if (game.isWaitingForUpgrade()) {
            drawPausedOverlay(canvas, "升级时间");
        }
        if (game.isGameOver()) {
            drawGameOver(canvas);
        }
    }

    /**
     * 设置虚拟摇杆的视觉状态，由 Activity 的触摸事件驱动。
     *
     * @param active 摇杆是否激活
     * @param baseX  摇杆圆心 X（屏幕像素）
     * @param baseY  摇杆圆心 Y（屏幕像素）
     * @param inputX 归一化方向输入 X
     * @param inputY 归一化方向输入 Y
     */
    public void setJoystick(boolean active, float baseX, float baseY, float inputX, float inputY) {
        joystickActive = active;
        joystickBaseX = baseX;
        joystickBaseY = baseY;
        joystickInputX = inputX;
        joystickInputY = inputY;
        invalidate();
    }

    /**
     * 绘制竞技场背景和网格线。
     * <p>
     * 网格线以玩家位置为中心，每隔 5 个游戏单位绘制一条，
     * 营造移动时的视觉参照感。
     *
     * @param canvas 画布
     */
    private void drawArena(Canvas canvas) {
        Paint arenaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arenaPaint.setColor(Color.rgb(30, 41, 59));
        arenaPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, getWidth(), getHeight(), arenaPaint);

        BrotatoGame.Player player = game.getPlayer();
        // 以玩家位置为中心计算网格范围
        int startX = (int) Math.floor(player.x / 5f) * 5 - 60;
        int endX = startX + 120;
        int startY = (int) Math.floor(player.y / 5f) * 5 - 80;
        int endY = startY + 160;
        for (int x = startX; x <= endX; x += 5) {
            float px = toScreenX(x);
            canvas.drawLine(px, 0, px, getHeight(), gridPaint);
        }
        for (int y = startY; y <= endY; y += 5) {
            float py = toScreenY(y);
            canvas.drawLine(0, py, getWidth(), py, gridPaint);
        }
    }

    /**
     * 绘制所有拾取物。
     * <p>
     * 不同类型使用不同形状和颜色：
     * 金币→菱形、经验→圆形、生命/医疗包→圆形+文字、磁铁→弧形+极点。
     *
     * @param canvas 画布
     */
    private void drawPickups(Canvas canvas) {
        for (BrotatoGame.Pickup pickup : game.getPickups()) {
            float x = toScreenX(pickup.x);
            float y = toScreenY(pickup.y);
            if (pickup.type == BrotatoGame.Pickup.Type.GOLD) {
                drawDiamond(canvas, x, y, cellSize * 0.45f, goldPaint);
            } else if (pickup.type == BrotatoGame.Pickup.Type.HP || pickup.type == BrotatoGame.Pickup.Type.MEDKIT) {
                canvas.drawCircle(x, y, cellSize * 0.42f, hpPickupPaint);
                smallTextPaint.setTextAlign(Paint.Align.CENTER);
                // 医疗包显示"80%"，普通生命显示"+"
                canvas.drawText(pickup.type == BrotatoGame.Pickup.Type.MEDKIT ? "80%" : "+", x, y + cellSize * 0.28f, smallTextPaint);
            } else if (pickup.type == BrotatoGame.Pickup.Type.MAGNET) {
                // 磁铁：绘制弧形磁铁轮廓和两个极点
                Paint magnetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                magnetPaint.setStyle(Paint.Style.STROKE);
                magnetPaint.setStrokeCap(Paint.Cap.ROUND);
                magnetPaint.setStrokeWidth(Math.max(4f, cellSize * 0.28f));
                magnetPaint.setColor(Color.rgb(34, 211, 238));
                RectF arc = new RectF(x - cellSize * 0.55f, y - cellSize * 0.55f, x + cellSize * 0.55f, y + cellSize * 0.55f);
                canvas.drawArc(arc, 35, 290, false, magnetPaint);
                Paint polePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                polePaint.setStyle(Paint.Style.FILL);
                polePaint.setColor(Color.rgb(239, 68, 68));
                canvas.drawCircle(x - cellSize * 0.42f, y + cellSize * 0.35f, cellSize * 0.16f, polePaint);
                canvas.drawCircle(x + cellSize * 0.42f, y + cellSize * 0.35f, cellSize * 0.16f, polePaint);
            } else {
                canvas.drawCircle(x, y, cellSize * 0.34f, expPaint);
            }
        }
    }

    /**
     * 绘制所有子弹，使用武器对应的颜色。
     *
     * @param canvas 画布
     */
    private void drawBullets(Canvas canvas) {
        for (BrotatoGame.Bullet bullet : game.getBullets()) {
            bulletPaint.setColor(bullet.color);
            canvas.drawCircle(toScreenX(bullet.x), toScreenY(bullet.y), Math.max(3f, cellSize * bullet.size), bulletPaint);
        }
    }

    /**
     * 绘制所有敌人，包括人形身体和血条。
     * <p>
     * 不同敌人类型使用不同配色方案。
     * 超出屏幕范围（±80px 缓冲区）的敌人跳过绘制以优化性能。
     *
     * @param canvas 画布
     */
    private void drawEnemies(Canvas canvas) {
        for (BrotatoGame.Enemy enemy : game.getEnemies()) {
            int bodyColor;
            int headColor;
            // 根据敌人类型选择配色
            if (enemy.kind == BrotatoGame.Enemy.Kind.FINAL_BOSS) {
                bodyColor = Color.rgb(88, 28, 135);
                headColor = Color.rgb(253, 224, 71);
            } else if (enemy.kind == BrotatoGame.Enemy.Kind.MINI_BOSS) {
                bodyColor = Color.rgb(190, 18, 60);
                headColor = Color.rgb(251, 113, 133);
            } else if (enemy.kind == BrotatoGame.Enemy.Kind.ELITE) {
                bodyColor = Color.rgb(14, 116, 144);
                headColor = Color.rgb(103, 232, 249);
            } else if (enemy.kind == BrotatoGame.Enemy.Kind.BRUTE) {
                bodyColor = Color.rgb(126, 34, 206);
                headColor = Color.rgb(192, 132, 252);
            } else if (enemy.kind == BrotatoGame.Enemy.Kind.RUNNER) {
                bodyColor = Color.rgb(234, 88, 12);
                headColor = Color.rgb(253, 186, 116);
            } else {
                bodyColor = Color.rgb(185, 28, 28);
                headColor = Color.rgb(248, 113, 113);
            }

            float x = toScreenX(enemy.x);
            float y = toScreenY(enemy.y);
            // 视锥裁剪：超出屏幕范围的敌人不绘制
            if (x < -80 || x > getWidth() + 80 || y < -80 || y > getHeight() + 80) {
                continue;
            }
            float size = Math.max(5f, cellSize * enemy.size);
            drawHumanoid(canvas, x, y, size, bodyColor, headColor, true);

            // 绘制敌人血条
            float hpRatio = Math.max(0f, enemy.hp / (float) enemy.maxHp);
            float hpWidth = size * 2.1f;
            float hpHeight = Math.max(3f, cellSize * 0.22f);
            canvas.drawRect(x - hpWidth / 2, y - size - hpHeight - 2, x + hpWidth / 2, y - size - 2, barBgPaint);
            canvas.drawRect(x - hpWidth / 2, y - size - hpHeight - 2, x - hpWidth / 2 + hpWidth * hpRatio, y - size - 2, hpBarPaint);
        }
    }

    /**
     * 绘制玩家角色，包括人形身体、瞄准线和所有携带的武器。
     * <p>
     * 武器以扇形分布在玩家朝向周围，主武器（索引0）尺寸略大。
     *
     * @param canvas 画布
     */
    private void drawPlayer(Canvas canvas) {
        BrotatoGame.Player player = game.getPlayer();
        float x = toScreenX(player.x);
        float y = toScreenY(player.y);
        float size = cellSize * player.size;
        float angle = (float) Math.toRadians(player.angle);

        drawHumanoid(canvas, x, y, size, Color.rgb(34, 197, 94), Color.rgb(190, 242, 100), false);

        // 绘制瞄准线（半透明白色）
        Paint aimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        aimPaint.setColor(Color.argb(90, 255, 255, 255));
        aimPaint.setStrokeWidth(Math.max(2f, cellSize * 0.15f));
        canvas.drawLine(x, y, x + (float) Math.cos(angle) * size * 2.2f, y + (float) Math.sin(angle) * size * 2.2f, aimPaint);

        // 绘制所有武器，以扇形分布在玩家朝向周围
        for (int i = 0; i < game.getWeapons().size(); i++) {
            BrotatoGame.Weapon weapon = game.getWeapons().get(i);
            float weaponAngle = angle + (i - (game.getWeapons().size() - 1) / 2f) * 0.42f;
            drawWeapon(canvas, x, y, size, weaponAngle, weapon, i == 0);
        }
    }

    /**
     * 绘制人形角色（头+身体+四肢）。
     * <p>
     * 敌人和玩家共用此方法，通过 hostile 参数区分配色。
     * 敌人使用灰色四肢和红色眼睛，玩家使用深色四肢和深色眼睛。
     *
     * @param canvas 画布
     * @param x 角色中心 X（屏幕像素）
     * @param y 角色中心 Y（屏幕像素）
     * @param size 角色缩放尺寸
     * @param bodyColor 身体颜色
     * @param headColor 头部颜色
     * @param hostile 是否为敌方角色
     */
    private void drawHumanoid(Canvas canvas, float x, float y, float size, int bodyColor, int headColor, boolean hostile) {
        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setColor(bodyColor);

        Paint limbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        limbPaint.setColor(hostile ? Color.rgb(71, 85, 105) : Color.rgb(15, 23, 42));
        limbPaint.setStrokeCap(Paint.Cap.ROUND);
        limbPaint.setStrokeWidth(Math.max(3f, size * 0.25f));

        Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headPaint.setStyle(Paint.Style.FILL);
        headPaint.setColor(headColor);

        // 头部位置（身体上方）
        float headY = y - size * 0.62f;
        float bodyTop = y - size * 0.2f;
        float bodyBottom = y + size * 0.65f;

        // 绘制四肢（手臂和腿）
        canvas.drawLine(x - size * 0.45f, y + size * 0.05f, x - size * 0.85f, y + size * 0.48f, limbPaint);
        canvas.drawLine(x + size * 0.45f, y + size * 0.05f, x + size * 0.85f, y + size * 0.48f, limbPaint);
        canvas.drawLine(x - size * 0.22f, bodyBottom, x - size * 0.48f, y + size * 1.02f, limbPaint);
        canvas.drawLine(x + size * 0.22f, bodyBottom, x + size * 0.48f, y + size * 1.02f, limbPaint);

        // 绘制身体（圆角矩形）和头部（圆形）
        RectF body = new RectF(x - size * 0.5f, bodyTop, x + size * 0.5f, bodyBottom);
        canvas.drawRoundRect(body, size * 0.22f, size * 0.22f, bodyPaint);
        canvas.drawCircle(x, headY, size * 0.42f, headPaint);

        // 绘制眼睛
        Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eyePaint.setColor(hostile ? Color.rgb(254, 226, 226) : Color.rgb(15, 23, 42));
        canvas.drawCircle(x - size * 0.14f, headY - size * 0.03f, Math.max(1.8f, size * 0.055f), eyePaint);
        canvas.drawCircle(x + size * 0.14f, headY - size * 0.03f, Math.max(1.8f, size * 0.055f), eyePaint);
        // 胸口装饰线（玩家为白色轮廓）
        canvas.drawRoundRect(new RectF(x - size * 0.32f, y + size * 0.12f, x + size * 0.32f, y + size * 0.32f), size * 0.08f, size * 0.08f, playerOutlinePaint);
    }

    /**
     * 绘制武器图形。
     * <p>
     * 使用 canvas.save/translate/rotate 将坐标系变换到武器挂载点（肩膀位置），
     * 然后根据武器类型绘制不同的枪械外观。
     * 主武器（primary=true）尺寸略大（1.0x），副武器缩小至 0.86x。
     *
     * @param canvas 画布
     * @param x 玩家中心 X
     * @param y 玩家中心 Y
     * @param size 角色缩放尺寸
     * @param angle 武器朝向角度（弧度）
     * @param weapon 武器对象
     * @param primary 是否为主武器
     */
    private void drawWeapon(Canvas canvas, float x, float y, float size, float angle, BrotatoGame.Weapon weapon, boolean primary) {
        // 计算肩膀位置（身体侧面偏移）
        float shoulderX = x + (float) Math.cos(angle + Math.PI / 2) * size * 0.42f;
        float shoulderY = y + (float) Math.sin(angle + Math.PI / 2) * size * 0.42f;
        float scale = size * (primary ? 1f : 0.86f);

        canvas.save();
        canvas.translate(shoulderX, shoulderY);
        canvas.rotate((float) Math.toDegrees(angle));

        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setColor(weapon.color);

        Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        darkPaint.setStyle(Paint.Style.FILL);
        darkPaint.setColor(Color.rgb(15, 23, 42));

        Paint metalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        metalPaint.setStyle(Paint.Style.FILL);
        metalPaint.setColor(Color.rgb(203, 213, 225));

        Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        highlightPaint.setStyle(Paint.Style.FILL);
        highlightPaint.setColor(Color.argb(210, 255, 255, 255));

        // 根据武器类型绘制不同的枪械外观
        switch (weapon.type) {
            case SHOTGUN:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.9f, 0.54f);
                // 双管设计
                canvas.drawRoundRect(new RectF(scale * 1.15f, -scale * 0.33f, scale * 2.25f, -scale * 0.15f), scale * 0.08f, scale * 0.08f, metalPaint);
                canvas.drawRoundRect(new RectF(scale * 1.15f, scale * 0.15f, scale * 2.25f, scale * 0.33f), scale * 0.08f, scale * 0.08f, metalPaint);
                canvas.drawRoundRect(new RectF(scale * 2.18f, -scale * 0.4f, scale * 2.42f, scale * 0.4f), scale * 0.1f, scale * 0.1f, darkPaint);
                break;
            case SMG:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.35f, 0.46f);
                // 弹匣和枪管
                canvas.drawRoundRect(new RectF(scale * 0.28f, scale * 0.25f, scale * 0.62f, scale * 1.0f), scale * 0.08f, scale * 0.08f, darkPaint);
                canvas.drawRoundRect(new RectF(scale * 1.05f, -scale * 0.12f, scale * 1.72f, scale * 0.12f), scale * 0.08f, scale * 0.08f, metalPaint);
                canvas.drawCircle(scale * 0.05f, -scale * 0.1f, scale * 0.18f, highlightPaint);
                break;
            case RIFLE:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 2.15f, 0.42f);
                // 长枪管、瞄准镜和弹匣
                canvas.drawRoundRect(new RectF(scale * 1.35f, -scale * 0.11f, scale * 2.75f, scale * 0.11f), scale * 0.07f, scale * 0.07f, metalPaint);
                canvas.drawRoundRect(new RectF(scale * 0.0f, -scale * 0.52f, scale * 0.78f, -scale * 0.32f), scale * 0.08f, scale * 0.08f, darkPaint);
                canvas.drawRoundRect(new RectF(scale * 0.28f, scale * 0.25f, scale * 0.62f, scale * 0.95f), scale * 0.08f, scale * 0.08f, darkPaint);
                break;
            case LASER:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.85f, 0.48f);
                // 发光效果和能量管
                Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                glowPaint.setStyle(Paint.Style.FILL);
                glowPaint.setColor(Color.argb(150, 255, 121, 198));
                canvas.drawRoundRect(new RectF(scale * 0.2f, -scale * 0.18f, scale * 1.55f, scale * 0.18f), scale * 0.18f, scale * 0.18f, glowPaint);
                canvas.drawCircle(scale * 1.75f, 0, scale * 0.22f, glowPaint);
                canvas.drawRoundRect(new RectF(scale * 1.3f, -scale * 0.08f, scale * 2.25f, scale * 0.08f), scale * 0.06f, scale * 0.06f, metalPaint);
                break;
            case ROCKET:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.75f, 0.72f);
                // 粗炮管、弹头和尾翼
                canvas.drawRoundRect(new RectF(scale * 1.15f, -scale * 0.45f, scale * 2.25f, scale * 0.45f), scale * 0.22f, scale * 0.22f, bodyPaint);
                canvas.drawCircle(scale * 2.32f, 0, scale * 0.46f, darkPaint);
                canvas.drawCircle(scale * 2.32f, 0, scale * 0.28f, metalPaint);
                canvas.drawPath(makeFinPath(scale), darkPaint);
                break;
            default:
                // 手枪：枪身 + 枪管 + 握把
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.35f, 0.42f);
                canvas.drawRoundRect(new RectF(scale * 0.95f, -scale * 0.1f, scale * 1.7f, scale * 0.1f), scale * 0.07f, scale * 0.07f, metalPaint);
                canvas.drawRoundRect(new RectF(scale * 0.15f, scale * 0.23f, scale * 0.45f, scale * 0.85f), scale * 0.07f, scale * 0.07f, darkPaint);
                break;
        }

        canvas.restore();
    }

    /**
     * 绘制枪械的通用基础部分（枪身、握把、枪管、瞄具）。
     * <p>
     * 各武器类型共用此方法绘制基础结构，再叠加各自的特色部件。
     *
     * @param canvas 画布
     * @param bodyPaint 枪身画笔
     * @param darkPaint 深色部件画笔
     * @param metalPaint 金属部件画笔
     * @param scale 缩放系数
     * @param length 枪身长度系数
     * @param height 枪身高度系数
     */
    private void drawGunBody(Canvas canvas, Paint bodyPaint, Paint darkPaint, Paint metalPaint, float scale, float length, float height) {
        // 枪身主体
        canvas.drawRoundRect(new RectF(-scale * 0.35f, -scale * height, scale * length, scale * height), scale * 0.16f, scale * 0.16f, bodyPaint);
        // 握把
        canvas.drawRoundRect(new RectF(-scale * 0.72f, -scale * 0.24f, -scale * 0.18f, scale * 0.24f), scale * 0.12f, scale * 0.12f, darkPaint);
        // 扳机护圈
        canvas.drawRoundRect(new RectF(scale * 0.05f, scale * 0.25f, scale * 0.45f, scale * 0.82f), scale * 0.1f, scale * 0.1f, darkPaint);
        // 枪管
        canvas.drawRoundRect(new RectF(scale * 0.45f, -scale * 0.18f, scale * (length + 0.55f), scale * 0.18f), scale * 0.08f, scale * 0.08f, metalPaint);
        // 瞄具
        canvas.drawRoundRect(new RectF(scale * 0.18f, -scale * (height + 0.18f), scale * 0.82f, -scale * height), scale * 0.08f, scale * 0.08f, darkPaint);
    }

    /**
     * 生成火箭筒尾翼的路径。
     *
     * @param scale 缩放系数
     * @return 尾翼路径
     */
    private Path makeFinPath(float scale) {
        Path path = new Path();
        path.moveTo(scale * 0.95f, scale * 0.46f);
        path.lineTo(scale * 1.35f, scale * 1.0f);
        path.lineTo(scale * 1.6f, scale * 0.42f);
        path.close();
        return path;
    }

    /**
     * 绘制 HUD（抬头显示），包括等级、波次、击杀数、生命条、经验条、时间、金币等信息。
     *
     * @param canvas 画布
     */
    private void drawHud(Canvas canvas) {
        BrotatoGame.Player player = game.getPlayer();
        float margin = 18f;
        float barW = Math.min(getWidth() - margin * 2, 360f);
        float hpRatio = Math.max(0f, player.hp / (float) player.maxHp);
        float expRatio = Math.max(0f, game.getExp() / (float) game.getExpToLevel());

        // 第一行：等级、波次、击杀
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Lv." + game.getLevel() + "  波次 " + game.getWave() + "  击杀 " + game.getKills(), margin, 34, textPaint);

        // 第二行：生命条
        canvas.drawRoundRect(new RectF(margin, 48, margin + barW, 66), 6, 6, barBgPaint);
        canvas.drawRoundRect(new RectF(margin, 48, margin + barW * hpRatio, 66), 6, 6, hpBarPaint);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(player.hp + "/" + player.maxHp, margin + barW / 2, 64, smallTextPaint);

        // 第三行：经验条
        canvas.drawRoundRect(new RectF(margin, 74, margin + barW, 90), 6, 6, barBgPaint);
        canvas.drawRoundRect(new RectF(margin, 74, margin + barW * expRatio, 90), 6, 6, expBarPaint);

        // 第四行：时间、金币
        smallTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("时间 " + formatTime(game.getElapsedTime()) + " / 10:00  金币 " + game.getGold(), margin, 116, smallTextPaint);
        // 第五行：Boss 状态或得分、难度、武器数
        String bossText = game.isFinalBossSpawned() ? "终局 Boss 已出现" : "得分 " + game.getScore();
        canvas.drawText(bossText + "  难度+" + game.getBossThreatLevel() + "  武器 " + game.getWeapons().size() + "/" + BrotatoGame.MAX_WEAPONS, margin, 142, smallTextPaint);
    }

    /**
     * 绘制虚拟摇杆的视觉反馈。
     * <p>
     * 摇杆由三部分组成：半透明底座圆、白色描边圆环、绿色摇杆旋钮。
     * 旋钮位置由方向输入值决定。
     *
     * @param canvas 画布
     */
    private void drawJoystick(Canvas canvas) {
        if (!joystickActive) return;

        float radius = Math.max(64f, getWidth() * 0.14f);
        float knobX = joystickBaseX + joystickInputX * radius;
        float knobY = joystickBaseY + joystickInputY * radius;

        // 底座
        Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setColor(Color.argb(80, 226, 232, 240));
        canvas.drawCircle(joystickBaseX, joystickBaseY, radius, basePaint);

        // 圆环
        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
        ringPaint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawCircle(joystickBaseX, joystickBaseY, radius, ringPaint);

        // 旋钮
        Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(Color.argb(185, 34, 197, 94));
        canvas.drawCircle(knobX, knobY, radius * 0.38f, knobPaint);
    }

    /**
     * 绘制半透明暂停覆盖层和提示文字。
     *
     * @param canvas 画布
     * @param label 显示的提示文字
     */
    private void drawPausedOverlay(Canvas canvas, String label) {
        Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlay.setColor(Color.argb(130, 2, 6, 23));
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlay);

        Paint pausePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pausePaint.setColor(Color.WHITE);
        pausePaint.setTextAlign(Paint.Align.CENTER);
        pausePaint.setTextSize(48);
        canvas.drawText(label, getWidth() / 2f, getHeight() / 2f - 12, pausePaint);
    }

    /**
     * 绘制游戏结束画面。
     * <p>
     * 胜利时标题为绿色"胜利"，失败时为红色"游戏结束"。
     * 显示游戏时间和击杀数，提示点击重新开始。
     *
     * @param canvas 画布
     */
    private void drawGameOver(Canvas canvas) {
        Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlay.setColor(Color.argb(205, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlay);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // 胜利为绿色，失败为红色
        titlePaint.setColor(game.isGameWon() ? Color.rgb(74, 222, 128) : Color.rgb(248, 113, 113));
        titlePaint.setTextSize(58);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(game.isGameWon() ? "胜利" : "游戏结束", getWidth() / 2f, getHeight() / 2f - 62, titlePaint);

        Paint scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(32);
        scorePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("时间 " + formatTime(game.getElapsedTime()) + "  击杀 " + game.getKills(), getWidth() / 2f, getHeight() / 2f, scorePaint);
        canvas.drawText("点击画面重新开始", getWidth() / 2f, getHeight() / 2f + 52, scorePaint);
    }

    /**
     * 绘制菱形图案（用于金币拾取物）。
     *
     * @param canvas 画布
     * @param x 中心 X
     * @param y 中心 Y
     * @param size 菱形半径
     * @param paint 画笔
     */
    private void drawDiamond(Canvas canvas, float x, float y, float size, Paint paint) {
        Path path = new Path();
        path.moveTo(x, y - size);
        path.lineTo(x + size, y);
        path.lineTo(x, y + size);
        path.lineTo(x - size, y);
        path.close();
        canvas.drawPath(path, paint);
    }

    /**
     * 将游戏世界 X 坐标转换为屏幕像素 X 坐标。
     * <p>
     * 以玩家位置为中心，游戏坐标乘以 cellSize 后加上屏幕中心偏移。
     *
     * @param x 游戏 X 坐标
     * @return 屏幕 X 像素
     */
    private float toScreenX(float x) {
        return offsetX + (x - game.getPlayer().x) * cellSize;
    }

    /**
     * 将游戏世界 Y 坐标转换为屏幕像素 Y 坐标。
     *
     * @param y 游戏 Y 坐标
     * @return 屏幕 Y 像素
     */
    private float toScreenY(float y) {
        return offsetY + (y - game.getPlayer().y) * cellSize;
    }

    /**
     * 将毫秒时间格式化为 "分:秒" 字符串。
     *
     * @param millis 毫秒数
     * @return 格式化的时间字符串
     */
    private String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * 实现 performClick 以满足无障碍访问要求。
     */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
