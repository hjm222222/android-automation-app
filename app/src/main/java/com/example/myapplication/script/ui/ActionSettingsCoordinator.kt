package com.example.myapplication.script.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.example.myapplication.script.model.ActionExecutionOptions
import com.example.myapplication.script.model.ActionSettings
import com.example.myapplication.script.model.ActionSettingsInput
import com.example.myapplication.script.model.ActionSettingsMapper
import com.example.myapplication.script.model.ActionSettingsMappingResult
import com.example.myapplication.script.model.JudgementCondition
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.model.VariableComparisonOperator
import com.example.myapplication.script.runtime.ActionCreationResult
import com.example.myapplication.script.runtime.ActionFactory

/**
 * 动作设置页面的唯一协调者。
 *
 * 输入是当前动作设置和两个 UI 回调；输出是确认后的 ActionSettings。
 * 它不持有工作区、Runner 或 Service 生命周期状态。
 */
class ActionSettingsCoordinator(
    private val context: Context,
    private val showDialog: (AlertDialog, Int) -> Unit,
    private val showNestedActionPicker: (String, (ScriptAction) -> Unit) -> Unit,
    private val onError: (String) -> Unit,
    private val dp: (Int) -> Int
) {
    fun show(
        currentOptions: ActionExecutionOptions,
        currentBeforeActions: List<ScriptAction>,
        currentAfterActions: List<ScriptAction>,
        onSettingsSelected: (ActionSettings) -> Unit
    ) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        var selectedBeforeActions = currentBeforeActions
        var selectedAfterActions = currentAfterActions
        lateinit var beforeActionButton: TextView
        beforeActionButton = settingChoiceButton(
            currentBeforeActions.firstOrNull()?.let { "运行前：${it.displayName}" } ?: "选择运行前动作"
        ) {
            showNestedActionPicker("选择运行前动作") { action ->
                selectedBeforeActions = listOf(action)
                beforeActionButton.text = "运行前：${action.displayName}"
            }
        }
        content.addView(settingSectionTitle("动作运行前"))
        content.addView(beforeActionButton, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(12) })

        lateinit var afterActionButton: TextView
        afterActionButton = settingChoiceButton(
            currentAfterActions.firstOrNull()?.let { "运行后：${it.displayName}" } ?: "选择运行后动作"
        ) {
            showNestedActionPicker("选择运行后动作") { action ->
                selectedAfterActions = listOf(action)
                afterActionButton.text = "运行后：${action.displayName}"
            }
        }
        content.addView(settingSectionTitle("动作运行后"))
        content.addView(afterActionButton, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(12) })

        content.addView(settingSectionTitle("判断条件"))
        val conditionType = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("变量判断", "OCR判断", "图片判断", "区域取色判断"))
        }
        val variableName = createDialogInput("变量名", "")
        val variableExpectedValue = createDialogInput("比较值", "")
        val variableOperator = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("==", "<="))
        }
        val ocrScope = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("区域内判断", "全屏判断")) }
        val ocrText = createDialogInput("目标文字", "")
        val imageScope = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("区域内判断", "全屏判断")) }
        val imageId = createDialogInput("图片模板 ID", "")
        val imageRegion = createRectInputs("图片区域")
        val color = createDialogInput("颜色（#RRGGBB）", "")
        val colorRegion = createRectInputs("颜色区域")
        val tolerance = createDialogInput("颜色容差（0-255）", "0").apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val conditionDetails = listOf(
            createVariableJudgement(variableName, variableOperator, variableExpectedValue).view,
            createSimpleCondition(ocrScope, ocrText),
            createSimpleCondition(imageScope, imageId, imageRegion.row),
            createSimpleCondition(color, colorRegion.row, tolerance)
        )
        content.addView(conditionType, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        conditionDetails.forEachIndexed { index, view ->
            view.visibility = if (index == 0) View.VISIBLE else View.GONE
            content.addView(view, LinearLayout.LayoutParams(-1, -2))
        }
        conditionType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                conditionDetails.forEachIndexed { index, detail -> detail.visibility = if (index == position) View.VISIBLE else View.GONE }
            }
        }

        val scrollView = ScrollView(context).apply { addView(content) }
        val dialog = AlertDialog.Builder(context)
            .setTitle("动作设置")
            .setView(scrollView)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val input = ActionSettingsInput(
                    judgementType = com.example.myapplication.script.model.JudgementInputType.values()[conditionType.selectedItemPosition],
                    variableName = variableName.text.toString(),
                    variableOperator = if (variableOperator.selectedItemPosition == 0) VariableComparisonOperator.EQUALS else VariableComparisonOperator.LESS_THAN_OR_EQUALS,
                    variableExpectedValue = variableExpectedValue.text.toString(),
                    ocrScope = if (ocrScope.selectedItemPosition == 0) com.example.myapplication.script.model.TextJudgementScope.REGION else com.example.myapplication.script.model.TextJudgementScope.FULL_SCREEN,
                    ocrExpectedText = ocrText.text.toString(),
                    imageScope = if (imageScope.selectedItemPosition == 0) com.example.myapplication.script.model.ImageJudgementScope.REGION else com.example.myapplication.script.model.ImageJudgementScope.FULL_SCREEN,
                    imageId = imageId.text.toString(),
                    imageRegion = imageRegion.value(),
                    regionColor = color.text.toString(),
                    regionColorRegion = colorRegion.value(),
                    regionColorTolerance = tolerance.text.toString().toIntOrNull() ?: -1,
                    beforeActions = selectedBeforeActions,
                    afterActions = selectedAfterActions
                )
                when (val result = ActionSettingsMapper.map(input, currentOptions)) {
                    is ActionSettingsMappingResult.Success -> onSettingsSelected(result.settings)
                    is ActionSettingsMappingResult.Invalid -> onError(result.message)
                }
            }
            .create()
        showDialog(dialog, dp(340))
    }

    private fun createExpandableJudgement(title: String, detail: View, onExpand: (() -> Unit)? = null): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.rgb(255, 248, 224), dp(12), Color.rgb(244, 226, 168))
        }
        val header = TextView(context).apply {
            text = "$title  ⌄"
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(72, 62, 47))
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
        }
        container.addView(header, LinearLayout.LayoutParams(-1, dp(44)))
        detail.visibility = View.GONE
        container.addView(detail, LinearLayout.LayoutParams(-1, -2))
        header.setOnClickListener {
            val expanding = detail.visibility != View.VISIBLE
            detail.visibility = if (expanding) View.VISIBLE else View.GONE
            header.text = "$title  ${if (expanding) "⌃" else "⌄"}"
            if (expanding) onExpand?.invoke()
        }
        return container
    }

    private fun createVariableJudgement(variableName: EditText, operator: Spinner, expectedValue: EditText): JudgementContent {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        val loading = ProgressBar(context).apply { isIndeterminate = true }
        val form = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
            addView(variableName, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
            addView(operator, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
            addView(expectedValue, LinearLayout.LayoutParams(-1, dp(48)))
            visibility = View.GONE
        }
        content.addView(loading, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(8)
        })
        content.addView(form, LinearLayout.LayoutParams(-1, -2))
        return JudgementContent(content) {
            loading.visibility = View.VISIBLE
            form.visibility = View.GONE
            Handler(Looper.getMainLooper()).postDelayed({
                loading.visibility = View.GONE
                form.visibility = View.VISIBLE
            }, 1500L)
        }
    }

    private data class JudgementContent(val view: View, val onExpand: () -> Unit)

    private data class RectInputs(val left: EditText, val top: EditText, val right: EditText, val bottom: EditText, val row: LinearLayout) {
        fun value(): com.example.myapplication.script.model.Rect? {
            val values = listOf(left, top, right, bottom).map { it.text.toString().trim().toIntOrNull() ?: return null }
            return com.example.myapplication.script.model.Rect(values[0], values[1], values[2], values[3])
        }
    }

    private fun createRectInputs(label: String): RectInputs {
        val fields = listOf("左", "上", "右", "下").map { createDialogInput("$label$it", "") }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fields.forEach { row.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) }) }
        return RectInputs(fields[0], fields[1], fields[2], fields[3], row)
    }

    private fun createSimpleCondition(vararg views: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(8), dp(14), dp(14))
        views.forEach { addView(it, LinearLayout.LayoutParams(-1, if (it is Spinner) dp(44) else if (it is LinearLayout) -2 else dp(48)).apply { bottomMargin = dp(8) }) }
    }

    private fun createOcrJudgement(): View = createJudgementView(arrayOf("区域内判断", "全屏判断"), "目标文字")

    private fun createImageJudgement(): View = createJudgementView(arrayOf("区域内判断", "全屏判断", "区域取色判断"), "图片或颜色")

    private fun createJudgementView(options: Array<String>, hint: String): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        val scope = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
        }
        content.addView(scope, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        content.addView(createDialogInput(hint, ""), LinearLayout.LayoutParams(-1, dp(48)))
        return content
    }

    private fun settingSectionTitle(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(106, 89, 64))
        setPadding(dp(2), dp(6), dp(2), dp(4))
    }

    private fun settingChoiceButton(text: String, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        setTextColor(Color.rgb(72, 62, 47))
        background = roundedBackground(Color.rgb(255, 243, 190), dp(12), Color.rgb(244, 226, 168))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun createDialogInput(hint: String, value: String): EditText = EditText(context).apply {
        this.hint = hint
        setSingleLine()
        setText(value)
        setTextColor(Color.rgb(72, 62, 47))
        setHintTextColor(Color.rgb(153, 136, 104))
        setPadding(dp(12), 0, dp(12), 0)
        background = roundedBackground(Color.rgb(255, 248, 224), dp(12), Color.rgb(244, 226, 168))
    }

    private fun roundedBackground(fillColor: Int, radius: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), strokeColor)
        }
}
