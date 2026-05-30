package com.gamecenter.app.games.dice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * 骰子对战游戏的自定义视图，负责绘制整个游戏界面并处理用户触摸交互。
 *
 * <p><b>界面布局（从上到下）：</b></p>
 * <ol>
 *   <li>AI骰子区域 — 显示AI的3颗骰子</li>
 *   <li>牌型信息区域 — 局结束时显示双方牌型</li>
 *   <li>玩家骰子区域 — 显示玩家的3颗骰子</li>
 *   <li>操作按钮 — 掷骰子/下一局</li>
 *   <li>比分栏 — 显示累计胜负平记录</li>
 * </ol>
 *
 * <p><b>关键设计决策：</b></p>
 * <ul>
 *   <li>所有绘制逻辑集中在 {@link #onDraw(Canvas)} 中，通过 {@link #invalidate()} 触发重绘</li>
 *   <li>触摸事件仅在按钮区域内生效，其他区域忽略</li>
 *   <li>骰子点数绘制使用预定义的相对坐标位置表，避免复杂的条件判断</li>
 * </ul>
 */
public class DiceView extends View {

    /** 游戏逻辑对象，提供骰子数据和状态查询 */
    private DiceGame game;
    /** 背景画笔（深色背景） */
    private Paint bgPaint;
    /** 玩家骰子填充画笔（白色，带阴影） */
    private Paint dicePaint;
    /** AI骰子填充画笔（浅灰色，带阴影） */
    private Paint aiDicePaint;
    /** 骰子点数画笔（黑色实心圆） */
    private Paint dotPaint;
    /** 标题/比分文本画笔（白色，居中） */
    private Paint textPaint;
    /** 小号文本画笔（用于区域标签） */
    private Paint smallPaint;

    /** 视图宽度（像素），在 {@link #onSizeChanged} 中更新 */
    private float viewWidth;
    /** 视图高度（像素），在 {@link #onSizeChanged} 中更新 */
    private float viewHeight;

    /**
     * 单参数构造方法，供代码动态创建时使用。
     * @param context 上下文
     */
    public DiceView(Context context) { super(context); init(); }

    /**
     * 双参数构造方法，供XML布局文件inflate时使用。
     * @param context 上下文
     * @param attrs XML属性集
     */
    public DiceView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }

    /**
     * 初始化所有画笔。每种画笔配置了抗锯齿、颜色、阴影等属性。
     *
     * <p>画笔用途说明：</p>
     * <ul>
     *   <li>{@code bgPaint} — 深色背景 #1E1E32</li>
     *   <li>{@code dicePaint} — 玩家骰子白色填充，带10px阴影</li>
     *   <li>{@code aiDicePaint} — AI骰子浅灰填充，带8px阴影</li>
     *   <li>{@code dotPaint} — 黑色实心圆点</li>
     *   <li>{@code textPaint} — 白色32sp居中文字</li>
     *   <li>{@code smallPaint} — 浅灰26sp居中文字</li>
     * </ul>
     */
    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#1E1E32"));

        dicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dicePaint.setColor(Color.WHITE);
        dicePaint.setShadowLayer(10, 3, 3, Color.parseColor("#66000000"));

        aiDicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        aiDicePaint.setColor(Color.parseColor("#CCCCCC"));
        aiDicePaint.setShadowLayer(8, 2, 2, Color.parseColor("#66000000"));

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.BLACK);
        dotPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32);
        textPaint.setTextAlign(Paint.Align.CENTER);

        smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        smallPaint.setColor(Color.parseColor("#CCCCCC"));
        smallPaint.setTextSize(26);
        smallPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * 设置游戏逻辑对象。视图通过此对象获取骰子数据和游戏状态。
     * @param game 骰子游戏逻辑实例
     */
    public void setGame(DiceGame game) { this.game = game; }

    /**
     * 视图尺寸变化时回调，记录当前宽高供绘制计算使用。
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
    }

    /**
     * 绘制整个游戏界面。按从上到下的顺序绘制各区域：
     * <ol>
     *   <li>AI骰子区域（上方，红色标签）</li>
     *   <li>牌型信息（局结束时显示）</li>
     *   <li>玩家骰子区域（下方，绿色标签）</li>
     *   <li>操作按钮（掷骰子或下一局）</li>
     *   <li>比分栏</li>
     * </ol>
     *
     * @param canvas 画布
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;
        canvas.drawColor(Color.parseColor("#1E1E32"));

        // 骰子尺寸根据视图大小自适应，取宽高较小值的16%
        float diceSize = Math.min(viewWidth, viewHeight) * 0.16f;
        // 骰子之间的间距为骰子尺寸的35%
        float spacing = diceSize * 0.35f;

        // AI骰子（上方）
        smallPaint.setColor(Color.parseColor("#FF8A80"));
        canvas.drawText("AI的骰子", viewWidth / 2, viewHeight * 0.08f, smallPaint);
        drawDiceRow(canvas, game.getAiDice(), diceSize, spacing, viewHeight * 0.18f, aiDicePaint, false);

        // 局结束时显示双方牌型
        if (game.isRoundOver()) {
            String type = handTypeName(DiceGame.getHandType(game.getPlayerDice()));
            textPaint.setColor(Color.parseColor("#4CAF50"));
            canvas.drawText("你的牌型: " + type, viewWidth / 2, viewHeight * 0.38f, textPaint);

            String aiType = handTypeName(DiceGame.getHandType(game.getAiDice()));
            textPaint.setColor(Color.parseColor("#FF8A80"));
            canvas.drawText("AI牌型: " + aiType, viewWidth / 2, viewHeight * 0.44f, textPaint);
        }

        // 你的骰子（下方）
        smallPaint.setColor(Color.parseColor("#4CAF50"));
        canvas.drawText("你的骰子", viewWidth / 2, viewHeight * 0.58f, smallPaint);
        drawDiceRow(canvas, game.getPlayerDice(), diceSize, spacing, viewHeight * 0.68f, dicePaint, true);

        // 按钮区域 — 根据游戏状态显示不同按钮
        float btnY = viewHeight * 0.82f;
        float btnW = viewWidth * 0.50f;
        float btnH = 56;
        float btnX = (viewWidth - btnW) / 2;

        if (!game.isRoundOver()) {
            // 游戏进行中 — 显示"掷骰子"按钮（橙色）
            Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnPaint.setColor(Color.parseColor("#FF9800"));
            btnPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(btnX, btnY, btnX + btnW, btnY + btnH, 16, 16, btnPaint);
            Paint btnText = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnText.setColor(Color.WHITE);
            btnText.setTextSize(28);
            btnText.setTextAlign(Paint.Align.CENTER);
            btnText.setFakeBoldText(true);
            // 计算剩余掷骰次数
            int left = game.getMaxRerolls() - game.getPlayerRolls();
            canvas.drawText("🎲 掷骰子 (" + left + "次机会)", viewWidth / 2, btnY + btnH / 2 + 10, btnText);
        } else {
            // 局已结束 — 显示"下一局"按钮（绿色）
            Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnPaint.setColor(Color.parseColor("#4CAF50"));
            btnPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(btnX, btnY, btnX + btnW, btnY + btnH, 16, 16, btnPaint);
            Paint btnText = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnText.setColor(Color.WHITE);
            btnText.setTextSize(28);
            btnText.setTextAlign(Paint.Align.CENTER);
            btnText.setFakeBoldText(true);
            canvas.drawText(game.getResultText() + "  下一局 ▶", viewWidth / 2, btnY + btnH / 2 + 10, btnText);
        }

        // 比分栏
        float scoreY = btnY + btnH + 32;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30);
        canvas.drawText("你 " + game.getPlayerWins() + " : " + game.getAiWins() + " AI  (平" + game.getDraws() + ")",
                viewWidth / 2, scoreY, textPaint);
    }

    /**
     * 绘制一行骰子（3颗），水平居中排列。
     *
     * @param canvas  画布
     * @param dice    骰子点数数组（长度为3）
     * @param size    单颗骰子的边长（像素）
     * @param sp      骰子之间的间距（像素）
     * @param centerY 骰子行的垂直中心Y坐标
     * @param fillPaint 骰子背景填充画笔
     * @param active  是否为活跃状态（当前实现中玩家和AI骰子均显示点数，此参数预留扩展）
     */
    private void drawDiceRow(Canvas canvas, int[] dice, float size, float sp, float centerY, Paint fillPaint, boolean active) {
        int n = dice.length;
        // 计算整行骰子的总宽度（含间距）
        float total = n * size + (n - 1) * sp;
        // 居中偏移
        float startX = (viewWidth - total) / 2;
        float startY = centerY - size / 2;

        for (int i = 0; i < n; i++) {
            float dx = startX + i * (size + sp);
            // 绘制圆角矩形骰子背景，圆角半径为边长的10%
            canvas.drawRoundRect(dx, startY, dx + size, startY + size, size * 0.1f, size * 0.1f, fillPaint);
            // 无论active与否，AI骰子同样显示点数
            if (active) drawDots(canvas, dice[i], dx, startY, size);
            else drawDots(canvas, dice[i], dx, startY, size);
        }
    }

    /**
     * 在指定位置绘制骰子上的点数（1-6）。
     *
     * <p>使用预定义的相对坐标位置表 {@code positions}，每个值对应一组
     * {水平偏移, 垂直偏移} 坐标对，其中 -1/0/1 分别表示左/中/右和上/中/下。
     * 实际绘制位置 = 骰子中心 + 偏移量 × off。</p>
     *
     * @param canvas 画布
     * @param value  骰子点数（1-6），超出范围则不绘制
     * @param x      骰子左上角X坐标
     * @param y      骰子左上角Y坐标
     * @param size   骰子边长
     */
    private void drawDots(Canvas canvas, int value, float x, float y, float size) {
        // 点的半径为骰子边长的8%
        float r = size * 0.08f;
        // 骰子中心坐标
        float cx = x + size / 2;
        float cy = y + size / 2;
        // 点的偏移基准距离为骰子边长的22%
        float off = size * 0.22f;

        // 点数位置表：索引0为空（骰子无0点），1-6对应各点数的位置
        // 每个位置为 {水平偏移, 垂直偏移}，-1=左/上，0=中，1=右/下
        int[][][] positions = {
            {}, // 0 — 不存在
            {{0,0}},                                          // 1 — 中心一点
            {{-1,-1},{1,1}},                                  // 2 — 左上+右下对角
            {{-1,-1},{0,0},{1,1}},                            // 3 — 对角线三点
            {{-1,-1},{1,-1},{-1,1},{1,1}},                    // 4 — 四角
            {{-1,-1},{1,-1},{0,0},{-1,1},{1,1}},              // 5 — 四角+中心
            {{-1,-1},{1,-1},{-1,1},{1,1},{-1,0},{1,0}},       // 6 — 四角+左右中
        };
        // 边界保护：点数不在1-6范围内则跳过
        if (value < 1 || value > 6) return;
        for (int[] p : positions[value]) {
            canvas.drawCircle(cx + p[0] * off, cy + p[1] * off, r, dotPaint);
        }
    }

    /**
     * 将牌型枚举转换为中文显示名称。
     *
     * @param t 牌型枚举值
     * @return 中文牌型名称，如 "豹子(三同)"、"顺子"、"对子"、"散牌"
     */
    private String handTypeName(DiceGame.HandType t) {
        switch (t) {
            case THREE_OF_A_KIND: return "豹子(三同)";
            case STRAIGHT: return "顺子";
            case PAIR: return "对子";
            default: return "散牌";
        }
    }

    /**
     * 处理触摸事件。仅在按钮区域内的点击生效：
     * <ul>
     *   <li>游戏进行中 → 调用 {@link DiceGame#rollPlayer()} 掷骰子</li>
     *   <li>局已结束 → 调用 {@link DiceGame#nextRound()} 开始下一局</li>
     * </ul>
     *
     * <p>按钮触摸区域比视觉区域略大（四周各扩展20px/10px），
     * 提升小屏幕设备上的操作容错性。</p>
     *
     * @param event 触摸事件
     * @return 始终返回true，表示消费所有触摸事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 仅处理按下事件，忽略移动和抬起
        if (event.getAction() != MotionEvent.ACTION_DOWN || game == null) return true;

        // 按钮区域坐标（与onDraw中一致）
        float btnY = viewHeight * 0.82f;
        float btnW = viewWidth * 0.50f;
        float btnH = 56;
        float btnX = (viewWidth - btnW) / 2;

        float x = event.getX(), y = event.getY();
        // 判断触摸点是否在按钮扩展区域内
        if (x >= btnX - 20 && x <= btnX + btnW + 20 && y >= btnY - 10 && y <= btnY + btnH + 10) {
            if (game.isRoundOver()) {
                game.nextRound();
            } else {
                game.rollPlayer();
            }
            invalidate();
            performClick();
        }
        return true;
    }

    /**
     * 辅助方法，满足无障碍访问要求。委托给父类实现。
     */
    @Override
    public boolean performClick() { super.performClick(); return true; }
}
