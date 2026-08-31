package com.example.myapplication.script.platform

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.roundToInt

/** 在截图上选择 OCR 识别出的整行文字。 */
class OcrTextPickerOverlay(
    context: Context,
    private val windowManager: WindowManager,
    private val screenshot: Bitmap,
    private val result: OcrTextResult,
    private val onConfirmed: (OcrTextLine) -> Unit,
    private val onCancelled: () -> Unit,
    private val onDismissed: () -> Unit = {}
) {
    private val root = FrameLayout(context.applicationContext)
    private val controlBar = FrameLayout(root.context)
    private val buttonWidth = dp(120)
    private val buttonHeight = dp(52)
    private val cancelButton = Button(root.context).apply {
        text = "取消"
        contentDescription = "取消文字选择"
        minWidth = 0
        minHeight = 0
        setOnClickListener { cancel() }
    }
    private val confirmButton = Button(root.context).apply {
        text = "确认文字"
        contentDescription = "确认文字选择"
        isEnabled = false
        minWidth = 0
        minHeight = 0
        setOnClickListener { confirm() }
    }
    private val textView = OcrTextView(context.applicationContext, screenshot, result.lines) { index ->
        confirmButton.isEnabled = index != null
    }

    init {
        val enabled = ColorStateList.valueOf(Color.rgb(45, 110, 75))
        val disabled = ColorStateList.valueOf(Color.rgb(170, 170, 170))
        confirmButton.backgroundTintList = ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(disabled.defaultColor, enabled.defaultColor)
        )
        cancelButton.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        cancelButton.setTextColor(Color.DKGRAY)
        confirmButton.setTextColor(Color.WHITE)
    }

    private enum class State {
        NEW,
        SHOWN,
        FINISHED
    }

    private var state = State.NEW
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun show(): Boolean {
        if (state != State.NEW || screenshot.isRecycled || result.lines.isEmpty()) return false
        root.removeAllViews()
        root.setBackgroundColor(Color.WHITE)
        root.addView(textView, FrameLayout.LayoutParams(-1, -1))
        controlBar.setBackgroundColor(Color.argb(190, 255, 255, 255))
        root.addView(controlBar, FrameLayout.LayoutParams(-1, dp(84), Gravity.BOTTOM))
        controlBar.addView(cancelButton, FrameLayout.LayoutParams(buttonWidth, buttonHeight, Gravity.CENTER_VERTICAL or Gravity.START).apply {
            leftMargin = dp(16)
        })
        controlBar.addView(confirmButton, FrameLayout.LayoutParams(buttonWidth, buttonHeight, Gravity.CENTER_VERTICAL or Gravity.END).apply {
            rightMargin = dp(16)
        })
        textView.clearSelection()
        confirmButton.isEnabled = false
        return try {
            windowManager.addView(root, params)
            state = State.SHOWN
            true
        } catch (_: RuntimeException) {
            state = State.FINISHED
            onDismissed()
            false
        }
    }

    fun dismiss() {
        finish { }
    }

    private fun confirm() {
        val index = textView.selectedIndex ?: return
        val selectedLine = result.lines[index]
        finish {
            onConfirmed(selectedLine)
        }
    }

    private fun cancel() {
        finish {
            onCancelled()
        }
    }

    private fun finish(callback: () -> Unit) {
        if (state == State.FINISHED) return
        state = State.FINISHED
        removeWindow()
        try {
            callback()
        } finally {
            onDismissed()
        }
    }

    private fun removeWindow() {
        if (root.isAttachedToWindow) {
            try {
                windowManager.removeView(root)
            } catch (_: RuntimeException) {
                // Window token may already be invalid while the service is stopping.
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * root.resources.displayMetrics.density).roundToInt()

    private class OcrTextView(
        context: Context,
        private val bitmap: Bitmap,
        private val lines: List<OcrTextLine>,
        private val onSelectionChanged: (Int?) -> Unit
    ) : View(context) {
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private var destination = RectF()
        var selectedIndex: Int? = null
            private set

        fun clearSelection() {
            selectedIndex = null
            onSelectionChanged(null)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (bitmap.isRecycled || width <= 0 || height <= 0) return
            val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            destination = RectF(
                (width - drawWidth) / 2f,
                (height - drawHeight) / 2f,
                (width + drawWidth) / 2f,
                (height + drawHeight) / 2f
            )
            canvas.drawBitmap(bitmap, null, destination, bitmapPaint)
            canvas.drawColor(Color.argb(77, 255, 255, 255))
            lines.forEachIndexed { index, line ->
                val rect = bitmapToView(line.boundingBox, scale)
                val selected = index == selectedIndex
                fillPaint.color = Color.argb(if (selected) 28 else 52, 255, 255, 255)
                rectPaint.color = Color.argb(if (selected) 210 else 90, 255, 255, 255)
                canvas.drawRect(rect, fillPaint)
                canvas.drawRect(rect, rectPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked != MotionEvent.ACTION_UP || destination.isEmpty) return true
            val scale = destination.width() / bitmap.width
            val point = viewToBitmap(event.x, event.y, scale)
            val index = lines.indexOfFirst { it.boundingBox.contains(point.first, point.second) }
            if (index >= 0) {
                selectedIndex = index
                onSelectionChanged(index)
                invalidate()
            }
            return true
        }

        private fun bitmapToView(rect: Rect, scale: Float) = RectF(
            destination.left + rect.left * scale,
            destination.top + rect.top * scale,
            destination.left + rect.right * scale,
            destination.top + rect.bottom * scale
        )

        private fun viewToBitmap(x: Float, y: Float, scale: Float) =
            ((x - destination.left) / scale).toInt() to ((y - destination.top) / scale).toInt()
    }
}
