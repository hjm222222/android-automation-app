package com.example.myapplication.script.repository

import com.example.myapplication.script.model.SavedScript

interface ScriptRepositoryStore {
    fun list(): List<SavedScript>
    fun load(id: String): SavedScript?
    fun save(script: SavedScript): SavedScript
    fun delete(id: String): Boolean

    fun saveDraft(script: SavedScript): SavedScript = save(script.copy(id = "__draft__", name = "草稿"))
    fun loadDraft(): SavedScript? = load("__draft__")
    fun deleteDraft(): Boolean = delete("__draft__")
}
