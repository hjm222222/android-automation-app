package com.example.myapplication.script.model

/**
 * 动作设置页面提交的纯数据，不依赖 Android View。
 * UI、AI 或导入器都可以使用同一套输入模型。
 */
enum class JudgementInputType {
    VARIABLE,
    OCR,
    IMAGE,
    REGION_COLOR
}

data class ActionSettingsInput(
    val judgementType: JudgementInputType = JudgementInputType.VARIABLE,
    val variableName: String = "",
    val variableOperator: VariableComparisonOperator = VariableComparisonOperator.EQUALS,
    val variableExpectedValue: String = "",
    val ocrScope: TextJudgementScope = TextJudgementScope.REGION,
    val ocrExpectedText: String = "",
    val imageScope: ImageJudgementScope = ImageJudgementScope.REGION,
    val imageId: String = "",
    val imageRegion: Rect? = null,
    val regionColor: String = "",
    val regionColorRegion: Rect? = null,
    val regionColorTolerance: Int = 0,
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
private fun Rect?.isValid(): Boolean = this != null && width > 0 && height > 0

object ActionSettingsMapper {
    fun map(
        input: ActionSettingsInput,
        currentOptions: ActionExecutionOptions = ActionExecutionOptions()
    ): ActionSettingsMappingResult {
        val condition = when (input.judgementType) {
            JudgementInputType.VARIABLE -> {
                val variableName = input.variableName.trim()
                val expectedValue = input.variableExpectedValue.trim()
                if (variableName.isEmpty() && expectedValue.isEmpty()) ActionCondition.Always
                else {
                    if (variableName.isEmpty()) return ActionSettingsMappingResult.Invalid("变量名不能为空")
                    if (expectedValue.isEmpty()) return ActionSettingsMappingResult.Invalid("比较值不能为空")
                    ActionCondition.Judgement(JudgementCondition.Variable(variableName, input.variableOperator, expectedValue))
                }
            }
            JudgementInputType.OCR -> {
                val text = input.ocrExpectedText.trim()
                if (text.isEmpty()) return ActionSettingsMappingResult.Invalid("目标文字不能为空")
                ActionCondition.Judgement(JudgementCondition.OcrText(input.ocrScope, text))
            }
            JudgementInputType.IMAGE -> {
                val id = input.imageId.trim()
                if (id.isEmpty()) return ActionSettingsMappingResult.Invalid("图片模板不能为空")
                if (input.imageScope == ImageJudgementScope.REGION && !input.imageRegion.isValid()) {
                    return ActionSettingsMappingResult.Invalid("图片区域无效")
                }
                ActionCondition.Judgement(JudgementCondition.Image(input.imageScope, id, input.imageRegion))
            }
            JudgementInputType.REGION_COLOR -> {
                val color = input.regionColor.trim()
                if (!Regex("#[0-9A-Fa-f]{6}").matches(color)) return ActionSettingsMappingResult.Invalid("颜色格式无效")
                if (!input.regionColorRegion.isValid()) return ActionSettingsMappingResult.Invalid("颜色区域无效")
                if (input.regionColorTolerance !in 0..255) return ActionSettingsMappingResult.Invalid("颜色容差无效")
                ActionCondition.Judgement(JudgementCondition.RegionColor(color, input.regionColorRegion!!, input.regionColorTolerance))
            }
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
