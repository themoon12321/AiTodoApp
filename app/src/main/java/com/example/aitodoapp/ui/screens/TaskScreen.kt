package com.example.aitodoapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.aitodoapp.DayFilter
import com.example.aitodoapp.MainViewModel
import com.example.aitodoapp.MatchCriteria
import com.example.aitodoapp.Priority
import com.example.aitodoapp.Tag
import com.example.aitodoapp.Task
import com.example.aitodoapp.findBestMatch
import com.example.aitodoapp.formatTaskForAi
import com.example.aitodoapp.data.AiAction
import com.example.aitodoapp.data.AiService
import com.example.aitodoapp.data.NotificationHelper
import com.example.aitodoapp.data.ReportRepository
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.data.TokenRepository
import com.example.aitodoapp.data.toMatchCriteria
import com.example.aitodoapp.ui.components.AddTaskDialog
import com.example.aitodoapp.ui.components.EditTaskDialog
import com.example.aitodoapp.ui.components.ReportBadge
import com.example.aitodoapp.ui.components.TaskItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    openReportTrigger: Boolean = false,
    onClearReportTrigger: () -> Unit = {}
) {
    val today = LocalDate.now()
    val tasks = viewModel.activeTasks
    val allTags = viewModel.allTags
    val overdueTasks = viewModel.overdueTasks
    val selectedDay = viewModel.selectedDay
    val settings = viewModel.settings

    var showInput by remember { mutableStateOf(false) }
    var chatMode by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<Task?>(null) }
    var aiReply by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var aiStatus by remember { mutableStateOf("") }
    var aiDoneMessage by remember { mutableStateOf("") }
    var pendingRetryInput by remember { mutableStateOf("") }
    var retryCount by remember { mutableStateOf(0) }
    val chatFocus = remember { FocusRequester() }
    // 播报状态
    var showReportView by remember { mutableStateOf(false) }
    var reportLoading by remember { mutableStateOf(false) }
    var reports by remember { mutableStateOf(ReportRepository.load()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 共享的 AI 结果处理逻辑（避免两处重复）
    // 记录 AI 完整链路：原始返回 → 逐条工具调用 → 工具执行结果 → AI 回复
    fun processAiResult(result: com.example.aitodoapp.data.AiResult, aiTaskList: List<Task>, archivedTaskList: List<Task> = emptyList(), userInput: String = "", traceId: String = "") {
        retryCount = 0  // AI 调用成功 → 重置重试计数
        aiReply = result.text

        // 1. 记录 AI 原始返回（完整响应 JSON）
        if (result.rawResponse.isNotBlank()) {
            val tokenInfo = if (result.promptTokens + result.completionTokens > 0) "token: ${result.promptTokens}+${result.completionTokens}" else ""
            com.example.aitodoapp.data.ActionLogRepository.add(
                com.example.aitodoapp.data.ActionLog(type = com.example.aitodoapp.data.LogType.AI_RAW_OUTPUT, source = "AI",
                    traceId = traceId, summary = "AI 原始返回（${tokenInfo}）", detail = result.rawResponse)
            )
        }

        // 2. 逐条记录工具调用和执行结果
        fun toolCallSummary(action: com.example.aitodoapp.data.AiAction): String = when (action) {
            is AiAction.CreateTask -> buildString {
                append("title: ${action.title}")
                if (action.deadline != null) append(", deadline: ${action.deadline}")
                if (action.deadlineTime != null) append(", time: ${action.deadlineTime}")
                if (action.priority != com.example.aitodoapp.Priority.P3) append(", pri: ${action.priority}")
                if (action.tags.isNotEmpty()) append(", tags: ${action.tags.joinToString(",")}")
                if (action.content.isNotBlank()) append(", content: ${action.content.take(50)}")
                if (action.plannedDates.isNotEmpty()) append(", planned: ${action.plannedDates.joinToString(",")}")
                if (action.estimatedMinutes != null) append(", est: ${action.estimatedMinutes}min")
            }
            is AiAction.CompleteTask -> buildString {
                append("title: ${action.title}")
                if (action.deadline != null) append(", deadline: ${action.deadline}")
                if (action.tags != null) append(", tags: ${action.tags.joinToString(",")}")
            }
            is AiAction.DeleteTask -> buildString {
                append("title: ${action.title}")
                if (action.deadline != null) append(", deadline: ${action.deadline}")
            }
            is AiAction.UpdateTask -> buildString {
                append("match: ${action.title}")
                if (action.newTitle != null) append(", new_title: ${action.newTitle}")
                if (action.priority != null) append(", pri: ${action.priority}")
                if (action.deadline != null) append(", deadline: ${action.deadline}")
                if (action.tags != null) append(", tags: ${action.tags.joinToString(",")}")
            }
            is AiAction.CompletedTasks -> "查看已完成任务"
            is AiAction.UpdateSettings -> buildString {
                append("settings: ")
                if (action.apiUrl != null) append("api_url, ")
                if (action.apiKey != null) append("api_key, ")
                if (action.model != null) append("model, ")
                if (action.showOverdueInline != null) append("show_overdue, ")
                if (action.longPressChat != null) append("long_press, ")
                if (action.showTokenUsage != null) append("show_tokens, ")
                if (action.autoSyncCalendar != null) append("auto_sync, ")
                if (action.defaultReminderMinutes != null) append("reminder, ")
            }
            is AiAction.ManageTag -> "${action.action} tag: ${action.tagName}"
            is AiAction.ArchiveTask -> buildString {
                append("title: ${action.title}")
                if (action.completedAt != null) append(", completed_at: ${action.completedAt}")
            }
            is AiAction.UnarchiveTask -> "title: ${action.title}"
        }

        var created = 0; var completed = 0; var deleted = 0; var updated = 0; var settingsChanged = 0; var tagged = 0; var archived = 0; var unarchived = 0
        viewModel.beginBatch()
        try {
            for (action in result.actions) {
                // 2a. 记录工具调用
                val actionName = action::class.simpleName ?: "?"
                com.example.aitodoapp.data.ActionLogRepository.add(
                    com.example.aitodoapp.data.ActionLog(type = com.example.aitodoapp.data.LogType.AI_TOOL_CALL, source = "AI",
                        traceId = traceId, summary = "AI → $actionName", detail = toolCallSummary(action))
                )
                // 2b. 执行工具（带 traceId，执行结果会自动关联到同一条追踪链）
                when (action) {
                    is AiAction.CreateTask -> { viewModel.addTask(action.title, action.priority, action.deadline, action.tags, action.content, action.plannedDates, action.deadlineTime, action.estimatedMinutes, traceId); created++ }
                    is AiAction.CompleteTask -> { findBestMatch(aiTaskList, action.toMatchCriteria())?.let { viewModel.completeTask(it.id, traceId) }; completed++ }
                    is AiAction.DeleteTask -> { findBestMatch(aiTaskList, action.toMatchCriteria())?.let { viewModel.deleteTask(it.id, traceId) }; deleted++ }
                    is AiAction.UpdateTask -> {
                        val task = findBestMatch(aiTaskList, action.toMatchCriteria())
                        if (task != null && (action.newTitle != null || action.priority != null || action.deadline != null || action.tags != null || action.plannedDates != null || action.deadlineTime != null || action.plannedTimes != null)) {
                            viewModel.updateTask(task.id, action.newTitle ?: task.title, task.content, action.priority ?: task.priority, action.deadline ?: task.deadline, action.tags ?: task.tags, action.plannedDates ?: task.plannedDates, action.priority != null && action.priority != task.priority, action.deadlineTime, action.plannedTimes ?: emptyList(), null, traceId); updated++
                        }
                    }
                    is AiAction.CompletedTasks -> {
                        val done = aiTaskList.filter { it.isCompleted }
                        aiReply = "已完成的任务（${done.size}个）：\n" + done.joinToString("\n") { "- ${it.title}" }
                    }
                    is AiAction.UpdateSettings -> {
                        val cur = SettingsRepository.load()
                        val upd = cur.copy(apiUrl = action.apiUrl ?: cur.apiUrl, apiKey = action.apiKey ?: cur.apiKey, model = action.model ?: cur.model, showOverdueInline = action.showOverdueInline ?: cur.showOverdueInline, longPressChat = action.longPressChat ?: cur.longPressChat, showTokenUsage = action.showTokenUsage ?: cur.showTokenUsage, autoSyncCalendar = action.autoSyncCalendar ?: cur.autoSyncCalendar, defaultReminderMinutes = action.defaultReminderMinutes ?: cur.defaultReminderMinutes)
                        SettingsRepository.save(upd); viewModel.refreshSettings(); settingsChanged++
                    }
                    is AiAction.ManageTag -> {
                        when (action.action) {
                            "create" -> viewModel.createTag(action.tagName, traceId)
                            "delete" -> viewModel.deleteTag(action.tagName, traceId)
                            "promote" -> viewModel.promoteTag(action.tagName, traceId)
                        }
                        tagged++
                    }
                    is AiAction.ArchiveTask -> { findBestMatch(aiTaskList, action.toMatchCriteria())?.let { viewModel.archiveTask(it.id, action.completedAt, traceId) }; archived++ }
                    is AiAction.UnarchiveTask -> { findBestMatch(archivedTaskList.ifEmpty { aiTaskList }, action.toMatchCriteria())?.let { viewModel.unarchiveTask(it.id, traceId) }; unarchived++ }
                }
            }
        } finally {
            viewModel.endBatch()
        }

        // 3. 记录 AI 回复文本（完整，不截断）
        val tokenInfoFull = if (result.promptTokens + result.completionTokens > 0) " | token: ${result.promptTokens}+${result.completionTokens}" else ""
        com.example.aitodoapp.data.ActionLogRepository.add(
            com.example.aitodoapp.data.ActionLog(type = com.example.aitodoapp.data.LogType.AI_OUTPUT, source = "AI",
                traceId = traceId, summary = "AI 回复", detail = "${result.text}${tokenInfoFull}")
        )

        // 4. 构建通知摘要（保持原有逻辑）
        val parts = mutableListOf<String>(); val details = mutableListOf<String>()
        if (created > 0) { parts.add("📝添加了${created}个任务"); val a = result.actions.firstOrNull { it is AiAction.CreateTask } as? AiAction.CreateTask; if (a != null) { var d = a.title; if (a.deadline != null) d += " 截止${a.deadline?.format(DateTimeFormatter.ofPattern("M月d日")) ?: ""}"; if (a.deadlineTime != null) d += " ${a.deadlineTime}"; details.add(d) } }
        if (completed > 0) { parts.add("✅完成了${completed}个任务"); details.add(result.actions.filterIsInstance<AiAction.CompleteTask>().firstOrNull()?.title ?: "") }
        if (deleted > 0) { parts.add("🗑️删除了${deleted}个任务"); details.add(result.actions.filterIsInstance<AiAction.DeleteTask>().firstOrNull()?.title ?: "") }
        if (updated > 0) { parts.add("✏️修改了${updated}个任务"); details.add(result.actions.filterIsInstance<AiAction.UpdateTask>().firstOrNull()?.let { "修改 ${it.title}" } ?: "") }
        if (settingsChanged > 0) parts.add("⚙️已更新设置")
        if (tagged > 0) parts.add("🏷已更新标签")
        if (archived > 0) parts.add("📦已归档${archived}个")
        if (unarchived > 0) parts.add("📤已恢复${unarchived}个")
        if (parts.isNotEmpty()) {
            aiDoneMessage = parts.joinToString(" ")
            val notifBody = details.filter { it.isNotBlank() }.joinToString("\n")
            NotificationHelper.show(context, "AI 代办", if (notifBody.isNotBlank()) notifBody else parts.joinToString(" "))
        }
        aiLoading = false; aiStatus = ""
    }

    LaunchedEffect(openReportTrigger) {
        if (openReportTrigger) {
            showReportView = true
        }
    }
    val scope = LocalLifecycleOwner.current.lifecycleScope
    val placeholders = remember { listOf("记个事...", "粘贴长文本或输入...", "告诉 AI 做什么...", "输入任务...", "试试自然语言...", "说你想做的事...") }
    val currentPlaceholder by remember(chatMode) { mutableStateOf(if (chatMode) placeholders.random() else "") }
    val aiStatusMessages = remember { listOf("🤔 思考中...", "🧠 分析中...", "📡 接收中...", "⚡ 处理中...", "🎯 定位中...", "✨ 优化中...", "🔍 检索中...", "💡 构思中...") }
    LaunchedEffect(aiLoading) {
        if (aiLoading) {
            var i = 0
            while (true) {
                aiStatus = aiStatusMessages[i % aiStatusMessages.size]
                delay(2200)
                i++
            }
        } else {
            aiStatus = ""
        }
    }
    LaunchedEffect(aiDoneMessage) {
        if (aiDoneMessage.isNotEmpty()) {
            delay(5000)
            aiDoneMessage = ""
        }
    }

    // 切后台网络失败后，回到前台自动重试（独立 traceId，视为一次新的请求）
    val retryLifecycle = LocalLifecycleOwner.current
    LaunchedEffect(retryLifecycle, pendingRetryInput) {
        if (pendingRetryInput.isNotEmpty()) {
            // 防御：如果重试次数已达上限，直接停止
            if (retryCount >= 3) {
                aiReply = "⚠️ 多次重试失败，请稍后再试"
                pendingRetryInput = ""
                return@LaunchedEffect
            }
            retryLifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val input = pendingRetryInput
                pendingRetryInput = ""
                val traceId = "ai_${System.currentTimeMillis()}"
                aiLoading = true; aiReply = ""; aiStatus = "🔄 自动重试..."
                delay(500)
                val aiTaskList = tasks
                val taskDescriptions = aiTaskList.map { t ->
                    val prefix = when {
                        t.isCompleted -> "[已完成] "
                        overdueTasks.any { it.id == t.id } -> "[过期] "
                        else -> ""
                    }
                    prefix + formatTaskForAi(t, today)
                }
                val tagNames = allTags.map { it.name }
                val result = AiService.processMessage(input, taskDescriptions, tagNames)
                TokenRepository.recordUsage(result.promptTokens, result.completionTokens)
                                withContext(Dispatchers.Main) {
                                    if (result.error != null) {
                                        // 重试失败也记录输入日志
                                        com.example.aitodoapp.data.ActionLogRepository.add(
                                            com.example.aitodoapp.data.ActionLog(type = com.example.aitodoapp.data.LogType.AI_INPUT, source = "AI",
                                                traceId = traceId, summary = "用户输入：$input（重试失败）",
                                                detail = buildString {
                                                    appendLine("=== 错误 ===")
                                                    appendLine(result.error ?: "未知错误")
                                                    if (result.rawRequest.isNotBlank()) { appendLine("=== 完整请求 JSON ==="); append(result.rawRequest) }
                                                })
                                        )
                                        // 网络类瞬时错误才保留待重试；配置类错误直接提示用户去设置
                                        val transient = result.error.contains("超时") || result.error.contains("无法连接") ||
                                            result.error.contains("网络异常") || result.error.contains("服务器内部错误")
                                        if (transient) {
                                            if (retryCount >= 3) {
                                                aiReply = "⚠️ 多次重试失败，请稍后再试"
                                            } else {
                                                retryCount++
                                                pendingRetryInput = input
                                                aiReply = "⚠️ ${result.error}，回到前台自动重试..."
                                            }
                                        } else {
                                            aiReply = "⚠️ ${result.error}"
                                        }
                                        aiLoading = false; aiStatus = ""; return@withContext
                                    }
                                    // 记录完整输入日志
                                    com.example.aitodoapp.data.ActionLogRepository.add(
                                        com.example.aitodoapp.data.ActionLog(type = com.example.aitodoapp.data.LogType.AI_INPUT, source = "AI",
                                            traceId = traceId, summary = "用户输入：$input（重试）",
                                            detail = buildString {
                                                appendLine("用户输入：$input")
                                                if (result.rawRequest.isNotBlank()) { appendLine("=== 完整请求 JSON ==="); append(result.rawRequest) }
                                            })
                                    )
                                    aiReply = result.text
                                    processAiResult(result, aiTaskList, viewModel.archivedTasks, input, traceId)
                                }
            }
        }
    }

    // AI 加载安全兜底：超过 40 秒自动重置
    LaunchedEffect(aiLoading) {
        if (aiLoading) {
            delay(40000)
            aiLoading = false; aiStatus = "⚠️ 请求超时，请重试"
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 顶部蓝条
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${today.monthValue}月", color = MaterialTheme.colorScheme.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("${today.dayOfMonth}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("今天要做的事", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val dow = when (today.dayOfWeek.value) { 1 -> "星期一"; 2 -> "星期二"; 3 -> "星期三"; 4 -> "星期四"; 5 -> "星期五"; 6 -> "星期六"; 7 -> "星期日"; else -> "" }
                    Text(dow, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    ReportBadge(onClick = { showReportView = true })
                    if (settings.showTokenUsage) {
                        val todayTk = TokenRepository.getTodayTokens()
                        if (todayTk.prompt + todayTk.completion > 0) {
                            Text("⬆${todayTk.prompt} ⬇${todayTk.completion}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            // AI 处理进度 / 完成反馈
            if (aiLoading) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer)) {
                    androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(aiStatus, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                    }
                }
            } else if (aiDoneMessage.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(aiDoneMessage, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            // 日期筛选芯片
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayFilter.entries.forEach { filter ->
                    val sel = selectedDay == filter
                    Surface(
                        onClick = { viewModel.selectedDay = filter }, shape = RoundedCornerShape(16.dp),
                        color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(filter.label(today), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (selectedDay == DayFilter.ALL) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("全部任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp)); Text("(${tasks.size})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val inlineOverdue = selectedDay == DayFilter.TODAY && overdueTasks.isNotEmpty() && !settings.showOverdueInline
            val displayTasks = if (inlineOverdue) tasks.filter { !it.isCompleted } + overdueTasks + tasks.filter { it.isCompleted } else tasks
            if (displayTasks.isEmpty() && overdueTasks.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (selectedDay == DayFilter.OVERDUE) "没有过期任务" else if (selectedDay == DayFilter.TODAY) "今天没有任务" else "还没有任务", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else {
                Column(Modifier.weight(1f)) {
                    LazyColumn(Modifier.weight(1f)) { items(displayTasks, key = { it.id }) { task -> TaskItem(task, false, { viewModel.completeTask(task.id) }, { editTarget = task }); HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) } }
                    if (selectedDay == DayFilter.TODAY && overdueTasks.isNotEmpty() && settings.showOverdueInline) {
                        var showOverdue by remember { mutableStateOf(true) }
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                                Text(" 过期任务 (${overdueTasks.size}) ", color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.clickable { showOverdue = !showOverdue })
                                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                            }
                            if (showOverdue) {
                                LazyColumn(Modifier.heightIn(max = 200.dp)) { items(overdueTasks, key = { it.id }) { task -> TaskItem(task, false, { viewModel.completeTask(task.id) }, { editTarget = task }); HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) } }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(28.dp).size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).combinedClickable(
            onClick = { if (settings.longPressChat) showInput = true else { chatMode = true; chatInput = "" } },
            onLongClick = { if (settings.longPressChat) { chatMode = true; chatInput = "" } else showInput = true }
        ), contentAlignment = Alignment.Center) {
            Text("+", fontSize = 24.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }

        if (chatMode) {
            LaunchedEffect(Unit) { chatFocus.requestFocus() }
            Box(Modifier.fillMaxSize().clickable { chatMode = false }.background(Color.Black.copy(alpha = 0.3f)))
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                    Text(if (aiReply.isNotEmpty()) aiReply else "跟 AI 说你要做什么",
                        style = MaterialTheme.typography.labelMedium, softWrap = true,
                        color = if (aiReply.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (aiLoading) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.weight(1f).height(3.dp))
                        Spacer(Modifier.width(8.dp)); Text(aiStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = chatInput, onValueChange = { chatInput = it }, placeholder = { Text(if (aiLoading) "处理中..." else currentPlaceholder) }, textStyle = TextStyle(fontSize = 14.sp), singleLine = false, minLines = 1, maxLines = 5, enabled = !aiLoading,
                        modifier = Modifier.weight(1f).focusRequester(chatFocus), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val t = chatInput.trim()
                        if (t.isNotBlank() && !aiLoading) {
                            retryCount = 0  // 用户新输入 → 重置重试计数
                            val traceId = "ai_${System.currentTimeMillis()}"
                            aiLoading = true; aiReply = ""; aiStatus = "🔄 正在连接 AI..."
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val aiTaskList = tasks
                                    val taskDescriptions = aiTaskList.map { t ->
                                        val prefix = when {
                                            t.isCompleted -> "[已完成] "
                                            overdueTasks.any { it.id == t.id } -> "[过期] "
                                            else -> ""
                                        }; prefix + formatTaskForAi(t, today)
                                    }
                                    val tagNames = allTags.map { it.name }
                                    // 先调用 AI，拿到结果后才写日志（因为需要完整的请求/响应 JSON）
                                    val result = AiService.processMessage(t, taskDescriptions, tagNames)
                                    TokenRepository.recordUsage(result.promptTokens, result.completionTokens)
                                    scope.launch(Dispatchers.Main) {
                                        if (result.error != null) {
                                            // 即使是错误也记录输入日志
                                            com.example.aitodoapp.data.ActionLogRepository.add(
                                                com.example.aitodoapp.data.ActionLog(type = com.example.aitodoapp.data.LogType.AI_INPUT, source = "AI",
                                                    traceId = traceId, summary = "用户输入：$t（失败）",
                                                    detail = buildString {
                                                        appendLine("=== 错误 ===")
                                                        appendLine(result.error)
                                                        if (result.rawRequest.isNotBlank()) { appendLine("=== 完整请求 JSON ==="); append(result.rawRequest) }
                                                    })
                                            )
                                            val transient = result.error.contains("超时") || result.error.contains("无法连接") ||
                                                result.error.contains("网络异常") || result.error.contains("服务器内部错误")
                                            if (transient) {
                                                if (retryCount >= 3) {
                                                    aiReply = "⚠️ 多次重试失败，请稍后再试"
                                                } else {
                                                    retryCount++
                                                    pendingRetryInput = t
                                                    aiReply = "⚠️ ${result.error}，回到前台自动重试..."
                                                }
                                            } else {
                                                aiReply = "⚠️ ${result.error}"
                                            }
                                            aiLoading = false; aiStatus = ""; return@launch
                                        }
                                        // 记录完整输入日志（含完整请求 JSON）
                                        com.example.aitodoapp.data.ActionLogRepository.add(
                                            com.example.aitodoapp.data.ActionLog(type = com.example.aitodoapp.data.LogType.AI_INPUT, source = "AI",
                                                traceId = traceId, summary = "用户输入：$t",
                                                detail = buildString {
                                                    appendLine("用户输入：$t")
                                                    if (result.rawRequest.isNotBlank()) { appendLine("=== 完整请求 JSON ==="); append(result.rawRequest) }
                                                })
                                        )
                                        aiReply = result.text
                                        processAiResult(result, aiTaskList, viewModel.archivedTasks, t, traceId)
                                    }
                                } catch (_: kotlinx.coroutines.CancellationException) { aiLoading = false; aiStatus = "" }
                                catch (e: Exception) { scope.launch(Dispatchers.Main) { aiReply = "出错了：${e.message}"; aiLoading = false; aiStatus = "" } }
                            }
                        }
                    }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(52.dp), enabled = !aiLoading) { Text(if (aiLoading) "..." else "发送") }
                }
            }
        }
        if (showInput) AddTaskDialog(viewModel.allTags, { showInput = false }) { t, p, d, tags, c, pl, dt -> viewModel.addTask(t, p, d, tags, c, pl, dt, null); showInput = false }
        if (editTarget != null) EditTaskDialog(editTarget!!, viewModel.allTags, { editTarget = null }, { id, ti, c, p, d, tags, pl, locked, dt, pt, em -> viewModel.updateTask(id, ti, c, p, d, tags, pl, locked, dt, pt, em); editTarget = null }, { viewModel.deleteTask(editTarget!!.id); editTarget = null })
        // 播报查看页
        BackHandler(enabled = showReportView) {
            showReportView = false
            onClearReportTrigger()
        }
        if (showReportView) {
            val allReports = ReportRepository.load()
            ReportViewScreen(reports = allReports, onDismiss = {
                ReportRepository.markRead()
                reports = ReportRepository.load()
                showReportView = false
                onClearReportTrigger()
            })
        }
    }
}

/** 随机生成通知文案 */
private fun generateRandomNotification(isMorning: Boolean): String {
    val prefix = if (isMorning) "🌅" else "🌙"
    val morningTexts = listOf(
        "你的早间播报已送达，今天也要元气满满哦 ☀️",
        "早安！来看看今天有哪些待办等着你～",
        "☀️ 新的一天，播报已更新，快来看看今天的安排吧！",
        "早上好～今日待办清单已生成，查看详情 👀",
        "🌅 播报已送达！今天也要高效完成任务，冲鸭！",
    )
    val eveningTexts = listOf(
        "🌙 晚间播报来了～回顾一下今天的完成情况吧！",
        "晚安～今天的播报已生成，看看明天要做什么 ✨",
        "🌃 晚间播报已更新，来看看今天的成果！",
        "今天的努力辛苦啦，来看看晚间总结 💪",
        "✨ 播报已送达！规划明天，从今晚开始～",
    )
    val texts = if (isMorning) morningTexts else eveningTexts
    return "$prefix ${texts.random()}"
}
