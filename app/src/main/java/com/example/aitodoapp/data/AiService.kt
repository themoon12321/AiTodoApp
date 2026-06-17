package com.example.aitodoapp.data

import com.example.aitodoapp.Priority
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object AiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mediaType = "application/json".toMediaType()

    fun processMessage(userMessage: String): AiResult {
        val settings = SettingsRepository.load()
        if (settings.apiKey.isBlank()) return AiResult("请先在设置页填写 API Key")

        val body = buildJson {
            put("model", settings.model)
            put("messages", buildMessages(userMessage))
            put("tools", buildTools())
            put("tool_choice", "auto")
        }

        val responseBody = httpPost(settings.apiUrl, settings.apiKey, body)
            ?: return AiResult("网络请求失败，检查网络连接和 API 设置")

        return parseResponse(responseBody)
    }

    /** 构建请求体 JSON 字符串 */
    private fun httpPost(url: String, apiKey: String, jsonBody: String): String? {
        return try {
            val response = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType)).build()).execute()
            if (!response.isSuccessful) null else response.body?.string()
        } catch (_: Exception) { null }
    }

    // ======== JSON 构建 ========

    private fun buildMessages(userMessage: String): JsonArray = JsonArray(listOf(
        JsonObject(mapOf("role" to JsonPrimitive("system"), "content" to JsonPrimitive("""
你是 AI 代办助手。用户用自然语言告诉你做什么，你调用工具来操作。

规则：
- 用 create_task 创建任务，提取标题、优先级、截止日期、标签
- 优先级默认 P3，"紧急/加急"→P0或P1
- 截止日期：相对时间推算具体日期（今天${java.time.LocalDate.now()}）
- 标签从内容推断，如"高数作业"→["高数","作业"]
- 工具调用后，用中文告知用户结果
""".trimIndent()))),
        JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive(userMessage)))
    ))

    private fun buildTools(): JsonArray = JsonArray(listOf(
        JsonObject(mapOf(
            "type" to JsonPrimitive("function"),
            "function" to JsonObject(mapOf(
                "name" to JsonPrimitive("create_task"),
                "description" to JsonPrimitive("创建新任务，从用户自然语言中提取任务信息"),
                "parameters" to JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(mapOf(
                        "title" to JsonObject(mapOf("type" to JsonPrimitive("string"), "description" to JsonPrimitive("任务标题"))),
                        "priority" to JsonObject(mapOf("type" to JsonPrimitive("string"),
                            "enum" to JsonArray(Priority.entries.map { JsonPrimitive(it.name) }),
                            "description" to JsonPrimitive("优先级，默认P3"))),
                        "deadline" to JsonObject(mapOf("type" to JsonPrimitive("string"), "description" to JsonPrimitive("截止日期 YYYY-MM-DD，用户说'后天''下周一'等时推算具体日期"))),
                        "tags" to JsonObject(mapOf("type" to JsonPrimitive("array"), "items" to JsonObject(mapOf("type" to JsonPrimitive("string"))), "description" to JsonPrimitive("标签列表")))
                    )),
                    "required" to JsonArray(listOf(JsonPrimitive("title")))
                ))
            ))
        ))
    ))

    // ======== JSON 响应解析 ========

    private fun parseResponse(body: String): AiResult {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return AiResult("AI 返回为空")
            val message = choice["message"]?.jsonObject ?: return AiResult("无法解析 AI 响应")

            val content = message["content"]?.jsonPrimitive?.content
            val toolCalls = message["tool_calls"]?.jsonArray

            val actions = mutableListOf<AiAction>()
            if (toolCalls != null) {
                for (tc in toolCalls) {
                    val func = tc.jsonObject["function"]?.jsonObject ?: continue
                    val name = func["name"]?.jsonPrimitive?.content ?: continue
                    val argsStr = func["arguments"]?.jsonPrimitive?.content ?: continue
                    if (name == "create_task") {
                        val args = json.parseToJsonElement(argsStr).jsonObject
                        val title = (args["title"] as? JsonPrimitive)?.content ?: continue
                        val priority = try { Priority.valueOf((args["priority"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { Priority.P3 }
                        val deadline = try { java.time.LocalDate.parse((args["deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                        val tagArr = args["tags"] as? JsonArray
                        val tags = tagArr?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                        actions.add(AiAction.CreateTask(title, priority, deadline, tags))
                    }
                }
            }
            AiResult(text = content ?: "已完成", actions = actions)
        } catch (_: Exception) { AiResult("处理 AI 响应时出错") }
    }
}

// ======== 辅助：构建 JSON 对象 ========

private fun buildJson(block: MutableMap<String, JsonElement>.() -> Unit): String {
    val map = mutableMapOf<String, JsonElement>()
    block(map)
    return JsonObject(map).toString()
}

private fun MutableMap<String, JsonElement>.put(key: String, value: String) { this[key] = JsonPrimitive(value) }
private fun MutableMap<String, JsonElement>.put(key: String, value: JsonElement) { this[key] = value }

data class AiResult(val text: String, val actions: List<AiAction> = emptyList())
sealed class AiAction {
    data class CreateTask(val title: String, val priority: Priority, val deadline: java.time.LocalDate?, val tags: List<String>) : AiAction()
}
