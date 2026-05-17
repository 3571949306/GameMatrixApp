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

/**
 * 二维码生成工具绑定器。
 * <p>
 * 负责将二维码生成工具的 UI 视图与 ZXing 编码逻辑进行绑定。
 * 用户输入文本后，点击按钮即可生成对应的 QR Code 并显示在 ImageView 中。
 * 关键设计决策：
 * <ul>
 *   <li>二维码尺寸固定为 256x256 像素，兼顾清晰度和性能</li>
 *   <li>生成操作在主线程执行（ZXing 编码速度较快，通常不会造成卡顿）</li>
 *   <li>输入为空时使用默认文本 "GameCenterApp" 作为兜底</li>
 * </ul>
 * </p>
 */
public final class QrToolBinder implements ToolBinder {

    /**
     * 绑定二维码生成工具的视图和交互逻辑。
     * <p>
     * 查找文本输入框、二维码 ImageView 和生成按钮，
     * 点击按钮时读取输入文本并调用二维码生成方法。
     * </p>
     *
     * @param context     上下文环境，用于显示 Toast 提示
     * @param contentView 工具页面的根视图，包含文本输入框、二维码 ImageView 和生成按钮
     * @param executor    线程池执行器（本工具未使用，因二维码生成在主线程完成）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        EditText etText = contentView.findViewById(R.id.et_qr_text);
        ImageView ivQr = contentView.findViewById(R.id.iv_qr_code);
        MaterialButton btn = contentView.findViewById(R.id.btn_generate_qr);

        if (btn != null) btn.setOnClickListener(v -> {
            String text = etText != null ? etText.getText().toString() : "";
            // 输入为空时使用默认文本
            if (text.isEmpty()) text = "GameCenterApp";
            generateAndDisplayQr(text, ivQr, context);
        });
    }

    /**
     * 生成二维码并显示到 ImageView 中。
     * <p>
     * 使用 ZXing 库将文本编码为 QR Code 的 BitMatrix，
     * 然后将 BitMatrix 转换为 Bitmap 并设置到 ImageView 上。
     * 黑色模块对应 0xFF000000，白色模块对应 0xFFFFFFFF。
     * </p>
     *
     * @param text    要编码为二维码的文本内容
     * @param ivQr    用于显示二维码的 ImageView 控件
     * @param context 上下文环境，用于生成失败时显示 Toast 提示
     */
    private void generateAndDisplayQr(String text, ImageView ivQr, Context context) {
        try {
            // 使用 ZXing 编码文本为 256x256 的 QR Code 矩阵
            BitMatrix bitMatrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 256, 256);
            int width = bitMatrix.getWidth(), height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];
            // 将 BitMatrix 转换为像素数组：黑色模块(真)为 0xFF000000，白色模块(假)为 0xFFFFFFFF
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            // 创建 Bitmap 并设置像素数据
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            ivQr.setImageBitmap(bitmap);
            ivQr.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            android.widget.Toast.makeText(context, "生成失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
