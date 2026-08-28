package com.example.myapplication.script.runtime

import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.action.ScriptActionHandler
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.platform.AccessibilityController
import com.example.myapplication.script.platform.ApplicationController

/**
 * 脚本的统一执行入口。
 *
 * 所有脚本来源（悬浮窗编辑、未来的 AI、录制或导入）最终都交给这里执行，
 * 这样条件判断、Handler 查找和失败处理不会在多个功能中重复实现。
 */
class ScriptRunner(
    /**
     * 由应用组装层提供当前的平台能力。
     * 默认返回 null，便于纯业务测试，也避免运行层依赖具体 Android Service。
     */
    private val accessibilityControllerProvider: () -> AccessibilityController? = { null },
    private val applicationControllerProvider: () -> ApplicationController? = { null },
    private val visionControllerProvider: () -> com.example.myapplication.script.platform.VisionController? = { null },
    private val initialVariablesProvider: () -> Map<String, String> = { emptyMap() },
    /**
     * 动作解析器由外部注入，运行器不关心 Handler 是手工注册、测试替身还是 DI 提供。
     */
    private val handlerResolver: (ActionType) -> ScriptActionHandler? = { null },
    private val onActionStarted: suspend (ScriptAction) -> Unit = {},
    private val onActionCompleted: suspend (ScriptAction, ActionExecutionResult) -> Unit = { _, _ -> }
) {
    suspend fun run(actions: List<ScriptAction>): ActionExecutionResult {
        val runtime = ScriptRuntime(
            accessibilityController = accessibilityControllerProvider(),
            applicationController = applicationControllerProvider(),
            visionController = visionControllerProvider(),
            initialVariables = initialVariablesProvider()
        )
        return runActions(actions, runtime)
    }

    /**
     * 所有动作都经过同一个递归入口，保证嵌套动作与顶层动作使用相同的
     * 条件、前置和后置执行语义。
     */
    private suspend fun runActions(
        actions: List<ScriptAction>,
        runtime: ScriptRuntime
    ): ActionExecutionResult {
        for (action in actions) {
            onActionStarted(action)
            // #region debug-point A:action-before
            android.util.Log.d("ScriptRunner", "[DEBUG] action before id=${action.id} type=${action.type} parameterKeys=${action.parameters.keys}")
            // #endregion
            if (!ActionConditionEvaluator.shouldExecute(action.executionOptions.condition, runtime)) {
                continue
            }

            val beforeResult = runActions(action.beforeActions, runtime)
            if (beforeResult !is ActionExecutionResult.Success) return beforeResult

            val result = executeAction(action, runtime)
            onActionCompleted(action, result)
            if (result !is ActionExecutionResult.Success) return result

            val afterResult = runActions(action.afterActions, runtime)
            if (afterResult !is ActionExecutionResult.Success) return afterResult
        }
        return ActionExecutionResult.Success
    }

    private suspend fun executeAction(
        action: ScriptAction,
        runtime: ScriptRuntime
    ): ActionExecutionResult {
        val handler = handlerResolver(action.type)
            ?: return ActionExecutionResult.Failed("找不到动作处理器：${action.type}")
        if (!handler.isAvailable) {
            val message = if (action.type == ActionType.PICK_COLOR) {
                "取色需要屏幕录制权限，请先返回主页授权"
            } else {
                "动作暂不可用：${action.displayName}"
            }
            return ActionExecutionResult.Failed(message)
        }
        return handler.execute(action, runtime)
    }
}
