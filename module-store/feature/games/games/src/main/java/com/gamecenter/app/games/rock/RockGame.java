package com.gamecenter.app.games.rock;

import java.util.Random;

/**
 * 石头剪刀布游戏的核心逻辑类（单机模式）
 * <p>
 * 管理玩家和电脑的选择、胜负判定和得分计算。
 * 电脑选择通过 Random 随机生成。
 * <p>
 * 关键设计决策：
 * - 使用整型常量（0/1/2）而非枚举表示石头/剪刀/布，简化序列化和网络传输
 * - 胜负结果使用正/零/负数（WIN=1, DRAW=0, LOSE=-1）表示，便于判断方向
 * - 提供静态工具方法 getChoiceName/getChoiceEmoji 供视图层统一使用
 */
public class RockGame {

    /** 石头 */
    public static final int ROCK = 0;
    /** 剪刀 */
    public static final int SCISSORS = 1;
    /** 布 */
    public static final int PAPER = 2;

    /** 玩家获胜 */
    public static final int WIN = 1;
    /** 平局 */
    public static final int DRAW = 0;
    /** 玩家失败 */
    public static final int LOSE = -1;

    private int playerScore;
    private int computerScore;
    /** 玩家当前选择（-1 表示未选择） */
    private int playerChoice = -1;
    /** 电脑当前选择（-1 表示未选择） */
    private int computerChoice = -1;
    /** 上次对局结果 */
    private int lastResult = 0;
    /** 上次对局结果文字描述 */
    private String lastResultText = "";
    private Random random;

    /**
     * 构造函数，初始化随机数生成器并重置游戏状态
     */
    public RockGame() {
        random = new Random();
        reset();
    }

    /**
     * 重置游戏状态，清空得分和选择
     */
    public void reset() {
        playerScore = 0;
        computerScore = 0;
        playerChoice = -1;
        computerChoice = -1;
        lastResult = 0;
        lastResultText = "";
    }

    /**
     * 玩家做出选择，同时电脑随机出拳，判定胜负
     * <p>
     * 胜负规则：
     * - 石头(0) 胜 剪刀(1)
     * - 剪刀(1) 胜 布(2)
     * - 布(2) 胜 石头(0)
     *
     * @param choice 玩家选择（ROCK/SCISSORS/PAPER）
     */
    public void choose(int choice) {
        playerChoice = choice;
        // 电脑随机出拳（0~2）
        computerChoice = random.nextInt(3);

        if (playerChoice == computerChoice) {
            lastResult = DRAW;
            lastResultText = "平局!";
        } else if ((playerChoice == ROCK && computerChoice == SCISSORS)
                || (playerChoice == SCISSORS && computerChoice == PAPER)
                || (playerChoice == PAPER && computerChoice == ROCK)) {
            lastResult = WIN;
            lastResultText = "你赢了!";
            playerScore++;
        } else {
            lastResult = LOSE;
            lastResultText = "电脑赢了!";
            computerScore++;
        }
    }

    /**
     * 获取选择的中文文名称
     *
     * @param choice 选择值（ROCK/SCISSORS/PAPER）
     * @return 中文名称
     */
    public static String getChoiceName(int choice) {
        switch (choice) {
            case ROCK: return "石头";
            case SCISSORS: return "剪刀";
            case PAPER: return "布";
            default: return "?";
        }
    }

    /**
     * 获取选择的 Emoji 表情符号
     *
     * @param choice 选择值（ROCK/SCISSORS/PAPER）
     * @return Emoji 字符串
     */
    public static String getChoiceEmoji(int choice) {
        switch (choice) {
            case ROCK: return "✊";
            case SCISSORS: return "✌️";
            case PAPER: return "✋";
            default: return "?";
        }
    }

    /**
     * 获取玩家得分
     * @return 玩家获胜次数
     */
    public int getPlayerScore() { return playerScore; }
    /**
     * 获取电脑得分
     * @return 电脑获胜次数
     */
    public int getComputerScore() { return computerScore; }
    /**
     * 获取玩家当前选择
     * @return 选择值（ROCK/SCISSORS/PAPER），未选择时为 -1
     */
    public int getPlayerChoice() { return playerChoice; }
    /**
     * 获取电脑当前选择
     * @return 选择值（ROCK/SCISSORS/PAPER），未选择时为 -1
     */
    public int getComputerChoice() { return computerChoice; }
    /**
     * 获取上次对局结果
     * @return WIN/DRAW/LOSE
     */
    public int getLastResult() { return lastResult; }
    /**
     * 获取上次对局结果文字
     * @return 结果描述（如"你赢了!"）
     */
    public String getLastResultText() { return lastResultText; }
}
