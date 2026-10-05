package com.gamecenter.app.sudoku;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Arrays;

/**
 * 数独自定义谜题编辑器 Fragment（纯代码构建 UI，与模块风格一致）。
 *
 * <p>编辑器持有一张 9×9 int 盘面：点选/拖动选格后用数字键盘填入给定数（擦除清 0），
 * 放置时行/列/宫同数冲突即时高亮提示（本地检查，无求解开销）。保存前经
 * {@link SudokuGame#validateCustomPuzzle(int[][])} 校验闸门（合法且恰一解），
 * 非法盘面一律拒绝保存。谜题码经 {@link SudokuPuzzleCodec} 导入导出，用于跨设备分享。</p>
 *
 * <p>宿主（{@link SudokuModuleFragment}）以 replace + addToBackStack 打开本编辑器；
 * 返回时宿主 Fragment 重建，自定义谜题列表自然刷新。</p>
 */
public class SudokuEditorFragment extends Fragment {

    private static final int GRID_SIZE = SudokuGame.GRID_SIZE;
    private static final int BOX_SIZE = SudokuGame.BOX_SIZE;
    /** 唯一解的数学下界：给定数少于 17 个的谜题必定多解。 */
    private static final int MIN_CLUES_FOR_UNIQUE = 17;

    // 主题色（整体 UI，仿 SokobanEditorFragment）
    private int colorBg;
    private int colorText;
    private int colorKeyNormal;
    // 画板调色板（深浅色各自适配）
    private int colorBoard;
    private int colorCell;
    private int colorBoxTint;
    private int colorSelected;
    private int colorConflict;
    private int colorGridLine;
    private int colorBoxLine;
    private int colorDigit;

    // 编辑状态（首次进入预置空盘）
    private final int[][] grid = new int[GRID_SIZE][GRID_SIZE];
    private final boolean[][] conflictMask = new boolean[GRID_SIZE][GRID_SIZE];
    private int selectedRow = -1;
    private int selectedCol = -1;
    /** 最近一次放置产生的即时提示（空串 = 无）。 */
    private String statusHint = "";

