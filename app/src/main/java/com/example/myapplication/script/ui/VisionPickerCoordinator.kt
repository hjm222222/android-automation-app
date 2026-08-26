package com.example.myapplication.script.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.WindowManager
import com.example.myapplication.script.platform.ImageTemplatePickerOverlay

/**
 * 协调基于截图的框选窗口及其临时位图。
 *
 * 它不持有 Service、协程或屏幕录制会话，调用方负责获取截图和处理框选结果。
 */
class VisionPickerCoordinator(
    context: Context,
    private val windowManager: WindowManager
) {
    private val applicationContext = context.applicationContext
    private var picker: ImageTemplatePickerOverlay? = null

    fun show(
        screenshot: Bitmap,
        onConfirmed: (Rect) -> Unit,
        onCancelled: () -> Unit
    ): Boolean {
        dismiss()
        val nextPicker = ImageTemplatePickerOverlay(
            context = applicationContext,
            windowManager = windowManager,
            screenshot = screenshot,
            onConfirmed = { selection ->
                picker = null
                onConfirmed(selection)
            },
            onCancelled = {
                picker = null
                onCancelled()
            }
        )
        return nextPicker.show().also { shown ->
            if (shown) picker = nextPicker
        }
    }

    fun dismiss() {
        picker?.dismiss()
        picker = null
    }
}
