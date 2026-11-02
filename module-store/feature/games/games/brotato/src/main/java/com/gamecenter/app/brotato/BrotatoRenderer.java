package com.gamecenter.app.brotato;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.gamecenter.app.brotato.engine.BrotatoArena;
import com.gamecenter.app.brotato.engine.Enemy;
import com.gamecenter.app.brotato.engine.Projectile;
import com.gamecenter.app.brotato.engine.Upgrade;

/**
 * 土豆兄弟全方位 2.5D / 3D 渲染与建模引擎：
 * 告别简陋几何圆点，提供全景工业废土竞技场地砖与动态光影、6位个性鲜明的立体土豆勇士
 * （拟真土豆体感、动态眨眼注视、拟真肢体行走、专属服饰与神兵）、8种怪物的精细异变体建模
 * 与多枪机械后坐力开火体系。
 */
public final class BrotatoRenderer {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tempPath = new Path();
    private final Path wingPath = new Path();
    private final Path hornPath = new Path();
    private final RectF tempRect = new RectF();

    public BrotatoRenderer() {
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    // =========================================================================
    // 1. 战场地面与动态环境光照建模 (Ground & Lighting)
    // =========================================================================

    public void drawArenaGround(Canvas canvas, float width, float height, float density,
                                float playerX, float playerY, long animTick) {
        // 1.1 《蛋壳特工队》经典废土都市深灰沥青地面
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF181B22);
        canvas.drawRect(0, 0, width, height, paint);

        // 1.2 人行横道斑马线 (Zebra Crosswalks)
        paint.setColor(0x18FFFFFF);
        float zebraW = 22 * density;
        float zebraGap = 14 * density;
        for (float x = 20 * density; x < width - 20 * density; x += zebraW + zebraGap) {
            canvas.drawRect(x, 18 * density, x + zebraW, 46 * density, paint);
            canvas.drawRect(x, height - 46 * density, x + zebraW, height - 18 * density, paint);
        }

        // 1.3 公路中央双黄实线与虚线 (Highway Double Yellow Center Lines)
        float centerY = height / 2f;
        paint.setColor(0x88F59E0B);
        paint.setStrokeWidth(2.5f * density);
        paint.setStyle(Paint.Style.STROKE);
        // 双黄实线
        canvas.drawLine(0, centerY - 3.5f * density, width, centerY - 3.5f * density, paint);
        canvas.drawLine(0, centerY + 3.5f * density, width, centerY + 3.5f * density, paint);

        // 纵向白色虚线分割带
        float centerX = width / 2f;
        paint.setColor(0x44FFFFFF);
        paint.setStrokeWidth(2f * density);
        for (float y = 0; y < height; y += 32 * density) {
            canvas.drawLine(centerX, y, centerX, y + 16 * density, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        // 1.4 四角与下水道铸铁圆形井盖 (Urban Sewer Manhole Covers)
        drawManholeCover(canvas, width * 0.22f, height * 0.28f, 18 * density, density);
        drawManholeCover(canvas, width * 0.78f, height * 0.28f, 18 * density, density);
        drawManholeCover(canvas, width * 0.22f, height * 0.72f, 18 * density, density);
        drawManholeCover(canvas, width * 0.78f, height * 0.72f, 18 * density, density);

        // 1.5 特工动态战术手电筒/聚光灯光晕（以特工为中心的真实径向光晕）
        float px = playerX * density;
        float py = playerY * density;
        float spotRadius = 220 * density;
        RadialGradient spotGradient = new RadialGradient(px, py, spotRadius,
                new int[]{0x33F8FAFC, 0x1838BDF8, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(spotGradient);
        canvas.drawCircle(px, py, spotRadius, paint);
        paint.setShader(null);

        // 1.6 边缘 45° 黑黄工业警戒防撞边框 (Hazard Warning Border)
        drawHazardBorder(canvas, width, height, density);
    }

    private void drawManholeCover(Canvas canvas, float cx, float cy, float r, float density) {
        // 外圈铸铁底色
        paint.setColor(0xFF262C36);
        canvas.drawCircle(cx, cy, r, paint);
        // 内凹同心圆
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.5f * density);
        strokePaint.setColor(0xFF1A1F27);
        canvas.drawCircle(cx, cy, r * 0.75f, strokePaint);
        // 井盖十字防滑网纹
        canvas.drawLine(cx - r * 0.6f, cy, cx + r * 0.6f, cy, strokePaint);
        canvas.drawLine(cx, cy - r * 0.6f, cx, cy + r * 0.6f, strokePaint);
        // 中心锁定螺栓
        paint.setColor(0xFF475569);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, 2.5f * density, paint);
    }

    private void drawHazardBorder(Canvas canvas, float width, float height, float density) {
        float borderW = 6 * density;
        paint.setStyle(Paint.Style.FILL);

        // 外层黄色警示底线
        paint.setColor(0xFFD97706);
        canvas.drawRect(0, 0, width, borderW, paint);
        canvas.drawRect(0, height - borderW, width, height, paint);
        canvas.drawRect(0, 0, borderW, height, paint);
        canvas.drawRect(width - borderW, 0, width, height, paint);

        // 45° 斜角黑色斑马斜纹
        paint.setColor(0xCC000000);
        float stripeStep = 18 * density;
        for (float x = 0; x < width; x += stripeStep) {
            tempPath.reset();
            tempPath.moveTo(x, 0);
            tempPath.lineTo(x + 8 * density, 0);
            tempPath.lineTo(x, borderW);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            tempPath.reset();
            tempPath.moveTo(x, height);
            tempPath.lineTo(x + 8 * density, height);
            tempPath.lineTo(x, height - borderW);
            tempPath.close();
            canvas.drawPath(tempPath, paint);
        }
    }

    // =========================================================================
    // 2. 土豆勇士角色全方位立体建模 (Potato Hero 3D Vector Modeling)
    // =========================================================================

    public void drawPlayer(Canvas canvas, BrotatoHero hero, float x, float y, float radius,
                           float aimAngle, boolean isMoving, long animTick, int damageFlashTicks,
                           float density) {
        canvas.save();
        canvas.translate(x, y);

        // 呼吸与行走颠簸仿真
        float walkCycle = isMoving ? (animTick * 0.45f) : (animTick * 0.1f);
        float bobY = isMoving ? Math.abs((float) Math.sin(walkCycle)) * 3f * density : 0f;
        float stretchX = 1.0f + (isMoving ? 0.05f * (float) Math.cos(walkCycle) : 0.02f * (float) Math.sin(walkCycle));
        float stretchY = 1.0f - (isMoving ? 0.05f * (float) Math.cos(walkCycle) : 0.02f * (float) Math.sin(walkCycle));
        canvas.translate(0, -bobY);
        canvas.scale(stretchX, stretchY);

        // 2.1 地面柔和接触阴影
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x4D000000);
        canvas.drawOval(new RectF(-radius * 1.15f, radius * 0.7f + bobY,
                radius * 1.15f, radius * 1.25f + bobY), paint);

        // 2.2 双脚迈步动画 (Walking Shoes)
        drawPotatoFeet(canvas, radius, isMoving, walkCycle, density);

        // 2.3 土豆有机本体球体光影 (Organic 3D Spherical Volume)
        drawPotatoBody(canvas, hero, radius, damageFlashTicks);

        // 2.4 英雄专属头饰、甲胄与特权装饰 (Bespoke Apparel)
        drawHeroBespokeGear(canvas, hero, radius, animTick, density);

        // 2.5 灵动大眼睛与动态视线锁定 (Expressive Eye Tracking)
        drawPotatoFace(canvas, radius, aimAngle, damageFlashTicks, animTick, density);

        canvas.restore();
    }

    private void drawPotatoFeet(Canvas canvas, float radius, boolean isMoving, float walkCycle, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF4A3427);
        float footR = radius * 0.26f;
        float footY = radius * 0.82f;
        float footOffset = isMoving ? (float) Math.sin(walkCycle) * (radius * 0.35f) : 0f;

        // 左脚
        canvas.drawCircle(-radius * 0.45f + footOffset, footY, footR, paint);
        // 右脚
        canvas.drawCircle(radius * 0.45f - footOffset, footY, footR, paint);
    }

