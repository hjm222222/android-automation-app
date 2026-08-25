package com.example.myapplication.script.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** 与 Android Bitmap 解耦的模板数据。 */
data class ImageTemplate(val id: String, val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(id.isNotBlank() && width > 0 && height > 0)
        require(width.toLong() * height.toLong() == pixels.size.toLong())
    }
}

/** 模板永久保存于应用私有目录，文件名即稳定 templateId。 */
class ImageTemplateRepository(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "image_templates")

    fun save(bitmap: Bitmap, selection: Rect): String? {
        if (bitmap.isRecycled) return null
        val left = selection.left.coerceIn(0, bitmap.width)
        val top = selection.top.coerceIn(0, bitmap.height)
        val right = selection.right.coerceIn(left, bitmap.width)
        val bottom = selection.bottom.coerceIn(top, bitmap.height)
        if (right - left < MIN_TEMPLATE_SIZE || bottom - top < MIN_TEMPLATE_SIZE) return null
        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.bin")
        return try {
            if (!directory.exists() && !directory.mkdirs()) return null
            val pixels = IntArray((right - left) * (bottom - top))
            bitmap.getPixels(pixels, 0, right - left, left, top, right - left, bottom - top)
            DataOutputStream(FileOutputStream(file)).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(right - left)
                output.writeInt(bottom - top)
                pixels.forEach(output::writeInt)
            }
            id
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun load(id: String): ImageTemplate? {
        if (!id.matches(ID_PATTERN)) return null
        val file = File(directory, "$id.bin")
        return try {
            DataInputStream(FileInputStream(file)).use { input ->
                if (input.readInt() != MAGIC) return null
                val width = input.readInt()
                val height = input.readInt()
                val size = width.toLong() * height.toLong()
                if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION || size > MAX_PIXELS) return null
                val pixels = IntArray(size.toInt()) { input.readInt() }
                ImageTemplate(id, width, height, pixels)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAGIC = 0x41495431
        private const val MIN_TEMPLATE_SIZE = 4
        private const val MAX_DIMENSION = 4096
        private const val MAX_PIXELS = 4_000_000L
        private val ID_PATTERN = Regex("[0-9a-fA-F-]{36}")
    }
}

class ImageTemplateMatcher {
    suspend fun find(
        capture: ScreenCapture,
        template: ImageTemplate,
        threshold: Float = 0.85f,
        region: Rect? = null,
        maxSamples: Int = 20_000
    ): TemplateMatch? {
        if (!threshold.isFinite() || threshold !in 0f..1f || maxSamples < 1) return null
        val left = region?.left?.coerceIn(0, capture.width) ?: 0
        val top = region?.top?.coerceIn(0, capture.height) ?: 0
        val right = region?.right?.coerceIn(0, capture.width) ?: capture.width
        val bottom = region?.bottom?.coerceIn(0, capture.height) ?: capture.height
        if (right - left < template.width || bottom - top < template.height) return null

        val maxX = right - template.width
        val maxY = bottom - template.height
        val step = kotlin.math.ceil(
            kotlin.math.sqrt((template.width.toLong() * template.height).toDouble() / maxSamples)
        ).toInt().coerceAtLeast(1)
        var best: Match? = null
        for (y in top..maxY) {
            coroutineContext.ensureActive()
            for (x in left..maxX) {
                val score = score(capture, template, x, y, step)
                if (score >= threshold && (best == null || score > best.score)) best = Match(x, y, score)
            }
        }
        return best?.let { TemplateMatch(it.x + template.width / 2, it.y + template.height / 2, it.score) }
    }

    private fun score(capture: ScreenCapture, template: ImageTemplate, x: Int, y: Int, step: Int): Float {
        var error = 0L
        var count = 0
        var ty = 0
        while (ty < template.height) {
            var tx = 0
            while (tx < template.width) {
                val a = capture.pixelAt(x + tx, y + ty)
                val b = template.pixels[ty * template.width + tx]
                error += kotlin.math.abs(channel(a, 16) - channel(b, 16))
                error += kotlin.math.abs(channel(a, 8) - channel(b, 8))
                error += kotlin.math.abs(channel(a, 0) - channel(b, 0))
                count++
                tx += step
            }
            ty += step
        }
        return 1f - error.toFloat() / (count * 765f)
    }

    private fun channel(color: Int, shift: Int): Int = color ushr shift and 0xFF

    private data class Match(val x: Int, val y: Int, val score: Float)
}
