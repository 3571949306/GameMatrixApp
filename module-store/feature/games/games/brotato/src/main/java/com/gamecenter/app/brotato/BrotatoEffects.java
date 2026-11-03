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
 * 土豆兄弟战斗特效与掉落拾取系统：
 * 包含翡翠材料宝石物理散落与磁吸、浮动暴击伤害数字、受击火花粒子与枪口火光。
 */
public final class BrotatoEffects {

    private final Random random = new Random();
    private final List<Gem> gems = new ArrayList<>();
    private final List<DamageNumber> damageNumbers = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final Path gemPath = new Path();

    /** 屏幕震颤强度与倒计时（带平滑二次阻尼衰减与最大位移约束） */
    private float shakeIntensity = 0f;
    private int shakeDuration = 0;
    private int maxShakeDuration = 0;

    public static class Gem {
        public float x, y;
        public float vx, vy;
        public int value;
        public int color;
        public boolean collected;
        public int age;
        public float animScale = 1.0f;

        public Gem(float x, float y, int value, int color, float vx, float vy) {
            this.x = x;
            this.y = y;
            this.value = value;
            this.color = color;
            this.vx = vx;
            this.vy = vy;
            this.collected = false;
            this.age = 0;
        }

        public boolean update(float playerX, float playerY, float magnetRadius, float playerRadius) {
            age++;
            // 初始散落减速
            vx *= 0.88f;
            vy *= 0.88f;
            x += vx;
            y += vy;

            float dx = playerX - x;
            float dy = playerY - y;
            float distSq = dx * dx + dy * dy;
            float dist = (float) Math.sqrt(distSq);

            // 触碰玩家：拾取成功
            if (dist < playerRadius + 4f) {
                collected = true;
                return true;
            }

            // 磁吸加速飞向玩家
            if (dist < magnetRadius && dist > 0.001f) {
                float pullSpeed = 4.5f + (magnetRadius - dist) * 0.08f;
                x += (dx / dist) * pullSpeed;
                y += (dy / dist) * pullSpeed;
            }
            return false;
        }
    }

    public static class DamageNumber {
        public float x, y;
        public String text;
        public int color;
        public boolean isCrit;
        public int age;
        public final int maxAge = 26;

        public DamageNumber(float x, float y, String text, int color, boolean isCrit) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.isCrit = isCrit;
            this.age = 0;
        }

        public boolean update() {
            age++;
            y -= (isCrit ? 0.9f : 0.65f);
            return age < maxAge;
        }