    private void drawPotatoBody(Canvas canvas, BrotatoHero hero, float radius, int damageFlashTicks) {
        paint.setStyle(Paint.Style.FILL);

        int baseColor = hero.getBodyColor();
        int highlightColor;
        int shadowColor;

        if (damageFlashTicks > 0) {
            // 受击红白闪烁
            baseColor = 0xFFFF5252;
            highlightColor = 0xFFFFCDD2;
            shadowColor = 0xFFB71C1C;
        } else {
            // 依据英雄基准色提取立体球形高光与暗部
            highlightColor = lightenColor(baseColor, 0.4f);
            shadowColor = darkenColor(baseColor, 0.35f);
        }

        // 径向球体高光模拟：光源在左上方 (-0.3r, -0.35r)
        RadialGradient bodyShader = new RadialGradient(
                -radius * 0.3f, -radius * 0.35f, radius * 1.35f,
                new int[]{highlightColor, baseColor, shadowColor},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(bodyShader);

        // 土豆非对称椭圆有机外形（微微上宽下窄的自然块茎轮廓）
        tempRect.set(-radius * 1.02f, -radius * 0.98f, radius * 1.02f, radius * 1.04f);
        canvas.drawRoundRect(tempRect, radius * 0.95f, radius * 1.05f, paint);
        paint.setShader(null);

        // 外部深色墨线勾边
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.2f);
        strokePaint.setColor(darkenColor(baseColor, 0.55f));
        canvas.drawRoundRect(tempRect, radius * 0.95f, radius * 1.05f, strokePaint);

        // 土豆皮自然雀斑斑点 (Potato Eyes / Freckles)
        if (damageFlashTicks <= 0) {
            paint.setColor(darkenColor(baseColor, 0.25f));
            canvas.drawCircle(-radius * 0.55f, radius * 0.25f, radius * 0.08f, paint);
            canvas.drawCircle(-radius * 0.65f, radius * 0.45f, radius * 0.05f, paint);
            canvas.drawCircle(radius * 0.6f, radius * 0.35f, radius * 0.07f, paint);
        }
    }

    private void drawHeroBespokeGear(Canvas canvas, BrotatoHero hero, float radius, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);

