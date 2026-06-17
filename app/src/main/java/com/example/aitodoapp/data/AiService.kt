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

    fun processMessage(userMessage: String, currentTasks: List<String> = emptyList(), currentTags: List<String> = emptyList()): AiResult {
        val settings = SettingsRepository.load()
        if (settings.apiKey.isBlank()) return AiResult("请先在设置页填写 API Key")

        val taskList = currentTasks.joinToString("\n") { "- $it" }.ifEmpty { "（暂无任务）" }
        val tagList = currentTags.joinToString("\n") { "- $it" }.ifEmpty { "（暂无标签）" }

        val requestJson = buildRequestJson(userMessage, taskList, tagList, settings.model)
        val responseBody = httpPost(settings.apiUrl, settings.apiKey, requestJson)
            ?: return AiResult("网络请求失败，检查网络连接和 API 设置")

        return parseResponse(responseBody)
    }

    private fun httpPost(url: String, apiKey: String, jsonBody: String): String? {
        return try {
            val response = client.newCall(Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType)).build()).execute()
            if (!response.isSuccessful) null else response.body?.string()
        } catch (_: Exception) { null }
    }

    // ======== JSON 构建（字符串拼接） ========

    private fun buildRequestJson(userMessage: String, taskList: String, tagList: String, model: String): String {
        val systemPrompt = """
你是 AI 代办助手。用户用自然语言告诉你做什么，你调用工具来操作。如果是询问任务列表等不需要操作的问题，直接文字回复即可。

当前任务列表：
$taskList

现有标签：
$tagList

可用工具：
- create_task：创建任务。参数 title(标题), priority(P0-P4), deadline(日期), tags(标签数组)
- complete_task：标记任务已完成。参数 title(任务标题或关键词)
- delete_task：删除任务。参数 title(任务标题或关键词)
- update_task：修改任务。参数 title(要修改的任务关键词), new_title(新标题), priority(新优先级), deadline(新日期), tags(新标签)

规则：
- 【标题精简】title 简短明确，不超过15字，提取核心内容
- 【内容生成】content 字段给出任务的详细描述、做法建议、注意事项等（50-100字），如"交大物实验报告"→"先处理数据，再画图表，最后写结论。注意实验报告格式要求"
- title 匹配时模糊匹配，如"实验"→"交大物实验报告"
- 【计划日期】planned_dates 是用户打算在哪几天做。如"今明两天做"→[今天,明天]，不明确则留空
- 【优先级多维度判断】综合考虑：
  ① 时间紧迫度（40%）：今天/已过期→P0，明天→P1，3天内→P2，7天内→P3，更远→P4
  ② 用户语气（20%）："紧急/加急/尽快/马上"+1~2级
  ③ 任务性质（20%）：学业/考试类默认偏紧（P2），生活琐事偏松（P3-P4）
  ④ 综合结果：默认P3，有DDL紧迫+P0~P2，语气急+提级
- 截止日期："后天/下周一"等推算具体日期（今天${java.time.LocalDate.now()}）
- 【标签策略】优先选现有标签，最多创建1-2个有分类价值的临时标签，不要为每个任务都创标签
- 工具调用后用中文告知用户结果
""".trimIndent()

        val toolsJson = """
[
  {"type":"function","function":{"name":"create_task","description":"创建新任务","parameters":{"type":"object","properties":{"title":{"type":"string","description":"任务标题（不超过15字）"},"content":{"type":"string","description":"任务详细描述、做法建议、注意事项等（可选）"},"priority":{"type":"string","enum":["P0","P1","P2","P3","P4"],"description":"优先级"},"deadline":{"type":"string","description":"截止日期 YYYY-MM-DD"},"planned_dates":{"type":"array","items":{"type":"string"},"description":"计划在哪几天做，YYYY-MM-DD数组，如[\"2026-06-17\",\"2026-06-18\"]"},"tags":{"type":"array","items":{"type":"string"},"description":"标签"}},"required":["title"]}}},
  {"type":"function","function":{"name":"complete_task","description":"标记任务已完成","parameters":{"type":"object","properties":{"title":{"type":"string","description":"任务标题或关键词"}},"required":["title"]}}},
  {"type":"function","function":{"name":"delete_task","description":"删除任务","parameters":{"type":"object","properties":{"title":{"type":"string","description":"任务标题或关键词"}},"required":["title"]}}},
  {"type":"function","function":{"name":"update_task","description":"修改任务的属性","parameters":{"type":"object","properties":{"title":{"type":"string","description":"要修改的任务标题或关键词"},"new_title":{"type":"string","description":"新标题"},"priority":{"type":"string","enum":["P0","P1","P2","P3","P4"],"description":"新优先级"},"deadline":{"type":"string","description":"新截止日期 YYYY-MM-DD"},"tags":{"type":"array","items":{"type":"string"},"description":"新标签"}},"required":["title"]}}}
]
""".trimIndent()

        val escPrompt = escapeJson(systemPrompt)
        val escUser = escapeJson(userMessage)

        return """
{"model":"$model","messages":[{"role":"system","content":"$escPrompt"},{"role":"user","content":"$escUser"}],"tools":$toolsJson,"tool_choice":"auto"}
""".trimIndent()
    }

    private fun escapeJson(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) { sb.append("\\u%04x".format(c.code)) } else { sb.append(c) }
            }
        }
        return sb.toString()
    }

    // ======== 响应解析 ========

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
                    val args = try { json.parseToJsonElement(argsStr).jsonObject } catch (_: Exception) { continue }
                    when (name) {
                        "create_task" -> {
                            val t = (args["title"] as? JsonPrimitive)?.content ?: continue
                            val c = (args["content"] as? JsonPrimitive)?.content ?: ""
                            val p = try { Priority.valueOf((args["priority"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { Priority.P3 }
                            val d = try { java.time.LocalDate.parse((args["deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                            val pl = (args["planned_dates"] as? JsonArray)?.mapNotNull { try { java.time.LocalDate.parse((it as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null } } ?: emptyList()
                            actions.add(AiAction.CreateTask(t, c, p, d, tags, pl))
                        }
                        "complete_task" -> (args["title"] as? JsonPrimitive)?.content?.let { actions.add(AiAction.CompleteTask(it)) }
                        "delete_task" -> (args["title"] as? JsonPrimitive)?.content?.let { actions.add(AiAction.DeleteTask(it)) }
                        "update_task" -> {
                            val t = (args["title"] as? JsonPrimitive)?.content ?: continue
                            val nt = (args["new_title"] as? JsonPrimitive)?.content
                            val p = try { Priority.valueOf((args["priority"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val d = try { java.time.LocalDate.parse((args["deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                            actions.add(AiAction.UpdateTask(t, nt, p, d, tags))
                        }
                    }
                }
            }
            AiResult(text = content ?: "已完成", actions = actions)
        } catch (_: Exception) { AiResult("处理 AI 响应时出错") }
    }
}

data class AiResult(val text: String, val actions: List<AiAction> = emptyList())
sealed class AiAction {
    data class CreateTask(val title: String, val content: String = "", val priority: Priority, val deadline: java.time.LocalDate?, val tags: List<String>, val plannedDates: List<java.time.LocalDate> = emptyList()) : AiAction()
    data class CompleteTask(val title: String) : AiAction()
    data class DeleteTask(val title: String) : AiAction()
    data class UpdateTask(val title: String, val newTitle: String?, val priority: Priority?, val deadline: java.time.LocalDate?, val tags: List<String>?) : AiAction()
}
