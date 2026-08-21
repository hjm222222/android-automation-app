package com.example.myapplication.script.action

import com.example.myapplication.script.model.ActionType

class ClickImageActionHandler : EmptyActionHandler(ActionType.CLICK_IMAGE)
class WaitImageActionHandler : EmptyActionHandler(ActionType.WAIT_IMAGE)
class OcrTextActionHandler : EmptyActionHandler(ActionType.OCR_TEXT)
class FindColorActionHandler : EmptyActionHandler(ActionType.FIND_COLOR)
class PickColorActionHandler : EmptyActionHandler(ActionType.PICK_COLOR)
