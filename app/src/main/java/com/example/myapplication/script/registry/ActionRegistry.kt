package com.example.myapplication.script.registry

import com.example.myapplication.script.action.AppControlActionHandler
import com.example.myapplication.script.action.ClickActionHandler
import com.example.myapplication.script.action.ClickImageActionHandler
import com.example.myapplication.script.action.ClickNodeActionHandler
import com.example.myapplication.script.action.CreateVariableActionHandler
import com.example.myapplication.script.action.DoubleClickActionHandler
import com.example.myapplication.script.action.FindColorActionHandler
import com.example.myapplication.script.action.FindNodeActionHandler
import com.example.myapplication.script.action.InputTextActionHandler
import com.example.myapplication.script.action.LongClickActionHandler
import com.example.myapplication.script.action.OcrTextActionHandler
import com.example.myapplication.script.action.PickColorActionHandler
import com.example.myapplication.script.action.ReadNodeTextActionHandler
import com.example.myapplication.script.action.ScriptActionHandler
import com.example.myapplication.script.action.SetVariableActionHandler
import com.example.myapplication.script.action.SwipeActionHandler
import com.example.myapplication.script.action.SystemNavigationActionHandler
import com.example.myapplication.script.action.WaitActionHandler
import com.example.myapplication.script.action.WaitImageActionHandler
import com.example.myapplication.script.model.ActionCategory
import com.example.myapplication.script.model.ActionType

object ActionRegistry {
    private val handlers = mapOf<ActionType, ScriptActionHandler>(
        ActionType.CLICK to ClickActionHandler(),
        ActionType.LONG_CLICK to LongClickActionHandler(),
        ActionType.DOUBLE_CLICK to DoubleClickActionHandler(),
        ActionType.SWIPE to SwipeActionHandler(),
        ActionType.INPUT_TEXT to InputTextActionHandler(),
        ActionType.WAIT to WaitActionHandler(),
        ActionType.CLICK_NODE to ClickNodeActionHandler(),
        ActionType.FIND_NODE to FindNodeActionHandler(),
        ActionType.READ_NODE_TEXT to ReadNodeTextActionHandler(),
        ActionType.CLICK_IMAGE to ClickImageActionHandler(),
        ActionType.WAIT_IMAGE to WaitImageActionHandler(),
        ActionType.OCR_TEXT to OcrTextActionHandler(),
        ActionType.FIND_COLOR to FindColorActionHandler(),
        ActionType.PICK_COLOR to PickColorActionHandler(),
        ActionType.CREATE_VARIABLE to CreateVariableActionHandler(),
        ActionType.SET_VARIABLE to SetVariableActionHandler(),
        ActionType.SYSTEM_NAVIGATION to SystemNavigationActionHandler(),
        ActionType.APP_CONTROL to AppControlActionHandler()
    )

    fun categories(): List<ActionCategory> = ActionCategory.entries

    fun actionsIn(category: ActionCategory): List<ActionType> =
        ActionType.entries.filter { it.category == category }

    fun handlerFor(type: ActionType): ScriptActionHandler? = handlers[type]
}
