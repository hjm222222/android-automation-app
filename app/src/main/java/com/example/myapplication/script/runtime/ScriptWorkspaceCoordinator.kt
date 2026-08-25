package com.example.myapplication.script.runtime

import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.model.SavedScript
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.repository.ScriptRepositoryStore
import java.util.UUID

/** Coordinates persisted script metadata and the current action workspace. */
class ScriptWorkspaceCoordinator(
    private val repository: ScriptRepositoryStore,
    private val workspace: ScriptWorkspaceController
) {
    private val sessionLock = Any()
    private var currentId: String? = null
    private var currentName: String? = null
    private var currentInitialVariables: Map<String, String> = emptyMap()

    val currentScriptId: String?
        get() = synchronized(sessionLock) { currentId }

    val currentScriptName: String?
        get() = synchronized(sessionLock) { currentName }

    val initialVariables: Map<String, String>
        get() = synchronized(sessionLock) { currentInitialVariables }

    val isEmpty: Boolean
        get() = workspace.isEmpty

    fun snapshot(): List<ScriptAction> = workspace.snapshot()

    fun listSavedScripts(): List<SavedScript> = repository.list()

    fun load(id: String): SavedScript? {
        val script = repository.load(id) ?: return null
        workspace.replaceAll(script.actions)
        synchronized(sessionLock) {
            currentId = script.id
            currentName = script.name
            currentInitialVariables = script.initialVariables.toMap()
        }
        return script
    }

    fun save(name: String): SavedScript {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "脚本名称不能为空" }
        val snapshot = executionSnapshot()
        val saved = repository.save(
            SavedScript(
                id = snapshot.scriptId ?: UUID.randomUUID().toString(),
                name = normalizedName,
                actions = snapshot.actions,
                initialVariables = snapshot.initialVariables
            )
        )
        synchronized(sessionLock) {
            currentId = saved.id
            currentName = saved.name
        }
        return saved
    }

    fun delete(id: String): Boolean {
        val deleted = repository.delete(id)
        if (deleted && currentScriptId == id) {
            clearCurrentScript()
        }
        return deleted
    }

    fun replaceActions(actions: List<ScriptAction>) = workspace.replaceAll(actions)

    fun setInitialVariables(values: Map<String, String>) {
        synchronized(sessionLock) {
            currentInitialVariables = values.toMap()
        }
    }

    fun executionSnapshot(): ScriptExecutionSnapshot {
        val actions = workspace.snapshot()
        return synchronized(sessionLock) {
            ScriptExecutionSnapshot(
                scriptId = currentId,
                scriptName = currentName,
                actions = actions,
                initialVariables = currentInitialVariables.toMap()
            )
        }
    }

    suspend fun run(): ActionExecutionResult = workspace.run(executionSnapshot())

    private fun clearCurrentScript() {
        synchronized(sessionLock) {
            currentId = null
            currentName = null
            currentInitialVariables = emptyMap()
        }
        workspace.replaceAll(emptyList())
    }
}
