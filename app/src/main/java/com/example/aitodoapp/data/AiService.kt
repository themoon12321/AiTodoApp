package com.example.aitodoapp.data

import com.example.aitodoapp.Priority
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object AiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

    // ======== JSON 构建 ========

    private fun buildRequestJson(userMessage: String, taskList: String, tagList: String, model: String): String {
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate()
        val dayNames = arrayOf("", "一", "二", "三", "四", "五", "六", "日")
        val dayOfWeekChinese = dayNames[today.dayOfWeek.value]
        val timeStr = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
        val systemPrompt = """
你是 AI 代办助手。用户用自然语言告诉你做什么，你调用工具来操作。如果是询问任务列表等不需要操作的问题，直接文字回复即可。

当前时间：${today}（${today.monthValue}月${today.dayOfMonth}日 星期$dayOfWeekChinese $timeStr）

当前任务列表（每行含截止时间/标签/计划日期/描述等）：
$taskList

现有标签：
$tagList

可用工具：
- create_task：创建任务。参数: title(标题), priority(P0-P4), deadline(日期), deadline_time(时间), tags(标签数组), content(描述), planned_dates(计划日期)
- complete_task：标记完成。必填title(关键词)，可传deadline/deadline_time/tags/content/planned_date精确定位
- delete_task：删除任务。必填title(关键词)，可传deadline/deadline_time/tags/content/planned_date精确定位
- update_task：修改任务。必填title(关键词)，可传match_deadline/match_tags等定位，传new_title/priority等修改
- completed_tasks：查看所有已完成任务（无需参数）

规则：
- 【工具选择】先判断用户意图再选工具。
  ① 含"删/移除/去掉"→delete_task
  ② 含"完成/做好了/搞定了/做完了"→complete_task
  ③ 含"改/修改/换/移到"→update_task
  ④ 用户询问完成了哪些任务/已完成的任务→completed_tasks
  ⑤ 其他→create_task
- 任务列表中标记了 [已完成] 的是已勾掉的任务，[过期] 的是已过期未完成的任务
- 【标题精简】title 只保留任务核心名称，去掉时间词。如"明天下午开会"→"开会"，"后天交实验报告"→"交实验报告"
- 【时间提取】从用户话中提取时间信息放到 planned_dates（计划时间）或 deadline（截止）中，不留在标题里。基于当前日期时间计算"今天/明天/后天/下周/上午/下午"等相对时间。提取具体时间时，deadline 设为 YYYY-MM-DD，deadline_time 设为 HH:mm（5分钟倍数）。注意：如果是考试/会议/活动等有明确时间的场景，planned_dates 和 deadline 应同时设为该日期
- 【内容生成】content 字段给出任务的详细描述、做法建议、注意事项等（20-100字）
- 【多字段精确匹配】标题相似时用 deadline/tags/content/planned_date 精确定位。用户提到时间/标签/内容时填入对应参数
- 【计划日期】planned_dates 是用户打算在哪几天做。如"今明两天做"→[今天,明天]
- 【优先级多维度判断】综合考虑：时间紧迫度(40%)+用户语气(20%)+任务性质(20%)
- 【标签策略】优先选现有标签，最多创建1-2个有分类价值的临时标签
- 【不要反问】信息不全直接做，缺的字段不填，一次完成。尤其是用户粘贴一段文字过来时，直接当做任务创建，不要问"是否需要添加"
- 工具调用后用中文告知用户结果
""".trimIndent()

        val toolsArray = buildJsonArray {
            // create_task
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "create_task")
                    put("description", "创建新任务")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "string"); put("description", "任务标题（不超过15字）") }
                            putJsonObject("content") { put("type", "string"); put("description", "任务详细描述、做法建议、注意事项等（可选）") }
                            putJsonObject("priority") { put("type", "string"); putJsonArray("enum") { add(JsonPrimitive("P0")); add(JsonPrimitive("P1")); add(JsonPrimitive("P2")); add(JsonPrimitive("P3")); add(JsonPrimitive("P4")) }; put("description", "优先级") }
                            putJsonObject("deadline") { put("type", "string"); put("description", "截止日期 YYYY-MM-DD") }
                            putJsonObject("deadline_time") { put("type", "string"); put("description", "截止时间 HH:mm（可选）") }
                            putJsonObject("planned_dates") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "计划在哪几天做，YYYY-MM-DD数组") }
                            putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "标签") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("title")) }
                    }
                }
            }
            // complete_task（支持多字段匹配）
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "complete_task")
                    put("description", "标记任务已完成。支持通过标题+时间/标签等多字段精确定位")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "string"); put("description", "任务标题或关键词（必填）") }
                            putJsonObject("deadline") { put("type", "string"); put("description", "匹配的截止日期 YYYY-MM-DD（可选，用于精确查找）") }
                            putJsonObject("deadline_time") { put("type", "string"); put("description", "匹配的截止时间 HH:mm（可选）") }
                            putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "匹配的标签（可选）") }
                            putJsonObject("content") { put("type", "string"); put("description", "匹配的描述关键词（可选，模糊匹配）") }
                            putJsonObject("planned_date") { put("type", "string"); put("description", "匹配的计划日期 YYYY-MM-DD（可选）") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("title")) }
                    }
                }
            }
            // delete_task（支持多字段匹配）
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "delete_task")
                    put("description", "删除任务。支持通过标题+时间/标签等多字段精确定位")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "string"); put("description", "任务标题或关键词（必填）") }
                            putJsonObject("deadline") { put("type", "string"); put("description", "匹配的截止日期 YYYY-MM-DD（可选，用于精确查找）") }
                            putJsonObject("deadline_time") { put("type", "string"); put("description", "匹配的截止时间 HH:mm（可选）") }
                            putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "匹配的标签（可选）") }
                            putJsonObject("content") { put("type", "string"); put("description", "匹配的描述关键词（可选，模糊匹配）") }
                            putJsonObject("planned_date") { put("type", "string"); put("description", "匹配的计划日期 YYYY-MM-DD（可选）") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("title")) }
                    }
                }
            }
            // update_task（支持多字段匹配 + 多字段修改）
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "update_task")
                    put("description", "修改任务的属性。支持通过多字段定位+修改")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "string"); put("description", "要修改的任务标题或关键词（必填）") }
                            // 匹配过滤字段（用于精确查找要修改的任务）
                            putJsonObject("match_deadline") { put("type", "string"); put("description", "匹配的截止日期 YYYY-MM-DD（可选，用于定位）") }
                            putJsonObject("match_deadline_time") { put("type", "string"); put("description", "匹配的截止时间 HH:mm（可选，用于定位）") }
                            putJsonObject("match_tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "匹配的标签（可选，用于定位）") }
                            putJsonObject("match_content") { put("type", "string"); put("description", "匹配的描述关键词（可选，用于定位）") }
                            putJsonObject("match_planned_date") { put("type", "string"); put("description", "匹配的计划日期 YYYY-MM-DD（可选，用于定位）") }
                            // 修改字段
                            putJsonObject("new_title") { put("type", "string"); put("description", "新标题") }
                            putJsonObject("priority") { put("type", "string"); putJsonArray("enum") { add(JsonPrimitive("P0")); add(JsonPrimitive("P1")); add(JsonPrimitive("P2")); add(JsonPrimitive("P3")); add(JsonPrimitive("P4")) }; put("description", "新优先级") }
                            putJsonObject("deadline") { put("type", "string"); put("description", "新截止日期 YYYY-MM-DD") }
                            putJsonObject("deadline_time") { put("type", "string"); put("description", "新截止时间 HH:mm") }
                            putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "新标签") }
                            putJsonObject("planned_dates") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "新计划日期 YYYY-MM-DD 数组") }
                            putJsonObject("planned_times") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "新计划时间 HH:mm 数组") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("title")) }
                    }
                }
            }
            // completed_tasks
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "completed_tasks")
                    put("description", "查看所有已完成的任务")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {}
                        putJsonArray("required") {}
                    }
                }
            }
        }

        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", systemPrompt) }
                addJsonObject { put("role", "user"); put("content", userMessage) }
            }
            put("tools", toolsArray)
            put("tool_choice", "auto")
        }

        return json.encodeToString(requestBody)
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
                            val dt = (args["deadline_time"] as? JsonPrimitive)?.content
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                            val pl = (args["planned_dates"] as? JsonArray)?.mapNotNull { try { java.time.LocalDate.parse((it as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null } } ?: emptyList()
                            actions.add(AiAction.CreateTask(t, c, p, d, tags, pl, dt))
                        }
                        "complete_task" -> {
                            val t = (args["title"] as? JsonPrimitive)?.content ?: continue
                            val dl = try { java.time.LocalDate.parse((args["deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val dlt = (args["deadline_time"] as? JsonPrimitive)?.content
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                            val ct = (args["content"] as? JsonPrimitive)?.content
                            val pd = try { java.time.LocalDate.parse((args["planned_date"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            actions.add(AiAction.CompleteTask(t, dl, dlt, tags, ct, pd))
                        }
                        "delete_task" -> {
                            val t = (args["title"] as? JsonPrimitive)?.content ?: continue
                            val dl = try { java.time.LocalDate.parse((args["deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val dlt = (args["deadline_time"] as? JsonPrimitive)?.content
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                            val ct = (args["content"] as? JsonPrimitive)?.content
                            val pd = try { java.time.LocalDate.parse((args["planned_date"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            actions.add(AiAction.DeleteTask(t, dl, dlt, tags, ct, pd))
                        }
                        "update_task" -> {
                            val t = (args["title"] as? JsonPrimitive)?.content ?: continue
                            val md = try { java.time.LocalDate.parse((args["match_deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val mdt = (args["match_deadline_time"] as? JsonPrimitive)?.content
                            val mt = (args["match_tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                            val mc = (args["match_content"] as? JsonPrimitive)?.content
                            val mpd = try { java.time.LocalDate.parse((args["match_planned_date"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val nt = (args["new_title"] as? JsonPrimitive)?.content
                            val p = try { Priority.valueOf((args["priority"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val d = try { java.time.LocalDate.parse((args["deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val dlt = (args["deadline_time"] as? JsonPrimitive)?.content
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                            val pl = (args["planned_dates"] as? JsonArray)?.mapNotNull { try { java.time.LocalDate.parse((it as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null } }
                            val pt = (args["planned_times"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                            actions.add(AiAction.UpdateTask(t, md, mdt, mt, mc, mpd, nt, p, d, dlt, tags, pl, pt))
                        }
                        "completed_tasks" -> actions.add(AiAction.CompletedTasks)
                    }
                }
            }
            val usage = root["usage"]?.jsonObject
            val promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            AiResult(text = content ?: "已完成", actions = actions, promptTokens = promptTokens, completionTokens = completionTokens)
        } catch (_: Exception) { AiResult("处理 AI 响应时出错") }
    }
}

data class AiResult(val text: String, val actions: List<AiAction> = emptyList(), val promptTokens: Int = 0, val completionTokens: Int = 0)

// ============ 扩展后的 AiAction ============

/**
 * 多字段匹配规则：
 * - title：必填关键词（标题/内容模糊匹配）
 * - deadline/deadline_time/tags/content/planned_date：可选过滤条件
 * - 所有条件同时评分，取最高分（≥阈值）
 * - 平局时返回 null，避免误操作
 */
sealed class AiAction {
    data class CreateTask(val title: String, val content: String = "", val priority: Priority, val deadline: java.time.LocalDate?, val tags: List<String>, val plannedDates: List<java.time.LocalDate> = emptyList(), val deadlineTime: String? = null) : AiAction()
    data class CompleteTask(val title: String, val deadline: java.time.LocalDate? = null, val deadlineTime: String? = null, val tags: List<String>? = null, val content: String? = null, val plannedDate: java.time.LocalDate? = null) : AiAction()
    data class DeleteTask(val title: String, val deadline: java.time.LocalDate? = null, val deadlineTime: String? = null, val tags: List<String>? = null, val content: String? = null, val plannedDate: java.time.LocalDate? = null) : AiAction()
    data class UpdateTask(
        val title: String,
        val matchDeadline: java.time.LocalDate? = null,
        val matchDeadlineTime: String? = null,
        val matchTags: List<String>? = null,
        val matchContent: String? = null,
        val matchPlannedDate: java.time.LocalDate? = null,
        val newTitle: String? = null,
        val priority: Priority? = null,
        val deadline: java.time.LocalDate? = null,
        val deadlineTime: String? = null,
        val tags: List<String>? = null,
        val plannedDates: List<java.time.LocalDate>? = null,
        val plannedTimes: List<String>? = null
    ) : AiAction()
    data object CompletedTasks : AiAction()
}
