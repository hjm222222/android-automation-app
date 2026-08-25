package com.example.myapplication.script.runtime

import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.registry.ActionRegistry

/**
 * 当前工作区脚本的唯一管理入口。
 *
 * UI、AI、录制器和导入器都可以向这里提供 ScriptAction，但它们不应该
 * 互相直接调用。这样，脚本来源可以继续增加，而脚本执行仍然只有一条路径。
 */
class ScriptWorkspaceController(
    private val scriptRunner: ScriptRunner = ScriptRunner(
        handlerResolver = ActionRegistry::handlerFor
    )
) {
    private val actions = mutableListOf<ScriptAction>()
    private val actionsLock = Any()

    val isEmpty: Boolean
        get() = synchronized(actionsLock) { actions.isEmpty() }

    /** 返回副本，避免界面代码绕过控制器直接修改内部列表。 */
    fun snapshot(): List<ScriptAction> = synchronized(actionsLock) { actions.toList() }

    fun add(action: ScriptAction) {
        apply(WorkspaceActionCommand.Add(action))
    }

    fun replaceAll(newActions: List<ScriptAction>) = synchronized(actionsLock) {
        actions.clear()
        actions.addAll(newActions)
    }

    /**
     * 将 UI、AI 或导入器的列表修改集中在这里，避免外部依赖易失效的列表索引。
     */
    fun apply(command: WorkspaceActionCommand): WorkspaceCommandResult = synchronized(actionsLock) {
        when (command) {
            is WorkspaceActionCommand.Add -> {
                val position = command.position ?: actions.size
                if (position !in 0..actions.size) {
                    return WorkspaceCommandResult.Invalid(
                        WorkspaceFailureCode.INVALID_POSITION,
                        "插入位置无效"
                    )
                }
                actions.add(position, command.action)
            }

            is WorkspaceActionCommand.Replace -> {
                val index = actions.indexOfFirst { it.id == command.actionId }
                if (index == -1) {
                    return WorkspaceCommandResult.Invalid(
                        WorkspaceFailureCode.ACTION_NOT_FOUND,
                        "未找到要修改的动作"
                    )
                }
                actions[index] = command.action.copy(id = command.actionId)
            }

            is WorkspaceActionCommand.Remove -> {
                val index = actions.indexOfFirst { it.id == command.actionId }
                if (index == -1) {
                    return WorkspaceCommandResult.Invalid(
                        WorkspaceFailureCode.ACTION_NOT_FOUND,
                        "未找到要删除的动作"
                    )
                }
                actions.removeAt(index)
            }

            is WorkspaceActionCommand.Move -> {
                val index = actions.indexOfFirst { it.id == command.actionId }
                if (index == -1) {
                    return WorkspaceCommandResult.Invalid(
                        WorkspaceFailureCode.ACTION_NOT_FOUND,
                        "未找到要移动的动作"
                    )
                }
                if (command.targetPosition !in actions.indices) {
                    return WorkspaceCommandResult.Invalid(
                        WorkspaceFailureCode.INVALID_POSITION,
                        "目标位置无效"
                    )
                }
                val action = actions.removeAt(index)
                actions.add(command.targetPosition, action)
            }
        }
        return WorkspaceCommandResult.Success(snapshot())
    }

    suspend fun run(): ActionExecutionResult = scriptRunner.run(snapshot())

    suspend fun run(snapshot: ScriptExecutionSnapshot): ActionExecutionResult =
        scriptRunner.run(snapshot.actions, snapshot.initialVariables)
}
