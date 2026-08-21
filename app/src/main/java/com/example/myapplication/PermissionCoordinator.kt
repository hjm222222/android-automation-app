package com.example.myapplication

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class PermissionCoordinator(
    private val activity: ComponentActivity,
    initialScreenCaptureGranted: Boolean = false,
    initialScreenCaptureResultCode: Int = 0,
    initialScreenCaptureData: Intent? = null,
    private val onScreenCaptureResult: (Boolean) -> Unit
) {
    private var screenCaptureGranted = initialScreenCaptureGranted && initialScreenCaptureData != null
    private var screenCaptureResultCode = initialScreenCaptureResultCode
    private var screenCaptureData: Intent? = initialScreenCaptureData

    private val screenCaptureLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        screenCaptureGranted = result.resultCode == ComponentActivity.RESULT_OK && result.data != null
        screenCaptureResultCode = if (screenCaptureGranted) result.resultCode else 0
        screenCaptureData = if (screenCaptureGranted) result.data else null
        onScreenCaptureResult(screenCaptureGranted)
    }

    fun refreshState(): PermissionState = PermissionState(
        overlayGranted = Settings.canDrawOverlays(activity),
        screenCaptureGranted = screenCaptureGranted,
        accessibilityGranted = isAccessibilityServiceEnabled()
    )

    fun requestOverlay() {
        activity.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
        )
    }

    fun requestScreenCapture() {
        val manager = activity.getSystemService(MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    fun requestAccessibility() {
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun addScreenCaptureData(intent: Intent) {
        if (!screenCaptureGranted || screenCaptureData == null) return
        intent.putExtra(EXTRA_RESULT_CODE, screenCaptureResultCode)
        intent.putExtra(EXTRA_RESULT_DATA, screenCaptureData)
    }

    fun saveScreenCaptureState(outState: android.os.Bundle) {
        outState.putBoolean(KEY_GRANTED, screenCaptureGranted)
        outState.putInt(EXTRA_RESULT_CODE, screenCaptureResultCode)
        screenCaptureData?.let { outState.putParcelable(EXTRA_RESULT_DATA, it) }
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
    val screenCaptureGranted: Boolean,
    val accessibilityGranted: Boolean
)

const val KEY_GRANTED = "screen_capture_granted"
const val EXTRA_RESULT_CODE = "screen_capture_result_code"
const val EXTRA_RESULT_DATA = "screen_capture_result_data"
