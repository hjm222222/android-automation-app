package com.example.myapplication

import android.graphics.Rect
import com.example.myapplication.script.action.OcrClickActionMapper
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.platform.OcrTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrClickActionMapperTest {
    @Test
    fun fieldsPreserveTextAndBitmapBounds() {
        val fields = OcrClickActionMapper.fields(
            OcrTextLine(
                "账号密码登录",
                Rect().apply {
                    left = 10
                    top = 20
                    right = 110
                    bottom = 60
                }
            )
        )

        assertEquals("账号密码登录", fields[ActionParameterKey.OCR_TARGET_TEXT])
        assertEquals("10", fields[ActionParameterKey.OCR_TEXT_LEFT])
        assertEquals("20", fields[ActionParameterKey.OCR_TEXT_TOP])
        assertEquals("110", fields[ActionParameterKey.OCR_TEXT_RIGHT])
        assertEquals("60", fields[ActionParameterKey.OCR_TEXT_BOTTOM])
    }

    @Test
    fun centerUsesSavedTextBounds() {
        val point = OcrClickActionMapper.center(
            ScriptAction(
                type = ActionType.CLICK_OCR_TEXT,
                parameters = mapOf(
                    ActionParameterKey.OCR_TEXT_LEFT to "10",
                    ActionParameterKey.OCR_TEXT_TOP to "20",
                    ActionParameterKey.OCR_TEXT_RIGHT to "111",
                    ActionParameterKey.OCR_TEXT_BOTTOM to "61"
                )
            )
        )

        assertEquals(60, point?.x)
        assertEquals(40, point?.y)
    }

    @Test
    fun centerRejectsMissingOrInvalidBounds() {
        assertNull(
            OcrClickActionMapper.center(
                ScriptAction(ActionType.CLICK_OCR_TEXT)
            )
        )
        assertNull(
            OcrClickActionMapper.center(
                ScriptAction(
                    type = ActionType.CLICK_OCR_TEXT,
                    parameters = mapOf(
                        ActionParameterKey.OCR_TEXT_LEFT to "20",
                        ActionParameterKey.OCR_TEXT_TOP to "20",
                        ActionParameterKey.OCR_TEXT_RIGHT to "10",
                        ActionParameterKey.OCR_TEXT_BOTTOM to "40"
                    )
                )
            )
        )
    }
}
