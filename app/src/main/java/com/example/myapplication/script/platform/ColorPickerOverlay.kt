package com.example.myapplication.script.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

class ColorPickerOverlay(
    context: Context,
    private val windowManager: WindowManager,
    private val screenshot: Bitmap,
    private val onConfirmed: (x: Int, y: Int, hex: String, red: Int, green: Int, blue: Int) -> Unit,
    private val onCancelled: () -> Unit
) {
    private val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
    private val pickerView = PickerView(context, screenshot)
    private val label = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        setPadding(24, 16, 24, 16)
        setBackgroundColor(Color.argb(180, 0, 0, 0))
    }
    private var shown = false
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun show(): Boolean {
        if (shown) return false
        root.addView(pickerView, FrameLayout.LayoutParams(-1, -1))
        root.addView(label, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        val actions = FrameLayout(root.context).apply {
            setPadding(24, 12, 24, 24)
            addView(button("取消", "取消取色") { cancel() }, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.START or Gravity.BOTTOM
            })
            addView(button("确认颜色", "确认当前颜色") { confirm() }, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.END or Gravity.BOTTOM
            })
        }
        root.addView(actions, FrameLayout.LayoutParams(-1, -1))
        pickerView.onColorChanged = ::updateLabel
        return try {
            windowManager.addView(root, params)
            shown = true
            root.post { pickerView.setPosition(root.width / 2f, root.height / 2f) }
            true
        } catch (_: RuntimeException) {
            if (!screenshot.isRecycled) screenshot.recycle()
            false
        }
    }

    private fun button(textValue: String, description: String, action: () -> Unit) = Button(root.context).apply {
        text = textValue
        contentDescription = description
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(244, 211, 115))
            cornerRadius = 18f
        }
        setPadding(28, 0, 28, 0)
        minHeight = 48
        setOnClickListener { action() }
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        if (root.isAttachedToWindow) runCatching { windowManager.removeView(root) }
        if (!screenshot.isRecycled) screenshot.recycle()
    }

    private fun updateLabel(color: Int) {
        label.text = "${color.toHex()}  RGB(${Color.red(color)}, ${Color.green(color)}, ${Color.blue(color)})"
    }

    private fun confirm() {
        val color = pickerView.currentColor
        val hex = color.toHex()
        dismiss()
        onConfirmed(pickerView.bitmapX, pickerView.bitmapY, hex, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun cancel() {
        dismiss()
        onCancelled()
    }

    private class PickerView(context: Context, private val bitmap: Bitmap) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private var xPosition = 0f
        private var yPosition = 0f
        private var dragging = false
        private var lastX = 0f
        private var lastY = 0f
        var currentColor: Int = Color.BLACK
            private set
        var bitmapX: Int = 0
            private set
        var bitmapY: Int = 0
            private set
        var onColorChanged: ((Int) -> Unit)? = null

        fun setPosition(x: Float, y: Float) {
            xPosition = x.coerceIn(0f, width.toFloat().coerceAtLeast(1f))
            yPosition = y.coerceIn(0f, height.toFloat().coerceAtLeast(1f))
            updateColor()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, width, height), null)
            canvas.drawCircle(xPosition, yPosition, 24f, paint)
            canvas.drawLine(xPosition - 42f, yPosition, xPosition + 42f, yPosition, paint)
            canvas.drawLine(xPosition, yPosition - 42f, xPosition, yPosition + 42f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    lastX = event.rawX
                    lastY = event.rawY
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (dragging) {
                    setPosition(xPosition + event.rawX - lastX, yPosition + event.rawY - lastY)
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
            }
            return true
        }

        private fun updateColor() {
            if (bitmap.width <= 0 || bitmap.height <= 0 || width <= 0 || height <= 0) return
            bitmapX = (xPosition / width * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            bitmapY = (yPosition / height * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
            currentColor = bitmap.getPixel(bitmapX, bitmapY)
            onColorChanged?.invoke(currentColor)
        }
    }
}

private fun Int.toHex(): String = String.format("#%06X", this and 0xFFFFFF)
