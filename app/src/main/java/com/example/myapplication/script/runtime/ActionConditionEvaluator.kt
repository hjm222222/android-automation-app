package com.example.myapplication.script.runtime

import android.graphics.Rect as AndroidRect
import com.example.myapplication.script.model.ActionCondition
import com.example.myapplication.script.model.ImageJudgementScope
import com.example.myapplication.script.model.JudgementCondition
import com.example.myapplication.script.model.VariableComparisonOperator
import kotlin.math.abs

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
            is JudgementCondition.OcrText -> runtime.lastOcrText?.contains(condition.expectedText) == true
            is JudgementCondition.Image -> {
                val templateId = condition.imageId.trim()
                if (templateId.isBlank()) return false
                val region = when (condition.scope) {
                    ImageJudgementScope.FULL_SCREEN -> null
                    ImageJudgementScope.REGION -> condition.region?.takeIf { it.width > 0 && it.height > 0 }
                        ?: return false
                }
                runtime.visionController?.match(templateId, IMAGE_MATCH_THRESHOLD, region?.toAndroidRect()) != null
            }
            is JudgementCondition.RegionColor -> matchesRegionColor(condition, runtime)
        }
    }

    private suspend fun matchesRegionColor(
        condition: JudgementCondition.RegionColor,
        runtime: ScriptRuntime
    ): Boolean {
        val targetColor = parseHexColor(condition.color) ?: return false
        if (condition.tolerance !in 0..MAX_COLOR_TOLERANCE) return false
        val capture = runtime.visionController?.capture() ?: return false
        val left = condition.region.left.coerceIn(0, capture.width)
        val top = condition.region.top.coerceIn(0, capture.height)
        val right = condition.region.right.coerceIn(0, capture.width)
        val bottom = condition.region.bottom.coerceIn(0, capture.height)
        if (left >= right || top >= bottom) return false

        val targetRed = targetColor.redChannel()
        val targetGreen = targetColor.greenChannel()
        val targetBlue = targetColor.blueChannel()
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = capture.pixelAt(x, y)
                if (
                    abs(pixel.redChannel() - targetRed) <= condition.tolerance &&
                    abs(pixel.greenChannel() - targetGreen) <= condition.tolerance &&
                    abs(pixel.blueChannel() - targetBlue) <= condition.tolerance
                ) return true
            }
        }
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

    private fun Int.redChannel(): Int = (this ushr 16) and 0xFF
    private fun Int.greenChannel(): Int = (this ushr 8) and 0xFF
    private fun Int.blueChannel(): Int = this and 0xFF

    private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
    private const val IMAGE_MATCH_THRESHOLD = 0.85f
    private const val MAX_COLOR_TOLERANCE = 255
}
