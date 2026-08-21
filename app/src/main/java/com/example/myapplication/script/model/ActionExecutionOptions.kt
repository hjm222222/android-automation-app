package com.example.myapplication.script.model

sealed interface ActionCondition {
    data object Always : ActionCondition

    data class VariableEquals(
        val variableName: String,
        val expectedValue: String
    ) : ActionCondition

    data class Judgement(val condition: JudgementCondition) : ActionCondition
}

data class ActionExecutionOptions(
    val condition: ActionCondition = ActionCondition.Always
)
