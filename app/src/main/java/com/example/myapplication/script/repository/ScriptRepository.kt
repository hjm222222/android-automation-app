package com.example.myapplication.script.repository

import android.content.Context
import com.example.myapplication.script.model.SavedScript
import java.io.File
import java.util.UUID

class ScriptRepository(context: Context) : ScriptRepositoryStore {
    private val directory = File(context.applicationContext.filesDir, "scripts").apply { mkdirs() }

    @Synchronized override fun list(): List<SavedScript> = directory.listFiles { file -> file.extension == "json" }
        ?.mapNotNull { file -> runCatching { ScriptJsonCodec.decode(file.readText()) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt }
        .orEmpty()

    @Synchronized override fun load(id: String): SavedScript? = fileFor(id).takeIf { it.isFile }
        ?.let { runCatching { ScriptJsonCodec.decode(it.readText()) }.getOrNull() }

    @Synchronized override fun save(script: SavedScript): SavedScript {
        require(script.id.isNotBlank() && script.name.isNotBlank())
        val saved = script.copy(updatedAt = System.currentTimeMillis())
        fileFor(saved.id).writeText(ScriptJsonCodec.encode(saved))
        return saved
    }

    @Synchronized fun save(name: String, actions: List<com.example.myapplication.script.model.ScriptAction>, initialVariables: Map<String, String>): SavedScript =
        save(SavedScript(UUID.randomUUID().toString(), name.trim(), actions, initialVariables))

    @Synchronized override fun delete(id: String): Boolean = fileFor(id).delete()

    private fun fileFor(id: String): File = File(directory, "${id.replace(Regex("[^A-Za-z0-9._-]"), "_")}.json")
}
