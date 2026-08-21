package com.example.myapplication.script.model

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
        val expectedText: String
    ) : JudgementCondition

    data class Image(
        val scope: ImageJudgementScope,
        val imageId: String
    ) : JudgementCondition

    data class RegionColor(
        val color: String,
        val region: String
    ) : JudgementCondition
}
