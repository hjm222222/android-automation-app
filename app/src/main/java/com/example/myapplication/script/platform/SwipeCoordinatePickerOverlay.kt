package com.example.myapplication.script.platform

import android.content.Context
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

/**
 * 滑动动作的坐标录入层。
 *
 * 第一次按下的位置会固定为起点；手指移动时只更新终点准星。
 * 该组件只负责采集坐标，不创建 ScriptAction，也不执行系统手势。
 */
class SwipeCoordinatePickerOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onConfirmed: (start: Point, end: Point) -> Unit,
    private val onCancelled: () -> Unit
) {
    private val root = FrameLayout(context).apply {
        // 与点击选择器统一使用淡黄色半透明背景。
        setBackgroundColor(Color.argb(150, 255, 248, 210))
    }
    private val gestureView = SwipeGestureView(context)
    private val positionLabel = TextView(context).apply {
        setTextColor(Color.rgb(72, 62, 47))
        textSize = 14f
        setPadding(24, 16, 24, 16)
        text = "先按下屏幕选择起点，再拖动选择终点"
    }
    private var shown = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    fun show(): Boolean {
        if (shown) return false
        root.addView(gestureView, FrameLayout.LayoutParams(-1, -1))
        root.addView(positionLabel, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        val actions = FrameLayout(context).apply {
            setPadding(24, 12, 24, 24)
            isClickable = false
            isFocusable = false
            addView(styledButton("取消", "取消滑动坐标选择") { cancel() }, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.START or Gravity.BOTTOM
            })
            addView(styledButton("确认滑动", "确认滑动起点和终点") { confirm() }, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.END or Gravity.BOTTOM
            })
        }
        root.addView(actions, FrameLayout.LayoutParams(-1, -1))
        gestureView.onPositionChanged = { start, end ->
            positionLabel.text = if (start == null || end == null) {
                "先按下屏幕选择起点，再拖动选择终点"
            } else {
                "起点：${start.x}, ${start.y}  终点：${end.x}, ${end.y}"
            }
        }
        return try {
            windowManager.addView(root, layoutParams)
            shown = true
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun styledButton(
        label: String,
        description: String,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        text = label
        contentDescription = description
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(244, 211, 115))
            cornerRadius = 18f
        }
        setPadding(28, 0, 28, 0)
        minHeight = 48
        setOnClickListener { onClick() }
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        if (root.isAttachedToWindow) {
            runCatching { windowManager.removeView(root) }
        }
    }

    private fun confirm() {
        val start = gestureView.startPoint ?: return
        val end = gestureView.endPoint ?: return
        dismiss()
        onConfirmed(start, end)
    }

    private fun cancel() {
        dismiss()
        onCancelled()
    }

    private class SwipeGestureView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private var dragging = false
        var startPoint: Point? = null
            private set
        var endPoint: Point? = null
            private set
        var onPositionChanged: ((Point?, Point?) -> Unit)? = null

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val start = startPoint
            val end = endPoint
            if (start != null) {
                drawCrosshair(canvas, start.x.toFloat(), start.y.toFloat(), startPaint)
            }
            if (end != null) {
                if (start != null) {
                    canvas.drawLine(
                        start.x.toFloat(),
                        start.y.toFloat(),
                        end.x.toFloat(),
                        end.y.toFloat(),
                        paint
                    )
                }
                drawCrosshair(canvas, end.x.toFloat(), end.y.toFloat(), paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    val point = Point(event.rawX.toInt(), event.rawY.toInt())
                    startPoint = point
                    endPoint = point
                    notifyChanged()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        endPoint = Point(event.rawX.toInt(), event.rawY.toInt())
                        notifyChanged()
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        endPoint = Point(event.rawX.toInt(), event.rawY.toInt())
                        notifyChanged()
                    }
                    dragging = false
                    return true
                }
            }
            return true
        }

        private fun notifyChanged() {
            invalidate()
            onPositionChanged?.invoke(startPoint, endPoint)
        }

        private fun drawCrosshair(canvas: Canvas, x: Float, y: Float, paint: Paint) {
            canvas.drawCircle(x, y, 24f, paint)
            canvas.drawLine(x - 42f, y, x + 42f, y, paint)
            canvas.drawLine(x, y - 42f, x, y + 42f, paint)
        }
    }
}
