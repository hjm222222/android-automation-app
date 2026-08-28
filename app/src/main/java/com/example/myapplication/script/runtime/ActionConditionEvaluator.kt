package com.example.myapplication.script.runtime

import android.graphics.Rect as AndroidRect
import android.util.Log
import com.example.myapplication.script.model.ActionCondition
import com.example.myapplication.script.model.ImageJudgementScope
import com.example.myapplication.script.model.JudgementCondition
import com.example.myapplication.script.model.TextJudgementScope
import com.example.myapplication.script.model.VariableComparisonOperator
import com.example.myapplication.script.platform.VisionResult

object ActionConditionEvaluator {
    suspend fun shouldExecute(condition: ActionCondition, runtime: ScriptRuntime): Boolean {
        return when (condition) {
            ActionCondition.Always -> true
            is ActionCondition.VariableEquals -> runtime.getVariable(condition.variableName) == condition.expectedValue
            is ActionCondition.Judgement -> evaluate(condition.condition, runtime)
        }
    }

    private suspend fun evaluate(condition: JudgementCondition, runtime: ScriptRuntime): Boolean {
        return when (condition) {
            is JudgementCondition.Variable -> {
                val actual = runtime.getVariable(condition.variableName)?.toIntOrNull() ?: return false
                val expected = condition.expectedValue.toIntOrNull() ?: return false
                when (condition.operator) {
                    VariableComparisonOperator.EQUALS -> actual == expected
                    VariableComparisonOperator.LESS_THAN_OR_EQUALS -> actual <= expected
                }
            }
            is JudgementCondition.OcrText -> evaluateOcr(condition, runtime)
            is JudgementCondition.Image -> evaluateImage(condition, runtime)
            is JudgementCondition.RegionColor -> evaluateColor(condition, runtime)
        }
    }

    private suspend fun evaluateOcr(
        condition: JudgementCondition.OcrText,
        runtime: ScriptRuntime
    ): Boolean {
        val vision = runtime.visionController ?: return conditionFailure("OCR 需要屏幕录制权限")
        val region = when (condition.scope) {
            TextJudgementScope.FULL_SCREEN -> null
            TextJudgementScope.REGION -> condition.region?.takeIf { it.width > 0 && it.height > 0 }
                ?.toAndroidRect() ?: return conditionFailure("OCR 条件执行失败：区域无效")
        }
        return when (val result = vision.recognizeTextResult(region)) {
            is VisionResult.Success -> result.value.contains(condition.expectedText)
            VisionResult.NotFound -> false
            VisionResult.Timeout -> conditionFailure("OCR 条件执行失败：文字识别超时")
            VisionResult.PermissionDenied -> conditionFailure("OCR 条件执行失败：屏幕录制权限不可用")
            is VisionResult.Failed -> conditionFailure("OCR 条件执行失败：${result.message}")
        }
    }

    private suspend fun evaluateImage(
        condition: JudgementCondition.Image,
        runtime: ScriptRuntime
    ): Boolean {
        val templateId = condition.imageId.trim()
        if (templateId.isBlank()) return false
        val region = when (condition.scope) {
            ImageJudgementScope.FULL_SCREEN -> null
            ImageJudgementScope.REGION -> condition.region?.takeIf { it.width > 0 && it.height > 0 }
                ?.toAndroidRect() ?: return false
        }
        val vision = runtime.visionController ?: return conditionFailure("图像条件需要屏幕录制权限")
        return when (val result = vision.matchResult(templateId, IMAGE_MATCH_THRESHOLD, region)) {
            is VisionResult.Success -> true
            VisionResult.NotFound -> false
            VisionResult.Timeout -> conditionFailure("图像条件执行失败：截图超时")
            VisionResult.PermissionDenied -> conditionFailure("图像条件执行失败：屏幕录制权限不可用")
            is VisionResult.Failed -> conditionFailure("图像条件执行失败：${result.message}")
        }
    }

    private suspend fun evaluateColor(
        condition: JudgementCondition.RegionColor,
        runtime: ScriptRuntime
    ): Boolean {
        val targetColor = parseHexColor(condition.color)
            ?: return conditionFailure("找色条件执行失败：颜色参数无效")
        if (condition.tolerance !in 0..MAX_COLOR_TOLERANCE) {
            return conditionFailure("找色条件执行失败：RGB 通道容差无效")
        }
        if (condition.region.width <= 0 || condition.region.height <= 0) {
            return conditionFailure("找色条件执行失败：区域无效")
        }
        val vision = runtime.visionController ?: return conditionFailure("找色条件需要屏幕录制权限")
        return when (val result = vision.findColorResult(
            targetColor,
            condition.tolerance,
            condition.region.toAndroidRect()
        )) {
            is VisionResult.Success -> true
            VisionResult.NotFound -> false
            VisionResult.Timeout -> conditionFailure("找色条件执行失败：截图超时")
            VisionResult.PermissionDenied -> conditionFailure("找色条件执行失败：屏幕录制权限不可用")
            is VisionResult.Failed -> conditionFailure("找色条件执行失败：${result.message}")
        }
    }

    private fun conditionFailure(message: String): Boolean {
        Log.w(TAG, message)
        return false
    }

    private fun com.example.myapplication.script.model.Rect.toAndroidRect() = AndroidRect().also {
        it.left = left
        it.top = top
        it.right = right
        it.bottom = bottom
    }

    private fun parseHexColor(value: String): Int? {
        val hex = value.trim().takeIf { it.matches(HEX_COLOR) } ?: return null
        return hex.substring(1).toIntOrNull(16)
    }

    private const val TAG = "ActionCondition"
    private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
    private const val IMAGE_MATCH_THRESHOLD = 0.85f
    private const val MAX_COLOR_TOLERANCE = 255
}
