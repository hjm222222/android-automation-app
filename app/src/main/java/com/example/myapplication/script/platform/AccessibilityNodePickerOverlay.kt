package com.example.myapplication.script.platform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 当前窗口的无障碍控件框选层。
 * 节点快照在显示前取得，避免悬浮层出现后改变当前活动窗口。
 */
class AccessibilityNodePickerOverlay(
    private val context: Context,
    private val windowManager: WindowManager,
    nodes: List<AccessibilityNodeSnapshot>,
    private val onConfirmed: (AccessibilityNodeSnapshot) -> Unit,
    private val onCancelled: () -> Unit
) {
    private val selectableNodes = nodes
        .filter { it.isEnabled && it.boundsInScreen.width() > 0 && it.boundsInScreen.height() > 0 }
        .sortedWith(compareBy<AccessibilityNodeSnapshot> { it.boundsInScreen.width() * it.boundsInScreen.height() }.thenByDescending { it.depth })
    private val root = FrameLayout(context)
    private val overlay = NodeBoundsView(context)
    private var selected: AccessibilityNodeSnapshot? = null
    private var shown = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    fun show() {
        if (shown) return
        shown = true
        root.setBackgroundColor(Color.argb(32, 255, 248, 210))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(context).apply {
            text = "点击淡黄色方框选择控件"
            textSize = 14f
            setTextColor(Color.rgb(72, 62, 47))
            setPadding(24, 16, 24, 16)
        }
        root.addView(title, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        })
        val actions = FrameLayout(context).apply {
            val cancel = button("取消") { cancel() }
            val confirm = button("确认选择") { confirm() }
            addView(cancel, FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.START or Gravity.BOTTOM })
            addView(confirm, FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END or Gravity.BOTTOM })
        }
        root.addView(actions, FrameLayout.LayoutParams(-1, -1))
        overlay.onSelected = { node ->
            selected = node
            overlay.selected = node
            overlay.invalidate()
        }
        windowManager.addView(root, layoutParams)
    }

    fun dismiss() {
        if (!shown) return
        shown = false
        windowManager.removeView(root)
    }

    private fun confirm() {
        val node = selected ?: return
        dismiss()
        onConfirmed(node)
    }

    private fun cancel() {
        dismiss()
        onCancelled()
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(context).apply {
        text = label
        contentDescription = label
        minHeight = 48
        background = GradientDrawable().apply {
            setColor(Color.rgb(244, 211, 115))
            cornerRadius = 18f
        }
        setOnClickListener { onClick() }
    }

    private inner class NodeBoundsView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var selected: AccessibilityNodeSnapshot? = null
        var onSelected: ((AccessibilityNodeSnapshot) -> Unit)? = null

        override fun onDraw(canvas: Canvas) {
            selectableNodes.forEach { node ->
                paint.style = Paint.Style.FILL
                paint.color = if (node == selected) Color.argb(105, 255, 214, 70) else Color.argb(55, 255, 248, 170)
                canvas.drawRect(node.boundsInScreen, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (node == selected) 5f else 2f
                paint.color = Color.rgb(238, 190, 56)
                canvas.drawRect(node.boundsInScreen, paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked != MotionEvent.ACTION_UP) return true
            val hit = selectableNodes
                .filter { it.boundsInScreen.contains(event.rawX.toInt(), event.rawY.toInt()) }
                .minWithOrNull(compareBy<AccessibilityNodeSnapshot> { it.boundsInScreen.width() * it.boundsInScreen.height() }.thenByDescending { it.depth })
            if (hit != null) onSelected?.invoke(hit)
            return true
        }
    }
}
