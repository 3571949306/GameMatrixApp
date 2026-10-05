package com.gamecenter.app.brotato;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.gamecenter.app.brotato.engine.BrotatoArena;
import com.gamecenter.app.brotato.engine.BrotatoContent;
import com.gamecenter.app.brotato.engine.Enemy;
import com.gamecenter.app.brotato.engine.Projectile;
import com.gamecenter.app.brotato.engine.Upgrade;

/**
 * 土豆兄弟游戏自定义 View（独立 APK 模块版本）。
 *
 * <p><b>这层只是渲染薄壳。</b>全部规则（出怪调度、行为、碰撞、成长、波次推进、胜负）都在
 * {@link BrotatoArena} 里；敌人数值与波次编成来自 assets 的 {@link BrotatoContent}
 * （装载入口见 {@link BrotatoAssets}）。View 只做三件事：把触摸翻成
 * {@link BrotatoArena#setTouchTarget}/{@link BrotatoArena#selectUpgrade(int)}、
 * 在 {@link #update()} 里推一 tick、按 kind 的颜色/半径把战场画出来。</p>
 *
 * <p>坐标口径：引擎工作在"逻辑单位"（{@link BrotatoArena#PX_PER_DP}=1，一局场地约 360×640），
 * View 以 {@code density} 为唯一换算比例 —— 送进引擎的边界是 {@code px / density}，
 * 画出来的是 {@code 引擎坐标 × density}。这样同一份内容在不同分辨率上视觉一致。</p>
 *
 * <p>驱动方式保持旧口径：由 Fragment 的 Handler 定时器按 16ms 调 {@link #update()}
 * （与引擎 {@link BrotatoArena#TICK_MS} 同频）。View 不自建帧回调，避免与外部定时器双驱动。</p>
 */
public class BrotatoView extends View {

    /** 宿主 UI 回调；语义与旧版一致，{@code onWin}/{@code onRewardCards} 是新增的默认实现。 */
    public interface OnGameListener {
        void onScoreChanged(int score);

        void onGameOver(int score, int wave);

        void onWaveComplete(int wave);

        /** 打完全部波次。默认空实现，保证既有接线代码不破。 */
        default void onWin(int score) { }

        /** 波次奖励：三张卡标题（与 {@code pendingCards()} 同序，即选卡下标）。 */
        default void onRewardCards(int wave, String[] titles) { }
    }

    /** HUD 预留高度（逻辑单位）：底部生命条区域不计入可行走场地。 */
    private static final float HUD_RESERVE_DP = 60f;
    private static final float GRID_STEP_DP = 40f;
    /** 子弹的可视半径下限：命中判定用引擎值，画得太小在高密度屏上会看不见。 */
    private static final float MIN_BULLET_DRAW_DP = 3f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float viewWidth;
    private float viewHeight;
    /** 设备密度（px = dp × density）；引擎逻辑单位与屏幕像素之间的唯一比例。 */
    private float density = 1f;

    private BrotatoContent content;
    private BrotatoArena arena;
    private OnGameListener listener;
    private float difficultyScalar = BrotatoArena.DIFFICULTY_NORMAL;
    /** 下一局的随机种子（{@link #setSeed(long)} 可复现某一局）。 */
    private long seed = System.nanoTime();
    private boolean gameRunning;
    private boolean gamePaused;
    /** 开局请求：等到有合法尺寸的那一帧再真正建 arena，保证玩家出生在场地正中。 */
    private boolean pendingStart;

    /** 引擎事件 → 宿主回调的唯一桥；随每个新 arena 重新挂载。 */
    private final BrotatoArena.Listener arenaListener = new BrotatoArena.Listener() {
        @Override
        public void onScoreChanged(int score) {
            OnGameListener value = listener;
            if (value != null) value.onScoreChanged(score);
        }

        @Override
        public void onWaveComplete(int wave) {
            OnGameListener value = listener;
            if (value != null) value.onWaveComplete(wave);
        }

        @Override
        public void onGameOver(int score, int wave) {
            gameRunning = false;
            OnGameListener value = listener;
            if (value != null) value.onGameOver(score, wave);
            invalidate();
        }

        @Override
        public void onWin(int score) {
            gameRunning = false;
            OnGameListener value = listener;
            if (value != null) value.onWin(score);
            invalidate();
        }

        @Override
        public void onRewardCards(int wave, String[] titles) {
            OnGameListener value = listener;
            if (value != null) value.onRewardCards(wave, titles);
            invalidate();
        }
    };

