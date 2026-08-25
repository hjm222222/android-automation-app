package com.example.myapplication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionUiState(
    val overlayGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val screenCaptureGranted: Boolean = false
) {
    val allGranted: Boolean
        get() = overlayGranted && accessibilityGranted && screenCaptureGranted
}

enum class WorkspaceStatus {
    NEEDS_PERMISSION,
    PREPARING,
    READY
}

data class MainUiState(
    val permissions: PermissionUiState = PermissionUiState(),
    val workspaceStatus: WorkspaceStatus = WorkspaceStatus.NEEDS_PERMISSION
) {
    val allPermissionsGranted: Boolean
        get() = permissions.allGranted
}

sealed interface MainEvent {
    data object ShakePermissionCard : MainEvent
    data object OpenFloatingWorkspace : MainEvent
}

class MainViewModel(
    private val workspacePreparationDelayMs: Long = WORKSPACE_PREPARATION_DELAY_MS
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val eventsChannel = Channel<MainEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private var workspacePreparationJob: Job? = null

    fun refreshPermissionState(permissionState: PermissionState) {
        val permissions = PermissionUiState(
            overlayGranted = permissionState.overlayGranted,
            accessibilityGranted = permissionState.accessibilityGranted,
            screenCaptureGranted = permissionState.screenCaptureGranted
        )

        _uiState.update { current ->
            current.copy(
                permissions = permissions,
                workspaceStatus = if (permissions.allGranted) {
                    current.workspaceStatus
                } else {
                    WorkspaceStatus.NEEDS_PERMISSION
                }
            )
        }

        if (permissions.allGranted) {
            prepareWorkspaceIfNeeded()
        } else {
            workspacePreparationJob?.cancel()
            workspacePreparationJob = null
        }
    }

    fun onAddClicked() {
        when (_uiState.value.workspaceStatus) {
            WorkspaceStatus.READY -> eventsChannel.trySend(MainEvent.OpenFloatingWorkspace)
            WorkspaceStatus.NEEDS_PERMISSION -> eventsChannel.trySend(MainEvent.ShakePermissionCard)
            WorkspaceStatus.PREPARING -> Unit
        }
    }

    private fun prepareWorkspaceIfNeeded() {
        if (workspacePreparationJob?.isActive == true ||
            _uiState.value.workspaceStatus == WorkspaceStatus.READY
        ) {
            return
        }

        _uiState.update { it.copy(workspaceStatus = WorkspaceStatus.PREPARING) }
        workspacePreparationJob = viewModelScope.launch {
            delay(workspacePreparationDelayMs)
            if (_uiState.value.allPermissionsGranted) {
                _uiState.update { it.copy(workspaceStatus = WorkspaceStatus.READY) }
            }
        }
    }

    private companion object {
        const val WORKSPACE_PREPARATION_DELAY_MS = 3_000L
    }
}
