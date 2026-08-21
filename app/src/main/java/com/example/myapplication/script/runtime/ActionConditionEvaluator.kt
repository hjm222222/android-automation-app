package com.example.myapplication.script.runtime

import com.example.myapplication.script.model.ActionCondition
import com.example.myapplication.script.model.JudgementCondition
import com.example.myapplication.script.model.VariableComparisonOperator

object ActionConditionEvaluator {
    fun shouldExecute(condition: ActionCondition, runtime: ScriptRuntime): Boolean {
        return when (condition) {
            ActionCondition.Always -> true
            is ActionCondition.VariableEquals -> {
                runtime.getVariable(condition.variableName) == condition.expectedValue
            }
            is ActionCondition.Judgement -> evaluate(condition.condition, runtime)
        }
    }

    private fun evaluate(condition: JudgementCondition, runtime: ScriptRuntime): Boolean {
        return when (condition) {
            is JudgementCondition.Variable -> {
                val actual = runtime.getVariable(condition.variableName)?.toIntOrNull() ?: return false
                val expected = condition.expectedValue.toIntOrNull() ?: return false
                when (condition.operator) {
                    VariableComparisonOperator.EQUALS -> actual == expected
                    VariableComparisonOperator.LESS_THAN_OR_EQUALS -> actual <= expected
                }
            }
            is JudgementCondition.OcrText,
            is JudgementCondition.Image,
            is JudgementCondition.RegionColor -> false
        }
    }
}
