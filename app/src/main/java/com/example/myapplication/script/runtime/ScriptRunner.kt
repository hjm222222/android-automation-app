package com.example.myapplication.script.runtime

import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.registry.ActionRegistry
import com.example.myapplication.script.model.ScriptAction

class ScriptRunner {
    suspend fun run(actions: List<ScriptAction>): ActionExecutionResult {
        val runtime = ScriptRuntime()
        for (action in actions) {
            if (!ActionConditionEvaluator.shouldExecute(action.executionOptions.condition, runtime)) {
                continue
            }
            val handler = ActionRegistry.handlerFor(action.type)
                ?: return ActionExecutionResult.Failed("找不到动作处理器：${action.type}")
            val result = handler.execute(action, runtime)
            if (result !is ActionExecutionResult.Success) return result
        }
        return ActionExecutionResult.Success
    }
}
