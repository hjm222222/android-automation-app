package com.example.myapplication.script.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager

data class OverlayPageAnchor(
    val x: Int,
    val y: Int,
    val width: Int,
    val screenWidth: Int,
    val screenHeight: Int
)

/** 管理工作区附属页面的显示、定位和关闭。 */
class OverlayPageCoordinator(
    private val context: Context,
    private val windowManager: WindowManager,
    private val isDestroyed: () -> Boolean,
    private val anchorProvider: () -> OverlayPageAnchor,
    private val dp: (Int) -> Int
) {
    private var pageView: View? = null
    private var pageLayoutParams: WindowManager.LayoutParams? = null
    private var followsWorkspace = false

    val isShowing: Boolean
        get() = pageView != null

    fun show(
        view: View,
        width: Int,
        height: Int,
        focusable: Boolean,
        followWorkspace: Boolean = false
    ) {
        if (isDestroyed()) return
        dismiss()
        pageView = view
        followsWorkspace = followWorkspace
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
        updatePosition()
        try {
            windowManager.addView(view, pageLayoutParams)
        } catch (_: RuntimeException) {
            clearPage()
            return
        }
        view.post {
            if (!isDestroyed() && view === pageView && view.isAttachedToWindow) updatePosition()
        }
        if (focusable) view.requestFocus()
    }

    fun dismiss() {
        val view = pageView
        clearPage()
        if (view?.isAttachedToWindow == true) runCatching { windowManager.removeView(view) }
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    fun updatePosition() {
        val params = pageLayoutParams ?: return
        val view = pageView ?: return
        if (followsWorkspace) {
            val anchor = anchorProvider()
            val pageWidth = view.width.takeIf { it > 0 } ?: params.width
            val pageHeight = view.height.takeIf { it > 0 } ?: params.height
            val gap = dp(3)
            val rightAlignedX = anchor.x + anchor.width + gap
            val leftAlignedX = anchor.x - pageWidth - gap
            val maxPageX = (anchor.screenWidth - pageWidth).coerceAtLeast(0)
            params.x = if (rightAlignedX + pageWidth <= anchor.screenWidth) {
                rightAlignedX
            } else {
                leftAlignedX.coerceIn(0, maxPageX)
            }
            params.y = anchor.y.coerceIn(0, (anchor.screenHeight - pageHeight).coerceAtLeast(0))
        }
        if (view.isAttachedToWindow) runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun clearPage() {
        pageView = null
        pageLayoutParams = null
        followsWorkspace = false
    }
}
