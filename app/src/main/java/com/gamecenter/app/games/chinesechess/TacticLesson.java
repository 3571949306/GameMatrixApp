package com.gamecenter.app.games.chinesechess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 战术课程。
 *
 * <p>定义一节战术学习课程，包含多个练习题（棋盘状态）、对应的解答和解释。
 * 棋盘约定：10×9 int 矩阵，正值=红子、负值=黑子，绝对值 1..7 对应 将/仕/相/马/车/炮/兵。</p>
 */
public class TacticLesson {

    private final String lessonId;
    private final String title;
    private final String description;
    private final TacticalPattern pattern;
    private final List<int[][]> puzzles;
    private final List<int[]> solutions;
    private final List<String> explanations;
    private final int difficulty;

    public TacticLesson(String lessonId, String title, String description,
                        TacticalPattern pattern, List<int[][]> puzzles,
                        List<int[]> solutions, List<String> explanations,
                        int difficulty) {
        this.lessonId = lessonId;
        this.title = title;
        this.description = description;
        this.pattern = pattern;
        this.puzzles = Collections.unmodifiableList(new ArrayList<>(puzzles));
        this.solutions = Collections.unmodifiableList(new ArrayList<>(solutions));
        this.explanations = Collections.unmodifiableList(new ArrayList<>(explanations));
        this.difficulty = difficulty;
    }

    public String getLessonId() {
        return lessonId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TacticalPattern getPattern() {
        return pattern;
    }

    public List<int[][]> getPuzzles() {
        return puzzles;
    }

    public List<int[]> getSolutions() {
        return solutions;
    }

    public List<String> getExplanations() {
        return explanations;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public int getPuzzleCount() {
        return puzzles.size();
    }

    /**
     * 获取指定索引的练习题棋盘。
     *
     * @param index 练习题索引
     * @return 棋盘状态，索引越界返回 null
     */
    public int[][] getPuzzle(int index) {
        if (index < 0 || index >= puzzles.size()) return null;
        return puzzles.get(index);
    }

    /**
     * 获取指定索引的解答走法。
     *
     * @param index 练习题索引
     * @return 走法 [fromR, fromC, toR, toC]，索引越界返回 null
     */
    public int[] getSolution(int index) {
        if (index < 0 || index >= solutions.size()) return null;
        return solutions.get(index);
    }

    /**
     * 获取指定索引的解释文本。
     *
     * @param index 练习题索引
     * @return 解释文本，索引越界返回 null
     */
    public String getExplanation(int index) {
        if (index < 0 || index >= explanations.size()) return null;
        return explanations.get(index);
    }
}
