package com.gamecenter.app.games.brotato;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class BrotatoView extends View {

    private BrotatoGame game;
    private int cellSize;
    private float offsetX;
    private float offsetY;
    private boolean joystickActive;
    private float joystickBaseX;
    private float joystickBaseY;
    private float joystickInputX;
    private float joystickInputY;

    private Paint bgPaint;
    private Paint gridPaint;
    private Paint playerPaint;
    private Paint playerOutlinePaint;
    private Paint enemyPaint;
    private Paint bulletPaint;
    private Paint expPaint;
    private Paint goldPaint;
    private Paint hpPickupPaint;
    private Paint textPaint;
    private Paint smallTextPaint;
    private Paint barBgPaint;
    private Paint hpBarPaint;
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

    public void setGame(BrotatoGame game) {
        this.game = game;
        recalcDimensions(getWidth(), getHeight());
        invalidate();
    }

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

    public void setJoystick(boolean active, float baseX, float baseY, float inputX, float inputY) {
        joystickActive = active;
        joystickBaseX = baseX;
        joystickBaseY = baseY;
        joystickInputX = inputX;
        joystickInputY = inputY;
        invalidate();
    }

    private void drawArena(Canvas canvas) {
        Paint arenaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arenaPaint.setColor(Color.rgb(30, 41, 59));
        arenaPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, getWidth(), getHeight(), arenaPaint);

        BrotatoGame.Player player = game.getPlayer();
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

    private void drawPickups(Canvas canvas) {
        for (BrotatoGame.Pickup pickup : game.getPickups()) {
            float x = toScreenX(pickup.x);
            float y = toScreenY(pickup.y);
            if (pickup.type == BrotatoGame.Pickup.Type.GOLD) {
                drawDiamond(canvas, x, y, cellSize * 0.45f, goldPaint);
            } else if (pickup.type == BrotatoGame.Pickup.Type.HP || pickup.type == BrotatoGame.Pickup.Type.MEDKIT) {
                canvas.drawCircle(x, y, cellSize * 0.42f, hpPickupPaint);
                smallTextPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(pickup.type == BrotatoGame.Pickup.Type.MEDKIT ? "80%" : "+", x, y + cellSize * 0.28f, smallTextPaint);
            } else if (pickup.type == BrotatoGame.Pickup.Type.MAGNET) {
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

    private void drawBullets(Canvas canvas) {
        for (BrotatoGame.Bullet bullet : game.getBullets()) {
            bulletPaint.setColor(bullet.color);
            canvas.drawCircle(toScreenX(bullet.x), toScreenY(bullet.y), Math.max(3f, cellSize * bullet.size), bulletPaint);
        }
    }

    private void drawEnemies(Canvas canvas) {
        for (BrotatoGame.Enemy enemy : game.getEnemies()) {
            int bodyColor;
            int headColor;
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
            if (x < -80 || x > getWidth() + 80 || y < -80 || y > getHeight() + 80) {
                continue;
            }
            float size = Math.max(5f, cellSize * enemy.size);
            drawHumanoid(canvas, x, y, size, bodyColor, headColor, true);

            float hpRatio = Math.max(0f, enemy.hp / (float) enemy.maxHp);
            float hpWidth = size * 2.1f;
            float hpHeight = Math.max(3f, cellSize * 0.22f);
            canvas.drawRect(x - hpWidth / 2, y - size - hpHeight - 2, x + hpWidth / 2, y - size - 2, barBgPaint);
            canvas.drawRect(x - hpWidth / 2, y - size - hpHeight - 2, x - hpWidth / 2 + hpWidth * hpRatio, y - size - 2, hpBarPaint);
        }
    }

    private void drawPlayer(Canvas canvas) {
        BrotatoGame.Player player = game.getPlayer();
        float x = toScreenX(player.x);
        float y = toScreenY(player.y);
        float size = cellSize * player.size;
        float angle = (float) Math.toRadians(player.angle);

        drawHumanoid(canvas, x, y, size, Color.rgb(34, 197, 94), Color.rgb(190, 242, 100), false);

        Paint aimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        aimPaint.setColor(Color.argb(90, 255, 255, 255));
        aimPaint.setStrokeWidth(Math.max(2f, cellSize * 0.15f));
        canvas.drawLine(x, y, x + (float) Math.cos(angle) * size * 2.2f, y + (float) Math.sin(angle) * size * 2.2f, aimPaint);

        for (int i = 0; i < game.getWeapons().size(); i++) {
            BrotatoGame.Weapon weapon = game.getWeapons().get(i);
            float weaponAngle = angle + (i - (game.getWeapons().size() - 1) / 2f) * 0.42f;
            drawWeapon(canvas, x, y, size, weaponAngle, weapon, i == 0);
        }
    }

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

        float headY = y - size * 0.62f;
        float bodyTop = y - size * 0.2f;
        float bodyBottom = y + size * 0.65f;

        canvas.drawLine(x - size * 0.45f, y + size * 0.05f, x - size * 0.85f, y + size * 0.48f, limbPaint);
        canvas.drawLine(x + size * 0.45f, y + size * 0.05f, x + size * 0.85f, y + size * 0.48f, limbPaint);
        canvas.drawLine(x - size * 0.22f, bodyBottom, x - size * 0.48f, y + size * 1.02f, limbPaint);
        canvas.drawLine(x + size * 0.22f, bodyBottom, x + size * 0.48f, y + size * 1.02f, limbPaint);

        RectF body = new RectF(x - size * 0.5f, bodyTop, x + size * 0.5f, bodyBottom);
        canvas.drawRoundRect(body, size * 0.22f, size * 0.22f, bodyPaint);
        canvas.drawCircle(x, headY, size * 0.42f, headPaint);

        Paint eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eyePaint.setColor(hostile ? Color.rgb(254, 226, 226) : Color.rgb(15, 23, 42));
        canvas.drawCircle(x - size * 0.14f, headY - size * 0.03f, Math.max(1.8f, size * 0.055f), eyePaint);
        canvas.drawCircle(x + size * 0.14f, headY - size * 0.03f, Math.max(1.8f, size * 0.055f), eyePaint);
        canvas.drawRoundRect(new RectF(x - size * 0.32f, y + size * 0.12f, x + size * 0.32f, y + size * 0.32f), size * 0.08f, size * 0.08f, playerOutlinePaint);
    }

    private void drawWeapon(Canvas canvas, float x, float y, float size, float angle, BrotatoGame.Weapon weapon, boolean primary) {
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

        switch (weapon.type) {
            case SHOTGUN:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.9f, 0.54f);
                canvas.drawRoundRect(new RectF(scale * 1.15f, -scale * 0.33f, scale * 2.25f, -scale * 0.15f), scale * 0.08f, scale * 0.08f, metalPaint);
                canvas.drawRoundRect(new RectF(scale * 1.15f, scale * 0.15f, scale * 2.25f, scale * 0.33f), scale * 0.08f, scale * 0.08f, metalPaint);
                canvas.drawRoundRect(new RectF(scale * 2.18f, -scale * 0.4f, scale * 2.42f, scale * 0.4f), scale * 0.1f, scale * 0.1f, darkPaint);
                break;
            case SMG:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.35f, 0.46f);
                canvas.drawRoundRect(new RectF(scale * 0.28f, scale * 0.25f, scale * 0.62f, scale * 1.0f), scale * 0.08f, scale * 0.08f, darkPaint);
                canvas.drawRoundRect(new RectF(scale * 1.05f, -scale * 0.12f, scale * 1.72f, scale * 0.12f), scale * 0.08f, scale * 0.08f, metalPaint);
                canvas.drawCircle(scale * 0.05f, -scale * 0.1f, scale * 0.18f, highlightPaint);
                break;
            case RIFLE:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 2.15f, 0.42f);
                canvas.drawRoundRect(new RectF(scale * 1.35f, -scale * 0.11f, scale * 2.75f, scale * 0.11f), scale * 0.07f, scale * 0.07f, metalPaint);
                canvas.drawRoundRect(new RectF(scale * 0.0f, -scale * 0.52f, scale * 0.78f, -scale * 0.32f), scale * 0.08f, scale * 0.08f, darkPaint);
                canvas.drawRoundRect(new RectF(scale * 0.28f, scale * 0.25f, scale * 0.62f, scale * 0.95f), scale * 0.08f, scale * 0.08f, darkPaint);
                break;
            case LASER:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.85f, 0.48f);
                Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                glowPaint.setStyle(Paint.Style.FILL);
                glowPaint.setColor(Color.argb(150, 255, 121, 198));
                canvas.drawRoundRect(new RectF(scale * 0.2f, -scale * 0.18f, scale * 1.55f, scale * 0.18f), scale * 0.18f, scale * 0.18f, glowPaint);
                canvas.drawCircle(scale * 1.75f, 0, scale * 0.22f, glowPaint);
                canvas.drawRoundRect(new RectF(scale * 1.3f, -scale * 0.08f, scale * 2.25f, scale * 0.08f), scale * 0.06f, scale * 0.06f, metalPaint);
                break;
            case ROCKET:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.75f, 0.72f);
                canvas.drawRoundRect(new RectF(scale * 1.15f, -scale * 0.45f, scale * 2.25f, scale * 0.45f), scale * 0.22f, scale * 0.22f, bodyPaint);
                canvas.drawCircle(scale * 2.32f, 0, scale * 0.46f, darkPaint);
                canvas.drawCircle(scale * 2.32f, 0, scale * 0.28f, metalPaint);
                canvas.drawPath(makeFinPath(scale), darkPaint);
                break;
            default:
                drawGunBody(canvas, bodyPaint, darkPaint, metalPaint, scale, 1.35f, 0.42f);
                canvas.drawRoundRect(new RectF(scale * 0.95f, -scale * 0.1f, scale * 1.7f, scale * 0.1f), scale * 0.07f, scale * 0.07f, metalPaint);
                canvas.drawRoundRect(new RectF(scale * 0.15f, scale * 0.23f, scale * 0.45f, scale * 0.85f), scale * 0.07f, scale * 0.07f, darkPaint);
                break;
        }

        canvas.restore();
    }

    private void drawGunBody(Canvas canvas, Paint bodyPaint, Paint darkPaint, Paint metalPaint, float scale, float length, float height) {
        canvas.drawRoundRect(new RectF(-scale * 0.35f, -scale * height, scale * length, scale * height), scale * 0.16f, scale * 0.16f, bodyPaint);
        canvas.drawRoundRect(new RectF(-scale * 0.72f, -scale * 0.24f, -scale * 0.18f, scale * 0.24f), scale * 0.12f, scale * 0.12f, darkPaint);
        canvas.drawRoundRect(new RectF(scale * 0.05f, scale * 0.25f, scale * 0.45f, scale * 0.82f), scale * 0.1f, scale * 0.1f, darkPaint);
        canvas.drawRoundRect(new RectF(scale * 0.45f, -scale * 0.18f, scale * (length + 0.55f), scale * 0.18f), scale * 0.08f, scale * 0.08f, metalPaint);
        canvas.drawRoundRect(new RectF(scale * 0.18f, -scale * (height + 0.18f), scale * 0.82f, -scale * height), scale * 0.08f, scale * 0.08f, darkPaint);
    }

    private Path makeFinPath(float scale) {
        Path path = new Path();
        path.moveTo(scale * 0.95f, scale * 0.46f);
        path.lineTo(scale * 1.35f, scale * 1.0f);
        path.lineTo(scale * 1.6f, scale * 0.42f);
        path.close();
        return path;
    }

    private void drawHud(Canvas canvas) {
        BrotatoGame.Player player = game.getPlayer();
        float margin = 18f;
        float barW = Math.min(getWidth() - margin * 2, 360f);
        float hpRatio = Math.max(0f, player.hp / (float) player.maxHp);
        float expRatio = Math.max(0f, game.getExp() / (float) game.getExpToLevel());

        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Lv." + game.getLevel() + "  波次 " + game.getWave() + "  击杀 " + game.getKills(), margin, 34, textPaint);

        canvas.drawRoundRect(new RectF(margin, 48, margin + barW, 66), 6, 6, barBgPaint);
        canvas.drawRoundRect(new RectF(margin, 48, margin + barW * hpRatio, 66), 6, 6, hpBarPaint);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(player.hp + "/" + player.maxHp, margin + barW / 2, 64, smallTextPaint);

        canvas.drawRoundRect(new RectF(margin, 74, margin + barW, 90), 6, 6, barBgPaint);
        canvas.drawRoundRect(new RectF(margin, 74, margin + barW * expRatio, 90), 6, 6, expBarPaint);

        smallTextPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("时间 " + formatTime(game.getElapsedTime()) + " / 10:00  金币 " + game.getGold(), margin, 116, smallTextPaint);
        String bossText = game.isFinalBossSpawned() ? "终局 Boss 已出现" : "得分 " + game.getScore();
        canvas.drawText(bossText + "  难度+" + game.getBossThreatLevel() + "  武器 " + game.getWeapons().size() + "/" + BrotatoGame.MAX_WEAPONS, margin, 142, smallTextPaint);
    }

    private void drawJoystick(Canvas canvas) {
        if (!joystickActive) return;

        float radius = Math.max(64f, getWidth() * 0.14f);
        float knobX = joystickBaseX + joystickInputX * radius;
        float knobY = joystickBaseY + joystickInputY * radius;

        Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setColor(Color.argb(80, 226, 232, 240));
        canvas.drawCircle(joystickBaseX, joystickBaseY, radius, basePaint);

        Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
        ringPaint.setColor(Color.argb(150, 255, 255, 255));
        canvas.drawCircle(joystickBaseX, joystickBaseY, radius, ringPaint);

        Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(Color.argb(185, 34, 197, 94));
        canvas.drawCircle(knobX, knobY, radius * 0.38f, knobPaint);
    }

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

    private void drawGameOver(Canvas canvas) {
        Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        overlay.setColor(Color.argb(205, 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), overlay);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

    private void drawDiamond(Canvas canvas, float x, float y, float size, Paint paint) {
        Path path = new Path();
        path.moveTo(x, y - size);
        path.lineTo(x + size, y);
        path.lineTo(x, y + size);
        path.lineTo(x - size, y);
        path.close();
        canvas.drawPath(path, paint);
    }

    private float toScreenX(float x) {
        return offsetX + (x - game.getPlayer().x) * cellSize;
    }

    private float toScreenY(float y) {
        return offsetY + (y - game.getPlayer().y) * cellSize;
    }

    private String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
