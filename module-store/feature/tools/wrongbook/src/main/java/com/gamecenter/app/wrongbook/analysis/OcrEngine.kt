package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.net.Uri

/**
 * OCR 引擎接口。
 *
 * 支持本地 ML Kit 与云端 OCR 双模式，未来可扩展其他引擎。
 */
interface OcrEngine {

    /** 引擎标识，如 local / scnet */
    val name: String

    /**
     * 识别图片中的文字。
     *
     * @param context 上下文
     * @param imageUri 图片 URI
     * @return 识别结果
     */
    suspend fun recognize(context: Context, imageUri: Uri): OcrResult
}
