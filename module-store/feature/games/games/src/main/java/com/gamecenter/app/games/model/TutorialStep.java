package com.gamecenter.app.games.model;

/**
 * 教程步骤定义
 * <p>
 * 描述游戏中教程的一个步骤。
 * </p>
 */
public class TutorialStep {

    /** 步骤顺序号 */
    public int order;

    /** 步骤标题 */
    public String title;

    /** 步骤说明文字 */
    public String description;

    /** 关联的图片资源名（可选） */
    public String imageRes;

    public TutorialStep() {}

    public TutorialStep(int order, String title, String description) {
        this.order = order;
        this.title = title;
        this.description = description;
    }
}
