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

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; prettyPrint = false }
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

    fun generateDailyReport(currentTasks: List<String>, currentTags: List<String>, isMorning: Boolean): AiResult {
        val settings = SettingsRepository.load()
        if (settings.apiKey.isBlank()) return AiResult("请先在设置页填写 API Key")

        val taskList = currentTasks.joinToString("\n") { "- $it" }.ifEmpty { "（暂无任务）" }
        val tagList = currentTags.joinToString("\n") { "- $it" }.ifEmpty { "（暂无标签）" }

        val requestJson = buildReportRequestJson(isMorning, taskList, tagList, settings.model)
        val responseBody = httpPost(settings.apiUrl, settings.apiKey, requestJson)
            ?: return AiResult("网络请求失败，检查网络连接和 API 设置")

        val result = parseResponse(responseBody)
        return result.copy(text = result.text.ifEmpty { "生成失败，请重试" })
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
- update_settings：修改设置。参数: api_url, api_key, model, show_overdue_inline(过期分区), long_press_chat(长按对话), show_token_usage(显示Token), auto_sync_calendar(日历同步), default_reminder_minutes(提醒分钟)
- manage_tag：管理标签。参数: action(create/delete/promote), tag_name(标签名)
- archive_task：归档任务。参数同delete_task（按多字段精确定位）
- unarchive_task：取消归档。参数同delete_task（按多字段精确定位）

规则：
- 【工具选择】先判断用户意图再选工具。
  ① 含"删/移除/去掉"→delete_task
  ② 含"完成/做好了/搞定了/做完了"→complete_task
  ③ 含"改/修改/换/移到"→update_task
  ④ 用户询问完成了哪些任务/已完成的任务→completed_tasks
  ⑤ 含"归档/收起来"→archive_task，含"取消归档/恢复"→unarchive_task
  ⑥ 含"标签/分类"→manage_tag
  ⑦ 含"设置/配置/API"→update_settings
  ⑧ 其他→create_task
- 任务列表中标记了 [已完成] 的是已勾掉的任务，[过期] 的是已过期未完成的任务
- 【标题精简】title 只保留任务核心名称，去掉时间词。如"明天下午开会"→"开会"，"后天交实验报告"→"交实验报告"
- 【时间提取】从用户话中提取时间信息放到 planned_dates（计划时间）或 deadline（截止）中，不留在标题里。基于当前日期时间计算"今天/明天/后天/下周/上午/下午"等相对时间。提取具体时间时，deadline 设为 YYYY-MM-DD，deadline_time 设为 HH:mm（5分钟倍数）。用户只说日期没说具体时间时不要设置 deadline_time（设为全天）；明确说了几点或"上午/下午/晚上"等词才设时间
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
                            putJsonObject("deadline_time") { put("type", "string"); put("description", "截止时间 HH:mm（可选，根据语义推断：上午→09:00、中午→11:00、下午→14:00、晚上→18:00）") }
                            putJsonObject("estimated_minutes") { put("type", "integer"); put("description", "预估耗时分钟（可选，AI根据任务内容估算：写实验报告120、买菜30、开会60）") }
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
                            // 匹配过滤字段
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
            // update_settings
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "update_settings")
                    put("description", "修改设置")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("api_url") { put("type", "string"); put("description", "API 地址（可选）") }
                            putJsonObject("api_key") { put("type", "string"); put("description", "API Key（可选）") }
                            putJsonObject("model") { put("type", "string"); put("description", "模型名称（可选）") }
                            putJsonObject("show_overdue_inline") { put("type", "boolean"); put("description", "过期任务是否分区显示（可选）") }
                            putJsonObject("long_press_chat") { put("type", "boolean"); put("description", "长按打开AI对话框（可选）") }
                            putJsonObject("show_token_usage") { put("type", "boolean"); put("description", "显示Token用量（可选）") }
                            putJsonObject("auto_sync_calendar") { put("type", "boolean"); put("description", "自动同步系统日历（可选）") }
                            putJsonObject("default_reminder_minutes") { put("type", "integer"); put("description", "默认提醒提前分钟数 0-120（可选）") }
                        }
                        putJsonArray("required") {}
                    }
                }
            }
            // manage_tag
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "manage_tag")
                    put("description", "管理标签：创建/删除/转正")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("action") { put("type", "string"); putJsonArray("enum") { add(JsonPrimitive("create")); add(JsonPrimitive("delete")); add(JsonPrimitive("promote")) }; put("description", "操作类型：create创建/delete删除/promote转正") }
                            putJsonObject("tag_name") { put("type", "string"); put("description", "标签名") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("action")); add(JsonPrimitive("tag_name")) }
                    }
                }
            }
            // archive_task
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "archive_task")
                    put("description", "归档任务。支持多字段精确定位，可指定归档日期")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "string"); put("description", "任务标题或关键词（必填）") }
                            putJsonObject("deadline") { put("type", "string"); put("description", "匹配的截止日期 YYYY-MM-DD（可选）") }
                            putJsonObject("deadline_time") { put("type", "string"); put("description", "匹配的截止时间 HH:mm（可选）") }
                            putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "匹配的标签（可选）") }
                            putJsonObject("content") { put("type", "string"); put("description", "匹配的描述关键词（可选）") }
                            putJsonObject("planned_date") { put("type", "string"); put("description", "匹配的计划日期 YYYY-MM-DD（可选）") }
                            putJsonObject("completed_at") { put("type", "string"); put("description", "归档日期 YYYY-MM-DD（可选，默认今天）") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("title")) }
                    }
                }
            }
            // unarchive_task
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", "unarchive_task")
                    put("description", "取消归档，将任务恢复至活跃列表。支持多字段精确定位")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "string"); put("description", "任务标题或关键词（必填）") }
                            putJsonObject("deadline") { put("type", "string"); put("description", "匹配的截止日期 YYYY-MM-DD（可选）") }
                            putJsonObject("deadline_time") { put("type", "string"); put("description", "匹配的截止时间 HH:mm（可选）") }
                            putJsonObject("tags") { put("type", "array"); putJsonObject("items") { put("type", "string") }; put("description", "匹配的标签（可选）") }
                            putJsonObject("content") { put("type", "string"); put("description", "匹配的描述关键词（可选）") }
                            putJsonObject("planned_date") { put("type", "string"); put("description", "匹配的计划日期 YYYY-MM-DD（可选）") }
                        }
                        putJsonArray("required") { add(JsonPrimitive("title")) }
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

    /** 生成日报时用独立的 system prompt（不带工具定义，纯文本生成） */
    private fun buildReportRequestJson(isMorning: Boolean, taskList: String, tagList: String, model: String): String {
        val now = java.time.LocalDateTime.now()
        val today = now.toLocalDate()
        val dayNames = arrayOf("", "一", "二", "三", "四", "五", "六", "日")
        val dayOfWeekChinese = dayNames[today.dayOfWeek.value]
        val period = if (isMorning) "早间" else "晚间"
        val emoji = if (isMorning) "🌅" else "🌙"
        val eveningSummary = if (!isMorning) "📊 今日任务小结\n- 已完成: 1项 | 总任务: 5项 | 待办: 4项\n- 完成率: 20% 🎯\n" else ""

        val systemPrompt = """你是 AI 代办助手。根据当前任务列表生成${period}播报。
严格按照下面的格式输出。

---
${emoji} ${period}代办报告 · ${today}（星期$dayOfWeekChinese）
---

📋 今日待办提醒
${if (isMorning) "暂无当日截止任务，好好规划今天的工作吧！" else "回顾今天的完成情况。"}

🔥 本周紧急任务（截止本周内）
● 交大物实验报告, 优先级: P0🔥, 截止: 7/3（周四）, 预估: 2h
● 买数据线, 优先级: P4⚪, 截止: 7/5（周六）, 预估: 0.5h

⚠️ 过期任务提醒（请尽快处理）
● 洗衣服, 优先级: P2🟡, 已过期 2天, 预估: 1h

${eveningSummary}---
💡 ${if (isMorning) "早间" else "智能"}小贴士
📌 今日重点：根据任务优先级给出具体可行的建议
⏰ 时间分配：给出上午/下午/晚间的分段时间安排
🌟 温馨提示：有温度有情绪的鼓励语

---
💬 ${if (isMorning) "根据任务情况写一句独一无二的早安问候，不要客套，可以带点俏皮、温暖或务实的语气，符合今天的任务状态" else "根据今天完成情况写一句温暖的晚安问候，具体到今天的任务表现，不套路"}

规则：
- 任务较多时只列出最关键的前5项
- 没有任务时写"暂无当日截止任务"等自然文案
- ● 行格式：● 任务名称, 优先级: PX+emoji, 截止: 日期, 预估: Xh
- 过期任务写"已过期 X天"而非截止日期
- 💡 小贴士的 📌⏰🌟 不需要前置 - 符号
- 💬 行结合当天具体任务写，不写通用祝福语

当前任务列表：
$taskList

现有标签：
$tagList
""".trimIndent()

        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", systemPrompt) }
                addJsonObject { put("role", "user"); put("content", "请根据当前任务生成${period}播报") }
            }
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
                            val em = (args["estimated_minutes"] as? JsonPrimitive)?.content?.toIntOrNull()
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
                            val pl = (args["planned_dates"] as? JsonArray)?.mapNotNull { try { java.time.LocalDate.parse((it as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null } } ?: emptyList()
                            actions.add(AiAction.CreateTask(t, c, p, d, tags, pl, dt, em))
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
                        "update_settings" -> {
                            val apiUrl = (args["api_url"] as? JsonPrimitive)?.content
                            val apiKey = (args["api_key"] as? JsonPrimitive)?.content
                            val model = (args["model"] as? JsonPrimitive)?.content
                            val showOverdue = (args["show_overdue_inline"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                            val longPress = (args["long_press_chat"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                            val showToken = (args["show_token_usage"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                            val autoSync = (args["auto_sync_calendar"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                            val reminder = (args["default_reminder_minutes"] as? JsonPrimitive)?.content?.toIntOrNull()
                            actions.add(AiAction.UpdateSettings(apiUrl, apiKey, model, showOverdue, longPress, showToken, autoSync, reminder))
                        }
                        "manage_tag" -> {
                            val act = (args["action"] as? JsonPrimitive)?.content ?: continue
                            val tn = (args["tag_name"] as? JsonPrimitive)?.content ?: continue
                            actions.add(AiAction.ManageTag(act, tn))
                        }
                        "archive_task" -> {
                            val t = (args["title"] as? JsonPrimitive)?.content ?: continue
                            val dl = try { java.time.LocalDate.parse((args["deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val dlt = (args["deadline_time"] as? JsonPrimitive)?.content
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                            val ct = (args["content"] as? JsonPrimitive)?.content
                            val pd = try { java.time.LocalDate.parse((args["planned_date"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val ca = try { java.time.LocalDate.parse((args["completed_at"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            actions.add(AiAction.ArchiveTask(t, dl, dlt, tags, ct, pd, ca))
                        }
                        "unarchive_task" -> {
                            val t = (args["title"] as? JsonPrimitive)?.content ?: continue
                            val dl = try { java.time.LocalDate.parse((args["deadline"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            val dlt = (args["deadline_time"] as? JsonPrimitive)?.content
                            val tags = (args["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
                            val ct = (args["content"] as? JsonPrimitive)?.content
                            val pd = try { java.time.LocalDate.parse((args["planned_date"] as? JsonPrimitive)?.content ?: "") } catch (_: Exception) { null }
                            actions.add(AiAction.UnarchiveTask(t, dl, dlt, tags, ct, pd))
                        }
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