    // UI
    private EditorBoardView boardView;
    private TextView tvStatus;
    private EditText etName;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int minimumTouchSize = (int) Math.ceil(48 * dp);
        initColors();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (12 * dp), (int) (12 * dp), (int) (12 * dp), (int) (12 * dp));

        // 标题 + 谜题名输入
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("谜题编辑器");
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextSize(18f);
        tvTitle.setTextColor(colorText);
        root.addView(tvTitle, matchWrapParams());

        etName = new EditText(ctx);
        etName.setHint("谜题名（选填）");
        etName.setMaxLines(1);
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        etName.setTextColor(colorText);
        etName.setHintTextColor(0xFF888888);
        root.addView(etName, matchWrapParams());

        // 9×9 编辑画板
        boardView = new EditorBoardView(ctx, this);
        boardView.setGrid(grid);
        boardView.setConflictMask(conflictMask);
        root.addView(boardView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 实时状态栏
        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(13f);
        tvStatus.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        root.addView(tvStatus, matchWrapParams());

        // 数字键盘：两行（1-5 / 6-9 + 擦除），点选格子后按键填入
        root.addView(buildKeyRow(ctx, dp, 1, 5, minimumTouchSize));
        root.addView(buildKeyRow(ctx, dp, 6, 10, minimumTouchSize));

        // 底部操作
        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.addView(buildActionButton(ctx, dp, minimumTouchSize, "校验并保存", v -> savePuzzle()));
        actionRow.addView(buildActionButton(ctx, dp, minimumTouchSize, "导入码", v -> showImportDialog()));
        actionRow.addView(buildActionButton(ctx, dp, minimumTouchSize, "导出码", v -> showExportDialog()));
        actionRow.addView(buildActionButton(ctx, dp, minimumTouchSize, "完成", v -> exitEditor()));
        root.addView(actionRow, matchWrapParams());

        refreshStatus();
        return root;
    }

    // ==================== 键盘与画板操作 ====================

    /** 数字键盘行：from..to 为 1-9，10 表示擦除（清 0）。 */
    private LinearLayout buildKeyRow(Context ctx, float dp, int from, int to, int minTouch) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int number = from; number <= to; number++) {
            final int value = number;
            Button btn = new Button(ctx);
            btn.setText(value <= 9 ? String.valueOf(value) : "擦除");
            btn.setTextSize(16f);
            btn.setMinimumHeight(minTouch);
            btn.setMinHeight(minTouch);
            btn.setBackgroundColor(colorKeyNormal);
            btn.setTextColor(colorText);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins((int) (2 * dp), (int) (2 * dp), (int) (2 * dp), (int) (2 * dp));
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> placeNumber(value <= 9 ? value : 0));
            row.addView(btn);
        }
        return row;
    }

    private Button buildActionButton(Context ctx, float dp, int minTouch, String text,
                                      View.OnClickListener listener) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setMinimumHeight(minTouch);
        btn.setMinHeight(minTouch);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (2 * dp), (int) (4 * dp), (int) (2 * dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** 画板点选/拖动回调：更新选中格并刷新状态栏。 */
    private void onCellPicked(int row, int col) {
        selectedRow = row;
        selectedCol = col;
        boardView.setSelected(row, col);
        refreshStatus();
    }

    /**
     * 往选中格填入数字（0=擦除）。
     *
     * <p>放置时做行/列/宫同数本地检查并即时提示，不调 validateCustomPuzzle
     * （那带求解开销，只在保存闸门用一次）。</p>
     */
    private void placeNumber(int number) {
        if (selectedRow < 0 || selectedCol < 0) {
            statusHint = "先点选棋盘上的格子";
            refreshStatus();
            return;
        }
        String conflict = number == 0 ? null : findConflictHint(selectedRow, selectedCol, number);
        grid[selectedRow][selectedCol] = number;
        statusHint = conflict == null ? "" : conflict;
        rebuildConflictMask();
        boardView.invalidate();
        refreshStatus();
    }

    /** 本地行/列/宫同数检查（O(27) 扫描），冲突返回提示文案，否则 null。 */
    private String findConflictHint(int row, int col, int number) {
        for (int c = 0; c < GRID_SIZE; c++) {
            if (c != col && grid[row][c] == number) return "第" + (row + 1) + "行已有 " + number;
        }
        for (int r = 0; r < GRID_SIZE; r++) {
            if (r != row && grid[r][col] == number) return "第" + (col + 1) + "列已有 " + number;
        }
        int boxRow = (row / BOX_SIZE) * BOX_SIZE;
        int boxCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = boxRow; r < boxRow + BOX_SIZE; r++) {
            for (int c = boxCol; c < boxCol + BOX_SIZE; c++) {
                if ((r != row || c != col) && grid[r][c] == number) return "所在宫已有 " + number;
            }
        }
        return null;
    }

    /** 重算冲突格掩码：同行/列/宫出现同数的格子标红。 */
    private void rebuildConflictMask() {
        for (int r = 0; r < GRID_SIZE; r++) Arrays.fill(conflictMask[r], false);
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                int value = grid[r][c];
                if (value != 0 && findConflictHint(r, c, value) != null) conflictMask[r][c] = true;
            }
        }
    }

    /** 实时状态栏：给定数 N 个 · 冲突 X 处 · 提示文案。 */
    private void refreshStatus() {
        int givens = 0;
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] != 0) givens++;
            }
        }
        int conflicts = 0;
        for (boolean[] row : conflictMask) {
            for (boolean conflicting : row) if (conflicting) conflicts++;
        }
        String hint;
        if (statusHint != null && !statusHint.isEmpty()) {
            hint = statusHint; // 最近一次放置的即时反馈
        } else if (conflicts > 0) {
            hint = "✗ 存在同数冲突，请先消除";
        } else if (givens < MIN_CLUES_FOR_UNIQUE) {
            hint = "给定数少于 " + MIN_CLUES_FOR_UNIQUE + " 个，必定多解";
        } else {
            hint = "✓ 可尝试校验保存";
        }
        tvStatus.setText("给定数 " + givens + " 个 · 冲突 " + conflicts + " 处 · " + hint);
        tvStatus.setTextColor(conflicts > 0 ? 0xFFC44536 : 0xFF2D6A4F);
    }

    // ==================== 保存 / 导入 / 导出 ====================

    private void savePuzzle() {
        // 校验闸门：与游戏装载同一套规则（合法且恰一解才放行）
        SudokuGame probe = new SudokuGame();
        if (probe.validateCustomPuzzle(grid) == null) {
            // null 无法区分冲突/无解/多解，统一提示
            Toast.makeText(requireContext(), "盘面无唯一解，请调整给定数", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = etName.getText().toString();
        int id = SudokuCustomPuzzles.add(requireContext(), name, grid);
        if (id < 0) {
            Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), "已保存「" + (name == null || name.trim().isEmpty() ? "未命名" : name.trim()) + "」", Toast.LENGTH_SHORT).show();
        exitEditor();
    }

    private void showImportDialog() {
        Context ctx = requireContext();
        EditText input = new EditText(ctx);
        input.setHint("粘贴 81 字符谜题码");
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        FrameLayout wrap = new FrameLayout(ctx);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.addView(input);
        new AlertDialog.Builder(ctx)
                .setTitle("导入谜题码")
                .setView(wrap)
                .setPositiveButton("导入", (d, w) -> {
                    int[][] decoded = SudokuPuzzleCodec.decode(input.getText().toString());
                    if (decoded == null) {
                        Toast.makeText(ctx, "谜题码无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (int r = 0; r < GRID_SIZE; r++) {
                        System.arraycopy(decoded[r], 0, grid[r], 0, GRID_SIZE);
                    }
                    selectedRow = -1;
                    selectedCol = -1;
                    statusHint = "";
                    boardView.setSelected(-1, -1);
                    rebuildConflictMask();
                    boardView.invalidate();
                    refreshStatus();
                    Toast.makeText(ctx, "导入成功，可继续编辑或直接保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showExportDialog() {
        Context ctx = requireContext();
        String code = SudokuPuzzleCodec.encode(grid);
        if (code == null) {
            Toast.makeText(ctx, "当前盘面无法导出", Toast.LENGTH_SHORT).show();
            return;
        }
        TextView tvCode = new TextView(ctx);
        tvCode.setText(code);
        tvCode.setTextIsSelectable(true);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.addView(tvCode);
        new AlertDialog.Builder(ctx)
                .setTitle("谜题码（复制分享）")
                .setView(wrap)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("sudoku_puzzle", code));
                        Toast.makeText(ctx, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void exitEditor() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        }
    }

    // ==================== 主题 ====================

    private void initColors() {
        boolean dark = isNightMode();
        colorBg = dark ? 0xFF121622 : 0xFFFAFAFA;
        colorText = dark ? 0xFFE4E6F0 : 0xFF212121;
        colorKeyNormal = dark ? 0xFF26304A : 0xFFDDDDDD;
        // 画板调色板：浅色沿用游玩棋盘纸感配色，深色整体压暗保持对比度
        colorBoard = dark ? 0xFF1E2430 : 0xFFF5F0E8;
        colorCell = dark ? 0xFF262D3D : 0xFFFBF9F6;
        colorBoxTint = dark ? 0xFF2A3242 : 0xFFEFF6F1;
        colorSelected = dark ? 0xFF3D4E8C : 0xFFD4EDE1;
        colorConflict = dark ? 0xFF5A2A2A : 0xFFFEE2E2;
        colorGridLine = dark ? 0xFF4A5262 : 0xFFB8B8B8;
        colorBoxLine = dark ? 0xFF8FA3B0 : 0xFF5B8A72;
        colorDigit = dark ? 0xFFE4E6F0 : 0xFF2D2D2D;
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    // ==================== 画板 View ====================

    /** 9×9 编辑画板：点选/拖动选格 + 渲染（选中格高亮、所在宫底色、冲突标红、宫分隔加粗）。 */
    private static final class EditorBoardView extends View {

        private static final int BOARD_SIZE = 9;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[][] grid = new int[0][0];
        private boolean[][] conflictMask = new boolean[0][0];
        private int selectedRow = -1;
        private int selectedCol = -1;
        private float cellSize;
        private float offsetX;
        private float offsetY;
        private final SudokuEditorFragment host;

        EditorBoardView(Context context, SudokuEditorFragment host) {
            super(context);
            this.host = host;
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setGrid(int[][] grid) {
            this.grid = grid;
            invalidate();
        }

        void setConflictMask(boolean[][] mask) {
            this.conflictMask = mask;
            invalidate();
        }

        void setSelected(int row, int col) {
            selectedRow = row;
            selectedCol = col;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            cellSize = Math.min(w, h) / BOARD_SIZE;
            offsetX = (w - cellSize * BOARD_SIZE) / 2f;
            offsetY = (h - cellSize * BOARD_SIZE) / 2f;
            textPaint.setTextSize(cellSize * 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (grid.length == 0) return;
            canvas.drawColor(host.colorBoard);
            textPaint.setColor(host.colorDigit);
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    float left = offsetX + c * cellSize;
                    float top = offsetY + r * cellSize;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(cellBackground(r, c));
                    canvas.drawRect(left + 1, top + 1, left + cellSize - 1, top + cellSize - 1, paint);
                    int value = grid[r][c];
                    if (value != 0) {
                        canvas.drawText(String.valueOf(value), left + cellSize / 2f,
                                top + cellSize / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
                                textPaint);
                    }
                }
            }
            // 细网格线，宫分隔（每 3 格）加粗
            paint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i <= BOARD_SIZE; i++) {
                boolean boxEdge = i % 3 == 0;
                paint.setColor(boxEdge ? host.colorBoxLine : host.colorGridLine);
                paint.setStrokeWidth(boxEdge ? 3f : 1f);
                float offset = i * cellSize;
                canvas.drawLine(offsetX + offset, offsetY,
                        offsetX + offset, offsetY + BOARD_SIZE * cellSize, paint);
                canvas.drawLine(offsetX, offsetY + offset,
                        offsetX + BOARD_SIZE * cellSize, offsetY + offset, paint);
            }
        }

        private int cellBackground(int r, int c) {
            if (conflictMask.length == BOARD_SIZE && conflictMask[r].length == BOARD_SIZE
                    && conflictMask[r][c]) {
                return host.colorConflict;
            }
            if (r == selectedRow && c == selectedCol) return host.colorSelected;
            // 选中格所在宫铺底色，帮助定位 3×3 区域
            if (selectedRow >= 0 && selectedCol >= 0
                    && r / 3 == selectedRow / 3 && c / 3 == selectedCol / 3) {
                return host.colorBoxTint;
            }
            return host.colorCell;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                int col = (int) ((event.getX() - offsetX) / cellSize);
                int row = (int) ((event.getY() - offsetY) / cellSize);
                if (row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE) {
                    // 盘面数据由宿主持有，选格逻辑统一走宿主
                    if (host != null) host.onCellPicked(row, col);
                    if (action == MotionEvent.ACTION_DOWN) performClick();
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
