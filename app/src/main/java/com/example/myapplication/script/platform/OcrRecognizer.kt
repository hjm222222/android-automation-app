package com.example.myapplication.script.platform

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

interface OcrRecognizer {
    suspend fun recognizeLines(
        bitmap: Bitmap,
        region: Rect? = null
    ): VisionResult<OcrTextResult>

    suspend fun recognizeText(
        bitmap: Bitmap,
        region: Rect? = null
    ): VisionResult<String>
}

class MlKitOcrRecognizer : OcrRecognizer {
    override suspend fun recognizeLines(
        bitmap: Bitmap,
        region: Rect?
    ): VisionResult<OcrTextResult> {
        return process(bitmap, region) { text, bounds ->
            val lines = text.textBlocks
                .flatMap { block -> block.lines }
                .mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrTextLine(
                        text = line.text.trim(),
                        boundingBox = Rect(
                            box.left + bounds.left,
                            box.top + bounds.top,
                            box.right + bounds.left,
                            box.bottom + bounds.top
                        )
                    )
                }
                .filter { it.text.isNotBlank() }

            if (lines.isEmpty()) {
                VisionResult.NotFound
            } else {
                VisionResult.Success(OcrTextResult(lines))
            }
        }
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        region: Rect?
    ): VisionResult<String> {
        return process(bitmap, region) { text, _ ->
            val value = text.text.trim()
            if (value.isBlank()) VisionResult.NotFound else VisionResult.Success(value)
        }
    }

    private suspend fun <T> process(
        bitmap: Bitmap,
        region: Rect?,
        mapper: (com.google.mlkit.vision.text.Text, Rect) -> VisionResult<T>
    ): VisionResult<T> {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return VisionResult.Failed("OCR 输入截图无效")
        }

        val bounds = Rect(0, 0, bitmap.width, bitmap.height)
        val clipped = region?.let { Rect(it).apply { intersect(bounds) } } ?: bounds
        if (clipped.width() <= 0 || clipped.height() <= 0) {
            return VisionResult.Failed("OCR 框选区域无效")
        }

        val cropped = try {
            Bitmap.createBitmap(
                bitmap,
                clipped.left,
                clipped.top,
                clipped.width(),
                clipped.height()
            ).copy(Bitmap.Config.ARGB_8888, false)
                ?: return VisionResult.Failed("OCR 截图裁剪失败")
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            return VisionResult.Failed("OCR 截图裁剪失败", error)
        }

        return try {
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
            )
            try {
                val text = withTimeout(OCR_TIMEOUT_MILLIS) {
                    recognizer.process(InputImage.fromBitmap(cropped, 0)).await()
                }
                mapper(text, clipped)
            } finally {
                recognizer.close()
            }
        } catch (error: TimeoutCancellationException) {
            VisionResult.Timeout
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(OCR_TAG, "OCR 识别失败", error)
            VisionResult.Failed("OCR 识别失败", error)
        } finally {
            if (!cropped.isRecycled) cropped.recycle()
        }
    }

    private companion object {
        const val OCR_TAG = "MlKitOcrRecognizer"
        const val OCR_TIMEOUT_MILLIS = 8_000L
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWith(Result.failure(error))
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
