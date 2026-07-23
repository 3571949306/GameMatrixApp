package com.gamecenter.app.games.chinesechess;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.gamecenter.app.R;

/**
 * 中国象棋 AI 提示视觉反馈管理器。
 *
 * <p>提供以下功能：
 * <ul>
 *   <li>棋盘高亮显示目标位置（起点绿色、终点蓝色）</li>
 *   <li>箭头指示走法方向</li>
 *   <li>显示提示弹窗（走法描述 + 解释文本 + 执行/取消按钮）</li>
 *   <li>执行走法后自动清除高亮</li>
 * </ul>
 * </p>
 *
 * @author AI Assistant
 * @since 2026-07-23
 */
public class HintVisualManager {

    // ==================== 回调接口 ====================

    /**
     * 提示执行回调：当用户点击"执行"按钮时触发。
     */
    public interface OnHintExecuteListener {
        void onHintExecute(int[] move);
    }

    /**
     * 提示取消回调：当用户点击"取消"按钮或关闭弹窗时触发。
     */
    public interface OnHintCancelListener {
        void onHintCancel();
    }

    // ==================== 常量 ====================

    /** 提示高亮自动消失时间（毫秒），0 表示不自动消失 */
    private static final long HINT_HIGHLIGHT_TIMEOUT_MS = 0L;

    // ==================== 成员变量 ====================

    private final ChineseChessView chessView;
    private final Handler mainHandler;
    private Context context;

    /** 当前提示走法 */
    private int[] currentHintMove;

    /** 当前提示解释文本 */
    private String currentExplanation;

    /** 当前显示的弹窗 */
    private AlertDialog currentDialog;

    /** 执行回调 */
    private OnHintExecuteListener executeListener;

    /** 取消回调 */
    private OnHintCancelListener cancelListener;

    /** 自动清除高亮的 Runnable */
    private final Runnable clearHighlightRunnable = this::clearHint;

    // ==================== 构造函数 ====================

    /**
     * 创建 HintVisualManager。
     *
     * @param view 关联的 ChineseChessView
     */
    public HintVisualManager(ChineseChessView view) {
        this.chessView = view;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.context = view.getContext();
    }

    // ==================== 公共 API ====================

    /**
     * 设置执行回调。
     */
    public void setOnHintExecuteListener(OnHintExecuteListener listener) {
        this.executeListener = listener;
    }

    /**
     * 设置取消回调。
     */
    public void setOnHintCancelListener(OnHintCancelListener listener) {
        this.cancelListener = listener;
    }

    /**
     * 显示提示：高亮棋盘位置 + 箭头 + 弹窗。
     *
     * @param move        [fromR, fromC, toR, toC]
     * @param explanation 解释文本（为什么这么走）
     */
    public void showHint(int[] move, String explanation) {
        if (move == null || move.length < 4) return;

        // 保存当前提示状态
        this.currentHintMove = move.clone();
        this.currentExplanation = explanation;

        // 在棋盘上显示增强提示（绿色起点 + 蓝色终点 + 箭头）
        mainHandler.post(() -> chessView.setEnhancedHint(move));

        // 显示提示弹窗
        showHintDialog(move, explanation);

        // 设置自动消失（如果配置了超时时间）
        if (HINT_HIGHLIGHT_TIMEOUT_MS > 0) {
            mainHandler.removeCallbacks(clearHighlightRunnable);
            mainHandler.postDelayed(clearHighlightRunnable, HINT_HIGHLIGHT_TIMEOUT_MS);
        }
    }

    /**
     * 清除所有提示效果（高亮 + 弹窗）。
     */
    public void clearHint() {
        mainHandler.removeCallbacks(clearHighlightRunnable);
        this.currentHintMove = null;
        this.currentExplanation = null;

        // 关闭弹窗
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        currentDialog = null;

        // 清除棋盘高亮
        mainHandler.post(() -> chessView.clearEnhancedHint());
    }

    /**
     * 显示提示弹窗：走法描述 + 解释文本 + 执行/取消按钮。
     *
     * @param move        [fromR, fromC, toR, toC]
     * @param explanation 解释文本
     */
    public void showHintDialog(int[] move, String explanation) {
        if (move == null || move.length < 4) return;

        // 关闭已有弹窗
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }

        // 获取走法描述
        String moveDescription = buildMoveDescription(move);

