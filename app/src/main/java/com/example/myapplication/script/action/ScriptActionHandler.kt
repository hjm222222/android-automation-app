package com.example.myapplication.script.action

import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ScriptRuntime

/**
 * 一个动作的完整执行契约。
 *
 * UI 只负责创建 ScriptAction，具体动作如何调用无障碍、截图或系统能力，
 * 必须放在对应的 Handler 中，避免 FloatingWorkspaceService 变成业务总管。
 */
interface ScriptActionHandler {
    /**
     * 只有真正完成执行逻辑的动作才允许出现在动作选择器中。
     * 未完成的动作保留类型和 Handler，便于后续实现，但不会伪装成可用功能。
     */
    val isAvailable: Boolean
        get() = true

    fun createDefault(): ScriptAction
    suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult
}

enum class ActionExecutionFailureCode {
    /** 无障碍服务尚未连接或已经断开。 */
    PLATFORM_DISCONNECTED,

    /** Android 平台拒绝执行当前手势。 */
    GESTURE_REJECTED,

    /** 其他动作执行失败。 */
    ACTION_FAILED
}

sealed interface ActionExecutionResult {
    data object Success : ActionExecutionResult
    data object NotImplemented : ActionExecutionResult
    data class Failed(
        val message: String,
        val code: ActionExecutionFailureCode = ActionExecutionFailureCode.ACTION_FAILED
    ) : ActionExecutionResult
}