        switch (hero) {
            case ALL_ROUNDER:
                // 《蛋壳特工队》看板特工「科萌」：标志性鲜红飘逸战术长领巾 + 工装战术背心
                // 1. 战术工装背心
                paint.setColor(0xFF334155);
                canvas.drawRoundRect(new RectF(-radius * 0.85f, radius * 0.2f, radius * 0.85f, radius * 0.95f),
                        4 * density, 4 * density, paint);
                // 战术口袋与金属搭扣
                paint.setColor(0xFF1E293B);
                canvas.drawRect(-radius * 0.65f, radius * 0.45f, -radius * 0.2f, radius * 0.75f, paint);
                canvas.drawRect(radius * 0.2f, radius * 0.45f, radius * 0.65f, radius * 0.75f, paint);
                paint.setColor(0xFF94A3B8);
                canvas.drawCircle(-radius * 0.42f, radius * 0.5f, 1.8f * density, paint);
                canvas.drawCircle(radius * 0.42f, radius * 0.5f, 1.8f * density, paint);

                // 2. 标志性鲜红战术领结
                paint.setColor(0xFFDC2626);
                canvas.drawCircle(0, radius * 0.15f, radius * 0.22f, paint);

                // 3. 动态物理飘逸双飘带 (随风浪荡飞扬)
                float wave1 = (float) Math.sin(animTick * 0.26) * 11 * density;
                float wave2 = (float) Math.cos(animTick * 0.29) * 9 * density;
                tempPath.reset();
                tempPath.moveTo(-radius * 0.15f, radius * 0.18f);
                tempPath.quadTo(-radius * 0.9f, radius * 0.5f + wave1 * 0.5f, -radius * 1.6f, radius * 0.35f + wave1);
                tempPath.lineTo(-radius * 1.55f, radius * 0.65f + wave1);
                tempPath.quadTo(-radius * 0.8f, radius * 0.65f + wave1 * 0.5f, -radius * 0.05f, radius * 0.28f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);

                tempPath.reset();
                tempPath.moveTo(radius * 0.05f, radius * 0.25f);
                tempPath.quadTo(-radius * 0.6f, radius * 0.8f + wave2 * 0.5f, -radius * 1.35f, radius * 0.95f + wave2);
                tempPath.lineTo(-radius * 1.25f, radius * 1.2f + wave2);
                tempPath.quadTo(-radius * 0.5f, radius * 0.95f + wave2 * 0.5f, radius * 0.15f, radius * 0.22f);
                tempPath.close();
                paint.setColor(0xFFB91C1C);
                canvas.drawPath(tempPath, paint);
                break;

            case BRAWLER:
                // 特工「金」：暴击女王 · 绯红双马尾 + 金色战术发箍
                paint.setColor(0xFFDC2626);
                float pigtailWave1 = (float) Math.sin(animTick * 0.25) * 6 * density;
                float pigtailWave2 = (float) Math.cos(animTick * 0.28) * 6 * density;
                // 左马尾
                tempPath.reset();
                tempPath.moveTo(-radius * 0.7f, -radius * 0.4f);
                tempPath.quadTo(-radius * 1.4f, -radius * 0.7f + pigtailWave1, -radius * 1.65f, radius * 0.3f + pigtailWave1);
                tempPath.lineTo(-radius * 1.35f, radius * 0.4f + pigtailWave1);
                tempPath.quadTo(-radius * 1.1f, -radius * 0.2f + pigtailWave1, -radius * 0.6f, -radius * 0.15f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                // 右马尾
                tempPath.reset();
                tempPath.moveTo(radius * 0.7f, -radius * 0.4f);
                tempPath.quadTo(radius * 1.4f, -radius * 0.7f + pigtailWave2, radius * 1.65f, radius * 0.3f + pigtailWave2);
                tempPath.lineTo(radius * 1.35f, radius * 0.4f + pigtailWave2);
                tempPath.quadTo(radius * 1.1f, -radius * 0.2f + pigtailWave2, radius * 0.6f, -radius * 0.15f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                // 金色发箍
                paint.setColor(0xFFF59E0B);
                tempRect.set(-radius * 0.85f, -radius * 0.65f, radius * 0.85f, -radius * 0.45f);
                canvas.drawRoundRect(tempRect, 2 * density, 2 * density, paint);
                break;

            case MAGE:
                // 特工「月咏」：月影剑客 · 墨紫短发 + 樱粉领结 + 武士刀柄
                paint.setColor(0xFF4C1D95);
                tempRect.set(-radius * 0.95f, -radius * 0.85f, radius * 0.95f, -radius * 0.25f);
                canvas.drawRoundRect(tempRect, 6 * density, 6 * density, paint);
                // 樱粉领结
                paint.setColor(0xFFF43F5E);
                canvas.drawCircle(0, radius * 0.15f, radius * 0.18f, paint);
                // 背负武士刀柄斜露
                paint.setColor(0xFF94A3B8);
                canvas.save();
                canvas.rotate(-35);
                canvas.drawRoundRect(new RectF(radius * 0.5f, -radius * 1.25f, radius * 0.7f, -radius * 0.2f),
                        2 * density, 2 * density, paint);
                paint.setColor(0xFFE11D48);
                canvas.drawRect(radius * 0.48f, -radius * 0.6f, radius * 0.72f, -radius * 0.5f, paint);
                canvas.restore();
                break;

            case CHUNKY:
                // 特工「沃尔默」：重装教官 · 凯夫拉重甲 + 特战护目镜
                paint.setColor(0xFF334155);
                canvas.drawRoundRect(new RectF(-radius * 1.15f, -radius * 0.85f, radius * 1.15f, -radius * 0.2f),
                        6 * density, 6 * density, paint);
                // 荧光青护目镜
                paint.setColor(0xFF06B6D4);
                canvas.drawRoundRect(new RectF(-radius * 0.75f, -radius * 0.35f, radius * 0.75f, -radius * 0.05f),
                        3 * density, 3 * density, paint);
                // 钛合金肩甲
                paint.setColor(0xFF64748B);
                canvas.drawCircle(-radius * 1.05f, radius * 0.2f, radius * 0.3f, paint);
                canvas.drawCircle(radius * 1.05f, radius * 0.2f, radius * 0.3f, paint);
                break;

            case LUCKY:
                // 特工「凯蒂斯」：机灵猫娘 · 战术猫耳无线电通信器 + 翡翠四叶草
                paint.setColor(0xFF10B981);
                // 左猫耳
                tempPath.reset();
                tempPath.moveTo(-radius * 0.75f, -radius * 0.55f);
                tempPath.lineTo(-radius * 0.65f, -radius * 1.35f);
                tempPath.lineTo(-radius * 0.25f, -radius * 0.75f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                // 右猫耳
                tempPath.reset();
                tempPath.moveTo(radius * 0.75f, -radius * 0.55f);
                tempPath.lineTo(radius * 0.65f, -radius * 1.35f);
                tempPath.lineTo(radius * 0.25f, -radius * 0.75f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                // 翡翠四叶草胸章
                paint.setColor(0xFF22C55E);
                canvas.drawCircle(0, radius * 0.2f, radius * 0.2f, paint);
                break;

            case SPEEDSTER:
                // 特工「斯巴达」：极速游侠 · 疾风战术披风 + 闪电红目夜视镜
                paint.setColor(0xFF0284C7);
                float capeWave = (float) Math.sin(animTick * 0.35) * 8 * density;
                tempPath.reset();
                tempPath.moveTo(-radius * 0.6f, radius * 0.1f);
                tempPath.lineTo(-radius * 1.5f, radius * 1.35f + capeWave);
                tempPath.lineTo(-radius * 0.8f, radius * 1.25f + capeWave);
                tempPath.lineTo(0f, radius * 0.25f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
                // 闪电黄目夜视镜
                paint.setColor(0xFFFBBF24);
                canvas.drawRoundRect(new RectF(-radius * 0.7f, -radius * 0.25f, radius * 0.7f, 0f),
                        2 * density, 2 * density, paint);
                break;
        }
    }

    private void drawPotatoFace(Canvas canvas, float radius, float aimAngle,
                                int damageFlashTicks, long animTick, float density) {
        float eyeY = -radius * 0.12f;
        float eyeSpacing = radius * 0.38f;
        float eyeR = radius * 0.28f;

        // 视线动态偏转 (Pupil Look-Ahead Offset)
        float lookX = (float) Math.cos(aimAngle) * (eyeR * 0.4f);
        float lookY = (float) Math.sin(aimAngle) * (eyeR * 0.3f);

        if (damageFlashTicks > 0) {
            // 受击闭眼表情 (> <)
            strokePaint.setColor(0xFF3E2723);
            strokePaint.setStrokeWidth(2.5f * density);
            canvas.drawLine(-eyeSpacing - eyeR * 0.6f, eyeY - eyeR * 0.4f, -eyeSpacing, eyeY, strokePaint);
            canvas.drawLine(-eyeSpacing, eyeY, -eyeSpacing - eyeR * 0.6f, eyeY + eyeR * 0.4f, strokePaint);

            canvas.drawLine(eyeSpacing + eyeR * 0.6f, eyeY - eyeR * 0.4f, eyeSpacing, eyeY, strokePaint);
            canvas.drawLine(eyeSpacing, eyeY, eyeSpacing + eyeR * 0.6f, eyeY + eyeR * 0.4f, strokePaint);
        } else {
            // 眼白与阴影
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(-eyeSpacing, eyeY, eyeR, paint);
            canvas.drawCircle(eyeSpacing, eyeY, eyeR, paint);

            // 瞳孔（黑亮眼球）
            paint.setColor(0xFF1A1A1A);
            float pupilR = eyeR * 0.58f;
            canvas.drawCircle(-eyeSpacing + lookX, eyeY + lookY, pupilR, paint);
            canvas.drawCircle(eyeSpacing + lookX, eyeY + lookY, pupilR, paint);

            // 灵动双高光 (Reflections)
            paint.setColor(Color.WHITE);
            canvas.drawCircle(-eyeSpacing + lookX - pupilR * 0.35f, eyeY + lookY - pupilR * 0.35f, pupilR * 0.38f, paint);
            canvas.drawCircle(eyeSpacing + lookX - pupilR * 0.35f, eyeY + lookY - pupilR * 0.35f, pupilR * 0.38f, paint);
            canvas.drawCircle(-eyeSpacing + lookX + pupilR * 0.28f, eyeY + lookY + pupilR * 0.28f, pupilR * 0.18f, paint);
            canvas.drawCircle(eyeSpacing + lookX + pupilR * 0.28f, eyeY + lookY + pupilR * 0.28f, pupilR * 0.18f, paint);
        }

        // 坚毅微笑嘴型
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0xFF3E2723);
        strokePaint.setStrokeWidth(2.2f * density);
        tempRect.set(-radius * 0.26f, eyeY + radius * 0.25f, radius * 0.26f, eyeY + radius * 0.55f);
        canvas.drawArc(tempRect, 0, 180, false, strokePaint);
    }

    // =========================================================================
    // 3. 多枪机械瞄准、轨道与后坐力建模 (Weapons & Recoil Engine)
    // =========================================================================

    public void drawWeapons(Canvas canvas, BrotatoArena arena, BrotatoHero hero,
                            float px, float py, float ps, long lastFireMs, long animTick, float density) {
        int shots = Math.max(1, arena.shotCount());
        Enemy target = arena.nearestEnemy();

        double baseAngle;
        if (target != null) {
            baseAngle = Math.atan2(target.y - arena.playerY(), target.x - arena.playerX());
        } else {
            baseAngle = (animTick * 0.04) % (Math.PI * 2);
        }

        double spreadRad = Math.toRadians(Upgrade.SPREAD_STEP_DEG);
        float orbitDist = ps + 14 * density;
        boolean justFired = (arena.clockMs() - lastFireMs < 64);

        for (int i = 0; i < shots; i++) {
            double angle = baseAngle + (i - (shots - 1) / 2.0) * spreadRad;
            float wx = px + (float) (Math.cos(angle - 0.28) * orbitDist);
            float wy = py + (float) (Math.sin(angle - 0.28) * orbitDist);

            canvas.save();
            canvas.translate(wx, wy);
            canvas.rotate((float) Math.toDegrees(angle));

            // 后坐力滑块位移 (Slide Blowback)
            if (justFired) {
                canvas.translate(-3.5f * density, 0);
            }

            // 绘制精细枪械模型
            drawFirearmModel(canvas, hero, density);

            // 枪口烈焰粒子 (Muzzle Flash Starburst)
            if (justFired) {
                drawMuzzleFlash(canvas, density);
            }

            canvas.restore();
        }
    }

    private void drawFirearmModel(Canvas canvas, BrotatoHero hero, float density) {
        paint.setStyle(Paint.Style.FILL);

        // 1. 枪身主体与机械切角 (Chamber & Receiver)
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(new RectF(0, -4.5f * density, 18 * density, 4.5f * density), 2 * density, 2 * density, paint);

        // 2. 枪管与消焰器 (Barrel & Shroud)
        LinearGradient barrelShader = new LinearGradient(
                4 * density, -2.5f * density, 22 * density, 2.5f * density,
                0xFFCBD5E1, 0xFF64748B, Shader.TileMode.CLAMP);
        paint.setShader(barrelShader);
        canvas.drawRoundRect(new RectF(4 * density, -2.5f * density, 22 * density, 2.5f * density), 1.5f * density, 1.5f * density, paint);
        paint.setShader(null);

        // 3. 枪管内部膛线深孔
        paint.setColor(0xFF0F172A);
        canvas.drawCircle(22 * density, 0, 1.8f * density, paint);

        // 4. 弹匣插槽 (Magazine Well)
        paint.setColor(0xFF334155);
        canvas.drawRect(6 * density, 2 * density, 10 * density, 8 * density, paint);

        // 5. 战术全息瞄具红点 (Holographic Sight Optic)
        paint.setColor(0xFF0284C7);
        canvas.drawRoundRect(new RectF(5 * density, -6.5f * density, 11 * density, -4.5f * density), 1 * density, 1 * density, paint);
        paint.setColor(0xFFEF4444);
        canvas.drawCircle(8 * density, -5.5f * density, 1.2f * density, paint);
    }

    private void drawMuzzleFlash(Canvas canvas, float density) {
        paint.setStyle(Paint.Style.FILL);
        float tipX = 24 * density;
        float s = 9 * density;

        // 橙红外爆破星
        paint.setColor(0xFFF59E0B);
        tempPath.reset();
        tempPath.moveTo(tipX, -s * 0.6f);
        tempPath.lineTo(tipX + s * 1.2f, 0);
        tempPath.lineTo(tipX, s * 0.6f);
        tempPath.lineTo(tipX - s * 0.4f, 0);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 核心亮白火焰
        paint.setColor(0xFFFFFBEB);
        canvas.drawCircle(tipX + s * 0.35f, 0, s * 0.38f, paint);
    }

    // =========================================================================
    // 4. 8大敌寇异变体全方位立体建模 (8 Distinct Enemy Vector Models)
    // =========================================================================

    public void drawEnemy(Canvas canvas, Enemy enemy, long animTick, float density) {
        float cx = enemy.x * density;
        float cy = enemy.y * density;
        float r = enemy.radiusPx * density;
        String id = (enemy.kind != null) ? enemy.kind.id() : "";

        canvas.save();
        canvas.translate(cx, cy);

        // 4.1 接触软阴影
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x40000000);
        canvas.drawOval(new RectF(-r * 0.95f, r * 0.6f, r * 0.95f, r * 1.15f), paint);

        // 4.2 依据敌人种类派发专属立体模型
        switch (id) {
            case "swarm":
                drawSwarmBug(canvas, enemy, r, animTick, density);
                break;
            case "grunt":
                drawGruntCyclops(canvas, enemy, r, animTick, density);
                break;
            case "runner":
                drawRunnerRaptor(canvas, enemy, r, animTick, density);
                break;
            case "zigzag":
                drawZigzagMoth(canvas, enemy, r, animTick, density);
                break;
            case "splitter":
                drawSplitterBlob(canvas, enemy, r, animTick, density);
                break;
            case "minion":
                drawMinionSlime(canvas, enemy, r, animTick, density);
                break;
            case "tank":
                drawTankBehemoth(canvas, enemy, r, animTick, density);
                break;
            case "boss":
                drawBossHiveLord(canvas, enemy, r, animTick, density);
                break;
            default:
                drawGenericEnemy(canvas, enemy, r, density);
                break;
        }

        // 4.3 敌人生命槽（受创时展示）
        if (enemy.hp < enemy.maxHp) {
            drawEnemyHealthBar(canvas, enemy, r, density);
        }

        canvas.restore();
    }

    /** 1. 小虫 (Swarm): 尖刺甲壳 + 震动双翅 + 猩红复眼 */
    private void drawSwarmBug(Canvas canvas, Enemy enemy, float r, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF97316);
        canvas.drawCircle(0, 0, r, paint);

        // 震动翅膀
        paint.setColor(0x88E2E8F0);
        float wingW = (float) Math.sin(animTick * 0.8) * r * 0.9f;
        canvas.drawOval(new RectF(-r * 1.3f, -r * 0.6f, -r * 0.3f + wingW, r * 0.2f), paint);
        canvas.drawOval(new RectF(r * 0.3f - wingW, -r * 0.6f, r * 1.3f, r * 0.2f), paint);

        // 猩红双复眼
        paint.setColor(0xFFEF4444);
        canvas.drawCircle(-r * 0.35f, -r * 0.2f, r * 0.25f, paint);
        canvas.drawCircle(r * 0.35f, -r * 0.2f, r * 0.25f, paint);
    }

    /** 2. 杂兵 (Grunt): 独角兽头 + 巨大独眼追踪 */
    private void drawGruntCyclops(Canvas canvas, Enemy enemy, float r, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFDC2626);
        canvas.drawCircle(0, 0, r, paint);

        // 锋利犄角
        paint.setColor(0xFF78350F);
        hornPath.reset();
        hornPath.moveTo(-r * 0.35f, -r * 0.8f);
        hornPath.lineTo(0, -r * 1.45f);
        hornPath.lineTo(r * 0.35f, -r * 0.8f);
        hornPath.close();
        canvas.drawPath(hornPath, paint);

        // 巨型独眼
        paint.setColor(Color.WHITE);
        canvas.drawCircle(0, -r * 0.15f, r * 0.45f, paint);
        paint.setColor(0xFF1E293B);
        canvas.drawCircle(0, -r * 0.15f, r * 0.24f, paint);
        paint.setColor(0xFFF87171);
        canvas.drawCircle(-r * 0.08f, -r * 0.22f, r * 0.08f, paint);
    }

