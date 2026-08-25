package com.example.myapplication.script.api

import com.example.myapplication.script.model.ActionExecutionOptions
import com.example.myapplication.script.model.ActionSettings
import com.example.myapplication.script.model.ActionType

/**
 * 面向 UI、规则解析器和未来 AI 的脚本动作接口。
 *
 * 调用方不需要了解 ScriptAction、Handler 或 MutableList。所有动作都会经过
 * ActionFactory 校验，再交给 ScriptWorkspaceController 保存。
 */
interface ScriptActionApi {
    /** 直接添加具备完整默认参数的动作，适用于无编辑器的动作入口。 */
    fun addDefaultAction(
        type: ActionType,
        position: InsertPosition = InsertPosition.End,
        displayName: String? = null
    ): ActionApiResult

    fun addAction(
        type: ActionType,
        fields: Map<String, String>,
        settings: ActionSettings = ActionSettings(
            executionOptions = ActionExecutionOptions(),
            beforeActions = emptyList(),
            afterActions = emptyList()
        ),
        position: InsertPosition = InsertPosition.End,
        displayName: String? = null
    ): ActionApiResult

    fun addWait(
        seconds: Long,
        position: InsertPosition = InsertPosition.End
    ): ActionApiResult

    fun addClick(
        x: Int,
        y: Int,
        position: InsertPosition = InsertPosition.End
    ): ActionApiResult

    fun remove(actionId: String): ActionApiResult

    fun removeAt(index: Int): ActionApiResult

    fun replaceWait(
        actionId: String,
        seconds: Long
    ): ActionApiResult

    fun listActions(): List<ActionSummary>
}

/** 动作插入位置。At 使用从 0 开始的列表位置，After 使用稳定动作 ID。 */
sealed interface InsertPosition {
    data object End : InsertPosition
    data class At(val index: Int) : InsertPosition
    data class After(val actionId: String) : InsertPosition
}

data class ActionSummary(
    val id: String,
    val type: String,
    val displayName: String,
    val parameters: Map<String, String>
)

sealed interface ActionApiResult {
    data class Success(val actionId: String? = null) : ActionApiResult

    data class Failure(
        val code: FailureCode,
        val message: String
    ) : ActionApiResult
}

/** UI、规则解析器和 AI 可以使用的稳定动作接口错误码。 */
enum class FailureCode {
    /** 请求本身不合法，例如空 ID 或负数等待时间。 */
    INVALID_PARAMETER,

    /** 动作创建缺少必填字段，应补充字段后重试。 */
    MISSING_FIELD,

    /** 动作创建中的数字字段格式错误，应输入有效数字后重试。 */
    INVALID_NUMBER,

    /** 指定的动作 ID 不存在，通常应刷新动作列表。 */
    ACTION_NOT_FOUND,

    /** 动作定义存在，但当前设备或平台暂时无法执行。 */
    ACTION_NOT_AVAILABLE,

    /** 系统没有找到该动作的完整定义，需要补充动作注册信息。 */
    ACTION_NOT_DEFINED,

    /** 动作插入或移动位置不合法，应重新计算位置。 */
    INVALID_POSITION,

    /** 请求的动作类型不在当前公开 API 支持范围内。 */
    UNSUPPORTED_ACTION
}
