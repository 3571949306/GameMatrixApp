package com.gamecenter.app.sokoban;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import com.gamecenter.app.games.ShareCardGenerator;

/**
 * 推箱子自定义关卡编辑器 Fragment（纯代码构建 UI，与模块风格一致）。
 *
 * <p>画板用网格触摸绘制（单指点/拖），工具含墙/地板/目标/箱子/玩家/擦除，
 * 放置箱子或玩家到目标点自动组合为 BOX_ON_TARGET / PLAYER_ON_TARGET，
 * 放置新玩家时自动迁移旧玩家保证唯一。保存前经 {@link SokobanGame#startCustomLevel(int[][])}
 * 校验闸门，非法布局一律拒绝保存。关卡码经 {@link SokobanLevelCodec} 导入导出，
 * 用于跨设备分享。</p>
 *
 * <p>宿主（{@link SokobanModuleFragment}）以 replace + addToBackStack 打开本编辑器；
 * 返回时宿主 Fragment 重建，自定义关卡列表自然刷新。</p>
 */
public class SokobanEditorFragment extends Fragment {

    /** 网格尺寸范围（与 {@link SokobanLevelCodec#MAX_DIM} 校验留出余量）。 */
    private static final int MIN_DIM = 4;
    private static final int MAX_DIM = 16;
    private static final int DEFAULT_ROWS = 8;
    private static final int DEFAULT_COLS = 8;

    // 主题色
    private int colorBg;
    private int colorText;
    private int colorToolSelected;
    private int colorToolNormal;

    // 编辑状态
    private int[][] grid = new int[DEFAULT_ROWS][DEFAULT_COLS];
    private int currentTool = SokobanGame.WALL;
    private int selectedToolViewId = -1;

    // UI
    private EditorCanvasView canvas;
    private TextView tvValidation;
    private EditText etName;
    private TextView tvSize;
    private final Button[] toolButtons = new Button[6];

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

        // 标题 + 关卡名输入
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("关卡编辑器");
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextSize(18f);
        tvTitle.setTextColor(colorText);
        root.addView(tvTitle, matchWrapParams(dp));

        etName = new EditText(ctx);
        etName.setHint("关卡名（选填）");
        etName.setMaxLines(1);
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        etName.setTextColor(colorText);
        etName.setHintTextColor(0xFF888888);
        root.addView(etName, matchWrapParams(dp));

        // 工具面板：两行三列
        root.addView(buildToolRow(ctx, dp, 0, 3, minimumTouchSize));
        root.addView(buildToolRow(ctx, dp, 3, 6, minimumTouchSize));

