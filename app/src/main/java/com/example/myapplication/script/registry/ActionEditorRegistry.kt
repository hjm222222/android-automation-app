package com.example.myapplication.script.registry

import com.example.myapplication.script.model.ActionEditorDefinition
import com.example.myapplication.script.model.ActionFieldDefinition
import com.example.myapplication.script.model.ActionInputType
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType

object ActionEditorRegistry {
    private val definitions = mapOf(
        ActionType.CLICK to coordinateActionDefinition("点击"),
        ActionType.LONG_CLICK to coordinateActionDefinition("长按"),
        ActionType.DOUBLE_CLICK to coordinateActionDefinition("双击"),
        ActionType.SWIPE to swipeActionDefinition(),
        ActionType.INPUT_TEXT to ActionEditorDefinition(
            fields = listOf(ActionFieldDefinition(ActionParameterKey.TEXT, "要输入的文字")),
            displayName = { values -> "输入文字：${values[ActionParameterKey.TEXT].orEmpty()}" }
        ),
        ActionType.CLICK_NODE to ActionEditorDefinition(
            fields = listOf(
                ActionFieldDefinition(ActionParameterKey.NODE_TEXT, "控件文字"),
                ActionFieldDefinition(ActionParameterKey.NODE_DESCRIPTION, "控件描述"),
                ActionFieldDefinition(ActionParameterKey.NODE_RESOURCE_ID, "控件资源 ID"),
                ActionFieldDefinition(ActionParameterKey.NODE_CLASS_NAME, "控件类型"),
                ActionFieldDefinition(ActionParameterKey.NODE_PACKAGE_NAME, "控件包名")
            ),
            displayName = { values ->
                "点击控件：${values[ActionParameterKey.NODE_TEXT].orEmpty().ifBlank { values[ActionParameterKey.NODE_RESOURCE_ID].orEmpty() }}"
            }
        ),
        ActionType.CLICK_IMAGE to imageActionDefinition("点击图像"),
        ActionType.WAIT_IMAGE to imageActionDefinition("等待图像"),
        ActionType.OCR_TEXT to ocrTextActionDefinition(),
        ActionType.FIND_COLOR to ActionEditorDefinition(
            fields = listOf(
                ActionFieldDefinition(ActionParameterKey.COLOR_HEX, "目标颜色 HEX（例如 #FF0000）"),
                ActionFieldDefinition(
                    ActionParameterKey.COLOR_TOLERANCE,
                    "RGB 通道容差（0-255）",
                    ActionInputType.NUMBER,
                    "0"
                ),
                ActionFieldDefinition(ActionParameterKey.MATCH_VARIABLE_NAME, "命中坐标变量名（可选）"),
                ActionFieldDefinition(ActionParameterKey.FIND_COLOR_CLICK, "命中后点击（true/false）", defaultValue = "false")
            ),
            displayName = { values ->
                "找色：${values[ActionParameterKey.COLOR_HEX].orEmpty()}，容差 ${values[ActionParameterKey.COLOR_TOLERANCE].orEmpty()}"
            }
        ),
        ActionType.PICK_COLOR to ActionEditorDefinition(
            fields = listOf(
                ActionFieldDefinition(ActionParameterKey.COLOR_HEX, "颜色 HEX"),
                ActionFieldDefinition(ActionParameterKey.COLOR_RED, "红色分量", ActionInputType.NUMBER),
                ActionFieldDefinition(ActionParameterKey.COLOR_GREEN, "绿色分量", ActionInputType.NUMBER),
                ActionFieldDefinition(ActionParameterKey.COLOR_BLUE, "蓝色分量", ActionInputType.NUMBER),
                ActionFieldDefinition(ActionParameterKey.PICK_X, "截图 X 坐标", ActionInputType.NUMBER),
                ActionFieldDefinition(ActionParameterKey.PICK_Y, "截图 Y 坐标", ActionInputType.NUMBER),
                ActionFieldDefinition(ActionParameterKey.COLOR_VARIABLE_NAME, "写入颜色变量名")
            ),
            displayName = { values -> "取色：${values[ActionParameterKey.COLOR_HEX].orEmpty()}" }
        ),
        ActionType.APP_CONTROL to ActionEditorDefinition(
            fields = listOf(
                ActionFieldDefinition(
                    ActionParameterKey.APP_CONTROL_OPERATION,
                    "应用操作",
                    defaultValue = "LAUNCH"
                ),
                ActionFieldDefinition(ActionParameterKey.PACKAGE_NAME, "应用包名")
            ),
            displayName = { values ->
                val operation = if (values[ActionParameterKey.APP_CONTROL_OPERATION] == "LAUNCH") "打开" else "关闭"
                "$operation 应用：${values[ActionParameterKey.PACKAGE_NAME].orEmpty()}"
            }
        ),
        ActionType.SYSTEM_NAVIGATION to ActionEditorDefinition(
            fields = listOf(
                ActionFieldDefinition(
                    ActionParameterKey.NAVIGATION_ACTION,
                    "系统导航动作",
                    defaultValue = "BACK"
                )
            ),
            displayName = { values ->
                "系统导航：${values[ActionParameterKey.NAVIGATION_ACTION].orEmpty()}"
            }
        ),
        ActionType.WAIT to ActionEditorDefinition(
            fields = listOf(
                ActionFieldDefinition(
                    key = ActionParameterKey.DURATION_MILLIS,
                    hint = "等待时间（秒）",
                    inputType = ActionInputType.NUMBER,
                    defaultValue = "1"
                )
            ),
            displayName = { values -> "等待 ${values[ActionParameterKey.DURATION_MILLIS]} 秒" }
        ),
        ActionType.CREATE_VARIABLE to variableEditorDefinition("创建变量"),
        ActionType.SET_VARIABLE to variableEditorDefinition("设置变量")
    )

