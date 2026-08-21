package com.example.myapplication.script.runtime

class ScriptRuntime {
    private val variables = mutableMapOf<String, String>()

    fun createVariable(name: String, value: String): Boolean {
        if (name.isBlank() || variables.containsKey(name)) return false
        variables[name] = value
        return true
    }

    fun setVariable(name: String, value: String): Boolean {
        if (!variables.containsKey(name)) return false
        variables[name] = value
        return true
    }

    fun getVariable(name: String): String? = variables[name]
}
