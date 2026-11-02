package com.gamecenter.app.brotato;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 《蛋壳特工队》战地掉落物生态系统：
 * 包含四阶立体多边形经验晶石（绿/蓝/金/红）、四大战术神装道具（全图磁铁/核弹/烤鸡腿/金币箱）
 * 与精英怪三叶草幸运宝箱（Lucky Chest）。
 */
public final class SurvivorDrops {

    public enum DropType {
        EXP_GEM,
        MAGNET,
        NUKE_BOMB,
        CHICKEN,
        GOLD_POUCH,
        LUCKY_CHEST
    }

    public static class DropItem {
        public DropType type;
        public float x, y;
        public float vx, vy;
        public int tier; // 晶石品质 1=绿(1EXP), 2=蓝(5EXP), 3=金(20EXP), 4=红(100EXP)
        public int expValue;
        public boolean collected;
        public int age;
        public float floatPhase;

        public DropItem(DropType type, float x, float y, int tier, int expValue, float vx, float vy) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.tier = tier;
            this.expValue = expValue;
            this.vx = vx;
            this.vy = vy;
            this.collected = false;
            this.age = 0;
            this.floatPhase = (float) (Math.random() * Math.PI * 2);
        }
    }

    public interface DropListener {
        void onExpCollected(int exp);
        void onMagnetTriggered();
        void onNukeTriggered();
        void onChickenCollected();
        void onLuckyChestOpened(int luckyRolls);
    }

    private final Random random = new Random();
    private final List<DropItem> items = new ArrayList<>();
    private final Path gemPath = new Path();
    private DropListener listener;

    /** 全图磁铁吸引状态 */
    private boolean magnetSuctionActive = false;

    public void setDropListener(DropListener listener) {
        this.listener = listener;
    }

    public void clear() {
        items.clear();
        magnetSuctionActive = false;
    }

    /**
     * 敌人被击杀时，根据强度产出经验晶石或概率产出战术道具
     */
    public void spawnOnKill(float x, float y, int scoreValue, boolean isBoss, boolean isElite) {
        // 1. 若为 Boss 或 精英怪，必定掉落三叶草幸运宝箱！
        if (isBoss || isElite || scoreValue >= 25) {
            items.add(new DropItem(DropType.LUCKY_CHEST, x, y, 0, 0, 0, -1.5f));
            // 伴随大额红晶石
            items.add(new DropItem(DropType.EXP_GEM, x + 10, y + 10, 4, 100, 1.5f, 1.5f));
            return;
        }

        // 2. 极小概率爆落四大战术道具
        float tacticalRoll = random.nextFloat();
        if (tacticalRoll < 0.025f) {
            // 战术磁铁
            items.add(new DropItem(DropType.MAGNET, x, y, 0, 0, 0, -1f));
            return;
        } else if (tacticalRoll < 0.045f) {
            // 全屏核弹
            items.add(new DropItem(DropType.NUKE_BOMB, x, y, 0, 0, 0, -1f));
            return;
        } else if (tacticalRoll < 0.075f) {
            // 香脆烤鸡腿
            items.add(new DropItem(DropType.CHICKEN, x, y, 0, 0, 0, -1f));
            return;
        }

        // 3. 正常产出对应阶级的经验晶石
        int tier;
        int exp;
        if (scoreValue >= 10) {
            tier = 3; // 金
            exp = 20;
        } else if (scoreValue >= 5) {
            tier = 2; // 蓝
            exp = 5;
        } else {
            tier = 1; // 绿
            exp = 1;
        }

        double angle = random.nextDouble() * Math.PI * 2.0;
        float speed = 2.0f + random.nextFloat() * 3.0f;
        float vx = (float) (Math.cos(angle) * speed);
        float vy = (float) (Math.sin(angle) * speed);

        items.add(new DropItem(DropType.EXP_GEM, x, y, tier, exp, vx, vy));
    }

    /** 触发战术磁铁吸取全图晶石 */
    public void triggerMagnetSuction() {
        this.magnetSuctionActive = true;
    }

    /**
     * 每帧推进物理运动与磁吸吸附
     */
    public void update(float playerX, float playerY, float magnetRadius, float playerRadius) {
        for (Iterator<DropItem> it = items.iterator(); it.hasNext(); ) {
            DropItem item = it.next();
            item.age++;
            item.vx *= 0.88f;
            item.vy *= 0.88f;
            item.x += item.vx;
            item.y += item.vy;

            float dx = playerX - item.x;
            float dy = playerY - item.y;
            float dist = (float) Math.hypot(dx, dy);

            // 触碰特工拾取结算
            if (dist < playerRadius + 8f) {
                handlePickup(item);
                it.remove();
                continue;
            }

            // 磁吸加速（磁铁全图爆发或进入磁吸半径）
            boolean shouldPull = magnetSuctionActive || (item.type == DropType.EXP_GEM && dist < magnetRadius);
            if (shouldPull && dist > 0.01f) {
                float pullSpeed = magnetSuctionActive ? 18f : (5.5f + (magnetRadius - dist) * 0.1f);
                item.x += (dx / dist) * pullSpeed;
                item.y += (dy / dist) * pullSpeed;
            }
        }
        if (items.isEmpty()) {
            magnetSuctionActive = false;
        }
    }

    private void handlePickup(DropItem item) {
        if (listener == null) return;
        switch (item.type) {
            case EXP_GEM:
                listener.onExpCollected(item.expValue);
                break;
            case MAGNET:
                triggerMagnetSuction();
                listener.onMagnetTriggered();
                break;
            case NUKE_BOMB:
                listener.onNukeTriggered();
                break;
            case CHICKEN:
                listener.onChickenCollected();
                break;
            case LUCKY_CHEST:
                // 70%概率1连抽，20%概率3连抽，10%概率5连抽大满贯
                float roll = random.nextFloat();
                int rolls = (roll < 0.7f) ? 1 : (roll < 0.9f) ? 3 : 5;
                listener.onLuckyChestOpened(rolls);
                break;
            case GOLD_POUCH:
            default:
                break;
        }
    }

    /**
     * 绘制所有战利品道具
     */
    public void draw(Canvas canvas, Paint paint, float density) {
        paint.setStyle(Paint.Style.FILL);

        for (DropItem item : items) {
            float ix = item.x * density;
            float iy = item.y * density;
            float floatOffset = (float) Math.sin(item.age * 0.12 + item.floatPhase) * 2.5f * density;

            switch (item.type) {
                case EXP_GEM:
                    drawFacetedGem(canvas, paint, ix, iy + floatOffset, item.tier, density);
                    break;
                case MAGNET:
                    drawMagnetIcon(canvas, paint, ix, iy + floatOffset, density);
                    break;
                case NUKE_BOMB:
                    drawNukeBombIcon(canvas, paint, ix, iy + floatOffset, density);
                    break;
                case CHICKEN:
                    drawChickenIcon(canvas, paint, ix, iy + floatOffset, density);
                    break;
                case LUCKY_CHEST:
                    drawLuckyChestIcon(canvas, paint, ix, iy + floatOffset, item.age, density);
                    break;
                default:
                    break;
            }
        }
    }

    /** 立体多面八面体晶石绘制（绿、蓝、金、红） */
    private void drawFacetedGem(Canvas canvas, Paint paint, float x, float y, int tier, float density) {
        int mainColor;
        int lightColor;
        float s;

        switch (tier) {
            case 2: // 碧蓝 (5 EXP)
                mainColor = 0xFF0284C7;
                lightColor = 0xFF38BDF8;
                s = 5.5f * density;
                break;
            case 3: // 璨金 (20 EXP)
                mainColor = 0xFFD97706;
                lightColor = 0xFFFCD34D;
                s = 7.0f * density;
                break;
            case 4: // 猩红 (100 EXP)
                mainColor = 0xFFDC2626;
                lightColor = 0xFFFCA5A5;
                s = 9.0f * density;
                break;
            case 1: // 翡翠绿 (1 EXP)
            default:
                mainColor = 0xFF059669;
                lightColor = 0xFF34D399;
                s = 4.5f * density;
                break;
        }

        // 外层阴影面
        gemPath.reset();
        gemPath.moveTo(x, y - s);
        gemPath.lineTo(x + s * 0.8f, y);
        gemPath.lineTo(x, y + s);
        gemPath.lineTo(x - s * 0.8f, y);
        gemPath.close();
        paint.setColor(mainColor);
        canvas.drawPath(gemPath, paint);

        // 内部高光折射面
        gemPath.reset();
        gemPath.moveTo(x, y - s * 0.7f);
        gemPath.lineTo(x + s * 0.45f, y);
        gemPath.lineTo(x, y + s * 0.2f);
        gemPath.lineTo(x - s * 0.45f, y);
        gemPath.close();
        paint.setColor(lightColor);
        canvas.drawPath(gemPath, paint);

        // 水晶顶峰白亮点
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y - s * 0.35f, s * 0.22f, paint);
    }

    /** 战术磁铁绘制 */
    private void drawMagnetIcon(Canvas canvas, Paint paint, float x, float y, float density) {
        float r = 10 * density;
        paint.setColor(0xFFEF4444); // 磁铁红极
        canvas.drawCircle(x - r * 0.3f, y, r * 0.6f, paint);
        paint.setColor(0xFF3B82F6); // 磁铁蓝极
        canvas.drawCircle(x + r * 0.3f, y, r * 0.6f, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(12 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🧲", x, y + 4 * density, paint);
    }

    /** 全屏核弹绘制 */
    private void drawNukeBombIcon(Canvas canvas, Paint paint, float x, float y, float density) {
        paint.setColor(0xFF15803D); // 核弹军绿
        canvas.drawCircle(x, y, 11 * density, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(14 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("💣", x, y + 5 * density, paint);
    }

    /** 香脆烤鸡腿绘制 */
    private void drawChickenIcon(Canvas canvas, Paint paint, float x, float y, float density) {
        paint.setColor(0xFFD97706); // 香脆金黄
        canvas.drawCircle(x, y, 10 * density, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(14 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🍗", x, y + 5 * density, paint);
    }

    /** 三叶草幸运宝箱绘制（带金光脉冲） */
    private void drawLuckyChestIcon(Canvas canvas, Paint paint, float x, float y, int age, float density) {
        float pulse = (float) (0.85 + 0.15 * Math.sin(age * 0.18));
        float r = 14 * density * pulse;

        // 宝箱外轮廓金光光晕
        paint.setColor(0x4DF59E0B);
        canvas.drawCircle(x, y, r * 1.5f, paint);

        // 宝箱实体
        paint.setColor(0xFFB45309);
        canvas.drawRoundRect(x - r, y - r * 0.8f, x + r, y + r * 0.8f, 4 * density, 4 * density, paint);
        paint.setColor(0xFFFCD34D);
        canvas.drawRect(x - r * 0.2f, y - r * 0.8f, x + r * 0.2f, y + r * 0.8f, paint);

        // 三叶草标志
        paint.setTextSize(15 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("🍀", x, y + 5 * density, paint);
    }
}
