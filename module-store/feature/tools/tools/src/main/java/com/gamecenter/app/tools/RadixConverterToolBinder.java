package com.gamecenter.app.tools;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.math.BigInteger;
import java.util.concurrent.ExecutorService;

/**
 * 进制转换工具绑定器（2026-07-25 新增）。
 * <p>
 * 用户选择输入进制（2/8/10/16）并输入数字，工具将其同时显示为
 * 二进制、八进制、十进制、十六进制四种表示。使用 BigInteger 支持大数。
 * </p>
 */
public final class RadixConverterToolBinder implements ToolBinder {

    private static final int[] BASES = {2, 8, 10, 16};

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        Spinner spBase = contentView.findViewById(R.id.sp_radix_input_base);
        EditText etInput = contentView.findViewById(R.id.et_radix_input);
        TextView tvBin = contentView.findViewById(R.id.tv_radix_bin);
        TextView tvOct = contentView.findViewById(R.id.tv_radix_oct);
        TextView tvDec = contentView.findViewById(R.id.tv_radix_dec);
        TextView tvHex = contentView.findViewById(R.id.tv_radix_hex);
        View btnConvert = contentView.findViewById(R.id.btn_radix_convert);

        if (spBase == null) return;

        String[] baseLabels = new String[BASES.length];
        for (int i = 0; i < BASES.length; i++) {
            baseLabels[i] = context.getString(R.string.tool_radix_base_format, BASES[i]);
        }
        spBase.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, baseLabels));
        spBase.setSelection(2); // 默认十进制

        if (btnConvert != null) {
            btnConvert.setOnClickListener(v -> {
                String input = etInput != null && etInput.getText() != null ? etInput.getText().toString().trim() : "";
                if (input.isEmpty()) return;
                int fromBase = BASES[spBase.getSelectedItemPosition()];
                try {
                    BigInteger value = new BigInteger(input, fromBase);
                    tvBin.setText("BIN  " + value.toString(2));
                    tvOct.setText("OCT  " + value.toString(8));
                    tvDec.setText("DEC  " + value.toString(10));
                    tvHex.setText("HEX  " + value.toString(16).toUpperCase(java.util.Locale.US));
                } catch (NumberFormatException e) {
                    Toast.makeText(context, context.getString(R.string.tool_radix_invalid_format, fromBase),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
