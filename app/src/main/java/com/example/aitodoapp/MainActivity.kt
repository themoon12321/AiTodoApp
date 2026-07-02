package com.example.aitodoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.aitodoapp.data.AiService
import com.example.aitodoapp.data.CalendarSyncHelper
import com.example.aitodoapp.data.LocalDateListSerializer
import com.example.aitodoapp.data.LocalDateSerializer
import com.example.aitodoapp.data.NotificationHelper
import com.example.aitodoapp.data.ReportRepository
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.data.TaskRepository
import com.example.aitodoapp.data.TokenRepository
import com.example.aitodoapp.model.ReportEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.aitodoapp.ui.screens.ArchiveScreen
import com.example.aitodoapp.ui.screens.SettingsScreen
import com.example.aitodoapp.ui.screens.TagManagerScreen
import com.example.aitodoapp.ui.screens.TaskScreen
import com.example.aitodoapp.ui.theme.AiTodoAppTheme
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

// ============ 数据模型 ============

@Serializable
enum class Priority(val emoji: String, val label: String, val color: Color) {
    P0("🔥", "紧急", Color(0xFFE53935)),
    P1("🔴", "高", Color(0xFFFB8C00)),
    P2("🟡", "中", Color(0xFFFDD835)),
    P3("🟢", "普通", Color(0xFF43A047)),
    P4("⚪", "低", Color(0xFF9E9E9E));
    companion object { fun fromString(s: String): Priority = entries.find { it.name == s } ?: P3 }
}

enum class DayFilter {
    OVERDUE, TODAY, TOMORROW, DAY_AFTER, ALL;

    fun label(today: LocalDate): String = when (this) {
        OVERDUE -> "过期"
        TODAY -> "今天"
        TOMORROW -> "明天"
        DAY_AFTER -> {
            val d = today.plusDays(2)
            "${d.monthValue}月${d.dayOfMonth}日"
        }
        ALL -> "全部"
    }

    fun date(today: LocalDate): LocalDate? = when (this) {
        TODAY -> today
        TOMORROW -> today.plusDays(1)
        DAY_AFTER -> today.plusDays(2)
        else -> null
    }
}

@Immutable
@Serializable
data class Tag(val name: String, val isTemporary: Boolean = true)

@Immutable
@Serializable
data class Task(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val content: String = "",
    @Serializable(with = LocalDateSerializer::class) val createdAt: LocalDate = LocalDate.now(),
    val priority: Priority = Priority.P3,
    val priorityLocked: Boolean = false,
    val tags: List<String> = emptyList(),
    @Serializable(with = LocalDateListSerializer::class) val plannedDates: List<LocalDate> = emptyList(),
    val plannedTimes: List<String> = emptyList(),
    @Serializable(with = LocalDateSerializer::class) val deadline: LocalDate? = null,
    val deadlineTime: String? = null,
    val isCompleted: Boolean = false, val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    @Serializable(with = LocalDateSerializer::class) val deletedAt: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val completedAt: LocalDate? = null,
    val estimatedMinutes: Int? = null,
    val calendarEventId: Long? = null
)

// ============ 多字段匹配引擎 ============

data class MatchCriteria(
    val keyword: String,
    val deadline: LocalDate? = null,
    val deadlineTime: String? = null,
    val tags: List<String>? = null,
    val content: String? = null,
    val plannedDate: LocalDate? = null
)

fun formatTaskForAi(task: Task, today: LocalDate = LocalDate.now()): String {
    val parts = mutableListOf(task.title)
    parts.add("${task.priority.emoji}${task.priority.label}")
    if (task.deadline != null) {
        val dStr = task.deadline.format(DateTimeFormatter.ofPattern("M/d"))
        val tStr = task.deadlineTime?.let { " $it" } ?: ""
        parts.add("截止:$dStr$tStr")
    }
    if (task.tags.isNotEmpty()) parts.add("🏷${task.tags.joinToString(",")}")
    if (task.plannedDates.isNotEmpty()) {
        val pd = task.plannedDates.sorted().joinToString(",") { it.format(DateTimeFormatter.ofPattern("M/d")) }
        parts.add("📍$pd")
    }
    if (task.content.isNotBlank()) {
        parts.add("💬${task.content.take(20)}${if (task.content.length > 20) "…" else ""}")
    }
    return parts.joinToString(" | ")
}

