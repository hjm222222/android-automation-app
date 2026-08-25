package com.example.myapplication.script.action

import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ScriptRuntime
import kotlinx.coroutines.delay

abstract class EmptyActionHandler(private val type: ActionType) : ScriptActionHandler {
    override val isAvailable: Boolean = false

    override fun createDefault(): ScriptAction = ScriptAction(type = type)

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult =
        ActionExecutionResult.NotImplemented
}

private const val DEFAULT_PRESS_DURATION_MILLIS = 80L

class ClickActionHandler : EmptyActionHandler(ActionType.CLICK) {
    override val isAvailable: Boolean = true

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val controller = runtime.accessibilityController
            ?: return ActionExecutionResult.Failed(
                "请先启用无障碍服务",
                ActionExecutionFailureCode.PLATFORM_DISCONNECTED
            )
        val x = action.parameters[ActionParameterKey.X]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("点击 X 坐标无效")
        val y = action.parameters[ActionParameterKey.Y]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("点击 Y 坐标无效")
        val duration = action.parameters[ActionParameterKey.DURATION_MILLIS]
            ?.toLongOrNull()
            ?: DEFAULT_PRESS_DURATION_MILLIS
        if (duration < 1L) return ActionExecutionResult.Failed("按下时长必须大于 0")
        return if (controller.press(x, y, duration)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed(
                "点击手势执行失败",
                ActionExecutionFailureCode.GESTURE_REJECTED
            )
        }
    }
}

class LongClickActionHandler : EmptyActionHandler(ActionType.LONG_CLICK) {
    override val isAvailable: Boolean = true

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val controller = runtime.accessibilityController
            ?: return ActionExecutionResult.Failed(
                "请先启用无障碍服务",
                ActionExecutionFailureCode.PLATFORM_DISCONNECTED
            )
        val x = action.parameters[ActionParameterKey.X]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("长按 X 坐标无效")
        val y = action.parameters[ActionParameterKey.Y]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("长按 Y 坐标无效")
        val duration = action.parameters[ActionParameterKey.DURATION_MILLIS]
            ?.toLongOrNull()
            ?: 800L
        if (duration < 500L) return ActionExecutionResult.Failed("长按时长不能小于 500ms")
        return if (controller.press(x, y, duration)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed(
                "长按手势执行失败",
                ActionExecutionFailureCode.GESTURE_REJECTED
            )
        }
    }
}

class DoubleClickActionHandler : EmptyActionHandler(ActionType.DOUBLE_CLICK) {
    override val isAvailable: Boolean = true

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val controller = runtime.accessibilityController
            ?: return ActionExecutionResult.Failed(
                "请先启用无障碍服务",
                ActionExecutionFailureCode.PLATFORM_DISCONNECTED
            )
        val x = action.parameters[ActionParameterKey.X]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("双击 X 坐标无效")
        val y = action.parameters[ActionParameterKey.Y]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("双击 Y 坐标无效")
        val duration = action.parameters[ActionParameterKey.DURATION_MILLIS]
            ?.toLongOrNull()
            ?: DEFAULT_PRESS_DURATION_MILLIS
        return if (controller.doublePress(x, y, duration)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed(
                "双击手势执行失败",
                ActionExecutionFailureCode.GESTURE_REJECTED
            )
        }
    }
}

class SwipeActionHandler : EmptyActionHandler(ActionType.SWIPE) {
    override val isAvailable: Boolean = true

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val controller = runtime.accessibilityController
            ?: return ActionExecutionResult.Failed(
                "请先启用无障碍服务",
                ActionExecutionFailureCode.PLATFORM_DISCONNECTED
            )
        val startX = action.parameters[ActionParameterKey.START_X]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("滑动起点 X 坐标无效")
        val startY = action.parameters[ActionParameterKey.START_Y]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("滑动起点 Y 坐标无效")
        val endX = action.parameters[ActionParameterKey.END_X]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("滑动终点 X 坐标无效")
        val endY = action.parameters[ActionParameterKey.END_Y]?.toIntOrNull()
            ?: return ActionExecutionResult.Failed("滑动终点 Y 坐标无效")
        val duration = action.parameters[ActionParameterKey.DURATION_MILLIS]?.toLongOrNull()
            ?: return ActionExecutionResult.Failed("滑动持续时间无效")
        if (duration < 1L) return ActionExecutionResult.Failed("滑动持续时间必须大于 0")
        return if (controller.swipe(startX, startY, endX, endY, duration)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed(
                "滑动手势执行失败",
                ActionExecutionFailureCode.GESTURE_REJECTED
            )
        }
    }
}

class InputTextActionHandler : EmptyActionHandler(ActionType.INPUT_TEXT) {
    override val isAvailable: Boolean = true

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val controller = runtime.accessibilityController
            ?: return ActionExecutionResult.Failed(
                "请先启用无障碍服务",
                ActionExecutionFailureCode.PLATFORM_DISCONNECTED
            )
        val text = action.parameters[ActionParameterKey.TEXT]
            ?: return ActionExecutionResult.Failed("输入文字不能为空")
        return if (controller.setFocusedText(text)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed("未找到可输入的焦点控件")
        }
    }
}

class WaitActionHandler : EmptyActionHandler(ActionType.WAIT) {
    override val isAvailable: Boolean = true

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
