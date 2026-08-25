package com.example.myapplication

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.myapplication.script.platform.AccessibilityController
import com.example.myapplication.script.platform.AccessibilityGestureDispatcher

/**
 * 系统无障碍服务的生命周期入口。
 *
 * 具体手势通过 controller 暴露给动作执行层，动作 Handler 不直接持有这个 Service。
 */
class AutomationAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        registerController(
            service = this,
            newController = AccessibilityGestureDispatcher(this)
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        var controller: AccessibilityController? = null
            private set

        @Volatile
        private var connectedService: AutomationAccessibilityService? = null

        fun registerController(
            service: AutomationAccessibilityService,
            newController: AccessibilityController
        ) {
            connectedService = service
            controller = newController
        }

        fun clearController(service: AutomationAccessibilityService) {
            if (connectedService === service) {
                controller = null
                connectedService = null
            }
        }
    }

    override fun onDestroy() {
        clearController(this)
        super.onDestroy()
    }
}
