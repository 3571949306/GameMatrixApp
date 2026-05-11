package com.gamecenter.app.games.dice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class DiceView extends View {

    private DiceGame game;
    private Paint bgPaint;
    private Paint dicePaint;
    private Paint aiDicePaint;
    private Paint dotPaint;
    private Paint textPaint;
    private Paint smallPaint;

    private float viewWidth;
    private float viewHeight;

    public DiceView(Context context) { super(context); init(); }
    public DiceView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }

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

    public void setGame(DiceGame game) { this.game = game; }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;
        canvas.drawColor(Color.parseColor("#1E1E32"));

        float diceSize = Math.min(viewWidth, viewHeight) * 0.16f;
        float spacing = diceSize * 0.35f;

        // AI骰子（上方）
        smallPaint.setColor(Color.parseColor("#FF8A80"));
        canvas.drawText("AI的骰子", viewWidth / 2, viewHeight * 0.08f, smallPaint);
        drawDiceRow(canvas, game.getAiDice(), diceSize, spacing, viewHeight * 0.18f, aiDicePaint, false);

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

        // 按钮区域
        float btnY = viewHeight * 0.82f;
        float btnW = viewWidth * 0.50f;
        float btnH = 56;
        float btnX = (viewWidth - btnW) / 2;

        if (!game.isRoundOver()) {
            Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnPaint.setColor(Color.parseColor("#FF9800"));
            btnPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(btnX, btnY, btnX + btnW, btnY + btnH, 16, 16, btnPaint);
            Paint btnText = new Paint(Paint.ANTI_ALIAS_FLAG);
            btnText.setColor(Color.WHITE);
            btnText.setTextSize(28);
            btnText.setTextAlign(Paint.Align.CENTER);
            btnText.setFakeBoldText(true);
            int left = game.getMaxRerolls() - game.getPlayerRolls();
            canvas.drawText("🎲 掷骰子 (" + left + "次机会)", viewWidth / 2, btnY + btnH / 2 + 10, btnText);
        } else {
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

        // 比分
        float scoreY = btnY + btnH + 32;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(30);
        canvas.drawText("你 " + game.getPlayerWins() + " : " + game.getAiWins() + " AI  (平" + game.getDraws() + ")",
                viewWidth / 2, scoreY, textPaint);
    }

    private void drawDiceRow(Canvas canvas, int[] dice, float size, float sp, float centerY, Paint fillPaint, boolean active) {
        int n = dice.length;
        float total = n * size + (n - 1) * sp;
        float startX = (viewWidth - total) / 2;
        float startY = centerY - size / 2;

        for (int i = 0; i < n; i++) {
            float dx = startX + i * (size + sp);
            canvas.drawRoundRect(dx, startY, dx + size, startY + size, size * 0.1f, size * 0.1f, fillPaint);
            if (active) drawDots(canvas, dice[i], dx, startY, size);
            else drawDots(canvas, dice[i], dx, startY, size); // AI也显示
        }
    }

    private void drawDots(Canvas canvas, int value, float x, float y, float size) {
        float r = size * 0.08f;
        float cx = x + size / 2;
        float cy = y + size / 2;
        float off = size * 0.22f;

        int[][][] positions = {
            {}, // 0
            {{0,0}},
            {{-1,-1},{1,1}},
            {{-1,-1},{0,0},{1,1}},
            {{-1,-1},{1,-1},{-1,1},{1,1}},
            {{-1,-1},{1,-1},{0,0},{-1,1},{1,1}},
            {{-1,-1},{1,-1},{-1,1},{1,1},{-1,0},{1,0}},
        };
        if (value < 1 || value > 6) return;
        for (int[] p : positions[value]) {
            canvas.drawCircle(cx + p[0] * off, cy + p[1] * off, r, dotPaint);
        }
    }

    private String handTypeName(DiceGame.HandType t) {
        switch (t) {
            case THREE_OF_A_KIND: return "豹子(三同)";
            case STRAIGHT: return "顺子";
            case PAIR: return "对子";
            default: return "散牌";
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN || game == null) return true;

        float btnY = viewHeight * 0.82f;
        float btnW = viewWidth * 0.50f;
        float btnH = 56;
        float btnX = (viewWidth - btnW) / 2;

        float x = event.getX(), y = event.getY();
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

    @Override
    public boolean performClick() { super.performClick(); return true; }
}
