package com.example.myapplication.script.platform

/**
 * 脚本可以使用的无障碍手势能力。
 *
 * 动作 Handler 只依赖这个接口，不直接依赖 Android Service。
 * 这样坐标点击、长按和双击的业务逻辑可以独立测试，系统实现集中在平台层。
 */
interface AccessibilityController : AccessibilityNodeProvider {
    suspend fun press(x: Int, y: Int, durationMillis: Long): Boolean

    suspend fun clickNode(selector: AccessibilityNodeSelector): Boolean = false

    suspend fun doublePress(x: Int, y: Int, durationMillis: Long): Boolean

    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMillis: Long
    ): Boolean

    /** 当前无障碍窗口所属包名，无法读取时返回 null。 */
    fun currentPackageName(): String? = null

    /** 将文字写入当前获得输入焦点的无障碍节点。 */
    suspend fun setFocusedText(text: String): Boolean

    suspend fun performGlobalAction(action: Int): Boolean
}
