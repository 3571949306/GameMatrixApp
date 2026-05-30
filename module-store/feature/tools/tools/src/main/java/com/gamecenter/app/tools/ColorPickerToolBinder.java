package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;
import android.graphics.Color;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.gamecenter.app.R;
import com.gamecenter.app.views.ColorAlphaBar;
import com.gamecenter.app.views.ColorHueBar;
import com.gamecenter.app.views.ColorSVPanel;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;

/**
 * 颜色选择器工具绑定器，实现 {@link ToolBinder} 接口。
 * <p>
 * 提供完整的颜色选择和格式转换功能，支持 HSV 色彩空间交互式选色、
 * RGB 滑块调节、十六进制输入、预设颜色快速选择，以及多种颜色格式输出。
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>以 HSV 为核心色彩模型：HSV 色板（SVPanel + HueBar）是主交互方式，
 *       所有其他输入方式（RGB 滑块、HEX 输入、预设颜色）最终都转换为 HSV 值，
 *       再通过统一的 updateAll 回调同步所有 UI 组件</li>
 *   <li>防递归更新：使用 updating 标志位防止组件间相互触发导致的无限递归更新，
 *       例如 SeekBar 变化 → 更新 HSV → updateAll → 又设置 SeekBar 进度</li>
 *   <li>多格式输出：同时显示 HEX、RGB、ARGB、HSL、HSV、INT、CSS 等格式，
 *       满足不同开发场景的需求</li>
 * </ul>
 */
public final class ColorPickerToolBinder implements ToolBinder {

    private static final String TAG = "ColorPickerToolBinder";

    public ColorPickerToolBinder() {
    }

