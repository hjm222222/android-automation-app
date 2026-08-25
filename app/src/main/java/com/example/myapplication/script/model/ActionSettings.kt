package com.example.myapplication.script.model

/**
 * 动作设置页面提交的纯数据，不依赖 Android View。
 * UI、AI 或导入器都可以使用同一套输入模型。
 */
data class ActionSettingsInput(
    val variableName: String = "",
    val variableOperator: VariableComparisonOperator = VariableComparisonOperator.EQUALS,
    val variableExpectedValue: String = "",
    val beforeActions: List<ScriptAction> = emptyList(),
    val afterActions: List<ScriptAction> = emptyList()
)

data class ActionSettings(
    val executionOptions: ActionExecutionOptions,
    val beforeActions: List<ScriptAction>,
    val afterActions: List<ScriptAction>
)

sealed interface ActionSettingsMappingResult {
    data class Success(val settings: ActionSettings) : ActionSettingsMappingResult
    data class Invalid(val message: String) : ActionSettingsMappingResult
}

/**
 * 把外部输入转换为运行时模型，集中处理设置字段的规则和校验。
 */
object ActionSettingsMapper {
    fun map(
        input: ActionSettingsInput,
        currentOptions: ActionExecutionOptions = ActionExecutionOptions()
    ): ActionSettingsMappingResult {
        val variableName = input.variableName.trim()
        val expectedValue = input.variableExpectedValue.trim()
        val condition = if (variableName.isEmpty() && expectedValue.isEmpty()) {
            ActionCondition.Always
        } else {
            if (variableName.isEmpty()) {
                return ActionSettingsMappingResult.Invalid("变量名不能为空")
            }
            if (expectedValue.isEmpty()) {
                return ActionSettingsMappingResult.Invalid("比较值不能为空")
            }
            ActionCondition.Judgement(
                JudgementCondition.Variable(
                    variableName = variableName,
                    operator = input.variableOperator,
                    expectedValue = expectedValue
                )
            )
        }

        return ActionSettingsMappingResult.Success(
            ActionSettings(
                executionOptions = currentOptions.copy(condition = condition),
                beforeActions = input.beforeActions,
                afterActions = input.afterActions
            )
        )
    }
}
