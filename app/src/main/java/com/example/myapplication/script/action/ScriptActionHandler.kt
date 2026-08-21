package com.example.myapplication.script.action

import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ScriptRuntime

interface ScriptActionHandler {
    fun createDefault(): ScriptAction
    suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult
}

sealed interface ActionExecutionResult {
    data object Success : ActionExecutionResult
    data object NotImplemented : ActionExecutionResult
    data class Failed(val message: String) : ActionExecutionResult
}
