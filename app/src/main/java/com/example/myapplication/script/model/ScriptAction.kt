package com.example.myapplication.script.model

data class ScriptAction(
    val type: ActionType,
    val displayName: String = type.displayName,
    val parameters: Map<String, String> = emptyMap(),
    val executionOptions: ActionExecutionOptions = ActionExecutionOptions(),
    val beforeActions: List<ScriptAction> = emptyList(),
    val afterActions: List<ScriptAction> = emptyList()
)
