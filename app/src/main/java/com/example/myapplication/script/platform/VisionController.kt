package com.example.myapplication.script.platform

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** 供脚本运行期读取屏幕和匹配模板的能力。 */
interface VisionController {
    suspend fun capture(): ScreenCapture?

    suspend fun captureResult(): VisionResult<ScreenCapture> =
        capture()?.let { VisionResult.Success(it) } ?: VisionResult.PermissionDenied

    /** 调用方取得后必须自行回收 Bitmap。默认实现兼容不支持 OCR 的测试替身。 */
    suspend fun captureBitmap(): Bitmap? = null

    suspend fun recognizeTextResult(region: Rect? = null): VisionResult<String> =
        VisionResult.PermissionDenied

    suspend fun findColorResult(
        color: Int,
        tolerance: Int,
        region: Rect? = null
    ): VisionResult<Point> = VisionResult.PermissionDenied

    suspend fun matchResult(
        templateId: String,
        threshold: Float,
        region: Rect? = null
    ): VisionResult<TemplateMatch> =
        match(templateId, threshold, region)?.let { VisionResult.Success(it) } ?: VisionResult.NotFound

    suspend fun match(templateId: String, threshold: Float, region: Rect? = null): TemplateMatch? = null
}

data class TemplateMatch(val x: Int, val y: Int, val score: Float)

/** 独立于 Android Bitmap 的屏幕像素快照，便于在 JVM 测试中替换。 */
data class ScreenCapture(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    init {
        require(width > 0 && height > 0)
        val requiredSize = width.toLong() * height.toLong()
        require(requiredSize <= Int.MAX_VALUE && pixels.size >= requiredSize)
    }

    fun pixelAt(x: Int, y: Int): Int = pixels[y * width + x]
}

/** 将已有 MediaProjection 授权包装为脚本运行期截图能力。 */
class ScreenCaptureVisionController(
    private val session: ScreenCaptureSession,
    context: android.content.Context
) : VisionController {
    private val applicationContext = context.applicationContext
    private val templates = ImageTemplateRepository(applicationContext)
    private val matcher = ImageTemplateMatcher()

    override suspend fun capture(): ScreenCapture? = when (val result = captureResult()) {
        is VisionResult.Success -> result.value
        else -> null
    }

    override suspend fun captureResult(): VisionResult<ScreenCapture> {
        return when (val bitmapResult = session.captureResult()) {
            is VisionResult.Success -> bitmapResult.value.toScreenCaptureResult()
            is VisionResult.NotFound -> VisionResult.NotFound
            is VisionResult.Timeout -> VisionResult.Timeout
            is VisionResult.PermissionDenied -> VisionResult.PermissionDenied
            is VisionResult.Failed -> bitmapResult
                .let { VisionResult.Failed(it.message, it.cause) }
        }
    }

    override suspend fun captureBitmap(): Bitmap? = when (val result = session.captureResult()) {
        is VisionResult.Success -> result.value
        else -> null
    }

    override suspend fun recognizeTextResult(region: Rect?): VisionResult<String> {
        return when (val captureResult = session.captureResult()) {
            is VisionResult.Success -> recognizeText(captureResult.value, region)
            VisionResult.NotFound -> VisionResult.NotFound
            VisionResult.Timeout -> VisionResult.Timeout
            VisionResult.PermissionDenied -> VisionResult.PermissionDenied
            is VisionResult.Failed -> VisionResult.Failed(captureResult.message, captureResult.cause)
        }
    }

    override suspend fun findColorResult(
        color: Int,
        tolerance: Int,
        region: Rect?
    ): VisionResult<Point> {
        if (tolerance !in 0..255) return VisionResult.Failed("RGB 通道容差必须在 0 到 255 之间")
        return when (val captureResult = captureResult()) {
            is VisionResult.Success -> withContext(Dispatchers.Default) {
                findColor(captureResult.value, color, tolerance, region)
            }
            VisionResult.NotFound -> VisionResult.NotFound
            VisionResult.Timeout -> VisionResult.Timeout
            VisionResult.PermissionDenied -> VisionResult.PermissionDenied
            is VisionResult.Failed -> VisionResult.Failed(captureResult.message, captureResult.cause)
        }
    }

    override suspend fun matchResult(
        templateId: String,
        threshold: Float,
        region: Rect?
    ): VisionResult<TemplateMatch> {
        val template = templates.load(templateId)
            ?: return VisionResult.Failed("图像模板不存在：$templateId")
        return when (val captureResult = captureResult()) {
            is VisionResult.Success -> {
                try {
                    val match = withContext(Dispatchers.Default) {
                        matcher.find(captureResult.value, template, threshold, region)
                    }
                    match?.let { VisionResult.Success(it) } ?: VisionResult.NotFound
                } catch (error: CancellationException) {
                    throw error
                } catch (error: RuntimeException) {
                    VisionResult.Failed("图像匹配失败", error)
                }
            }
            is VisionResult.NotFound -> VisionResult.NotFound
            is VisionResult.Timeout -> VisionResult.Timeout
            is VisionResult.PermissionDenied -> VisionResult.PermissionDenied
            is VisionResult.Failed -> VisionResult.Failed(captureResult.message, captureResult.cause)
        }
    }

    override suspend fun match(templateId: String, threshold: Float, region: Rect?): TemplateMatch? =
        when (val result = matchResult(templateId, threshold, region)) {
            is VisionResult.Success -> result.value
            else -> null
        }
}

