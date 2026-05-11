package com.gamecenter.app.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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

public final class ColorPickerToolBinder implements ToolBinder {

    public ColorPickerToolBinder() {
    }

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

        float[] currentHsv = {14f, 0.867f, 1f};
        float[] currentAlpha = {1f};
        boolean[] updating = {false};

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

            if (preview != null) {
                preview.setBackgroundColor(color);
            }
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

            String hex = a < 255
                    ? String.format("#%02X%02X%02X%02X", a, r, g, b)
                    : String.format("#%02X%02X%02X", r, g, b);
            if (etHex != null) {
                etHex.setText(hex);
            }

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

            if (tvRgb != null) {
                tvRgb.setText("RGB: " + r + ", " + g + ", " + b);
            }
            if (tvArgb != null) {
                tvArgb.setText("ARGB: " + a + ", " + r + ", " + g + ", " + b);
            }

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

        if (svPanel != null) {
            svPanel.setOnColorChangedListener((hue, sat, val) -> {
                currentHsv[0] = hue;
                currentHsv[1] = sat;
                currentHsv[2] = val;
                updateAll.run();
            });
        }

        if (hueBar != null) {
            hueBar.setOnHueChangedListener(hue -> {
                currentHsv[0] = hue;
                updateAll.run();
            });
        }

        if (alphaBar != null) {
            alphaBar.setOnAlphaChangedListener(alpha -> {
                currentAlpha[0] = alpha;
                updateAll.run();
            });
        }

        SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser || updating[0]) {
                    return;
                }
                int r = seekR != null ? seekR.getProgress() : 0;
                int g = seekG != null ? seekG.getProgress() : 0;
                int b = seekB != null ? seekB.getProgress() : 0;
                int a = seekA != null ? seekA.getProgress() : 255;
                currentAlpha[0] = a / 255f;
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
                    Color.RGBToHSV(r, g, b, currentHsv);
                    updateAll.run();
                } catch (Exception e) {
                    Toast.makeText(context, "无效颜色值", Toast.LENGTH_SHORT).show();
                }
            });
        }

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
                        currentAlpha[0] = 1.0f;
                        Color.RGBToHSV(r, g, b, currentHsv);
                        updateAll.run();
                    } catch (Exception ignored) {
                    }
                });
            }
        }

        updateAll.run();
    }

    private static void rgbToHsl(float r, float g, float b, float[] hsl) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h = 0f;
        float s = 0f;
        float l = (max + min) / 2f;

        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
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
