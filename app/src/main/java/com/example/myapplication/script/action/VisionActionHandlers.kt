package com.example.myapplication.script.action

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ScriptRuntime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ClickImageActionHandler(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : EmptyActionHandler(ActionType.CLICK_IMAGE) {
    override val isAvailable: Boolean = true
    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val templateId = action.parameters[ActionParameterKey.TEMPLATE_ID]?.trim().orEmpty()
        if (templateId.isBlank()) return ActionExecutionResult.Failed("图像模板不能为空")
        val controller = runtime.accessibilityController ?: return ActionExecutionResult.Failed("请先启用无障碍服务", ActionExecutionFailureCode.PLATFORM_DISCONNECTED)
        val vision = runtime.visionController ?: return ActionExecutionResult.Failed("图像匹配需要屏幕录制权限")
        val threshold = action.parameters[ActionParameterKey.MATCH_THRESHOLD]?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..1f } ?: return ActionExecutionResult.Failed("相似度阈值无效")
        val match = withContext(dispatcher) { vision.match(templateId, threshold, region(action)) }
            ?: return ActionExecutionResult.Failed("未找到图像模板：$templateId")
        runtime.recordVisionMatch(match.x, match.y)
        return if (controller.press(match.x, match.y, 80L)) ActionExecutionResult.Success
        else ActionExecutionResult.Failed("图像命中后的点击手势执行失败", ActionExecutionFailureCode.GESTURE_REJECTED)
    }
}

class WaitImageActionHandler(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val pollIntervalMillis: Long = 200L,
    private val elapsedRealtimeMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) : EmptyActionHandler(ActionType.WAIT_IMAGE) {
    override val isAvailable: Boolean = true
    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val templateId = action.parameters[ActionParameterKey.TEMPLATE_ID]?.trim().orEmpty()
        if (templateId.isBlank()) return ActionExecutionResult.Failed("图像模板不能为空")
        val vision = runtime.visionController ?: return ActionExecutionResult.Failed("图像匹配需要屏幕录制权限")
        val threshold = action.parameters[ActionParameterKey.MATCH_THRESHOLD]?.toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..1f } ?: return ActionExecutionResult.Failed("相似度阈值无效")
        val timeout = action.parameters[ActionParameterKey.WAIT_TIMEOUT_MILLIS]?.toLongOrNull()
            ?.takeIf { it >= 0L } ?: return ActionExecutionResult.Failed("等待超时时间无效")
        val startedAt = elapsedRealtimeMillis()
        val deadline = if (timeout > Long.MAX_VALUE - startedAt) Long.MAX_VALUE else startedAt + timeout
        do {
            coroutineContext.ensureActive()
            val match = withContext(dispatcher) { vision.match(templateId, threshold, region(action)) }
            if (match != null) { runtime.recordVisionMatch(match.x, match.y); return ActionExecutionResult.Success }
            if (elapsedRealtimeMillis() >= deadline) break
            kotlinx.coroutines.delay(pollIntervalMillis.coerceAtLeast(1L))
        } while (true)
        return ActionExecutionResult.Failed("等待图像超时：$templateId")
    }

    private fun region(action: ScriptAction): Rect? = imageRegion(action)
}

