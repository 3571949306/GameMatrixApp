package com.gamecenter.app.games.game2048;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;

public class Game2048View extends View {

    private Game2048Game game;
    private Paint paint;
    private int[] tileColors;
    private int[] textColors;

    public Game2048View(Context context) {
        super(context);
        init();
    }

    public Game2048View(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tileColors = new int[]{
            0xFFE0D5C5, 0xFFEEE4DA, 0xFFFFDFB8, 0xFFFFCC66,
            0xFFFFAA33, 0xFFFF8833, 0xFFFF6633, 0xFFFF4400,
            0xFF88CCFF, 0xFF55AADD, 0xFF2288BB, 0xFF115599,
            0xFFDDAA77, 0xFFCC8844, 0xFFBB6622, 0xFF4A3728
        };
        textColors = new int[]{
            0xFF5D4E37, 0xFF5D4E37, 0xFF5D4E37, 0xFF5D4E37,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF
        };
    }

    public void setGame(Game2048Game game) {
        this.game = game;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (game == null) return;

        int width = getWidth();
        int height = getHeight();
        int boardSize = Math.min(width, height) - 32;
        int tileSize = boardSize / 4;
        int offsetX = (width - boardSize) / 2;
        int offsetY = (height - boardSize) / 2;

        paint.setColor(0xFFBBADA0);
        canvas.drawRoundRect(new RectF(offsetX, offsetY, offsetX + boardSize, offsetY + boardSize), 10, 10, paint);

        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int left = offsetX + x * tileSize + 4;
                int top = offsetY + y * tileSize + 4;
                int right = left + tileSize - 8;
                int bottom = top + tileSize - 8;

                int value = game.getTile(x, y);
                int colorIndex = 0;
                if (value > 0) {
                    colorIndex = (int)(Math.log(value) / Math.log(2));
                    if (colorIndex >= tileColors.length) colorIndex = tileColors.length - 1;
                }

                paint.setColor(tileColors[colorIndex]);
                canvas.drawRoundRect(new RectF(left, top, right, bottom), 5, 5, paint);

                if (value > 0) {
                    paint.setColor(textColors[colorIndex]);
                    paint.setTextSize(tileSize / 3);
                    paint.setTextAlign(Paint.Align.CENTER);
                    String text = String.valueOf(value);
                    if (value >= 1000) paint.setTextSize(tileSize / 4);
                    canvas.drawText(text, (left + right) / 2f, (top + bottom) / 2f - (paint.ascent() + paint.descent()) / 2f, paint);
                }
            }
        }

        if (game.isGameOver()) {
            paint.setColor(0xCC000000);
            canvas.drawRect(0, 0, width, height, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(48);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("游戏结束!", width / 2f, height / 2f - 30, paint);
            paint.setTextSize(24);
            canvas.drawText("最终分数: " + game.getScore(), width / 2f, height / 2f + 30, paint);
        }
    }
}