    // ==================== 构造方法 ====================

    public BrotatoView(Context context) {
        super(context);
        init();
    }

    /** 内容可由外部注入（预加载/测试路径）；不注入则在首次 {@link #startGame()} 时从资产装载。 */
    public BrotatoView(Context context, BrotatoContent content) {
        super(context);
        this.content = content;
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        setBackgroundColor(isNightMode() ? 0xFF0E1016 : 0xFF1B1B1F);
    }

    private boolean isNightMode() {
        int nightMode = getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public void setOnGameListener(OnGameListener listener) {
        this.listener = listener;
    }

    /**
     * 难度标量（{@link BrotatoArena#DIFFICULTY_EASY}/{@code NORMAL}/{@code HARD}）：
     * 乘在敌人 hp/speed 上、除在出怪间隔上。引擎把标量固定在构造期（一局一个值，保证同种子可复现），
     * 所以这里设置的是"下一局"的难度 —— {@link #startGame()} 会带着它重建 arena。
     */
    public void setDifficultyScalar(float scalar) {
        this.difficultyScalar = scalar;
    }

    /** 下一局起生效的种子：同种子 + 同操作 = 同结局。 */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    public long getSeed() {
        return seed;
    }

    // ==================== 游戏控制 ====================

    /** 签名与旧版一致（无参、void）。重开即重建 arena：状态干净、种子与难度生效。 */
    public void startGame() {
        content = requireContent();
        pendingStart = true;
        gameRunning = true;
        gamePaused = false;
        createArenaIfPossible();
        invalidate();
    }

    /** 场地尺寸可用时才真正开局：{@link BrotatoArena#setBounds} 要求正数边界。 */
    private void createArenaIfPossible() {
        if (!pendingStart || content == null) return;
        float width = viewWidth / density;
        float height = playfieldHeightPx() / density;
        if (width <= 0f || height <= 0f) return;
        try {
            BrotatoArena value = new BrotatoArena(content, difficultyScalar, seed);
            value.setListener(arenaListener);
            value.setBounds(width, height);
            value.start();
            arena = value;
        } catch (RuntimeException exception) {
            // fail-closed 但不崩宿主：停在开场画面，重开按钮可再试
            android.util.Log.e("BrotatoView", "failed to start brotato arena", exception);
            gameRunning = false;
        }
        pendingStart = false;
    }

    public void pauseGame() {
        gamePaused = true;
    }

    public void resumeGame() {
        gamePaused = false;
    }

    public void stopGame() {
        gameRunning = false;
        pendingStart = false;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public int getScore() {
        return arena == null ? 0 : arena.score();
    }

    public int getWave() {
        return arena == null ? 0 : arena.wave();
    }

    /** 已装载内容的内容版本号（未装载时为 0），排错与埋点用。 */
    public int getContentVersion() {
        return content == null ? 0 : content.contentVersion();
    }

    /**
     * 内容只在这里装载一次：失败即抛 {@link IllegalStateException}（fail-closed，
     * 不做 Java 内置回退表）。构造函数不读资产，避免"只是 inflate 就崩"的路径。
     */
    private BrotatoContent requireContent() {
        if (content == null) {
            throw new IllegalStateException(
                    "brotato content must be injected via Fragment module assets, never host");
        }
        return content;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        createArenaIfPossible();
        BrotatoArena value = arena;
        if (value != null && value.isStarted()) {
            float width = w / density;
            float height = playfieldHeightPx() / density;
            if (width > 0f && height > 0f) value.setBounds(width, height);
        }
    }

    /** 可行走区高度（px）：整屏扣掉底部 HUD。 */
    private float playfieldHeightPx() {
        return Math.max(0f, viewHeight - HUD_RESERVE_DP * density);
    }

    // ==================== 游戏循环 ====================

    /**
     * 推进一帧（一次 {@link BrotatoArena#tick()}）。由 Fragment 的 16ms 定时器驱动；
     * 暂停/未开局/终态时不推进，只保持画面。
     */
    public void update() {
        if (!gameRunning || gamePaused) return;
        if (arena == null) {
            createArenaIfPossible();
            if (arena == null) return;
        }
        arena.tick();
        postInvalidateOnAnimation();
    }

    // ==================== 绘制 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        BrotatoArena value = arena;
        if (value == null) return;
        paint.setStyle(Paint.Style.FILL);
        var state = value.state();
        if (state == BrotatoArena.State.GAME_OVER) {
            drawEndOverlay(canvas, value, "游戏结束", "点击下方按钮重新开始", 0xFFF44336);
            return;
        }
        if (state == BrotatoArena.State.WIN) {
            drawEndOverlay(canvas, value, "胜利！", "全部波次已肃清", 0xFF4CAF50);
            return;
        }
        if (!gameRunning) return;

        drawGrid(canvas);
        drawProjectiles(canvas, value);
        drawEnemies(canvas, value);
        drawPlayer(canvas, value);
        drawHud(canvas, value);
        if (state == BrotatoArena.State.REWARD_PENDING) {
            drawRewardCards(canvas, value);
        } else if (state == BrotatoArena.State.INTERMISSION) {
            drawIntermissionBanner(canvas, value);
        }
    }

    private void drawGrid(Canvas canvas) {
        paint.setColor(0x22FFFFFF);
        paint.setStrokeWidth(1);
        float step = GRID_STEP_DP * density;
        for (float x = 0; x < viewWidth; x += step) {
            canvas.drawLine(x, 0, x, viewHeight, paint);
        }
        for (float y = 0; y < viewHeight; y += step) {
            canvas.drawLine(0, y, viewWidth, y, paint);
        }
    }

    private void drawProjectiles(Canvas canvas, BrotatoArena value) {
        paint.setColor(0xFFFFEB3B);
        for (Projectile bullet : value.projectiles()) {
            canvas.drawCircle(bullet.x * density, bullet.y * density,
                    Math.max(MIN_BULLET_DRAW_DP, bullet.radiusPx) * density, paint);
        }
    }

    /** 敌人按 kind 的颜色与半径画（尺寸/颜色全部来自 enemies.json），受伤时补一条血皮。 */
    private void drawEnemies(Canvas canvas, BrotatoArena value) {
        for (Enemy enemy : value.enemies()) {
            float cx = enemy.x * density;
            float cy = enemy.y * density;
            float radius = enemy.radiusPx * density;
            paint.setColor(enemy.kind == null ? 0xFFFF5722 : enemy.kind.color());
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx - radius * 0.35f, cy - radius * 0.25f, radius * 0.26f, paint);
            canvas.drawCircle(cx + radius * 0.35f, cy - radius * 0.25f, radius * 0.26f, paint);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.25f, radius * 0.12f, paint);
            canvas.drawCircle(cx + radius * 0.42f, cy - radius * 0.25f, radius * 0.12f, paint);
            if (enemy.hp < enemy.maxHp) drawEnemyHealth(canvas, enemy, cx, cy, radius);
        }
    }

    private void drawEnemyHealth(Canvas canvas, Enemy enemy, float cx, float cy, float radius) {
        float width = radius * 2f;
        float height = 3 * density;
        float top = cy - radius - 7 * density;
        float ratio = enemy.hpRatio();
        paint.setColor(0x66000000);
        canvas.drawRect(cx - width / 2f, top, cx + width / 2f, top + height, paint);
        paint.setColor(0xFFFF5252);
        canvas.drawRect(cx - width / 2f, top, cx - width / 2f + width * ratio, top + height, paint);
    }

    /** 土豆本体：与旧版逐条一致的画法，半径改为向引擎取值（视觉与碰撞同源）。 */
    private void drawPlayer(Canvas canvas, BrotatoArena value) {
        float px = value.playerX() * density;
        float py = value.playerY() * density;
        float ps = value.playerRadiusPx() * density;
        paint.setColor(0xFFD4A574);
        canvas.drawCircle(px, py, ps, paint);
        paint.setColor(0xFF8D6E63);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        canvas.drawCircle(px, py, ps, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(px - ps * 0.3f, py - ps * 0.25f, ps * 0.28f, paint);
        canvas.drawCircle(px + ps * 0.3f, py - ps * 0.25f, ps * 0.28f, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(px - ps * 0.24f, py - ps * 0.25f, ps * 0.13f, paint);
        canvas.drawCircle(px + ps * 0.36f, py - ps * 0.25f, ps * 0.13f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        canvas.drawArc(new RectF(px - ps * 0.3f, py + ps * 0.08f, px + ps * 0.3f, py + ps * 0.5f),
                0, 180, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF9E9E9E);
        canvas.drawRect(px + ps, py - ps * 0.16f, px + ps * 2.2f, py + ps * 0.16f, paint);
    }

    private void drawHud(Canvas canvas, BrotatoArena value) {
        float hpBarH = 16 * density;
        float hpBarBottom = viewHeight - 34 * density;
        float hpBarTop = hpBarBottom - hpBarH;
        int hp = value.hp();
        int maxHp = Math.max(1, value.maxHp());
        paint.setColor(0xFF333333);
        canvas.drawRect(16 * density, hpBarTop, viewWidth - 16 * density, hpBarBottom, paint);
        float hpRatio = (float) hp / maxHp;
        paint.setColor(hpRatio > 0.5f ? 0xFF4CAF50 : hpRatio > 0.25f ? 0xFFFF9800 : 0xFFF44336);
        canvas.drawRect(16 * density, hpBarTop,
                16 * density + (viewWidth - 32 * density) * hpRatio, hpBarBottom, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(16 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(hp + "/" + maxHp, viewWidth / 2, hpBarBottom - 2 * density, paint);

        paint.setTextSize(20 * density);
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("分：" + value.score(), 16 * density, 32 * density, paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("波次 " + value.wave() + "/" + value.content().totalWaves(),
                viewWidth - 16 * density, 32 * density, paint);
        if (value.state() != BrotatoArena.State.INTERMISSION) {
            paint.setTextSize(13 * density);
            paint.setColor(0xFFB0B0B0);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("还需清剿 " + (value.remainingSpawns() + value.remainingBosses())
                    + " · 场上 " + value.enemies().size(), viewWidth / 2f, 52 * density, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawIntermissionBanner(Canvas canvas, BrotatoArena value) {
        float band = 40 * density;
        float middle = playfieldHeightPx() / 2f;
        paint.setColor(0xAA000000);
        canvas.drawRect(0, middle - band, viewWidth, middle + band, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(28 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("波次 " + value.wave() + " 即将开始...", viewWidth / 2,
                middle + 10 * density, paint);
        paint.setTextSize(15 * density);
        paint.setColor(0xFFB0B0B0);
        canvas.drawText(value.upcomingWaveTotal() + " 只逼近 · "
                + String.format(java.util.Locale.US, "%.1f", value.intermissionRemainingMs() / 1000f)
                + "s", viewWidth / 2,
                middle + 34 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** 奖励三卡：标题用 {@link Upgrade#displayNameCn()}，点中卡片即 {@code selectUpgrade}。 */
    private void drawRewardCards(Canvas canvas, BrotatoArena value) {
        Upgrade[] cards = value.pendingCards();
        if (cards == null || cards.length == 0) return;
        paint.setColor(0xCC000000);
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(22 * density);
        canvas.drawText("选择一项强化", viewWidth / 2f, viewHeight * 0.22f, paint);
        paint.setTextSize(13 * density);
        paint.setColor(0xFFB0B0B0);
        canvas.drawText("第 " + value.wave() + " 波已清 · 选完开下一波", viewWidth / 2f,
                viewHeight * 0.22f + 24 * density, paint);
        for (int i = 0; i < cards.length; i++) {
            drawCard(canvas, value, cards[i], rewardCardRect(i, cards.length), i);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCard(Canvas canvas, BrotatoArena value, Upgrade upgrade, RectF rect,
                          int index) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1E2740);
        canvas.drawRoundRect(rect, 10 * density, 10 * density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(0xFF5C6BC0);
        canvas.drawRoundRect(rect, 10 * density, 10 * density, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(17 * density);
        canvas.drawText(upgrade.displayNameCn(), rect.centerX(), rect.top + 34 * density, paint);
        paint.setTextSize(15 * density);
        paint.setColor(0xFFB0B0B0);
        canvas.drawText((index + 1) + " 号", rect.centerX(), rect.top + 56 * density, paint);
        paint.setTextSize(13 * density);
        paint.setColor(0xFFE4E6F0);
        drawWrapped(canvas, upgrade.descriptionCn(), rect.centerX(), rect.top + 80 * density,
                rect.width() - 16 * density, 18 * density);
        int owned = value.upgradeCount(upgrade);
        if (owned > 0) {
            paint.setColor(0xFFFFD54F);
            canvas.drawText("已持有 " + owned, rect.centerX(), rect.bottom - 14 * density, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawWrapped(Canvas canvas, String text, float centerX, float baseline,
                             float maxWidth, float lineHeight) {
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

    /** 卡片几何：绘制与命中判定共用同一份算法，避免"看得见点不着"。 */
    private RectF rewardCardRect(int index, int cardCount) {
        float gap = 12 * density;
        float outer = 16 * density;
        float available = viewWidth - outer * 2f - gap * (cardCount - 1);
        float cardW = Math.min(150 * density, available / cardCount);
        float cardH = Math.min(190 * density, viewHeight * 0.4f);
        float blockW = cardW * cardCount + gap * (cardCount - 1);
        float left = (viewWidth - blockW) / 2f + index * (cardW + gap);
        float top = (playfieldHeightPx() - cardH) / 2f + 20 * density;
        return new RectF(left, top, left + cardW, top + cardH);
    }

    /** 终局遮罩（死亡/胜利）。重开由 Fragment 的"重新开始"按钮负责。 */
    private void drawEndOverlay(Canvas canvas, BrotatoArena value, String title, String hint,
                                int accent) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(170, 0, 0, 0));
        canvas.drawRect(0, 0, viewWidth, viewHeight, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(accent);
        paint.setTextSize(34 * density);
        canvas.drawText(title, viewWidth / 2f, viewHeight / 2f - 30 * density, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(18 * density);
        canvas.drawText("得分 " + value.score() + "   波次 " + value.wave()
                + "/" + value.content().totalWaves(), viewWidth / 2f,
                viewHeight / 2f + 10 * density, paint);
        paint.setTextSize(15 * density);
        paint.setColor(0xFFB0B0B0);
        canvas.drawText(hint, viewWidth / 2f, viewHeight / 2f + 50 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ==================== 触摸事件 ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        BrotatoArena value = arena;
        if (!gameRunning || gamePaused || value == null) return true;
        if (value.state() == BrotatoArena.State.REWARD_PENDING) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                pickRewardCard(value, event.getX(), event.getY());
                // 选卡即释放触摸：否则 touchActive 残留，间歇期土豆会自走向上次触点
                value.setTouchTarget(value.playerX(), value.playerY(), false);
            }
            return true;
        }
        boolean active = event.getActionMasked() == MotionEvent.ACTION_DOWN
                || event.getActionMasked() == MotionEvent.ACTION_MOVE;
        value.setTouchTarget(event.getX() / density, event.getY() / density, active);
        return true;
    }

    private void pickRewardCard(BrotatoArena value, float x, float y) {
        Upgrade[] cards = value.pendingCards();
        if (cards == null) return;
        for (int i = 0; i < cards.length; i++) {
            if (rewardCardRect(i, cards.length).contains(x, y)) {
                value.selectUpgrade(i);
                invalidate();
                return;
            }
        }
    }
}