class OcrTextActionHandler(
    private val recognize: suspend (Bitmap) -> Result<String> = ::recognizeChineseText
) : EmptyActionHandler(ActionType.OCR_TEXT) {
    override val isAvailable: Boolean = true

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val variableName = action.parameters[ActionParameterKey.OCR_VARIABLE_NAME].orEmpty().trim()
        if (variableName.isBlank()) return ActionExecutionResult.Failed("OCR 写入变量名不能为空")
        val region = imageRegion(action) ?: return ActionExecutionResult.Failed("OCR 框选区域无效")
        val vision = runtime.visionController
            ?: return ActionExecutionResult.Failed("OCR 需要屏幕录制权限，请先返回主页授权")
        val screenshot = vision.captureBitmap()
            ?: return ActionExecutionResult.Failed("OCR 截图失败，请重新授权屏幕录制")
        val cropped = try {
            cropToRegion(screenshot, region)
        } finally {
            if (!screenshot.isRecycled) screenshot.recycle()
        } ?: return ActionExecutionResult.Failed("OCR 框选区域超出当前屏幕")

        val text = try {
            recognize(cropped).getOrElse { return ActionExecutionResult.Failed("OCR 识别失败") }.trim()
        } finally {
            if (!cropped.isRecycled) cropped.recycle()
        }
        if (text.isBlank()) return ActionExecutionResult.Failed("OCR 未识别到文字")
        val targetText = action.parameters[ActionParameterKey.OCR_TARGET_TEXT].orEmpty().trim()
        if (targetText.isNotEmpty() && !text.contains(targetText)) {
            return ActionExecutionResult.Failed("OCR 结果未包含目标文字：$targetText")
        }
        val stored = runtime.setVariable(variableName, text) || runtime.createVariable(variableName, text)
        return if (stored) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed("OCR 写入失败：变量名无效或无法创建：$variableName")
        }
    }

    private fun cropToRegion(screenshot: Bitmap, region: Rect): Bitmap? {
        if (screenshot.isRecycled || screenshot.width <= 0 || screenshot.height <= 0) return null
        val bounds = Rect(0, 0, screenshot.width, screenshot.height)
        val clipped = Rect(region).apply { intersect(bounds) }
        if (clipped.width() <= 0 || clipped.height() <= 0) return null
        var cropped: Bitmap? = null
        return try {
            cropped = Bitmap.createBitmap(
                screenshot,
                clipped.left,
                clipped.top,
                clipped.width(),
                clipped.height()
            )
            cropped.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            if (cropped != null && !cropped.isRecycled) cropped.recycle()
        }
    }
}

private suspend fun recognizeChineseText(bitmap: Bitmap): Result<String> {
    val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    return try {
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        Result.success(text)
    } catch (error: Exception) {
        Result.failure(error)
    } finally {
        recognizer.close()
    }
}

private suspend fun <T> Task<T>.await(): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) {} }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWith(Result.failure(error)) }
    addOnCanceledListener { continuation.cancel() }
}

private fun region(action: ScriptAction): Rect? = imageRegion(action)

private fun Int.redChannel(): Int = (this ushr 16) and 0xFF
private fun Int.greenChannel(): Int = (this ushr 8) and 0xFF
private fun Int.blueChannel(): Int = this and 0xFF

private fun imageRegion(action: ScriptAction): Rect? {
    val values = action.parameters
    val numbers = listOf(ActionParameterKey.MATCH_REGION_LEFT, ActionParameterKey.MATCH_REGION_TOP, ActionParameterKey.MATCH_REGION_RIGHT, ActionParameterKey.MATCH_REGION_BOTTOM)
        .map { values[it]?.toIntOrNull() }
    if (numbers.any { it == null }) return null
    val (left, top, right, bottom) = numbers.map { it ?: return null }
    return Rect(left, top, right, bottom).takeIf { it.width() > 0 && it.height() > 0 }
}

