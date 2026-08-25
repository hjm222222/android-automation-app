package com.example.myapplication.script.platform

import android.graphics.Bitmap
import android.graphics.Rect

/** 供脚本运行期读取屏幕和匹配模板的能力。 */
interface VisionController {
    suspend fun capture(): ScreenCapture?
    /** 调用方取得后必须自行回收 Bitmap。默认实现兼容不支持 OCR 的测试替身。 */
    suspend fun captureBitmap(): Bitmap? = null
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

    override suspend fun capture(): ScreenCapture? {
        val bitmap = captureBitmap() ?: return null
        return bitmap.toScreenCapture()
    }

    override suspend fun captureBitmap(): Bitmap? = session.captureBitmap()

    override suspend fun match(templateId: String, threshold: Float, region: Rect?): TemplateMatch? {
        val template = templates.load(templateId) ?: return null
        val capture = capture() ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            matcher.find(capture, template, threshold, region)?.let { TemplateMatch(it.x, it.y, it.score) }
        }
    }
}

private fun Bitmap.toScreenCapture(): ScreenCapture? {
    return try {
        if (width <= 0 || height <= 0 || isRecycled) {
            null
        } else {
            val pixels = IntArray(width * height)
            getPixels(pixels, 0, width, 0, 0, width, height)
            ScreenCapture(width, height, pixels)
        }
    } catch (_: RuntimeException) {
        null
    } finally {
        if (!isRecycled) recycle()
    }
}
