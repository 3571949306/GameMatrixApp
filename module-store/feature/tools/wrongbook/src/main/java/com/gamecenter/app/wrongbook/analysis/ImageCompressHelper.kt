package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 图片压缩与旋转修正工具。
 *
 * - 读取 EXIF 旋转信息并自动修正方向
 * - 按最大边长缩放（默认 1600px）
 * - JPEG 质量压缩（默认 85）
 * - 输出到私有目录，返回文件路径
 */
object ImageCompressHelper {

    private const val TAG = "ImageCompressHelper"
    private const val DEFAULT_MAX_EDGE = 1600
    private const val DEFAULT_QUALITY = 85
    private const val COMPRESS_DIR = "wrongbook_images"

    /**
     * 压缩并修正图片方向。
     *
     * @param context 上下文
     * @param uri 原始图片 URI
     * @param maxEdge 最大边长（px），默认 1600
     * @param quality JPEG 质量 0-100，默认 85
     * @return 压缩后的文件绝对路径；失败返回空字符串
     */
    fun compressAndFixOrientation(
        context: Context,
        uri: Uri,
        maxEdge: Int = DEFAULT_MAX_EDGE,
        quality: Int = DEFAULT_QUALITY
    ): String {
        return try {
            val bitmap = decodeSampledBitmap(context, uri, maxEdge, maxEdge)
                ?: return ""

            val rotated = fixOrientation(context, uri, bitmap)
            val compressed = scaleIfNeeded(rotated, maxEdge)

            val dir = File(context.filesDir, COMPRESS_DIR)
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "img_${System.currentTimeMillis()}.jpg")

            FileOutputStream(outFile).use { fos ->
                compressed.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }

            if (compressed !== bitmap) {
                bitmap.recycle()
            }
            if (rotated !== compressed) {
                rotated.recycle()
            }
            compressed.recycle()

            Log.d(TAG, "压缩完成: ${outFile.absolutePath} (${outFile.length()} bytes)")
            outFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "图片压缩失败: ${e.message}")
            ""
        }
    }

    /** 读取 EXIF 旋转并修正 */
    private fun fixOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(context.contentResolver.openInputStream(uri)!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }
            val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (result !== bitmap) bitmap.recycle()
            result
        } catch (e: Exception) {
            Log.w(TAG, "EXIF 旋转读取失败: ${e.message}")
            bitmap
        }
    }

    /** 按最大边长缩放 */
    private fun scaleIfNeeded(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val maxOrig = maxOf(w, h)
        if (maxOrig <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / maxOrig
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        val result = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        if (result !== bitmap) bitmap.recycle()
        return result
    }

    /** 解码 Bitmap 时做采样，避免 OOM */
    private fun decodeSampledBitmap(context: Context, uri: Uri, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            opts.inSampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, reqW, reqH)
            opts.inJustDecodeBounds = false
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bitmap 解码失败: ${e.message}")
            null
        }
    }

    private fun calculateSampleSize(outW: Int, outH: Int, reqW: Int, reqH: Int): Int {
        if (outW == 0 || outH == 0) return 1
        var sample = 1
        while (outW / sample > reqW || outH / sample > reqH) {
            sample *= 2
        }
        return sample
    }
}
