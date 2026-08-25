package com.example.myapplication.script.model

data class ScriptAction(
    val type: ActionType,
    /** 脚本动作的稳定标识，用于 UI、AI 和导入器安全定位已有动作。 */
    val id: String = java.util.UUID.randomUUID().toString(),
    val displayName: String = type.displayName,
    val parameters: Map<String, String> = emptyMap(),
    val executionOptions: ActionExecutionOptions = ActionExecutionOptions(),
    val beforeActions: List<ScriptAction> = emptyList(),
    val afterActions: List<ScriptAction> = emptyList()
)