        public float alpha() {
            return Math.max(0f, 1f - (float) age / maxAge);
        }
    }

    public static class Particle {
        public float x, y;
        public float vx, vy;
        public int color;
        public float size;
        public int age;
        public int maxAge;

        public Particle(float x, float y, float vx, float vy, int color, float size, int maxAge) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.size = size;
            this.age = 0;
            this.maxAge = maxAge;
        }

        public boolean update() {
            age++;
            x += vx;
            y += vy;
            vx *= 0.92f;
            vy *= 0.92f;
            return age < maxAge;
        }

        public float alpha() {
            return Math.max(0f, 1f - (float) age / maxAge);
        }
    }

    public void clear() {
        gems.clear();
        damageNumbers.clear();
        particles.clear();
        shakeDuration = 0;
        maxShakeDuration = 0;
        shakeIntensity = 0f;
    }

    /**
     * 触发经过调校的平滑打击感屏幕震颤
     * @param intensity 震颤强度（dp标量，会自动按二次阻尼衰减，并严格硬上限约束在 3.5dp 以内）
     * @param durationFrames 持续帧数（通常为 3~6 帧，短促有力）
     */
    public void triggerScreenShake(float intensity, int durationFrames) {
        if (durationFrames <= 0 || intensity <= 0f) return;
        // 严格硬约束最高单次震感在 3.5dp 以内，避免晃眼与晕眩
        float clamped = Math.min(intensity, 3.5f);

        // 计算当前尚未衰减完的震度余量
        float remaining = 0f;
        if (shakeDuration > 0 && maxShakeDuration > 0) {
            float p = (float) shakeDuration / (float) maxShakeDuration;
            remaining = shakeIntensity * (p * p);
        }

        this.shakeIntensity = Math.max(remaining, clamped);
        this.shakeDuration = Math.max(this.shakeDuration, durationFrames);
        this.maxShakeDuration = Math.max(this.maxShakeDuration, durationFrames);
    }

    public float getShakeOffsetX() {
        if (shakeDuration <= 0 || maxShakeDuration <= 0) return 0f;
        float progress = (float) shakeDuration / (float) maxShakeDuration; // 1.0 -> 0.0
        float damped = shakeIntensity * (progress * progress); // 二次平滑衰减
        return (random.nextFloat() * 2f - 1f) * damped;
    }

    public float getShakeOffsetY() {
        if (shakeDuration <= 0 || maxShakeDuration <= 0) return 0f;
        float progress = (float) shakeDuration / (float) maxShakeDuration;
        float damped = shakeIntensity * (progress * progress);
        return (random.nextFloat() * 2f - 1f) * damped;
    }

    /**
     * 敌人被击杀时掉落翡翠或钻石宝石
     */
    public void spawnGemsOnKill(float x, float y, int scoreValue, float extraChance) {
        int gemCount = 1;
        if (scoreValue >= 5) gemCount = 3;
        if (scoreValue >= 15) gemCount = 6;
        if (random.nextFloat() < extraChance) gemCount++;

        for (int i = 0; i < gemCount; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            float speed = 2.0f + random.nextFloat() * 4.0f;
            float vx = (float) (Math.cos(angle) * speed);
            float vy = (float) (Math.sin(angle) * speed);
            int color = 0xFF10B981; // 翡翠绿
            int val = 1;
            if (i == 0 && scoreValue >= 10) {
                color = 0xFFF59E0B; // 黄金钻
                val = 5;
            } else if (random.nextFloat() < 0.2f) {
                color = 0xFF06B6D4; // 幽蓝钻
                val = 2;
            }
            gems.add(new Gem(x, y, val, color, vx, vy));
        }

        // 击杀伴随小爆炸火花
        spawnBurst(x, y, 0xFFFFA000, 8, 3.5f);
    }

    /**
     * 造成伤害时跳出浮动伤害数字
     */
    public void spawnDamage(float x, float y, float damage, boolean isCrit) {
        String text;
        int color;
        if (isCrit) {
            text = "暴击! " + (int) Math.round(damage);
            color = 0xFFFF5722;
        } else {
            text = String.format(java.util.Locale.US, "%.0f", damage);
            color = 0xFFFFEB3B;
        }
        // 增加一点随机微小横向偏移，避免多个数字重叠
        float offsetX = (random.nextFloat() * 10f - 5f);
        damageNumbers.add(new DamageNumber(x + offsetX, y - 8f, text, color, isCrit));
    }

    /**
     * 产生小范围粒子冲击波
     */
    public void spawnBurst(float x, float y, int color, int count, float maxSpeed) {
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            float speed = 1.0f + random.nextFloat() * maxSpeed;
            float vx = (float) (Math.cos(angle) * speed);
            float vy = (float) (Math.sin(angle) * speed);
            float size = 2.5f + random.nextFloat() * 3.5f;
            int maxAge = 12 + random.nextInt(12);
            particles.add(new Particle(x, y, vx, vy, color, size, maxAge));
        }
    }

    /**
     * 每帧推进物理与特效，返回本帧收集到的材料总数
     */
    public int update(float playerX, float playerY, float magnetRadius, float playerRadius) {
        if (shakeDuration > 0) {
            shakeDuration--;
            if (shakeDuration == 0) {
                shakeIntensity = 0f;
                maxShakeDuration = 0;
            }
        }

        int collectedMaterials = 0;
        // 更新宝石
        for (Iterator<Gem> it = gems.iterator(); it.hasNext(); ) {
            Gem gem = it.next();
            if (gem.update(playerX, playerY, magnetRadius, playerRadius)) {
                collectedMaterials += gem.value;
                // 拾取光效
                spawnBurst(playerX, playerY, gem.color, 4, 2.5f);
                it.remove();
            }
        }

        // 更新伤害大字
        for (Iterator<DamageNumber> it = damageNumbers.iterator(); it.hasNext(); ) {
            if (!it.next().update()) {
                it.remove();
            }
        }

        // 更新粒子
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            if (!it.next().update()) {
                it.remove();
            }
        }

        return collectedMaterials;
    }

    /**
     * 绘制宝石、粒子与浮动伤害
     */
    public void draw(Canvas canvas, Paint paint, float density) {
        // 1. 绘制掉落宝石（棱形翡翠水晶）
        paint.setStyle(Paint.Style.FILL);
        for (Gem gem : gems) {
            float gx = gem.x * density;
            float gy = gem.y * density;
            float s = (gem.value > 1 ? 5.5f : 4.2f) * density;

            gemPath.reset();
            gemPath.moveTo(gx, gy - s);
            gemPath.lineTo(gx + s * 0.75f, gy);
            gemPath.lineTo(gx, gy + s);
            gemPath.lineTo(gx - s * 0.75f, gy);
            gemPath.close();

            paint.setColor(gem.color);
            canvas.drawPath(gemPath, paint);

            // 水晶内芯亮白高光
            paint.setColor(0xCCFFFFFF);
            canvas.drawCircle(gx, gy - s * 0.25f, s * 0.25f, paint);
        }

        // 2. 绘制受击粒子
        for (Particle p : particles) {
            float px = p.x * density;
            float py = p.y * density;
            int alpha = (int) (p.alpha() * 255);
            int baseColor = p.color;
            paint.setColor(Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)));
            canvas.drawCircle(px, py, p.size * density * (0.5f + 0.5f * p.alpha()), paint);
        }

        // 3. 绘制伤害大字
        for (DamageNumber dn : damageNumbers) {
            float dx = dn.x * density;
            float dy = dn.y * density;
            int alpha = (int) (dn.alpha() * 255);
            paint.setTextAlign(Paint.Align.CENTER);

            if (dn.isCrit) {
                paint.setTextSize(17 * density);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setColor(Color.argb(alpha, 255, 60, 0));
                canvas.drawText(dn.text, dx, dy, paint);
                // 叠一层亮黄内芯
                paint.setTextSize(15 * density);
                paint.setColor(Color.argb(alpha, 255, 240, 100));
                canvas.drawText(dn.text, dx, dy, paint);
            } else {
                paint.setTextSize(13 * density);
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                paint.setColor(Color.argb(alpha, 255, 235, 59));
                canvas.drawText(dn.text, dx, dy, paint);
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