class FindColorActionHandler(
    private val scanDispatcher: CoroutineDispatcher = Dispatchers.Default
) : EmptyActionHandler(ActionType.FIND_COLOR) {
    override val isAvailable: Boolean = true

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val targetColor = parseHexColor(action.parameters[ActionParameterKey.COLOR_HEX])
            ?: return ActionExecutionResult.Failed("找色缺少有效的目标颜色 HEX")
        val tolerance = action.parameters[ActionParameterKey.COLOR_TOLERANCE]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("RGB 通道容差无效")
        if (tolerance !in 0..MAX_COLOR_TOLERANCE) {
            return ActionExecutionResult.Failed("RGB 通道容差必须在 0 到 255 之间")
        }
        val capture = runtime.visionController?.capture()
            ?: return ActionExecutionResult.Failed("找色需要屏幕录制权限，请先返回主页授权")

        val match = withContext(scanDispatcher) {
            findFirstMatch(capture.pixels, capture.width, capture.height, targetColor, tolerance)
        } ?: return ActionExecutionResult.Failed("未找到目标颜色")

        runtime.recordVisionMatch(match.x, match.y)
        val variableName = action.parameters[ActionParameterKey.MATCH_VARIABLE_NAME].orEmpty().trim()
        if (variableName.isNotEmpty()) {
            val stored = runtime.setVariable(variableName, "${match.x},${match.y}") ||
                runtime.createVariable(variableName, "${match.x},${match.y}")
            if (!stored) return ActionExecutionResult.Failed("找色坐标写入失败：$variableName")
        }
        if (action.parameters[ActionParameterKey.FIND_COLOR_CLICK]?.equals("true", ignoreCase = true) == true) {
            val controller = runtime.accessibilityController
                ?: return ActionExecutionResult.Failed("找色点击需要无障碍服务", ActionExecutionFailureCode.PLATFORM_DISCONNECTED)
            if (!controller.press(match.x, match.y, 80L)) {
                return ActionExecutionResult.Failed("找色命中后的点击手势执行失败", ActionExecutionFailureCode.GESTURE_REJECTED)
            }
        }
        return ActionExecutionResult.Success
    }

    private suspend fun findFirstMatch(
        pixels: IntArray,
        width: Int,
        height: Int,
        targetColor: Int,
        tolerance: Int
    ): ColorMatch? {
        val targetRed = targetColor.redChannel()
        val targetGreen = targetColor.greenChannel()
        val targetBlue = targetColor.blueChannel()
        val pixelCount = minOf(width.toLong() * height, pixels.size.toLong()).toInt()
        for (index in 0 until pixelCount) {
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            val pixel = pixels[index]
            if (
                kotlin.math.abs(pixel.redChannel() - targetRed) <= tolerance &&
                kotlin.math.abs(pixel.greenChannel() - targetGreen) <= tolerance &&
                kotlin.math.abs(pixel.blueChannel() - targetBlue) <= tolerance
            ) {
                return ColorMatch(x = index % width, y = index / width)
            }
        }
        return null
    }

    private fun parseHexColor(value: String?): Int? {
        val hex = value?.trim()?.takeIf { it.matches(HEX_COLOR) } ?: return null
        return hex.substring(1).toLongOrNull(16)?.toInt()
    }

    private data class ColorMatch(val x: Int, val y: Int)

    private companion object {
        val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
        const val MAX_COLOR_TOLERANCE = 255
        const val CANCELLATION_CHECK_INTERVAL = 4_096
    }
}

class PickColorActionHandler : EmptyActionHandler(ActionType.PICK_COLOR) {
    override val isAvailable: Boolean = true

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val x = action.parameters[ActionParameterKey.PICK_X]?.toIntOrNull()
        val y = action.parameters[ActionParameterKey.PICK_Y]?.toIntOrNull()
        val variableName = action.parameters[ActionParameterKey.COLOR_VARIABLE_NAME].orEmpty().trim()
        if (x == null || y == null || variableName.isBlank()) {
            return legacyResult(action)
        }
        val capture = runtime.visionController?.capture()
            ?: return ActionExecutionResult.Failed("取色需要屏幕录制权限，请先返回主页授权")
        if (x !in 0 until capture.width || y !in 0 until capture.height) {
            return ActionExecutionResult.Failed("取色坐标超出当前截图范围")
        }
        val color = capture.pixelAt(x, y)
        val hex = String.format("#%06X", color and 0xFFFFFF)
        val stored = runtime.setVariable(variableName, hex) || runtime.createVariable(variableName, hex)
        return if (stored) ActionExecutionResult.Success else ActionExecutionResult.Failed("取色写入失败：$variableName")
    }

    private fun legacyResult(action: ScriptAction): ActionExecutionResult {
        val hex = action.parameters[ActionParameterKey.COLOR_HEX]
            ?.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) }
            ?: return ActionExecutionResult.Failed("取色结果缺少有效 HEX 参数")
        return ActionExecutionResult.Success
    }
}
