package com.example.myapplication.script.model

data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class VariableComparisonOperator(val symbol: String) {
    EQUALS("=="),
    LESS_THAN_OR_EQUALS("<=")
}

enum class TextJudgementScope {
    REGION,
    FULL_SCREEN
}

enum class ImageJudgementScope {
    REGION,
    FULL_SCREEN
}

sealed interface JudgementCondition {
    data class Variable(
        val variableName: String = "a",
        val operator: VariableComparisonOperator = VariableComparisonOperator.EQUALS,
        val expectedValue: String = "0"
    ) : JudgementCondition

    data class OcrText(
        val scope: TextJudgementScope,
        val expectedText: String,
        val region: Rect? = null
    ) : JudgementCondition

    data class Image(
        val scope: ImageJudgementScope,
        val imageId: String,
        val region: Rect? = null
    ) : JudgementCondition

    data class RegionColor(
        val color: String,
        val region: Rect,
        val tolerance: Int = 0
    ) : JudgementCondition
}
