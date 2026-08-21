package com.example.myapplication

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var permissionCoordinator: PermissionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionCoordinator = PermissionCoordinator(
            activity = this,
            initialScreenCaptureGranted = savedInstanceState?.getBoolean(KEY_GRANTED) == true,
            initialScreenCaptureResultCode = savedInstanceState?.getInt(EXTRA_RESULT_CODE) ?: 0,
            initialScreenCaptureData = savedInstanceState?.let { state ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    state.getParcelable(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    state.getParcelable(EXTRA_RESULT_DATA)
                }
            },
            onScreenCaptureResult = viewModel::onScreenCaptureResult
        )
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                HomeRoute(
                    viewModel = viewModel,
                    onRequestOverlay = permissionCoordinator::requestOverlay,
                    onRequestScreenCapture = permissionCoordinator::requestScreenCapture,
                    onRequestAccessibility = permissionCoordinator::requestAccessibility,
                    onOpenFloatingWorkspace = ::openFloatingWorkspace
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        permissionCoordinator.saveScreenCaptureState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState(permissionCoordinator.refreshState())
    }

    private fun openFloatingWorkspace() {
        val serviceIntent = Intent(this, FloatingWorkspaceService::class.java)
        permissionCoordinator.addScreenCaptureData(serviceIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        finishAndRemoveTask()
    }

}
