package com.gamecenter.app.games.chinesechess;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋AI提示新手引导管理器。
 *
 * <p>功能：
 * <ul>
 *   <li>7步引导流程，介绍提示按钮、提示弹窗、走法演示和次数限制</li>
 *   <li>使用SharedPreferences持久化引导状态</li>
 *   <li>支持开始、跳过、完成引导</li>
 *   <li>通过OnTutorialListener回调引导进度</li>
 * </ul>
 */
public class HintTutorialManager {

    private static final String PREFS_NAME = "chinese_chess_tutorial";
    private static final String KEY_TUTORIAL_SHOWN = "hint_tutorial_shown";

    /**
     * 引导事件监听接口。
     */
    public interface OnTutorialListener {
        /** 当前引导步骤回调 */
        void onTutorialStep(int stepIndex, TutorialStep step);
        /** 引导全部完成 */
        void onTutorialComplete();
        /** 用户跳过引导 */
        void onTutorialSkip();
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Handler mainHandler;
    private final List<TutorialStep> steps;
    private boolean isRunning;
    private int currentStep;
    private OnTutorialListener listener;

    /**
     * 构造函数。
     *
     * @param context 上下文
     */
    public HintTutorialManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.steps = buildTutorialSteps();
    }

    /**
     * 检查是否需要显示引导。
     *
     * @return true表示需要显示，false表示已显示过
     */
    public boolean shouldShowTutorial() {
        return !prefs.getBoolean(KEY_TUTORIAL_SHOWN, false);
    }

    /**
     * 开始引导流程。
     *
     * @param listener 引导事件监听器
     */
    public void startTutorial(OnTutorialListener listener) {
        if (isRunning) {
            return;
        }
        this.listener = listener;
        this.isRunning = true;
        this.currentStep = 0;
        executeCurrentStep();
    }

    /**
     * 跳过引导。
     */
    public void skipTutorial() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        mainHandler.removeCallbacksAndMessages(null);
        markTutorialShown();
        if (listener != null) {
            listener.onTutorialSkip();
        }
    }

    /**
     * 推进到下一步（由外部UI交互触发）。
     */
    public void nextStep() {
        if (!isRunning) {
            return;
        }
        currentStep++;
        if (currentStep >= steps.size()) {
            completeTutorial();
        } else {
            executeCurrentStep();
        }
    }

    /**
     * 标记引导已显示。
     */
    public void markTutorialShown() {
        prefs.edit().putBoolean(KEY_TUTORIAL_SHOWN, true).apply();
    }

    /**
     * 重置引导状态（允许重新显示引导）。
     */
    public void resetTutorial() {
        prefs.edit().putBoolean(KEY_TUTORIAL_SHOWN, false).apply();
    }

    /**
     * 获取引导总步骤数。
     *
     * @return 步骤数量
     */
    public int getStepCount() {
        return steps.size();
    }

    /**
     * 获取指定步骤。
     *
     * @param index 步骤索引
     * @return 对应的TutorialStep
     */
    public TutorialStep getStep(int index) {
        if (index < 0 || index >= steps.size()) {
            return null;
        }
        return steps.get(index);
    }

    /**
     * 是否正在运行引导。
     *
     * @return true表示引导进行中
     */
    public boolean isRunning() {
        return isRunning;
    }

    private void executeCurrentStep() {
        if (currentStep >= steps.size()) {
            completeTutorial();
            return;
        }
        TutorialStep step = steps.get(currentStep);
        if (listener != null) {
            listener.onTutorialStep(currentStep, step);
        }
        long delay = step.getDelay();
        if (delay > 0) {
            mainHandler.postDelayed(this::nextStep, delay);
        }
    }

    private void completeTutorial() {
        isRunning = false;
        markTutorialShown();
        if (listener != null) {
            listener.onTutorialComplete();
        }
    }

    private List<TutorialStep> buildTutorialSteps() {
        List<TutorialStep> list = new ArrayList<>();

        // 步骤1: 介绍提示按钮位置
        list.add(new TutorialStep(
                TutorialStep.StepType.HIGHLIGHT,
                "提示按钮",
                "点击右下角的💡按钮，获取AI走法建议",
                0, null, 0
        ));

        // 步骤2: 演示点击提示按钮
        list.add(new TutorialStep(
                TutorialStep.StepType.ANIMATION,
                "点击提示",
                "现在点击提示按钮试试",
                0, null, 2000
        ));

        // 步骤3: 展示提示弹窗
        list.add(new TutorialStep(
                TutorialStep.StepType.DIALOG,
                "AI建议",
                "弹窗会显示AI推荐的最佳走法",
                0, null, 0
        ));

        // 步骤4: 解释提示内容
        list.add(new TutorialStep(
                TutorialStep.StepType.DIALOG,
                "走法解读",
                "提示会用箭头标出起点和终点，绿色表示推荐走法",
                0, null, 3000
        ));

        // 步骤5: 演示执行走法
        list.add(new TutorialStep(
                TutorialStep.StepType.ANIMATION,
                "执行走法",
                "点击棋子后，棋盘会高亮提示走法范围",
                0, new int[]{4, 3, 3, 3}, 3000
        ));

        // 步骤6: 介绍次数限制
        list.add(new TutorialStep(
                TutorialStep.StepType.DIALOG,
                "使用限制",
                "每局游戏有10次提示机会，请合理使用",
                0, null, 0
        ));

        // 步骤7: 完成提示
        list.add(new TutorialStep(
                TutorialStep.StepType.DIALOG,
                "开始游戏",
                "引导完成！享受中国象棋的乐趣吧",
                0, null, 2000
        ));

        return list;
    }
}
