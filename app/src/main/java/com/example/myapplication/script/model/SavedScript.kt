package com.example.myapplication.script.model

data class SavedScript(
    val id: String,
    val name: String,
    val actions: List<ScriptAction>,
    val initialVariables: Map<String, String> = emptyMap(),
    val version: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)
