package com.example.myapplication

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.myapplication.script.model.ActionCondition
import com.example.myapplication.script.model.ActionExecutionOptions
import com.example.myapplication.script.model.ImageJudgementScope
import com.example.myapplication.script.model.JudgementCondition
import com.example.myapplication.script.model.TextJudgementScope
import com.example.myapplication.script.model.VariableComparisonOperator
import com.example.myapplication.script.model.ActionInputType
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionSettings
import com.example.myapplication.script.model.ActionSettingsInput
import com.example.myapplication.script.model.ActionSettingsMapper
import com.example.myapplication.script.model.ActionSettingsMappingResult
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.registry.ActionDefinitionRegistry
import com.example.myapplication.script.registry.ActionEditorRegistry
import com.example.myapplication.script.registry.ActionRegistry
import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.api.ScriptActionApi
import com.example.myapplication.script.api.ScriptActionApiImpl
import com.example.myapplication.script.runtime.ActionCreationResult
import com.example.myapplication.script.runtime.ActionFactory
import com.example.myapplication.script.runtime.ScriptRunner
import com.example.myapplication.script.runtime.ScriptWorkspaceController
import com.example.myapplication.script.runtime.ScriptWorkspaceCoordinator
import com.example.myapplication.script.platform.CoordinatePickerOverlay
import com.example.myapplication.script.platform.SwipeCoordinatePickerOverlay
import com.example.myapplication.script.platform.ColorPickerOverlay
import com.example.myapplication.script.platform.ScreenCaptureSession
import com.example.myapplication.script.platform.ScreenCaptureVisionController
import com.example.myapplication.script.repository.ScriptRepository
import com.example.myapplication.script.platform.ImageTemplateRepository
import com.example.myapplication.script.platform.AccessibilityController
import com.example.myapplication.script.platform.AndroidApplicationController
import com.example.myapplication.script.platform.AccessibilityNodePickerOverlay
import com.example.myapplication.script.ui.ActionSettingsCoordinator
import com.example.myapplication.script.ui.ActionEditorCoordinator
import com.example.myapplication.script.ui.ActionWorkspaceCoordinator
import com.example.myapplication.script.ui.WorkspaceCoordinator
import com.example.myapplication.script.ui.VisionPickerCoordinator
import com.example.myapplication.script.ui.OverlayPageAnchor
import com.example.myapplication.script.ui.OverlayPageCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingWorkspaceService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var workspaceView: View
    private lateinit var workspaceLayoutParams: WindowManager.LayoutParams
    private val applicationController by lazy { AndroidApplicationController(this) }
    // 悬浮窗只负责交互和页面展示，脚本数据由独立控制器统一管理。
    // 未来的 AI、录制、导入功能也应该通过这个入口提供动作。
    // Service 只在组装依赖时读取平台能力，脚本运行层不反向依赖 Service。
    private val scriptWorkspace: ScriptWorkspaceController = ScriptWorkspaceController(
        scriptRunner = ScriptRunner(
            accessibilityControllerProvider = { AutomationAccessibilityService.controller },
            applicationControllerProvider = { applicationController },
            visionControllerProvider = ::visionControllerOrNull,
            handlerResolver = ActionRegistry::handlerFor
        )
    )
    private val scriptWorkspaceCoordinator: ScriptWorkspaceCoordinator by lazy {
        ScriptWorkspaceCoordinator(
            repository = ScriptRepository(applicationContext),
            workspace = scriptWorkspace
        )
    }
    private val scriptActionApi: ScriptActionApi by lazy { ScriptActionApiImpl(scriptWorkspace) }
    private val actionWorkspaceCoordinator by lazy { ActionWorkspaceCoordinator(scriptActionApi) }
    private val actionEditorCoordinator by lazy {
        ActionEditorCoordinator(
            showSwipeEditor = ::showSwipeActionEditor,
            showCoordinateEditor = ::showCoordinateActionEditor,
            showFormEditor = ::showActionEditor,
            showVariantEditor = ::chooseActionVariant,
            showAppControlEditor = ::showAppControlEditor,
            showNodePicker = ::showNodePicker,
            showColorPicker = ::showColorPicker,
            showFindColorEditor = ::showFindColorEditor,
            showOcrTextEditor = ::showOcrTextEditor,
            showImageTemplateEditor = ::showImageTemplateEditor,
            showDefaultAction = ::addDefaultAction
        )
    }
    private val workspaceCoordinator by lazy {
        WorkspaceCoordinator(
            context = this,
            actionWorkspaceCoordinator = actionWorkspaceCoordinator,
            dp = ::dp,
            roundedBackground = ::roundedBackground,
            showPage = { view, width, height, focusable, followWorkspace ->
                showPage(view, width, height, focusable, followWorkspace)
            },
            addCloseButton = ::addCloseButton,
            onPageClosed = ::dismissPage,
            onEditAction = ::showExistingActionEditor
        )
    }
    private val actionSettingsCoordinator by lazy {
        ActionSettingsCoordinator(
            context = this,
            showDialog = { dialog, width -> showOverlayDialog(dialog, width) },
            showNestedActionPicker = { title, onActionSelected ->
                showNestedActionPicker(title, onActionSelected)
            },
            onError = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() },
            dp = ::dp
        )
    }
    private val serviceJob = SupervisorJob()
    private val scriptScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var runningJob: Job? = null
    @Volatile
    private var isDestroyed = false
    private var coordinatePicker: CoordinatePickerOverlay? = null
    private var swipeCoordinatePicker: SwipeCoordinatePickerOverlay? = null
    private var nodePicker: AccessibilityNodePickerOverlay? = null
    private var colorPicker: ColorPickerOverlay? = null
    private val shownDialogs = mutableSetOf<AlertDialog>()
    private val visionPickerCoordinator by lazy { VisionPickerCoordinator(applicationContext, windowManager) }
    private val pageCoordinator by lazy {
        OverlayPageCoordinator(
            context = this,
            windowManager = windowManager,
            isDestroyed = { isDestroyed },
            anchorProvider = {
                val metrics = resources.displayMetrics
                OverlayPageAnchor(
                    x = workspaceLayoutParams.x,
                    y = workspaceLayoutParams.y,
                    width = workspaceView.width.takeIf { it > 0 } ?: dp(72),
                    screenWidth = metrics.widthPixels,
                    screenHeight = metrics.heightPixels
                )
            },
            dp = ::dp
        )
    }
    private var screenCaptureResultCode: Int? = null
    private var screenCaptureData: Intent? = null
    private var screenCaptureSession: ScreenCaptureSession? = null

    companion object {
        const val EXTRA_SCREEN_CAPTURE_RESULT_CODE = "screenCaptureResultCode"
        const val EXTRA_SCREEN_CAPTURE_DATA = "screenCaptureData"
        const val EXTRA_SCRIPT_ID = "scriptId"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        isDestroyed = false
        android.util.Log.d(TAG, "FloatingWorkspaceService.onCreate")
        startForegroundServiceNotification()
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
        intent?.getIntExtra(EXTRA_SCREEN_CAPTURE_RESULT_CODE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let {
            screenCaptureResultCode = it
        }
        intent?.getParcelableExtraCompat<Intent>(EXTRA_SCREEN_CAPTURE_DATA)?.let {
            screenCaptureData = Intent(it)
            screenCaptureSession?.close()
            screenCaptureSession = ScreenCaptureSession(applicationContext, screenCaptureResultCode ?: Int.MIN_VALUE, it)
        }
        intent?.getStringExtra(EXTRA_SCRIPT_ID)?.let { loadScript(it) }
        android.util.Log.d(
            TAG,
            "FloatingWorkspaceService.onStartCommand: startId=$startId flags=$flags"
        )
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
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        if (isDestroyed) return
        isDestroyed = true
        android.util.Log.d(TAG, "FloatingWorkspaceService.onDestroy")
        runningJob?.cancel()
        runningJob = null
        scriptScope.cancel()
        screenCaptureSession?.close()
        screenCaptureSession = null
        mainHandler.removeCallbacksAndMessages(null)
        dismissDialogs()
        dismissPickers()
        dismissPage()
        if (::workspaceView.isInitialized && workspaceView.isAttachedToWindow) {
            try {
                windowManager.removeViewImmediate(workspaceView)
            } catch (_: RuntimeException) {
                // The window may already have been removed by the system.
            }
        }
        super.onDestroy()
    }

    private fun dismissPickers() {
        try {
            coordinatePicker?.dismiss()
        } catch (_: RuntimeException) {
        } finally {
            coordinatePicker = null
        }
        try {
            colorPicker?.dismiss()
        } catch (_: RuntimeException) {
        } finally {
            colorPicker = null
        }
        if (::windowManager.isInitialized) visionPickerCoordinator.dismiss()
        try {
            nodePicker?.dismiss()
        } catch (_: RuntimeException) {
        } finally {
            nodePicker = null
        }
        try {
            swipeCoordinatePicker?.dismiss()
        } catch (_: RuntimeException) {
        } finally {
            swipeCoordinatePicker = null
        }
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

    private fun visionControllerOrNull() = screenCaptureSession?.let {
        ScreenCaptureVisionController(it, applicationContext)
    }

    private fun runScript() {
        if (isDestroyed || runningJob?.isActive == true) return
        if (scriptWorkspace.isEmpty) {
            showRunResult("请先添加动作")
            return
        }
        runningJob = scriptScope.launch {
            val result = scriptWorkspaceCoordinator.run()
            val message = when (result) {
                ActionExecutionResult.Success -> "脚本运行完成"
                ActionExecutionResult.NotImplemented -> "包含暂未实现的动作"
                is ActionExecutionResult.Failed -> {
                    if (screenCaptureSession?.isValid == false && result.message.contains("屏幕")) {
                        runningJob?.cancel()
                        "屏幕录制授权已失效，请重新授权"
                    } else "运行失败：${result.message}"
                }
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
        dialog.setOnDismissListener { shownDialogs.remove(dialog) }
        shownDialogs.add(dialog)
        dialog.show()
    }

    private fun dismissDialogs() {
        shownDialogs.toList().forEach { dialog ->
            runCatching { dialog.dismiss() }
        }
        shownDialogs.clear()
    }

    private fun toggleSettings() {
        if (pageCoordinator.isShowing) {
            dismissPage()
        } else {
            showSettingsPage()
        }
    }

    private fun toggleActionList() {
        if (pageCoordinator.isShowing) {
            dismissPage()
        } else {
            showActionListPage()
        }
    }

    private fun loadScript(id: String) {
        val script = scriptWorkspaceCoordinator.load(id) ?: return
        Toast.makeText(this, "已加载：${script.name}", Toast.LENGTH_SHORT).show()
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
        content.addView(ScrollView(this).apply { addView(body) }, FrameLayout.LayoutParams(-1, -1))
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
        scriptWorkspaceCoordinator.currentScriptName?.let(scriptName::setText)
        body.addView(scriptName, LinearLayout.LayoutParams(-1, dp(52)))

        val savedTitle = TextView(this).apply { text = "已保存脚本"; setTextColor(Color.rgb(106, 89, 64)); setPadding(0, dp(12), 0, dp(4)) }
        body.addView(savedTitle, LinearLayout.LayoutParams(-1, dp(32)))
        scriptWorkspaceCoordinator.listSavedScripts().forEach { saved ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val load = pageButton(saved.name, dp(42)).apply { setOnClickListener { loadScript(saved.id); showSettingsPage() } }
            row.addView(load, LinearLayout.LayoutParams(0, dp(42), 1f))
            val delete = pageButton("删除", dp(42)).apply {
                setTextColor(Color.rgb(156, 83, 64))
                setOnClickListener {
                    AlertDialog.Builder(this@FloatingWorkspaceService).setTitle("删除脚本").setMessage(saved.name)
                        .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                            scriptWorkspaceCoordinator.delete(saved.id)
                            showSettingsPage()
                        }.create().also(::showOverlayDialog)
                }
            }
            row.addView(delete, LinearLayout.LayoutParams(dp(64), dp(42)).apply { leftMargin = dp(6) })
            body.addView(row, LinearLayout.LayoutParams(-1, dp(46)))
        }

        val saveButton = pageButton("保存", dp(48))
        saveButton.setOnClickListener {
            val name = scriptName.text.toString().trim()
            if (name.isBlank()) {
                scriptName.error = "脚本名称不能为空"
            } else {
                val saved = scriptWorkspaceCoordinator.save(name)
                Toast.makeText(this, "脚本已保存", Toast.LENGTH_SHORT).show()
                showSettingsPage()
            }
        }
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

    private fun addAction(type: ActionType) = actionEditorCoordinator.open(type)

    private fun addDefaultAction(type: ActionType) {
        when (val apiResult = scriptActionApi.addDefaultAction(type)) {
            is com.example.myapplication.script.api.ActionApiResult.Success -> dismissPage()
            is com.example.myapplication.script.api.ActionApiResult.Failure ->
                Toast.makeText(this, apiResult.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSwipeActionEditor() {
        dismissPage()
        val picker = SwipeCoordinatePickerOverlay(
            context = this,
            windowManager = windowManager,
            onConfirmed = { start, end ->
                if (!isDestroyed) showSwipeActionDialog(start.x, start.y, end.x, end.y)
            },
            onCancelled = { swipeCoordinatePicker = null }
        )
        if (picker.show()) {
            swipeCoordinatePicker = picker
        } else {
            Toast.makeText(this, "无法显示滑动坐标选择窗口", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSwipeActionDialog(startX: Int, startY: Int, endX: Int, endY: Int) {
        swipeCoordinatePicker = null
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val duration = EditText(this).apply {
            hint = "滑动持续时间（毫秒）"
            setText("400")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        body.addView(TextView(this).apply {
            text = "起点：$startX, $startY\n终点：$endX, $endY"
            setTextColor(Color.rgb(72, 62, 47))
        })
        body.addView(duration, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(10)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle("滑动")
            .setView(body)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加") { _, _ ->
                val durationMillis = duration.text.toString().toLongOrNull()
                    ?: return@setPositiveButton
                if (durationMillis < 1L) return@setPositiveButton
                when (val apiResult = scriptActionApi.addAction(
                    type = ActionType.SWIPE,
                    fields = mapOf(
                        ActionParameterKey.START_X to startX.toString(),
                        ActionParameterKey.START_Y to startY.toString(),
                        ActionParameterKey.END_X to endX.toString(),
                        ActionParameterKey.END_Y to endY.toString(),
                        ActionParameterKey.DURATION_MILLIS to durationMillis.toString()
                    )
                )) {
                    is com.example.myapplication.script.api.ActionApiResult.Success -> dismissPage()
                    is com.example.myapplication.script.api.ActionApiResult.Failure -> {
                        Toast.makeText(this, apiResult.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .create()
        showOverlayDialog(dialog)
    }

    private fun showNodePicker() {
        val controller = AutomationAccessibilityService.controller
        val nodes = controller?.snapshotCurrentWindow().orEmpty()
        if (nodes.isEmpty()) {
            Toast.makeText(this, "当前屏幕没有可选择的控件", Toast.LENGTH_SHORT).show()
            return
        }
        dismissPage()
        val picker = AccessibilityNodePickerOverlay(
            context = this,
            windowManager = windowManager,
            nodes = nodes,
            onConfirmed = { node ->
                nodePicker = null
                if (!isDestroyed) {
                    val selector = node.toSelector()
                    val fields = mapOf(
                    ActionParameterKey.NODE_TEXT to selector.text.orEmpty(),
                    ActionParameterKey.NODE_DESCRIPTION to selector.contentDescription.orEmpty(),
                    ActionParameterKey.NODE_RESOURCE_ID to selector.resourceId.orEmpty(),
                    ActionParameterKey.NODE_CLASS_NAME to selector.className.orEmpty(),
                    ActionParameterKey.NODE_PACKAGE_NAME to selector.packageName.orEmpty()
                )
                    when (val result = scriptActionApi.addAction(
                        type = ActionType.CLICK_NODE,
                        fields = fields,
                        displayName = "点击控件：${node.text.orEmpty().ifBlank { node.resourceId.orEmpty() }}"
                    )) {
                        is com.example.myapplication.script.api.ActionApiResult.Success -> dismissPage()
                        is com.example.myapplication.script.api.ActionApiResult.Failure ->
                            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onCancelled = { nodePicker = null }
        )
        if (picker.show()) {
            nodePicker = picker
        } else {
            Toast.makeText(this, "无法显示控件选择窗口", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFindColorEditor() {
        showActionEditor(ActionType.FIND_COLOR)
    }

    private fun showOcrTextEditor() {
        dismissPage()
        val resultCode = screenCaptureResultCode
        val data = screenCaptureData?.let(::Intent)
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            Toast.makeText(this, "请先返回主页授权屏幕录制", Toast.LENGTH_SHORT).show()
            return
        }
        scriptScope.launch {
            val bitmap = screenCaptureSession?.captureBitmap()
            if (isDestroyed) {
                bitmap?.takeIf { !it.isRecycled }?.recycle()
                return@launch
            }
            if (bitmap == null) {
                Toast.makeText(this@FloatingWorkspaceService, "无法获取当前屏幕快照，请重新授权", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val shown = visionPickerCoordinator.show(
                screenshot = bitmap,
                onConfirmed = confirmed@ { selection ->
                    if (isDestroyed) return@confirmed
                    showActionEditor(
                        ActionType.OCR_TEXT,
                        mapOf(
                            ActionParameterKey.MATCH_REGION_LEFT to selection.left.toString(),
                            ActionParameterKey.MATCH_REGION_TOP to selection.top.toString(),
                            ActionParameterKey.MATCH_REGION_RIGHT to selection.right.toString(),
                            ActionParameterKey.MATCH_REGION_BOTTOM to selection.bottom.toString()
                        )
                    )
                },
                onCancelled = {}
            )
            if (!shown) {
                Toast.makeText(this@FloatingWorkspaceService, "无法显示 OCR 框选窗口", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showImageTemplateEditor(type: ActionType) {
        dismissPage()
        val resultCode = screenCaptureResultCode
        val data = screenCaptureData?.let { Intent(it) }
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            Toast.makeText(this, "请先返回主页授权屏幕录制", Toast.LENGTH_SHORT).show()
            return
        }
        scriptScope.launch {
            val bitmap = screenCaptureSession?.captureBitmap()
            if (isDestroyed) {
                bitmap?.takeIf { !it.isRecycled }?.recycle()
                return@launch
            }
            if (bitmap == null) {
                Toast.makeText(this@FloatingWorkspaceService, "无法获取当前屏幕快照，请重新授权", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val shown = visionPickerCoordinator.show(
                screenshot = bitmap,
                onConfirmed = confirmed@ { selection ->
                    if (isDestroyed) {
                        return@confirmed
                    }
                    val id = ImageTemplateRepository(applicationContext).save(bitmap, selection)
                    if (id == null) {
                        Toast.makeText(this@FloatingWorkspaceService, "模板保存失败或框选区域过小", Toast.LENGTH_SHORT).show()
                    } else {
                        showActionEditor(type, mapOf(ActionParameterKey.TEMPLATE_ID to id))
                    }
                },
                onCancelled = {}
            )
            if (!shown) {
                Toast.makeText(this@FloatingWorkspaceService, "无法显示模板框选窗口", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showColorPicker() {
        dismissPage()
        val resultCode = screenCaptureResultCode
        val data = screenCaptureData?.let { Intent(it) }
        if (resultCode == null || data == null || resultCode != android.app.Activity.RESULT_OK) {
            Toast.makeText(this, "请先返回主页授权屏幕录制", Toast.LENGTH_SHORT).show()
            return
        }
        scriptScope.launch {
            val bitmap = screenCaptureSession?.captureBitmap()
            if (isDestroyed) {
                bitmap?.takeIf { !it.isRecycled }?.recycle()
                return@launch
            }
            if (bitmap == null) {
                Toast.makeText(this@FloatingWorkspaceService, "无法获取当前屏幕快照，请重新授权", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val picker = ColorPickerOverlay(
                context = this@FloatingWorkspaceService,
                windowManager = windowManager,
                screenshot = bitmap,
                onConfirmed = { x, y, hex, red, green, blue ->
                    colorPicker = null
                    if (isDestroyed) return@ColorPickerOverlay
                    when (val result = scriptActionApi.addAction(
                        type = ActionType.PICK_COLOR,
                        fields = mapOf(
                            ActionParameterKey.COLOR_HEX to hex,
                            ActionParameterKey.COLOR_RED to red.toString(),
                            ActionParameterKey.COLOR_GREEN to green.toString(),
                            ActionParameterKey.COLOR_BLUE to blue.toString(),
                            ActionParameterKey.PICK_X to x.toString(),
                            ActionParameterKey.PICK_Y to y.toString(),
                            ActionParameterKey.COLOR_VARIABLE_NAME to "pickedColor"
                        ),
                        displayName = "取色：$hex"
                    )) {
                        is com.example.myapplication.script.api.ActionApiResult.Success ->
                            Toast.makeText(this@FloatingWorkspaceService, "颜色已记录：$hex", Toast.LENGTH_SHORT).show()
                        is com.example.myapplication.script.api.ActionApiResult.Failure ->
                            Toast.makeText(this@FloatingWorkspaceService, result.message, Toast.LENGTH_SHORT).show()
                    }
                },
                onCancelled = { colorPicker = null }
            )
            if (picker.show()) {
                colorPicker = picker
            } else {
                Toast.makeText(this@FloatingWorkspaceService, "无法显示取色窗口", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCoordinateActionEditor(type: ActionType) {
        dismissPage()
        val picker = CoordinatePickerOverlay(
            context = this,
            windowManager = windowManager,
            onConfirmed = { point ->
                if (!isDestroyed) showCoordinateActionDialog(type, point.x, point.y)
            },
            onCancelled = { coordinatePicker = null }
        )
        if (picker.show()) {
            coordinatePicker = picker
        } else {
            Toast.makeText(this, "无法显示坐标选择窗口", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCoordinateActionDialog(type: ActionType, x: Int, y: Int) {
        coordinatePicker = null
        val definition = ActionEditorRegistry.definitionFor(type) ?: return
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val duration = EditText(this).apply {
            hint = "按下时长（毫秒）"
            setText(if (type == ActionType.LONG_CLICK) "800" else "80")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        body.addView(TextView(this).apply {
            text = "坐标：$x, $y"
            setTextColor(Color.rgb(72, 62, 47))
        })
        body.addView(duration, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(10)
        })
        val dialog = AlertDialog.Builder(this)
            .setTitle(type.displayName)
            .setView(body)
            .setNegativeButton("取消", null)
            .setPositiveButton("添加") { _, _ ->
                val durationMillis = duration.text.toString().toLongOrNull()
                    ?: return@setPositiveButton
                val minimum = if (type == ActionType.LONG_CLICK) 500L else 1L
                if (durationMillis < minimum) return@setPositiveButton
                when (val apiResult = scriptActionApi.addAction(
                    type = type,
                    fields = mapOf(
                        ActionParameterKey.X to x.toString(),
                        ActionParameterKey.Y to y.toString(),
                        ActionParameterKey.DURATION_MILLIS to durationMillis.toString()
                    )
                )) {
                    is com.example.myapplication.script.api.ActionApiResult.Success -> dismissPage()
                    is com.example.myapplication.script.api.ActionApiResult.Failure -> {
                        Toast.makeText(this, apiResult.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .create()
        showOverlayDialog(dialog)
    }

    private fun showExistingActionEditor(action: ScriptAction) {
        dismissPage()
        showActionEditor(action.type, action.parameters, action)
    }

    private fun showActionEditor(
        type: ActionType,
        initialValues: Map<String, String> = emptyMap(),
        existingAction: ScriptAction? = null
    ) {
        val definition = ActionEditorRegistry.definitionFor(type) ?: return
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        var executionOptions = existingAction?.executionOptions ?: ActionExecutionOptions()
        var beforeActions: List<ScriptAction> = existingAction?.beforeActions ?: emptyList()
        var afterActions: List<ScriptAction> = existingAction?.afterActions ?: emptyList()
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
                showActionSettings(executionOptions, beforeActions, afterActions) { settings ->
                    executionOptions = settings.executionOptions
                    beforeActions = settings.beforeActions
                    afterActions = settings.afterActions
                    text = if (settings.executionOptions.condition is ActionCondition.Always &&
                        settings.beforeActions.isEmpty() && settings.afterActions.isEmpty()
                    ) "设置" else "已设置"
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
                val storedValue = initialValues[fieldDefinition.key]
                setText(
                    if (type == ActionType.WAIT && fieldDefinition.key == ActionParameterKey.DURATION_MILLIS) {
                        storedValue?.toLongOrNull()?.div(1000L)?.toString() ?: fieldDefinition.defaultValue
                    } else {
                        storedValue ?: fieldDefinition.defaultValue
                    }
                )
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
            .setTitle(if (existingAction == null) type.displayName else "编辑${type.displayName}")
            .setView(body)
            .setNegativeButton("取消", null)
            .setPositiveButton(if (existingAction == null) "添加" else "保存") { _, _ ->
                val editorValues = initialValues + fields.mapValues { (_, field) -> field.text.toString() }
                val settings = ActionSettings(
                    executionOptions = executionOptions,
                    beforeActions = beforeActions,
                    afterActions = afterActions
                )
                val apiResult = if (existingAction == null) {
                    scriptActionApi.addAction(type, editorValues, settings)
                } else {
                    actionWorkspaceCoordinator.replace(existingAction.id, type, editorValues, settings)
                }
                when (apiResult) {
                    is com.example.myapplication.script.api.ActionApiResult.Success -> dismissPage()
                    is com.example.myapplication.script.api.ActionApiResult.Failure -> {
                        Toast.makeText(this, apiResult.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .create()
        showOverlayDialog(dialog)
    }

    private fun showActionSettings(
        currentOptions: ActionExecutionOptions,
        currentBeforeActions: List<ScriptAction>,
        currentAfterActions: List<ScriptAction>,
        onSettingsSelected: (ActionSettings) -> Unit
    ) = actionSettingsCoordinator.show(
        currentOptions = currentOptions,
        currentBeforeActions = currentBeforeActions,
        currentAfterActions = currentAfterActions,
        onSettingsSelected = onSettingsSelected
    )

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

    private fun createVariableJudgement(
        variableName: EditText,
        operator: Spinner,
        expectedValue: EditText
    ): JudgementContent {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
        }
        val loading = ProgressBar(this).apply {
            isIndeterminate = true
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(14))
            addView(variableName, LinearLayout.LayoutParams(-1, dp(48)).apply {
                bottomMargin = dp(8)
            })
            addView(operator, LinearLayout.LayoutParams(-1, dp(44)).apply {
                bottomMargin = dp(8)
            })
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
            mainHandler.postDelayed({
                if (!isDestroyed && content.isAttachedToWindow) {
                    loading.visibility = View.GONE
                    form.visibility = View.VISIBLE
                }
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

    private fun showNestedActionPicker(title: String, onActionSelected: (ScriptAction) -> Unit) {
        val actionTypes = ActionDefinitionRegistry.availableTypes()
            .filter { type -> ActionDefinitionRegistry.canCreateDefault(type) }
        if (actionTypes.isEmpty()) {
            Toast.makeText(this, "当前没有可以直接添加的嵌套动作，请先完成参数编辑", Toast.LENGTH_SHORT).show()
            return
        }
        val actionNames = actionTypes.map { it.displayName }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(actionNames) { pickerDialog, index ->
                val type = actionTypes[index]
                when (val result = ActionFactory.createDefault(type)) {
                    is ActionCreationResult.Success -> onActionSelected(result.action)
                    is ActionCreationResult.Invalid -> {
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
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

    private fun showAppControlEditor() {
        dismissPage()
        val applications = applicationController.queryLaunchableApplications()
        if (applications.isEmpty()) {
            Toast.makeText(this, "没有找到可启动的应用", Toast.LENGTH_SHORT).show()
            return
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedBackground(Color.argb(240, 255, 248, 224), dp(18), Color.rgb(244, 226, 168))
        }
        val appList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val toggle = TextView(this).apply {
            text = "选择应用（点击展开）"
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(72, 62, 47))
            setPadding(dp(12), 0, dp(12), 0)
            background = roundedBackground(Color.rgb(255, 243, 190), dp(12), Color.rgb(244, 226, 168))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val expanded = appList.visibility != View.VISIBLE
                appList.visibility = if (expanded) View.VISIBLE else View.GONE
                text = if (expanded) "选择应用（点击收起）" else "选择应用（点击展开）"
            }
        }
        content.addView(toggle, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(appList, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10)
        })
        applications.forEach { application ->
            val button = TextView(this).apply {
                text = "${application.label}\n${application.packageName}"
                textSize = 13f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Color.rgb(72, 62, 47))
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = roundedBackground(Color.rgb(255, 243, 190), dp(10), Color.TRANSPARENT)
                isClickable = true
                isFocusable = true
                setOnClickListener { showAppControlOperation(application.packageName, application.label) }
            }
            appList.addView(button, LinearLayout.LayoutParams(-1, dp(58)).apply {
                bottomMargin = dp(6)
            })
        }
        showPage(
            view = content,
            width = dp(300),
            height = (resources.displayMetrics.heightPixels * 0.7f).toInt(),
            focusable = true,
            followWorkspace = true
        )
    }

    private fun showAppControlOperation(packageName: String, label: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(label)
            .setItems(arrayOf("打开应用")) { _, _ ->
                when (val result = scriptActionApi.addAction(
                    type = ActionType.APP_CONTROL,
                    fields = mapOf(
                        ActionParameterKey.APP_CONTROL_OPERATION to "LAUNCH",
                        ActionParameterKey.PACKAGE_NAME to packageName
                    ),
                    displayName = "打开应用：$label"
                )) {
                    is com.example.myapplication.script.api.ActionApiResult.Success -> dismissPage()
                    is com.example.myapplication.script.api.ActionApiResult.Failure ->
                        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                }
            }
            .create()
        showOverlayDialog(dialog)
    }

    private fun chooseActionVariant(type: ActionType, variants: List<String>) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(type.displayName)
            .setItems(variants.toTypedArray()) { _, index ->
                val navigationAction = listOf("BACK", "HOME", "RECENTS")[index]
                when (val apiResult = scriptActionApi.addAction(
                    type = type,
                    fields = mapOf(
                        com.example.myapplication.script.model.ActionParameterKey.NAVIGATION_ACTION to
                            navigationAction
                    ),
                    displayName = "${type.displayName}：${variants[index]}"
                )) {
                    is com.example.myapplication.script.api.ActionApiResult.Success -> dismissPage()
                    is com.example.myapplication.script.api.ActionApiResult.Failure ->
                        Toast.makeText(this, apiResult.message, Toast.LENGTH_SHORT).show()
                }
            }
            .create()
        showOverlayDialog(dialog)
    }

    private fun showActionListPage() = workspaceCoordinator.showActionListPage()

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
        if (isDestroyed) return
        dismissPage()
        pageCoordinator.show(view, width, height, focusable, followWorkspace)
    }

    private fun dismissPage() {
        if (::windowManager.isInitialized) pageCoordinator.dismiss()
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
        pageCoordinator.updatePosition()
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

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        getParcelableExtra(key)
    }
