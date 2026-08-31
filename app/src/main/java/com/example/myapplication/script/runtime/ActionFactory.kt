package com.example.myapplication.script.runtime

import com.example.myapplication.script.model.ActionExecutionOptions
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionSettings
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.registry.ActionDefinitionRegistry

/** 动作创建阶段的稳定错误码，调用方不应依赖 message 文本判断错误类型。 */
enum class ActionCreationFailureCode {
    /** 动作类型没有完整的 Handler、编辑器定义或参数定义。 */
    ACTION_NOT_DEFINED,

    /** 动作定义存在，但当前平台没有可用的执行能力。 */
    ACTION_NOT_AVAILABLE,

    /** 必填编辑字段为空，调用方应补充字段后重试。 */
    MISSING_FIELD,

    /** 数字字段无法解析，调用方应提示输入有效数字后重试。 */
    INVALID_NUMBER
}

sealed interface ActionCreationResult {
    data class Success(val action: ScriptAction) : ActionCreationResult
    data class Invalid(
        val code: ActionCreationFailureCode,
        val message: String
    ) : ActionCreationResult
}

/**
 * 创建完整动作的唯一入口。
 *
 * UI 和未来的 AI 只提供动作类型、编辑字段和设置；Handler、编辑器定义以及
 * 参数格式由这里统一组装，避免调用方重复了解 ScriptAction 的内部结构。
 */
