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
 * 全屏坐标录入层。
 *
 * 它只负责让用户拖动准星并确认坐标，不创建 ScriptAction，也不执行点击。
 * 未来把自绘准星替换成图片时，只需替换 CrosshairView 的绘制部分。
 */
class CoordinatePickerOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onConfirmed: (Point) -> Unit,
    private val onCancelled: () -> Unit
) {
    private val root = FrameLayout(context).apply {
        // 淡黄色半透明背景，保留底层轮廓，便于精确定位。
        setBackgroundColor(Color.argb(150, 255, 248, 210))
    }
    private val crosshair = CrosshairView(context)
    private val positionLabel = TextView(context).apply {
        setTextColor(Color.rgb(72, 62, 47))
        textSize = 14f
        setPadding(24, 16, 24, 16)
        text = "坐标：0, 0"
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

    fun show() {
        if (shown) return
        shown = true
        root.addView(crosshair, FrameLayout.LayoutParams(-1, -1))
        root.addView(positionLabel, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        val actions = FrameLayout(context).apply {
            setPadding(24, 12, 24, 24)
            isClickable = false
            isFocusable = false
            val cancel = styledButton("取消", "取消坐标选择") { cancel() }
            val confirm = styledButton("确认坐标", "确认当前坐标") { confirm() }
            addView(cancel, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.START or Gravity.BOTTOM
            })
            addView(confirm, FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.END or Gravity.BOTTOM
            })
        }
        root.addView(actions, FrameLayout.LayoutParams(-1, -1))
        crosshair.onPositionChanged = { x, y ->
            positionLabel.text = "坐标：$x, $y"
        }
        windowManager.addView(root, layoutParams)
        root.post { crosshair.setPosition(root.width / 2f, root.height / 2f) }
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
        windowManager.removeView(root)
    }

    private fun confirm() {
        val point = Point(crosshair.xPosition.toInt(), crosshair.yPosition.toInt())
        dismiss()
        onConfirmed(point)
    }

    private fun cancel() {
        dismiss()
        onCancelled()
    }

    private class CrosshairView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        var xPosition = 0f
            private set
        var yPosition = 0f
            private set
        var onPositionChanged: ((Int, Int) -> Unit)? = null
        // 记录手指上一帧的位置。准星只消费位移增量，不吸附到指尖。
        private var pointerDown = false
        private var lastPointerX = 0f
        private var lastPointerY = 0f

        fun setPosition(x: Float, y: Float) {
            xPosition = x
            yPosition = y
            invalidate()
            onPositionChanged?.invoke(x.toInt(), y.toInt())
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawCircle(xPosition, yPosition, 24f, paint)
            canvas.drawLine(xPosition - 42f, yPosition, xPosition + 42f, yPosition, paint)
            canvas.drawLine(xPosition, yPosition - 42f, xPosition, yPosition + 42f, paint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 任意位置都可以按下，但按下本身不改变准星位置。
                    pointerDown = true
                    lastPointerX = event.rawX
                    lastPointerY = event.rawY
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pointerDown) {
                        val deltaX = event.rawX - lastPointerX
                        val deltaY = event.rawY - lastPointerY
                        setPosition(xPosition + deltaX, yPosition + deltaY)
                        lastPointerX = event.rawX
                        lastPointerY = event.rawY
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerDown = false
                    return true
                }
            }
            return true
        }
    }
}
