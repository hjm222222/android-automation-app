package com.example.myapplication

import com.example.myapplication.script.model.SavedScript
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.action.ScriptActionHandler
import com.example.myapplication.script.repository.ScriptRepositoryStore
import com.example.myapplication.script.runtime.ScriptRunner
import com.example.myapplication.script.runtime.ScriptRuntime
import com.example.myapplication.script.runtime.ScriptWorkspaceController
import com.example.myapplication.script.runtime.ScriptWorkspaceCoordinator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptWorkspaceCoordinatorTest {
    @Test
    fun saveAndLoadKeepCurrentScriptStateAndActions() {
        val repository = InMemoryScriptRepository()
        val workspace = ScriptWorkspaceController()
        val coordinator = ScriptWorkspaceCoordinator(repository, workspace)
        val action = ScriptAction(ActionType.WAIT, id = "wait-1")
        workspace.add(action)

        val saved = coordinator.save("  示例脚本  ")
        workspace.replaceAll(emptyList())

        val loaded = coordinator.load(saved.id)

        assertEquals(saved, loaded)
        assertEquals(saved.id, coordinator.currentScriptId)
        assertEquals("示例脚本", coordinator.currentScriptName)
        assertEquals(emptyMap<String, String>(), coordinator.initialVariables)
        assertEquals(listOf(action), coordinator.snapshot())
    }

    @Test
    fun listSavedScriptsDelegatesToRepository() {
        val repository = InMemoryScriptRepository()
        val script = SavedScript("script-1", "脚本", emptyList())
        repository.save(script)
        val coordinator = ScriptWorkspaceCoordinator(repository, ScriptWorkspaceController())

        assertEquals(listOf(script), coordinator.listSavedScripts())
    }

    @Test
    fun deletingCurrentScriptClearsCurrentStateAndWorkspace() {
        val repository = InMemoryScriptRepository()
        val workspace = ScriptWorkspaceController()
        val coordinator = ScriptWorkspaceCoordinator(repository, workspace)
        val saved = coordinator.save("待删除")
        workspace.add(ScriptAction(ActionType.WAIT, id = "wait-1"))
        coordinator.load(saved.id)

        assertTrue(coordinator.delete(saved.id))

        assertNull(coordinator.currentScriptId)
        assertNull(coordinator.currentScriptName)
        assertTrue(coordinator.initialVariables.isEmpty())
        assertTrue(coordinator.isEmpty)
        assertNull(repository.load(saved.id))
    }

    @Test
    fun deletingCurrentScriptKeepsWorkspaceWhenRepositoryDeleteFails() {
        val repository = InMemoryScriptRepository(deleteResult = false)
        val workspace = ScriptWorkspaceController()
        val coordinator = ScriptWorkspaceCoordinator(repository, workspace)
        val action = ScriptAction(ActionType.WAIT, id = "wait-1")
        workspace.add(action)
        val saved = coordinator.save("待保留")

        assertFalse(coordinator.delete(saved.id))
        assertEquals(saved.id, coordinator.currentScriptId)
        assertEquals("待保留", coordinator.currentScriptName)
        assertEquals(listOf(action), coordinator.snapshot())
    }

    @Test
    fun loadingMissingScriptKeepsCurrentWorkspaceState() {
        val repository = InMemoryScriptRepository()
        val workspace = ScriptWorkspaceController()
        val coordinator = ScriptWorkspaceCoordinator(repository, workspace)
        val action = ScriptAction(ActionType.WAIT, id = "wait-1")
        workspace.add(action)
        val saved = coordinator.save("现有脚本")

        assertNull(coordinator.load("missing"))
        assertEquals(saved.id, coordinator.currentScriptId)
        assertEquals("现有脚本", coordinator.currentScriptName)
        assertEquals(listOf(action), coordinator.snapshot())
    }

    @Test
    fun runningScriptUsesSnapshotBeforeLaterWorkspaceEdits() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var variableDuringRun: String? = null
        val handler = object : ScriptActionHandler {
            override fun createDefault(): ScriptAction = ScriptAction(ActionType.WAIT)

            override suspend fun execute(action: ScriptAction, runtime: ScriptRuntime): ActionExecutionResult {
                variableDuringRun = runtime.getVariable("mode")
                started.complete(Unit)
                release.await()
                return ActionExecutionResult.Success
            }
        }
        val workspace = ScriptWorkspaceController(
            ScriptRunner(handlerResolver = { handler })
        )
        val coordinator = ScriptWorkspaceCoordinator(InMemoryScriptRepository(), workspace)
        coordinator.setInitialVariables(mapOf("mode" to "saved"))
        workspace.add(ScriptAction(ActionType.WAIT, id = "wait-1"))

        val result = async { coordinator.run() }
        started.await()
        coordinator.setInitialVariables(mapOf("mode" to "edited"))
        coordinator.replaceActions(emptyList())
        release.complete(Unit)

        assertEquals(ActionExecutionResult.Success, result.await())
        assertEquals("saved", variableDuringRun)
    }

    private class InMemoryScriptRepository(
        private val deleteResult: Boolean = true
    ) : ScriptRepositoryStore {
        private val scripts = linkedMapOf<String, SavedScript>()

        override fun list(): List<SavedScript> = scripts.values.toList()

        override fun load(id: String): SavedScript? = scripts[id]

        override fun save(script: SavedScript): SavedScript {
            scripts[script.id] = script
            return script
        }

        override fun delete(id: String): Boolean = deleteResult && scripts.remove(id) != null
    }
}
