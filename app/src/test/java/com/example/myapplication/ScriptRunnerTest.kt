package com.example.myapplication

import com.example.myapplication.script.action.ActionExecutionFailureCode
import com.example.myapplication.script.action.ActionExecutionResult
import com.example.myapplication.script.action.ClickActionHandler
import com.example.myapplication.script.action.ClickNodeActionHandler
import com.example.myapplication.script.action.ClickImageActionHandler
import com.example.myapplication.script.action.FindColorActionHandler
import com.example.myapplication.script.action.OcrTextActionHandler
import com.example.myapplication.script.action.PickColorActionHandler
import com.example.myapplication.script.action.WaitImageActionHandler
import android.accessibilityservice.AccessibilityService
import com.example.myapplication.script.action.InputTextActionHandler
import com.example.myapplication.script.action.AppControlActionHandler
import com.example.myapplication.script.action.ScriptActionHandler
import com.example.myapplication.script.action.SystemNavigationActionHandler
import com.example.myapplication.script.api.ActionApiResult
import com.example.myapplication.script.api.FailureCode
import com.example.myapplication.script.api.InsertPosition
import com.example.myapplication.script.api.ScriptActionApiImpl
import com.example.myapplication.script.model.ActionCondition
import android.graphics.Rect as AndroidRect
import com.example.myapplication.script.model.ImageJudgementScope
import com.example.myapplication.script.model.Rect
import com.example.myapplication.script.model.JudgementCondition
import com.example.myapplication.script.model.TextJudgementScope
import com.example.myapplication.script.model.ActionExecutionOptions
import com.example.myapplication.script.model.ActionSettingsInput
import com.example.myapplication.script.model.JudgementInputType
import com.example.myapplication.script.model.ActionParameterKey
import com.example.myapplication.script.model.ActionSettingsMapper
import com.example.myapplication.script.model.ActionSettingsMappingResult
import com.example.myapplication.script.model.ActionType
import com.example.myapplication.script.model.ScriptAction
import com.example.myapplication.script.runtime.ActionCreationFailureCode
import com.example.myapplication.script.runtime.ActionCreationResult
import com.example.myapplication.script.runtime.ActionFactory
import com.example.myapplication.script.model.VariableComparisonOperator
import com.example.myapplication.script.runtime.ScriptRunner
import com.example.myapplication.script.runtime.ScriptRuntime
import com.example.myapplication.script.runtime.ScriptWorkspaceController
import com.example.myapplication.script.runtime.WorkspaceActionCommand
import com.example.myapplication.script.runtime.WorkspaceCommandResult
import com.example.myapplication.script.runtime.WorkspaceFailureCode
import com.example.myapplication.script.platform.AccessibilityController
import com.example.myapplication.script.platform.AccessibilityNodeSelector
import com.example.myapplication.script.platform.ApplicationControlResult
import com.example.myapplication.script.platform.ApplicationController
import com.example.myapplication.script.platform.ScreenCapture
import com.example.myapplication.script.platform.VisionController
import com.example.myapplication.script.registry.ActionDefinitionRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test

class ScriptRunnerTest {
    @Test
    fun clickActionReportsPlatformDisconnected() = runTest {
        val result = ClickActionHandler().execute(
            action = ScriptAction(
                type = ActionType.CLICK,
                parameters = mapOf("x" to "10", "y" to "20")
            ),
            runtime = ScriptRuntime(accessibilityController = null)
        )

        assertEquals(
            ActionExecutionFailureCode.PLATFORM_DISCONNECTED,
            (result as ActionExecutionResult.Failed).code
        )
    }

    @Test
    fun systemNavigationActionMapsBackToAccessibilityGlobalAction() = runTest {
        val controller = RecordingAccessibilityController(setTextResult = true)
        val result = SystemNavigationActionHandler().execute(
            action = ScriptAction(
                type = ActionType.SYSTEM_NAVIGATION,
                parameters = mapOf(ActionParameterKey.NAVIGATION_ACTION to "BACK")
            ),
            runtime = ScriptRuntime(accessibilityController = controller)
        )

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(AccessibilityService.GLOBAL_ACTION_BACK, controller.globalAction)
    }

    @Test
    fun systemNavigationActionRejectsUnknownNavigationValue() = runTest {
        val result = SystemNavigationActionHandler().execute(
            action = ScriptAction(
                type = ActionType.SYSTEM_NAVIGATION,
                parameters = mapOf(ActionParameterKey.NAVIGATION_ACTION to "UNKNOWN")
            ),
            runtime = ScriptRuntime(accessibilityController = RecordingAccessibilityController(true))
        )

        assertEquals("系统导航动作无效", (result as ActionExecutionResult.Failed).message)
    }

