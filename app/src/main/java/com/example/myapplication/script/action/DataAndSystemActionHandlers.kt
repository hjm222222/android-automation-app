package com.example.myapplication.script.action

import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ScriptRuntime

class CreateVariableActionHandler : EmptyActionHandler(ActionType.CREATE_VARIABLE) {
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

class SystemNavigationActionHandler : EmptyActionHandler(ActionType.SYSTEM_NAVIGATION)
class AppControlActionHandler : EmptyActionHandler(ActionType.APP_CONTROL)
