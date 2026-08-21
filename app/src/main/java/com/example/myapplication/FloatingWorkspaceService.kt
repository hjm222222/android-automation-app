package com.example.myapplication

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import com.example.myapplication.script.model.ActionCondition
import com.example.myapplication.script.model.ActionExecutionOptions
import com.example.myapplication.script.model.ImageJudgementScope
import com.example.myapplication.script.model.JudgementCondition
import com.example.myapplication.script.model.TextJudgementScope
import com.example.myapplication.script.model.VariableComparisonOperator
import com.example.myapplication.script.model.ActionInputType
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.registry.ActionEditorRegistry
import com.example.myapplication.script.registry.ActionRegistry
import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.runtime.ScriptRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingWorkspaceService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var workspaceView: View
    private lateinit var workspaceLayoutParams: WindowManager.LayoutParams
    private var pageView: View? = null
    private var pageLayoutParams: WindowManager.LayoutParams? = null
    private var pageFollowsWorkspace = false
    private val scriptActions = mutableListOf<ScriptAction>()
    private val scriptScope = CoroutineScope(Dispatchers.Main.immediate)
    private var runningJob: Job? = null
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private var mediaProjection: MediaProjection? = null

    private companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        workspaceView = createWorkspaceView()
        workspaceLayoutParams = WindowManager.LayoutParams(
            dp(72),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(120)
        }
        windowManager.addView(workspaceView, workspaceLayoutParams)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        screenCaptureResultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0)
            ?.takeIf { it != 0 }
        screenCaptureData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        startForegroundServiceNotification()
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "floating_workspace"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "悬浮窗脚本服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }.setContentTitle("自动化小精灵")
            .setContentText("悬浮窗脚本服务正在运行")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

        val hasProjection =
            screenCaptureResultCode != null && screenCaptureData != null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (hasProjection) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, type)
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

        if (hasProjection) {
            acquireMediaProjection()
        }
    }

    private fun acquireMediaProjection() {
        val code = screenCaptureResultCode ?: return
        val data = screenCaptureData ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val manager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = try {
            manager.getMediaProjection(code, data)
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        mediaProjection?.stop()
        mediaProjection = null
        runningJob?.cancel()
        scriptScope.cancel()
        dismissPage()
        if (::workspaceView.isInitialized && workspaceView.isAttachedToWindow) {
            windowManager.removeViewImmediate(workspaceView)
        }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::workspaceView.isInitialized) return

        constrainWorkspacePosition()
        if (workspaceView.isAttachedToWindow) {
            windowManager.updateViewLayout(workspaceView, workspaceLayoutParams)
        }
        updateAttachedPagePosition()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createWorkspaceView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBackground(Color.WHITE, dp(20), Color.rgb(244, 226, 168))
            elevation = dp(6).toFloat()
        }

        val dragHandle = TextView(this).apply {
            minHeight = dp(48)
            contentDescription = "拖动悬浮窗"
        }
        dragHandle.setOnTouchListener(DragListener())
        container.addView(dragHandle, LinearLayout.LayoutParams(-1, dp(48)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        addActionButton(actions, "+", "添加动作", ::showAddActionPage)
        addActionButton(actions, "▶", "运行", ::runScript)
        addActionButton(actions, "☷", "动作列表", ::toggleActionList)
        addActionButton(actions, "⚙", "设置", ::toggleSettings)
        container.addView(actions, LinearLayout.LayoutParams(-1, -2))

        return container
    }

    private fun addActionButton(
        container: LinearLayout,
        icon: String,
        description: String,
        onClick: (() -> Unit)? = null
    ) {
        val button = TextView(this).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(106, 89, 64))
            background = roundedBackground(Color.rgb(255, 240, 190), dp(24), Color.TRANSPARENT)
            minWidth = dp(48)
            minHeight = dp(48)
            contentDescription = description
            isClickable = onClick != null
            isFocusable = onClick != null
            onClick?.let { setOnClickListener { it() } }
        }
        val params = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            topMargin = dp(3)
            bottomMargin = dp(3)
        }
        container.addView(button, params)
    }

    private fun runScript() {
        if (runningJob?.isActive == true) return
        if (scriptActions.isEmpty()) {
            showRunResult("请先添加动作")
            return
        }
        runningJob = scriptScope.launch {
            val result = ScriptRunner().run(scriptActions.toList())
            val message = when (result) {
                ActionExecutionResult.Success -> "脚本运行完成"
                ActionExecutionResult.NotImplemented -> "包含暂未实现的动作"
                is ActionExecutionResult.Failed -> "运行失败：${result.message}"
            }
            showRunResult(message)
        }
    }

    private fun showRunResult(message: String) {
        showOverlayDialog(
            AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .create()
        )
    }

    private fun showOverlayDialog(dialog: AlertDialog, width: Int = dp(280)) {
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(
                    roundedBackground(Color.rgb(255, 248, 224), dp(18), Color.rgb(244, 226, 168))
                )
                setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.rgb(132, 99, 32))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.rgb(132, 99, 32))
        }
        dialog.show()
    }

    private fun toggleSettings() {
        if (pageView != null) {
            dismissPage()
        } else {
            showSettingsPage()
        }
    }

    private fun toggleActionList() {
        if (pageView != null) {
            dismissPage()
        } else {
            showActionListPage()
        }
    }

    private fun showSettingsPage() {
        val content = FrameLayout(this).apply {
            background = roundedBackground(Color.WHITE, dp(20), Color.rgb(244, 226, 168))
            elevation = dp(8).toFloat()
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        content.addView(body, FrameLayout.LayoutParams(-1, -1))
        addCloseButton(content)

        val scriptName = EditText(this).apply {
            hint = "请输入脚本名称"
            textSize = 16f
            setSingleLine()
            setTextColor(Color.rgb(72, 62, 47))
            setHintTextColor(Color.rgb(153, 136, 104))
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBackground(Color.rgb(255, 248, 224), dp(12), Color.rgb(244, 226, 168))
            contentDescription = "脚本名称"
        }
        body.addView(scriptName, LinearLayout.LayoutParams(-1, dp(52)))

        val saveButton = pageButton("保存", dp(48))
        body.addView(
            saveButton,
            LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(12)
            }
        )

        val exitButton = pageButton("退出脚本", dp(48))
        exitButton.setOnClickListener { stopSelf() }
        body.addView(
            exitButton,
            LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(16)
            }
        )

        showPage(
            view = content,
            width = dp(260),
            height = dp(260),
            focusable = true,
            followWorkspace = true
        )
    }

    private fun showAddActionPage() {
        val metrics = resources.displayMetrics
        val panelWidth = (metrics.widthPixels * 0.792f).toInt()
        val panelHeight = (metrics.heightPixels * 0.726f).toInt()
        val content = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 38, 32, 22))
            contentDescription = "添加动作"
            setOnClickListener { dismissPage() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedBackground(Color.argb(220, 255, 248, 224), dp(22), Color.rgb(244, 226, 168))
            elevation = dp(10).toFloat()
            isClickable = true
            contentDescription = "添加动作面板"
        }
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        panel.addView(ScrollView(this).apply {
            addView(scrollContent)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        ActionRegistry.categories().map { category ->
            category.displayName to ActionRegistry.actionsIn(category)
        }.forEach { (title, types) ->
            scrollContent.addView(TextView(this).apply {
                text = title
                textSize = 12f
                setTextColor(Color.rgb(106, 89, 64))
                setPadding(dp(4), dp(6), dp(4), dp(2))
            }, LinearLayout.LayoutParams(-1, dp(28)))
            types.chunked(3).forEach { rowTypes ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowTypes.forEach { actionType ->
                    val button = TextView(this).apply {
                        text = actionType.displayName
                        textSize = 10f
                        gravity = Gravity.CENTER
                        setTextColor(Color.rgb(106, 89, 64))
                        background = roundedBackground(Color.rgb(255, 243, 190), dp(12), Color.TRANSPARENT)
                        contentDescription = actionType.displayName
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { addAction(actionType) }
                    }
                    row.addView(button, LinearLayout.LayoutParams(0, dp(28), 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    })
                }
                repeat(3 - rowTypes.size) { row.addView(View(this), LinearLayout.LayoutParams(0, dp(28), 1f)) }
                scrollContent.addView(row, LinearLayout.LayoutParams(-1, dp(34)))
            }
        }
        content.addView(panel, FrameLayout.LayoutParams(panelWidth, panelHeight, Gravity.CENTER))
        showPage(content, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, false)
    }

    private fun addAction(type: ActionType) {
        when (type) {
            ActionType.WAIT,
            ActionType.CREATE_VARIABLE,
            ActionType.SET_VARIABLE -> showActionEditor(type)
            ActionType.SYSTEM_NAVIGATION -> chooseActionVariant(type, listOf("返回", "主页", "多任务"))
            ActionType.APP_CONTROL -> chooseActionVariant(type, listOf("启动应用", "关闭应用"))
            else -> {
                val handler = ActionRegistry.handlerFor(type) ?: return
                scriptActions += handler.createDefault()
                dismissPage()
            }
        }
    }

    private fun showActionEditor(type: ActionType) {
        val definition = ActionEditorRegistry.definitionFor(type) ?: return
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        var executionOptions = ActionExecutionOptions()
        val settingsButton = TextView(this).apply {
            text = "设置"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(106, 89, 64))
            background = roundedBackground(Color.rgb(255, 240, 190), dp(14), Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            contentDescription = "动作设置"
            setOnClickListener {
                showActionSettings(executionOptions) { selectedOptions ->
                    executionOptions = selectedOptions
                    text = if (selectedOptions.condition is ActionCondition.Always) "设置" else "已设置条件"
                }
            }
        }
        body.addView(settingsButton, LinearLayout.LayoutParams(dp(82), dp(30)).apply {
            gravity = Gravity.END
            bottomMargin = dp(8)
        })
        val fields = mutableMapOf<String, EditText>()
        definition.fields.forEach { fieldDefinition ->
            val field = EditText(this).apply {
                hint = fieldDefinition.hint
                setSingleLine()
                inputType = when (fieldDefinition.inputType) {
                    ActionInputType.TEXT -> android.text.InputType.TYPE_CLASS_TEXT
                    ActionInputType.NUMBER -> android.text.InputType.TYPE_CLASS_NUMBER
                }
                setText(fieldDefinition.defaultValue)
                setTextColor(Color.rgb(72, 62, 47))
                setHintTextColor(Color.rgb(153, 136, 104))
                setPadding(dp(12), 0, dp(12), 0)
                background = roundedBackground(Color.rgb(255, 248, 224), dp(12), Color.rgb(244, 226, 168))
            }
            fields[fieldDefinition.key] = field
            body.addView(field, LinearLayout.LayoutParams(-1, dp(52)).apply {
                bottomMargin = dp(8)
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(type.displayName)
            .setView(body)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加") { _, _ ->
                val editorValues = fields.mapValues { (_, field) -> field.text.toString() }
                val parameters = editorValues.mapValues { (key, value) ->
                    if (key == ActionParameterKey.DURATION_MILLIS) {
                        ((value.toLongOrNull() ?: 0L) * 1000L).toString()
                    } else {
                        value
                    }
                }
                val handler = ActionRegistry.handlerFor(type) ?: return@setPositiveButton
                val defaultAction = handler.createDefault()
                scriptActions += defaultAction.copy(
                    displayName = definition.displayName(editorValues),
                    parameters = parameters,
                    executionOptions = executionOptions
                )
                dismissPage()
            }
            .create()
        showOverlayDialog(dialog)
    }

    private fun showActionSettings(
        currentOptions: ActionExecutionOptions,
        onOptionsSelected: (ActionExecutionOptions) -> Unit
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val defaultWait = createDialogInput("默认等待（ms）", "50").apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        content.addView(settingSectionTitle("默认等待"))
        content.addView(defaultWait, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(12) })

        lateinit var beforeActionButton: TextView
        beforeActionButton = settingChoiceButton("选择运行前动作") {
            showNestedActionPicker("选择运行前动作") { actionName ->
                beforeActionButton.text = "运行前：$actionName"
            }
        }
        content.addView(settingSectionTitle("动作运行前"))
        content.addView(beforeActionButton, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(12) })

        lateinit var afterActionButton: TextView
        afterActionButton = settingChoiceButton("选择运行后动作") {
            showNestedActionPicker("选择运行后动作") { actionName ->
                afterActionButton.text = "运行后：$actionName"
            }
        }
        content.addView(settingSectionTitle("动作运行后"))
        content.addView(afterActionButton, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(12) })

        content.addView(settingSectionTitle("判断"))
        val variableJudgement = createVariableJudgement()
        content.addView(createExpandableJudgement("变量判断", variableJudgement.view, variableJudgement.onExpand), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
        content.addView(createExpandableJudgement("OCR文字判断", createOcrJudgement()), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
        content.addView(createExpandableJudgement("识图判断", createImageJudgement()), LinearLayout.LayoutParams(-1, -2))

        val scrollView = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("动作设置")
            .setView(scrollView)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                onOptionsSelected(currentOptions)
            }
            .create()
        showOverlayDialog(dialog, dp(340))
    }

    private fun createExpandableJudgement(
        title: String,
        detail: View,
        onExpand: (() -> Unit)? = null
    ): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.rgb(255, 248, 224), dp(12), Color.rgb(244, 226, 168))
        }
        val header = TextView(this).apply {
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

    private fun createVariableJudgement(): JudgementContent {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        val loading = ProgressBar(this).apply {
            isIndeterminate = true
        }
        val form = createVariableJudgementView().apply {
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

    private data class JudgementContent(
        val view: View,
        val onExpand: () -> Unit
    )

    private fun createVariableJudgementView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        content.addView(createDialogInput("变量名", "a"), LinearLayout.LayoutParams(-1, dp(48)).apply {
            bottomMargin = dp(8)
        })
        val operator = Spinner(this).apply {
            adapter = ArrayAdapter(this@FloatingWorkspaceService, android.R.layout.simple_spinner_dropdown_item, arrayOf("==", "<="))
        }
        content.addView(operator, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        content.addView(createDialogInput("比较值", "0"), LinearLayout.LayoutParams(-1, dp(48)))
        return content
    }

    private fun createOcrJudgement(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        val scope = Spinner(this).apply {
            adapter = ArrayAdapter(this@FloatingWorkspaceService, android.R.layout.simple_spinner_dropdown_item, arrayOf("区域内判断", "全屏判断"))
        }
        content.addView(scope, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        content.addView(createDialogInput("目标文字", ""), LinearLayout.LayoutParams(-1, dp(48)))
        return content
    }

    private fun createImageJudgement(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        val scope = Spinner(this).apply {
            adapter = ArrayAdapter(this@FloatingWorkspaceService, android.R.layout.simple_spinner_dropdown_item, arrayOf("区域内判断", "全屏判断", "区域取色判断"))
        }
        content.addView(scope, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        content.addView(createDialogInput("图片或颜色", ""), LinearLayout.LayoutParams(-1, dp(48)))
        return content
    }

    private fun settingSectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.rgb(106, 89, 64))
            setPadding(dp(2), dp(6), dp(2), dp(4))
        }
    }

    private fun settingChoiceButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
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
    }

    private fun showNestedActionPicker(title: String, onActionSelected: (String) -> Unit) {
        val actions = ActionRegistry.categories()
            .flatMap { category -> ActionRegistry.actionsIn(category) }
            .map { it.displayName }
            .toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actions) { pickerDialog, index ->
                onActionSelected(actions[index])
                pickerDialog.dismiss()
            }
            .create()
        showOverlayDialog(dialog, dp(300))
    }

    private fun createDialogInput(hint: String, value: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setSingleLine()
            setText(value)
            setTextColor(Color.rgb(72, 62, 47))
            setHintTextColor(Color.rgb(153, 136, 104))
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBackground(Color.rgb(255, 248, 224), dp(12), Color.rgb(244, 226, 168))
        }
    }

    private fun chooseActionVariant(type: ActionType, variants: List<String>) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(type.displayName)
            .setItems(variants.toTypedArray()) { _, index ->
                scriptActions += ScriptAction(type, "${type.displayName}：${variants[index]}")
                dismissPage()
            }
            .create()
        showOverlayDialog(dialog)
    }

    private fun showActionListPage() {
        val content = FrameLayout(this).apply {
            background = roundedBackground(Color.rgb(255, 248, 224), dp(16), Color.rgb(244, 226, 168))
            elevation = dp(8).toFloat()
            contentDescription = "动作列表"
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(48), dp(12), dp(12))
        }
        if (scriptActions.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "暂无动作"
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(153, 136, 104))
            }, LinearLayout.LayoutParams(-1, dp(48)))
        } else {
            scriptActions.forEachIndexed { index, action ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(4), 0)
                    background = roundedBackground(Color.rgb(255, 243, 190), dp(10), Color.TRANSPARENT)
                }
                row.addView(TextView(this).apply {
                    text = "${index + 1}. ${action.displayName}"
                    textSize = 13f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(Color.rgb(72, 62, 47))
                }, LinearLayout.LayoutParams(0, dp(40), 1f))
                row.addView(TextView(this).apply {
                    text = "删除"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(156, 83, 64))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        scriptActions.removeAt(index)
                        showActionListPage()
                    }
                }, LinearLayout.LayoutParams(dp(44), dp(40)))
                list.addView(row, LinearLayout.LayoutParams(-1, dp(44)).apply {
                    bottomMargin = dp(6)
                })
            }
        }
        content.addView(ScrollView(this).apply {
            addView(list)
        }, FrameLayout.LayoutParams(-1, -1))
        addCloseButton(content)
        showPage(
            view = content,
            width = dp(260),
            height = dp(360),
            focusable = false,
            followWorkspace = true
        )
    }

    private fun addCloseButton(container: FrameLayout) {
        val closeButton = TextView(this).apply {
            text = "X"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(106, 89, 64))
            background = roundedBackground(Color.rgb(255, 243, 190), dp(16), Color.TRANSPARENT)
            contentDescription = "关闭"
            isClickable = true
            isFocusable = true
            setOnClickListener { dismissPage() }
        }
        container.addView(
            closeButton,
            FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8)
                rightMargin = dp(8)
            }
        )
    }

    private fun pageButton(text: String, height: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(106, 89, 64))
        background = roundedBackground(Color.rgb(255, 224, 138), dp(height / 2), Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
    }

    private fun showPage(
        view: View,
        width: Int,
        height: Int,
        focusable: Boolean,
        followWorkspace: Boolean = false
    ) {
        dismissPage()
        pageView = view
        pageFollowsWorkspace = followWorkspace
        pageLayoutParams = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            if (focusable) WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (followWorkspace) Gravity.TOP or Gravity.START else Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        updateAttachedPagePosition()
        windowManager.addView(view, pageLayoutParams)
        view.post { updateAttachedPagePosition() }
        if (focusable) {
            view.requestFocus()
        }
    }

    private fun dismissPage() {
        pageView?.let { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        pageView = null
        pageLayoutParams = null
        pageFollowsWorkspace = false
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            workspaceView.windowToken,
            0
        )
    }

    private inner class DragListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = workspaceLayoutParams.x
                    initialY = workspaceLayoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    workspaceLayoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    workspaceLayoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    constrainWorkspacePosition()
                    if (workspaceView.isAttachedToWindow) {
                        windowManager.updateViewLayout(workspaceView, workspaceLayoutParams)
                    }
                    updateAttachedPagePosition()
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    snapWorkspaceToEdge()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    snapWorkspaceToEdge()
                    return true
                }
            }
            return false
        }
    }

    private fun constrainWorkspacePosition() {
        val metrics = resources.displayMetrics
        val width = workspaceView.width.takeIf { it > 0 } ?: dp(72)
        val height = workspaceView.height.takeIf { it > 0 } ?: dp(300)
        val gap = dp(8)
        val maxX = metrics.widthPixels - width - gap
        val maxY = metrics.heightPixels - height - gap
        workspaceLayoutParams.x = workspaceLayoutParams.x.coerceIn(gap, maxX.coerceAtLeast(gap))
        workspaceLayoutParams.y = workspaceLayoutParams.y.coerceIn(gap, maxY.coerceAtLeast(gap))
    }

    private fun snapWorkspaceToEdge() {
        val metrics = resources.displayMetrics
        val width = workspaceView.width.takeIf { it > 0 } ?: dp(72)
        val gap = dp(8)
        val rightX = (metrics.widthPixels - width - gap).coerceAtLeast(gap)
        workspaceLayoutParams.x = if (workspaceLayoutParams.x + width / 2 < metrics.widthPixels / 2) {
            gap
        } else {
            rightX
        }
        constrainWorkspacePosition()
        if (workspaceView.isAttachedToWindow) {
            windowManager.updateViewLayout(workspaceView, workspaceLayoutParams)
        }
        updateAttachedPagePosition()
    }

    private fun updateAttachedPagePosition() {
        val params = pageLayoutParams ?: return
        val view = pageView ?: return
        val metrics = resources.displayMetrics

        if (pageFollowsWorkspace) {
            val workspaceWidth = workspaceView.width.takeIf { it > 0 } ?: dp(72)
            val pageWidth = view.width.takeIf { it > 0 } ?: params.width
            val pageHeight = view.height.takeIf { it > 0 } ?: params.height
            val gap = dp(3)
            val rightAlignedX = workspaceLayoutParams.x + workspaceWidth + gap
            val leftAlignedX = workspaceLayoutParams.x - pageWidth - gap
            val maxPageX = (metrics.widthPixels - pageWidth).coerceAtLeast(0)

            params.x = if (rightAlignedX + pageWidth <= metrics.widthPixels) {
                rightAlignedX
            } else {
                leftAlignedX.coerceIn(0, maxPageX)
            }
            params.y = workspaceLayoutParams.y.coerceIn(
                0,
                (metrics.heightPixels - pageHeight).coerceAtLeast(0)
            )
        }

        if (view.isAttachedToWindow) {
            windowManager.updateViewLayout(view, params)
        }
    }

    private fun roundedBackground(color: Int, radius: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != Color.TRANSPARENT) {
                setStroke(dp(1), strokeColor)
            }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
