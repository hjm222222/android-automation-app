package com.example.myapplication.script.platform

import android.graphics.Rect

/** ML Kit OCR 的整行文字结果，坐标均位于输入 Bitmap 内。 */
data class OcrTextLine(
    val text: String,
    val boundingBox: Rect
)

data class OcrTextResult(
    val lines: List<OcrTextLine>
)
