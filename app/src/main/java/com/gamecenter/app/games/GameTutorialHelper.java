package com.gamecenter.app.games;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

public final class GameTutorialHelper {
    private GameTutorialHelper() {
    }

    public static void showGomokuTutorial(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("五子棋玩法")
                .setMessage("黑棋先手，点击棋盘落子。先在横、竖或斜线上连成五子的一方获胜。")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void showKlotskiTutorial(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("华容道玩法")
                .setMessage("滑动方块，帮助曹操（最大方块）从底部中央的出口逃出。\n\n• 红色方块为曹操，目标是移动到底部出口\n• 其他武将挡住了去路，需要巧妙移动\n• 点击提示按钮可以获得最优解指引\n• 打乱按钮可以生成新的可解局面")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    public static void showChineseChessTutorial(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("中国象棋玩法")
                .setMessage("经典中国象棋，红方先走。\n\n• 将/帅：在九宫内移动，不能出九宫\n• 士/仕：斜行一格，不出九宫\n• 象/相：斜行两格，不能过河\n• 马：一直一斜，蹩马腿规则生效\n• 车：直线移动，无限格数\n• 炮：移动同车，吃子需隔一子\n• 兵/卒：未过河前进一步，过河可横移\n\n点击棋子查看可走位置，点击目标格落子。")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
