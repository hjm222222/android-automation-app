package com.example.myapplication.script.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.script.api.ActionApiResult
import com.example.myapplication.script.model.ScriptAction

/**
 * 工作区动作列表的协调者。
 *
 * 通过 ActionWorkspaceCoordinator 读取和修改动作，不直接接触工作区内部列表。
 * 页面窗口的创建、关闭和定位仍由 FloatingWorkspaceService 持有。
 */
class WorkspaceCoordinator(
    private val context: Context,
    private val actionWorkspaceCoordinator: ActionWorkspaceCoordinator,
    private val dp: (Int) -> Int,
    private val roundedBackground: (fillColor: Int, radius: Int, strokeColor: Int) -> android.graphics.drawable.GradientDrawable,
    private val showPage: (view: FrameLayout, width: Int, height: Int, focusable: Boolean, followWorkspace: Boolean) -> Unit,
    private val addCloseButton: (FrameLayout) -> Unit,
    private val onPageClosed: () -> Unit,
    private val onEditAction: (ScriptAction) -> Unit
) {
    fun showActionListPage() {
        val content = FrameLayout(context).apply {
            background = roundedBackground(Color.rgb(255, 248, 224), dp(16), Color.rgb(244, 226, 168))
            elevation = dp(8).toFloat()
            contentDescription = "动作列表"
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(48), dp(12), dp(12))
        }
        val actions = actionWorkspaceCoordinator.actions()
        if (actions.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "暂无动作"
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(153, 136, 104))
            }, LinearLayout.LayoutParams(-1, dp(48)))
        } else {
            actions.forEachIndexed { displayIndex, action ->
                list.addView(createActionRow(displayIndex, action, content), LinearLayout.LayoutParams(-1, dp(72)).apply {
                    bottomMargin = dp(6)
                })
            }
        }
        content.addView(ScrollView(context).apply { addView(list) }, FrameLayout.LayoutParams(-1, -1))
        addCloseButton(content)
        showPage(content, dp(260), dp(360), false, true)
    }

    private fun createActionRow(
        displayIndex: Int,
        action: ScriptAction,
        content: FrameLayout
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), 0, dp(4), 0)
        background = roundedBackground(Color.rgb(255, 243, 190), dp(10), Color.TRANSPARENT)
        addView(TextView(context).apply {
            text = "${displayIndex + 1}. ${action.displayName}"
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(72, 62, 47))
        }, LinearLayout.LayoutParams(-1, dp(32)))
        val commands = LinearLayout(context).apply {
            gravity = Gravity.END
        }
        commands.addView(createCommandButton("编辑", Color.rgb(132, 99, 32)) {
            onEditAction(action)
        }, LinearLayout.LayoutParams(dp(44), dp(32)))
        commands.addView(createCommandButton("上移") {
            update(actionWorkspaceCoordinator.move(action.id, displayIndex - 1), content)
        }, LinearLayout.LayoutParams(dp(44), dp(32)))
        commands.addView(createCommandButton("下移") {
            update(actionWorkspaceCoordinator.move(action.id, displayIndex + 1), content)
        }, LinearLayout.LayoutParams(dp(44), dp(32)))
        commands.addView(createCommandButton("删除", Color.rgb(156, 83, 64)) {
            update(actionWorkspaceCoordinator.remove(action.id), content)
        }, LinearLayout.LayoutParams(dp(44), dp(32)))
        addView(commands, LinearLayout.LayoutParams(-1, dp(32)))
    }

    private fun createCommandButton(
        label: String,
        color: Int = Color.rgb(106, 89, 64),
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = label
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(color)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun update(result: ActionApiResult, content: FrameLayout) {
        when (result) {
            is ActionApiResult.Success -> {
                onPageClosed()
                showActionListPage()
            }
            is ActionApiResult.Failure -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }
}
