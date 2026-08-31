package com.example.myapplication.script.action

import android.graphics.Point
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.platform.OcrTextLine

object OcrClickActionMapper {
    fun center(action: ScriptAction): Point? {
        val left = action.parameters[ActionParameterKey.OCR_TEXT_LEFT]?.toIntOrNull()
        val top = action.parameters[ActionParameterKey.OCR_TEXT_TOP]?.toIntOrNull()
        val right = action.parameters[ActionParameterKey.OCR_TEXT_RIGHT]?.toIntOrNull()
        val bottom = action.parameters[ActionParameterKey.OCR_TEXT_BOTTOM]?.toIntOrNull()
        if (left == null || top == null || right == null || bottom == null || right <= left || bottom <= top) {
            return null
        }
        return Point().apply {
            x = left + (right - left) / 2
            y = top + (bottom - top) / 2
        }
    }

    fun fields(line: OcrTextLine): Map<String, String> {
        val box = line.boundingBox
        return mapOf(
            ActionParameterKey.OCR_TARGET_TEXT to line.text,
            ActionParameterKey.OCR_TEXT_LEFT to box.left.toString(),
            ActionParameterKey.OCR_TEXT_TOP to box.top.toString(),
            ActionParameterKey.OCR_TEXT_RIGHT to box.right.toString(),
            ActionParameterKey.OCR_TEXT_BOTTOM to box.bottom.toString()
        )
    }
}
