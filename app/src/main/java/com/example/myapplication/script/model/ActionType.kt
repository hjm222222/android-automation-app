package com.example.myapplication.script.model

enum class ActionType(val displayName: String, val category: ActionCategory) {
    CLICK("点击", ActionCategory.BASIC),
    LONG_CLICK("长按", ActionCategory.BASIC),
    DOUBLE_CLICK("双击", ActionCategory.BASIC),
    SWIPE("滑动", ActionCategory.BASIC),
    INPUT_TEXT("输入文字", ActionCategory.BASIC),
    WAIT("等待", ActionCategory.BASIC),
    CLICK_NODE("点击控件", ActionCategory.ACCESSIBILITY),
    CLICK_IMAGE("点击图像", ActionCategory.VISION),
    WAIT_IMAGE("等待图像", ActionCategory.VISION),
    OCR_TEXT("OCR文字", ActionCategory.VISION),
    CLICK_OCR_TEXT("点击屏幕文字", ActionCategory.VISION),
    FIND_COLOR("找色", ActionCategory.VISION),
    PICK_COLOR("取色", ActionCategory.VISION),
    CREATE_VARIABLE("创建变量", ActionCategory.DATA),
    SET_VARIABLE("设置变量", ActionCategory.DATA),
    SYSTEM_NAVIGATION("系统导航", ActionCategory.SYSTEM),
    APP_CONTROL("应用控制", ActionCategory.SYSTEM)
}
