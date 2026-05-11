package com.gamecenter.app.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import com.gamecenter.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.concurrent.ExecutorService;

public final class QrToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        EditText etText = contentView.findViewById(R.id.et_qr_text);
        ImageView ivQr = contentView.findViewById(R.id.iv_qr_code);
        MaterialButton btn = contentView.findViewById(R.id.btn_generate_qr);

        if (btn != null) btn.setOnClickListener(v -> {
            String text = etText != null ? etText.getText().toString() : "";
            if (text.isEmpty()) text = "GameCenterApp";
            generateAndDisplayQr(text, ivQr, context);
        });
    }

    private void generateAndDisplayQr(String text, ImageView ivQr, Context context) {
        try {
            BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 256, 256);
            int width = bitMatrix.getWidth(), height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            ivQr.setImageBitmap(bitmap);
            ivQr.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            android.widget.Toast.makeText(context, "生成失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
