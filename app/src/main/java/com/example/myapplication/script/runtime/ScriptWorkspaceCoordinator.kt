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
    var currentScriptId: String? = null
        private set

    var currentScriptName: String? = null
        private set

    var initialVariables: Map<String, String> = emptyMap()
        private set

    val isEmpty: Boolean
        get() = workspace.isEmpty

    fun snapshot(): List<ScriptAction> = workspace.snapshot()

    fun listSavedScripts(): List<SavedScript> = repository.list()

    fun load(id: String): SavedScript? {
        val script = repository.load(id) ?: return null
        currentScriptId = script.id
        currentScriptName = script.name
        initialVariables = script.initialVariables
        workspace.replaceAll(script.actions)
        return script
    }

    fun save(name: String): SavedScript {
        val normalizedName = name.trim()
        require(normalizedName.isNotBlank()) { "脚本名称不能为空" }
        val saved = repository.save(
            SavedScript(
                id = currentScriptId ?: UUID.randomUUID().toString(),
                name = normalizedName,
                actions = workspace.snapshot(),
                initialVariables = initialVariables
            )
        )
        currentScriptId = saved.id
        currentScriptName = saved.name
        return saved
    }

    fun delete(id: String): Boolean {
        val deleted = repository.delete(id)
        if (currentScriptId == id) {
            clearCurrentScript()
        }
        return deleted
    }

    fun replaceActions(actions: List<ScriptAction>) = workspace.replaceAll(actions)

    suspend fun run(): ActionExecutionResult = workspace.run()

    private fun clearCurrentScript() {
        currentScriptId = null
        currentScriptName = null
        initialVariables = emptyMap()
        workspace.replaceAll(emptyList())
    }
}
