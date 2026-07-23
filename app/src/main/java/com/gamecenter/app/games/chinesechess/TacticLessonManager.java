package com.gamecenter.app.games.chinesechess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 战术课程管理器。
 *
 * <p>管理所有战术课程和用户学习进度，提供课程查询、答案验证和进度追踪功能。</p>
 */
public class TacticLessonManager {

    private final List<TacticLesson> lessons;
    private final Map<String, LessonProgress> progressMap;
    private final Map<String, Integer> lessonPuzzleIndex;

    public TacticLessonManager() {
        this.lessons = new ArrayList<>();
        this.progressMap = new HashMap<>();
        this.lessonPuzzleIndex = new HashMap<>();
        loadDefaultLessons();
    }

    public List<TacticLesson> getLessons() {
        return lessons;
    }

    /**
     * 按战术类型筛选课程。
     */
    public List<TacticLesson> getLessonsByPattern(TacticalPattern pattern) {
        List<TacticLesson> result = new ArrayList<>();
        for (TacticLesson lesson : lessons) {
            if (lesson.getPattern() == pattern) {
                result.add(lesson);
            }
        }
        return result;
    }

    /**
     * 根据课程ID获取课程。
     */
    public TacticLesson getLesson(String lessonId) {
        for (TacticLesson lesson : lessons) {
            if (lesson.getLessonId().equals(lessonId)) {
                return lesson;
            }
        }
        return null;
    }

    /**
     * 验证用户对当前练习题的答案。
     *
     * @param lessonId 课程ID
     * @param move     用户走法 [fromR, fromC, toR, toC]
     * @return 答案是否正确
     */
    public boolean checkAnswer(String lessonId, int[] move) {
        TacticLesson lesson = getLesson(lessonId);
        if (lesson == null) return false;

        int puzzleIdx = getCurrentPuzzleIndex(lessonId);
        if (puzzleIdx >= lesson.getPuzzleCount()) return false;

        int[] solution = lesson.getSolution(puzzleIdx);
        LessonProgress progress = getOrCreateProgress(lessonId);
        progress.incrementAttempts();

        return move != null && solution != null && Arrays.equals(move, solution);
    }

    /**
     * 获取当前练习题的结果详情。
     */
    public LessonResult checkAnswerWithResult(String lessonId, int[] move) {
        TacticLesson lesson = getLesson(lessonId);
        if (lesson == null) {
            return new LessonResult(false, 0, "课程不存在", null, -1);
        }

        int puzzleIdx = getCurrentPuzzleIndex(lessonId);
        if (puzzleIdx >= lesson.getPuzzleCount()) {
            return new LessonResult(false, 0, "所有练习已完成", null, puzzleIdx);
        }

        int[] solution = lesson.getSolution(puzzleIdx);
        String explanation = lesson.getExplanation(puzzleIdx);
        LessonProgress progress = getOrCreateProgress(lessonId);
        progress.incrementAttempts();

        boolean correct = move != null && solution != null && Arrays.equals(move, solution);
        int score = 0;
        String feedback;

        if (correct) {
            score = calculateScore(lesson.getDifficulty(), progress.getAttempts());
            progress.updateScore(score);
            feedback = "回答正确！" + (explanation != null ? "\n" + explanation : "");
            advancePuzzle(lessonId);
        } else {
            feedback = "回答不正确。" + (explanation != null ? "\n" + explanation : "");
        }

        return new LessonResult(correct, score, feedback, solution, puzzleIdx);
    }

    /**
     * 完成课程。
     */
    public void completeLesson(String lessonId) {
        LessonProgress progress = getOrCreateProgress(lessonId);
        progress.setCompleted(true);
        progress.setCompletionTime(System.currentTimeMillis());
    }

    /**
     * 获取课程进度。
     */
    public LessonProgress getProgress(String lessonId) {
        return progressMap.get(lessonId);
    }

    /**
     * 获取已完成课程数量。
     */
    public int getCompletedCount() {
        int count = 0;
        for (LessonProgress p : progressMap.values()) {
            if (p.isCompleted()) count++;
        }
        return count;
    }

    /**
     * 获取课程总数。
     */
    public int getTotalCount() {
        return lessons.size();
    }

    /**
     * 获取当前练习题索引。
     */
    public int getCurrentPuzzleIndex(String lessonId) {
        return lessonPuzzleIndex.getOrDefault(lessonId, 0);
    }

