package com.gamecenter.app.wrongbook.analysis

/**
 * OCR 识别结果。
 *
 * @param success 是否成功
 * @param text 识别文本
 * @param message 失败原因
 */
data class OcrResult(
    val success: Boolean,
    val text: String = "",
    val message: String = ""
)