    /** 3. 疾奔者 (Runner): 破风前倾 + 尖锐双刺 + 闪电红眼 */
    private void drawRunnerRaptor(Canvas canvas, Enemy enemy, float r, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE11D48);

        // 前倾菱形兽头
        tempPath.reset();
        tempPath.moveTo(0, -r * 1.25f);
        tempPath.lineTo(r, 0);
        tempPath.lineTo(0, r * 1.15f);
        tempPath.lineTo(-r, 0);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 荧光双目
        paint.setColor(0xFFFDE047);
        canvas.drawCircle(-r * 0.35f, -r * 0.25f, r * 0.18f, paint);
        canvas.drawCircle(r * 0.35f, -r * 0.25f, r * 0.18f, paint);
    }

    /** 4. 飘忽蛾 (Flutterer): 华丽蝶翼扑动 + 幽紫粉尘 */
    private void drawZigzagMoth(Canvas canvas, Enemy enemy, float r, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);

        // 扑动翅膀
        float flap = (float) Math.cos(animTick * 0.28) * r * 0.8f;
        paint.setColor(0xFF9333EA);
        canvas.drawOval(new RectF(-r * 1.4f, -r * 0.8f, -r * 0.2f + flap, r * 0.6f), paint);
        canvas.drawOval(new RectF(r * 0.2f - flap, -r * 0.8f, r * 1.4f, r * 0.6f), paint);

        // 飞蛾体躯
        paint.setColor(0xFF581C87);
        canvas.drawOval(new RectF(-r * 0.45f, -r * 0.9f, r * 0.45f, r * 0.9f), paint);

        // 发光触须与眼
        paint.setColor(0xFFC084FC);
        canvas.drawCircle(-r * 0.2f, -r * 0.5f, r * 0.15f, paint);
        canvas.drawCircle(r * 0.2f, -r * 0.5f, r * 0.15f, paint);
    }

    /** 5. 胞裂兽 (Splitter): 液态半透原浆 + 细胞核浮动 */
    private void drawSplitterBlob(Canvas canvas, Enemy enemy, float r, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF059669);
        canvas.drawCircle(0, 0, r, paint);

        // 内部漂浮的双生细胞核 (Twin Nuclei)
        float swirl = animTick * 0.12f;
        float n1x = (float) Math.cos(swirl) * (r * 0.35f);
        float n1y = (float) Math.sin(swirl) * (r * 0.35f);
        paint.setColor(0xFF10B981);
        canvas.drawCircle(n1x, n1y, r * 0.28f, paint);
        canvas.drawCircle(-n1x, -n1y, r * 0.28f, paint);

        // 胞质高光
        paint.setColor(0x66FFFFFF);
        canvas.drawCircle(-r * 0.35f, -r * 0.35f, r * 0.22f, paint);
    }

    /** 6. 裂子 (Minion): 呆萌弹跳幼体 */
    private void drawMinionSlime(Canvas canvas, Enemy enemy, float r, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF10B981);
        canvas.drawCircle(0, 0, r, paint);

        // 水灵大眼睛
        paint.setColor(Color.WHITE);
        canvas.drawCircle(-r * 0.3f, -r * 0.15f, r * 0.32f, paint);
        canvas.drawCircle(r * 0.3f, -r * 0.15f, r * 0.32f, paint);
        paint.setColor(0xFF064E3B);
        canvas.drawCircle(-r * 0.3f, -r * 0.15f, r * 0.16f, paint);
        canvas.drawCircle(r * 0.3f, -r * 0.15f, r * 0.16f, paint);
    }

    /** 7. 厚甲兽 (Tank): 重铠龟甲 + 钢铁獠牙 */
    private void drawTankBehemoth(Canvas canvas, Enemy enemy, float r, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        // 铁甲基底
        paint.setColor(0xFF334155);
        canvas.drawCircle(0, 0, r, paint);

        // 重盾装甲片 (Armor Plating)
        paint.setColor(0xFF1E293B);
        canvas.drawCircle(0, 0, r * 0.75f, paint);

        // 巨型骨质獠牙
        paint.setColor(0xFFE2E8F0);
        tempPath.reset();
        tempPath.moveTo(-r * 0.65f, r * 0.2f);
        tempPath.lineTo(-r * 0.95f, r * 0.9f);
        tempPath.lineTo(-r * 0.35f, r * 0.5f);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        tempPath.reset();
        tempPath.moveTo(r * 0.65f, r * 0.2f);
        tempPath.lineTo(r * 0.95f, r * 0.9f);
        tempPath.lineTo(r * 0.35f, r * 0.5f);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 危险红目窄缝 (Visor Slit)
        paint.setColor(0xFFEF4444);
        canvas.drawRoundRect(new RectF(-r * 0.45f, -r * 0.25f, r * 0.45f, -r * 0.08f), 2 * density, 2 * density, paint);
    }

    /** 8. 巢穴主宰 (Hive Lord Boss): 荆棘骨冠 + 五眼炼狱 + 搏动熔岩核心 */
    private void drawBossHiveLord(Canvas canvas, Enemy enemy, float r, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);

        // 凶煞暗红波纹光环
        float auraPulse = (float) (0.8 + 0.2 * Math.sin(animTick * 0.15));
        paint.setColor(0x33EF4444);
        canvas.drawCircle(0, 0, r * 1.35f * auraPulse, paint);

        // 荆棘骨刺皇冠 (Crown of Thorns)
        paint.setColor(0xFF450A0A);
        for (int i = 0; i < 7; i++) {
            double a = Math.toRadians(-120 + i * 40);
            float hx = (float) Math.cos(a) * (r * 1.35f);
            float hy = (float) Math.sin(a) * (r * 1.35f);
            canvas.drawCircle(hx, hy, r * 0.22f, paint);
        }

        // 主宰本体巨球
        paint.setColor(0xFF7F1D1D);
        canvas.drawCircle(0, 0, r, paint);

        // 搏动熔岩内芯 (Pulsating Magma Core)
        float corePulse = (float) (0.9 + 0.1 * Math.sin(animTick * 0.25));
        paint.setColor(0xFFF97316);
        canvas.drawCircle(0, 0, r * 0.5f * corePulse, paint);
        paint.setColor(0xFFFEF08A);
        canvas.drawCircle(0, 0, r * 0.25f * corePulse, paint);

        // 环绕的五枚邪神之眼
        paint.setColor(0xFFEF4444);
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(i * 72 + animTick * 1.5);
            float ex = (float) Math.cos(a) * (r * 0.72f);
            float ey = (float) Math.sin(a) * (r * 0.72f);
            canvas.drawCircle(ex, ey, r * 0.12f, paint);
        }
    }

    private void drawGenericEnemy(Canvas canvas, Enemy enemy, float r, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(enemy.kind == null ? 0xFFFF5722 : enemy.kind.color());
        canvas.drawCircle(0, 0, r, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(-r * 0.35f, -r * 0.2f, r * 0.22f, paint);
        canvas.drawCircle(r * 0.35f, -r * 0.2f, r * 0.22f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(-r * 0.28f, -r * 0.2f, r * 0.1f, paint);
        canvas.drawCircle(r * 0.42f, -r * 0.2f, r * 0.1f, paint);
    }

    private void drawEnemyHealthBar(Canvas canvas, Enemy enemy, float r, float density) {
        float width = r * 2.2f;
        float height = 3.5f * density;
        float top = -r - 10 * density;
        float ratio = enemy.hpRatio();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xAA0F172A);
        canvas.drawRoundRect(new RectF(-width / 2f, top, width / 2f, top + height), 2 * density, 2 * density, paint);

        paint.setColor(0xFFEF4444);
        canvas.drawRoundRect(new RectF(-width / 2f, top, -width / 2f + width * ratio, top + height), 2 * density, 2 * density, paint);
    }

    // =========================================================================
    // 5. 《蛋壳特工队》专属特战实体与武器视觉渲染 (Survivor.io Combat Visuals)
    // =========================================================================

    /** 绘制守卫者/捍卫者高速旋转飞盘 */
    public void drawGuardianBlades(Canvas canvas, float px, float py, float orbitRadius,
                                  int bladeCount, boolean isEvo, long animTick, float density) {
        float speed = isEvo ? 0.12f : 0.08f;
        float baseAngle = animTick * speed;
        float bladeR = (isEvo ? 13f : 10f) * density;
        int bladeColor = isEvo ? 0xFFF59E0B : 0xFF0284C7;
        int glowColor = isEvo ? 0x66F59E0B : 0x4438BDF8;

        // 旋转流光轨迹光环
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth((isEvo ? 4f : 2f) * density);
        strokePaint.setColor(glowColor);
        canvas.drawCircle(px, py, orbitRadius, strokePaint);

        paint.setStyle(Paint.Style.FILL);
        double angleStep = (Math.PI * 2.0) / Math.max(1, bladeCount);

        for (int i = 0; i < bladeCount; i++) {
            double angle = baseAngle + i * angleStep;
            float bx = px + (float) Math.cos(angle) * orbitRadius;
            float by = py + (float) Math.sin(angle) * orbitRadius;

            canvas.save();
            canvas.translate(bx, by);
            canvas.rotate((float) Math.toDegrees(angle * 3.5)); // 飞盘自身极速自转

            // 飞盘外层能量光晕
            paint.setColor(glowColor);
            canvas.drawCircle(0, 0, bladeR * 1.4f, paint);

            // 飞盘金属旋刃
            paint.setColor(bladeColor);
            for (int b = 0; b < 4; b++) {
                canvas.rotate(90);
                tempPath.reset();
                tempPath.moveTo(-bladeR * 0.35f, -bladeR * 0.2f);
                tempPath.lineTo(0, -bladeR * 1.15f);
                tempPath.lineTo(bladeR * 0.35f, -bladeR * 0.2f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
            }

            // 飞盘中心高光核
            paint.setColor(Color.WHITE);
            canvas.drawCircle(0, 0, bladeR * 0.42f, paint);

            canvas.restore();
        }
    }

    /** 绘制力场发生器 / 超武压制领域高能光环 */
    public void drawForcefield(Canvas canvas, float px, float py, float fieldRadius,
                              boolean isEvo, long animTick, float density) {
        float pulse = (float) (0.95 + 0.05 * Math.sin(animTick * 0.15));
        float r = fieldRadius * pulse;

        paint.setStyle(Paint.Style.FILL);
        if (isEvo) {
            // 超武【压制领域】：深紫-暗红高能电浆领域，电弧粒子扩散
            RadialGradient evoField = new RadialGradient(px, py, r,
                    new int[]{0x157E22CE, 0x33A855F7, 0x66C084FC, 0x00000000},
                    new float[]{0f, 0.55f, 0.88f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(evoField);
            canvas.drawCircle(px, py, r, paint);
            paint.setShader(null);

            // 边缘电弧跳跃线条
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2.5f * density);
            strokePaint.setColor(0x88C084FC);
            canvas.drawCircle(px, py, r * 0.92f, strokePaint);

            // 环形跳跃电弧粒子
            paint.setColor(0xFFF3E8FF);
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(i * 60 + animTick * 4);
                float arcX = px + (float) Math.cos(a) * (r * 0.92f);
                float arcY = py + (float) Math.sin(a) * (r * 0.92f);
                canvas.drawCircle(arcX, arcY, 2.5f * density, paint);
            }
        } else {
            // 普通【力场发生器】：青蓝等离子防护光球
            RadialGradient basicField = new RadialGradient(px, py, r,
                    new int[]{0x100284C7, 0x2438BDF8, 0x4438BDF8, 0x00000000},
                    new float[]{0f, 0.6f, 0.85f, 1f}, Shader.TileMode.CLAMP);
            paint.setShader(basicField);
            canvas.drawCircle(px, py, r, paint);
            paint.setShader(null);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(1.8f * density);
            strokePaint.setColor(0x5538BDF8);
            canvas.drawCircle(px, py, r * 0.94f, strokePaint);
        }
    }

    /** 绘制战术无人机伴飞（A机、B机、超武毁灭者母机） */
    public void drawDrones(Canvas canvas, float px, float py, boolean hasA, boolean hasB,
                          boolean isDestroyer, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);

        if (isDestroyer) {
            // 超武【毁灭者】：合体黑红双核重型母机，悬浮特工正上方
            float floatY = (float) Math.sin(animTick * 0.18) * 4 * density;
            float dx = px;
            float dy = py - 46 * density + floatY;

            canvas.save();
            canvas.translate(dx, dy);

            // 推进器尾焰
            paint.setColor(0xFFEF4444);
            canvas.drawCircle(-8 * density, 8 * density, 4 * density, paint);
            canvas.drawCircle(8 * density, 8 * density, 4 * density, paint);
            paint.setColor(0xFFFEF08A);
            canvas.drawCircle(-8 * density, 7 * density, 2 * density, paint);
            canvas.drawCircle(8 * density, 7 * density, 2 * density, paint);

            // 母机黑钛合金机体
            paint.setColor(0xFF0F172A);
            canvas.drawRoundRect(new RectF(-16 * density, -8 * density, 16 * density, 8 * density), 4 * density, 4 * density, paint);

            // 能量核心红芒
            paint.setColor(0xFFDC2626);
            canvas.drawCircle(0, 0, 4.5f * density, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(0, 0, 2f * density, paint);

            // 双旋翼高速旋转
            paint.setColor(0x88CBD5E1);
            float rotorSpin = (animTick * 1.2f) % (float) Math.PI;
            canvas.drawLine(-16 * density - 6 * density, -8 * density, -16 * density + 6 * density, -8 * density, paint);
            canvas.drawLine(16 * density - 6 * density, -8 * density, 16 * density + 6 * density, -8 * density, paint);

            canvas.restore();
            return;
        }

        // 普通 A 机 (左上伴飞，白银机体，机枪机)
        if (hasA) {
            float floatY = (float) Math.sin(animTick * 0.16) * 3 * density;
            float dx = px - 32 * density;
            float dy = py - 30 * density + floatY;

            canvas.save();
            canvas.translate(dx, dy);

            paint.setColor(0xFFE2E8F0);
            canvas.drawRoundRect(new RectF(-10 * density, -5 * density, 10 * density, 5 * density), 3 * density, 3 * density, paint);
            paint.setColor(0xFFEF4444); // 红色雷达指示灯
            canvas.drawCircle(4 * density, 0, 2f * density, paint);
            // 机枪枪管
            paint.setColor(0xFF334155);
            canvas.drawRect(8 * density, -1.5f * density, 13 * density, 1.5f * density, paint);

            canvas.restore();
        }

        // 普通 B 机 (右上伴飞，黑曜机体，导弹机)
        if (hasB) {
            float floatY = (float) Math.cos(animTick * 0.16) * 3 * density;
            float dx = px + 32 * density;
            float dy = py - 30 * density + floatY;

            canvas.save();
            canvas.translate(dx, dy);

            paint.setColor(0xFF1E293B);
            canvas.drawRoundRect(new RectF(-10 * density, -5 * density, 10 * density, 5 * density), 3 * density, 3 * density, paint);
            paint.setColor(0xFF38BDF8); // 蓝色雷达指示灯
            canvas.drawCircle(-4 * density, 0, 2f * density, paint);
            // 微型导弹挂架
            paint.setColor(0xFF94A3B8);
            canvas.drawRect(-8 * density, 4 * density, -2 * density, 7 * density, paint);
            canvas.drawRect(2 * density, 4 * density, 8 * density, 7 * density, paint);

            canvas.restore();
        }
    }

    /** 绘制《蛋壳特工队》标志性全屏 WARNING 警报横幅 */
    public void drawWarningBanner(Canvas canvas, float width, float height, float density, long animTick) {
        float pulse = (float) (0.5 + 0.5 * Math.sin(animTick * 0.28));
        int redAlpha = (int) (140 * pulse);

        // 全屏四周猩红警报扫掠
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(22 * density);
        strokePaint.setColor(Color.argb(redAlpha, 220, 38, 38));
        canvas.drawRect(0, 0, width, height, strokePaint);

        // 屏幕中段黑黄斜斑马纹横幅
        float bannerH = 56 * density;
        float bannerY = height * 0.38f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xEE0F172A);
        canvas.drawRect(0, bannerY, width, bannerY + bannerH, paint);

        // 横幅上下黄色警戒线
        paint.setColor(0xFFF59E0B);
        canvas.drawRect(0, bannerY, width, bannerY + 3 * density, paint);
        canvas.drawRect(0, bannerY + bannerH - 3 * density, width, bannerY + bannerH, paint);

        // 居中大字：⚠️ WARNING!! ⚠️
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(26 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setColor(0xFFEF4444);
        canvas.drawText("⚠️ WARNING!! ⚠️", width / 2f, bannerY + 30 * density, paint);

        // 副标题警报提示
        paint.setTextSize(12.5f * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFFFDE047);
        canvas.drawText("高危生化母体迫近 · 特工请全力迎敌", width / 2f, bannerY + 48 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 绘制 Boss 冲撞矩形预警带 */
    public void drawBossTelegraph(Canvas canvas, float startX, float startY, float endX, float endY,
                                 float bandWidth, float density, float progress) {
        double angle = Math.atan2(endY - startY, endX - startX);
        float dist = (float) Math.hypot(endX - startX, endY - startY);

        canvas.save();
        canvas.translate(startX * density, startY * density);
        canvas.rotate((float) Math.toDegrees(angle));

        // 红色半透明警示带
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33EF4444);
        canvas.drawRect(0, -bandWidth / 2f * density, dist * density, bandWidth / 2f * density, paint);

        // 预警蓄力前进扫掠进度条
        paint.setColor(0x66DC2626);
        canvas.drawRect(0, -bandWidth / 2f * density, dist * density * progress, bandWidth / 2f * density, paint);

        // 红色边缘虚线
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.5f * density);
        strokePaint.setColor(0xAAEF4444);
        canvas.drawRect(0, -bandWidth / 2f * density, dist * density, bandWidth / 2f * density, strokePaint);

        canvas.restore();
    }

    /**
     * 绘制《蛋壳特工队》顶栏横贯式高能绿色经验槽 (Survivor.io Top EXP Bar)
     */
    public void drawTopExpBar(Canvas canvas, float width, float density, int curExp, int maxExp,
                             int level, int score, int wave, int totalWaves) {
        float barH = 10 * density;
        float ratio = Math.min(1.0f, (float) curExp / Math.max(1, maxExp));

        // 经验条深色基底
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0F172A);
        canvas.drawRect(0, 0, width, barH, paint);

        // 充盈的翡翠亮绿经验条
        paint.setColor(0xFF10B981);
        canvas.drawRect(0, 0, width * ratio, barH, paint);
        // 经验条上边缘高光
        paint.setColor(0xFF6EE7B7);
        canvas.drawRect(0, 0, width * ratio, 2.5f * density, paint);

        // 左上角六边形等级勋章 (LV. X)
        float badgeX = 26 * density;
        float badgeY = barH + 14 * density;
        float badgeR = 12 * density;

        // 勋章深绿底
        paint.setColor(0xFF065F46);
        canvas.drawCircle(badgeX, badgeY, badgeR, paint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2 * density);
        strokePaint.setColor(0xFF34D399);
        canvas.drawCircle(badgeX, badgeY, badgeR, strokePaint);

        // 等级文本
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(11 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("LV." + level, badgeX, badgeY + 4 * density, paint);

        // 顶部 HUD 战况信息
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(14 * density);
        paint.setColor(0xFFF1F5F9);
        canvas.drawText("战功 " + score, 48 * density, badgeY + 4.5f * density, paint);

        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(0xFF38BDF8);
        canvas.drawText("波次 " + wave + "/" + totalWaves, width - 14 * density, badgeY + 4.5f * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * 绘制《蛋壳特工队》三选一战术科技卡片 (Level Up Card Selection)
     */
    public void drawLevelUpDialog(Canvas canvas, float width, float height, float density,
                                 java.util.List<SurvivorSkill.Type> choices,
                                 SurvivorSkill.SkillInventory inventory, RectF[] cardRects) {
        if (choices == null || choices.isEmpty()) return;

        // 深暗半透明背景遮罩
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDD090D16);
        canvas.drawRect(0, 0, width, height, paint);

        // 顶部标题：⚡ LEVEL UP! ⚡
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xFFFBBF24);
        paint.setTextSize(26 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("⚡ LEVEL UP! ⚡", width / 2f, height * 0.16f, paint);

        paint.setTextSize(13 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("特工战术科技进化 · 选取一项核心专精", width / 2f, height * 0.16f + 24 * density, paint);

        for (int i = 0; i < choices.size() && i < cardRects.length; i++) {
            SurvivorSkill.Type skill = choices.get(i);
            RectF rect = cardRects[i];
            drawSurvivorCard(canvas, skill, inventory, rect, density);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSurvivorCard(Canvas canvas, SurvivorSkill.Type skill,
                                  SurvivorSkill.SkillInventory inventory, RectF rect, float density) {
        boolean canEvo = inventory.canEvo(skill);
        int curLevel = inventory.getLevel(skill);

        // 卡片底板
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(canEvo ? 0xFF2A1C0A : 0xFF1E293B);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, paint);

        // 卡片发光边框（超武金色流光，普通青蓝）
        int borderColor = canEvo ? 0xFFF59E0B : 0xFF38BDF8;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth((canEvo ? 3.5f : 2f) * density);
        strokePaint.setColor(borderColor);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, strokePaint);
        paint.setStyle(Paint.Style.FILL);

        // 超武「EVO 进化」标签
        if (canEvo) {
            paint.setColor(0xFFF59E0B);
            canvas.drawRoundRect(new RectF(rect.centerX() - 44 * density, rect.top + 8 * density,
                    rect.centerX() + 44 * density, rect.top + 24 * density), 6 * density, 6 * density, paint);
            paint.setColor(0xFF78350F);
            paint.setTextSize(11 * density);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("⭐ EVO 超武进化", rect.centerX(), rect.top + 20 * density, paint);
        }

        // 图标徽章圆底
        float iconY = canEvo ? (rect.top + 46 * density) : (rect.top + 38 * density);
        paint.setColor(canEvo ? 0x4DF59E0B : 0x22FFFFFF);
        canvas.drawCircle(rect.centerX(), iconY, 20 * density, paint);

        // 技能 Emoji 图标
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(22 * density);
        String icon = canEvo ? skill.evoIcon : skill.icon;
        canvas.drawText(icon, rect.centerX(), iconY + 8 * density, paint);

        // 技能名称
        paint.setColor(canEvo ? 0xFFFDE047 : Color.WHITE);
        paint.setTextSize(16 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        String name = canEvo ? skill.evoName : skill.nameCn;
        canvas.drawText(name, rect.centerX(), iconY + 40 * density, paint);

        // 星级展示（⭐⭐⭐⭐⭐）
        if (!canEvo) {
            drawStarSlots(canvas, rect.centerX(), iconY + 54 * density, curLevel + 1, density);
        }

        // 描述文案
        paint.setTextSize(12 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFFCBD5E1);
        String desc = canEvo ? skill.evoDesc : skill.descCn;
        drawWrappedText(canvas, desc, rect.centerX(), iconY + 70 * density, rect.width() - 18 * density, 16 * density);

        // EVO 配方契约提示
        if (skill.category == SurvivorSkill.Category.ACTIVE_WEAPON && skill.isEvoCapable()) {
            SurvivorSkill.Type req = SurvivorSkill.getRequiredPair(skill);
            if (req != null) {
                boolean hasReq = inventory.getLevel(req) > 0;
                paint.setColor(hasReq ? 0xFF34D399 : 0xFF64748B);
                paint.setTextSize(10.5f * density);
                canvas.drawText("配方: " + req.nameCn + " ➔ " + skill.evoName + (hasReq ? " (已达成)" : ""),
                        rect.centerX(), rect.bottom - 12 * density, paint);
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawStarSlots(Canvas canvas, float cx, float y, int filledStars, float density) {
        float spacing = 12 * density;
        float startX = cx - spacing * 2f;
        paint.setTextSize(11 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 1; i <= 5; i++) {
            paint.setColor(i <= filledStars ? 0xFFF59E0B : 0xFF475569);
            canvas.drawText("★", startX + (i - 1) * spacing, y, paint);
        }
    }

    private void drawWrappedText(Canvas canvas, String text, float centerX, float baseline,
                                 float maxWidth, float lineHeight) {
        if (text == null) return;
        float y = baseline;
        int cursor = 0;
        while (cursor < text.length()) {
            int count = paint.breakText(text, cursor, text.length(), true, maxWidth, null);
            if (count <= 0) return;
            canvas.drawText(text, cursor, cursor + count, centerX, y, paint);
            cursor += count;
            y += lineHeight;
        }
    }

    /**
     * 绘制《蛋壳特工队》三叶草幸运宝箱老虎机转盘弹窗 (Lucky Chest Lottery Dialog)
     */
    public void drawLuckyChestDialog(Canvas canvas, float width, float height, float density,
                                    int rolls, java.util.List<SurvivorSkill.Type> rewards,
                                    float spinProgress) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDD090D16);
        canvas.drawRect(0, 0, width, height, paint);

        // 金光放射光晕
        RadialGradient chestGlow = new RadialGradient(width / 2f, height * 0.42f, 180 * density,
                new int[]{0x55F59E0B, 0x18F59E0B, 0x00000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(chestGlow);
        canvas.drawCircle(width / 2f, height * 0.42f, 180 * density, paint);
        paint.setShader(null);

        // 标题
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xFFFBBF24);
        paint.setTextSize(28 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("🍀 幸运神装宝箱 🍀", width / 2f, height * 0.22f, paint);

        paint.setTextSize(14 * density);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setColor(0xFFE2E8F0);
        canvas.drawText("触发 " + rolls + " 连大满贯抽奖 · 点击收下战地神装！", width / 2f, height * 0.22f + 26 * density, paint);

        // 奖励展示槽位
        if (rewards != null && !rewards.isEmpty()) {
            float cardW = Math.min(100 * density, (width - 40 * density) / rewards.size());
            float cardH = 120 * density;
            float totalW = cardW * rewards.size() + (rewards.size() - 1) * 10 * density;
            float startX = (width - totalW) / 2f;
            float topY = height * 0.40f;

            for (int i = 0; i < rewards.size(); i++) {
                SurvivorSkill.Type t = rewards.get(i);
                float x = startX + i * (cardW + 10 * density);
                RectF r = new RectF(x, topY, x + cardW, topY + cardH);

                paint.setColor(0xFF1E293B);
                canvas.drawRoundRect(r, 10 * density, 10 * density, paint);
                strokePaint.setColor(0xFFF59E0B);
                strokePaint.setStrokeWidth(2 * density);
                canvas.drawRoundRect(r, 10 * density, 10 * density, strokePaint);

                paint.setTextSize(28 * density);
                canvas.drawText(t.icon, r.centerX(), r.top + 45 * density, paint);

                paint.setColor(Color.WHITE);
                paint.setTextSize(13 * density);
                paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText(t.nameCn, r.centerX(), r.top + 76 * density, paint);

                paint.setColor(0xFF34D399);
                paint.setTextSize(11 * density);
                paint.setTypeface(android.graphics.Typeface.DEFAULT);
                canvas.drawText("+1 阶已入库", r.centerX(), r.top + 98 * density, paint);
            }
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // =========================================================================
    // 6. 颜色辅助函数 (Color Shading Utilities)
    // =========================================================================

    private static int lightenColor(int color, float fraction) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        r = Math.min(255, (int) (r + (255 - r) * fraction));
        g = Math.min(255, (int) (g + (255 - g) * fraction));
        b = Math.min(255, (int) (b + (255 - b) * fraction));
        return Color.rgb(r, g, b);
    }

    private static int darkenColor(int color, float fraction) {
        int r = Math.max(0, (int) (Color.red(color) * (1f - fraction)));
        int g = Math.max(0, (int) (Color.green(color) * (1f - fraction)));
        int b = Math.max(0, (int) (Color.blue(color) * (1f - fraction)));
        return Color.rgb(r, g, b);
    }
}
