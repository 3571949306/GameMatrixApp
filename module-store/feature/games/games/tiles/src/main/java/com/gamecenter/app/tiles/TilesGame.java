package com.gamecenter.app.tiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 麻将连连看核心逻辑模型（独立状态引擎，无 UI 依赖）。
 */
public class TilesGame {

    public static final int DIFFICULTY_CASUAL = 0;   // 4x4, 8 对
    public static final int DIFFICULTY_CLASSIC = 1;  // 4x6, 12 对
    public static final int DIFFICULTY_MASTER = 2;   // 6x6, 18 对

    public static final int RESULT_NONE = 0;
    public static final int RESULT_FIRST_SELECTED = 1;
    public static final int RESULT_MATCH = 2;
    public static final int RESULT_MISMATCH = 3;

    public static final String[] SYMBOLS = {
            "一万", "二万", "三万", "四万", "五万", "六万", "七万", "八万", "九万",
            "東", "南", "西", "北", "中", "發", "白", "一筒", "一索"
    };

    private int difficulty = DIFFICULTY_CLASSIC;
    private int rows = 4;
    private int cols = 6;
    private int pairCount = 12;

    private int[] tileValues;
    private boolean[] tileMatched;
    private boolean[] tileRevealed;

    private int firstSelectedIndex = -1;
    private int secondSelectedIndex = -1;
    private int moveCount = 0;
    private int matchedPairs = 0;

    public TilesGame() {
        setDifficulty(DIFFICULTY_CLASSIC);
    }

    public void setDifficulty(int diff) {
        if (diff < DIFFICULTY_CASUAL || diff > DIFFICULTY_MASTER) {
            diff = DIFFICULTY_CLASSIC;
        }
        this.difficulty = diff;
        switch (diff) {
            case DIFFICULTY_CASUAL:
                this.rows = 4;
                this.cols = 4;
                this.pairCount = 8;
                break;
            case DIFFICULTY_MASTER:
                this.rows = 6;
                this.cols = 6;
                this.pairCount = 18;
                break;
            case DIFFICULTY_CLASSIC:
            default:
                this.rows = 4;
                this.cols = 6;
                this.pairCount = 12;
                break;
        }
        startNewGame();
    }

    public void startNewGame() {
        int total = rows * cols;
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < pairCount; i++) {
            int symIdx = i % SYMBOLS.length;
            values.add(symIdx);
            values.add(symIdx);
        }
        Collections.shuffle(values);

        tileValues = new int[total];
        tileMatched = new boolean[total];
        tileRevealed = new boolean[total];
        for (int i = 0; i < total; i++) {
            tileValues[i] = values.get(i);
        }

        firstSelectedIndex = -1;
        secondSelectedIndex = -1;
        moveCount = 0;
        matchedPairs = 0;
    }

    public int onTileClick(int index) {
        if (index < 0 || index >= getTotalTiles()) return RESULT_NONE;
        if (tileMatched[index] || tileRevealed[index]) return RESULT_NONE;
        if (index == firstSelectedIndex) return RESULT_NONE;

        tileRevealed[index] = true;

        if (firstSelectedIndex == -1) {
            firstSelectedIndex = index;
            return RESULT_FIRST_SELECTED;
        } else {
            secondSelectedIndex = index;
            moveCount++;

            if (tileValues[firstSelectedIndex] == tileValues[secondSelectedIndex]) {
                tileMatched[firstSelectedIndex] = true;
                tileMatched[secondSelectedIndex] = true;
                matchedPairs++;
                return RESULT_MATCH;
            } else {
                return RESULT_MISMATCH;
            }
        }
    }

    public void resolveMismatch() {
        if (firstSelectedIndex != -1) {
            tileRevealed[firstSelectedIndex] = false;
        }
        if (secondSelectedIndex != -1) {
            tileRevealed[secondSelectedIndex] = false;
        }
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;
    }

    public void resolveMatch() {
        firstSelectedIndex = -1;
        secondSelectedIndex = -1;
    }

    /** 寻找一对未消除相同牌的索引以供提示。找不到返回 null。 */
    public int[] findHintPair() {
        for (int i = 0; i < getTotalTiles(); i++) {
            if (tileMatched[i]) continue;
            for (int j = i + 1; j < getTotalTiles(); j++) {
                if (tileMatched[j]) continue;
                if (tileValues[i] == tileValues[j]) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    public boolean isGameComplete() {
        return matchedPairs >= pairCount;
    }

    public int computeScore(long elapsedSec) {
        int base = Math.max(300 - moveCount * 8 - (int) (elapsedSec * 2), 50);
        int multiplier = difficulty == DIFFICULTY_MASTER ? 3 : (difficulty == DIFFICULTY_CLASSIC ? 2 : 1);
        return base * multiplier;
    }

    public int getDifficulty() { return difficulty; }
    public int getRows() { return rows; }
    public int getCols() { return cols; }
    public int getTotalTiles() { return rows * cols; }
    public int getPairCount() { return pairCount; }
    public int getRemainingPairs() { return pairCount - matchedPairs; }
    public int getMatchedPairs() { return matchedPairs; }
    public int getMoveCount() { return moveCount; }
    public int getFirstSelectedIndex() { return firstSelectedIndex; }
    public int getSecondSelectedIndex() { return secondSelectedIndex; }
    public boolean isTileMatched(int index) { return tileMatched[index]; }
    public boolean isTileRevealed(int index) { return tileRevealed[index]; }
    public String getTileSymbol(int index) { return SYMBOLS[tileValues[index]]; }
    public int getTileValue(int index) { return tileValues[index]; }
}
