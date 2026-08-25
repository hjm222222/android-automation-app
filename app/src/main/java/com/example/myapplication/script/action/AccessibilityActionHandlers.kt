package com.example.myapplication.script.action

import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.platform.AccessibilityNodeSelector
import com.example.myapplication.script.runtime.ScriptRuntime

class ClickNodeActionHandler : ScriptActionHandler {
    override val isAvailable: Boolean = true

    override fun createDefault(): ScriptAction = ScriptAction(type = ActionType.CLICK_NODE)

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val controller = runtime.accessibilityController
            ?: return ActionExecutionResult.Failed(
                "请先启用无障碍服务",
                ActionExecutionFailureCode.PLATFORM_DISCONNECTED
            )
        val selector = selectorFrom(action) ?: return ActionExecutionResult.Failed("控件选择条件为空")
        return if (controller.clickNode(selector)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed("当前屏幕未找到选中的控件")
        }
    }

    private fun selectorFrom(action: ScriptAction): AccessibilityNodeSelector? {
        val values = action.parameters
        val selector = AccessibilityNodeSelector(
            text = values[ActionParameterKey.NODE_TEXT].nullIfBlank(),
            contentDescription = values[ActionParameterKey.NODE_DESCRIPTION].nullIfBlank(),
            resourceId = values[ActionParameterKey.NODE_RESOURCE_ID].nullIfBlank(),
            className = values[ActionParameterKey.NODE_CLASS_NAME].nullIfBlank(),
            packageName = values[ActionParameterKey.NODE_PACKAGE_NAME].nullIfBlank()
        )
        return selector.takeIf {
            it.text != null || it.contentDescription != null || it.resourceId != null ||
                it.className != null || it.packageName != null
        }
    }
}

private fun String?.nullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
