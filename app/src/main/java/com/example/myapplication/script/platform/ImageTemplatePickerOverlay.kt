package com.example.myapplication.script.platform

import android.content.Context
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
import android.widget.TextView

/** 在已有截图上框选模板，不重新读取屏幕。 */
class ImageTemplatePickerOverlay(
    context: Context,
    private val windowManager: WindowManager,
    private val screenshot: Bitmap,
    private val onConfirmed: (Rect) -> Unit,
    private val onCancelled: () -> Unit
) {
    private val root = FrameLayout(context.applicationContext)
    private val selectionView = SelectionView(context.applicationContext, screenshot)
    private var shown = false
    private val params = WindowManager.LayoutParams(
        -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun show(): Boolean {
        if (shown || screenshot.isRecycled) return false
        root.setBackgroundColor(Color.BLACK)
        root.addView(selectionView, FrameLayout.LayoutParams(-1, -1))
        root.addView(TextView(root.context).apply {
            text = "拖动框选模板"
            setTextColor(Color.WHITE)
            setPadding(24, 16, 24, 16)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        root.addView(Button(root.context).apply { text = "取消"; contentDescription = "取消模板采集"; setOnClickListener { cancel() } }, FrameLayout.LayoutParams(-2, 56, Gravity.BOTTOM or Gravity.START).apply { leftMargin = 24; bottomMargin = 24 })
        root.addView(Button(root.context).apply { text = "确认模板"; contentDescription = "确认模板采集"; setOnClickListener { confirm() } }, FrameLayout.LayoutParams(-2, 56, Gravity.BOTTOM or Gravity.END).apply { rightMargin = 24; bottomMargin = 24 })
        return try {
            windowManager.addView(root, params)
            shown = true
            true
        } catch (_: RuntimeException) {
            recycleScreenshot()
            false
        }
    }

    fun dismiss() {
        removeWindow()
        recycleScreenshot()
    }

    private fun confirm() {
        val rect = selectionView.selectionInBitmap ?: return
        removeWindow()
        try {
            onConfirmed(rect)
        } finally {
            recycleScreenshot()
        }
    }

    private fun cancel() {
        removeWindow()
        recycleScreenshot()
        onCancelled()
    }

    private fun removeWindow() {
        if (!shown) return
        shown = false
        if (root.isAttachedToWindow) {
            try {
                windowManager.removeView(root)
            } catch (_: RuntimeException) {
                // Window token may already be invalid while the service is stopping.
            }
        }
    }

    private fun recycleScreenshot() {
        if (!screenshot.isRecycled) screenshot.recycle()
    }

    private class SelectionView(context: Context, private val bitmap: Bitmap) : View(context) {
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.YELLOW
        }
        private var destination = RectF()
        private var downX = 0f
        private var downY = 0f
        var selectionInBitmap: Rect? = null
            private set

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (bitmap.isRecycled || width <= 0 || height <= 0) return
            val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val left = (width - drawWidth) / 2f
            val top = (height - drawHeight) / 2f
            destination = RectF(left, top, left + drawWidth, top + drawHeight)
            canvas.drawBitmap(bitmap, null, destination, bitmapPaint)
            selectionInBitmap?.let { selection ->
                canvas.drawRect(
                    RectF(
                        destination.left + selection.left * scale,
                        destination.top + selection.top * scale,
                        destination.left + selection.right * scale,
                        destination.top + selection.bottom * scale
                    ),
                    selectionPaint
                )
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (destination.isEmpty) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    updateSelection(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    updateSelection(event.x, event.y)
                    return true
                }
            }
            return true
        }

        private fun updateSelection(x: Float, y: Float) {
            val start = viewToBitmap(downX, downY)
            val end = viewToBitmap(x, y)
            selectionInBitmap = Rect(
                minOf(start.first, end.first),
                minOf(start.second, end.second),
                maxOf(start.first, end.first),
                maxOf(start.second, end.second)
            )
            invalidate()
        }

        private fun viewToBitmap(x: Float, y: Float): Pair<Int, Int> {
            val safeX = x.coerceIn(destination.left, destination.right)
            val safeY = y.coerceIn(destination.top, destination.bottom)
            val bitmapX = ((safeX - destination.left) * bitmap.width / destination.width()).toInt()
            val bitmapY = ((safeY - destination.top) * bitmap.height / destination.height()).toInt()
            return bitmapX.coerceIn(0, bitmap.width) to bitmapY.coerceIn(0, bitmap.height)
        }
    }
}
