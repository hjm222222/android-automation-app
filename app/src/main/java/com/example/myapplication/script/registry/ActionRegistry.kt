package com.example.myapplication.script.registry

import com.example.myapplication.script.action.AppControlActionHandler
import com.example.myapplication.script.action.ClickActionHandler
import com.example.myapplication.script.action.ClickImageActionHandler
import com.example.myapplication.script.action.ClickNodeActionHandler
import com.example.myapplication.script.action.ClickOcrTextActionHandler
import com.example.myapplication.script.action.CreateVariableActionHandler
import com.example.myapplication.script.action.DoubleClickActionHandler
import com.example.myapplication.script.action.FindColorActionHandler
import com.example.myapplication.script.action.InputTextActionHandler
import com.example.myapplication.script.action.LongClickActionHandler
import com.example.myapplication.script.action.OcrTextActionHandler
import com.example.myapplication.script.action.PickColorActionHandler
import com.example.myapplication.script.action.ScriptActionHandler
import com.example.myapplication.script.action.SetVariableActionHandler
import com.example.myapplication.script.action.SwipeActionHandler
import com.example.myapplication.script.action.SystemNavigationActionHandler
import com.example.myapplication.script.action.WaitActionHandler
import com.example.myapplication.script.action.WaitImageActionHandler
import com.example.myapplication.script.model.ActionCategory
import com.example.myapplication.script.model.ActionType

/**
 * 动作类型到执行器的集中注册表。
 *
 * 新增动作时，通常只需要完成三步：
 * 1. 在 ActionType 增加类型；
 * 2. 创建对应的 ScriptActionHandler；
 * 3. 在这里注册 Handler。
 *
 * 页面不直接 new Handler，执行器也不反向依赖页面，这就是动作功能可扩展的边界。
 */
object ActionRegistry {
    private val handlers = mapOf<ActionType, ScriptActionHandler>(
        ActionType.CLICK to ClickActionHandler(),
        ActionType.LONG_CLICK to LongClickActionHandler(),
        ActionType.DOUBLE_CLICK to DoubleClickActionHandler(),
        ActionType.SWIPE to SwipeActionHandler(),
        ActionType.INPUT_TEXT to InputTextActionHandler(),
        ActionType.WAIT to WaitActionHandler(),
        ActionType.CLICK_NODE to ClickNodeActionHandler(),
        ActionType.CLICK_IMAGE to ClickImageActionHandler(),
        ActionType.WAIT_IMAGE to WaitImageActionHandler(),
        ActionType.OCR_TEXT to OcrTextActionHandler(),
        ActionType.CLICK_OCR_TEXT to ClickOcrTextActionHandler(),
        ActionType.FIND_COLOR to FindColorActionHandler(),
        ActionType.PICK_COLOR to PickColorActionHandler(),
        ActionType.CREATE_VARIABLE to CreateVariableActionHandler(),
        ActionType.SET_VARIABLE to SetVariableActionHandler(),
        ActionType.SYSTEM_NAVIGATION to SystemNavigationActionHandler(),
        ActionType.APP_CONTROL to AppControlActionHandler()
    )

    /** 只显示至少包含一个可用动作的分类，避免动作选择器出现空分组。 */
    fun categories(): List<ActionCategory> = ActionCategory.entries.filter { category ->
        actionsIn(category).isNotEmpty()
    }

    /** 只向 UI 暴露已经具备可执行能力的动作，避免客户创建无法运行的脚本。 */
    fun actionsIn(category: ActionCategory): List<ActionType> =
        ActionType.entries.filter { it.category == category && handlers[it]?.isAvailable == true }

    fun handlerFor(type: ActionType): ScriptActionHandler? = handlers[type]
}