        // 构建弹窗视图
        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_chess_hint, null);

        // 设置标题
        TextView tvTitle = dialogView.findViewById(R.id.tv_hint_title);
        tvTitle.setText(R.string.chinese_chess_hint_title);

        // 设置走法描述
        TextView tvMoveDesc = dialogView.findViewById(R.id.tv_hint_move_desc);
        tvMoveDesc.setText(moveDescription);

        // 设置解释文本
        TextView tvExplanation = dialogView.findViewById(R.id.tv_hint_explanation);
        if (explanation != null && !explanation.isEmpty()) {
            tvExplanation.setText(explanation);
            tvExplanation.setVisibility(View.VISIBLE);
        } else {
            tvExplanation.setVisibility(View.GONE);
        }

        // 构建弹窗
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true);

        currentDialog = builder.create();

        // 设置按钮点击事件
        Button btnExecute = dialogView.findViewById(R.id.btn_hint_execute);
        Button btnCancel = dialogView.findViewById(R.id.btn_hint_cancel);

        btnExecute.setOnClickListener(v -> {
            currentDialog.dismiss();
            if (executeListener != null && currentHintMove != null) {
                executeListener.onHintExecute(currentHintMove);
            }
            // 执行后清除高亮
            clearHint();
        });

        btnCancel.setOnClickListener(v -> {
            currentDialog.dismiss();
            if (cancelListener != null) {
                cancelListener.onHintCancel();
            }
            clearHint();
        });

        // 弹窗关闭时也清除高亮
        currentDialog.setOnDismissListener(dialog -> {
            // 不在这里清除，由按钮处理
        });

        currentDialog.show();
    }

    // ==================== 内部方法 ====================

    /**
     * 构建走法描述文本。
     * 格式："[棋子名] 从 (起始位置) → (目标位置)"
     *
     * @param move [fromR, fromC, toR, toC]
     * @return 走法描述字符串
     */
    private String buildMoveDescription(int[] move) {
        if (move == null || move.length < 4) {
            return context.getString(R.string.chinese_chess_hint_suggested_move);
        }

        int fromR = move[0], fromC = move[1];
        int toR = move[2], toC = move[3];

        // 获取棋盘上的棋子
        int[][] board = chessView.getBoardState();
        int piece = board[fromR][fromC];

        if (piece == 0) {
            return context.getString(R.string.chinese_chess_hint_suggested_move);
        }

        // 获取棋子名称
        boolean isRed = piece > 0;
        int type = Math.abs(piece);
        String pieceName = ChineseChessView.getPieceName(type, isRed);

        // 构建位置描述（红方从右到左 9-1，黑方从左到右 1-9）
        String fromPos = formatPosition(fromR, fromC, isRed);
        String toPos = formatPosition(toR, toC, isRed);

        return pieceName + " " + fromPos + " → " + toPos;
    }

    /**
     * 格式化位置为象棋术语。
     * 红方：列号从右到左（9-c），行号从下到上
     * 黑方：列号从左到右（c+1），行号从上到下
     *
     * @param row   行号（0-9）
     * @param col   列号（0-8）
     * @param isRed 是否红方
     * @return 位置描述（如 "二路"、"三楼"）
     */
    private String formatPosition(int row, int col, boolean isRed) {
        int colNum = isRed ? (9 - col) : (col + 1);
        String colStr = isRed ? cnNum(colNum) : String.valueOf(colNum);

        // 行号描述
        String rowDesc;
        if (isRed) {
            // 红方：底线为"一路"，河界为"五路"
            rowDesc = cnNum(10 - row) + "楼";
        } else {
            // 黑方：底线为"1楼"，河界为"5楼"
            rowDesc = (row + 1) + "楼";
        }

        return colStr + "路" + rowDesc;
    }

    /**
     * 将数字转为中文数字（红方记谱用）。
     */
    private String cnNum(int n) {
        String[] cnNums = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (n >= 0 && n <= 9) return cnNums[n];
        return String.valueOf(n);
    }

    /**
     * 获取当前是否有活跃的提示。
     */
    public boolean isHintActive() {
        return currentHintMove != null;
    }

    /**
     * 获取当前提示走法。
     */
    public int[] getCurrentHintMove() {
        return currentHintMove;
    }

    /**
     * 获取当前提示解释文本。
     */
    public String getCurrentExplanation() {
        return currentExplanation;
    }

    /**
     * 释放资源，应在 Activity/Fragment 销毁时调用。
     */
    public void release() {
        mainHandler.removeCallbacksAndMessages(null);
        clearHint();
        executeListener = null;
        cancelListener = null;
    }
}
