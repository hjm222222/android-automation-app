package com.example.myapplication

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeScreenInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreenDisplaysEveryPrimaryControl() {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                HomeScreen(
                    state = MainUiState(),
                    onAddClick = {},
                    onRequestOverlay = {},
                    onRequestScreenCapture = {},
                    onRequestAccessibility = {}
                )
            }
        }

        composeRule.onNodeWithText("自动化小精灵").assertExists()
        composeRule.onNodeWithContentDescription("创建任务").assertExists()
        composeRule.onNodeWithContentDescription("请求悬浮窗权限").assertExists()
        composeRule.onNodeWithContentDescription("请求无障碍权限").assertExists()
        composeRule.onNodeWithContentDescription("请求屏幕录制权限").assertExists()
    }

    @Test
    fun homeScreenInvokesEveryPrimaryControl() {
        var addCount = 0
        var overlayCount = 0
        var accessibilityCount = 0
        var screenCaptureCount = 0
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                HomeScreen(
                    state = MainUiState(),
                    onAddClick = { addCount++ },
                    onRequestOverlay = { overlayCount++ },
                    onRequestScreenCapture = { screenCaptureCount++ },
                    onRequestAccessibility = { accessibilityCount++ }
                )
            }
        }

        composeRule.onNodeWithContentDescription("创建任务").performClick()
        composeRule.onNodeWithContentDescription("请求悬浮窗权限").performClick()
        composeRule.onNodeWithContentDescription("请求无障碍权限").performClick()
        composeRule.onNodeWithContentDescription("请求屏幕录制权限").performClick()

        composeRule.runOnIdle {
            assertEquals(1, addCount)
            assertEquals(1, overlayCount)
            assertEquals(1, accessibilityCount)
            assertEquals(1, screenCaptureCount)
        }
    }
}
