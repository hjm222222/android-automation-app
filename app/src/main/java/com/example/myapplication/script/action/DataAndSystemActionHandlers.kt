package com.example.myapplication.script.action

import android.accessibilityservice.AccessibilityService
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ScriptRuntime

class CreateVariableActionHandler : EmptyActionHandler(ActionType.CREATE_VARIABLE) {
    override val isAvailable: Boolean = true

    override fun createDefault(): ScriptAction = ScriptAction(
        type = ActionType.CREATE_VARIABLE,
        displayName = "创建变量",
        parameters = mapOf("name" to "", "value" to "")
    )

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val name = action.parameters[ActionParameterKey.VARIABLE_NAME].orEmpty().trim()
        val value = action.parameters[ActionParameterKey.VARIABLE_VALUE].orEmpty()
        return if (runtime.createVariable(name, value)) ActionExecutionResult.Success
        else ActionExecutionResult.Failed("变量名为空或已存在：$name")
    }
}

class SetVariableActionHandler : EmptyActionHandler(ActionType.SET_VARIABLE) {
    override val isAvailable: Boolean = true

    override fun createDefault(): ScriptAction = ScriptAction(
        type = ActionType.SET_VARIABLE,
        displayName = "设置变量",
        parameters = mapOf("name" to "", "value" to "")
    )

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val name = action.parameters[ActionParameterKey.VARIABLE_NAME].orEmpty().trim()
        val value = action.parameters[ActionParameterKey.VARIABLE_VALUE].orEmpty()
        return if (runtime.setVariable(name, value)) ActionExecutionResult.Success
        else ActionExecutionResult.Failed("变量不存在：$name")
    }
}

class SystemNavigationActionHandler : EmptyActionHandler(ActionType.SYSTEM_NAVIGATION) {
    override val isAvailable: Boolean = true

    override fun createDefault(): ScriptAction = ScriptAction(
        type = ActionType.SYSTEM_NAVIGATION,
        displayName = "系统导航：返回",
        parameters = mapOf(ActionParameterKey.NAVIGATION_ACTION to "BACK")
    )

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val controller = runtime.accessibilityController
            ?: return ActionExecutionResult.Failed(
                "请先启用无障碍服务",
                ActionExecutionFailureCode.PLATFORM_DISCONNECTED
            )
        val globalAction = when (action.parameters[ActionParameterKey.NAVIGATION_ACTION]?.uppercase()) {
            "BACK" -> AccessibilityService.GLOBAL_ACTION_BACK
            "HOME" -> AccessibilityService.GLOBAL_ACTION_HOME
            "RECENTS" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> return ActionExecutionResult.Failed("系统导航动作无效")
        }
        return if (controller.performGlobalAction(globalAction)) {
            ActionExecutionResult.Success
        } else {
            ActionExecutionResult.Failed(
                "系统导航执行失败",
                ActionExecutionFailureCode.ACTION_FAILED
            )
        }
    }
}

class AppControlActionHandler : EmptyActionHandler(ActionType.APP_CONTROL) {
    override val isAvailable: Boolean = true

    override fun createDefault(): ScriptAction = ScriptAction(
        type = ActionType.APP_CONTROL,
        displayName = "应用控制",
        parameters = mapOf(
            ActionParameterKey.APP_CONTROL_OPERATION to "LAUNCH",
            ActionParameterKey.PACKAGE_NAME to ""
        )
    )

    override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
        val operation = action.parameters[ActionParameterKey.APP_CONTROL_OPERATION]?.uppercase()
        val packageName = action.parameters[ActionParameterKey.PACKAGE_NAME].orEmpty().trim()
        if (packageName.isBlank()) {
            return ActionExecutionResult.Failed("应用包名不能为空")
        }
        val controller = runtime.applicationController
            ?: return ActionExecutionResult.Failed("应用控制能力不可用")
        if (operation == "CLOSE") {
            val accessibility = runtime.accessibilityController
                ?: return ActionExecutionResult.Failed("关闭应用需要无障碍服务", ActionExecutionFailureCode.PLATFORM_DISCONNECTED)
            if (accessibility.currentPackageName() != packageName) {
                return ActionExecutionResult.Failed("当前无障碍窗口不是目标应用，无法确认已关闭")
            }
            return if (accessibility.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                ActionExecutionResult.Success
            } else {
                ActionExecutionResult.Failed("关闭应用失败，系统返回操作未执行")
            }
        }
        if (operation != "LAUNCH") return ActionExecutionResult.Failed("应用操作无效")
        return when (val result = controller.launch(packageName)) {
            is com.example.myapplication.script.platform.ApplicationControlResult.Success ->
                ActionExecutionResult.Success
            is com.example.myapplication.script.platform.ApplicationControlResult.Failed ->
                ActionExecutionResult.Failed(result.message)
        }
    }
}
