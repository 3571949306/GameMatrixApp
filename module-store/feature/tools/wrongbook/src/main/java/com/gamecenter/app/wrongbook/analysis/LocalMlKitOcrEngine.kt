package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 本地 ML Kit OCR 引擎。
 *
 * 无需网络，离线识别图片中的文字。
 */
class LocalMlKitOcrEngine : OcrEngine {

    override val name: String = "local"

    override suspend fun recognize(context: Context, imageUri: Uri): OcrResult =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val image: InputImage?
                try {
                    image = InputImage.fromFilePath(context, imageUri)
                } catch (e: Exception) {
                    continuation.resume(
                        OcrResult(success = false, message = "无法加载图片: ${e.message}")
                    )
                    return@suspendCancellableCoroutine
                }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val task = recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text
                        continuation.resume(
                            OcrResult(
                                success = text.isNotBlank(),
                                text = text,
                                message = if (text.isBlank()) "未识别到文字" else ""
                            )
                        )
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(
                            OcrResult(success = false, message = "OCR识别失败: ${e.message}")
                        )
                    }

                continuation.invokeOnCancellation {
                    task.isComplete || task.isCanceled || task.isSuccessful || task.isComplete
                }
            }
        }
}
