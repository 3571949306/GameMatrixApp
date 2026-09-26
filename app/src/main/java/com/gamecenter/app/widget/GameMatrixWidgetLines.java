package com.gamecenter.app.widget;

/**
 * 桌面小组件三行文案的纯 Java 拼装（无任何 Android 依赖，可被 javac 级单测直接编译；
 * GameMatrixWidgetProvider.buildLines 是本类的委托门面，与 rating 切片
 * RatingStore/EloCalculator 的"门面 + 纯逻辑"分层惯例一致）。
 *
 * <p>行规格：</p>
 * <ul>
 *   <li>金币行：如 "金币 1234"</li>
 *   <li>挑战行：completed → "今日挑战已完成 ✓"；
 *       否则 "挑战：{gameName} {progress}/{target}"——progress 显示前钳到 target
 *       （进度不允许越过目标上限），gameName 空时省略名字槽避免双空格</li>
 *   <li>时长行：分钟向下取整，如 "今日 45 分钟"；0 分钟显示 "今日 0 分钟"，
 *       负值（异常数据）防御为 0</li>
 * </ul>
 */
final class GameMatrixWidgetLines {

    private static final long MINUTE_MS = 60_000L;

    private GameMatrixWidgetLines() {
    }

    static String[] buildLines(long balance, String gameName, int progress, int target,
            boolean completed, long todayPlayMs) {
        String[] lines = new String[3];
        lines[0] = "金币 " + balance;
        if (completed) {
            lines[1] = "今日挑战已完成 ✓";
        } else {
            String name = gameName == null ? "" : gameName;
            int shownProgress = Math.min(progress, target);
            lines[1] = name.isEmpty()
                    ? "挑战：" + shownProgress + "/" + target
                    : "挑战：" + name + " " + shownProgress + "/" + target;
        }
        long minutes = Math.max(0L, todayPlayMs) / MINUTE_MS;
        lines[2] = "今日 " + minutes + " 分钟";
        return lines;
    }
}