    @Test
    fun actionApiCreatesSystemNavigationWithSelectedVariant() {
        val api = ScriptActionApiImpl(ScriptWorkspaceController())

        val result = api.addAction(
            type = ActionType.SYSTEM_NAVIGATION,
            fields = mapOf(ActionParameterKey.NAVIGATION_ACTION to "HOME")
        )

        assertTrue(result is ActionApiResult.Success)
        assertEquals(
            "HOME",
            api.listActions().single().parameters[ActionParameterKey.NAVIGATION_ACTION]
        )
    }

    @Test
    fun workspaceCommandsModifyActionsByStableId() {
        val workspace = ScriptWorkspaceController()
        val first = ScriptAction(type = ActionType.WAIT, id = "first")
        val second = ScriptAction(type = ActionType.CREATE_VARIABLE, id = "second")
        val replacement = ScriptAction(type = ActionType.SET_VARIABLE, id = "other")

        workspace.apply(WorkspaceActionCommand.Add(first))
        workspace.apply(WorkspaceActionCommand.Add(second, position = 0))
        val replaceResult = workspace.apply(
            WorkspaceActionCommand.Replace("first", replacement)
        )
        val moveResult = workspace.apply(WorkspaceActionCommand.Move("first", targetPosition = 0))
        val removeResult = workspace.apply(WorkspaceActionCommand.Remove("second"))

        assertTrue(replaceResult is WorkspaceCommandResult.Success)
        assertTrue(moveResult is WorkspaceCommandResult.Success)
        assertTrue(removeResult is WorkspaceCommandResult.Success)
        assertEquals(listOf("first"), workspace.snapshot().map { it.id })
        assertEquals(ActionType.SET_VARIABLE, workspace.snapshot().single().type)
    }

    @Test
    fun workspaceCommandsRejectUnknownActionId() {
        val result = ScriptWorkspaceController().apply(
            WorkspaceActionCommand.Remove("missing")
        )

        assertEquals(
            WorkspaceCommandResult.Invalid(
                WorkspaceFailureCode.ACTION_NOT_FOUND,
                "未找到要删除的动作"
            ),
            result
        )
    }

    @Test
    fun everyAvailableActionHasACompleteDefinition() {
        ActionDefinitionRegistry.availableTypes().forEach { type ->
            val definition = ActionDefinitionRegistry.definitionFor(type)
            assertTrue("缺少动作定义：$type", definition != null)
            assertTrue("缺少动作处理器：$type", definition?.handler?.isAvailable == true)
            assertTrue("缺少动作编辑字段：$type", definition?.editor?.fields != null)
        }
    }

    @Test
    fun onlyActionsWithCompleteDefaultsCanBeCreatedWithoutEditing() {
        assertTrue(ActionDefinitionRegistry.canCreateDefault(ActionType.WAIT))
        assertTrue(!ActionDefinitionRegistry.canCreateDefault(ActionType.CLICK))
        assertTrue(!ActionDefinitionRegistry.canCreateDefault(ActionType.SWIPE))
        assertTrue(!ActionDefinitionRegistry.canCreateDefault(ActionType.CREATE_VARIABLE))
    }

    @Test
    fun actionApiAddsActionsThroughFactory() {
        val api = ScriptActionApiImpl(ScriptWorkspaceController())

        val result = api.addAction(
            type = ActionType.WAIT,
            fields = mapOf("durationMs" to "2")
        )

        assertTrue(result is com.example.myapplication.script.api.ActionApiResult.Success)
        assertEquals("等待 2 秒", api.listActions().single().displayName)
        assertEquals("2000", api.listActions().single().parameters["durationMs"])
    }

    @Test
    fun actionApiAddsDefaultWaitWithoutConvertingMillisecondsAgain() {
        val api = ScriptActionApiImpl(ScriptWorkspaceController())

        val result = api.addDefaultAction(ActionType.WAIT)

        assertTrue(result is ActionApiResult.Success)
        val action = api.listActions().single()
        assertEquals("1000", action.parameters[ActionParameterKey.DURATION_MILLIS])
        assertEquals("等待 1 秒", action.displayName)
    }

    @Test
    fun actionApiRejectsInvalidActionParameters() {
        val api = ScriptActionApiImpl(ScriptWorkspaceController())

        val result = api.addAction(
            type = ActionType.CLICK,
            fields = mapOf("x" to "", "y" to "")
        )

        assertEquals(
            com.example.myapplication.script.api.FailureCode.MISSING_FIELD,
            (result as com.example.myapplication.script.api.ActionApiResult.Failure).code
        )
    }

