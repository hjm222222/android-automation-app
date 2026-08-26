package com.example.myapplication

import android.graphics.Bitmap
import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.action.OcrTextActionHandler
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.platform.AccessibilityController
import com.example.myapplication.script.platform.AccessibilityNodeSelector
import com.example.myapplication.script.platform.ScreenCapture
import com.example.myapplication.script.platform.TemplateMatch
import com.example.myapplication.script.platform.VisionController
import com.example.myapplication.script.registry.ActionRegistry
import com.example.myapplication.script.runtime.ScriptRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptActionIntegrationTest {
    @Test
    fun scriptExecutesInputGestureVisionAndOcrActions() = runBlocking {
        val accessibility = RecordingAccessibilityController()
        val vision = RecordingVisionController()
        val runner = ScriptRunner(
            accessibilityControllerProvider = { accessibility },
            visionControllerProvider = { vision },
            handlerResolver = { type ->
                if (type == ActionType.OCR_TEXT) {
                    OcrTextActionHandler { Result.success("自动化测试") }
                } else {
                    ActionRegistry.handlerFor(type)
                }
            }
        )
        val actions = listOf(
            ScriptAction(ActionType.CLICK, parameters = mapOf(ActionParameterKey.X to "10", ActionParameterKey.Y to "20")),
            ScriptAction(ActionType.LONG_CLICK, parameters = mapOf(ActionParameterKey.X to "30", ActionParameterKey.Y to "40", ActionParameterKey.DURATION_MILLIS to "500")),
            ScriptAction(ActionType.DOUBLE_CLICK, parameters = mapOf(ActionParameterKey.X to "50", ActionParameterKey.Y to "60")),
            ScriptAction(ActionType.SWIPE, parameters = mapOf(ActionParameterKey.START_X to "1", ActionParameterKey.START_Y to "2", ActionParameterKey.END_X to "100", ActionParameterKey.END_Y to "200", ActionParameterKey.DURATION_MILLIS to "300")),
            ScriptAction(ActionType.INPUT_TEXT, parameters = mapOf(ActionParameterKey.TEXT to "hello")),
            ScriptAction(ActionType.CLICK_NODE, parameters = mapOf(ActionParameterKey.NODE_TEXT to "登录")),
            ScriptAction(ActionType.CLICK_IMAGE, parameters = mapOf(ActionParameterKey.TEMPLATE_ID to "login", ActionParameterKey.MATCH_THRESHOLD to "0.8")),
            ScriptAction(ActionType.WAIT_IMAGE, parameters = mapOf(ActionParameterKey.TEMPLATE_ID to "ready", ActionParameterKey.MATCH_THRESHOLD to "0.8", ActionParameterKey.WAIT_TIMEOUT_MILLIS to "20")),
            ScriptAction(ActionType.OCR_TEXT, parameters = mapOf(ActionParameterKey.OCR_VARIABLE_NAME to "ocr", ActionParameterKey.OCR_TARGET_TEXT to "自动化", ActionParameterKey.MATCH_REGION_LEFT to "0", ActionParameterKey.MATCH_REGION_TOP to "0", ActionParameterKey.MATCH_REGION_RIGHT to "2", ActionParameterKey.MATCH_REGION_BOTTOM to "2")),
            ScriptAction(ActionType.FIND_COLOR, parameters = mapOf(ActionParameterKey.COLOR_HEX to "#FF0000", ActionParameterKey.COLOR_TOLERANCE to "0", ActionParameterKey.MATCH_VARIABLE_NAME to "red")),
            ScriptAction(ActionType.PICK_COLOR, parameters = mapOf(ActionParameterKey.PICK_X to "1", ActionParameterKey.PICK_Y to "0", ActionParameterKey.COLOR_VARIABLE_NAME to "picked"))
        )

        assertEquals(ActionExecutionResult.Success, runner.run(actions))
        assertEquals(listOf("10,20,80", "30,40,500", "42,18,80"), accessibility.presses)
        assertEquals(listOf("50,60,80"), accessibility.doublePresses)
        assertEquals(listOf("1,2,100,200,300"), accessibility.swipes)
        assertEquals("hello", accessibility.enteredText)
        assertEquals(AccessibilityNodeSelector(text = "登录"), accessibility.clickedSelector)
        assertTrue(vision.matchTemplateIds.containsAll(listOf("login", "ready")))
    }

    private class RecordingAccessibilityController : AccessibilityController {
        val presses = mutableListOf<String>()
        val doublePresses = mutableListOf<String>()
        val swipes = mutableListOf<String>()
        var enteredText: String? = null
        var clickedSelector: AccessibilityNodeSelector? = null

        override suspend fun press(x: Int, y: Int, durationMillis: Long): Boolean {
            presses += "$x,$y,$durationMillis"
            return true
        }

        override suspend fun doublePress(x: Int, y: Int, durationMillis: Long): Boolean {
            doublePresses += "$x,$y,$durationMillis"
            return true
        }

        override suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMillis: Long): Boolean {
            swipes += "$startX,$startY,$endX,$endY,$durationMillis"
            return true
        }

        override suspend fun setFocusedText(text: String): Boolean {
            enteredText = text
            return true
        }

        override suspend fun clickNode(selector: AccessibilityNodeSelector): Boolean {
            clickedSelector = selector
            return true
        }

        override suspend fun performGlobalAction(action: Int): Boolean = true
    }

    private class RecordingVisionController : VisionController {
        val matchTemplateIds = mutableListOf<String>()

        override suspend fun capture(): ScreenCapture = ScreenCapture(
            width = 2,
            height = 2,
            pixels = intArrayOf(0xFF000000.toInt(), 0xFFAABBCC.toInt(), 0xFFFF0000.toInt(), 0xFF000000.toInt())
        )

        override suspend fun captureBitmap(): Bitmap = Bitmap.createBitmap(
            intArrayOf(0xFF000000.toInt(), 0xFFAABBCC.toInt(), 0xFFFF0000.toInt(), 0xFF000000.toInt()),
            2,
            2,
            Bitmap.Config.ARGB_8888
        )

        override suspend fun match(templateId: String, threshold: Float, region: android.graphics.Rect?): TemplateMatch? {
            matchTemplateIds += templateId
            return when (templateId) {
                "login" -> TemplateMatch(42, 18, 0.95f)
                "ready" -> TemplateMatch(12, 34, 0.9f)
                else -> null
            }
        }
    }
}
