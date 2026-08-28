package com.example.myapplication.script.repository

import com.example.myapplication.script.model.*
import org.json.JSONArray
import org.json.JSONObject

object ScriptJsonCodec {
    fun encode(script: SavedScript): String = script.toJson().toString()

    fun decode(json: String): SavedScript? = runCatching {
        JSONObject(json).toSavedScript()
    }.getOrNull()

    private fun SavedScript.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("actions", JSONArray().also { array -> actions.forEach { array.put(it.toJson()) } })
        put("initialVariables", JSONObject().also { values -> initialVariables.forEach { (key, value) -> values.put(key, value) } })
        put("version", version)
        put("updatedAt", updatedAt)
    }

    private fun JSONObject.toSavedScript() = SavedScript(
        id = getString("id"),
        name = getString("name"),
        actions = getJSONArray("actions").toActionList(),
        initialVariables = optJSONObject("initialVariables").toStringMap(),
        version = optInt("version", 1),
        updatedAt = optLong("updatedAt", 0L)
    )

    private fun ScriptAction.toJson() = JSONObject().apply {
        put("type", type.name)
        put("id", id)
        put("displayName", displayName)
        put("parameters", parameters.toJson())
        put("executionOptions", executionOptions.toJson())
        put("beforeActions", beforeActions.toActionArray())
        put("afterActions", afterActions.toActionArray())
    }

    private fun JSONObject.toAction(): ScriptAction = ScriptAction(
        type = ActionType.valueOf(getString("type")),
        id = getString("id"),
        displayName = getString("displayName"),
        parameters = optJSONObject("parameters").toStringMap(),
        executionOptions = optJSONObject("executionOptions").toExecutionOptions(),
        beforeActions = optJSONArray("beforeActions").toActionList(),
        afterActions = optJSONArray("afterActions").toActionList()
    )

    private fun ActionExecutionOptions.toJson() = JSONObject().put("condition", condition.toJson())

    private fun ActionCondition.toJson(): JSONObject = when (this) {
        ActionCondition.Always -> JSONObject().put("type", "always")
        is ActionCondition.VariableEquals -> JSONObject().apply {
            put("type", "variableEquals"); put("variableName", variableName); put("expectedValue", expectedValue)
        }
        is ActionCondition.Judgement -> JSONObject().put("type", "judgement").put("condition", condition.toJson())
    }

    private fun JSONObject?.toExecutionOptions(): ActionExecutionOptions {
        val condition = this?.optJSONObject("condition").toActionCondition() ?: ActionCondition.Always
        return ActionExecutionOptions(condition)
    }

    private fun JSONObject?.toActionCondition(): ActionCondition? = this?.let { value ->
        when (value.optString("type")) {
            "always" -> ActionCondition.Always
            "variableEquals" -> ActionCondition.VariableEquals(value.getString("variableName"), value.getString("expectedValue"))
            "judgement" -> value.optJSONObject("condition")?.toJudgementCondition()?.let(ActionCondition::Judgement)
            else -> null
        }
    }

    private fun JudgementCondition.toJson(): JSONObject = when (this) {
        is JudgementCondition.Variable -> JSONObject().apply { put("type", "variable"); put("variableName", variableName); put("operator", operator.name); put("expectedValue", expectedValue) }
        is JudgementCondition.OcrText -> JSONObject().apply {
            put("type", "ocrText")
            put("scope", scope.name)
            put("expectedText", expectedText)
            region?.let { put("region", it.toJson()) }
        }
        is JudgementCondition.Image -> JSONObject().apply {
            put("type", "image"); put("scope", scope.name); put("imageId", imageId); region?.let { put("region", it.toJson()) }
        }
        is JudgementCondition.RegionColor -> JSONObject().apply {
            put("type", "regionColor"); put("color", color); put("region", region.toJson()); put("tolerance", tolerance)
        }
    }

    private fun JSONObject.toJudgementCondition(): JudgementCondition? = when (optString("type")) {
        "variable" -> JudgementCondition.Variable(getString("variableName"), VariableComparisonOperator.valueOf(getString("operator")), getString("expectedValue"))
        "ocrText" -> JudgementCondition.OcrText(
            TextJudgementScope.valueOf(getString("scope")),
            getString("expectedText"),
            optRect("region")
        )
        "image" -> JudgementCondition.Image(ImageJudgementScope.valueOf(getString("scope")), getString("imageId"), optRect("region"))
        "regionColor" -> JudgementCondition.RegionColor(getString("color"), getRect("region"), optInt("tolerance", 0))
        else -> null
    }

    private fun Rect.toJson(): JSONObject = JSONObject().apply {
        put("left", left)
        put("top", top)
        put("right", right)
        put("bottom", bottom)
    }

    private fun JSONObject.optRect(name: String): Rect? {
        if (!has(name) || isNull(name)) return null
        return getRect(name)
    }

    private fun JSONObject.getRect(name: String): Rect {
        val value = get(name)
        return when (value) {
            is JSONObject -> Rect(value.getInt("left"), value.getInt("top"), value.getInt("right"), value.getInt("bottom"))
            is String -> value.split(',').map(String::trim).takeIf { it.size == 4 }?.map(String::toInt) ?.let { Rect(it[0], it[1], it[2], it[3]) }
                ?: throw IllegalArgumentException("Invalid rect")
            else -> throw IllegalArgumentException("Invalid rect")
        }.takeIf { it.width > 0 && it.height > 0 } ?: throw IllegalArgumentException("Invalid rect")
    }

    private fun Map<String, String>.toJson() = JSONObject().also { obj -> forEach { (key, value) -> obj.put(key, value) } }
    private fun JSONObject?.toStringMap(): Map<String, String> = this?.keys()?.asSequence()?.associateWith { optString(it) }.orEmpty()
    private fun List<ScriptAction>.toActionArray(): JSONArray = JSONArray().also { array -> forEach { action -> array.put(action.toJson()) } }
    private fun JSONArray?.toActionList(): List<ScriptAction> = this?.let { array ->
        buildList<ScriptAction> { for (index in 0 until array.length()) add(array.getJSONObject(index).toAction()) }
    }.orEmpty()
}