object ActionFactory {
    fun create(
        type: ActionType,
        editorValues: Map<String, String>,
        settings: ActionSettings = ActionSettings(
            executionOptions = ActionExecutionOptions(),
            beforeActions = emptyList(),
            afterActions = emptyList()
        )
    ): ActionCreationResult {
        val definition = ActionDefinitionRegistry.definitionFor(type)
            ?: return ActionCreationResult.Invalid(
                ActionCreationFailureCode.ACTION_NOT_DEFINED,
                "动作未完整定义：${type.displayName}"
            )
        if (!definition.handler.isAvailable) {
            return ActionCreationResult.Invalid(
                ActionCreationFailureCode.ACTION_NOT_AVAILABLE,
                "动作暂不可用：${type.displayName}"
            )
        }

        val normalizedValues = editorValues.mapValues { (_, value) -> value.trim() }
        if (type == ActionType.CLICK_NODE) {
            val hasSelector = listOf(
                ActionParameterKey.NODE_TEXT,
                ActionParameterKey.NODE_DESCRIPTION,
                ActionParameterKey.NODE_RESOURCE_ID,
                ActionParameterKey.NODE_CLASS_NAME,
                ActionParameterKey.NODE_PACKAGE_NAME
            ).any { normalizedValues[it].orEmpty().isNotBlank() }
            if (!hasSelector) {
                return ActionCreationResult.Invalid(
                    ActionCreationFailureCode.MISSING_FIELD,
                    "至少需要一个控件识别条件"
                )
            }
        } else {
            val requiredFields = definition.editor.fields.filterNot { field ->
                (type == ActionType.OCR_TEXT && field.key == ActionParameterKey.OCR_TARGET_TEXT) ||
                    (type == ActionType.FIND_COLOR && field.key in setOf(
                        ActionParameterKey.MATCH_VARIABLE_NAME,
                        ActionParameterKey.FIND_COLOR_CLICK
                    )) ||
                    (type == ActionType.PICK_COLOR && field.key in setOf(
                        ActionParameterKey.PICK_X,
                        ActionParameterKey.PICK_Y,
                        ActionParameterKey.COLOR_VARIABLE_NAME
                    ))
            }
            val missingField = requiredFields.firstOrNull { field ->
                normalizedValues[field.key].orEmpty().isBlank()
            }
            if (missingField != null) {
                return ActionCreationResult.Invalid(
                    ActionCreationFailureCode.MISSING_FIELD,
                    "${missingField.hint}不能为空"
                )
            }
        }

        val numericKeys = when (type) {
            ActionType.CLICK,
            ActionType.LONG_CLICK,
            ActionType.DOUBLE_CLICK -> listOf(
                ActionParameterKey.X,
                ActionParameterKey.Y,
                ActionParameterKey.DURATION_MILLIS
            )
            ActionType.SWIPE -> listOf(
                ActionParameterKey.START_X,
                ActionParameterKey.START_Y,
                ActionParameterKey.END_X,
                ActionParameterKey.END_Y,
                ActionParameterKey.DURATION_MILLIS
            )
            ActionType.WAIT -> listOf(ActionParameterKey.DURATION_MILLIS)
            ActionType.PICK_COLOR -> listOf(
                ActionParameterKey.COLOR_RED,
                ActionParameterKey.COLOR_GREEN,
                ActionParameterKey.COLOR_BLUE
            )
            else -> emptyList()
        }
        for (key in numericKeys) {
            val number = normalizedValues[key]?.toLongOrNull()
                ?: return ActionCreationResult.Invalid(
                    ActionCreationFailureCode.INVALID_NUMBER,
                    "${key}必须是有效数字"
                )
            if (number < 0L) {
                return ActionCreationResult.Invalid(
                    ActionCreationFailureCode.INVALID_NUMBER,
                    "${key}不能小于 0"
                )
            }
        }

        if (type == ActionType.CLICK_IMAGE || type == ActionType.WAIT_IMAGE) {
            val threshold = normalizedValues[ActionParameterKey.MATCH_THRESHOLD]?.toFloatOrNull()
            if (threshold == null || !threshold.isFinite() || threshold !in 0f..1f) {
                return ActionCreationResult.Invalid(ActionCreationFailureCode.INVALID_NUMBER, "相似度阈值必须在 0 到 1 之间")
            }
        }
        if (type == ActionType.WAIT_IMAGE) {
            val timeout = normalizedValues[ActionParameterKey.WAIT_TIMEOUT_MILLIS]?.toLongOrNull()
            if (timeout == null || timeout < 0L) {
                return ActionCreationResult.Invalid(ActionCreationFailureCode.INVALID_NUMBER, "等待超时时间必须是非负整数")
            }
        }

        if (type == ActionType.CLICK_OCR_TEXT) {
            val text = normalizedValues[ActionParameterKey.OCR_TARGET_TEXT].orEmpty()
            if (text.isBlank()) {
                return ActionCreationResult.Invalid(ActionCreationFailureCode.MISSING_FIELD, "目标文字不能为空")
            }
            val textKeys = listOf(
                ActionParameterKey.OCR_TEXT_LEFT,
                ActionParameterKey.OCR_TEXT_TOP,
                ActionParameterKey.OCR_TEXT_RIGHT,
                ActionParameterKey.OCR_TEXT_BOTTOM
            )
            val values = textKeys.map { normalizedValues[it]?.toIntOrNull() }
            if (values.any { it == null }) {
                return ActionCreationResult.Invalid(ActionCreationFailureCode.INVALID_NUMBER, "文字位置必须是有效坐标")
            }
            val (left, top, right, bottom) = values.map { it ?: 0 }
            if (left < 0 || top < 0 || right <= left || bottom <= top) {
                return ActionCreationResult.Invalid(ActionCreationFailureCode.INVALID_NUMBER, "文字位置无效")
            }
        }

        if (type == ActionType.OCR_TEXT) {
            val regionKeys = listOf(
                ActionParameterKey.MATCH_REGION_LEFT,
                ActionParameterKey.MATCH_REGION_TOP,
                ActionParameterKey.MATCH_REGION_RIGHT,
                ActionParameterKey.MATCH_REGION_BOTTOM
            )
            val regionValues = regionKeys.map { key -> normalizedValues[key]?.toIntOrNull() }
            if (regionValues.any { it == null }) {
                return ActionCreationResult.Invalid(ActionCreationFailureCode.INVALID_NUMBER, "OCR 框选区域必须是有效坐标")
            }
            val (left, top, right, bottom) = regionValues.map { it ?: return ActionCreationResult.Invalid(ActionCreationFailureCode.INVALID_NUMBER, "OCR 框选区域必须是有效坐标") }
            if (left < 0 || top < 0 || right <= left || bottom <= top) {
                return ActionCreationResult.Invalid(ActionCreationFailureCode.INVALID_NUMBER, "OCR 框选区域无效")
            }
        }

        if (type == ActionType.FIND_COLOR) {
            val hex = normalizedValues[ActionParameterKey.COLOR_HEX].orEmpty()
            if (!hex.matches(Regex("#[0-9A-Fa-f]{6}"))) {
                return ActionCreationResult.Invalid(
                    ActionCreationFailureCode.INVALID_NUMBER,
                    "目标颜色必须是 #RRGGBB 格式"
                )
            }
            val tolerance = normalizedValues[ActionParameterKey.COLOR_TOLERANCE]?.toIntOrNull()
            if (tolerance == null || tolerance !in 0..255) {
                return ActionCreationResult.Invalid(
                    ActionCreationFailureCode.INVALID_NUMBER,
                    "RGB 通道容差必须在 0 到 255 之间"
                )
            }
        }

        val parameters = normalizedValues.mapValues { (key, value) ->
            if (type == ActionType.WAIT && key == ActionParameterKey.DURATION_MILLIS) {
                val seconds = value.toLong()
                if (seconds > Long.MAX_VALUE / 1000L) {
                    return ActionCreationResult.Invalid(
                        ActionCreationFailureCode.INVALID_NUMBER,
                        "等待时间超出可支持范围"
                    )
                }
                (seconds * 1000L).toString()
            } else {
                value
            }
        }

        return ActionCreationResult.Success(
            definition.handler.createDefault().copy(
                displayName = definition.editor.displayName(normalizedValues),
                parameters = parameters,
                executionOptions = settings.executionOptions,
                beforeActions = settings.beforeActions,
                afterActions = settings.afterActions
            )
        )
    }

    fun createDefault(type: ActionType): ActionCreationResult {
        val definition = ActionDefinitionRegistry.definitionFor(type)
            ?: return ActionCreationResult.Invalid(
                ActionCreationFailureCode.ACTION_NOT_DEFINED,
                "动作未完整定义：${type.displayName}"
            )
        if (!definition.handler.isAvailable) {
            return ActionCreationResult.Invalid(
                ActionCreationFailureCode.ACTION_NOT_AVAILABLE,
                "动作暂不可用：${type.displayName}"
            )
        }
        return ActionCreationResult.Success(definition.handler.createDefault())
    }
}
