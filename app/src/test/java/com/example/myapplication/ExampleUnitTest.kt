package com.example.myapplication

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.myapplication.script.model.ActionCondition
import com.example.myapplication.script.model.JudgementCondition
import com.example.myapplication.script.model.VariableComparisonOperator
import com.example.myapplication.script.runtime.ActionConditionEvaluator
import com.example.myapplication.script.runtime.ScriptRuntime

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultState_requiresPermissions() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = MainViewModel()

        assertFalse(viewModel.uiState.value.allPermissionsGranted)
        assertEquals(
            WorkspaceStatus.NEEDS_PERMISSION,
            viewModel.uiState.value.workspaceStatus
        )
    }

    @Test
    fun allPermissionsGranted_preparesWorkspace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = MainViewModel(workspacePreparationDelayMs = 0)

        viewModel.refreshPermissionState(PermissionState(true, true, true))
        assertEquals(
            WorkspaceStatus.PREPARING,
            viewModel.uiState.value.workspaceStatus
        )

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.allPermissionsGranted)
        assertEquals(
            WorkspaceStatus.READY,
            viewModel.uiState.value.workspaceStatus
        )
    }

    @Test
    fun permissionRevoked_returnsToPermissionPreparation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = MainViewModel(workspacePreparationDelayMs = 0)

        viewModel.refreshPermissionState(PermissionState(true, true, true))
        advanceUntilIdle()
        viewModel.refreshPermissionState(PermissionState(false, true, true))

        assertEquals(
            WorkspaceStatus.NEEDS_PERMISSION,
            viewModel.uiState.value.workspaceStatus
        )
    }

    @Test
    fun addClickWithoutPermissions_emitsShakeEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = MainViewModel()
        val event = CompletableDeferred<MainEvent>()
        backgroundScope.launch {
            viewModel.events.take(1).collect { event.complete(it) }
        }
        runCurrent()

        viewModel.onAddClicked()

        assertEquals(MainEvent.ShakePermissionCard, event.await())
    }

    @Test
    fun addClickWhilePreparing_doesNotOpenWorkspace() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = MainViewModel(workspacePreparationDelayMs = 3_000)
        viewModel.refreshPermissionState(PermissionState(true, true, true))
        val event = CompletableDeferred<MainEvent>()
        backgroundScope.launch {
            viewModel.events.collect { event.complete(it) }
        }

        viewModel.onAddClicked()

        assertFalse(event.isCompleted)
    }

    @Test
    fun addClickWithPermissions_emitsOpenWorkspaceEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = MainViewModel(workspacePreparationDelayMs = 0)
        viewModel.refreshPermissionState(PermissionState(true, true, true))
        advanceUntilIdle()
        val event = CompletableDeferred<MainEvent>()
        backgroundScope.launch {
            viewModel.events.take(1).collect { event.complete(it) }
        }
        runCurrent()

        viewModel.onAddClicked()

        assertEquals(MainEvent.OpenFloatingWorkspace, event.await())
    }

    @Test
    fun variableJudgement_supportsIntEqualsAndLessThanOrEquals() {
        val runtime = ScriptRuntime()
        assertTrue(runtime.createVariable("a", "0"))

        assertTrue(
            ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(
                    JudgementCondition.Variable(
                        variableName = "a",
                        operator = VariableComparisonOperator.EQUALS,
                        expectedValue = "0"
                    )
                ),
                runtime
            )
        )
        assertTrue(
            ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(
                    JudgementCondition.Variable(
                        variableName = "a",
                        operator = VariableComparisonOperator.LESS_THAN_OR_EQUALS,
                        expectedValue = "0"
                    )
                ),
                runtime
            )
        )
    }

    @Test
    fun variableJudgement_rejectsNonIntValues() {
        val runtime = ScriptRuntime()
        assertTrue(runtime.createVariable("a", "1.5"))

        assertFalse(
            ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(JudgementCondition.Variable()),
                runtime
            )
        )
    }

    @Test
    fun addClickBeforeEventCollection_deliversPendingWorkspaceEvent() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = MainViewModel(workspacePreparationDelayMs = 0)
        viewModel.refreshPermissionState(PermissionState(true, true, true))
        advanceUntilIdle()

        viewModel.onAddClicked()

        assertEquals(
            MainEvent.OpenFloatingWorkspace,
            viewModel.events.take(1).first()
        )
    }
}
