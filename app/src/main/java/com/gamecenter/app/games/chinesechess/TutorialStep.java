package com.gamecenter.app.games.chinesechess;

/**
 * 新手引导步骤定义。
 *
 * <p>定义引导流程中每一步的类型、内容和高亮区域信息。</p>
 */
public class TutorialStep {

    /**
     * 引导步骤类型枚举。
     */
    public enum StepType {
        /** 高亮UI元素 */
        HIGHLIGHT,
        /** 显示对话框/提示文字 */
        DIALOG,
        /** 播放动画演示 */
        ANIMATION
    }

    private final StepType type;
    private final String title;
    private final String description;
    private final int highlightArea;
    private final int[] highlightMove;
    private final long delay;
    private int stepIndex;

    /**
     * 构造函数。
     *
     * @param type           步骤类型
     * @param title          标题
     * @param description    描述文字
     * @param highlightArea  高亮区域资源ID（0表示无高亮）
     * @param highlightMove  高亮走法坐标 [fromRow, fromCol, toRow, toCol]（null表示无走法高亮）
     * @param delay          步骤间延迟（毫秒）
     */
    public TutorialStep(StepType type, String title, String description,
                        int highlightArea, int[] highlightMove, long delay) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.highlightArea = highlightArea;
        this.highlightMove = highlightMove;
        this.delay = delay;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(int stepIndex) {
        this.stepIndex = stepIndex;
    }

    public StepType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getHighlightArea() {
        return highlightArea;
    }

    public int[] getHighlightMove() {
        return highlightMove;
    }

    public long getDelay() {
        return delay;
    }
}
