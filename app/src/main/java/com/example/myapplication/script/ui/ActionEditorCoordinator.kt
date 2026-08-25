package com.example.myapplication.script.ui

import com.example.myapplication.script.model.ActionType

/**
 * 动作编辑入口协调者。
 *
 * 只负责把动作类型路由到对应编辑流程，不持有窗口、页面或工作区状态。
 * 具体编辑器通过回调由 FloatingWorkspaceService 创建，以保留平台生命周期所有权。
 */
class ActionEditorCoordinator(
    private val showSwipeEditor: () -> Unit,
    private val showCoordinateEditor: (ActionType) -> Unit,
    private val showFormEditor: (ActionType) -> Unit,
    private val showVariantEditor: (ActionType, List<String>) -> Unit,
    private val showAppControlEditor: () -> Unit,
    private val showNodePicker: () -> Unit,
    private val showColorPicker: () -> Unit,
    private val showFindColorEditor: () -> Unit,
    private val showOcrTextEditor: () -> Unit,
    private val showImageTemplateEditor: (ActionType) -> Unit,
    private val showDefaultAction: (ActionType) -> Unit
) {
    fun open(type: ActionType) {
        when (type) {
            ActionType.SWIPE -> showSwipeEditor()
            ActionType.CLICK,
            ActionType.LONG_CLICK,
            ActionType.DOUBLE_CLICK -> showCoordinateEditor(type)

            ActionType.CLICK_NODE -> showNodePicker()
            ActionType.PICK_COLOR -> showColorPicker()
            ActionType.FIND_COLOR -> showFindColorEditor()
            ActionType.OCR_TEXT -> showOcrTextEditor()
            ActionType.CLICK_IMAGE,
            ActionType.WAIT_IMAGE -> showImageTemplateEditor(type)
            ActionType.INPUT_TEXT,
            ActionType.WAIT,
            ActionType.CREATE_VARIABLE,
            ActionType.SET_VARIABLE -> showFormEditor(type)
            ActionType.SYSTEM_NAVIGATION -> showVariantEditor(type, listOf("返回", "主页", "多任务"))
            ActionType.APP_CONTROL -> showAppControlEditor()
            else -> showDefaultAction(type)
        }
    }
}
