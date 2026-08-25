package com.example.myapplication.script.runtime

import com.example.myapplication.script.model.ScriptAction

/**
 * 工作区唯一接受的动作列表变更命令。
 * UI、AI、录制器和导入器都通过命令修改列表，而不是直接操作内部 MutableList。
 */
sealed interface WorkspaceActionCommand {
    data class Add(
        val action: ScriptAction,
        val position: Int? = null
    ) : WorkspaceActionCommand

    data class Replace(
        val actionId: String,
        val action: ScriptAction
    ) : WorkspaceActionCommand

    data class Remove(val actionId: String) : WorkspaceActionCommand

    data class Move(
        val actionId: String,
        val targetPosition: Int
    ) : WorkspaceActionCommand
}

/** 动作列表修改阶段的稳定错误码。 */
enum class WorkspaceFailureCode {
    /** 插入或移动目标超出当前动作列表允许的范围。 */
    INVALID_POSITION,

    /** 要修改、删除或移动的动作 ID 不存在。 */
    ACTION_NOT_FOUND
}

sealed interface WorkspaceCommandResult {
    data class Success(val actions: List<ScriptAction>) : WorkspaceCommandResult
    data class Invalid(
        val code: WorkspaceFailureCode,
        val message: String
    ) : WorkspaceCommandResult
}
