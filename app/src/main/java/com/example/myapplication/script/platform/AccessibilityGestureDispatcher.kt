package com.example.myapplication.script.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Android 无障碍手势的系统实现。 */
class AccessibilityGestureDispatcher(
    private val service: AccessibilityService
) : AccessibilityController {
    override suspend fun press(x: Int, y: Int, durationMillis: Long): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatch(path, durationMillis.coerceAtLeast(1L))
    }

    override suspend fun clickNode(selector: AccessibilityNodeSelector): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return try {
            val nodes = findMatchingNodes(root, selector)
            try {
                val target = nodes.getOrNull(selector.occurrence) ?: return false
                clickNodeOrClickableParent(target)
            } finally {
                nodes.forEach { it.recycle() }
            }
        } finally {
            root.recycle()
        }
    }

    override suspend fun doublePress(x: Int, y: Int, durationMillis: Long): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val first = dispatch(path, durationMillis.coerceAtLeast(1L))
        if (!first) return false
        kotlinx.coroutines.delay(80L)
        return dispatch(path, durationMillis.coerceAtLeast(1L))
    }

    override suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        return dispatch(path, durationMillis.coerceAtLeast(1L))
    }

    override suspend fun performGlobalAction(action: Int): Boolean =
        service.performGlobalAction(action)

    override fun currentPackageName(): String? = service.rootInActiveWindow?.packageName?.toString()

    override fun snapshotCurrentWindow(): List<AccessibilityNodeSnapshot> {
        val root = service.rootInActiveWindow ?: return emptyList()
        return try {
            buildSnapshots(root)
        } finally {
            root.recycle()
        }
    }

    override suspend fun setFocusedText(text: String): Boolean {
        val node = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        return try {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
            )
        } finally {
            node.recycle()
        }
    }

    private fun findMatchingNodes(
        node: AccessibilityNodeInfo,
        selector: AccessibilityNodeSelector,
        result: MutableList<AccessibilityNodeInfo> = mutableListOf()
    ): List<AccessibilityNodeInfo> {
        val snapshot = AccessibilityNodeSnapshot(
            boundsInScreen = android.graphics.Rect().also { node.getBoundsInScreen(it) },
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            resourceId = node.viewIdResourceName,
            className = node.className?.toString(),
            packageName = node.packageName?.toString(),
            isClickable = node.isClickable,
            isEnabled = node.isEnabled,
            depth = 0
        )
        if (node.isVisibleToUser && selector.matches(snapshot)) {
            result += AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                findMatchingNodes(child, selector, result)
            } finally {
                child.recycle()
            }
        }
        return result
    }

    private fun clickNodeOrClickableParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val parent = node.parent ?: return false
        return try {
            clickNodeOrClickableParent(parent)
        } finally {
            parent.recycle()
        }
    }

    private fun buildSnapshots(
        node: AccessibilityNodeInfo,
        depth: Int = 0
    ): List<AccessibilityNodeSnapshot> {
        val snapshots = mutableListOf<AccessibilityNodeSnapshot>()
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && node.isVisibleToUser) {
            snapshots += AccessibilityNodeSnapshot(
                boundsInScreen = android.graphics.Rect(bounds),
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                resourceId = node.viewIdResourceName,
                className = node.className?.toString(),
                packageName = node.packageName?.toString(),
                isClickable = node.isClickable,
                isEnabled = node.isEnabled,
                depth = depth
            )
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                snapshots += buildSnapshots(child, depth + 1)
            } finally {
                child.recycle()
            }
        }
        return snapshots
    }

    private suspend fun dispatch(path: Path, durationMillis: Long): Boolean =
        suspendCancellableCoroutine { continuation ->
            val stroke = GestureDescription.StrokeDescription(
                path,
                0L,
                durationMillis
            )
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null
            )
            if (!dispatched && continuation.isActive) continuation.resume(false)
            continuation.invokeOnCancellation { SystemClock.uptimeMillis() }
        }
}
