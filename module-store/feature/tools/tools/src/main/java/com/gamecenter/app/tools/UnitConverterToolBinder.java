package com.gamecenter.app.tools;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * 单位换算工具绑定器（2026-07-25 新增）。
 * <p>
 * 支持分类：长度、重量、温度。每类下多个单位互转。
 * 长度/重量采用"基准单位 × 因子"线性换算；温度使用公式换算。
 * </p>
 */
public final class UnitConverterToolBinder implements ToolBinder {

    /** 单位分类 → 单位列表（第一个为基准单位） */
    private static final Map<String, List<UnitDef>> CATEGORIES = new HashMap<>();

    static {
        // 长度：以米为基准
        CATEGORIES.put("length", Arrays.asList(
                new UnitDef("m", 1.0),
                new UnitDef("km", 1000.0),
                new UnitDef("cm", 0.01),
                new UnitDef("mm", 0.001),
                new UnitDef("in", 0.0254),
                new UnitDef("ft", 0.3048),
                new UnitDef("mi", 1609.344)
        ));
        // 重量：以克为基准
        CATEGORIES.put("weight", Arrays.asList(
                new UnitDef("g", 1.0),
                new UnitDef("kg", 1000.0),
                new UnitDef("mg", 0.001),
                new UnitDef("lb", 453.59237),
                new UnitDef("oz", 28.349523)
        ));
        // 温度：特殊处理（factor 标记用于识别）
        CATEGORIES.put("temperature", Arrays.asList(
                new UnitDef("C", Double.NaN),
                new UnitDef("F", Double.NaN),
                new UnitDef("K", Double.NaN)
        ));
    }

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        Spinner spCategory = contentView.findViewById(R.id.sp_unit_category);
        final Spinner spFrom = contentView.findViewById(R.id.sp_unit_from);
        final Spinner spTo = contentView.findViewById(R.id.sp_unit_to);
        EditText etValue = contentView.findViewById(R.id.et_unit_value);
        TextView tvResult = contentView.findViewById(R.id.tv_unit_result);
        View btnConvert = contentView.findViewById(R.id.btn_unit_convert);

        if (spCategory == null || spFrom == null || spTo == null) return;

        // 分类选择器
        String[] categoryKeys = {"length", "weight", "temperature"};
        String[] categoryLabels = {
                context.getString(R.string.tool_unit_category_length),
                context.getString(R.string.tool_unit_category_weight),
                context.getString(R.string.tool_unit_category_temperature)
        };
        spCategory.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, categoryLabels));

        // 初始化为长度
        updateUnitSpinner(context, spFrom, "length");
        updateUnitSpinner(context, spTo, "length");

        spCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private boolean isFirst = true;

            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (isFirst) {
                    isFirst = false;
                    return;
                }
                String key = categoryKeys[position];
                updateUnitSpinner(context, spFrom, key);
                updateUnitSpinner(context, spTo, key);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        if (btnConvert != null) {
            btnConvert.setOnClickListener(v -> {
                String valueStr = etValue != null && etValue.getText() != null ? etValue.getText().toString().trim() : "";
                if (TextUtils.isEmpty(valueStr)) {
                    Toast.makeText(context, R.string.tool_unit_invalid_value, Toast.LENGTH_SHORT).show();
                    return;
                }
                double value;
                try {
                    value = Double.parseDouble(valueStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(context, R.string.tool_unit_invalid_value, Toast.LENGTH_SHORT).show();
                    return;
                }
                int catIdx = spCategory.getSelectedItemPosition();
                String categoryKey = categoryKeys[catIdx];
                String fromUnit = CATEGORIES.get(categoryKey).get(spFrom.getSelectedItemPosition()).name;
                String toUnit = CATEGORIES.get(categoryKey).get(spTo.getSelectedItemPosition()).name;

                double result = convert(categoryKey, fromUnit, toUnit, value);
                String formatted = formatNumber(result);
                tvResult.setText(String.format(Locale.US, "%s %s = %s %s",
                        formatNumber(value), fromUnit, formatted, toUnit));
            });
        }
    }

    private void updateUnitSpinner(Context context, Spinner spinner, String categoryKey) {
        List<UnitDef> units = CATEGORIES.get(categoryKey);
        String[] names = new String[units.size()];
        for (int i = 0; i < units.size(); i++) names[i] = units.get(i).name;
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, names));
    }

    private double convert(String categoryKey, String fromUnit, String toUnit, double value) {
        if ("temperature".equals(categoryKey)) {
            // 先转 C
            double celsius;
            switch (fromUnit) {
                case "C": celsius = value; break;
                case "F": celsius = (value - 32) * 5.0 / 9.0; break;
                case "K": celsius = value - 273.15; break;
                default: celsius = value;
            }
            // C → target
            switch (toUnit) {
                case "C": return celsius;
                case "F": return celsius * 9.0 / 5.0 + 32;
                case "K": return celsius + 273.15;
                default: return celsius;
            }
        }
        // 线性换算：先转基准，再转目标
        List<UnitDef> units = CATEGORIES.get(categoryKey);
        double baseFactor = 1.0;
        double targetFactor = 1.0;
        for (UnitDef u : units) {
            if (u.name.equals(fromUnit)) baseFactor = u.factor;
            if (u.name.equals(toUnit)) targetFactor = u.factor;
        }
        return value * baseFactor / targetFactor;
    }

    static String formatNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "—";
        // Math.round saturates outside long's range.  Restrict the integer
        // rendering path to values whose rounded result is representable;
        // 2^63 itself must remain scientific notation rather than Long.MAX_VALUE.
        if (v >= -0x1.0p63 && v < 0x1.0p63) {
            long rounded = Math.round(v);
            if (Math.abs(v - (double) rounded) < 1e-9) {
                return Long.toString(rounded);
            }
        }
        return String.format(Locale.US, "%.6g", v);
    }

    private static class UnitDef {
        final String name;
        final double factor;

        UnitDef(String name, double factor) {
            this.name = name;
            this.factor = factor;
        }
    }
}