fun findBestMatch(tasks: List<Task>, criteria: MatchCriteria): Task? {
    val kw = criteria.keyword.trim()
    data class Scored(val task: Task, val score: Int)

    val scored = tasks.mapNotNull { task ->
        var score = 0
        if (kw.isNotBlank()) {
            if (task.title.contains(kw, ignoreCase = true)) score += 50
            else if (kw.contains(task.title, ignoreCase = true)) score += 30
            if (task.content.contains(kw, ignoreCase = true)) score += 10
        }
        if (criteria.deadline != null && task.deadline == criteria.deadline) score += 30
        if (criteria.deadlineTime != null && task.deadlineTime == criteria.deadlineTime) score += 20
        if (criteria.tags != null && criteria.tags.isNotEmpty()) {
            val matchCnt = criteria.tags.count { mt -> task.tags.any { taskTag -> taskTag.contains(mt, ignoreCase = true) } }
            if (matchCnt > 0) score += 20
        }
        if (criteria.content != null && criteria.content.isNotBlank() && task.content.contains(criteria.content, ignoreCase = true)) score += 15
        if (criteria.plannedDate != null && task.plannedDates.any { it == criteria.plannedDate }) score += 25
        if (score <= 0) null else Scored(task, score)
    }
    if (scored.isEmpty()) return null
    val maxScore = scored.maxOf { it.score }
    val best = scored.filter { it.score == maxScore }
    if (best.size > 1) {
        val exact = best.filter { it.task.title.equals(kw, ignoreCase = true) || kw.equals(it.task.title, ignoreCase = true) }
        return if (exact.size == 1) exact[0].task else null
    }
    return best[0].task
}

// ============ 主 Activity ============

class MainActivity : ComponentActivity() {
    private var openReportTrigger by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TaskRepository.init(applicationContext)
        SettingsRepository.init(applicationContext)
        TokenRepository.init(applicationContext)
        ReportRepository.init(applicationContext)
        NotificationHelper.createChannel(applicationContext)
        if (intent?.getBooleanExtra("open_report", false) == true) openReportTrigger = true
        setContent { AiTodoAppTheme { AppMain(openReportTrigger) { openReportTrigger = false } } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_report", false)) openReportTrigger = true
    }

    override fun onResume() {
        super.onResume()
        if (intent?.getBooleanExtra("open_report", false) == true) {
            openReportTrigger = true
        }
    }

}

// ============ 主导航 ============

