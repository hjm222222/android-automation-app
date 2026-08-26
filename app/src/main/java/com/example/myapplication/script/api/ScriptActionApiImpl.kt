package com.example.myapplication.script.api

import com.example.myapplication.script.model.ActionExecutionOptions
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionSettings
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ActionCreationFailureCode
import com.example.myapplication.script.runtime.ActionCreationResult
import com.example.myapplication.script.runtime.ActionFactory
import com.example.myapplication.script.runtime.ScriptWorkspaceController
import com.example.myapplication.script.runtime.WorkspaceActionCommand
import com.example.myapplication.script.runtime.WorkspaceCommandResult
import com.example.myapplication.script.runtime.WorkspaceFailureCode

/** 默认实现，集中协调动作创建和工作区更新。 */
class ScriptActionApiImpl(
    private val workspace: ScriptWorkspaceController
) : ScriptActionApi {
    override fun addDefaultAction(
        type: ActionType,
        position: InsertPosition,
        displayName: String?
    ): ActionApiResult {
        val created = ActionFactory.createDefault(type)
        val action = created.actionOrFailure()?.let { action ->
            displayName?.let { action.copy(displayName = it) } ?: action
        } ?: return created.toApiFailure()
        val commandPosition = position.toCommandPosition(workspace.snapshot())
            ?: return failure(FailureCode.INVALID_POSITION, "插入位置无效")
        return apply(
            command = WorkspaceActionCommand.Add(action, commandPosition),
            successActionId = action.id
        )
    }

    override fun addAction(
        type: ActionType,
        fields: Map<String, String>,
        settings: ActionSettings,
        position: InsertPosition,
        displayName: String?
    ): ActionApiResult {
        return add(type, fields, settings, position, displayName)
    }

    override fun addWait(seconds: Long, position: InsertPosition): ActionApiResult {
        if (seconds < 0) {
            return failure(FailureCode.INVALID_PARAMETER, "等待时间不能小于 0 秒")
        }
        return add(
            type = ActionType.WAIT,
            fields = mapOf(ActionParameterKey.DURATION_MILLIS to seconds.toString()),
            position = position
        )
    }

    override fun addClick(x: Int, y: Int, position: InsertPosition): ActionApiResult {
        return add(
            type = ActionType.CLICK,
            fields = mapOf(
                ActionParameterKey.X to x.toString(),
                ActionParameterKey.Y to y.toString(),
                ActionParameterKey.DURATION_MILLIS to "80"
            ),
            position = position
        )
    }

    override fun remove(actionId: String): ActionApiResult {
        if (actionId.isBlank()) {
            return failure(FailureCode.INVALID_PARAMETER, "动作 ID 不能为空")
        }
        return apply(WorkspaceActionCommand.Remove(actionId))
    }

    override fun removeAt(index: Int): ActionApiResult {
        val actionId = workspace.snapshot().getOrNull(index)?.id
            ?: return failure(FailureCode.INVALID_POSITION, "动作位置无效")
        return remove(actionId)
    }

    override fun replaceWait(actionId: String, seconds: Long): ActionApiResult {
        if (actionId.isBlank()) {
            return failure(FailureCode.INVALID_PARAMETER, "动作 ID 不能为空")
        }
        if (seconds < 0) {
            return failure(FailureCode.INVALID_PARAMETER, "等待时间不能小于 0 秒")
        }
        val current = action(actionId)
            ?: return failure(FailureCode.ACTION_NOT_FOUND, "未找到要修改的动作")
        return replaceAction(
            actionId = actionId,
            type = ActionType.WAIT,
            fields = mapOf(ActionParameterKey.DURATION_MILLIS to seconds.toString()),
            settings = currentSettings(current)
        )
    }

    override fun action(actionId: String): ScriptAction? = workspace.snapshot()
        .firstOrNull { it.id == actionId }

    override fun replaceAction(
        actionId: String,
        type: ActionType,
        fields: Map<String, String>,
        settings: ActionSettings,
        displayName: String?
    ): ActionApiResult {
        if (actionId.isBlank()) {
            return failure(FailureCode.INVALID_PARAMETER, "动作 ID 不能为空")
        }
        if (action(actionId) == null) {
            return failure(FailureCode.ACTION_NOT_FOUND, "未找到要修改的动作")
        }
        val created = ActionFactory.create(type, fields, settings)
        val replacement = created.actionOrFailure()?.let { action ->
            action.copy(
                id = actionId,
                displayName = displayName ?: action.displayName
            )
        } ?: return created.toApiFailure()
        return apply(WorkspaceActionCommand.Replace(actionId, replacement))
    }

    override fun move(actionId: String, targetPosition: Int): ActionApiResult {
        if (actionId.isBlank()) {
            return failure(FailureCode.INVALID_PARAMETER, "动作 ID 不能为空")
        }
        return apply(WorkspaceActionCommand.Move(actionId, targetPosition))
    }

    override fun listActions(): List<ActionSummary> = workspace.snapshot().map { action ->
        ActionSummary(
            id = action.id,
            type = action.type.name,
            displayName = action.displayName,
            parameters = action.parameters
        )
    }

    private fun add(
        type: ActionType,
        fields: Map<String, String>,
        settings: ActionSettings = ActionSettings(
            executionOptions = ActionExecutionOptions(),
            beforeActions = emptyList(),
            afterActions = emptyList()
        ),
        position: InsertPosition,
        displayName: String? = null
    ): ActionApiResult {
        val created = ActionFactory.create(type, fields, settings)
        val action = created.actionOrFailure()?.let { action ->
            displayName?.let { action.copy(displayName = it) } ?: action
        } ?: return created.toApiFailure()
        val commandPosition = position.toCommandPosition(workspace.snapshot())
            ?: return failure(FailureCode.INVALID_POSITION, "插入位置无效")
        return apply(
            command = WorkspaceActionCommand.Add(action, commandPosition),
            successActionId = action.id
        )
    }

    private fun apply(
        command: WorkspaceActionCommand,
        successActionId: String? = null
    ): ActionApiResult {
        return when (val result = workspace.apply(command)) {
            is WorkspaceCommandResult.Success -> ActionApiResult.Success(successActionId)
            is WorkspaceCommandResult.Invalid -> {
                val code = when (result.code) {
                    WorkspaceFailureCode.INVALID_POSITION -> FailureCode.INVALID_POSITION
                    WorkspaceFailureCode.ACTION_NOT_FOUND -> FailureCode.ACTION_NOT_FOUND
                }
                failure(code, result.message)
            }
        }
    }

    private fun currentSettings(action: ScriptAction) =
        ActionSettings(
            executionOptions = action.executionOptions,
            beforeActions = action.beforeActions,
            afterActions = action.afterActions
        )
}