    @Test
    fun actionApiRemovesActionByStableId() {
        val api = ScriptActionApiImpl(ScriptWorkspaceController())
        val added = api.addWait(seconds = 1)
        val actionId = (added as ActionApiResult.Success).actionId

        assertTrue(actionId != null)
        assertEquals(ActionApiResult.Success(null), api.remove(actionId!!))
        assertTrue(api.listActions().isEmpty())
    }

    @Test
    fun actionApiMapsInvalidNumberPrecisely() {
        val api = ScriptActionApiImpl(ScriptWorkspaceController())

        val result = api.addAction(
            type = ActionType.WAIT,
            fields = mapOf("durationMs" to "invalid")
        )

        assertEquals(
            FailureCode.INVALID_NUMBER,
            (result as ActionApiResult.Failure).code
        )
    }

    @Test
    fun actionApiReportsInvalidInsertPosition() {
        val api = ScriptActionApiImpl(ScriptWorkspaceController())

        val result = api.addWait(
            seconds = 1,
            position = InsertPosition.At(1)
        )

        assertEquals(
            FailureCode.INVALID_POSITION,
            (result as ActionApiResult.Failure).code
        )
    }

    @Test
    fun actionFactoryCreatesConfiguredWaitAction() {
        val result = ActionFactory.create(
            type = ActionType.WAIT,
            editorValues = mapOf("durationMs" to "2")
        )

        val action = (result as ActionCreationResult.Success).action
        assertEquals(ActionType.WAIT, action.type)
        assertEquals("2000", action.parameters["durationMs"])
        assertEquals("等待 2 秒", action.displayName)
    }

    @Test
    fun actionFactoryRejectsMissingEditorValue() {
        val result = ActionFactory.create(
            type = ActionType.WAIT,
            editorValues = mapOf("durationMs" to "")
        )

        assertEquals(
            ActionCreationResult.Invalid(
                ActionCreationFailureCode.MISSING_FIELD,
                "等待时间（秒）不能为空"
            ),
            result
        )
    }

    @Test
    fun actionFactoryRejectsInvalidNumericValuesBeforeAddingAction() {
        val invalidClick = ActionFactory.create(
            type = ActionType.CLICK,
            editorValues = mapOf(
                ActionParameterKey.X to "invalid",
                ActionParameterKey.Y to "20",
                ActionParameterKey.DURATION_MILLIS to "80"
            )
        )
        val negativeSwipeDuration = ActionFactory.create(
            type = ActionType.SWIPE,
            editorValues = mapOf(
                ActionParameterKey.START_X to "0",
                ActionParameterKey.START_Y to "0",
                ActionParameterKey.END_X to "10",
                ActionParameterKey.END_Y to "10",
                ActionParameterKey.DURATION_MILLIS to "-1"
            )
        )

        assertEquals(
            ActionCreationFailureCode.INVALID_NUMBER,
            (invalidClick as ActionCreationResult.Invalid).code
        )
        assertEquals(
            ActionCreationFailureCode.INVALID_NUMBER,
            (negativeSwipeDuration as ActionCreationResult.Invalid).code
        )
    }

    @Test
    fun actionFactoryRejectsWaitOverflow() {
        val result = ActionFactory.create(
            type = ActionType.WAIT,
            editorValues = mapOf(
                ActionParameterKey.DURATION_MILLIS to (Long.MAX_VALUE / 1000L + 1L).toString()
            )
        )

        assertEquals(
            ActionCreationFailureCode.INVALID_NUMBER,
            (result as ActionCreationResult.Invalid).code
        )
    }