private suspend fun ScreenCaptureVisionController.recognizeText(
    bitmap: Bitmap,
    region: Rect?
): VisionResult<String> {
    val cropped = try {
        val bounds = Rect(0, 0, bitmap.width, bitmap.height)
        val clipped = region?.let { Rect(it).apply { intersect(bounds) } } ?: bounds
        if (clipped.width() <= 0 || clipped.height() <= 0) return VisionResult.Failed("OCR 框选区域无效")
        Bitmap.createBitmap(bitmap, clipped.left, clipped.top, clipped.width(), clipped.height())
            .copy(Bitmap.Config.ARGB_8888, false)
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        return VisionResult.Failed("OCR 截图裁剪失败", error)
    } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    return try {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val text = recognizer.process(InputImage.fromBitmap(cropped, 0)).await().text.trim()
            if (text.isBlank()) VisionResult.NotFound else VisionResult.Success(text)
        } finally {
            recognizer.close()
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        VisionResult.Failed("OCR 识别失败", error)
    } finally {
        if (!cropped.isRecycled) cropped.recycle()
    }
}

private suspend fun findColor(
    capture: ScreenCapture,
    color: Int,
    tolerance: Int,
    region: Rect?
): VisionResult<Point> {
    val left = maxOf(0, region?.left ?: 0)
    val top = maxOf(0, region?.top ?: 0)
    val right = minOf(capture.width, region?.right ?: capture.width)
    val bottom = minOf(capture.height, region?.bottom ?: capture.height)
    if (right <= left || bottom <= top) return VisionResult.Failed("找色区域无效")
    val red = (color ushr 16) and 0xFF
    val green = (color ushr 8) and 0xFF
    val blue = color and 0xFF
    for (y in top until bottom) {
        for (x in left until right) {
            coroutineContext.ensureActive()
            val pixel = capture.pixelAt(x, y)
            if (kotlin.math.abs(((pixel ushr 16) and 0xFF) - red) <= tolerance &&
                kotlin.math.abs(((pixel ushr 8) and 0xFF) - green) <= tolerance &&
                kotlin.math.abs((pixel and 0xFF) - blue) <= tolerance
            ) return VisionResult.Success(Point().apply {
                this.x = x
                this.y = y
            })
        }
    }
    return VisionResult.NotFound
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) {} }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWith(Result.failure(error)) }
    addOnCanceledListener { continuation.cancel() }
}

private fun Bitmap.toScreenCaptureResult(): VisionResult<ScreenCapture> {
    return try {
        if (width <= 0 || height <= 0 || isRecycled) {
            VisionResult.Failed("屏幕截图尺寸无效")
        } else {
            val pixels = IntArray(width * height)
            getPixels(pixels, 0, width, 0, 0, width, height)
            VisionResult.Success(ScreenCapture(width, height, pixels))
        }
    } catch (error: RuntimeException) {
        VisionResult.Failed("屏幕截图读取失败", error)
    } finally {
        if (!isRecycled) recycle()
    }
}