        // 尺寸控制
        LinearLayout sizeRow = new LinearLayout(ctx);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER);
        tvSize = new TextView(ctx);
        tvSize.setTextColor(colorText);
        tvSize.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
        sizeRow.addView(tvSize, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sizeRow.addView(buildSizeButton(ctx, dp, minimumTouchSize, "行-", v -> resizeGrid(-1, 0)));
        sizeRow.addView(buildSizeButton(ctx, dp, minimumTouchSize, "行+", v -> resizeGrid(1, 0)));
        sizeRow.addView(buildSizeButton(ctx, dp, minimumTouchSize, "列-", v -> resizeGrid(0, -1)));
        sizeRow.addView(buildSizeButton(ctx, dp, minimumTouchSize, "列+", v -> resizeGrid(0, 1)));
        sizeRow.addView(buildSizeButton(ctx, dp, minimumTouchSize, "清空", v -> clearGrid()));
        root.addView(sizeRow, matchWrapParams(dp));

        // 画板
        canvas = new EditorCanvasView(ctx, this);
        canvas.setGrid(grid);
        root.addView(canvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 校验状态
        tvValidation = new TextView(ctx);
        tvValidation.setGravity(Gravity.CENTER);
        tvValidation.setTextSize(13f);
        tvValidation.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        root.addView(tvValidation, matchWrapParams(dp));

        // 底部操作
        LinearLayout actionRow = new LinearLayout(ctx);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.addView(buildActionButton(ctx, dp, minimumTouchSize, "保存", v -> saveLevel()));
        actionRow.addView(buildActionButton(ctx, dp, minimumTouchSize, "导入码", v -> showImportDialog()));
        actionRow.addView(buildActionButton(ctx, dp, minimumTouchSize, "导出码", v -> showExportDialog()));
        actionRow.addView(buildActionButton(ctx, dp, minimumTouchSize, "完成", v -> exitEditor()));
        root.addView(actionRow, matchWrapParams(dp));

        applyToolSelection();
        refreshValidation();
        return root;
    }

    // ==================== 工具与画板操作 ====================

    private LinearLayout buildToolRow(Context ctx, float dp, int from, int to, int minTouch) {
        int[] cells = {
                SokobanGame.WALL, SokobanGame.FLOOR, SokobanGame.TARGET,
                SokobanGame.BOX, SokobanGame.PLAYER, SokobanGame.EMPTY,
        };
        String[] labels = {"墙", "地板", "目标", "箱子", "玩家", "擦除"};
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = from; i < to; i++) {
            final int cell = cells[i];
            Button btn = new Button(ctx);
            btn.setText(labels[i]);
            btn.setTextSize(13f);
            btn.setId(1000 + i);
            btn.setMinimumHeight(minTouch);
            btn.setMinHeight(minTouch);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                currentTool = cell;
                applyToolSelection();
            });
            toolButtons[i] = btn;
            row.addView(btn);
        }
        return row;
    }

    private Button buildSizeButton(Context ctx, float dp, int minTouch, String text, View.OnClickListener listener) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setMinimumHeight(minTouch);
        btn.setMinHeight(minTouch);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private Button buildActionButton(Context ctx, float dp, int minTouch, String text, View.OnClickListener listener) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setMinimumHeight(minTouch);
        btn.setMinHeight(minTouch);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (2 * dp), 0, (int) (2 * dp), 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private LinearLayout.LayoutParams matchWrapParams(float dp) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void applyToolSelection() {
        int[] tools = {
                SokobanGame.WALL, SokobanGame.FLOOR, SokobanGame.TARGET,
                SokobanGame.BOX, SokobanGame.PLAYER, SokobanGame.EMPTY,
        };
        for (int i = 0; i < toolButtons.length; i++) {
            Button btn = toolButtons[i];
            if (btn == null) continue;
            boolean selected = tools[i] == currentTool;
            btn.setBackgroundColor(selected ? colorToolSelected : colorToolNormal);
            btn.setTextColor(selected ? Color.WHITE : colorText);
        }
    }

    /** 按当前工具设置画板格子（自动组合目标点上的实体，保持玩家唯一）。 */
    private void setGridCell(int row, int col) {
        if (row < 0 || row >= grid.length || col < 0 || col >= grid[0].length) return;
        int existing = grid[row][col];
        int value = currentTool;
        if (value == SokobanGame.BOX
                && (existing == SokobanGame.TARGET || existing == SokobanGame.PLAYER_ON_TARGET)) {
            value = SokobanGame.BOX_ON_TARGET;
        } else if (value == SokobanGame.PLAYER && existing == SokobanGame.TARGET) {
            value = SokobanGame.PLAYER_ON_TARGET;
        } else if (value == SokobanGame.TARGET) {
            if (existing == SokobanGame.BOX) value = SokobanGame.BOX_ON_TARGET;
            else if (existing == SokobanGame.PLAYER) value = SokobanGame.PLAYER_ON_TARGET;
        }
        if (value == SokobanGame.PLAYER || value == SokobanGame.PLAYER_ON_TARGET) {
            // 玩家唯一：清除旧玩家
            for (int r = 0; r < grid.length; r++) {
                for (int c = 0; c < grid[0].length; c++) {
                    if (grid[r][c] == SokobanGame.PLAYER) grid[r][c] = SokobanGame.FLOOR;
                    else if (grid[r][c] == SokobanGame.PLAYER_ON_TARGET) grid[r][c] = SokobanGame.TARGET;
                }
            }
        }
        grid[row][col] = value;
        canvas.setGrid(grid);
        refreshValidation();
    }

    private void resizeGrid(int rowDelta, int colDelta) {
        int newRows = Math.max(MIN_DIM, Math.min(MAX_DIM, grid.length + rowDelta));
        int newCols = Math.max(MIN_DIM, Math.min(MAX_DIM, grid[0].length + colDelta));
        if (newRows == grid.length && newCols == grid[0].length) return;
        int[][] next = new int[newRows][newCols];
        for (int r = 0; r < Math.min(grid.length, newRows); r++) {
            System.arraycopy(grid[r], 0, next[r], 0, Math.min(grid[0].length, newCols));
        }
        grid = next;
        canvas.setGrid(grid);
        refreshValidation();
    }

    private void clearGrid() {
        grid = new int[grid.length][grid[0].length];
        canvas.setGrid(grid);
        refreshValidation();
    }

    /** 实时校验信息：玩家数/箱子数/目标数与合法性。 */
    private void refreshValidation() {
        tvSize.setText(grid.length + "×" + grid[0].length);
        int players = 0, boxes = 0, targets = 0;
        for (int[] row : grid) {
            for (int cell : row) {
                if (cell == SokobanGame.PLAYER || cell == SokobanGame.PLAYER_ON_TARGET) players++;
                if (cell == SokobanGame.BOX || cell == SokobanGame.BOX_ON_TARGET) boxes++;
                if (cell == SokobanGame.TARGET || cell == SokobanGame.BOX_ON_TARGET
                        || cell == SokobanGame.PLAYER_ON_TARGET) targets++;
            }
        }
        boolean valid = players == 1 && boxes >= 1 && boxes == targets;
        String verdict = valid ? "✓ 可保存" : "✗ 尚不可保存";
        String reason;
        if (players != 1) reason = "需恰 1 个玩家";
        else if (boxes < 1) reason = "至少 1 个箱子";
        else if (boxes != targets) reason = "箱子数需等于目标数";
        else reason = "";
        String msg = "玩家 " + players + " · 箱子 " + boxes + " · 目标 " + targets
                + " · " + verdict + (reason.isEmpty() ? "" : "（" + reason + "）");
        tvValidation.setText(msg);
        tvValidation.setTextColor(valid ? 0xFF2D6A4F : 0xFFC44536);
    }

    // ==================== 保存 / 导入 / 导出 ====================

    private void saveLevel() {
        // 校验闸门：与游戏装载同一套规则（经 SokobanGame.startCustomLevel 试装载）
        SokobanGame probe = new SokobanGame();
        if (!probe.startCustomLevel(grid)) {
            Toast.makeText(requireContext(), "关卡不合法，无法保存", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = etName.getText().toString();
        int id = SokobanCustomLevels.add(requireContext(), name, grid);
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
        input.setHint("粘贴关卡码");
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        FrameLayout wrap = new FrameLayout(ctx);
        int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.addView(input);
        new AlertDialog.Builder(ctx)
                .setTitle("导入关卡码")
                .setView(wrap)
                .setPositiveButton("导入", (d, w) -> {
                    int[][] decoded = SokobanLevelCodec.decode(input.getText().toString());
                    if (decoded == null) {
                        Toast.makeText(ctx, "关卡码无效", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    grid = decoded;
                    canvas.setGrid(grid);
                    refreshValidation();
                    Toast.makeText(ctx, "导入成功，可继续编辑或直接保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showExportDialog() {
        Context ctx = requireContext();
        String code = SokobanLevelCodec.encode(grid);
        if (code == null) {
            Toast.makeText(ctx, "当前网格无法导出", Toast.LENGTH_SHORT).show();
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
                .setTitle("关卡码（复制分享）")
                .setView(wrap)
                .setPositiveButton("复制", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("sokoban_level", code));
                        Toast.makeText(ctx, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
                    }
                })
                // 分享卡片入口：生成带关卡码二维码的战报卡，发起系统分享
                .setNeutralButton("分享卡片", (d, w) -> shareLevelCard(code))
                .setNegativeButton("关闭", null)
                .show();
    }

    /**
     * 生成带关卡码二维码的分享卡片并发起系统分享。
     *
     * <p>跨用户传播闭环：本机编辑器「导出码」→ 分享卡片（二维码 + 附带关卡码明文）→
     * 接收方扫码或复制文本得到关卡码 → 其编辑器「导入码」粘贴即得同一关卡，实现 UGC 跨用户传播。</p>
     */
    private void shareLevelCard(String code) {
        if (code == null) {
            Toast.makeText(requireContext(), "当前网格无法导出", Toast.LENGTH_SHORT).show();
            return;
        }
        // 在主线程先取好 ApplicationContext，避免后台回调时 Fragment 已销毁
        final Context appContext = requireContext().getApplicationContext();
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        ShareCardGenerator.Data data = new ShareCardGenerator.Data();
        data.gameName = "推箱子·自定义关卡";
        data.gameId = "sokoban";
        data.gameIconRes = 0; // 宿主无推箱子专属图标资源，保持默认占位圆
        data.qrPayload = code; // 卡片二维码与分享文本均携带关卡码
        // Bitmap 绘制 + PNG 编码较重，放后台线程（与宿主 shareGameStats 同一模式）
        new Thread(() -> {
            ShareCardGenerator generator = new ShareCardGenerator(appContext);
            final Intent share = generator.buildShareIntent(data);
            mainHandler.post(() -> {
                if (share == null) {
                    Toast.makeText(appContext, "分享卡片生成失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    // appContext 启动 Activity 需要 NEW_TASK；share 已是 chooser Intent
                    share.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    appContext.startActivity(share);
                } catch (Exception e) {
                    Toast.makeText(appContext, "分享卡片生成失败", Toast.LENGTH_SHORT).show();
                }
            });
        }, "sokoban_share_card").start();
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
        colorToolSelected = dark ? 0xFF3949AB : 0xFF3F51B5;
        colorToolNormal = dark ? 0xFF26304A : 0xFFDDDDDD;
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    // ==================== 画板 View ====================

    /** 网格画板：触摸绘制 + 渲染。 */
    private static final class EditorCanvasView extends View {

        private static final int COLOR_GRID_BG = Color.parseColor("#F5F0E8");
        private static final int COLOR_EMPTY = Color.parseColor("#EFEFEF");
        private static final int COLOR_WALL = Color.parseColor("#5B8A72");
        private static final int COLOR_FLOOR = Color.parseColor("#E8E0D0");
        private static final int COLOR_TARGET = Color.parseColor("#C44536");
        private static final int COLOR_BOX = Color.parseColor("#8B7355");
        private static final int COLOR_BOX_ON_TARGET = Color.parseColor("#5B8A72");
        private static final int COLOR_PLAYER = Color.parseColor("#2D6A4F");
        private static final int COLOR_GRID_LINE = Color.parseColor("#BBBBBB");

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[][] grid = new int[0][0];
        private float cellSize;
        private float offsetX;
        private float offsetY;
        private final SokobanEditorFragment host;

        EditorCanvasView(Context context, SokobanEditorFragment host) {
            super(context);
            this.host = host;
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setGrid(int[][] grid) {
            this.grid = grid;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            computeCellSize(w, h);
        }

        private void computeCellSize(int w, int h) {
            if (grid.length == 0 || grid[0].length == 0) return;
            float sizeByWidth = w / (float) grid[0].length;
            float sizeByHeight = h / (float) grid.length;
            cellSize = Math.min(sizeByWidth, sizeByHeight);
            offsetX = (w - cellSize * grid[0].length) / 2f;
            offsetY = (h - cellSize * grid.length) / 2f;
            textPaint.setTextSize(cellSize * 0.4f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (grid.length == 0 || grid[0].length == 0) return;
            canvas.drawColor(COLOR_GRID_BG);
            for (int r = 0; r < grid.length; r++) {
                for (int c = 0; c < grid[0].length; c++) {
                    float left = offsetX + c * cellSize;
                    float top = offsetY + r * cellSize;
                    RectF rect = new RectF(left + 1, top + 1, left + cellSize - 1, top + cellSize - 1);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(cellColor(grid[r][c]));
                    canvas.drawRect(rect, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(COLOR_GRID_LINE);
                    canvas.drawRect(rect, paint);
                    String label = cellLabel(grid[r][c]);
                    if (label != null) {
                        canvas.drawText(label, rect.centerX(), rect.centerY() + textPaint.getTextSize() / 3, textPaint);
                    }
                }
            }
        }

        private int cellColor(int cell) {
            switch (cell) {
                case SokobanGame.WALL: return COLOR_WALL;
                case SokobanGame.FLOOR: return COLOR_FLOOR;
                case SokobanGame.TARGET: return COLOR_TARGET;
                case SokobanGame.BOX: return COLOR_BOX;
                case SokobanGame.BOX_ON_TARGET: return COLOR_BOX_ON_TARGET;
                case SokobanGame.PLAYER:
                case SokobanGame.PLAYER_ON_TARGET: return COLOR_PLAYER;
                default: return COLOR_EMPTY;
            }
        }

        private String cellLabel(int cell) {
            switch (cell) {
                case SokobanGame.TARGET: return "○";
                case SokobanGame.BOX: return "箱";
                case SokobanGame.BOX_ON_TARGET: return "✓";
                case SokobanGame.PLAYER:
                case SokobanGame.PLAYER_ON_TARGET: return "人";
                default: return null;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                int col = (int) ((event.getX() - offsetX) / cellSize);
                int row = (int) ((event.getY() - offsetY) / cellSize);
                if (row >= 0 && row < grid.length && col >= 0 && col < grid[0].length) {
                    // 网格内容与 Fragment 的 grid 数组同引用，直接走宿主的设置逻辑
                    if (host != null) {
                        host.setGridCell(row, col);
                    } else {
                        performClick();
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
