package com.example.myapplication.script.platform

/**
 * 应用控制的平台能力边界。
 *
 * 动作 Handler 只依赖这个接口，不直接接触 Context 或 PackageManager。
 * 首版实现启动应用；关闭应用暂不执行，后续可由无障碍设置页适配或 Root 实现。
 */
interface ApplicationController {
    suspend fun launch(packageName: String): ApplicationControlResult

    fun queryLaunchableApplications(): List<LaunchableApplication>
}

data class LaunchableApplication(
    val packageName: String,
    val label: String
)

sealed interface ApplicationControlResult {
    data object Success : ApplicationControlResult
    data class Failed(val message: String) : ApplicationControlResult
}
