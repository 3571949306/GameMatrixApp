package com.gamecenter.app.brotato;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.gamecenter.app.brotato.engine.BrotatoArena;
import com.gamecenter.app.brotato.engine.Enemy;
import com.gamecenter.app.brotato.engine.Projectile;
import com.gamecenter.app.brotato.engine.Upgrade;

import java.util.List;

/**
 * 《蛋壳特工队》（Survivor.io）全方位视觉渲染引擎
 * 负责渲染废土现代都市战场、特工战队Q版精模（科萌/金/月咏/沃尔默/凯蒂斯/斯巴达）、
 * 8大生化异变体敌寇（丧尸/毒蛛/狂犬/幻蛾/史莱姆/泰坦屠夫/深渊Boss）、
 * 真实武器实体弹道（苦无/幽灵手里剑/守卫者锯齿飞盘/悬浮无人机/等离子力场蜂窝护盾）、
 * 以及顶栏荧光经验条、战术升级选卡与幸运宝箱UI。
 */
public class BrotatoRenderer {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tempPath = new Path();
    private final Path scarfPath = new Path();
    private final RectF tempRect = new RectF();

    public BrotatoRenderer() {
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    // =========================================================================
    // 1. 废土现代都市公路战场建模 (Wasteland Urban Highway Terrain)
    // =========================================================================

    public void drawArenaGround(Canvas canvas, float width, float height, float density,
                                float playerX, float playerY, long animTick) {
        // 1.1 深灰战术沥青路面底色
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(0xFF141820);
        canvas.drawRect(0, 0, width, height, paint);

        // 1.2 沥青颗粒质感与路面微弱网格
        paint.setColor(0xFF1A202C);
        float gridSize = 48 * density;
        for (float x = 0; x < width; x += gridSize) {
            canvas.drawRect(x, 0, x + 1.5f * density, height, paint);
        }
        for (float y = 0; y < height; y += gridSize) {
            canvas.drawRect(0, y, width, y + 1.5f * density, paint);
        }

        // 1.3 人行横道斑马线（顶部与底部战区，模拟真实十字路口）
        drawZebraCrossing(canvas, width, 18 * density, 34 * density, density);
        drawZebraCrossing(canvas, width, height - 52 * density, 34 * density, density);

        // 1.4 双黄中心分道实线与反光道钉（路中核心主轴）
        float midY = height / 2f;
        paint.setColor(0xFFEAB308); // 警示金黄
        float yellowLineWidth = 3.2f * density;
        float yellowGap = 4.5f * density;
        canvas.drawRect(0, midY - yellowGap - yellowLineWidth, width, midY - yellowGap, paint);
        canvas.drawRect(0, midY + yellowGap, width, midY + yellowGap + yellowLineWidth, paint);

        // 中心反光道钉 (Cat's Eyes)
        paint.setColor(0xFFFEF08A);
        float studSpacing = 55 * density;
        for (float sx = 20 * density; sx < width; sx += studSpacing) {
            canvas.drawCircle(sx, midY, 2.2f * density, paint);
        }

        // 1.5 白色虚线分车道
        paint.setColor(0x80CBD5E1);
        float laneQuarterY1 = midY - (height * 0.24f);
        float laneQuarterY2 = midY + (height * 0.24f);
        float dashW = 28 * density;
        float dashGap = 36 * density;
        for (float dx = 12 * density; dx < width; dx += (dashW + dashGap)) {
            canvas.drawRoundRect(new RectF(dx, laneQuarterY1 - 1.2f * density,
                    dx + dashW, laneQuarterY1 + 1.2f * density), 1.2f * density, 1.2f * density, paint);
            canvas.drawRoundRect(new RectF(dx, laneQuarterY2 - 1.2f * density,
                    dx + dashW, laneQuarterY2 + 1.2f * density), 1.2f * density, 1.2f * density, paint);
        }

        // 1.6 下水道铸铁圆形井盖 (Sewer Manhole Covers)
        drawManhole(canvas, width * 0.22f, height * 0.32f, 22 * density, density);
        drawManhole(canvas, width * 0.78f, height * 0.68f, 22 * density, density);
        drawManhole(canvas, width * 0.78f, height * 0.32f, 20 * density, density);
        drawManhole(canvas, width * 0.22f, height * 0.68f, 20 * density, density);

        // 1.7 边缘45°黑黄相间工业防撞警示带 (Hazard Stripes)
        drawHazardStrip(canvas, 0, 0, width, 10 * density, density, false);
        drawHazardStrip(canvas, 0, height - 10 * density, width, 10 * density, density, true);

        // 1.8 特工中心战术聚光灯与环境暗角 (Tactical Ambient Spotlight)
        float lightPx = playerX * density;
        float lightPy = playerY * density;
        float spotRadius = Math.max(width, height) * 0.65f;
        RadialGradient vignette = new RadialGradient(
                lightPx, lightPy, spotRadius,
                new int[]{0x00000000, 0x33000000, 0x88050910, 0xD0030508},
                new float[]{0.0f, 0.45f, 0.78f, 1.0f},
                Shader.TileMode.CLAMP
        );
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(vignette);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
    }

    private void drawZebraCrossing(Canvas canvas, float width, float top, float h, float density) {
        paint.setColor(0x4094A3B8);
        float stripeW = 16 * density;
        float gap = 12 * density;
        for (float x = 8 * density; x < width; x += (stripeW + gap)) {
            canvas.drawRect(x, top, Math.min(x + stripeW, width - 8 * density), top + h, paint);
        }
    }

    private void drawManhole(Canvas canvas, float cx, float cy, float r, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1E293B);
        canvas.drawCircle(cx, cy, r, paint);

        strokePaint.setColor(0xFF334155);
        strokePaint.setStrokeWidth(2.5f * density);
        canvas.drawCircle(cx, cy, r - 2f * density, strokePaint);

        paint.setColor(0xFF0F172A);
        canvas.drawCircle(cx, cy, r * 0.62f, paint);

        // 铸铁井盖十字与通风槽
        strokePaint.setColor(0xFF475569);
        strokePaint.setStrokeWidth(1.8f * density);
        canvas.drawLine(cx - r * 0.45f, cy, cx + r * 0.45f, cy, strokePaint);
        canvas.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.45f, strokePaint);
    }

    private void drawHazardStrip(Canvas canvas, float x, float y, float w, float h,
                                 float density, boolean flip) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1E232A);
        canvas.drawRect(x, y, x + w, y + h, paint);

        paint.setColor(0xFFEAB308);
        float step = 14 * density;
        for (float startX = -h; startX < w + h; startX += step * 2) {
            tempPath.reset();
            if (!flip) {
                tempPath.moveTo(startX, y);
                tempPath.lineTo(startX + step, y);
                tempPath.lineTo(startX + step - h, y + h);
                tempPath.lineTo(startX - h, y + h);
            } else {
                tempPath.moveTo(startX, y);
                tempPath.lineTo(startX + step, y);
                tempPath.lineTo(startX + step + h, y + h);
                tempPath.lineTo(startX + h, y + h);
            }
            tempPath.close();
            canvas.drawPath(tempPath, paint);
        }
    }

    // =========================================================================
    // 2. 六大特战特工 Q版二头身高精度建模 (6 Chibi Agents Vector Models)
    // =========================================================================

    public void drawPlayer(Canvas canvas, BrotatoHero hero, float x, float y, float radius,
                           float aimAngle, boolean isMoving, long animTick, int damageFlashTicks, float density) {
        canvas.save();
        canvas.translate(x, y);

        // 2.1 视觉等比放大：将微观物理半径放大至生动Q版特工体态（身高约48-52dp）
        float s = Math.max(22f * density, radius * 2.2f);

        // 2.2 行走物理动效（双腿踏步、躯干呼吸上下轻微浮沉）
        float bob = isMoving ? (float) Math.abs(Math.sin(animTick * 0.35f)) * (2.8f * density) : 0f;
        float legSwing = isMoving ? (float) Math.sin(animTick * 0.35f) * (6f * density) : 0f;

        // 2.3 接触地面软阴影
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        canvas.drawOval(new RectF(-s * 0.85f, s * 0.65f, s * 0.85f, s * 1.05f), paint);

        // 2.4 腿部与战术作战靴 (Legs & Combat Boots)
        paint.setColor(0xFF1F2937); // 作战裤
        canvas.drawRoundRect(new RectF(-s * 0.48f, s * 0.42f - bob, -s * 0.16f, s * 0.82f - bob + legSwing),
                2 * density, 2 * density, paint);
        canvas.drawRoundRect(new RectF(s * 0.16f, s * 0.42f - bob, s * 0.48f, s * 0.82f - bob - legSwing),
                2 * density, 2 * density, paint);

        // 作战靴靴底与鞋尖
        paint.setColor(0xFF111827);
        canvas.drawRoundRect(new RectF(-s * 0.52f, s * 0.72f - bob + legSwing, -s * 0.12f, s * 0.88f - bob + legSwing),
                2 * density, 2 * density, paint);
        canvas.drawRoundRect(new RectF(s * 0.12f, s * 0.72f - bob - legSwing, s * 0.52f, s * 0.88f - bob - legSwing),
                2 * density, 2 * density, paint);

        // 2.5 躯干与特战服装 (Torso & Tactical Outfit)
        canvas.save();
        canvas.translate(0, -bob);
        drawAgentTorso(canvas, hero, s, density);

        // 2.6 科萌标志性飘逸鲜红长领巾（带物理多段波动）
        if (hero == BrotatoHero.ALL_ROUNDER || hero == null) {
            drawCommonDynamicScarf(canvas, s, aimAngle, isMoving, animTick, density);
        }

        // 2.7 头部、面部五官与特色发饰 (Head, Expressive Anime Face & Hair)
        drawAgentHead(canvas, hero, s, aimAngle, damageFlashTicks, animTick, density);

        // 2.8 手臂与主战武器持握 (Arms & Held Weapons)
        drawAgentArmsAndWeapons(canvas, hero, s, aimAngle, animTick, density);

        canvas.restore(); // 还原躯干浮沉位移
        canvas.restore(); // 还原角色中心位移
    }

    private void drawAgentTorso(Canvas canvas, BrotatoHero hero, float s, float density) {
        paint.setStyle(Paint.Style.FILL);
        RectF torsoRect = new RectF(-s * 0.52f, -s * 0.08f, s * 0.52f, s * 0.56f);

        if (hero == BrotatoHero.BRAWLER) { // 金 (King) - 暴击女王战斗夹克
            paint.setColor(0xFFDC2626);
            canvas.drawRoundRect(torsoRect, 6 * density, 6 * density, paint);
            // 战术腰带与搭扣
            paint.setColor(0xFF1E293B);
            canvas.drawRect(-s * 0.52f, s * 0.40f, s * 0.52f, s * 0.52f, paint);
            paint.setColor(0xFFFBBF24);
            canvas.drawRect(-s * 0.15f, s * 0.38f, s * 0.15f, s * 0.54f, paint);
        } else if (hero == BrotatoHero.MAGE) { // 月咏 (Tsukuyomi) - 月影剑客忍者和服
            paint.setColor(0xFF1E1B4B);
            canvas.drawRoundRect(torsoRect, 6 * density, 6 * density, paint);
            paint.setColor(0xFF818CF8);
            canvas.drawRect(-s * 0.12f, -s * 0.08f, s * 0.12f, s * 0.56f, paint);
            paint.setColor(0xFFDC2626);
            canvas.drawRect(-s * 0.52f, s * 0.38f, s * 0.52f, s * 0.50f, paint);
        } else if (hero == BrotatoHero.CHUNKY) { // 沃尔默 (Worm) - 重装教官泰坦防弹背心
            paint.setColor(0xFF334155);
            canvas.drawRoundRect(torsoRect, 6 * density, 6 * density, paint);
            // 重型凯夫拉胸甲板
            paint.setColor(0xFF1E293B);
            canvas.drawRoundRect(new RectF(-s * 0.42f, 0f, s * 0.42f, s * 0.38f), 3 * density, 3 * density, paint);
            paint.setColor(0xFF64748B);
            canvas.drawCircle(-s * 0.28f, s * 0.12f, 2 * density, paint);
            canvas.drawCircle(s * 0.28f, s * 0.12f, 2 * density, paint);
        } else if (hero == BrotatoHero.LUCKY) { // 凯蒂斯 (Catnips) - 机灵猫娘战地白袍
            paint.setColor(0xFFF1F5F9);
            canvas.drawRoundRect(torsoRect, 6 * density, 6 * density, paint);
            // 绿色四叶草胸针
            paint.setColor(0xFF10B981);
            canvas.drawCircle(-s * 0.28f, s * 0.12f, 3.5f * density, paint);
            paint.setColor(0xFFF43F5E);
            canvas.drawRect(-s * 0.52f, s * 0.38f, s * 0.52f, s * 0.46f, paint);
        } else if (hero == BrotatoHero.SPEEDSTER) { // 斯巴达 (Spartacus) - 极速游侠赛博外骨骼
            paint.setColor(0xFF0F172A);
            canvas.drawRoundRect(torsoRect, 6 * density, 6 * density, paint);
            // 荧光动力导管
            paint.setColor(0xFF06B6D4);
            canvas.drawRoundRect(new RectF(-s * 0.08f, -s * 0.05f, s * 0.08f, s * 0.54f), 1.5f * density, 1.5f * density, paint);
        } else { // 科萌 (Common) - 经典战备工装风衣
            paint.setColor(0xFF334155);
            canvas.drawRoundRect(torsoRect, 6 * density, 6 * density, paint);
            // 亮黄色工装拉链与胸前挂扣
            paint.setColor(0xFFF59E0B);
            canvas.drawRect(-s * 0.06f, -s * 0.08f, s * 0.06f, s * 0.54f, paint);
            paint.setColor(0xFF1E293B);
            canvas.drawRect(-s * 0.52f, s * 0.38f, s * 0.52f, s * 0.50f, paint);
        }
    }

    private void drawCommonDynamicScarf(Canvas canvas, float s, float aimAngle,
                                        boolean isMoving, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFDC2626); // 鲜红领巾

        // 领口环绕领结
        canvas.drawRoundRect(new RectF(-s * 0.42f, -s * 0.16f, s * 0.42f, s * 0.06f),
                4 * density, 4 * density, paint);

        // 飘逸后拂的两条长缎带（依据视线与移动方向逆向飘舞）
        float trailAngle = aimAngle + (float) Math.PI;
        float wave1 = (float) Math.sin(animTick * 0.28f) * (6f * density);
        float wave2 = (float) Math.cos(animTick * 0.24f) * (5f * density);

        float sx = -s * 0.15f;
        float sy = -s * 0.05f;
        float len1 = s * 1.05f;
        float len2 = s * 0.85f;

        float endX1 = sx + (float) Math.cos(trailAngle) * len1 + (float) Math.sin(trailAngle) * wave1;
        float endY1 = sy + (float) Math.sin(trailAngle) * len1 - (float) Math.cos(trailAngle) * wave1;

        float endX2 = sx + (float) Math.cos(trailAngle + 0.25f) * len2 + (float) Math.sin(trailAngle) * wave2;
        float endY2 = sy + (float) Math.sin(trailAngle + 0.25f) * len2 - (float) Math.cos(trailAngle) * wave2;

        scarfPath.reset();
        scarfPath.moveTo(sx - 3f * density, sy);
        scarfPath.quadTo((sx + endX1) / 2f + wave1, (sy + endY1) / 2f, endX1, endY1);
        scarfPath.lineTo(endX1 + 4f * density, endY1 + 2f * density);
        scarfPath.quadTo((sx + endX1) / 2f - wave1, (sy + endY1) / 2f, sx + 3f * density, sy);
        scarfPath.close();
        canvas.drawPath(scarfPath, paint);

        // 第二条小飘带
        paint.setColor(0xFFEF4444);
        scarfPath.reset();
        scarfPath.moveTo(sx - 2f * density, sy + 3f * density);
        scarfPath.quadTo((sx + endX2) / 2f + wave2, (sy + endY2) / 2f, endX2, endY2);
        scarfPath.lineTo(endX2 + 3f * density, endY2 + 2f * density);
        scarfPath.quadTo((sx + endX2) / 2f - wave2, (sy + endY2) / 2f, sx + 2f * density, sy + 3f * density);
        scarfPath.close();
        canvas.drawPath(scarfPath, paint);
    }

    private void drawAgentHead(Canvas canvas, BrotatoHero hero, float s, float aimAngle,
                               int damageFlashTicks, long animTick, float density) {
        float headR = s * 0.52f;
        float headCy = -s * 0.46f;

        // 1. 皮肤底色 (Chibi Peach Skin Tone)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(damageFlashTicks > 0 ? 0xFFFFCDD2 : 0xFFFFE4D6);
        canvas.drawCircle(0, headCy, headR, paint);

        // 2. 害羞红晕 (Cheek Blush)
        paint.setColor(0x44FF8A80);
        canvas.drawCircle(-headR * 0.55f, headCy + headR * 0.22f, headR * 0.22f, paint);
        canvas.drawCircle(headR * 0.55f, headCy + headR * 0.22f, headR * 0.22f, paint);

        // 3. 灵动大眼 (Expressive Anime Eyes)
        float eyeSpacing = headR * 0.42f;
        float eyeY = headCy + headR * 0.05f;
        float eyeR = headR * 0.28f;

        float lookX = (float) Math.cos(aimAngle) * (eyeR * 0.35f);
        float lookY = (float) Math.sin(aimAngle) * (eyeR * 0.25f);

        if (damageFlashTicks > 0) {
            // 受击眨眼 (> <)
            strokePaint.setColor(0xFF1E293B);
            strokePaint.setStrokeWidth(2.5f * density);
            canvas.drawLine(-eyeSpacing - eyeR * 0.6f, eyeY - eyeR * 0.4f, -eyeSpacing, eyeY, strokePaint);
            canvas.drawLine(-eyeSpacing, eyeY, -eyeSpacing - eyeR * 0.6f, eyeY + eyeR * 0.4f, strokePaint);
            canvas.drawLine(eyeSpacing + eyeR * 0.6f, eyeY - eyeR * 0.4f, eyeSpacing, eyeY, strokePaint);
            canvas.drawLine(eyeSpacing, eyeY, eyeSpacing + eyeR * 0.6f, eyeY + eyeR * 0.4f, strokePaint);
        } else {
            // 纯白眼底
            paint.setColor(Color.WHITE);
            canvas.drawCircle(-eyeSpacing, eyeY, eyeR, paint);
            canvas.drawCircle(eyeSpacing, eyeY, eyeR, paint);

            // 瞳孔与专属瞳色
            int pupilColor = 0xFF1E293B;
            if (hero == BrotatoHero.BRAWLER) pupilColor = 0xFFD97706; // 金 - 琥珀金瞳
            if (hero == BrotatoHero.MAGE) pupilColor = 0xFF7C3AED;    // 月咏 - 秘紫灵瞳
            if (hero == BrotatoHero.LUCKY) pupilColor = 0xFF059669;   // 凯蒂斯 - 翡翠绿瞳
            if (hero == BrotatoHero.SPEEDSTER) pupilColor = 0xFF0284C7;// 斯巴达 - 赛博青瞳

            paint.setColor(pupilColor);
            float pR = eyeR * 0.62f;
            canvas.drawCircle(-eyeSpacing + lookX, eyeY + lookY, pR, paint);
            canvas.drawCircle(eyeSpacing + lookX, eyeY + lookY, pR, paint);

            // 双反光灵动高光
            paint.setColor(Color.WHITE);
            canvas.drawCircle(-eyeSpacing + lookX - pR * 0.3f, eyeY + lookY - pR * 0.3f, pR * 0.35f, paint);
            canvas.drawCircle(eyeSpacing + lookX - pR * 0.3f, eyeY + lookY - pR * 0.3f, pR * 0.35f, paint);
            canvas.drawCircle(-eyeSpacing + lookX + pR * 0.25f, eyeY + lookY + pR * 0.25f, pR * 0.18f, paint);
            canvas.drawCircle(eyeSpacing + lookX + pR * 0.25f, eyeY + lookY + pR * 0.25f, pR * 0.18f, paint);
        }

        // 4. 特工特色发型与头饰 (Hero Hair & Headgear)
        drawHeroHairAndGear(canvas, hero, headCy, headR, animTick, density);
    }

    private void drawHeroHairAndGear(Canvas canvas, BrotatoHero hero, float cy, float r,
                                     long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);

        if (hero == BrotatoHero.BRAWLER) { // 金 (King) - 绚烂金发双马尾
            paint.setColor(0xFFFBBF24); // 闪亮金发
            // 前额刘海
            canvas.drawCircle(0, cy - r * 0.32f, r * 0.95f, paint);
            // 左右双马尾（带跑动摇曳）
            float pigtailSwing = (float) Math.sin(animTick * 0.3f) * (4f * density);
            canvas.drawOval(new RectF(-r * 1.55f, cy - r * 0.3f + pigtailSwing,
                    -r * 0.75f, cy + r * 1.05f + pigtailSwing), paint);
            canvas.drawOval(new RectF(r * 0.75f, cy - r * 0.3f - pigtailSwing,
                    r * 1.55f, cy + r * 1.05f - pigtailSwing), paint);
            // 红色发带与金色微型小皇冠
            paint.setColor(0xFFDC2626);
            canvas.drawCircle(-r * 0.95f, cy - r * 0.2f + pigtailSwing, 3.5f * density, paint);
            canvas.drawCircle(r * 0.95f, cy - r * 0.2f - pigtailSwing, 3.5f * density, paint);
            paint.setColor(0xFFEAB308);
            canvas.drawRect(-3 * density, cy - r * 1.15f, 3 * density, cy - r * 0.95f, paint);
        } else if (hero == BrotatoHero.MAGE) { // 月咏 (Tsukuyomi) - 墨黑姬发与红发带
            paint.setColor(0xFF0F172A);
            canvas.drawCircle(0, cy - r * 0.25f, r * 0.95f, paint);
            canvas.drawRect(-r * 0.92f, cy - r * 0.1f, -r * 0.65f, cy + r * 0.85f, paint);
            canvas.drawRect(r * 0.65f, cy - r * 0.1f, r * 0.92f, cy + r * 0.85f, paint);
            paint.setColor(0xFFDC2626);
            canvas.drawRoundRect(new RectF(-r * 0.85f, cy - r * 0.55f, r * 0.85f, cy - r * 0.4f),
                    2 * density, 2 * density, paint);
        } else if (hero == BrotatoHero.CHUNKY) { // 沃尔默 (Worm) - 军绿贝雷帽与刚毅刀疤
            paint.setColor(0xFF1E3A2F); // 贝雷帽
            canvas.drawOval(new RectF(-r * 0.98f, cy - r * 1.15f, r * 0.85f, cy - r * 0.32f), paint);
            paint.setColor(0xFFE2E8F0); // 银色鹰徽
            canvas.drawCircle(r * 0.35f, cy - r * 0.72f, 3f * density, paint);
        } else if (hero == BrotatoHero.LUCKY) { // 凯蒂斯 (Catnips) - 可爱猫耳与战术耳麦
            paint.setColor(0xFFF8FAFC); // 白猫耳
            tempPath.reset();
            tempPath.moveTo(-r * 0.75f, cy - r * 0.5f);
            tempPath.lineTo(-r * 0.65f, cy - r * 1.35f);
            tempPath.lineTo(-r * 0.22f, cy - r * 0.85f);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            tempPath.reset();
            tempPath.moveTo(r * 0.75f, cy - r * 0.5f);
            tempPath.lineTo(r * 0.65f, cy - r * 1.35f);
            tempPath.lineTo(r * 0.22f, cy - r * 0.85f);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            paint.setColor(0xFFF472B6); // 猫耳内粉绒毛
            canvas.drawCircle(-r * 0.55f, cy - r * 0.85f, 3.5f * density, paint);
            canvas.drawCircle(r * 0.55f, cy - r * 0.85f, 3.5f * density, paint);

            // 战术耳麦
            paint.setColor(0xFF334155);
            canvas.drawCircle(r * 0.88f, cy + 2 * density, 4 * density, paint);
            strokePaint.setColor(0xFF64748B);
            strokePaint.setStrokeWidth(1.8f * density);
            canvas.drawLine(r * 0.88f, cy + 2 * density, r * 0.55f, cy + r * 0.45f, strokePaint);
        } else if (hero == BrotatoHero.SPEEDSTER) { // 斯巴达 (Spartacus) - 赛博发光全息护目镜
            paint.setColor(0xFF0F172A);
            canvas.drawCircle(0, cy - r * 0.3f, r * 0.95f, paint);
            paint.setColor(0xFF00E5FF); // 赛博青全息护目镜
            canvas.drawRoundRect(new RectF(-r * 0.82f, cy - r * 0.12f, r * 0.82f, cy + r * 0.25f),
                    3 * density, 3 * density, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(-r * 0.6f, cy, r * 0.6f, cy + 1.5f * density, paint);
        } else { // 科萌 (Common) - 经典黑色微蓬战术短发
            paint.setColor(0xFF1E293B);
            // 顶部圆发蓬
            canvas.drawCircle(0, cy - r * 0.28f, r * 0.98f, paint);
            // 帅气侧向碎刘海
            tempPath.reset();
            tempPath.moveTo(-r * 0.85f, cy - r * 0.25f);
            tempPath.lineTo(-r * 0.45f, cy + r * 0.05f);
            tempPath.lineTo(-r * 0.15f, cy - r * 0.25f);
            tempPath.lineTo(r * 0.25f, cy + r * 0.05f);
            tempPath.lineTo(r * 0.65f, cy - r * 0.25f);
            tempPath.lineTo(r * 0.85f, cy - r * 0.5f);
            tempPath.close();
            canvas.drawPath(tempPath, paint);
        }
    }

    private void drawAgentArmsAndWeapons(Canvas canvas, BrotatoHero hero, float s,
                                         float aimAngle, long animTick, float density) {
        float armDist = s * 0.48f;
        float armY = s * 0.18f;

        // 依据瞄准角度绘制持枪手与武器实体
        float handX = (float) Math.cos(aimAngle) * armDist;
        float handY = armY + (float) Math.sin(aimAngle) * (armDist * 0.65f);

        canvas.save();
        canvas.translate(handX, handY);
        canvas.rotate((float) Math.toDegrees(aimAngle));

        paint.setStyle(Paint.Style.FILL);
        if (hero == BrotatoHero.BRAWLER) { // 金 - 双持战术左轮/手枪
            drawDualPistolsModel(canvas, density);
        } else if (hero == BrotatoHero.MAGE) { // 月咏 - 和风太刀
            drawKatanaModel(canvas, density);
        } else { // 科萌与通用特工 - 经典战术苦无/冲锋枪
            drawKunaiModel(canvas, density);
        }

        canvas.restore();
    }

    private void drawKunaiModel(Canvas canvas, float density) {
        // 苦无刃面（银钢质感）
        paint.setColor(0xFFE2E8F0);
        tempPath.reset();
        tempPath.moveTo(14 * density, 0);
        tempPath.lineTo(2 * density, -4 * density);
        tempPath.lineTo(-4 * density, -2.5f * density);
        tempPath.lineTo(-4 * density, 2.5f * density);
        tempPath.lineTo(2 * density, 4 * density);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 苦无锋刃脊线
        paint.setColor(0xFF94A3B8);
        canvas.drawLine(2 * density, 0, 14 * density, 0, strokePaint);

        // 绑带握柄
        paint.setColor(0xFF1E293B);
        canvas.drawRect(-9 * density, -2f * density, -4 * density, 2f * density, paint);

        // 尾端圆环
        strokePaint.setColor(0xFFCBD5E1);
        strokePaint.setStrokeWidth(1.8f * density);
        canvas.drawCircle(-11 * density, 0, 2.5f * density, strokePaint);

        // 尾环红穗
        paint.setColor(0xFFEF4444);
        canvas.drawCircle(-14 * density, 0, 2f * density, paint);
    }

    private void drawDualPistolsModel(Canvas canvas, float density) {
        // 右手机枪
        paint.setColor(0xFF374151);
        canvas.drawRoundRect(new RectF(0, -6 * density, 13 * density, -2 * density), 1.5f * density, 1.5f * density, paint);
        paint.setColor(0xFFDC2626); // 激光指示器红点
        canvas.drawCircle(13 * density, -4 * density, 1.5f * density, paint);

        // 左手机枪
        paint.setColor(0xFF374151);
        canvas.drawRoundRect(new RectF(0, 2 * density, 13 * density, 6 * density), 1.5f * density, 1.5f * density, paint);
        paint.setColor(0xFFDC2626);
        canvas.drawCircle(13 * density, 4 * density, 1.5f * density, paint);
    }

    private void drawKatanaModel(Canvas canvas, float density) {
        // 弧度刀刃
        paint.setColor(0xFFE2E8F0);
        tempPath.reset();
        tempPath.moveTo(0, -2 * density);
        tempPath.quadTo(10 * density, -3 * density, 22 * density, -1 * density);
        tempPath.lineTo(20 * density, 1.5f * density);
        tempPath.quadTo(10 * density, -1 * density, 0, 2 * density);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 金色刀镡与柄卷
        paint.setColor(0xFFF59E0B);
        canvas.drawCircle(0, 0, 3.5f * density, paint);
        paint.setColor(0xFF1E1B4B);
        canvas.drawRect(-8 * density, -2 * density, 0, 2 * density, paint);
    }

    // =========================================================================
    // 3. 八大敌寇生化异变体全方位立体建模 (8 Distinct Enemy Vector Models)
    // =========================================================================

    public void drawEnemy(Canvas canvas, Enemy enemy, long animTick, float density) {
        float cx = enemy.x * density;
        float cy = enemy.y * density;
        String id = (enemy.kind != null) ? enemy.kind.id() : "";

        canvas.save();
        canvas.translate(cx, cy);

        // 依据怪物类型设置生动饱满的体型尺度
        float r = enemy.radiusPx * density;
        float visualScale = Math.max(16f * density, r * 1.8f);

        // 接触软阴影
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x40000000);
        canvas.drawOval(new RectF(-visualScale * 0.9f, visualScale * 0.65f,
                visualScale * 0.9f, visualScale * 1.15f), paint);

        // 派发专属高模渲染
        switch (id) {
            case "swarm":
                drawSwarmBug(canvas, enemy, visualScale, animTick, density);
                break;
            case "runner":
                drawRunnerHound(canvas, enemy, visualScale, animTick, density);
                break;
            case "zigzag":
                drawZigzagMoth(canvas, enemy, visualScale, animTick, density);
                break;
            case "splitter":
                drawSplitterBlob(canvas, enemy, visualScale, animTick, density);
                break;
            case "minion":
                drawMinionSlime(canvas, enemy, visualScale, animTick, density);
                break;
            case "tank":
                drawTankBehemoth(canvas, enemy, visualScale * 1.35f, animTick, density);
                break;
            case "boss":
                drawBossHiveLord(canvas, enemy, visualScale * 1.85f, animTick, density);
                break;
            case "grunt":
            default:
                drawClassicZombie(canvas, enemy, visualScale, animTick, density);
                break;
        }

        // 生命值血槽（受创展示）
        if (enemy.hp < enemy.maxHp) {
            drawEnemyHealthBar(canvas, enemy, visualScale, density);
        }

        canvas.restore();
    }

    /**
     * 1. 经典普通生化丧尸 (Classic Walking Dead Zombie - 《蛋壳特工队》最常见小怪)
     */
    private void drawClassicZombie(Canvas canvas, Enemy enemy, float s, long animTick, float density) {
        float lurch = (float) Math.sin(animTick * 0.22f + enemy.x) * (3f * density);
        float armReach = (float) Math.cos(animTick * 0.22f + enemy.x) * (4f * density);

        paint.setStyle(Paint.Style.FILL);

        // 破烂深蓝工装裤与迈步双腿
        paint.setColor(0xFF1E293B);
        canvas.drawRect(-s * 0.45f, s * 0.35f, -s * 0.12f, s * 0.85f + lurch, paint);
        canvas.drawRect(s * 0.12f, s * 0.35f, s * 0.45f, s * 0.85f - lurch, paint);

        // 破旧办公室青蓝衬衫（露出撕裂口）
        paint.setColor(0xFF0284C7);
        canvas.drawRoundRect(new RectF(-s * 0.52f, -s * 0.05f, s * 0.52f, s * 0.45f), 4 * density, 4 * density, paint);
        // 破损处露出腐烂绿肉
        paint.setColor(0xFF65A30D);
        canvas.drawCircle(-s * 0.25f, s * 0.2f, 3.5f * density, paint);

        // 丧尸伸直抓挠双臂 (Outstretched Grasping Zombie Arms)
        paint.setColor(0xFF84CC16); // 腐败浅绿表皮
        canvas.drawRoundRect(new RectF(-s * 0.65f, 0, -s * 0.35f, s * 0.5f + armReach), 2.5f * density, 2.5f * density, paint);
        canvas.drawRoundRect(new RectF(s * 0.35f, 0, s * 0.65f, s * 0.5f - armReach), 2.5f * density, 2.5f * density, paint);
        // 尖锐指爪
        paint.setColor(0xFF14532D);
        canvas.drawCircle(-s * 0.5f, s * 0.52f + armReach, 2 * density, paint);
        canvas.drawCircle(s * 0.5f, s * 0.52f - armReach, 2 * density, paint);

        // 腐烂绿色丧尸头颅
        paint.setColor(0xFF84CC16);
        float headCy = -s * 0.42f;
        canvas.drawCircle(0, headCy, s * 0.48f, paint);

        // 凌乱黑发
        paint.setColor(0xFF0F172A);
        tempPath.reset();
        tempPath.moveTo(-s * 0.45f, headCy - s * 0.2f);
        tempPath.lineTo(-s * 0.2f, headCy - s * 0.55f);
        tempPath.lineTo(0, headCy - s * 0.35f);
        tempPath.lineTo(s * 0.3f, headCy - s * 0.58f);
        tempPath.lineTo(s * 0.48f, headCy - s * 0.15f);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 猩红凶煞双眼 (Glowing Evil Red Eyes)
        paint.setColor(0xFF0F172A); // 深陷黑眼眶
        canvas.drawCircle(-s * 0.22f, headCy + 1 * density, s * 0.16f, paint);
        canvas.drawCircle(s * 0.22f, headCy + 1 * density, s * 0.16f, paint);

        paint.setColor(0xFFDC2626); // 血红凶光
        canvas.drawCircle(-s * 0.22f, headCy + 1 * density, s * 0.09f, paint);
        canvas.drawCircle(s * 0.22f, headCy + 1 * density, s * 0.09f, paint);

        // 咧开的腐烂嘴部与尖牙
        paint.setColor(0xFF1F2937);
        canvas.drawRoundRect(new RectF(-s * 0.25f, headCy + s * 0.22f, s * 0.25f, headCy + s * 0.38f),
                2 * density, 2 * density, paint);
        paint.setColor(0xFFFEF08A); // 黄板牙
        canvas.drawRect(-s * 0.15f, headCy + s * 0.24f, -s * 0.05f, headCy + s * 0.30f, paint);
        canvas.drawRect(s * 0.05f, headCy + s * 0.24f, s * 0.15f, headCy + s * 0.30f, paint);
    }

    /**
     * 2. 自爆剧毒蛛虫 (Poison Swarm Spider Bug)
     */
    private void drawSwarmBug(Canvas canvas, Enemy enemy, float s, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);

        // 6条分节蜘蛛快频爬行腿
        strokePaint.setColor(0xFF4C1D95);
        strokePaint.setStrokeWidth(2.2f * density);
        float legSwing = (float) Math.sin(animTick * 0.4f + enemy.x) * (5f * density);

        for (int i = -1; i <= 1; i++) {
            float ly = i * (s * 0.32f);
            canvas.drawLine(-s * 0.3f, ly, -s * 0.85f, ly - s * 0.4f + legSwing * i, strokePaint);
            canvas.drawLine(-s * 0.85f, ly - s * 0.4f + legSwing * i, -s * 1.15f, ly + s * 0.2f, strokePaint);

            canvas.drawLine(s * 0.3f, ly, s * 0.85f, ly - s * 0.4f - legSwing * i, strokePaint);
            canvas.drawLine(s * 0.85f, ly - s * 0.4f - legSwing * i, s * 1.15f, ly + s * 0.2f, strokePaint);
        }

        // 膨胀剧毒荧光尾囊 (Glowing Venom Egg Sac)
        paint.setColor(0xFF7E22CE);
        canvas.drawOval(new RectF(-s * 0.65f, -s * 0.1f, s * 0.65f, s * 0.85f), paint);

        // 剧毒荧光斑纹 (Biohazard Glow)
        float pulse = 0.8f + 0.2f * (float) Math.sin(animTick * 0.35f);
        paint.setColor(0xFF84CC16);
        canvas.drawCircle(0, s * 0.45f, s * 0.25f * pulse, paint);
        paint.setColor(0xFFFACC15);
        canvas.drawCircle(0, s * 0.45f, s * 0.12f * pulse, paint);

        // 甲壳胸节与凶残口器
        paint.setColor(0xFF3B0764);
        canvas.drawOval(new RectF(-s * 0.45f, -s * 0.65f, s * 0.45f, 0f), paint);

        // 4只猩红复眼
        paint.setColor(0xFFEF4444);
        canvas.drawCircle(-s * 0.22f, -s * 0.45f, 2.5f * density, paint);
        canvas.drawCircle(s * 0.22f, -s * 0.45f, 2.5f * density, paint);
        canvas.drawCircle(-s * 0.1f, -s * 0.55f, 1.8f * density, paint);
        canvas.drawCircle(s * 0.1f, -s * 0.55f, 1.8f * density, paint);
    }

    /**
     * 3. 感染狂暴猎犬 (Infected Runner Hound)
     */
    private void drawRunnerHound(Canvas canvas, Enemy enemy, float s, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        float gallop = (float) Math.sin(animTick * 0.45f + enemy.x) * (6f * density);

        // 狂暴血肉四肢奔跑姿态
        paint.setColor(0xFF7F1D1D);
        canvas.drawRoundRect(new RectF(-s * 0.75f, s * 0.2f, -s * 0.45f, s * 0.85f + gallop), 2 * density, 2 * density, paint);
        canvas.drawRoundRect(new RectF(s * 0.45f, s * 0.2f, s * 0.75f, s * 0.85f - gallop), 2 * density, 2 * density, paint);

        // 肌肉虬结的血肉躯干
        paint.setColor(0xFF991B1B);
        canvas.drawOval(new RectF(-s * 0.65f, -s * 0.35f, s * 0.65f, s * 0.45f), paint);

        // 脊椎骨凸起锋利骨刺 (Spinal Bone Spikes)
        paint.setColor(0xFFF1F5F9);
        for (int i = -2; i <= 2; i++) {
            float bx = i * (s * 0.22f);
            tempPath.reset();
            tempPath.moveTo(bx - 2 * density, -s * 0.35f);
            tempPath.lineTo(bx, -s * 0.65f);
            tempPath.lineTo(bx + 2 * density, -s * 0.35f);
            tempPath.close();
            canvas.drawPath(tempPath, paint);
        }

        // 凶残獠牙兽首
        paint.setColor(0xFF7F1D1D);
        canvas.drawOval(new RectF(-s * 0.45f, -s * 0.82f, s * 0.45f, -s * 0.2f), paint);

        // 狂怒黄色兽瞳与滴血利齿
        paint.setColor(0xFFFBBF24);
        canvas.drawCircle(-s * 0.2f, -s * 0.55f, 2.8f * density, paint);
        canvas.drawCircle(s * 0.2f, -s * 0.55f, 2.8f * density, paint);

        paint.setColor(Color.WHITE); // 惨白獠牙
        canvas.drawRect(-s * 0.25f, -s * 0.35f, -s * 0.15f, -s * 0.2f, paint);
        canvas.drawRect(s * 0.15f, -s * 0.35f, s * 0.25f, -s * 0.2f, paint);
    }

    /**
     * 4. 剧毒晶翅幻蛾 (Toxic Zigzag Moth)
     */
    private void drawZigzagMoth(Canvas canvas, Enemy enemy, float s, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        float wingFlap = (float) Math.sin(animTick * 0.45f + enemy.x) * (s * 0.35f);

        // 左右巨型斑纹毒翼 (Poison Patterned Wings)
        paint.setColor(0xDDA855F7);
        tempPath.reset();
        tempPath.moveTo(-s * 0.15f, -s * 0.1f);
        tempPath.quadTo(-s * 1.35f, -s * 0.85f + wingFlap, -s * 1.25f, s * 0.35f + wingFlap);
        tempPath.lineTo(-s * 0.15f, s * 0.35f);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        tempPath.reset();
        tempPath.moveTo(s * 0.15f, -s * 0.1f);
        tempPath.quadTo(s * 1.35f, -s * 0.85f + wingFlap, s * 1.25f, s * 0.35f + wingFlap);
        tempPath.lineTo(s * 0.15f, s * 0.35f);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 翅膀迷幻眼斑
        paint.setColor(0xFFF43F5E);
        canvas.drawCircle(-s * 0.75f, -s * 0.15f + wingFlap, s * 0.18f, paint);
        canvas.drawCircle(s * 0.75f, -s * 0.15f + wingFlap, s * 0.18f, paint);

        // 茸毛躯干与羽状触角
        paint.setColor(0xFF3B0764);
        canvas.drawRoundRect(new RectF(-s * 0.22f, -s * 0.55f, s * 0.22f, s * 0.65f),
                3 * density, 3 * density, paint);

        strokePaint.setColor(0xFFC084FC);
        strokePaint.setStrokeWidth(1.8f * density);
        canvas.drawLine(0, -s * 0.5f, -s * 0.35f, -s * 0.95f, strokePaint);
        canvas.drawLine(0, -s * 0.5f, s * 0.35f, -s * 0.95f, strokePaint);
    }

    /**
     * 5. 异变分裂黏液球 (Splitter Slime Blob with Floating Skull)
     */
    private void drawSplitterBlob(Canvas canvas, Enemy enemy, float s, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        float squish = (float) Math.sin(animTick * 0.3f + enemy.x) * (s * 0.15f);

        // 半透翠绿胶质黏液躯体
        paint.setColor(0xCC10B981);
        canvas.drawOval(new RectF(-s * 0.95f - squish, -s * 0.75f + squish,
                s * 0.95f + squish, s * 0.85f - squish), paint);

        // 内部包裹的人类骷髅残骸 (Floating Skull Inside Slime)
        paint.setColor(0xFFE2E8F0);
        canvas.drawCircle(0, -s * 0.12f, s * 0.32f, paint);
        canvas.drawRect(-s * 0.2f, s * 0.05f, s * 0.2f, s * 0.25f, paint);

        // 骷髅黑眼窝
        paint.setColor(0xFF0F172A);
        canvas.drawCircle(-s * 0.12f, -s * 0.15f, s * 0.09f, paint);
        canvas.drawCircle(s * 0.12f, -s * 0.15f, s * 0.09f, paint);

        // 黏液表面光泽斑块
        paint.setColor(0x88A7F3D0);
        canvas.drawOval(new RectF(-s * 0.6f, -s * 0.55f, -s * 0.15f, -s * 0.25f), paint);
    }

    /**
     * 6. 小型分裂幼体 (Minion Slime)
     */
    private void drawMinionSlime(Canvas canvas, Enemy enemy, float s, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        float hop = (float) Math.abs(Math.sin(animTick * 0.35f + enemy.x)) * (3f * density);

        paint.setColor(0xFF34D399);
        canvas.drawOval(new RectF(-s * 0.75f, -s * 0.65f - hop, s * 0.75f, s * 0.55f - hop), paint);

        // 呆萌大眼
        paint.setColor(Color.WHITE);
        canvas.drawCircle(-s * 0.28f, -s * 0.15f - hop, s * 0.22f, paint);
        canvas.drawCircle(s * 0.28f, -s * 0.15f - hop, s * 0.22f, paint);

        paint.setColor(0xFF0F172A);
        canvas.drawCircle(-s * 0.25f, -s * 0.15f - hop, s * 0.12f, paint);
        canvas.drawCircle(s * 0.31f, -s * 0.15f - hop, s * 0.12f, paint);
    }

    /**
     * 7. 生化重装泰坦屠夫 (Mutant Tank Behemoth - 精英巨兽)
     */
    private void drawTankBehemoth(Canvas canvas, Enemy enemy, float s, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);
        float stomp = (float) Math.sin(animTick * 0.18f + enemy.x) * (2f * density);

        // 巨型紫灰粗壮四肢
        paint.setColor(0xFF334155);
        canvas.drawRoundRect(new RectF(-s * 0.85f, s * 0.2f, -s * 0.35f, s * 0.95f + stomp), 4 * density, 4 * density, paint);
        canvas.drawRoundRect(new RectF(s * 0.35f, s * 0.2f, s * 0.85f, s * 0.95f - stomp), 4 * density, 4 * density, paint);

        // 雄伟巨硕肌肉躯干
        paint.setColor(0xFF475569);
        canvas.drawRoundRect(new RectF(-s * 0.95f, -s * 0.45f, s * 0.95f, s * 0.55f), 8 * density, 8 * density, paint);

        // 铆钉厚铁肩甲 (Riveted Iron Shoulder Armor)
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(new RectF(-s * 1.15f, -s * 0.65f, -s * 0.55f, -s * 0.15f), 4 * density, 4 * density, paint);
        canvas.drawRoundRect(new RectF(s * 0.55f, -s * 0.65f, s * 1.15f, -s * 0.15f), 4 * density, 4 * density, paint);

        paint.setColor(0xFF94A3B8); // 钢制铆钉
        canvas.drawCircle(-s * 0.85f, -s * 0.4f, 2.5f * density, paint);
        canvas.drawCircle(s * 0.85f, -s * 0.4f, 2.5f * density, paint);

        // 凶恶铁面具兽头
        paint.setColor(0xFF334155);
        canvas.drawCircle(0, -s * 0.65f, s * 0.42f, paint);
        paint.setColor(0xFF0F172A); // 铁面罩
        canvas.drawRect(-s * 0.28f, -s * 0.65f, s * 0.28f, -s * 0.35f, paint);

        // 猩红发光狭缝眼
        paint.setColor(0xFFEF4444);
        canvas.drawRect(-s * 0.25f, -s * 0.78f, -s * 0.08f, -s * 0.70f, paint);
        canvas.drawRect(s * 0.08f, -s * 0.78f, s * 0.25f, -s * 0.70f, paint);

        // 右手持握巨大生化重锤/钢管
        paint.setColor(0xFF0F172A);
        canvas.drawRect(s * 0.95f, -s * 0.95f, s * 1.15f, s * 0.65f, paint);
        paint.setColor(0xFFDC2626); // 沾染血迹的锤头
        canvas.drawRoundRect(new RectF(s * 0.8f, -s * 1.35f, s * 1.3f, -s * 0.85f), 3 * density, 3 * density, paint);
    }

    /**
     * 8. 深渊母体生化领主 (Abyssal Hive Lord - 关底终极Boss)
     */
    private void drawBossHiveLord(Canvas canvas, Enemy enemy, float s, long animTick, float density) {
        paint.setStyle(Paint.Style.FILL);

        // 4条扭动巨型合金生化触手 (Writhe Bladed Cyber Tentacles)
        for (int i = 0; i < 4; i++) {
            float tAngle = (float) (i * Math.PI / 2.0 + animTick * 0.05f);
            float tx = (float) Math.cos(tAngle) * (s * 1.15f);
            float ty = (float) Math.sin(tAngle) * (s * 1.15f);

            strokePaint.setColor(0xFF7F1D1D);
            strokePaint.setStrokeWidth(5 * density);
            canvas.drawLine(0, 0, tx, ty, strokePaint);

            // 触手尖端利刃
            paint.setColor(0xFFEF4444);
            canvas.drawCircle(tx, ty, 5.5f * density, paint);
        }

        // 钛合金重装母体躯干
        paint.setColor(0xFF18181B);
        canvas.drawCircle(0, 0, s * 0.85f, paint);
        strokePaint.setColor(0xFF71717A);
        strokePaint.setStrokeWidth(4 * density);
        canvas.drawCircle(0, 0, s * 0.82f, strokePaint);

        // 核心脉动反应堆 (Pulsating Bio-Core)
        float corePulse = 0.85f + 0.15f * (float) Math.sin(animTick * 0.25f);
        paint.setColor(0xFFDC2626);
        canvas.drawCircle(0, 0, s * 0.42f * corePulse, paint);
        paint.setColor(0xFFFEF08A);
        canvas.drawCircle(0, 0, s * 0.22f * corePulse, paint);

        // 五颗环绕冷酷复眼
        for (int i = 0; i < 5; i++) {
            float eAngle = (float) (i * 2.0 * Math.PI / 5.0 - Math.PI / 2.0);
            float ex = (float) Math.cos(eAngle) * (s * 0.62f);
            float ey = (float) Math.sin(eAngle) * (s * 0.62f);

            paint.setColor(0xFFFBBF24);
            canvas.drawCircle(ex, ey, 4.5f * density, paint);
            paint.setColor(0xFF7F1D1D);
            canvas.drawCircle(ex, ey, 2.2f * density, paint);
        }

        // 顶部 Boss 战神标识
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(12 * density);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(0xFFEF4444);
        canvas.drawText("👑 BOSS 👑", 0, -s * 1.05f, textPaint);
    }

    private void drawEnemyHealthBar(Canvas canvas, Enemy enemy, float s, float density) {
        float barW = s * 1.5f;
        float barH = 3.5f * density;
        float topY = -s * 0.95f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x88000000);
        canvas.drawRoundRect(new RectF(-barW / 2f, topY, barW / 2f, topY + barH), 1.5f * density, 1.5f * density, paint);

        float pct = Math.max(0f, (float) enemy.hp / Math.max(1, enemy.maxHp));
        paint.setColor(pct > 0.4f ? 0xFFEF4444 : 0xFFDC2626);
        canvas.drawRoundRect(new RectF(-barW / 2f, topY, -barW / 2f + barW * pct, topY + barH),
                1.5f * density, 1.5f * density, paint);
    }

    // =========================================================================
    // 4. 武器弹道、手里剑、旋转飞盘、无人机与力场高模 (Weapons & Effects)
    // =========================================================================

    public void drawProjectiles(Canvas canvas, BrotatoArena value, BrotatoHero hero,
                                SurvivorSkill.SkillInventory inventory, long animTick, float density) {
        boolean isGhostShuriken = inventory != null && inventory.isEvolved(SurvivorSkill.Type.KUNAI);

        for (Projectile bullet : value.projectiles()) {
            float bx = bullet.x * density;
            float by = bullet.y * density;
            float angle = (float) Math.atan2(bullet.vy, bullet.vx);

            canvas.save();
            canvas.translate(bx, by);
            canvas.rotate((float) Math.toDegrees(angle));

            if (isGhostShuriken) {
                // 幽灵手里剑 (Ghost Shuriken EVO): 旋转飞舞四角破空飞镖
                drawGhostShurikenBullet(canvas, animTick, density);
            } else {
                // 经典战术苦无飞镖
                drawKunaiBullet(canvas, density);
            }

            canvas.restore();
        }
    }

    private void drawKunaiBullet(Canvas canvas, float density) {
        // 气流尾光
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x6638BDF8);
        canvas.drawRect(-12 * density, -1.8f * density, 0, 1.8f * density, paint);

        // 银刃苦无
        paint.setColor(0xFFF1F5F9);
        tempPath.reset();
        tempPath.moveTo(8 * density, 0);
        tempPath.lineTo(0, -3.2f * density);
        tempPath.lineTo(-5 * density, -2f * density);
        tempPath.lineTo(-5 * density, 2f * density);
        tempPath.lineTo(0, 3.2f * density);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 锋刃高光
        paint.setColor(0xFF38BDF8);
        canvas.drawCircle(8 * density, 0, 1.5f * density, paint);
    }

    private void drawGhostShurikenBullet(Canvas canvas, long animTick, float density) {
        float rot = (animTick * 28f) % 360f;
        canvas.rotate(rot);

        // 幽能灵焰外晕
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x88A855F7);
        canvas.drawCircle(0, 0, 10 * density, paint);

        // 4角旋转刀刃
        paint.setColor(0xFFE0E7FF);
        for (int i = 0; i < 4; i++) {
            canvas.save();
            canvas.rotate(i * 90f);
            tempPath.reset();
            tempPath.moveTo(0, 0);
            tempPath.lineTo(9 * density, -2.5f * density);
            tempPath.lineTo(12 * density, 0);
            tempPath.lineTo(9 * density, 2.5f * density);
            tempPath.close();
            canvas.drawPath(tempPath, paint);
            canvas.restore();
        }

        // 紫电幽核
        paint.setColor(0xFF8B5CF6);
        canvas.drawCircle(0, 0, 3.5f * density, paint);
    }

    public void drawGuardianBlades(Canvas canvas, float px, float py, float orbitRadius,
                                   int bladeCount, boolean isDefender, long animTick, float density) {
        float baseAngle = (animTick * (isDefender ? 14f : 8.5f)) % 360f;

        // 防御力场环
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(isDefender ? 0x66F59E0B : 0x4438BDF8);
        strokePaint.setStrokeWidth(2f * density);
        canvas.drawCircle(px, py, orbitRadius, strokePaint);

        float step = 360f / bladeCount;
        for (int i = 0; i < bladeCount; i++) {
            float angleDeg = baseAngle + i * step;
            double rad = Math.toRadians(angleDeg);
            float bx = px + (float) Math.cos(rad) * orbitRadius;
            float by = py + (float) Math.sin(rad) * orbitRadius;

            canvas.save();
            canvas.translate(bx, by);
            canvas.rotate(angleDeg * 3.5f); // 飞盘高速自转

            float bladeR = (isDefender ? 14f : 11f) * density;

            // 锯齿圆形飞盘模型
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isDefender ? 0xFFF59E0B : 0xFFE2E8F0);
            canvas.drawCircle(0, 0, bladeR, paint);

            // 齿刃切割线
            strokePaint.setColor(isDefender ? 0xFFDC2626 : 0xFF0284C7);
            strokePaint.setStrokeWidth(2f * density);
            for (int k = 0; k < 6; k++) {
                float kRad = (float) (k * Math.PI / 3.0);
                canvas.drawLine((float) Math.cos(kRad) * bladeR * 0.35f, (float) Math.sin(kRad) * bladeR * 0.35f,
                        (float) Math.cos(kRad) * bladeR * 1.15f, (float) Math.sin(kRad) * bladeR * 1.15f, strokePaint);
            }

            // 核心轴承
            paint.setColor(0xFF1E293B);
            canvas.drawCircle(0, 0, bladeR * 0.32f, paint);

            canvas.restore();
        }
    }

    public void drawForcefield(Canvas canvas, float px, float py, float fieldRadius,
                               boolean isSuppression, long animTick, float density) {
        // 半透力场护盾球
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isSuppression ? 0x24A855F7 : 0x1E06B6D4);
        canvas.drawCircle(px, py, fieldRadius, paint);

        // 旋转蜂窝六边形能量边界网格
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(isSuppression ? 0x88C084FC : 0x7738BDF8);
        strokePaint.setStrokeWidth(2.2f * density);
        canvas.drawCircle(px, py, fieldRadius, strokePaint);

        // 6个能量节点
        float rotAngle = (animTick * 1.5f) % 360f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isSuppression ? 0xFFE879F9 : 0xFF67E8F9);
        for (int i = 0; i < 6; i++) {
            double rad = Math.toRadians(rotAngle + i * 60);
            float nx = px + (float) Math.cos(rad) * fieldRadius;
            float ny = py + (float) Math.sin(rad) * fieldRadius;
            canvas.drawCircle(nx, ny, 3.2f * density, paint);
        }
    }

    public void drawDrones(Canvas canvas, float px, float py, boolean hasA, boolean hasB,
                           boolean isDestroyer, long animTick, float density) {
        if (isDestroyer) {
            // 毁灭者 (Destroyer EVO): 双机融合终极隐身战舰
            float dist = 42 * density;
            double rad = Math.toRadians((animTick * 2.5f) % 360f);
            float dx = px + (float) Math.cos(rad) * dist;
            float dy = py + (float) Math.sin(rad) * dist - 18 * density;
            drawDestroyerDroneModel(canvas, dx, dy, animTick, density);
        } else {
            if (hasA) {
                float dx = px - 32 * density;
                float dy = py - 32 * density + (float) Math.sin(animTick * 0.15f) * (4f * density);
                drawSingleDroneModel(canvas, dx, dy, true, animTick, density);
            }
            if (hasB) {
                float dx = px + 32 * density;
                float dy = py - 32 * density + (float) Math.cos(animTick * 0.15f) * (4f * density);
                drawSingleDroneModel(canvas, dx, dy, false, animTick, density);
            }
        }
    }

    private void drawSingleDroneModel(Canvas canvas, float x, float y, boolean isWhiteA,
                                      long animTick, float density) {
        canvas.save();
        canvas.translate(x, y);

        // 无人机机身
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isWhiteA ? 0xFFF1F5F9 : 0xFF1E293B);
        canvas.drawRoundRect(new RectF(-10 * density, -6 * density, 10 * density, 6 * density), 3 * density, 3 * density, paint);

        // 光学扫描仪眼睛
        paint.setColor(isWhiteA ? 0xFF0284C7 : 0xFFEF4444);
        canvas.drawCircle(0, 0, 3 * density, paint);

        // 4个高速旋转螺旋桨旋翼
        float rotorRot = (animTick * 35f) % 360f;
        strokePaint.setColor(0x8894A3B8);
        strokePaint.setStrokeWidth(1.5f * density);
        for (float[] p : new float[][]{{-9, -6}, {9, -6}, {-9, 6}, {9, 6}}) {
            canvas.save();
            canvas.translate(p[0] * density, p[1] * density);
            canvas.rotate(rotorRot);
            canvas.drawLine(-5 * density, 0, 5 * density, 0, strokePaint);
            canvas.restore();
        }

        canvas.restore();
    }

    private void drawDestroyerDroneModel(Canvas canvas, float x, float y, long animTick, float density) {
        canvas.save();
        canvas.translate(x, y);

        // 终极毁灭者隐形轰炸机翼
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0F172A);
        tempPath.reset();
        tempPath.moveTo(0, -12 * density);
        tempPath.lineTo(18 * density, 10 * density);
        tempPath.lineTo(0, 5 * density);
        tempPath.lineTo(-18 * density, 10 * density);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        // 炽热能量喷气尾焰
        paint.setColor(0xFFF59E0B);
        canvas.drawCircle(0, 8 * density, 4.5f * density, paint);

        // 红色瞄准激光十字
        paint.setColor(0xFFDC2626);
        canvas.drawCircle(0, 0, 3 * density, paint);

        canvas.restore();
    }

    // =========================================================================
    // 5. 界面与 HUD 辅助系统 (Warning, EXP Bar, Dialogs)
    // =========================================================================

    public void drawWarningBanner(Canvas canvas, float width, float height, float density, long animTick) {
        float alpha = (float) (0.65f + 0.35f * Math.sin(animTick * 0.4f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) (alpha * 195), 185, 28, 28));

        float bannerH = 46 * density;
        float bannerY = height * 0.22f;
        canvas.drawRect(0, bannerY, width, bannerY + bannerH, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20 * density);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("⚠️ WARNING! 生化变异领主逼近 ⚠️", width / 2f, bannerY + 30 * density, textPaint);
    }

    public void drawTopExpBar(Canvas canvas, float width, float density, int curExp, int maxExp,
                              int curLevel, int score, int wave, int totalWaves) {
        float barH = 14 * density;

        // 顶栏底黑衬底
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0F172A);
        canvas.drawRect(0, 0, width, barH, paint);

        // 荧光亮绿经验进度条
        float ratio = maxExp > 0 ? Math.min(1.0f, (float) curExp / maxExp) : 0f;
        paint.setColor(0xFF22C55E);
        canvas.drawRect(0, 0, width * ratio, barH, paint);

        // 等级宝石勋章徽章
        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(new RectF(10 * density, 2 * density, 60 * density, barH + 16 * density),
                6 * density, 6 * density, paint);
        strokePaint.setColor(0xFFFBBF24);
        strokePaint.setStrokeWidth(1.8f * density);
        canvas.drawRoundRect(new RectF(10 * density, 2 * density, 60 * density, barH + 16 * density),
                6 * density, 6 * density, strokePaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFBBF24);
        textPaint.setTextSize(12 * density);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("LV." + curLevel, 35 * density, barH + 12 * density, textPaint);

        // 顶部小字
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(0xFF94A3B8);
        textPaint.setTextSize(10 * density);
        canvas.drawText("战功 " + score, 68 * density, barH + 11 * density, textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("波次 " + wave + "/" + totalWaves, width - 14 * density, barH + 11 * density, textPaint);
    }

    public void drawLevelUpDialog(Canvas canvas, float width, float height, float density,
                                  List<SurvivorSkill.Type> choices, SurvivorSkill.SkillInventory inventory,
                                  RectF[] cardRects) {
        // 暗夜半透遮罩
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDD090D16);
        canvas.drawRect(0, 0, width, height, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFFFBBF24);
        textPaint.setTextSize(26 * density);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("⚡ LEVEL UP! ⚡", width / 2f, height * 0.22f, textPaint);

        textPaint.setColor(0xFFCBD5E1);
        textPaint.setTextSize(13 * density);
        textPaint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("特工战术科技进化 · 选取一项核心专精", width / 2f, height * 0.22f + 25 * density, textPaint);

        int count = Math.min(3, choices.size());
        for (int i = 0; i < count; i++) {
            SurvivorSkill.Type type = choices.get(i);
            RectF rect = cardRects[i];
            drawTechChoiceCard(canvas, rect, type, inventory, density);
        }
    }

    private void drawTechChoiceCard(Canvas canvas, RectF rect, SurvivorSkill.Type type,
                                    SurvivorSkill.SkillInventory inventory, float density) {
        boolean isEvo = inventory.isEvolved(type);
        int curLvl = inventory.getLevel(type);

        // 卡片底色
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isEvo ? 0xFF2A1B0A : 0xFF1E293B);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, paint);

        // 卡片发光边框
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.5f * density);
        strokePaint.setColor(isEvo ? 0xFFF59E0B : 0xFF38BDF8);
        canvas.drawRoundRect(rect, 14 * density, 14 * density, paint);

        // 顶部圆形图标衬底
        float cx = rect.centerX();
        float topIconY = rect.top + 38 * density;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33FFFFFF);
        canvas.drawCircle(cx, topIconY, 18 * density, paint);

        // 图标文字
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(20 * density);
        canvas.drawText(type.icon, cx, topIconY + 7 * density, textPaint);

        // 科技名称
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(15 * density);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(isEvo ? type.evoName : type.nameCn, cx, rect.top + 76 * density, textPaint);

        // 星级显示 (⭐)
        paint.setColor(0xFFFBBF24);
        textPaint.setTextSize(11 * density);
        StringBuilder stars = new StringBuilder();
        for (int k = 0; k < 5; k++) {
            stars.append(k < curLvl ? "⭐" : "☆");
        }
        canvas.drawText(stars.toString(), cx, rect.top + 92 * density, textPaint);

        // 详细战术技能描述
        textPaint.setColor(0xFF94A3B8);
        textPaint.setTextSize(10.5f * density);
        textPaint.setTypeface(Typeface.DEFAULT);
        drawWrappedText(canvas, type.descCn, cx, rect.top + 108 * density, rect.width() - 16 * density, textPaint);

        // EVO 超武合成配方提示
        String hint = getEvoHint(type);
        if (hint != null && !hint.isEmpty()) {
            textPaint.setColor(0xFFFBBF24);
            textPaint.setTextSize(9.5f * density);
            canvas.drawText("配方: " + hint, cx, rect.bottom - 12 * density, textPaint);
        }
    }

    private String getEvoHint(SurvivorSkill.Type type) {
        if (SurvivorSkill.EVO_RULES != null) {
            for (SurvivorSkill.EvoRule rule : SurvivorSkill.EVO_RULES) {
                if (rule.activeA == type) {
                    return rule.passiveRequired.nameCn + " ➔ " + rule.evoTitle;
                }
            }
        }
        return "";
    }

    private void drawWrappedText(Canvas canvas, String text, float x, float y, float maxW, Paint paint) {
        if (text == null) return;
        float lineH = paint.getTextSize() * 1.35f;
        int start = 0;
        float curY = y;
        while (start < text.length()) {
            int count = paint.breakText(text, start, text.length(), true, maxW, null);
            if (count <= 0) break;
            String line = text.substring(start, start + count);
            canvas.drawText(line, x, curY, paint);
            curY += lineH;
            start += count;
        }
    }

    public void drawLuckyChestDialog(Canvas canvas, float width, float height, float density,
                                     int rolls, List<SurvivorSkill.Type> rewards, float spinProgress) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xEE090D16);
        canvas.drawRect(0, 0, width, height, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFF10B981);
        textPaint.setTextSize(26 * density);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("🍀 三叶草幸运军需箱 🍀", width / 2f, height * 0.28f, textPaint);

        textPaint.setColor(0xFFCBD5E1);
        textPaint.setTextSize(14 * density);
        textPaint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("击溃异变精英 · 战术老虎机抽奖 (" + rolls + " 连抽)", width / 2f, height * 0.28f + 28 * density, textPaint);

        // 宝箱老虎机转盘视窗
        float boxW = width * 0.82f;
        float boxH = 140 * density;
        float boxL = (width - boxW) / 2f;
        float boxT = height * 0.38f;

        paint.setColor(0xFF1E293B);
        canvas.drawRoundRect(new RectF(boxL, boxT, boxL + boxW, boxT + boxH), 16 * density, 16 * density, paint);
        strokePaint.setColor(0xFFFBBF24);
        strokePaint.setStrokeWidth(3 * density);
        canvas.drawRoundRect(new RectF(boxL, boxT, boxL + boxW, boxT + boxH), 16 * density, 16 * density, strokePaint);

        if (rewards != null && !rewards.isEmpty()) {
            float slotW = boxW / rewards.size();
            for (int i = 0; i < rewards.size(); i++) {
                SurvivorSkill.Type r = rewards.get(i);
                float scx = boxL + (i + 0.5f) * slotW;
                float scy = boxT + boxH / 2f;

                textPaint.setTextSize(32 * density);
                canvas.drawText(r.icon, scx, scy + 4 * density, textPaint);
                textPaint.setTextSize(13 * density);
                textPaint.setColor(Color.WHITE);
                canvas.drawText(r.nameCn, scx, scy + 34 * density, textPaint);
            }
        }

        textPaint.setColor(0xFF38BDF8);
        textPaint.setTextSize(14 * density);
        canvas.drawText("点击任意位置装填军需", width / 2f, boxT + boxH + 42 * density, textPaint);
    }
}
