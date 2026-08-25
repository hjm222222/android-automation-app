package com.example.myapplication.script.runtime

import com.example.myapplication.script.model.ScriptAction

/** 脚本启动时冻结的执行输入，避免编辑中的工作区影响当前运行。 */
data class ScriptExecutionSnapshot(
    val scriptId: String?,
    val scriptName: String?,
    val actions: List<ScriptAction>,
    val initialVariables: Map<String, String>
)