    /**
     * 检查课程是否所有练习都已完成。
     */
    public boolean isLessonFinished(String lessonId) {
        TacticLesson lesson = getLesson(lessonId);
        if (lesson == null) return false;
        return getCurrentPuzzleIndex(lessonId) >= lesson.getPuzzleCount();
    }

    // ==================== 内部方法 ====================

    private LessonProgress getOrCreateProgress(String lessonId) {
        LessonProgress progress = progressMap.get(lessonId);
        if (progress == null) {
            progress = new LessonProgress(lessonId);
            progressMap.put(lessonId, progress);
        }
        return progress;
    }

    private void advancePuzzle(String lessonId) {
        int current = lessonPuzzleIndex.getOrDefault(lessonId, 0);
        lessonPuzzleIndex.put(lessonId, current + 1);

        TacticLesson lesson = getLesson(lessonId);
        if (lesson != null && current + 1 >= lesson.getPuzzleCount()) {
            completeLesson(lessonId);
        }
    }

    private int calculateScore(int difficulty, int attempts) {
        int baseScore = difficulty * 20;
        int bonus = Math.max(0, (4 - attempts) * 5);
        return baseScore + bonus;
    }

    // ==================== 加载默认课程 ====================

    private void loadDefaultLessons() {
        lessons.add(createCheckBasicsLesson());
        lessons.add(createDiscoveredCheckLesson());
        lessons.add(createDoubleCheckLesson());
        lessons.add(createKnightCradleLesson());
        lessons.add(createIronBoltLesson());
    }