    /**
     * 绑定颜色选择器工具的 UI 交互。
     * <p>
     * 初始化所有颜色交互组件并建立联动关系：
     * <ol>
     *   <li>HSV 色板和色相条：拖动时实时更新颜色</li>
     *   <li>透明度条：调节 Alpha 通道值</li>
     *   <li>RGB/Alpha 滑块：拖动时反向转换为 HSV 并更新</li>
     *   <li>HEX 输入框：输入十六进制颜色值并应用</li>
     *   <li>预设颜色按钮：快速选择常用颜色</li>
     *   <li>复制按钮：将当前颜色的多格式信息复制到剪贴板</li>
     * </ol>
     *
     * @param context     上下文，用于剪贴板操作和 Toast 提示
     * @param contentView 工具页面的根视图
     * @param executor    线程池（本工具未使用，颜色计算均在主线程完成）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (context == null || contentView == null) {
            return;
        }

        ColorSVPanel svPanel = contentView.findViewById(R.id.sv_panel);
        ColorHueBar hueBar = contentView.findViewById(R.id.hue_bar);
        ColorAlphaBar alphaBar = contentView.findViewById(R.id.alpha_bar);
        View preview = contentView.findViewById(R.id.v_color_preview);
        EditText etHex = contentView.findViewById(R.id.et_color_hex);
        SeekBar seekR = contentView.findViewById(R.id.seek_r);
        SeekBar seekG = contentView.findViewById(R.id.seek_g);
        SeekBar seekB = contentView.findViewById(R.id.seek_b);
        SeekBar seekA = contentView.findViewById(R.id.seek_a);
        TextView tvRVal = contentView.findViewById(R.id.tv_r_val);
        TextView tvGVal = contentView.findViewById(R.id.tv_g_val);
        TextView tvBVal = contentView.findViewById(R.id.tv_b_val);
        TextView tvAVal = contentView.findViewById(R.id.tv_a_val);
        TextView tvRgb = contentView.findViewById(R.id.tv_color_rgb);
        TextView tvArgb = contentView.findViewById(R.id.tv_color_argb);
        TextView tvHsl = contentView.findViewById(R.id.tv_color_hsl);
        TextView tvHsv = contentView.findViewById(R.id.tv_color_hsv);
        TextView tvInt = contentView.findViewById(R.id.tv_color_int);
        TextView tvCss = contentView.findViewById(R.id.tv_color_css);

        // 当前颜色的 HSV 值和 Alpha 值，使用数组包装以支持 lambda 内修改
        float[] currentHsv = {14f, 0.867f, 1f};
        float[] currentAlpha = {1f};
        // 防递归标志：当 updateAll 正在更新各组件时设为 true，防止组件回调再次触发 updateAll
        boolean[] updating = {false};

        // 核心更新回调：将当前 HSV/Alpha 值同步到所有 UI 组件
        Runnable updateAll = () -> {
            if (updating[0]) {
                return;
            }
            updating[0] = true;

            int color = Color.HSVToColor(Math.round(currentAlpha[0] * 255), currentHsv);
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            int a = Color.alpha(color);

            // 更新颜色预览
            if (preview != null) {
                preview.setBackgroundColor(color);
            }
            // 同步 HSV 色板状态
            if (svPanel != null) {
                svPanel.setHue(currentHsv[0]);
                svPanel.setSV(currentHsv[1], currentHsv[2]);
            }
            if (hueBar != null) {
                hueBar.setHue(currentHsv[0]);
            }
            if (alphaBar != null) {
                alphaBar.setColor(Color.HSVToColor(currentHsv));
                alphaBar.setAlpha(currentAlpha[0]);
            }

            // 更新 HEX 显示：不透明时显示 #RRGGBB，半透明时显示 #AARRGGBB
            String hex = a < 255
                    ? String.format("#%02X%02X%02X%02X", a, r, g, b)
                    : String.format("#%02X%02X%02X", r, g, b);
            if (etHex != null) {
                etHex.setText(hex);
            }

            // 同步 RGB/Alpha 滑块进度
            if (seekR != null) {
                seekR.setProgress(r);
            }
            if (seekG != null) {
                seekG.setProgress(g);
            }
            if (seekB != null) {
                seekB.setProgress(b);
            }
            if (seekA != null) {
                seekA.setProgress(a);
            }

            // 更新 RGB/Alpha 数值标签
            if (tvRVal != null) {
                tvRVal.setText(String.valueOf(r));
            }
            if (tvGVal != null) {
                tvGVal.setText(String.valueOf(g));
            }
            if (tvBVal != null) {
                tvBVal.setText(String.valueOf(b));
            }
            if (tvAVal != null) {
                tvAVal.setText(String.valueOf(a));
            }

            // 更新多格式颜色文本
            if (tvRgb != null) {
                tvRgb.setText("RGB: " + r + ", " + g + ", " + b);
            }
            if (tvArgb != null) {
                tvArgb.setText("ARGB: " + a + ", " + r + ", " + g + ", " + b);
            }

            // 计算 HSL 值（需要从 RGB 转换，Android 未提供原生 API）
            float[] hsl = new float[3];
            rgbToHsl(r, g, b, hsl);
            if (tvHsl != null) {
                tvHsl.setText(String.format("HSL: %d°, %d%%, %d%%",
                        Math.round(hsl[0]), Math.round(hsl[1] * 100), Math.round(hsl[2] * 100)));
            }

            if (tvHsv != null) {
                tvHsv.setText(String.format("HSV: %d°, %d%%, %d%%",
                        Math.round(currentHsv[0]), Math.round(currentHsv[1] * 100), Math.round(currentHsv[2] * 100)));
            }

            if (tvInt != null) {
                tvInt.setText("INT: 0x" + Integer.toHexString(color).toUpperCase());
            }

            // CSS 格式：半透明时使用 rgba()，不透明时使用 rgb()
            if (tvCss != null) {
                float alphaF = a / 255f;
                if (alphaF < 1f) {
                    tvCss.setText(String.format("CSS: rgba(%d,%d,%d,%.2f)", r, g, b, alphaF));
                } else {
                    tvCss.setText(String.format("CSS: rgb(%d,%d,%d)", r, g, b));
                }
            }

            updating[0] = false;
        };

        // HSV 色板（饱和度-明度）变化监听
        if (svPanel != null) {
            svPanel.setOnColorChangedListener((hue, sat, val) -> {
                currentHsv[0] = hue;
                currentHsv[1] = sat;
                currentHsv[2] = val;
                updateAll.run();
            });
        }

        // 色相条变化监听
        if (hueBar != null) {
            hueBar.setOnHueChangedListener(hue -> {
                currentHsv[0] = hue;
                updateAll.run();
            });
        }

        // 透明度条变化监听
        if (alphaBar != null) {
            alphaBar.setOnAlphaChangedListener(alpha -> {
                currentAlpha[0] = alpha;
                updateAll.run();
            });
        }

        // RGB/Alpha 滑块变化监听：将 RGB 值反向转换为 HSV，再触发统一更新
        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                // 仅处理用户手动拖动事件，忽略程序设置进度触发的回调
                if (!fromUser || updating[0]) {
                    return;
                }
                int r = seekR != null ? seekR.getProgress() : 0;
                int g = seekG != null ? seekG.getProgress() : 0;
                int b = seekB != null ? seekB.getProgress() : 0;
                int a = seekA != null ? seekA.getProgress() : 255;
                currentAlpha[0] = a / 255f;
                // RGB → HSV 转换，用于同步 HSV 色板
                float[] hsv = new float[3];
                Color.RGBToHSV(r, g, b, hsv);
                currentHsv[0] = hsv[0];
                currentHsv[1] = hsv[1];
                currentHsv[2] = hsv[2];
                updateAll.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        };

        if (seekR != null) {
            seekR.setOnSeekBarChangeListener(seekListener);
        }
        if (seekG != null) {
            seekG.setOnSeekBarChangeListener(seekListener);
        }
        if (seekB != null) {
            seekB.setOnSeekBarChangeListener(seekListener);
        }
        if (seekA != null) {
            seekA.setOnSeekBarChangeListener(seekListener);
        }

        // HEX 输入应用按钮：解析十六进制颜色值并更新
        MaterialButton btnApply = contentView.findViewById(R.id.btn_apply_hex);
        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                String hex = etHex != null ? etHex.getText().toString().trim() : "";
                if (hex.isEmpty()) {
                    return;
                }
                try {
                    int color = Color.parseColor(hex);
                    int a = Color.alpha(color);
                    int r = Color.red(color);
                    int g = Color.green(color);
                    int b = Color.blue(color);
                    currentAlpha[0] = a / 255f;
                    // HEX → RGB → HSV 转换
                    Color.RGBToHSV(r, g, b, currentHsv);
                    updateAll.run();
                } catch (Exception e) {
                    Toast.makeText(context, "无效颜色值", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 复制颜色信息按钮：将当前颜色的多格式信息复制到剪贴板
        MaterialButton btnCopy = contentView.findViewById(R.id.btn_copy_color);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                StringBuilder builder = new StringBuilder();
                int color = Color.HSVToColor(Math.round(currentAlpha[0] * 255), currentHsv);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int a = Color.alpha(color);
                String hex = a < 255
                        ? String.format("#%02X%02X%02X%02X", a, r, g, b)
                        : String.format("#%02X%02X%02X", r, g, b);
                builder.append("HEX: ").append(hex).append("\n");
                builder.append("RGB: ").append(r).append(", ").append(g).append(", ").append(b).append("\n");
                builder.append("ARGB: ").append(a).append(", ").append(r).append(", ").append(g).append(", ").append(b).append("\n");
                float[] hsl = new float[3];
                rgbToHsl(r, g, b, hsl);
                builder.append(String.format("HSL: %d°, %d%%, %d%%\n",
                        Math.round(hsl[0]), Math.round(hsl[1] * 100), Math.round(hsl[2] * 100)));
                builder.append(String.format("HSV: %d°, %d%%, %d%%\n",
                        Math.round(currentHsv[0]), Math.round(currentHsv[1] * 100), Math.round(currentHsv[2] * 100)));
                builder.append("INT: 0x").append(Integer.toHexString(color).toUpperCase());
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("color", builder.toString()));
                    Toast.makeText(context, "已复制颜色信息", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // 预设颜色按钮：10 种常用颜色，点击后直接应用
        int[] colorIds = {R.id.btn_color_red, R.id.btn_color_orange, R.id.btn_color_yellow,
                R.id.btn_color_green, R.id.btn_color_blue, R.id.btn_color_purple,
                R.id.btn_color_pink, R.id.btn_color_teal, R.id.btn_color_white, R.id.btn_color_black};
        String[] colorHexs = {"#F44336", "#FF9800", "#FFEB3B", "#4CAF50", "#2196F3",
                "#9C27B0", "#E91E63", "#009688", "#FFFFFF", "#212121"};
        for (int i = 0; i < colorIds.length; i++) {
            final String colorHex = colorHexs[i];
            View button = contentView.findViewById(colorIds[i]);
            if (button != null) {
                button.setOnClickListener(v -> {
                    try {
                        int color = Color.parseColor(colorHex);
                        int r = Color.red(color);
                        int g = Color.green(color);
                        int b = Color.blue(color);
                        // 预设颜色默认不透明
                        currentAlpha[0] = 1.0f;
                        Color.RGBToHSV(r, g, b, currentHsv);
                        updateAll.run();
                    } catch (Exception ignored) {
                        Log.w(TAG, "Apply preset color failed: " + ignored.getMessage());
                    }
                });
            }
        }

        // 初始化时执行一次全量更新，确保所有组件状态一致
        updateAll.run();
    }

    /**
     * 将 RGB 颜色值转换为 HSL 色彩空间。
     * <p>
     * HSL（色相、饱和度、明度）与 HSV 的区别在于 L（明度）的计算方式不同：
     * HSL 的 L = (max + min) / 2，而 HSV 的 V = max。
     * 这使得 HSL 在描述人眼感知的明暗时更直观。
     * <p>
     * 算法步骤：
     * <ol>
     *   <li>将 RGB 归一化到 [0,1] 范围</li>
     *   <li>计算最大值、最小值和明度 L</li>
     *   <li>若 max == min，色相和饱和度均为 0（灰色）</li>
     *   <li>否则根据最大值所在的通道计算色相 H，并根据明度计算饱和度 S</li>
     * </ol>
     *
     * @param r   红色分量，范围 0-255
     * @param g   绿色分量，范围 0-255
     * @param b   蓝色分量，范围 0-255
     * @param hsl 输出数组，hsl[0] 为色相（0-360°），hsl[1] 为饱和度（0-1），
     *            hsl[2] 为明度（0-1）
     */
    private static void rgbToHsl(float r, float g, float b, float[] hsl) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h = 0f;
        float s = 0f;
        // HSL 的明度 L = (max + min) / 2，不同于 HSV 的 V = max
        float l = (max + min) / 2f;

        if (max != min) {
            float d = max - min;
            // 饱和度公式取决于明度：明度 > 0.5 时用一种分母，否则用另一种
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            // 根据最大值所在通道计算色相
            if (max == rf) {
                h = (gf - bf) / d + (gf < bf ? 6f : 0f);
            } else if (max == gf) {
                h = (bf - rf) / d + 2f;
            } else {
                h = (rf - gf) / d + 4f;
            }
            h /= 6f;
        }
        hsl[0] = h * 360f;
        hsl[1] = s;
        hsl[2] = l;
    }
}