@Composable
fun AppMain(openReportTrigger: Boolean = false, onClearReportTrigger: () -> Unit = {}) {
    val today = LocalDate.now()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var tasks by remember { mutableStateOf(TaskRepository.load<Task>("tasks.json")) }
    var allTags by remember { mutableStateOf(TaskRepository.load<Tag>("tags.json")) }
    var loaded by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(SettingsRepository.load()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var calendarPermitted by remember { mutableStateOf(false) }
    val calendarPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarPermitted = it }
    LaunchedEffect(tab) { settings = SettingsRepository.load() }
    fun saveAll() { TaskRepository.save("tasks.json", tasks); TaskRepository.save("tags.json", allTags) }

    fun autoPriority(task: Task): Priority {
        if (task.isCompleted || task.isArchived || task.priorityLocked) return task.priority
        val daysTillDeadline = task.deadline?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
        return when {
            daysTillDeadline != null && daysTillDeadline < 0 -> Priority.P0
            daysTillDeadline == 0L -> Priority.P0
            daysTillDeadline != null && daysTillDeadline <= 1 -> Priority.P1
            daysTillDeadline != null && daysTillDeadline <= 3 -> Priority.P2
            daysTillDeadline != null && daysTillDeadline <= 7 -> Priority.P3
            daysTillDeadline != null && daysTillDeadline > 7 -> Priority.P4
            task.deadline == null -> task.priority
            else -> task.priority
        }
    }

    LaunchedEffect(Unit) {
        if (!loaded) {
            loaded = true; var changed = false
            if (tasks.isEmpty()) {
                tasks = listOf(
                    Task(title = "交大物实验报告", priority = Priority.P0, tags = listOf("大物", "实验报告"), deadline = today),
                    Task(title = "洗衣服", priority = Priority.P2, deadline = today.minusDays(1)),
                    Task(title = "看那篇文献", priority = Priority.P3, tags = listOf("文献"), deadline = today.plusDays(3)),
                    Task(title = "买数据线", priority = Priority.P4),
                    Task(title = "机械建模作业", priority = Priority.P1, tags = listOf("建模"), deadline = today.plusDays(1)),
                ); changed = true
            } else {
                tasks = tasks.map { t ->
                    var nt = t
                    if (t.completedAt != null && t.completedAt < today && !t.isArchived) { changed = true; nt = nt.copy(isArchived = true) }
                    val ap = autoPriority(nt)
                    if (ap != nt.priority && !nt.isArchived && !nt.isCompleted) { changed = true; nt = nt.copy(priority = ap) }
                    nt
                }
            }
            if (allTags.isEmpty()) {
                allTags = listOf(Tag("大物", false), Tag("实验报告", false), Tag("文献", false), Tag("建模", false), Tag("英语", false), Tag("高数", false))
                changed = true
            }
            if (changed) saveAll()
            if (settings.autoSyncCalendar) {
                val toSync = tasks.filter { !it.isCompleted && !it.isArchived && it.calendarEventId == null && (it.deadline != null || it.plannedDates.isNotEmpty()) }
                if (toSync.isNotEmpty()) {
                    var synced = false
                    for (task in toSync) {
                        val syncDate = task.deadline ?: task.plannedDates.first()
                        try {
                            val eid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                CalendarSyncHelper.createEvent(context, task.title, syncDate, settings.defaultReminderMinutes,
                                    time = task.deadlineTime?.let { try { java.time.LocalTime.parse(it) } catch (_: Exception) { null } },
                                    durationMinutes = task.estimatedMinutes ?: settings.defaultDurationMinutes)
                            }
                            if (eid != null) { tasks = tasks.map { if (it.id == task.id) it.copy(calendarEventId = eid) else it }; synced = true }
                        } catch (_: Exception) {}
                    }
                    if (synced) saveAll()
                }
            }
            // 清理超过30天的已删除任务
            val cutoff = today.minusDays(30)
            val hasOldDeleted = tasks.any { it.isDeleted && it.deletedAt != null && it.deletedAt!! < cutoff }
            if (hasOldDeleted) {
                tasks = tasks.filter { !(it.isDeleted && it.deletedAt != null && it.deletedAt!! < cutoff) }
                saveAll()
            }
        }
    }

    fun completeTask(id: String) {
        val t = tasks.find { it.id == id }
        val wasCompleted = t?.isCompleted == true
        if (t != null && !t.isCompleted && t.calendarEventId != null) {
            CalendarSyncHelper.deleteEvent(context, t.calendarEventId!!)
        }
        tasks = tasks.map { if (it.id == id) { if (it.isCompleted) it.copy(isCompleted = false, completedAt = null, calendarEventId = null) else it.copy(isCompleted = true, completedAt = today) } else it }
            .let { it.filter { !it.isCompleted } + it.filter { it.isCompleted } }
        if (wasCompleted && settings.autoSyncCalendar) {
            val updated = tasks.find { it.id == id }
            if (updated != null && (updated.deadline != null || updated.plannedDates.isNotEmpty())) {
                val sd = updated.deadline ?: updated.plannedDates.first()
                try {
                    val eid = CalendarSyncHelper.createEvent(context, updated.title, sd, settings.defaultReminderMinutes,
                        time = updated.deadlineTime?.let { try { java.time.LocalTime.parse(it) } catch (_: Exception) { null } },
                        durationMinutes = updated.estimatedMinutes ?: settings.defaultDurationMinutes)
                    if (eid != null) tasks = tasks.map { if (it.id == id) it.copy(calendarEventId = eid) else it }
                } catch (_: Exception) {}
            }
        }
        saveAll()
    }
    fun deleteTask(id: String) {
        val t = tasks.find { it.id == id }
        if (t?.calendarEventId != null) CalendarSyncHelper.deleteEvent(context, t.calendarEventId!!)
        tasks = tasks.map { if (it.id == id) it.copy(isDeleted = true, deletedAt = today) else it }; saveAll()
    }
    fun archiveTask(id: String, completedDate: LocalDate? = null) {
        tasks = tasks.map { if (it.id == id) it.copy(isArchived = true, isCompleted = true, completedAt = completedDate ?: today) else it }; saveAll()
    }
    fun unarchiveTask(id: String) { tasks = tasks.map { if (it.id == id) it.copy(isArchived = false, isCompleted = false) else it }; saveAll() }
    fun addTag(n: String): String { val t = n.trim(); if (t.isBlank() || allTags.any { it.name == t }) return t; allTags = allTags + Tag(t, true); saveAll(); return t }
    fun createTag(n: String) { val t = n.trim(); if (t.isNotBlank() && allTags.none { it.name == t }) { allTags = allTags + Tag(t, false); saveAll() } }
    fun promoteTag(n: String) { allTags = allTags.map { if (it.name == n) it.copy(isTemporary = false) else it }; saveAll() }
    fun deleteTag(n: String) { allTags = allTags.filter { it.name != n }; tasks = tasks.map { it.copy(tags = it.tags.filter { t -> t != n }) }; saveAll() }
    fun addTask(title: String, pri: Priority, dl: LocalDate?, tags: List<String>, content: String = "", planned: List<LocalDate> = emptyList(), deadlineTime: String? = null, estimatedMinutes: Int? = null) {
        tags.forEach { addTag(it) }
        val newTask = Task(title = title, content = content, priority = pri, tags = tags, deadline = dl, plannedDates = planned, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes)
        tasks = listOf(newTask) + tasks
        if (settings.autoSyncCalendar && (dl != null || planned.isNotEmpty())) {
            val syncDate = dl ?: planned.first()
            try {
                val eid = CalendarSyncHelper.createEvent(context, title, syncDate, settings.defaultReminderMinutes,
                    time = newTask.deadlineTime?.let { try { java.time.LocalTime.parse(it) } catch (_: Exception) { null } },
                    durationMinutes = newTask.estimatedMinutes ?: settings.defaultDurationMinutes)
                if (eid != null) tasks = tasks.map { if (it.id == newTask.id) it.copy(calendarEventId = eid) else it }
            } catch (_: Exception) {}
        }
        saveAll()
    }

    fun updateTask(id: String, title: String, content: String, pri: Priority, dl: LocalDate?, tags: List<String>, planned: List<LocalDate> = emptyList(), lockPriority: Boolean = false, deadlineTime: String? = null, plannedTimes: List<String> = emptyList(), estimatedMinutes: Int? = null) {
        tags.forEach { addTag(it) }
        val old = tasks.find { it.id == id }
        if (old?.deadline != dl || old?.deadlineTime != deadlineTime) {
            if (old?.calendarEventId != null) CalendarSyncHelper.deleteEvent(context, old.calendarEventId!!)
            if (settings.autoSyncCalendar && dl != null) {
                try {
                    val eid = CalendarSyncHelper.createEvent(context, title, dl, settings.defaultReminderMinutes,
                        time = deadlineTime?.let { try { java.time.LocalTime.parse(it) } catch (_: Exception) { null } },
                        durationMinutes = tasks.find { it.id == id }?.estimatedMinutes ?: settings.defaultDurationMinutes)
                    tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes, plannedDates = planned, plannedTimes = plannedTimes, calendarEventId = eid) else it }
                    saveAll(); return
                } catch (_: Exception) {}
            }
        }
        tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes, plannedDates = planned, plannedTimes = plannedTimes) else it }
        saveAll()
    }

    var selectedDay by remember { mutableStateOf(DayFilter.TODAY) }
    val allActive = remember(tasks) { tasks.filter { !it.isArchived && !it.isDeleted } }
    val overdueTasks = remember(allActive) { allActive.filter { !it.isCompleted && (it.deadline != null && it.deadline < today || it.plannedDates.isNotEmpty() && it.plannedDates.all { d -> d < today }) } }
    val activeTasks = remember(selectedDay, allActive) {
    when (selectedDay) {
        DayFilter.OVERDUE -> allActive.filter { it.deadline != null && it.deadline < today && !it.isCompleted }
        DayFilter.ALL -> allActive
        DayFilter.TODAY -> allActive.filter {
            val todayRelevant = it.plannedDates.any { d -> d == today } || it.deadline == today || (it.plannedDates.isEmpty() && it.createdAt <= today && (it.deadline == null || it.deadline >= today))
            todayRelevant || (it.isCompleted && it.completedAt == today)
        }
        else -> { val d = selectedDay.date(today) ?: today; allActive.filter { it.plannedDates.any { p -> p == d } || (it.plannedDates.isEmpty() && it.createdAt <= d && (it.deadline == null || it.deadline >= d)) } }
    } }
    val archivedTasks = remember(tasks) { tasks.filter { it.isArchived && !it.isDeleted } }
    val deletedTasks = remember(tasks) { tasks.filter { it.isDeleted }.sortedByDescending { it.deletedAt ?: it.createdAt } }

    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📋", fontSize = 16.sp) }, label = { Text("任务 (${activeTasks.size})") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📦", fontSize = 16.sp) }, label = { Text("归档 (${archivedTasks.size})") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("🏷", fontSize = 16.sp) }, label = { Text("标签") })
            NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Text("⚙️", fontSize = 16.sp) }, label = { Text("设置") })
        }
    }) { innerPadding ->
        when (tab) {
            0 -> TaskScreen(activeTasks, allTags, ::completeTask, { t, p, d, tags, c, pl, dt, em -> addTask(t, p, d, tags, c, pl, dt, em) }, ::deleteTask, { id, t, c, p, d, tags, pl, lk, dt, pt, em -> updateTask(id, t, c, p, d, tags, pl, lk, dt, pt, em) }, Modifier.padding(innerPadding), false, selectedDay, { selectedDay = it }, overdueTasks, settings.showOverdueInline, settings.longPressChat, settings.showTokenUsage, onUpdateSettings = { s -> settings = s }, onTagAction = { act, name -> when (act) { "create" -> createTag(name); "delete" -> deleteTag(name); "promote" -> promoteTag(name) } }, onArchiveToggle = { id, archive, date -> if (archive) archiveTask(id, date) else unarchiveTask(id) }, openReportTrigger = openReportTrigger, onClearReportTrigger = onClearReportTrigger)
            1 -> ArchiveScreen(archivedTasks, ::unarchiveTask, ::deleteTask)
            2 -> TagManagerScreen(allTags, allActive, ::createTag, ::promoteTag, ::deleteTag, Modifier.padding(innerPadding))
            3 -> SettingsScreen(Modifier.padding(innerPadding), onTestReport = { isMorning ->
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val aiTasks = (tasks + overdueTasks).distinctBy { it.id }
                            val descs = aiTasks.map { t ->
                                val p = when { t.isCompleted -> "[已完成] "; overdueTasks.any { o -> o.id == t.id } -> "[过期] "; else -> "" }
                                p + formatTaskForAi(t, today)
                            }
                            val tags = allTags.map { it.name }
                            val result = AiService.generateDailyReport(descs, tags, isMorning)
                            if (!result.text.startsWith("网络请求失败") && !result.text.startsWith("请先")) {
                                val entry = com.example.aitodoapp.model.ReportEntry(result.text, isMorning, today.toString())
                                com.example.aitodoapp.data.ReportRepository.addReport(entry)
                                com.example.aitodoapp.data.NotificationHelper.show(context,
                                    if (isMorning) "🌅 早间播报已送达" else "🌙 晚间播报已送达",
                                    "测试播报已生成，点击📋图标查看")
                            }
                        } catch (_: Exception) {}
                    }
                }, onScheduleReport = { isMorning ->
                    com.example.aitodoapp.data.ReportWorker.schedule(context, isMorning, 1)
                }, onScheduleDaily = { isMorning, hour, minute ->
                    com.example.aitodoapp.data.ReportWorker.scheduleDaily(context, isMorning, hour, minute)
                }, onRestoreTask = { id ->
                    tasks = tasks.map { if (it.id == id) it.copy(isDeleted = false, deletedAt = null) else it }; saveAll()
                }, onPermanentDelete = { id ->
                    tasks = tasks.filter { it.id != id }; saveAll()
                })
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, heightDp = 700)
@Composable
fun AppMainPreview() { AiTodoAppTheme { AppMain() } }