    @Test
    fun workspaceAppliesConcurrentAddsWithoutLosingActions() {
        val workspace = ScriptWorkspaceController()
        val workerCount = 16
        val ready = CountDownLatch(workerCount)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(workerCount)

        repeat(workerCount) { index ->
            Thread {
                ready.countDown()
                start.await()
                workspace.apply(
                    WorkspaceActionCommand.Add(
                        ScriptAction(type = ActionType.WAIT, id = "action-$index")
                    )
                )
                finished.countDown()
            }.start()
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(finished.await(2, TimeUnit.SECONDS))
        val actions = workspace.snapshot()
        assertEquals(workerCount, actions.size)
        assertEquals(workerCount, actions.map { it.id }.toSet().size)
        assertFalse(workspace.isEmpty)
    }

    @Test
    fun actionSettingsMapperBuildsConditionAndKeepsNestedActions() {
        val before = ScriptAction(ActionType.CREATE_VARIABLE)
        val after = ScriptAction(ActionType.SET_VARIABLE)

        val result = ActionSettingsMapper.map(
            ActionSettingsInput(
                variableName = " count ",
                variableOperator = VariableComparisonOperator.LESS_THAN_OR_EQUALS,
                variableExpectedValue = " 3 ",
                beforeActions = listOf(before),
                afterActions = listOf(after)
            )
        )

        val settings = (result as ActionSettingsMappingResult.Success).settings
        assertEquals(
            ActionCondition.Judgement(
                JudgementCondition.Variable(
                    variableName = "count",
                    operator = VariableComparisonOperator.LESS_THAN_OR_EQUALS,
                    expectedValue = "3"
                )
            ),
            settings.executionOptions.condition
        )
        assertEquals(listOf(before), settings.beforeActions)
        assertEquals(listOf(after), settings.afterActions)
    }

    @Test
    fun actionSettingsMapperRejectsPartialVariableCondition() {
        val result = ActionSettingsMapper.map(ActionSettingsInput(variableName = "count"))

        assertEquals(
            ActionSettingsMappingResult.Invalid("比较值不能为空"),
            result
        )
    }

    @Test
    fun actionSettingsMapperMapsOcrImageAndRegionColorInputs() {
        val ocr = ActionSettingsMapper.map(
            ActionSettingsInput(
                judgementType = JudgementInputType.OCR,
                ocrScope = TextJudgementScope.FULL_SCREEN,
                ocrExpectedText = " 登录 "
            )
        ) as ActionSettingsMappingResult.Success
        assertEquals(
            JudgementCondition.OcrText(TextJudgementScope.FULL_SCREEN, "登录"),
            (ocr.settings.executionOptions.condition as ActionCondition.Judgement).condition
        )

        val imageRegion = Rect(1, 2, 30, 40)
        val image = ActionSettingsMapper.map(
            ActionSettingsInput(
                judgementType = JudgementInputType.IMAGE,
                imageScope = ImageJudgementScope.REGION,
                imageId = " template ",
                imageRegion = imageRegion
            )
        ) as ActionSettingsMappingResult.Success
        assertEquals(
            JudgementCondition.Image(ImageJudgementScope.REGION, "template", imageRegion),
            (image.settings.executionOptions.condition as ActionCondition.Judgement).condition
        )

        val colorRegion = Rect(0, 0, 8, 9)
        val color = ActionSettingsMapper.map(
            ActionSettingsInput(
                judgementType = JudgementInputType.REGION_COLOR,
                regionColor = " #aBc123 ",
                regionColorRegion = colorRegion,
                regionColorTolerance = 12
            )
        ) as ActionSettingsMappingResult.Success
        assertEquals(
            JudgementCondition.RegionColor("#aBc123", colorRegion, 12),
            (color.settings.executionOptions.condition as ActionCondition.Judgement).condition
        )
    }

    @Test
    fun actionSettingsMapperRejectsInvalidImageAndColorInputs() {
        assertEquals(
            ActionSettingsMappingResult.Invalid("图片区域无效"),
            ActionSettingsMapper.map(
                ActionSettingsInput(
                    judgementType = JudgementInputType.IMAGE,
                    imageId = "template",
                    imageRegion = Rect(0, 0, 0, 1)
                )
            )
        )
        assertEquals(
            ActionSettingsMappingResult.Invalid("颜色格式无效"),
            ActionSettingsMapper.map(
                ActionSettingsInput(
                    judgementType = JudgementInputType.REGION_COLOR,
                    regionColor = "red",
                    regionColorRegion = Rect(0, 0, 1, 1)
                )
            )
        )
        assertEquals(
            ActionSettingsMappingResult.Invalid("颜色容差无效"),
            ActionSettingsMapper.map(
                ActionSettingsInput(
                    judgementType = JudgementInputType.REGION_COLOR,
                    regionColor = "#112233",
                    regionColorRegion = Rect(0, 0, 1, 1),
                    regionColorTolerance = 256
                )
            )
        )
    }

    @Test
    fun run_executesBeforeMainAndAfterActionsInOrder() = runTest {
        val executedTypes = mutableListOf<ActionType>()
        val handler = RecordingHandler(executedTypes)
        val runner = ScriptRunner(handlerResolver = { handler })
        val action = ScriptAction(
            type = ActionType.WAIT,
            beforeActions = listOf(ScriptAction(ActionType.CREATE_VARIABLE)),
            afterActions = listOf(ScriptAction(ActionType.SET_VARIABLE))
        )

        val result = runner.run(listOf(action))

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(
            listOf(
                ActionType.CREATE_VARIABLE,
                ActionType.WAIT,
                ActionType.SET_VARIABLE
            ),
            executedTypes
        )
    }

    @Test
    fun run_appliesConditionAndNestedBeforeAfterActionsRecursively() = runTest {
        val executedTypes = mutableListOf<ActionType>()
        val handler = RecordingHandler(executedTypes)
        val runner = ScriptRunner(handlerResolver = { handler })
        val nested = ScriptAction(
            type = ActionType.WAIT,
            beforeActions = listOf(ScriptAction(ActionType.CREATE_VARIABLE)),
            afterActions = listOf(ScriptAction(ActionType.SET_VARIABLE))
        )
        val skipped = nested.copy(
            executionOptions = ActionExecutionOptions(
                condition = ActionCondition.VariableEquals("missing", "value")
            )
        )
        val recursiveNested = nested.copy(
            beforeActions = listOf(skipped, nested)
        )

        val result = runner.run(listOf(recursiveNested))

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(
            listOf(
                ActionType.CREATE_VARIABLE,
                ActionType.WAIT,
                ActionType.SET_VARIABLE,
                ActionType.WAIT,
                ActionType.SET_VARIABLE
            ),
            executedTypes
        )
    }

    @Test
    fun appControlLaunchPassesPackageNameToPlatform() = runTest {
        val controller = RecordingApplicationController()
        val result = AppControlActionHandler().execute(
            action = ScriptAction(
                type = ActionType.APP_CONTROL,
                parameters = mapOf(
                    ActionParameterKey.APP_CONTROL_OPERATION to "LAUNCH",
                    ActionParameterKey.PACKAGE_NAME to "com.example.target"
                )
            ),
            runtime = ScriptRuntime(applicationController = controller)
        )

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals("com.example.target", controller.launchedPackage)
    }

    @Test
    fun appControlCloseRemainsExplicitlyUnavailable() = runTest {
        val result = AppControlActionHandler().execute(
            action = ScriptAction(
                type = ActionType.APP_CONTROL,
                parameters = mapOf(
                    ActionParameterKey.APP_CONTROL_OPERATION to "CLOSE",
                    ActionParameterKey.PACKAGE_NAME to "com.example.target"
                )
            ),
            runtime = ScriptRuntime(applicationController = RecordingApplicationController())
        )

        assertEquals("关闭应用需要无障碍服务", (result as ActionExecutionResult.Failed).message)
    }

    @Test
    fun pickColorReadsSavedPixelAndWritesVariable() = runTest {
        val runtime = ScriptRuntime(visionController = RecordingVisionController(ScreenCapture(2, 1, intArrayOf(0xFF112233.toInt(), 0xFFAABBCC.toInt()))))
        val result = PickColorActionHandler().execute(
            ScriptAction(ActionType.PICK_COLOR, parameters = mapOf(
                ActionParameterKey.PICK_X to "1",
                ActionParameterKey.PICK_Y to "0",
                ActionParameterKey.COLOR_VARIABLE_NAME to "picked"
            )), runtime
        )
        assertEquals(ActionExecutionResult.Success, result)
        assertEquals("#AABBCC", runtime.getVariable("picked"))
    }

    @Test
    fun findColorStoresMatchAndOptionallyClicks() = runTest {
        val accessibility = RecordingAccessibilityController(true)
        val runtime = ScriptRuntime(
            accessibilityController = accessibility,
            visionController = RecordingVisionController(ScreenCapture(2, 1, intArrayOf(0xFF000000.toInt(), 0xFFFF0000.toInt())))
        )
        val result = FindColorActionHandler().execute(
            ScriptAction(ActionType.FIND_COLOR, parameters = mapOf(
                ActionParameterKey.COLOR_HEX to "#FF0000",
                ActionParameterKey.COLOR_TOLERANCE to "0",
                ActionParameterKey.MATCH_VARIABLE_NAME to "point",
                ActionParameterKey.FIND_COLOR_CLICK to "true"
            )), runtime
        )
        assertEquals(ActionExecutionResult.Success, result)
        assertEquals("1,0", runtime.getVariable("point"))
        assertEquals(1 to 0, accessibility.lastPressedPoint)
    }

    @Test
    fun clickNodePassesSelectorToAccessibilityController() = runTest {
        val controller = RecordingAccessibilityController(setTextResult = true)
        val result = ClickNodeActionHandler().execute(
            action = ScriptAction(
                type = ActionType.CLICK_NODE,
                parameters = mapOf(
                    ActionParameterKey.NODE_TEXT to "登录",
                    ActionParameterKey.NODE_RESOURCE_ID to "com.example:id/login"
                )
            ),
            runtime = ScriptRuntime(accessibilityController = controller)
        )

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(
            AccessibilityNodeSelector(
                text = "登录",
                resourceId = "com.example:id/login"
            ),
            controller.clickedSelector
        )
    }

    @Test
    fun clickNodeRejectsEmptySelector() = runTest {
        val result = ClickNodeActionHandler().execute(
            action = ScriptAction(ActionType.CLICK_NODE),
            runtime = ScriptRuntime(accessibilityController = RecordingAccessibilityController(true))
        )

        assertEquals("控件选择条件为空", (result as ActionExecutionResult.Failed).message)
    }

    @Test
    fun run_returnsClearFailureForUnavailableAction() = runTest {
        val runner = ScriptRunner(
            handlerResolver = { UnavailableHandler }
        )

        val result = runner.run(listOf(ScriptAction(ActionType.OCR_TEXT)))

        assertTrue(result is ActionExecutionResult.Failed)
        assertEquals("动作暂不可用：OCR文字", (result as ActionExecutionResult.Failed).message)
    }

    @Test
    fun imageTemplateMatcherFindsCenterOfExactTemplate() = runTest {
        val capture = ScreenCapture(5, 5, IntArray(25) { index ->
            if (index == 7 || index == 8 || index == 12 || index == 13) 0xFFFF0000.toInt() else 0xFF000000.toInt()
        })
        val template = com.example.myapplication.script.platform.ImageTemplate(
            id = "template",
            width = 1,
            height = 1,
            pixels = intArrayOf(0xFFFF0000.toInt())
        )

        val match = com.example.myapplication.script.platform.ImageTemplateMatcher()
            .find(capture, template, threshold = 0.0f)

        assertEquals(2, match?.x)
        assertEquals(1, match?.y)
    }

    @Test
    fun imageActionFactoryRejectsMissingTemplateAndInvalidThreshold() {
        val missing = ActionFactory.create(
            ActionType.CLICK_IMAGE,
            mapOf(
                ActionParameterKey.TEMPLATE_ID to "",
                ActionParameterKey.MATCH_THRESHOLD to "0.85",
                ActionParameterKey.WAIT_TIMEOUT_MILLIS to "0"
            )
        )
        val invalidThreshold = ActionFactory.create(
            ActionType.WAIT_IMAGE,
            mapOf(
                ActionParameterKey.TEMPLATE_ID to "template-id",
                ActionParameterKey.MATCH_THRESHOLD to "1.1",
                ActionParameterKey.WAIT_TIMEOUT_MILLIS to "1000"
            )
        )

        assertEquals(ActionCreationFailureCode.MISSING_FIELD, (missing as ActionCreationResult.Invalid).code)
        assertEquals(ActionCreationFailureCode.INVALID_NUMBER, (invalidThreshold as ActionCreationResult.Invalid).code)
    }

    @Test
    fun ocrFactoryRequiresRegionAndKeepsTargetOptional() {
        val result = ActionFactory.create(
            ActionType.OCR_TEXT,
            mapOf(
                ActionParameterKey.OCR_VARIABLE_NAME to "recognized",
                ActionParameterKey.OCR_TARGET_TEXT to "登录",
                ActionParameterKey.MATCH_REGION_LEFT to "10",
                ActionParameterKey.MATCH_REGION_TOP to "20",
                ActionParameterKey.MATCH_REGION_RIGHT to "110",
                ActionParameterKey.MATCH_REGION_BOTTOM to "80"
            )
        )

        val action = (result as ActionCreationResult.Success).action
        assertEquals("recognized", action.parameters[ActionParameterKey.OCR_VARIABLE_NAME])
        assertEquals("登录", action.parameters[ActionParameterKey.OCR_TARGET_TEXT])
        assertEquals("10", action.parameters[ActionParameterKey.MATCH_REGION_LEFT])
    }

    @Test
    fun ocrConditionMatchesRecentRecognitionResult() = runTest {
        val runtime = ScriptRuntime()
        runtime.recordOcrText("登录成功 123")

        assertTrue(
            com.example.myapplication.script.runtime.ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(
                    JudgementCondition.OcrText(TextJudgementScope.REGION, "成功")
                ),
                runtime
            )
        )
        assertFalse(
            com.example.myapplication.script.runtime.ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(
                    JudgementCondition.OcrText(TextJudgementScope.REGION, "失败")
                ),
                runtime
            )
        )
    }

    @Test
    fun imageConditionUsesFixedThresholdAndScopeRegion() = runTest {
        val fullScreenVision = ConditionVisionController(match = com.example.myapplication.script.platform.TemplateMatch(1, 2, 0.9f))
        assertTrue(
            com.example.myapplication.script.runtime.ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(JudgementCondition.Image(ImageJudgementScope.FULL_SCREEN, "template")),
                ScriptRuntime(visionController = fullScreenVision)
            )
        )
        assertEquals("template", fullScreenVision.templateId)
        assertEquals(0.85f, fullScreenVision.threshold)
        assertEquals(null, fullScreenVision.region)

        val region = Rect(10, 20, 30, 40)
        val regionVision = ConditionVisionController(match = com.example.myapplication.script.platform.TemplateMatch(11, 21, 0.9f))
        assertTrue(
            com.example.myapplication.script.runtime.ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(JudgementCondition.Image(ImageJudgementScope.REGION, "template", region)),
                ScriptRuntime(visionController = regionVision)
            )
        )
        assertEquals(region.left, regionVision.region?.left)
        assertEquals(region.top, regionVision.region?.top)
        assertEquals(region.right, regionVision.region?.right)
        assertEquals(region.bottom, regionVision.region?.bottom)
    }

    @Test
    fun regionColorConditionMatchesOnlyInsideRegionWithTolerance() = runTest {
        val capture = ScreenCapture(
            3,
            2,
            intArrayOf(
                0xFF112233.toInt(), 0xFF122335.toInt(), 0xFFFF0000.toInt(),
                0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt()
            )
        )
        val runtime = ScriptRuntime(visionController = ConditionVisionController(capture = capture))

        assertTrue(
            com.example.myapplication.script.runtime.ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(JudgementCondition.RegionColor("#112233", Rect(0, 0, 2, 1), tolerance = 0)),
                runtime
            )
        )
        assertTrue(
            com.example.myapplication.script.runtime.ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(JudgementCondition.RegionColor("#112233", Rect(1, 0, 2, 1), tolerance = 2)),
                runtime
            )
        )
        assertFalse(
            com.example.myapplication.script.runtime.ActionConditionEvaluator.shouldExecute(
                ActionCondition.Judgement(JudgementCondition.RegionColor("#FF0000", Rect(0, 0, 2, 1))),
                runtime
            )
        )
    }

    @Test
    fun ocrResultCanCreateAndUpdateRuntimeVariable() {
        val runtime = ScriptRuntime()
        val variableName = "recognized"
        val text = "中文123 English"

        runtime.recordOcrText(text)
        assertTrue(runtime.setVariable(variableName, text) || runtime.createVariable(variableName, text))
        assertEquals(text, runtime.getVariable(variableName))
        assertEquals(text, runtime.lastOcrText)

        val updatedText = "更新后的结果"
        runtime.recordOcrText(updatedText)
        assertTrue(runtime.setVariable(variableName, updatedText) || runtime.createVariable(variableName, updatedText))
        assertEquals(updatedText, runtime.getVariable(variableName))
    }

    @Test
    fun clickImageWithoutTemplateFailsExplicitly() = runTest {
        val result = ClickImageActionHandler().execute(
            ScriptAction(
                ActionType.CLICK_IMAGE,
                parameters = mapOf(
                    ActionParameterKey.TEMPLATE_ID to "",
                    ActionParameterKey.MATCH_THRESHOLD to "0.85"
                )
            ),
            ScriptRuntime()
        )

        assertEquals("图像模板不能为空", (result as ActionExecutionResult.Failed).message)
    }

    @Test
    fun clickImageClicksMatchedCenter() = runTest {
        val accessibility = RecordingAccessibilityController(setTextResult = true)
        val vision = MatchingVisionController(matches = listOf(com.example.myapplication.script.platform.TemplateMatch(42, 18, 0.95f)))

        val result = ClickImageActionHandler().execute(
            ScriptAction(
                ActionType.CLICK_IMAGE,
                parameters = mapOf(
                    ActionParameterKey.TEMPLATE_ID to "template-id",
                    ActionParameterKey.MATCH_THRESHOLD to "0.85"
                )
            ),
            ScriptRuntime(accessibilityController = accessibility, visionController = vision)
        )

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(42 to 18, accessibility.lastPressedPoint)
    }

    @Test
    fun waitImagePollsUntilMatchBeforeTimeout() = runTest {
        val vision = MatchingVisionController(matches = listOf(null, null, com.example.myapplication.script.platform.TemplateMatch(12, 34, 0.9f)))
        val runtime = ScriptRuntime(visionController = vision)

        val result = WaitImageActionHandler(
            pollIntervalMillis = 1L,
            elapsedRealtimeMillis = { 0L }
        ).execute(
            ScriptAction(
                ActionType.WAIT_IMAGE,
                parameters = mapOf(
                    ActionParameterKey.TEMPLATE_ID to "template-id",
                    ActionParameterKey.MATCH_THRESHOLD to "0.85",
                    ActionParameterKey.WAIT_TIMEOUT_MILLIS to "1000"
                )
            ),
            runtime
        )

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(3, vision.matchCalls)
        assertEquals(12, runtime.lastVisionMatch?.x)
        assertEquals(34, runtime.lastVisionMatch?.y)
    }

    @Test
    fun clickImageDoesNotRequireWaitTimeoutParameter() {
        val result = ActionFactory.create(
            ActionType.CLICK_IMAGE,
            mapOf(
                ActionParameterKey.TEMPLATE_ID to "template-id",
                ActionParameterKey.MATCH_THRESHOLD to "0.85"
            )
        )

        assertTrue(result is ActionCreationResult.Success)
    }

    private class ConditionVisionController(
        private val capture: ScreenCapture? = null,
        private val match: com.example.myapplication.script.platform.TemplateMatch? = null
    ) : VisionController {
        var templateId: String? = null
            private set
        var threshold: Float? = null
            private set
        var region: AndroidRect? = null
            private set

        override suspend fun capture(): ScreenCapture? = capture

        override suspend fun match(
            templateId: String,
            threshold: Float,
            region: AndroidRect?
        ): com.example.myapplication.script.platform.TemplateMatch? {
            this.templateId = templateId
            this.threshold = threshold
            this.region = region
            return match
        }
    }

    private class MatchingVisionController(matches: List<com.example.myapplication.script.platform.TemplateMatch?>) : VisionController {
        private val queuedMatches = matches.toMutableList()
        var matchCalls = 0
            private set

        override suspend fun capture(): ScreenCapture? = null

        override suspend fun match(
            templateId: String,
            threshold: Float,
            region: android.graphics.Rect?
        ): com.example.myapplication.script.platform.TemplateMatch? {
            matchCalls++
            return queuedMatches.removeFirstOrNull()
        }
    }

    private class RecordingVisionController(
        private val screenCapture: ScreenCapture?
    ) : VisionController {
        override suspend fun capture(): ScreenCapture? {
            return screenCapture
        }
    }

    private class RecordingAccessibilityController(
        private val setTextResult: Boolean
    ) : AccessibilityController {
        var enteredText: String? = null
            private set
        var globalAction: Int? = null
            private set
        var clickedSelector: AccessibilityNodeSelector? = null
            private set
        var lastPressedPoint: Pair<Int, Int>? = null
            private set

        override suspend fun clickNode(selector: AccessibilityNodeSelector): Boolean {
            clickedSelector = selector
            return setTextResult
        }

        override suspend fun performGlobalAction(action: Int): Boolean {
            globalAction = action
            return setTextResult
        }

        override suspend fun press(x: Int, y: Int, durationMillis: Long): Boolean {
            lastPressedPoint = x to y
            return true
        }

        override suspend fun doublePress(x: Int, y: Int, durationMillis: Long): Boolean = true

        override suspend fun swipe(
            startX: Int,
            startY: Int,
            endX: Int,
            endY: Int,
            durationMillis: Long
        ): Boolean = true

        override suspend fun setFocusedText(text: String): Boolean {
            enteredText = text
            return setTextResult
        }
    }

    private class RecordingApplicationController : ApplicationController {
        var launchedPackage: String? = null

        override suspend fun launch(packageName: String): ApplicationControlResult {
            launchedPackage = packageName
            return ApplicationControlResult.Success
        }

        override fun queryLaunchableApplications() = emptyList<
            com.example.myapplication.script.platform.LaunchableApplication
        >()
    }

    private class RecordingHandler(
        private val executedTypes: MutableList<ActionType>
    ) : ScriptActionHandler {
        override fun createDefault(): ScriptAction = ScriptAction(ActionType.WAIT)

        override suspend fun execute(
            action: ScriptAction,
            runtime: ScriptRuntime
        ): ActionExecutionResult {
            executedTypes += action.type
            return ActionExecutionResult.Success
        }
    }

    private object UnavailableHandler : ScriptActionHandler {
        override val isAvailable: Boolean = false

        override fun createDefault(): ScriptAction = ScriptAction(ActionType.OCR_TEXT)

        override suspend fun execute(
            action: ScriptAction,
            runtime: ScriptRuntime
        ): ActionExecutionResult = ActionExecutionResult.NotImplemented
    }
}
