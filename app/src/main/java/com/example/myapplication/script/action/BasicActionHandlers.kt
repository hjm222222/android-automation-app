package com.example.myapplication.script.action

import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ScriptRuntime
import kotlinx.coroutines.delay

abstract class EmptyActionHandler(private val type: ActionType) : ScriptActionHandler {
    override fun createDefault(): ScriptAction = ScriptAction(type)

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult =
        ActionExecutionResult.NotImplemented
}

class ClickActionHandler : EmptyActionHandler(ActionType.CLICK)
class LongClickActionHandler : EmptyActionHandler(ActionType.LONG_CLICK)
class DoubleClickActionHandler : EmptyActionHandler(ActionType.DOUBLE_CLICK)
class SwipeActionHandler : EmptyActionHandler(ActionType.SWIPE)
class InputTextActionHandler : EmptyActionHandler(ActionType.INPUT_TEXT)

class WaitActionHandler : EmptyActionHandler(ActionType.WAIT) {
    override fun createDefault(): ScriptAction = ScriptAction(
        type = ActionType.WAIT,
        displayName = "等待 1 秒",
        parameters = mapOf(ActionParameterKey.DURATION_MILLIS to "1000")
    )

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val duration = action.parameters[ActionParameterKey.DURATION_MILLIS]?.toLongOrNull()
            ?: return ActionExecutionResult.Failed("等待时长无效")
        if (duration < 0) return ActionExecutionResult.Failed("等待时长不能小于 0")
        delay(duration)
        return ActionExecutionResult.Success
    }
}