private fun InsertPosition.toCommandPosition(
    actions: List<com.example.myapplication.script.model.ScriptAction>
): Int? = when (this) {
    InsertPosition.End -> actions.size
    is InsertPosition.At -> index.takeIf { it in 0..actions.size }
    is InsertPosition.After -> actions.indexOfFirst { it.id == actionId }
        .takeIf { it >= 0 }
        ?.plus(1)
}

private fun ActionCreationResult.actionOrFailure() =
    (this as? ActionCreationResult.Success)?.action

private fun ActionCreationResult.toApiFailure(): ActionApiResult = when (this) {
    is ActionCreationResult.Invalid -> {
        val apiCode = when (code) {
            ActionCreationFailureCode.ACTION_NOT_DEFINED -> FailureCode.ACTION_NOT_DEFINED
            ActionCreationFailureCode.ACTION_NOT_AVAILABLE -> FailureCode.ACTION_NOT_AVAILABLE
            ActionCreationFailureCode.MISSING_FIELD -> FailureCode.MISSING_FIELD
            ActionCreationFailureCode.INVALID_NUMBER -> FailureCode.INVALID_NUMBER
        }
        ActionApiResult.Failure(apiCode, message)
    }
    is ActionCreationResult.Success ->
        ActionApiResult.Success(action.id)
}

private fun failure(code: FailureCode, message: String) =
    ActionApiResult.Failure(code, message)
