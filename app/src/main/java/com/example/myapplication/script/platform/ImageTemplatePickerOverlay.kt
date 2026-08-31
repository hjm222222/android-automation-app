package com.example.myapplication.script.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
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
    private val onCancelled: () -> Unit,
    private val onDismissed: () -> Unit = {}
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
        Log.d(TAG, "event=image_selection_overlay_show width=${screenshot.width} height=${screenshot.height}")
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
        } catch (error: RuntimeException) {
            Log.e(TAG, "Failed to add template picker overlay", error)
            false
        }
    }

    fun dismiss() {
        if (!shown) return
        removeWindow()
        onDismissed()
    }

    private fun confirm() {
        val rect = selectionView.selectionInBitmap ?: return
        Log.d(TAG, "event=image_selection_overlay_confirm left=${rect.left} top=${rect.top} right=${rect.right} bottom=${rect.bottom}")
        removeWindow()
        try {
            onConfirmed(rect)
        } finally {
            onDismissed()
        }
    }

    private fun cancel() {
        Log.d(TAG, "event=image_selection_overlay_cancel")
        removeWindow()
        try {
            onCancelled()
        } finally {
            onDismissed()
        }
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

    private companion object {
        const val TAG = "ImageTemplatePickerOverlay"
    }

    private class SelectionView(context: Context, private val bitmap: Bitmap) : View(context) {
        private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.YELLOW
        }
        private var destination = RectF()
        private var initialized = false
        private var downX = 0f
        private var downY = 0f
        private var dragMode = DragMode.CREATE
        private var startSelection: Rect? = null
        var selectionInBitmap: Rect? = null
            private set

        private enum class DragMode { CREATE, MOVE, LEFT, TOP, RIGHT, BOTTOM }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (bitmap.isRecycled || width <= 0 || height <= 0) return
            val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val left = (width - drawWidth) / 2f
            val top = (height - drawHeight) / 2f
            destination = RectF(left, top, left + drawWidth, top + drawHeight)
            if (!initialized) {
                val selectionWidth = (bitmap.width * 0.5f).toInt().coerceAtLeast(2)
                val selectionHeight = (bitmap.height * 0.3f).toInt().coerceAtLeast(2)
                selectionInBitmap = Rect(
                    (bitmap.width - selectionWidth) / 2,
                    (bitmap.height - selectionHeight) / 2,
                    (bitmap.width + selectionWidth) / 2,
                    (bitmap.height + selectionHeight) / 2
                )
                initialized = true
            }
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
                    startSelection = selectionInBitmap?.let(::Rect)
                    dragMode = hitTest(event.x, event.y)
                    if (dragMode == DragMode.CREATE) updateSelection(event.x, event.y)
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
            val end = viewToBitmap(x, y)
            val start = viewToBitmap(downX, downY)
            val base = startSelection
            selectionInBitmap = when (dragMode) {
                DragMode.CREATE -> Rect(minOf(start.first, end.first), minOf(start.second, end.second), maxOf(start.first, end.first), maxOf(start.second, end.second))
                DragMode.MOVE -> base?.let { val dx = end.first - start.first; val dy = end.second - start.second; val left = (it.left + dx).coerceIn(0, bitmap.width - it.width()); val top = (it.top + dy).coerceIn(0, bitmap.height - it.height()); Rect(left, top, left + it.width(), top + it.height()) }
                DragMode.LEFT -> base?.let { Rect(end.first.coerceIn(0, it.right - 1), it.top, it.right, it.bottom) }
                DragMode.TOP -> base?.let { Rect(it.left, end.second.coerceIn(0, it.bottom - 1), it.right, it.bottom) }
                DragMode.RIGHT -> base?.let { Rect(it.left, it.top, end.first.coerceIn(it.left + 1, bitmap.width), it.bottom) }
                DragMode.BOTTOM -> base?.let { Rect(it.left, it.top, it.right, end.second.coerceIn(it.top + 1, bitmap.height)) }
            }
            invalidate()
        }

        private fun hitTest(x: Float, y: Float): DragMode {
            val selection = selectionInBitmap ?: return DragMode.CREATE
            val point = viewToBitmap(x, y)
            val edge = maxOf(8, minOf(bitmap.width, bitmap.height) / 40)
            val nearLeft = kotlin.math.abs(point.first - selection.left) <= edge
            val nearRight = kotlin.math.abs(point.first - selection.right) <= edge
            val nearTop = kotlin.math.abs(point.second - selection.top) <= edge
            val nearBottom = kotlin.math.abs(point.second - selection.bottom) <= edge
            val withinHorizontalEdge = point.second in selection.top..selection.bottom
            val withinVerticalEdge = point.first in selection.left..selection.right
            return when {
                nearLeft && withinHorizontalEdge -> DragMode.LEFT
                nearRight && withinHorizontalEdge -> DragMode.RIGHT
                nearTop && withinVerticalEdge -> DragMode.TOP
                nearBottom && withinVerticalEdge -> DragMode.BOTTOM
                point.first in selection.left..selection.right && point.second in selection.top..selection.bottom -> DragMode.MOVE
                else -> DragMode.CREATE
            }
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
