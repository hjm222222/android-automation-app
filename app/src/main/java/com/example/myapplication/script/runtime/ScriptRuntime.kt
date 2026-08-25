package com.example.myapplication.script.runtime

import com.example.myapplication.script.platform.AccessibilityController
import com.example.myapplication.script.platform.ApplicationController
import com.example.myapplication.script.platform.VisionController

class ScriptRuntime(
    val accessibilityController: AccessibilityController? = null,
    val applicationController: ApplicationController? = null,
    val visionController: VisionController? = null,
    initialVariables: Map<String, String> = emptyMap()
) {
    private val variables = initialVariables.toMutableMap()
    var lastVisionMatch: VisionMatch? = null
        private set
    var lastOcrText: String? = null
        private set

    fun recordVisionMatch(x: Int, y: Int) {
        lastVisionMatch = VisionMatch(x, y)
    }

    fun recordOcrText(text: String) {
        lastOcrText = text
    }

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

data class VisionMatch(val x: Int, val y: Int)
