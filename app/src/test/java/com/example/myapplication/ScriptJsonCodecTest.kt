package com.example.myapplication

import com.example.myapplication.script.model.*
import com.example.myapplication.script.repository.ScriptJsonCodec
import org.junit.Assert.*
import org.junit.Test

class ScriptJsonCodecTest {
    @Test fun roundTripPreservesFullActionTreeAndConditions() {
        val nested = ScriptAction(
            type = ActionType.WAIT,
            id = "nested",
            displayName = "等待",
            parameters = mapOf("durationMillis" to "20"),
            executionOptions = ActionExecutionOptions(ActionCondition.VariableEquals("ready", "yes"))
        )
        val action = ScriptAction(
            type = ActionType.CLICK_IMAGE,
            id = "root",
            displayName = "点击模板",
            parameters = mapOf("templateId" to "template-1", "threshold" to "0.9"),
            executionOptions = ActionExecutionOptions(ActionCondition.Judgement(JudgementCondition.OcrText(TextJudgementScope.FULL_SCREEN, "完成"))),
            beforeActions = listOf(nested),
            afterActions = listOf(nested.copy(id = "after", executionOptions = ActionExecutionOptions(ActionCondition.Judgement(JudgementCondition.RegionColor("#ffffff", "1,2,3,4")))) )
        )
        val script = SavedScript("script-1", "测试脚本", listOf(action), mapOf("ready" to "yes"), 3, 1234L)

        val decoded = ScriptJsonCodec.decode(ScriptJsonCodec.encode(script))

        assertEquals(script, decoded)
    }

    @Test fun roundTripPreservesAllJudgementBranches() {
        val conditions = listOf<JudgementCondition>(
            JudgementCondition.Variable("count", VariableComparisonOperator.LESS_THAN_OR_EQUALS, "3"),
            JudgementCondition.OcrText(TextJudgementScope.REGION, "文字"),
            JudgementCondition.Image(ImageJudgementScope.FULL_SCREEN, "image-id"),
            JudgementCondition.RegionColor("#123456", "0,0,10,10")
        )
        conditions.forEachIndexed { index, condition ->
            val action = ScriptAction(ActionType.WAIT, id = "a$index", executionOptions = ActionExecutionOptions(ActionCondition.Judgement(condition)))
            val decoded = ScriptJsonCodec.decode(ScriptJsonCodec.encode(SavedScript("s$index", "name", listOf(action))))
            assertEquals(condition, decoded?.actions?.single()?.executionOptions?.condition?.let { (it as ActionCondition.Judgement).condition })
        }
    }

    @Test fun missingOrMalformedFileReturnsNull() {
        assertNull(ScriptJsonCodec.decode("{}"))
        assertNull(ScriptJsonCodec.decode("not-json"))
        assertNull(ScriptJsonCodec.decode("{\"id\":\"s\",\"name\":\"n\",\"actions\":null}"))
    }
}
