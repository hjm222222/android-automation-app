package com.example.myapplication.script.repository

import com.example.myapplication.script.model.SavedScript

interface ScriptRepositoryStore {
    fun list(): List<SavedScript>
    fun load(id: String): SavedScript?
    fun save(script: SavedScript): SavedScript
    fun delete(id: String): Boolean
}