    /**
     * 将军基础课程（5题）。
     */
    private TacticLesson createCheckBasicsLesson() {
        List<int[][]> puzzles = new ArrayList<>();
        List<int[]> solutions = new ArrayList<>();
        List<String> explanations = new ArrayList<>();

        // 题1：车将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 5, 0, 0, 0, 0}
        });
        solutions.add(new int[]{9, 4, 0, 4});
        explanations.add("车直接移动到将所在的列进行将军，这是最基本的将军方式。");

        // 题2：炮将军（有炮架）
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 4, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 6, 0, 0, 0, 0}
        });
        solutions.add(new int[]{9, 4, 0, 4});
        explanations.add("炮需要一个炮架（中间的马）才能将军。炮架是炮发挥威力的关键。");

        // 题3：马将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 4, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{7, 3, 5, 4});
        explanations.add("马走日字跳到将军位置，注意马腿不能被蹩。");

        // 题4：兵将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 7, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{1, 4, 0, 4});
        explanations.add("过河兵向前直进将军，兵过河后可以左右移动。");

        // 题5：双车错将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 5, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 5, 0, 0, 0, 0}
        });
        solutions.add(new int[]{7, 0, 0, 0});
        explanations.add("车从侧面将军，利用双车配合形成连续将军。");

        return new TacticLesson("check_basics", "将军基础",
                "学习中国象棋中最基本的将军方式，包括车、炮、马、兵的将军方法。",
                TacticalPattern.CHECK_BASICS, puzzles, solutions, explanations, 1);
    }

    /**
     * 抽将技巧课程（5题）。
     */
    private TacticLesson createDiscoveredCheckLesson() {
        List<int[][]> puzzles = new ArrayList<>();
        List<int[]> solutions = new ArrayList<>();
        List<String> explanations = new ArrayList<>();

        // 题1：马移开露出车将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 4, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 5, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{3, 3, 1, 2});
        explanations.add("马跳开后，露出后面的车对将形成将军。这就是抽将——移动一个棋子，露出后面的棋子进行攻击。");

        // 题2：炮移开露出车将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 6, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 5, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{3, 3, 3, 0});
        explanations.add("炮横向移开，露出后面的车进行将军。抽将的关键是选择正确的移动方向。");

        // 题3：相移开露出车将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 3, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 5, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{3, 3, 5, 1});
        explanations.add("相飞开后露出车将军。注意相只能在己方半场内飞行，且不能过河。");

        // 题4：仕移开露出车将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 2, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 5, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{3, 3, 4, 4});
        explanations.add("仕移开后露出车将军。仕在九宫内斜线移动，选择合适的方向移开。");

        // 题5：兵移开露出炮将军
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 7, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 4, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 6, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{2, 3, 1, 3});
        explanations.add("兵向前推进，露出后面的炮对将进行将军。注意炮需要马作为炮架。");

        return new TacticLesson("discovered_check", "抽将技巧",
                "学习抽将战术——移动一个棋子后，露出后面的棋子进行将军。",
                TacticalPattern.DISCOVERED_CHECK, puzzles, solutions, explanations, 2);
    }

    /**
     * 双将杀法课程（3题）。
     */
    private TacticLesson createDoubleCheckLesson() {
        List<int[][]> puzzles = new ArrayList<>();
        List<int[]> solutions = new ArrayList<>();
        List<String> explanations = new ArrayList<>();

        // 题1：马+车双将
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 4, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 5, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{3, 2, 1, 3});
        explanations.add("马跳到(1,3)位置，同时对将形成将军，而马跳开后露出车也对将形成将军。双将无法用吃子化解，只能移动将。");

        // 题2：炮+马双将
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 4, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 5, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 6, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{4, 3, 2, 4});
        explanations.add("马跳开后同时形成两个将军：马直接将军，同时露出车和炮的联合将军。");

        // 题3：双炮双将
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 4, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 6, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 6, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{2, 3, 0, 3});
        explanations.add("马跳到底线，同时对将形成将军，且露出后面的炮也形成将军。双将是致命的战术组合。");

        return new TacticLesson("double_check", "双将杀法",
                "学习双将战术——同时由两个棋子将军，对方无法同时应对。",
                TacticalPattern.DOUBLE_CHECK, puzzles, solutions, explanations, 3);
    }

    /**
     * 卧槽马课程（3题）。
     */
    private TacticLesson createKnightCradleLesson() {
        List<int[][]> puzzles = new ArrayList<>();
        List<int[]> solutions = new ArrayList<>();
        List<String> explanations = new ArrayList<>();

        // 题1：基础卧槽马
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 4, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{7, 5, 5, 4});
        explanations.add("马跳到(5,4)位置，这就是卧槽马的位置——对方底象前一格。卧槽马配合车炮可以形成强大的杀招。");

        // 题2：卧槽马+车配合
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 4, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 5, 0, 0, 0}
        });
        solutions.add(new int[]{6, 5, 4, 4});
        explanations.add("马跳到卧槽位置(4,4)，同时车在底线形成配合。卧槽马控制了将的移动路线。");

        // 题3：卧槽马+炮配合
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 4, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 6, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{5, 5, 3, 4});
        explanations.add("马跳到(3,4)位置，形成卧槽马态势，同时炮在中路有马作为炮架可以将军。");

        return new TacticLesson("knight_cradle", "卧槽马",
                "学习卧槽马战术——马跳到对方底象前一格，配合其他棋子形成杀招。",
                TacticalPattern.KNIGHT_CRADLE, puzzles, solutions, explanations, 2);
    }

    /**
     * 铁门栓课程（3题）。
     */
    private TacticLesson createIronBoltLesson() {
        List<int[][]> puzzles = new ArrayList<>();
        List<int[]> solutions = new ArrayList<>();
        List<String> explanations = new ArrayList<>();

        // 题1：基础铁门栓
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0,-2, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 4, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 6, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{7, 3, 5, 4});
        explanations.add("马跳到中路(5,4)位置，为炮提供炮架。炮在中路配合马形成铁门栓，封锁将的移动。");

        // 题2：铁门栓+车配合
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0,-2, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 4, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 6, 0, 0, 0, 0},
            { 0, 0, 0, 5, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{9, 3, 0, 3});
        explanations.add("车移动到底线(0,3)位置，配合中路的炮和马形成铁门栓。车控制底线，炮控制中路。");

        // 题3：铁门栓变招
        puzzles.add(new int[][] {
            { 0, 0, 0, 0,-1, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 4, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 0, 0, 0, 0, 0},
            { 0, 0, 0, 0, 6, 0, 0, 0, 0},
            { 0, 0, 5, 0, 0, 0, 0, 0, 0}
        });
        solutions.add(new int[]{9, 2, 0, 2});
        explanations.add("车移动到(0,2)位置，配合中路的炮和马形成铁门栓。铁门栓的核心是炮在中路有炮架。");

        return new TacticLesson("iron_bolt", "铁门栓",
                "学习铁门栓战术——炮在中路配合其他棋子封锁对方将帅的移动。",
                TacticalPattern.IRON_BOLT, puzzles, solutions, explanations, 2);
    }
}
