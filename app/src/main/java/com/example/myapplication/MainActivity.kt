package com.example.myapplication

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var permissionCoordinator: PermissionCoordinator
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        permissionCoordinator.acceptScreenCaptureResult(result.resultCode, result.data)
        viewModel.refreshPermissionState(permissionCoordinator.refreshState())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionCoordinator = PermissionCoordinator(activity = this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                HomeRoute(
                    viewModel = viewModel,
                    onRequestOverlay = permissionCoordinator::requestOverlay,
                    onRequestAccessibility = permissionCoordinator::requestAccessibility,
                    onRequestScreenCapture = { screenCaptureLauncher.launch(permissionCoordinator.screenCaptureIntent()) },
                onOpenFloatingWorkspace = ::openFloatingWorkspace
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState(permissionCoordinator.refreshState())
    }

    private fun openFloatingWorkspace() {
        val serviceIntent = Intent(this, FloatingWorkspaceService::class.java).apply {
            permissionCoordinator.screenCaptureResultCode?.let { code ->
                putExtra(FloatingWorkspaceService.EXTRA_SCREEN_CAPTURE_RESULT_CODE, code)
            }
            permissionCoordinator.screenCaptureData?.let { data ->
                putExtra(FloatingWorkspaceService.EXTRA_SCREEN_CAPTURE_DATA, data)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        finishAndRemoveTask()
    }
}
