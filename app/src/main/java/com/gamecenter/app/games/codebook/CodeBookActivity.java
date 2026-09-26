package com.gamecenter.app.games.codebook;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.R;

import java.util.List;

/**
 * 关卡码合集本页 —— 收藏外部分享的关卡码（推箱子 / 数独），一键复制导入对应游戏编辑器。
 *
 * <p>功能：</p>
 * <ul>
 *   <li>添加区：标题输入（可选）+ 关卡码输入（多行粘贴）+ 添加按钮；
 *       添加前经 {@link CodeBookCodec#detect} 识别格式，UNKNOWN 拒收并 Toast 提示</li>
 *   <li>列表区：每条目一行——游戏名徽标（推箱子主题色 / 数独绿色）+ 标题
 *       + 「复制」「删除」两按钮；复制写入剪贴板并提示跳转对应编辑器导入</li>
 *   <li>空列表显示占位文案；onResume 全量刷新（外部页面返回后数据可能变化）</li>
 * </ul>
 *
 * <p>分工约定：本页只校验关卡码"格式合法"（detect），关卡内容合法性
 * （数独是否可解、推箱子地图是否有玩家/箱子）由对应游戏编辑器导入时校验。</p>
 */
public class CodeBookActivity extends AppCompatActivity {

    private static final String TAG = "CodeBookActivity";
    /** 数独徽标背景色（与金币钱包正向色一致，区别于推箱子的主题色徽标）。 */
    private static final String COLOR_SUDOKU_BADGE = "#5B8A72";

    private EditText etTitle;
    private EditText etCode;
    private LinearLayout layoutEntries;
    private TextView tvEmpty;

    /**
     * 启动关卡码合集本的便捷方法
     *
     * @param context 上下文
     */
    public static void launch(@NonNull Context context) {
        context.startActivity(new Intent(context, CodeBookActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_book);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        etTitle = findViewById(R.id.et_codebook_title);
        etCode = findViewById(R.id.et_codebook_code);
        layoutEntries = findViewById(R.id.layout_codebook_entries);
        tvEmpty = findViewById(R.id.tv_codebook_empty);

        findViewById(R.id.btn_codebook_add).setOnClickListener(v -> onAddClicked());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    /** 添加按钮：识别关卡码格式，UNKNOWN 拒收；合法则入库、清空输入并刷新列表。 */
    private void onAddClicked() {
        String title = etTitle.getText().toString();
        String code = etCode.getText().toString();
        CodeBookCodec.Kind kind = CodeBookCodec.detect(code);
        if (kind == CodeBookCodec.Kind.UNKNOWN) {
            Toast.makeText(this, R.string.codebook_unrecognized, Toast.LENGTH_SHORT).show();
            return;
        }
        CodeBookStore.add(this, title, code);
        Toast.makeText(this, getString(
                R.string.codebook_added_format, CodeBookCodec.gameName(kind)),
                Toast.LENGTH_SHORT).show();
        etTitle.setText("");
        etCode.setText("");
        refreshList();
    }

    /** 全量刷新列表；空列表切换占位文案可见性。 */
    private void refreshList() {
        layoutEntries.removeAllViews();
        List<CodeBookStore.Entry> entries = CodeBookStore.loadAll(this);
        tvEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        for (CodeBookStore.Entry entry : entries) {
            layoutEntries.addView(createItemRow(entry));
        }
    }

    /**
     * 单条收藏行：游戏名徽标（detect 现场识别，单一真相源在 Codec）
     * + 标题（超长省略）+ 「复制」「删除」两按钮。
     */
    private View createItemRow(CodeBookStore.Entry entry) {
        CodeBookCodec.Kind kind = CodeBookCodec.detect(entry.code);
        String gameName = CodeBookCodec.gameName(kind);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView tvBadge = new TextView(this);
        tvBadge.setText(gameName);
        tvBadge.setTextSize(11);
        tvBadge.setTextColor(Color.WHITE);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(8));
        badgeBg.setColor(badgeColor(kind));
        tvBadge.setBackground(badgeBg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(entry.title);
        tvTitle.setTextSize(14);
        tvTitle.setTextColor(themeColor(
                com.google.android.material.R.attr.colorOnSurface));
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(8);
        titleParams.rightMargin = dp(8);
        tvTitle.setLayoutParams(titleParams);

        // 项目硬性规范：所有 Button 显式关闭 stateListAnimator
        Button btnCopy = new Button(this);
        btnCopy.setStateListAnimator(null);
        btnCopy.setTextSize(12);
        btnCopy.setPadding(dp(12), dp(2), dp(12), dp(2));
        btnCopy.setText(R.string.codebook_copy);
        btnCopy.setOnClickListener(v -> copyEntry(entry, gameName));

        Button btnDelete = new Button(this);
        btnDelete.setStateListAnimator(null);
        btnDelete.setTextSize(12);
        btnDelete.setPadding(dp(12), dp(2), dp(12), dp(2));
        btnDelete.setText(R.string.codebook_delete);
        btnDelete.setOnClickListener(v -> {
            CodeBookStore.remove(CodeBookActivity.this, entry.id);
            refreshList();
        });

        row.addView(tvBadge);
        row.addView(tvTitle);
        row.addView(btnCopy);
        row.addView(btnDelete);
        return row;
    }

    /** 复制关卡码到剪贴板，并提示到对应游戏编辑器导入。 */
    private void copyEntry(CodeBookStore.Entry entry, String gameName) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            // 剪贴板服务不可用属异常环境：留日志并明确提示，不静默吞掉
            Log.w(TAG, "ClipboardService 不可用，复制失败");
            Toast.makeText(this, R.string.codebook_copy_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText("codebook_level", entry.code));
        Toast.makeText(this, getString(R.string.codebook_copied_format, gameName),
                Toast.LENGTH_SHORT).show();
    }

    /** 徽标颜色：推箱子随主题色，数独固定绿色，未知灰底兜底。 */
    private int badgeColor(CodeBookCodec.Kind kind) {
        if (kind == CodeBookCodec.Kind.SUDOKU) {
            return Color.parseColor(COLOR_SUDOKU_BADGE);
        }
        if (kind == CodeBookCodec.Kind.SOKOBAN) {
            return themeColor(androidx.appcompat.R.attr.colorPrimary);
        }
        return themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    /** 解析主题属性颜色（护眼主题可随 DayNight 切换）。 */
    private int themeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    /** dp 转 px。 */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
