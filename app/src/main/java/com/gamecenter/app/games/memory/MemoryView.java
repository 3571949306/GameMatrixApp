package com.gamecenter.app.games.memory;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class MemoryView extends View {

    private MemoryGame game;
    private Paint cardBackPaint;
    private Paint cardFrontPaint;
    private Paint cardMatchedPaint;
    private Paint textPaint;
    private Paint emojiPaint;
    private Paint qPaint;
    private Paint infoPaint;

    private float viewWidth;
    private float viewHeight;
    private float cardSize;
    private float cardSpacing;
    private float offsetX;
    private float offsetY;

    private static final String[] EMOJIS = {"🐶","🐱","🐼","🐨","🐰","🦊","🐸","🐵","🦁","🐯","🐮","🐷","🐭","🐹","🐻","🐔"};

    public MemoryView(Context context) {
        super(context);
        init();
    }

    public MemoryView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        cardBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBackPaint.setColor(Color.parseColor("#1565C0"));
        cardBackPaint.setStyle(Paint.Style.FILL);

        cardFrontPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardFrontPaint.setColor(Color.parseColor("#FFF9C4"));
        cardFrontPaint.setStyle(Paint.Style.FILL);

        cardMatchedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardMatchedPaint.setColor(Color.parseColor("#A5D6A7"));
        cardMatchedPaint.setStyle(Paint.Style.FILL);

        qPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        qPaint.setColor(Color.WHITE);
        qPaint.setTextAlign(Paint.Align.CENTER);
        qPaint.setFakeBoldText(true);

        emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emojiPaint.setTextAlign(Paint.Align.CENTER);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        infoPaint.setColor(Color.parseColor("#AAAAAA"));
        infoPaint.setTextSize(30);
        infoPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(MemoryGame game) {
        this.game = game;
    }

    private OnCardFlipListener flipListener;

    public interface OnCardFlipListener {
        void onCardFlipped();
    }

    public void setOnCardFlipListener(OnCardFlipListener l) {
        this.flipListener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        if (game == null) return;
        recalcLayout();
    }

    private void recalcLayout() {
        int cols = MemoryGame.COLS;
        int rows = MemoryGame.ROWS;
        float margin = 16;
        float cardW = (viewWidth - margin * 2) / cols;
        float cardH = (viewHeight * 0.82f - margin * 2) / rows;
        cardSize = Math.min(cardW, cardH) * 0.94f;
        cardSpacing = cardSize * 0.06f;
        float totalW = cols * (cardSize + cardSpacing) - cardSpacing;
        float totalH = rows * (cardSize + cardSpacing) - cardSpacing;
        offsetX = (viewWidth - totalW) / 2;
        offsetY = (viewHeight * 0.82f - totalH) / 2 + 8;

        float emojiSize = cardSize * 0.62f;
        emojiPaint.setTextSize(emojiSize);
        qPaint.setTextSize(cardSize * 0.35f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (game == null) return;

        canvas.drawColor(Color.parseColor("#1A1A2E"));

        int cols = MemoryGame.COLS;
        int rows = MemoryGame.ROWS;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                float left = offsetX + x * (cardSize + cardSpacing);
                float top = offsetY + y * (cardSize + cardSpacing);
                float right = left + cardSize;

                if (game.isRevealed(x, y)) {
                    Paint bg = game.isMatched(x, y) ? cardMatchedPaint : cardFrontPaint;
                    canvas.drawRoundRect(left, top, right, top + cardSize, 12, 12, bg);

                    int val = game.getCardValue(x, y);
                    String emoji = EMOJIS[val % EMOJIS.length];
                    float cx = left + cardSize / 2;
                    float cy = top + cardSize / 2 - (emojiPaint.descent() + emojiPaint.ascent()) / 2;
                    canvas.drawText(emoji, cx, cy, emojiPaint);
                } else {
                    canvas.drawRoundRect(left, top, right, top + cardSize, 12, 12, cardBackPaint);
                    float cx = left + cardSize / 2;
                    float cy = top + cardSize / 2 - (qPaint.descent() + qPaint.ascent()) / 2;
                    canvas.drawText("?", cx, cy, qPaint);
                }
            }
        }

        float infoY = offsetY + rows * (cardSize + cardSpacing) + 32;
        canvas.drawText("已匹配: " + game.getMatched() + "/" + MemoryGame.PAIRS,
                viewWidth / 2, infoY, textPaint);

        if (game.isGameOver()) {
            Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            overPaint.setColor(Color.parseColor("#4CAF50"));
            overPaint.setTextSize(44);
            overPaint.setTextAlign(Paint.Align.CENTER);
            overPaint.setFakeBoldText(true);
            canvas.drawText("🎉 全部找到!", viewWidth / 2, infoY + 42, overPaint);
            infoPaint.setTextSize(32);
            canvas.drawText("得分: " + game.getScore() + "  点击重新开始", viewWidth / 2, infoY + 78, infoPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
        if (game == null) return true;

        if (game.isWaiting()) return true;

        if (game.isGameOver()) {
            game.reset();
            recalcLayout();
            invalidate();
            performClick();
            if (flipListener != null) flipListener.onCardFlipped();
            return true;
        }

        float x = event.getX() - offsetX;
        float y = event.getY() - offsetY;
        int col = (int) (x / (cardSize + cardSpacing));
        int row = (int) (y / (cardSize + cardSpacing));

        if (col < 0 || col >= MemoryGame.COLS || row < 0 || row >= MemoryGame.ROWS) return true;

        if (!game.canFlip(col, row)) return true;

        game.flipCard(col, row);
        invalidate();

        if (game.isWaiting()) {
            postDelayed(() -> {
                game.hideMismatch();
                invalidate();
            }, 800);
        } else if (game.lastMatchSuccessful()) {
            postDelayed(() -> {
                game.confirmMatch();
                invalidate();
                if (flipListener != null) flipListener.onCardFlipped();
            }, 400);
        }

        if (!game.isWaiting() && !game.lastMatchSuccessful()) {
            if (flipListener != null) flipListener.onCardFlipped();
        }

        performClick();
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
