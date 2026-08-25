package com.example.myapplication

import com.example.myapplication.script.model.SavedScript
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.repository.ScriptRepositoryStore
import com.example.myapplication.script.runtime.ScriptWorkspaceController
import com.example.myapplication.script.runtime.ScriptWorkspaceCoordinator
import org.junit.Assert.assertEquals
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

    private class InMemoryScriptRepository : ScriptRepositoryStore {
        private val scripts = linkedMapOf<String, SavedScript>()

        override fun list(): List<SavedScript> = scripts.values.toList()

        override fun load(id: String): SavedScript? = scripts[id]

        override fun save(script: SavedScript): SavedScript {
            scripts[script.id] = script
            return script
        }

        override fun delete(id: String): Boolean = scripts.remove(id) != null
    }
}
