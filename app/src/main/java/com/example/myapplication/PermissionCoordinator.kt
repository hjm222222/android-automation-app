package com.example.myapplication

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity

class PermissionCoordinator(
    private val activity: ComponentActivity
) {
    private var screenCaptureGranted = false
    private var screenCaptureResultCodeValue: Int? = null
    private var screenCaptureDataValue: Intent? = null

    val screenCaptureResultCode: Int?
        get() = screenCaptureResultCodeValue

    val screenCaptureData: Intent?
        get() = screenCaptureDataValue

    fun refreshState(): PermissionState = PermissionState(
        overlayGranted = Settings.canDrawOverlays(activity),
        accessibilityGranted = isAccessibilityServiceEnabled(),
        screenCaptureGranted = screenCaptureGranted
    )

    fun screenCaptureIntent(): Intent {
        val manager = activity.getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    fun acceptScreenCaptureResult(resultCode: Int, data: Intent?): Boolean {
        screenCaptureResultCodeValue = resultCode
        screenCaptureDataValue = data
        screenCaptureGranted = resultCode == android.app.Activity.RESULT_OK && data != null
        return screenCaptureGranted
    }

    fun requestOverlay() {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
        )
    }

    fun requestAccessibility() {
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { serviceInfo ->
                serviceInfo.resolveInfo.serviceInfo?.let { serviceInfoData ->
                    ComponentName(activity, AutomationAccessibilityService::class.java) ==
                        ComponentName(serviceInfoData.packageName, serviceInfoData.name)
                } == true
            }
    }
}

data class PermissionState(
    val overlayGranted: Boolean,
    val accessibilityGranted: Boolean,
    val screenCaptureGranted: Boolean = false
)

const val TAG = "SC_DEBUG"
