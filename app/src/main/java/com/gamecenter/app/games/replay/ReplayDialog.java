package com.gamecenter.app.games.replay;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * P2-7 (BOARD_REPLAY): 通用回放对话框。
 *
 * 提供逐步回放 UI（上一步/下一步/重置/跳到终点），
 * 通过 [ReplayPlayer.Listener] 回调通知宿主刷新棋盘。
 *
 * 用法：
 * <pre>
 *   ReplayDialog.show(this, record, new ReplayPlayer.Listener() {
 *       public void onBoardUpdated(int step, List<ReplayMove> played) {
 *           // 重建棋盘到第 step 步局面
 *           myView.rebuildFromMoves(played);
 *       }
 *       public void onReplayFinished() {}
 *       public void onReplayReset() {}
 *   });
 * </pre>
 */
public class ReplayDialog {

    /**
     * 显示回放对话框。
     * @param context Activity Context
     * @param record 回放记录
     * @param listener 棋盘刷新回调
     */
    public static void show(Context context, ReplayRecord record, ReplayPlayer.Listener listener) {
        if (record == null || record.getMoveCount() == 0) {
            Toast.makeText(context, "暂无回放数据", Toast.LENGTH_SHORT).show();
            return;
        }

        ReplayPlayer player = new ReplayPlayer(record);
        player.addListener(listener);

        // 构建对话框布局
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        // 信息行
        TextView tvInfo = new TextView(context);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setText(String.format("共 %d 步 · 结果：%s · 用时 %s",
                record.getMoveCount(),
                record.getResultLabel(),
                formatDuration(record.getDurationMs())));
        layout.addView(tvInfo);

        // 步数显示
        TextView tvStep = new TextView(context);
        tvStep.setGravity(Gravity.CENTER);
        tvStep.setTextSize(16f);
        tvStep.setText("0 / " + record.getMoveCount());
        layout.addView(tvStep);

        // 当前走法描述
        TextView tvMoveDesc = new TextView(context);
        tvMoveDesc.setGravity(Gravity.CENTER);
        tvMoveDesc.setTextSize(12f);
        layout.addView(tvMoveDesc);

        // 按钮行
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        Button btnReset = new Button(context);
        btnReset.setText("⏮");
        btnReset.setLayoutParams(btnParams);

        Button btnPrev = new Button(context);
        btnPrev.setText("◀");
        btnPrev.setLayoutParams(btnParams);

        Button btnNext = new Button(context);
        btnNext.setText("▶");
        btnNext.setLayoutParams(btnParams);

        Button btnEnd = new Button(context);
        btnEnd.setText("⏭");
        btnEnd.setLayoutParams(btnParams);

        btnRow.addView(btnReset);
        btnRow.addView(btnPrev);
        btnRow.addView(btnNext);
        btnRow.addView(btnEnd);
        layout.addView(btnRow);

        // 更新 UI 的辅助
        Runnable updateUi = () -> {
            int cur = player.getCurrentStep();
            int total = player.getTotalSteps();
            tvStep.setText(cur + " / " + total);
            if (cur == 0) {
                tvMoveDesc.setText("初始局面");
            } else {
                ReplayMove m = record.moves.get(cur - 1);
                tvMoveDesc.setText("第 " + cur + " 步：" + m.toString());
            }
            btnPrev.setEnabled(cur > 0);
            btnReset.setEnabled(cur > 0);
            btnNext.setEnabled(cur < total);
            btnEnd.setEnabled(cur < total);
        };

        btnNext.setOnClickListener(v -> {
            player.stepForward();
            updateUi.run();
        });
        btnPrev.setOnClickListener(v -> {
            player.stepBack();
            updateUi.run();
        });
        btnReset.setOnClickListener(v -> {
            player.reset();
            updateUi.run();
        });
        btnEnd.setOnClickListener(v -> {
            player.gotoStep(player.getTotalSteps());
            updateUi.run();
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("对局回放")
                .setView(layout)
                .setPositiveButton("关闭", null)
                .setOnDismissListener(d -> player.removeListener(listener))
                .create();

        // 初始局面
        player.reset();
        updateUi.run();
        dialog.show();
    }

    private static String formatDuration(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min > 0 ? min + "分" + sec + "秒" : sec + "秒";
    }
}
