package com.example.myapplication.script.model

enum class ActionInputType {
    TEXT,
    NUMBER
}

data class ActionFieldDefinition(
    val key: String,
    val hint: String,
    val inputType: ActionInputType = ActionInputType.TEXT,
    val defaultValue: String = ""
)

data class ActionEditorDefinition(
    val fields: List<ActionFieldDefinition>,
    val displayName: (Map<String, String>) -> String
)

object ActionParameterKey {
    const val DURATION_MILLIS = "durationMs"
    const val VARIABLE_NAME = "name"
    const val VARIABLE_VALUE = "value"
}
