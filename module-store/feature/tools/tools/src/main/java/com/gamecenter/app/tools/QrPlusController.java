package com.gamecenter.app.tools;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 二维码增强控制器（qr_plus）：美化控制面板 + 批量生码 + 生码历史 + 保存/分享。
 * <p>
 * 布局真源在宿主 {@code item_tool_qr_plus.xml}（模块以 compileOnly 方式引用宿主 R），
 * 因此本类的扩展 UI 全部纯代码构建（风格对齐 InstalledAppsToolBinder 的行式列表），
 * 动态插入既有布局的预览图前后，避免改动宿主资源。
 * </p>
 * <p>
 * 生成逻辑接管自 AdvancedToolBinders 原私有 renderQr/createQr：
 * 统一走 {@link #render(String)}，应用样式面板参数并记录生码历史。
 * 几何/策略常量在 {@link QrStyleMath}，历史编解码在 {@link QrHistoryCodec}。
 * </p>
 */
final class QrPlusController {

    /** ZXing 静区模块数（固定值，不向 UI 暴露边距设置） */
    private static final int QUIET_ZONE_MODULES = QrStyleMath.QUIET_ZONE_MODULES;

    /** 批量生码行数上限 */
    private static final int BATCH_MAX_LINES = QrStyleMath.BATCH_MAX_LINES;

    /** 批量小码渲染尺寸（像素） */
    private static final int BATCH_QR_SIZE = QrStyleMath.BATCH_QR_SIZE;

    /** 前景色板（参考草料二维码预设色） */
    private static final int[] FG_PALETTE = {
            0xFF17171C, // 黑
            0xFF1546A0, // 深蓝
            0xFF1E6B33, // 深绿
            0xFF6D28A8, // 紫
            0xFF8E1F3F, // 酒红
            0xFF4B5563  // 灰
    };

    /** 背景色板 */
    private static final int[] BG_PALETTE = {
            0xFFFFFFFF, // 白
            0xFFFFF3C4  // 浅黄
    };

    private final Context appContext;
    private final View anchor;
    private final EditText input;
    private final TextView result;
    private final ImageView preview;
    private final QrHistoryStore history;

    // 样式状态（由面板控件驱动）
    private int fgColor = FG_PALETTE[0];
    private int bgColor = BG_PALETTE[0];
    private boolean withLogo = false;
    private char ecLevel = 'M';

    // 动态构建的控件引用
    private TextInputEditText captionInput;
    private CheckBox logoCheck;
    private CheckBox batchCheck;
    private LinearLayout batchList;
    private LinearLayout historySection;
    private LinearLayout historyList;
    private CharSequence inputOriginalHint;

    /** 最近一次渲染完成的完整 Bitmap（保存/分享的操作对象） */
    private Bitmap lastBitmap;

    /** save/share 进行中持有 lastBitmap，替换时不得 recycle */
    private final ToolIo.BitmapHold bitmapHold = new ToolIo.BitmapHold();

    /**
     * 构建控制器并挂接扩展 UI。
     */
    QrPlusController(Context context, View contentView, EditText input,
            TextView result, ImageView preview) {
        this.appContext = context.getApplicationContext();
        this.anchor = contentView;
        this.input = input;
        this.result = result;
        this.preview = preview;
        this.history = new QrHistoryStore(appContext);
        if (input != null && input.getParent() instanceof TextInputLayout) {
            inputOriginalHint = ((TextInputLayout) input.getParent()).getHint();
        }
        buildPanel(contentView);
        refreshHistory();
    }

    /**
     * 统一生成入口：生成/剪贴板/WiFi码/名片码按钮均走此方法。
     */
    void render(String content) {
        if (content == null || content.trim().isEmpty()) {
            Toast.makeText(appContext, R.string.tool_input_qr_content, Toast.LENGTH_SHORT).show();
            return;
        }
        if (batchCheck != null && batchCheck.isChecked()) {
            renderBatch(content);
        } else {
            if (batchList != null) {
                batchList.removeAllViews();
                batchList.setVisibility(View.GONE);
            }
            renderSingle(content, true);
        }
    }

    /**
     * 渲染单张全尺寸二维码。
     *
     * @param recordIntoHistory 是否记入生码历史（用户主动生成记，预览放大不记）
     */
    private void renderSingle(String content, boolean recordIntoHistory) {
        try {
            Bitmap bitmap = QrStyleRenderer.renderStyledQr(content, fgColor, bgColor,
                    withLogo, readCaption(), QUIET_ZONE_MODULES, ecLevel);
            Bitmap previous = lastBitmap;
            lastBitmap = bitmap;
            // 旧图在无 save/share 持有时回收，避免 720px ARGB 堆积
            if (previous != null && previous != bitmap && !previous.isRecycled()
                    && bitmapHold.canRecycle()) {
                previous.recycle();
            }
            if (preview != null) {
                preview.setImageBitmap(bitmap);
                preview.setVisibility(View.VISIBLE);
            }
            if (result != null) {
                result.setText(content);
            }
            if (recordIntoHistory) {
                history.record(content);
                refreshHistory();
            }
        } catch (Exception e) {
            if (result != null) {
                result.setText(appContext.getString(R.string.tool_diag_qr_failed_format, e.getMessage()));
            }
        }
    }

    /**
     * 批量渲染：每行一条内容，逐条生成小码列表预览。
     */
    private void renderBatch(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        if (lines.isEmpty()) {
            Toast.makeText(appContext, R.string.tool_input_qr_content, Toast.LENGTH_SHORT).show();
            return;
        }
        if (lines.size() > BATCH_MAX_LINES) {
            Toast.makeText(appContext,
                    appContext.getString(R.string.tool_qr_plus_batch_limit_format,
                            BATCH_MAX_LINES, lines.size()),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (batchList == null) return;
        batchList.removeAllViews();
        batchList.setVisibility(View.VISIBLE);
        String caption = readCaption();
        for (String line : lines) {
            history.record(line);
            try {
                Bitmap small = QrStyleRenderer.renderStyledQr(line, fgColor, bgColor,
                        withLogo, caption, QUIET_ZONE_MODULES, ecLevel, BATCH_QR_SIZE);
                batchList.addView(createBatchRow(small, line));
            } catch (Exception e) {
                TextView failed = new TextView(batchList.getContext());
                failed.setText(appContext.getString(
                        R.string.tool_qr_plus_batch_item_failed_format, line, String.valueOf(e.getMessage())));
                failed.setTextColor(resolveTextColor(appContext));
                failed.setTextSize(13);
                batchList.addView(failed);
            }
        }
        refreshHistory();
    }

    /**
     * 构建批量列表中的一行：40dp 小码 + 内容文本，点击放大预览。
     */
    private View createBatchRow(Bitmap small, String line) {
        Context ctx = batchList.getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(makePanelBackground(ctx));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(rowParams);

        ImageView image = new ImageView(ctx);
        image.setImageBitmap(small);
        image.setContentDescription(ctx.getString(R.string.tool_qr_plus_preview_desc));
        int side = dp(40);
        row.addView(image, new LinearLayout.LayoutParams(side, side));

        TextView text = new TextView(ctx);
        text.setText(line);
        text.setTextColor(resolveTextColor(ctx));
        text.setTextSize(13);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMargins(dp(10), 0, 0, 0);
        textParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        row.addView(text, textParams);

        row.setOnClickListener(v -> renderSingle(line, false));
        return row;
    }

    // ---------------------------------------------------------------------
    // 扩展 UI 构建（纯代码，插入既有布局）
    // ---------------------------------------------------------------------

    private void buildPanel(View contentView) {
        if (!(contentView instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) contentView;
        int previewIndex = preview != null ? root.indexOfChild(preview) : -1;

        View actionRow = buildSaveShareRow();
        if (previewIndex >= 0) {
            root.addView(actionRow, previewIndex + 1);
            root.addView(buildStylePanel(), previewIndex);
        } else {
            root.addView(buildStylePanel());
            root.addView(actionRow);
        }

        batchList = new LinearLayout(root.getContext());
        batchList.setOrientation(LinearLayout.VERTICAL);
        batchList.setVisibility(View.GONE);
        int actionIndex = root.indexOfChild(actionRow);
        root.addView(batchList, actionIndex + 1);

        historySection = buildHistorySection();
        root.addView(historySection);
    }

    private View buildStylePanel() {
        Context ctx = anchor.getContext();
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackground(makePanelBackground(ctx));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        panel.setLayoutParams(params);

        panel.addView(buildSwatchRow(ctx.getString(R.string.tool_qr_plus_fg_label), FG_PALETTE, true));
        panel.addView(buildSwatchRow(ctx.getString(R.string.tool_qr_plus_bg_label), BG_PALETTE, false));

        logoCheck = new CheckBox(ctx);
        logoCheck.setText(R.string.tool_qr_plus_logo_check);
        logoCheck.setTextSize(13);
        logoCheck.setTextColor(resolveTextColor(ctx));
        logoCheck.setOnCheckedChangeListener((button, checked) -> withLogo = checked);
        panel.addView(logoCheck, marginTopParams(dp(4)));

        panel.addView(buildEcLevelRow());

        TextInputLayout captionLayout = new TextInputLayout(ctx);
        captionInput = new TextInputEditText(ctx);
        captionInput.setHint(R.string.tool_qr_plus_caption_hint);
        captionInput.setTextSize(14);
        captionInput.setMaxLines(1);
        captionInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        captionLayout.addView(captionInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(captionLayout, marginTopParams(dp(4)));

        batchCheck = new CheckBox(ctx);
        batchCheck.setText(ctx.getString(R.string.tool_qr_plus_batch_check, BATCH_MAX_LINES));
        batchCheck.setTextSize(13);
        batchCheck.setTextColor(resolveTextColor(ctx));
        batchCheck.setOnCheckedChangeListener((button, checked) -> switchBatchMode(checked));
        panel.addView(batchCheck, marginTopParams(dp(4)));
        return panel;
    }

    /**
     * 色板行：label + 圆形/圆角色块。视觉 28dp，触达外框 48dp。
     */
    private View buildSwatchRow(String label, int[] colors, boolean forForeground) {
        Context ctx = anchor.getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(ctx);
        labelView.setText(label);
        labelView.setTextColor(resolveTextColor(ctx));
        labelView.setTextSize(12);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(48),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        int current = forForeground ? fgColor : bgColor;
        for (int color : colors) {
            View swatch = new View(ctx);
            // 触达 48dp：InsetDrawable 把 28dp 色块居中，外框透明扩 hit 区
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
            swatch.setLayoutParams(lp);
            swatch.setBackground(makeTouchSwatchDrawable(ctx, color, color == current, forForeground));
            swatch.setContentDescription(ctx.getString(
                    forForeground ? R.string.tool_qr_plus_swatch_desc : R.string.tool_qr_plus_bg_swatch_desc,
                    String.format(Locale.getDefault(), "#%06X", (0xFFFFFF & color))));
            swatch.setOnClickListener(v -> {
                if (forForeground) {
                    fgColor = color;
                } else {
                    bgColor = color;
                }
                restyleSwatches(row, color, forForeground);
            });
            row.addView(swatch);
        }
        return row;
    }

    private void restyleSwatches(LinearLayout row, int selected, boolean forForeground) {
        Context ctx = row.getContext();
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (i == 0) continue;
            int color = forForeground ? FG_PALETTE[i - 1] : BG_PALETTE[i - 1];
            child.setBackground(makeTouchSwatchDrawable(ctx, color, color == selected, forForeground));
        }
    }

    /**
     * 48dp 触达色块：28dp 视觉色块居中，外框透明。
     */
    private android.graphics.drawable.Drawable makeTouchSwatchDrawable(
            Context ctx, int color, boolean selected, boolean oval) {
        int inset = dp(10);
        return new android.graphics.drawable.InsetDrawable(
                makeSwatchDrawable(color, selected, oval), inset);
    }

    private GradientDrawable makeSwatchDrawable(int color, boolean selected, boolean oval) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        if (oval) {
            drawable.setShape(GradientDrawable.OVAL);
        } else {
            drawable.setCornerRadius(dp(8));
        }
        if (selected) {
            drawable.setStroke(dp(2), resolveColorPrimary(anchor.getContext()));
        }
        return drawable;
    }

    private View buildEcLevelRow() {
        Context ctx = anchor.getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(ctx);
        labelView.setText(R.string.tool_qr_plus_ec_label);
        labelView.setTextColor(resolveTextColor(ctx));
        labelView.setTextSize(12);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(48),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        RadioGroup group = new RadioGroup(ctx);
        group.setOrientation(LinearLayout.HORIZONTAL);
        for (char level : new char[]{'L', 'M', 'Q', 'H'}) {
            RadioButton button = new RadioButton(ctx);
            button.setText(String.valueOf(level));
            button.setTextSize(12);
            button.setChecked(level == 'M');
            group.addView(button, new RadioGroup.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        group.setOnCheckedChangeListener((group1, checkedId) -> {
            RadioButton checked = group1.findViewById(checkedId);
            if (checked != null) {
                ecLevel = checked.getText().charAt(0);
            }
        });
        row.addView(group, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View buildSaveShareRow() {
        Context ctx = anchor.getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton save = new MaterialButton(ctx);
        save.setText(R.string.tool_qr_plus_save);
        save.setTextSize(12);
        save.setOnClickListener(v -> saveLastBitmap());
        row.addView(save, buttonParams());

        MaterialButton share = new MaterialButton(ctx);
        share.setText(R.string.tool_qr_plus_share);
        share.setTextSize(12);
        share.setOnClickListener(v -> shareLastBitmap());
        row.addView(share, buttonParams());
        return row;
    }

    private LinearLayout buildHistorySection() {
        Context ctx = anchor.getContext();
        historySection = new LinearLayout(ctx);
        historySection.setOrientation(LinearLayout.VERTICAL);
        historySection.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = new TextView(ctx);
        title.setText(R.string.tool_qr_plus_history_title);
        title.setTextColor(resolveTextColor(ctx));
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialButton clear = new MaterialButton(ctx);
        clear.setText(R.string.tool_qr_plus_history_clear);
        clear.setTextSize(12);
        clear.setOnClickListener(v -> confirmClearHistory());
        header.addView(clear, buttonParams());

        historySection.addView(header, marginTopParams(dp(8)));

        historyList = new LinearLayout(ctx);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historySection.addView(historyList, marginTopParams(dp(4)));
        return historySection;
    }

    private void confirmClearHistory() {
        List<QrHistoryStore.Entry> entries = history.load();
        if (entries.isEmpty()) return;
        new AlertDialog.Builder(anchor.getContext())
                .setTitle(R.string.tool_qr_plus_history_clear_confirm_title)
                .setMessage(appContext.getString(
                        R.string.tool_qr_plus_history_clear_confirm_message, entries.size()))
                .setPositiveButton(R.string.tool_qr_plus_history_clear_confirm_ok, (d, w) -> {
                    history.clear();
                    refreshHistory();
                    Toast.makeText(appContext, R.string.tool_qr_plus_history_cleared,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshHistory() {
        if (historyList == null) return;
        List<QrHistoryStore.Entry> entries = history.load();
        historyList.removeAllViews();
        if (entries.isEmpty()) {
            historySection.setVisibility(View.GONE);
            return;
        }
        historySection.setVisibility(View.VISIBLE);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (QrHistoryStore.Entry entry : entries) {
            historyList.addView(createHistoryRow(entry, format));
        }
    }

    private View createHistoryRow(QrHistoryStore.Entry entry, SimpleDateFormat format) {
        Context ctx = historyList.getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(10);
        row.setPadding(padding, padding, padding, padding);
        row.setBackground(makePanelBackground(ctx));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(rowParams);

        TextView content = new TextView(ctx);
        content.setText(entry.content);
        content.setTextColor(resolveTextColor(ctx));
        content.setTextSize(14);
        content.setSingleLine(true);
        content.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(content);

        TextView time = new TextView(ctx);
        time.setText(format.format(new Date(entry.timestamp)));
        time.setTextColor(resolveTextColorSecondary(ctx));
        time.setTextSize(11);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(2);
        row.addView(time, timeParams);

        row.setOnClickListener(v -> {
            if (input != null) {
                input.setText(entry.content);
            }
            renderSingle(entry.content, false);
        });
        row.setOnLongClickListener(v -> {
            history.remove(entry.content);
            refreshHistory();
            Toast.makeText(appContext, R.string.tool_qr_plus_history_deleted,
                    Toast.LENGTH_SHORT).show();
            return true;
        });
        return row;
    }

    private void switchBatchMode(boolean enabled) {
        if (batchList != null && !enabled) {
            batchList.removeAllViews();
            batchList.setVisibility(View.GONE);
        }
        if (input != null && input.getParent() instanceof TextInputLayout
                && inputOriginalHint != null) {
            TextInputLayout layout = (TextInputLayout) input.getParent();
            layout.setHint(enabled
                    ? appContext.getString(R.string.tool_qr_plus_batch_hint, BATCH_MAX_LINES)
                    : inputOriginalHint);
        }
        if (input != null) {
            input.setMaxLines(enabled ? 12 : 4);
        }
    }

    // ---------------------------------------------------------------------
    // 保存 / 分享
    // ---------------------------------------------------------------------

    private void saveLastBitmap() {
        Bitmap snapshot = lastBitmap;
        if (snapshot == null || snapshot.isRecycled()) {
            Toast.makeText(appContext, R.string.tool_qr_plus_generate_first, Toast.LENGTH_SHORT).show();
            return;
        }
        bitmapHold.acquire();
        ExecutorService executor = ToolIo.require(null);
        executor.execute(() -> {
            try {
                Uri uri = QrImageIo.saveToGallery(appContext, snapshot, QrImageIo.displayName());
                anchor.post(() -> {
                    if (uri != null) {
                        Toast.makeText(appContext,
                                appContext.getString(R.string.tool_qr_plus_saved_format,
                                        QrImageIo.galleryPathLabel()),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(appContext, R.string.tool_qr_plus_save_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } finally {
                bitmapHold.release();
            }
        });
    }

    private void shareLastBitmap() {
        Bitmap snapshot = lastBitmap;
        if (snapshot == null || snapshot.isRecycled()) {
            Toast.makeText(appContext, R.string.tool_qr_plus_generate_first, Toast.LENGTH_SHORT).show();
            return;
        }
        bitmapHold.acquire();
        ExecutorService executor = ToolIo.require(null);
        executor.execute(() -> {
            try {
                boolean shared = QrImageIo.sharePng(appContext, snapshot);
                if (!shared) {
                    anchor.post(() -> Toast.makeText(appContext, R.string.tool_qr_plus_share_failed,
                            Toast.LENGTH_SHORT).show());
                }
            } finally {
                bitmapHold.release();
            }
        });
    }

    // ---------------------------------------------------------------------
    // 小工具
    // ---------------------------------------------------------------------

    private String readCaption() {
        return captionInput != null && captionInput.getText() != null
                ? captionInput.getText().toString() : "";
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, dp(48), 1f);
        int margin = dp(3);
        lp.setMargins(margin, margin, margin, margin);
        return lp;
    }

    private LinearLayout.LayoutParams marginTopParams(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * anchor.getResources().getDisplayMetrics().density);
    }

    /** 主题面板底色：colorSurfaceVariant 约 12% 透明，深浅色自适应。 */
    private static GradientDrawable makePanelBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(resolveSurfaceVariant(context));
        background.setCornerRadius(Math.round(12 * context.getResources().getDisplayMetrics().density));
        return background;
    }

    private static int resolveTextColor(Context context) {
        TypedArray colors = context.obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary});
        try {
            return colors.getColor(0, Color.WHITE);
        } finally {
            colors.recycle();
        }
    }

    private static int resolveTextColorSecondary(Context context) {
        TypedArray colors = context.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary});
        try {
            return colors.getColor(0, 0x99000000);
        } finally {
            colors.recycle();
        }
    }

    private static int resolveColorPrimary(Context context) {
        TypedArray colors = context.obtainStyledAttributes(
                new int[]{androidx.appcompat.R.attr.colorPrimary});
        try {
            return colors.getColor(0, 0xFF1565C0);
        } finally {
            colors.recycle();
        }
    }

    private static int resolveSurfaceVariant(Context context) {
        TypedArray colors = context.obtainStyledAttributes(
                new int[]{com.google.android.material.R.attr.colorSurfaceVariant});
        try {
            return colors.getColor(0, 0x12000000);
        } finally {
            colors.recycle();
        }
    }
}
