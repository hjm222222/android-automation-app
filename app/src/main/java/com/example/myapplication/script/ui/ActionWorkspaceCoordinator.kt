package com.example.myapplication.script.ui

import com.example.myapplication.script.api.ActionApiResult
import com.example.myapplication.script.api.ScriptActionApi
import com.example.myapplication.script.model.ActionSettings
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction

/**
 * 协调当前工作区的动作树编辑结果。
 *
 * 不依赖窗口和 Service，界面层只需要根据返回值刷新或显示错误。
 */
class ActionWorkspaceCoordinator(
    private val actionApi: ScriptActionApi
) {
    fun actions(): List<ScriptAction> = actionApi.listActions().mapNotNull { actionApi.action(it.id) }

    fun action(actionId: String): ScriptAction? = actionApi.action(actionId)

    fun replace(
        actionId: String,
        type: ActionType,
        fields: Map<String, String>,
        settings: ActionSettings,
        displayName: String? = null
    ): ActionApiResult = actionApi.replaceAction(actionId, type, fields, settings, displayName)

    fun move(actionId: String, targetPosition: Int): ActionApiResult =
        actionApi.move(actionId, targetPosition)

    fun remove(actionId: String): ActionApiResult = actionApi.remove(actionId)
}
