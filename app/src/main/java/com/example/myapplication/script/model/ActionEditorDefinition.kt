package com.example.myapplication.script.model

enum class ActionInputType {
    TEXT,
    NUMBER
}

data class ActionFieldDefinition(
    val key: String,
    val hint: String,
    val inputType: ActionInputType = ActionInputType.TEXT,
    val defaultValue: String = ""
)

data class ActionEditorDefinition(
    val fields: List<ActionFieldDefinition>,
    val displayName: (Map<String, String>) -> String
)

object ActionParameterKey {
    const val DURATION_MILLIS = "durationMs"
    const val X = "x"
    const val Y = "y"
    const val START_X = "startX"
    const val START_Y = "startY"
    const val END_X = "endX"
    const val END_Y = "endY"
    const val VARIABLE_NAME = "name"
    const val VARIABLE_VALUE = "value"
    const val TEXT = "text"
    const val NAVIGATION_ACTION = "navigationAction"
    const val APP_CONTROL_OPERATION = "appControlOperation"
    const val PACKAGE_NAME = "packageName"
    const val NODE_TEXT = "nodeText"
    const val NODE_DESCRIPTION = "nodeDescription"
    const val NODE_RESOURCE_ID = "nodeResourceId"
    const val NODE_CLASS_NAME = "nodeClassName"
    const val NODE_PACKAGE_NAME = "nodePackageName"
    const val COLOR_HEX = "colorHex"
    const val COLOR_RED = "colorRed"
    const val COLOR_GREEN = "colorGreen"
    const val COLOR_BLUE = "colorBlue"
    const val COLOR_TOLERANCE = "colorTolerance"
    const val COLOR_VARIABLE_NAME = "colorVariableName"
    const val MATCH_VARIABLE_NAME = "matchVariableName"
    const val FIND_COLOR_CLICK = "findColorClick"
    const val PICK_X = "pickX"
    const val PICK_Y = "pickY"
    const val TEMPLATE_ID = "templateId"
    const val MATCH_THRESHOLD = "matchThreshold"
    const val WAIT_TIMEOUT_MILLIS = "waitTimeoutMs"
    const val MATCH_REGION_LEFT = "matchRegionLeft"
    const val MATCH_REGION_TOP = "matchRegionTop"
    const val MATCH_REGION_RIGHT = "matchRegionRight"
    const val MATCH_REGION_BOTTOM = "matchRegionBottom"
    const val OCR_VARIABLE_NAME = "ocrVariableName"
    const val OCR_TARGET_TEXT = "ocrTargetText"
}
