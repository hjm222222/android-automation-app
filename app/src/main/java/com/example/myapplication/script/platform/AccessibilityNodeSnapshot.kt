package com.example.myapplication.script.platform

import android.graphics.Rect

data class AccessibilityNodeSnapshot(
    val boundsInScreen: Rect,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val packageName: String?,
    val isClickable: Boolean,
    val isEnabled: Boolean,
    val depth: Int
) {
    fun toSelector(): AccessibilityNodeSelector = AccessibilityNodeSelector(
        text = text,
        contentDescription = contentDescription,
        resourceId = resourceId,
        className = className,
        packageName = packageName
    )
}

data class AccessibilityNodeSelector(
    val text: String? = null,
    val contentDescription: String? = null,
    val resourceId: String? = null,
    val className: String? = null,
    val packageName: String? = null,
    val occurrence: Int = 0
) {
    fun matches(node: AccessibilityNodeSnapshot): Boolean =
        (text == null || text == node.text) &&
            (contentDescription == null || contentDescription == node.contentDescription) &&
            (resourceId == null || resourceId == node.resourceId) &&
            (className == null || className == node.className) &&
            (packageName == null || packageName == node.packageName)
}

interface AccessibilityNodeProvider {
    fun snapshotCurrentWindow(): List<AccessibilityNodeSnapshot> = emptyList()
}
