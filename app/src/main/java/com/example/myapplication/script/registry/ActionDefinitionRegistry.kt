package com.example.myapplication.script.registry

import com.example.myapplication.script.action.ScriptActionHandler
import com.example.myapplication.script.model.ActionEditorDefinition
import com.example.myapplication.script.model.ActionType

/**
 * 动作创建和编辑所需的统一查询入口。
 *
 * 新增动作时，必须同时提供 Handler 和编辑定义。暂不可编辑或不可执行的动作
 * 不会通过 ActionFactory 创建，避免 UI 或外部调用方得到不可运行的动作。
 */
object ActionDefinitionRegistry {
    fun definitionFor(type: ActionType): ActionDefinition? {
        val handler = ActionRegistry.handlerFor(type) ?: return null
        val editor = ActionEditorRegistry.definitionFor(type) ?: return null
        return ActionDefinition(
            handler = handler,
            editor = editor
        )
    }

    fun availableDefinitionFor(type: ActionType): ActionDefinition? {
        val definition = definitionFor(type) ?: return null
        return definition.takeIf { it.handler.isAvailable }
    }

    /** 只有默认参数完整的动作，才允许在没有编辑器的场景直接创建。 */
    fun canCreateDefault(type: ActionType): Boolean {
        val definition = availableDefinitionFor(type) ?: return false
        return definition.editor.fields.all { it.defaultValue.isNotBlank() }
    }

    fun availableTypes(): List<ActionType> = ActionType.entries.filter {
        availableDefinitionFor(it) != null
    }
}

data class ActionDefinition(
    val handler: ScriptActionHandler,
    val editor: ActionEditorDefinition
)