    fun definitionFor(type: ActionType): ActionEditorDefinition? = definitions[type]

    private fun coordinateActionDefinition(label: String) = ActionEditorDefinition(
        fields = listOf(
            ActionFieldDefinition(ActionParameterKey.X, "$label X 坐标", ActionInputType.NUMBER),
            ActionFieldDefinition(ActionParameterKey.Y, "$label Y 坐标", ActionInputType.NUMBER),
            ActionFieldDefinition(
                ActionParameterKey.DURATION_MILLIS,
                "$label 按下时长（毫秒）",
                ActionInputType.NUMBER,
                if (label == "长按") "800" else "80"
            )
        ),
        displayName = { values ->
            "$label（${values[ActionParameterKey.X]}, ${values[ActionParameterKey.Y]}）"
        }
    )

    private fun swipeActionDefinition() = ActionEditorDefinition(
        fields = listOf(
            ActionFieldDefinition(ActionParameterKey.START_X, "起点 X 坐标", ActionInputType.NUMBER),
            ActionFieldDefinition(ActionParameterKey.START_Y, "起点 Y 坐标", ActionInputType.NUMBER),
            ActionFieldDefinition(ActionParameterKey.END_X, "终点 X 坐标", ActionInputType.NUMBER),
            ActionFieldDefinition(ActionParameterKey.END_Y, "终点 Y 坐标", ActionInputType.NUMBER),
            ActionFieldDefinition(
                ActionParameterKey.DURATION_MILLIS,
                "滑动持续时间（毫秒）",
                ActionInputType.NUMBER,
                "400"
            )
        ),
        displayName = { values ->
            "滑动（${values[ActionParameterKey.START_X]}, ${values[ActionParameterKey.START_Y]} → " +
                "${values[ActionParameterKey.END_X]}, ${values[ActionParameterKey.END_Y]}）"
        }
    )

    private fun imageActionDefinition(label: String): ActionEditorDefinition {
        val fields = buildList {
            add(ActionFieldDefinition(ActionParameterKey.TEMPLATE_ID, "模板 ID"))
            add(ActionFieldDefinition(ActionParameterKey.MATCH_THRESHOLD, "相似度阈值（0-1）", ActionInputType.TEXT, "0.85"))
            if (label == "等待图像") {
                add(ActionFieldDefinition(ActionParameterKey.WAIT_TIMEOUT_MILLIS, "超时时间（毫秒）", ActionInputType.NUMBER, "5000"))
            }
        }
        return ActionEditorDefinition(
            fields = fields,
            displayName = { values -> "$label：${values[ActionParameterKey.TEMPLATE_ID].orEmpty()}" }
        )
    }

    private fun ocrTextActionDefinition() = ActionEditorDefinition(
        fields = listOf(
            ActionFieldDefinition(ActionParameterKey.OCR_VARIABLE_NAME, "写入变量名"),
            ActionFieldDefinition(ActionParameterKey.OCR_TARGET_TEXT, "目标文字（可选）"),
            ActionFieldDefinition(ActionParameterKey.MATCH_REGION_LEFT, "框选区域左边界", ActionInputType.NUMBER),
            ActionFieldDefinition(ActionParameterKey.MATCH_REGION_TOP, "框选区域上边界", ActionInputType.NUMBER),
            ActionFieldDefinition(ActionParameterKey.MATCH_REGION_RIGHT, "框选区域右边界", ActionInputType.NUMBER),
            ActionFieldDefinition(ActionParameterKey.MATCH_REGION_BOTTOM, "框选区域下边界", ActionInputType.NUMBER)
        ),
        displayName = { values ->
            "OCR文字：写入 ${values[ActionParameterKey.OCR_VARIABLE_NAME].orEmpty()}"
        }
    )

    private fun variableEditorDefinition(label: String) = ActionEditorDefinition(
        fields = listOf(
            ActionFieldDefinition(ActionParameterKey.VARIABLE_NAME, "变量名"),
            ActionFieldDefinition(ActionParameterKey.VARIABLE_VALUE, "变量值")
        ),
        displayName = { values -> "$label：${values[ActionParameterKey.VARIABLE_NAME].orEmpty()}" }
    )
}
