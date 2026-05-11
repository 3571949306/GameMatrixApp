package com.gamecenter.app.games.guess;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

public class GuessView extends View {

    private GuessGame game;
    private Paint bgPaint;
    private Paint btnPaint;
    private Paint btnTextPaint;
    private Paint inputPaint;
    private Paint hintPaint;
    private Paint titlePaint;

    private float viewWidth;
    private float viewHeight;

    private StringBuilder inputBuffer;
    private static final int MAX_DIGITS = 3;

    public GuessView(Context context) {
        super(context);
        init();
    }

    public GuessView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#1E1E32"));

        btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnPaint.setColor(Color.parseColor("#3949AB"));
        btnPaint.setStyle(Paint.Style.FILL);

        btnTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        btnTextPaint.setColor(Color.WHITE);
        btnTextPaint.setTextSize(48);
        btnTextPaint.setTextAlign(Paint.Align.CENTER);

        inputPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inputPaint.setColor(Color.WHITE);
        inputPaint.setTextSize(60);
        inputPaint.setTextAlign(Paint.Align.CENTER);
        inputPaint.setFakeBoldText(true);

        hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hintPaint.setColor(Color.parseColor("#FFD700"));
        hintPaint.setTextSize(40);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setFakeBoldText(true);

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.parseColor("#AAAAFF"));
        titlePaint.setTextSize(32);
        titlePaint.setTextAlign(Paint.Align.CENTER);

        inputBuffer = new StringBuilder();
    }

    public void setGame(GuessGame game) {
        this.game = game;
    }

    public void resetInput() {
        inputBuffer.setLength(0);
    }

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

        float topY = viewHeight * 0.08f + 20;
        canvas.drawText("猜一个 " + game.getMinRange() + "-" + game.getMaxRange() + " 的数字",
                viewWidth / 2, topY, titlePaint);

        float inputY = topY + 80;
        String display = inputBuffer.length() == 0 ? "___" : inputBuffer.toString();
        canvas.drawText(display, viewWidth / 2, inputY, inputPaint);

        if (game.getLastHint().length() > 0) {
            float hintY = inputY + 70;
            if (game.isGameOver()) {
                hintPaint.setColor(Color.parseColor("#4CAF50"));
            } else if (game.getLastHint().contains("大")) {
                hintPaint.setColor(Color.parseColor("#FF9800"));
            } else {
                hintPaint.setColor(Color.parseColor("#2196F3"));
            }
            canvas.drawText(game.getLastHint(), viewWidth / 2, hintY, hintPaint);
        }

        Paint attemptPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        attemptPaint.setColor(Color.parseColor("#AAAAAA"));
        attemptPaint.setTextSize(28);
        attemptPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("已猜 " + game.getAttempts() + " 次", viewWidth / 2, viewHeight * 0.92f, attemptPaint);

        float keypadY = viewHeight * 0.4f;
        float keySize = viewWidth * 0.22f;
        float keySpacing = keySize * 0.15f;
        float totalW = 3 * keySize + 2 * keySpacing;
        float padOffsetX = (viewWidth - totalW) / 2;

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                float left = padOffsetX + col * (keySize + keySpacing);
                float top = keypadY + row * (keySize + keySpacing);
                float right = left + keySize;
                float bottom = top + keySize;

                String label;
                if (row < 3) {
                    int num = row * 3 + col + 1;
                    label = String.valueOf(num);
                } else if (col == 0) {
                    label = "清除";
                } else if (col == 1) {
                    label = "0";
                } else {
                    label = "确定";
                }

                if (label.equals("确定")) {
                    btnPaint.setColor(Color.parseColor("#4CAF50"));
                } else if (label.equals("清除")) {
                    btnPaint.setColor(Color.parseColor("#E53935"));
                } else {
                    btnPaint.setColor(Color.parseColor("#3949AB"));
                }

                canvas.drawRoundRect(left, top, right, bottom, 16, 16, btnPaint);
                canvas.drawText(label, left + keySize / 2, top + keySize / 2 + 16, btnTextPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (game == null) return true;
            if (game.isGameOver()) {
                game.reset();
                resetInput();
                invalidate();
                performClick();
                return true;
            }

            float x = event.getX();
            float y = event.getY();

            float keypadY = viewHeight * 0.4f;
            float keySize = viewWidth * 0.22f;
            float keySpacing = keySize * 0.15f;
            float totalW = 3 * keySize + 2 * keySpacing;
            float padOffsetX = (viewWidth - totalW) / 2;

            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 3; col++) {
                    float left = padOffsetX + col * (keySize + keySpacing);
                    float top = keypadY + row * (keySize + keySpacing);
                    float right = left + keySize;
                    float bottom = top + keySize;

                    if (x >= left && x <= right && y >= top && y <= bottom) {
                        handleKeyPress(row, col);
                        performClick();
                        return true;
                    }
                }
            }
        }
        return true;
    }

    private void handleKeyPress(int row, int col) {
        if (row < 3) {
            int num = row * 3 + col + 1;
            if (inputBuffer.length() < MAX_DIGITS) {
                inputBuffer.append(num);
                invalidate();
            }
        } else if (col == 0) {
            if (inputBuffer.length() > 0) {
                inputBuffer.setLength(inputBuffer.length() - 1);
            }
            invalidate();
        } else if (col == 1) {
            if (inputBuffer.length() < MAX_DIGITS) {
                inputBuffer.append('0');
                invalidate();
            }
        } else {
            if (inputBuffer.length() > 0) {
                int guess = Integer.parseInt(inputBuffer.toString());
                game.makeGuess(guess);
                inputBuffer.setLength(0);
                invalidate();
            }
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
