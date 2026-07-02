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
                    put("description", "归档任务。支持多字段精确定位")
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

        val systemPrompt = """你是一个温暖的 AI 代办助手。根据用户当前的任务列表，用自然语言生成一份【${period}播报】。
严格按照下面的模板结构和格式输出。

模板示例（${emoji} ${period}代办报告）：

🌅 早间代办报告 · ${today}（星期$dayOfWeekChinese）

📋 今日待办提醒
买数据线
- 优先级: P4⚪, 截止日期: 今天 7月3日, 预估时长: 0.2h
交大物实验报告
- 优先级: P0🔥, 截止日期: 今天 7月3日, 预估时长: 2h

🔥 本周紧急任务
机械建模作业
- 状态: 🔴 待办, 截止: 7月4日, 预估时长: 3h

💡 早间小贴士
📌 今日重点：今天有X项待办需要完成，优先处理P0任务
⏰ 时间分配建议：
- 上午：先完成...
- 下午：集中处理...
- 晚间：...
🌟 温馨提示：一句温暖的鼓励
💬 早安呀～一句温暖问候

---晚间模板不同之处---
如果是晚间播报，在📋待办前增加：

📊 今日任务小结
- 总任务: X项 | 已完成: X项 | 待办: X项
- 完成率: X% 🎯

然后增加：

💡 智能提示
（一段提醒文字）

🗓️ 明日行动建议
（建议内容）

💌 温馨提示
（一句晚安问候）

规则：
- 标题格式：${emoji} ${period}代办报告 · 日期（星期X）
- 每个 section 用 emoji 作为视觉分隔：📋🔥💡📊🗓️💌💬
- 任务行格式：任务名称单独一行，属性另起一行以"- "开头
- 属性格式：优先级: PX+emoji, 截止日期: XX, 预估时长: Xh
- 任务较多时只列出最关键的前5项
- 语气温暖、鼓励、带情绪价值
- 最后一句问候要自然有温度

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
                            actions.add(AiAction.ArchiveTask(t, dl, dlt, tags, ct, pd))
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

data class AiResult(val text: String, val actions: List<AiAction> = emptyList(), val promptTokens: Int = 0, val completionTokens: Int = 0)

// ============ 扩展后的 AiAction ============

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
    // 设置
    data class UpdateSettings(
        val apiUrl: String? = null,
        val apiKey: String? = null,
        val model: String? = null,
        val showOverdueInline: Boolean? = null,
        val longPressChat: Boolean? = null,
        val showTokenUsage: Boolean? = null,
        val autoSyncCalendar: Boolean? = null,
        val defaultReminderMinutes: Int? = null
    ) : AiAction()
    // 标签管理
    data class ManageTag(val action: String, val tagName: String) : AiAction()
    // 归档 / 取消归档
    data class ArchiveTask(val title: String, val deadline: java.time.LocalDate? = null, val deadlineTime: String? = null, val tags: List<String>? = null, val content: String? = null, val plannedDate: java.time.LocalDate? = null) : AiAction()
    data class UnarchiveTask(val title: String, val deadline: java.time.LocalDate? = null, val deadlineTime: String? = null, val tags: List<String>? = null, val content: String? = null, val plannedDate: java.time.LocalDate? = null) : AiAction()
}
