package com.example.myapplication.script.registry

import com.example.myapplication.script.model.ActionEditorDefinition
import com.example.myapplication.script.model.ActionFieldDefinition
import com.example.myapplication.script.model.ActionInputType
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType

object ActionEditorRegistry {
    private val definitions = mapOf(
        ActionType.WAIT to ActionEditorDefinition(
            fields = listOf(
                ActionFieldDefinition(
                    key = ActionParameterKey.DURATION_MILLIS,
                    hint = "等待时间（秒）",
                    inputType = ActionInputType.NUMBER,
                    defaultValue = "1"
                )
            ),
            displayName = { values -> "等待 ${values[ActionParameterKey.DURATION_MILLIS]} 秒" }
        ),
        ActionType.CREATE_VARIABLE to variableEditorDefinition("创建变量"),
        ActionType.SET_VARIABLE to variableEditorDefinition("设置变量")
    )

    fun definitionFor(type: ActionType): ActionEditorDefinition? = definitions[type]

    private fun variableEditorDefinition(label: String) = ActionEditorDefinition(
        fields = listOf(
            ActionFieldDefinition(ActionParameterKey.VARIABLE_NAME, "变量名"),
            ActionFieldDefinition(ActionParameterKey.VARIABLE_VALUE, "变量值")
        ),
        displayName = { values -> "$label：${values[ActionParameterKey.VARIABLE_NAME].orEmpty()}" }
    )
}
