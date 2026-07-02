package com.example.aitodoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
// keep for Surface in chip row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.setValue
import com.example.aitodoapp.data.AiAction
import com.example.aitodoapp.data.AiService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.data.LocalDateListSerializer
import com.example.aitodoapp.data.LocalDateSerializer
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.data.TaskRepository
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

@Serializable
data class Tag(val name: String, val isTemporary: Boolean = true)

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
    val plannedTimes: List<String> = emptyList(),  // "HH:mm" 格式，与 plannedDates 对应
    @Serializable(with = LocalDateSerializer::class) val deadline: LocalDate? = null,
    val deadlineTime: String? = null,  // "HH:mm" 格式，5分钟精度
    val isCompleted: Boolean = false, val isArchived: Boolean = false,
    @Serializable(with = LocalDateSerializer::class) val completedAt: LocalDate? = null,
    val calendarEventId: Long? = null
)

// ============ 多字段匹配引擎 ============

/** AI 多字段匹配条件 */
data class MatchCriteria(
    val keyword: String,
    val deadline: LocalDate? = null,
    val deadlineTime: String? = null,
    val tags: List<String>? = null,
    val content: String? = null,
    val plannedDate: LocalDate? = null
)

/** 为 AI 格式化任务详情（标题 + 截止时间 + 标签 + 计划日期 + 内容摘要） */
private fun formatTaskForAi(task: Task, today: LocalDate = LocalDate.now()): String {
    val parts = mutableListOf(task.title)
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

/** 基于多字段评分找最佳匹配任务。返回 null 表示无匹配或歧义。 */
private fun findBestMatch(tasks: List<Task>, criteria: MatchCriteria): Task? {
    val kw = criteria.keyword.trim()
    data class Scored(val task: Task, val score: Int)

    val scored = tasks.mapNotNull { task ->
        var score = 0

        // 标题关键词匹配（核心，权重 50）
        if (kw.isNotBlank()) {
            if (task.title.contains(kw, ignoreCase = true)) score += 50
            else if (kw.contains(task.title, ignoreCase = true)) score += 30
            // 关键词反向匹配内容作为补充
            if (task.content.contains(kw, ignoreCase = true)) score += 10
        }

        // 截止日期精确匹配（权重 30）
        if (criteria.deadline != null && task.deadline == criteria.deadline) score += 30

        // 截止时间精确匹配（权重 20）
        if (criteria.deadlineTime != null && task.deadlineTime == criteria.deadlineTime) score += 20

        // 标签重叠（权重 20）
        if (criteria.tags != null && criteria.tags.isNotEmpty()) {
            val matchCnt = criteria.tags.count { mt -> task.tags.any { taskTag -> taskTag.contains(mt, ignoreCase = true) } }
            if (matchCnt > 0) score += 20
        }

        // 内容关键词匹配（权重 15）
        if (criteria.content != null && criteria.content.isNotBlank() && task.content.contains(criteria.content, ignoreCase = true)) score += 15

        // 计划日期精确匹配（权重 25）
        if (criteria.plannedDate != null && task.plannedDates.any { it == criteria.plannedDate }) score += 25

        if (score <= 0) null else Scored(task, score)
    }

    if (scored.isEmpty()) return null

    val maxScore = scored.maxOf { it.score }
    val best = scored.filter { it.score == maxScore }

    // 平局时尝试用精确标题匹配打破僵局
    if (best.size > 1) {
        val exact = best.filter {
            it.task.title.equals(kw, ignoreCase = true) || kw.equals(it.task.title, ignoreCase = true)
        }
        return if (exact.size == 1) exact[0].task else null // 仍有歧义 → 不操作
    }

    return best[0].task
}

/** AiAction → MatchCriteria 转换 */
private fun AiAction.CompleteTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = deadline, deadlineTime = deadlineTime,
    tags = tags, content = content, plannedDate = plannedDate
)
private fun AiAction.DeleteTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = deadline, deadlineTime = deadlineTime,
    tags = tags, content = content, plannedDate = plannedDate
)
private fun AiAction.UpdateTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = matchDeadline, deadlineTime = matchDeadlineTime,
    tags = matchTags, content = matchContent, plannedDate = matchPlannedDate
)
private fun AiAction.ArchiveTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = deadline, deadlineTime = deadlineTime,
    tags = tags, content = content, plannedDate = plannedDate
)
private fun AiAction.UnarchiveTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = deadline, deadlineTime = deadlineTime,
    tags = tags, content = content, plannedDate = plannedDate
)

// ============ 主 Activity ============

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TaskRepository.init(applicationContext)
        SettingsRepository.init(applicationContext)
        com.example.aitodoapp.data.TokenRepository.init(applicationContext)
        com.example.aitodoapp.data.NotificationHelper.createChannel(applicationContext)
        setContent { AiTodoAppTheme { AppMain() } }
    }
}

// ============ 主导航 ============

@Composable
fun AppMain() {
    val today = LocalDate.now()
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

    // 根据时间自动调整优先级（用户锁定的不动）
    fun autoPriority(task: Task): Priority {
        if (task.isCompleted || task.isArchived || task.priorityLocked) return task.priority
        val daysTillDeadline = task.deadline?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
        return when {
            daysTillDeadline != null && daysTillDeadline < 0 -> Priority.P0       // 已过期
            daysTillDeadline == 0L -> Priority.P0                                  // 今天
            daysTillDeadline != null && daysTillDeadline <= 1 -> Priority.P1       // 明天
            daysTillDeadline != null && daysTillDeadline <= 3 -> Priority.P2       // 3天内
            daysTillDeadline != null && daysTillDeadline <= 7 -> Priority.P3       // 7天内
            daysTillDeadline != null && daysTillDeadline > 7 -> Priority.P4        // 很久以后→可降级
            task.deadline == null -> task.priority                                  // 无DDL不动
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
            // 启动时补同步日历：已有任务但未同步过的
            if (settings.autoSyncCalendar) {
                val toSync = tasks.filter { !it.isCompleted && !it.isArchived && it.calendarEventId == null && (it.deadline != null || it.plannedDates.isNotEmpty()) }
                if (toSync.isNotEmpty()) {
                    var synced = false
                    for (task in toSync) {
                        val syncDate = task.deadline ?: task.plannedDates.first()
                        try {
                            val eid = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.example.aitodoapp.data.CalendarSyncHelper.createEvent(context, task.title, syncDate, settings.defaultReminderMinutes)
                            }
                            if (eid != null) { tasks = tasks.map { if (it.id == task.id) it.copy(calendarEventId = eid) else it }; synced = true }
                        } catch (_: Exception) {}
                    }
                    if (synced) saveAll()
                }
            }
        }
    }

    fun completeTask(id: String) {
        val t = tasks.find { it.id == id }
        val wasCompleted = t?.isCompleted == true
        if (t != null && !t.isCompleted && t.calendarEventId != null) {
            com.example.aitodoapp.data.CalendarSyncHelper.deleteEvent(context, t.calendarEventId!!)
        }
        tasks = tasks.map { if (it.id == id) { if (it.isCompleted) it.copy(isCompleted = false, completedAt = null, calendarEventId = null) else it.copy(isCompleted = true, completedAt = today) } else it }
            .let { it.filter { !it.isCompleted } + it.filter { it.isCompleted } }
        if (wasCompleted && settings.autoSyncCalendar) {
            val updated = tasks.find { it.id == id }
            if (updated != null && (updated.deadline != null || updated.plannedDates.isNotEmpty())) {
                val sd = updated.deadline ?: updated.plannedDates.first()
                try {
                    val eid = com.example.aitodoapp.data.CalendarSyncHelper.createEvent(context, updated.title, sd, settings.defaultReminderMinutes)
                    if (eid != null) tasks = tasks.map { if (it.id == id) it.copy(calendarEventId = eid) else it }
                } catch (_: Exception) {}
            }
        }
        saveAll()
    }
    fun deleteTask(id: String) {
        val t = tasks.find { it.id == id }
        if (t?.calendarEventId != null) com.example.aitodoapp.data.CalendarSyncHelper.deleteEvent(context, t.calendarEventId!!)
        tasks = tasks.filter { it.id != id }; saveAll()
    }
    fun archiveTask(id: String) { tasks = tasks.map { if (it.id == id) it.copy(isArchived = true) else it }; saveAll() }
    fun unarchiveTask(id: String) { tasks = tasks.map { if (it.id == id) it.copy(isArchived = false, isCompleted = false) else it }; saveAll() }
    fun addTag(n: String): String { val t = n.trim(); if (t.isBlank() || allTags.any { it.name == t }) return t; allTags = allTags + Tag(t, true); saveAll(); return t }
    fun createTag(n: String) { val t = n.trim(); if (t.isNotBlank() && allTags.none { it.name == t }) { allTags = allTags + Tag(t, false); saveAll() } }
    fun promoteTag(n: String) { allTags = allTags.map { if (it.name == n) it.copy(isTemporary = false) else it }; saveAll() }
    fun deleteTag(n: String) { allTags = allTags.filter { it.name != n }; tasks = tasks.map { it.copy(tags = it.tags.filter { t -> t != n }) }; saveAll() }
    fun addTask(title: String, pri: Priority, dl: LocalDate?, tags: List<String>, content: String = "", planned: List<LocalDate> = emptyList(), deadlineTime: String? = null) {
        tags.forEach { addTag(it) }
        val newTask = Task(title = title, content = content, priority = pri, tags = tags, deadline = dl, plannedDates = planned, deadlineTime = deadlineTime)
        tasks = listOf(newTask) + tasks
        if (settings.autoSyncCalendar && (dl != null || planned.isNotEmpty())) {
            val syncDate = dl ?: planned.first()
            try {
                val eid = com.example.aitodoapp.data.CalendarSyncHelper.createEvent(context, title, syncDate, settings.defaultReminderMinutes)
                if (eid != null) tasks = tasks.map { if (it.id == newTask.id) it.copy(calendarEventId = eid) else it }
            } catch (_: Exception) {}
        }
        saveAll()
    }

    fun updateTask(id: String, title: String, content: String, pri: Priority, dl: LocalDate?, tags: List<String>, planned: List<LocalDate> = emptyList(), lockPriority: Boolean = false, deadlineTime: String? = null, plannedTimes: List<String> = emptyList()) {
        tags.forEach { addTag(it) }
        val old = tasks.find { it.id == id }
        if (old?.deadline != dl) {
            if (old?.calendarEventId != null) com.example.aitodoapp.data.CalendarSyncHelper.deleteEvent(context, old.calendarEventId!!)
            if (settings.autoSyncCalendar && dl != null) {
                try {
                    val eid = com.example.aitodoapp.data.CalendarSyncHelper.createEvent(context, title, dl, settings.defaultReminderMinutes)
                    tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, plannedDates = planned, plannedTimes = plannedTimes, calendarEventId = eid) else it }
                    saveAll(); return
                } catch (_: Exception) {}
            }
        }
                    tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, plannedDates = planned, plannedTimes = plannedTimes) else it }
        saveAll()
    }

    var selectedDay by remember { mutableStateOf(DayFilter.TODAY) }
    val allActive = tasks.filter { !it.isArchived }
    val overdueTasks = allActive.filter { !it.isCompleted && (it.deadline != null && it.deadline < today || it.plannedDates.isNotEmpty() && it.plannedDates.all { d -> d < today }) }
    val activeTasks = when (selectedDay) {
        DayFilter.OVERDUE -> allActive.filter { it.deadline != null && it.deadline < today && !it.isCompleted }
        DayFilter.ALL -> allActive
        DayFilter.TODAY -> allActive.filter { 
            val todayRelevant = it.plannedDates.any { d -> d == today } || it.deadline == today || (it.plannedDates.isEmpty() && it.createdAt <= today && (it.deadline == null || it.deadline >= today))
            todayRelevant || (it.isCompleted && it.completedAt == today)
        }
        else -> { val d = selectedDay.date(today) ?: today; allActive.filter { it.plannedDates.any { p -> p == d } || (it.plannedDates.isEmpty() && it.createdAt <= d && (it.deadline == null || it.deadline >= d)) } }
    }
    val archivedTasks = tasks.filter { it.isArchived }

    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📋", fontSize = 16.sp) }, label = { Text("任务 (${activeTasks.size})") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("📦", fontSize = 16.sp) }, label = { Text("归档 (${archivedTasks.size})") })
            NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("🏷", fontSize = 16.sp) }, label = { Text("标签") })
            NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Text("⚙️", fontSize = 16.sp) }, label = { Text("设置") })
        }
    }) { innerPadding ->
        when (tab) {
            0 -> TaskScreen(activeTasks, allTags, ::completeTask, { t, p, d, tags, c, pl, dt -> addTask(t, p, d, tags, c, pl, dt) }, ::deleteTask, { id, t, c, p, d, tags, pl, lk, dt, pt -> updateTask(id, t, c, p, d, tags, pl, lk, dt, pt) }, Modifier.padding(innerPadding), false, selectedDay, { selectedDay = it }, overdueTasks, settings.showOverdueInline, settings.longPressChat, settings.showTokenUsage, onUpdateSettings = { s -> settings = s }, onTagAction = { act, name -> when (act) { "create" -> createTag(name); "delete" -> deleteTag(name); "promote" -> promoteTag(name) } }, onArchiveToggle = { id, archive -> if (archive) archiveTask(id) else unarchiveTask(id) })
            1 -> TaskScreen(archivedTasks, allTags, ::unarchiveTask, { _, _, _, _, _, _, _ -> }, ::deleteTask, { _, _, _, _, _, _, _, _, _, _ -> }, Modifier.padding(innerPadding), true, DayFilter.ALL, {})
            2 -> TagManagerScreen(allTags, allActive, ::createTag, ::promoteTag, ::deleteTag, Modifier.padding(innerPadding))
            3 -> SettingsScreen(Modifier.padding(innerPadding))
        }
    }
}

// ============ 日历测试弹窗 ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTestDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now()) }
    var hour by remember { mutableStateOf(10) }
    var minute by remember { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf(-1) }
    var deleteTitle by remember { mutableStateOf("") }
    var deleteResult by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) result = "✅ 权限已授予，请再次点击添加"
        else result = "❌ 权限被拒绝"
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { onDismiss() }.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("测试日历写入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("日程标题") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDatePicker = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Text("📅 " + date.format(java.time.format.DateTimeFormatter.ofPattern("M月d日")), fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { hour = (hour + 1) % 24 }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Text("🕐 ${hour.toString().padStart(2,'0')}:${minute.toString().padStart(2,'0')}", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("分钟：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { minute = (minute + 10) % 60 }, shape = RoundedCornerShape(8.dp)) { Text("+10", fontSize = 12.sp) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { minute = (minute + 30) % 60 }, shape = RoundedCornerShape(8.dp)) { Text("+30", fontSize = 12.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("提醒：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val reminders = listOf(-1 to "无", 0 to "准时", 10 to "10分", 30 to "30分", 60 to "1小时")
                reminders.forEach { (min, label) ->
                    OutlinedButton(onClick = { reminderMinutes = min }, shape = RoundedCornerShape(8.dp),
                        colors = if (reminderMinutes == min) androidx.compose.material3.ButtonDefaults.filledTonalButtonColors() else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (title.isBlank()) { result = "请输入标题"; return@Button }
                val check = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
                if (check != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    launcher.launch(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR))
                    return@Button
                }
                try {
                    val projection = arrayOf("_id", "account_name", "calendar_displayName")
                    var cursor = context.contentResolver.query(android.provider.CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
                    var calendarId = -1L
                    cursor?.use { while (it.moveToNext()) { calendarId = it.getLong(0); if (calendarId > 0) break } }
                    if (calendarId <= 0) {
                        // 直接尝试用常用日历 ID 写入用户事件
                        val startMillis = date.atTime(java.time.LocalTime.of(hour, minute)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val endMillis = startMillis + 3600000
                        for (id in 1L..5L) {
                            val v = android.content.ContentValues().apply {
                                put(android.provider.CalendarContract.Events.DTSTART, startMillis)
                                put(android.provider.CalendarContract.Events.DTEND, endMillis)
                                put(android.provider.CalendarContract.Events.TITLE, title.trim())
                                put(android.provider.CalendarContract.Events.CALENDAR_ID, id)
                                put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                            }
                            try {
                                val uri = context.contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, v)
                                if (uri != null) {
                                    val eventId = uri.lastPathSegment?.toLong()
                                    if (eventId != null && reminderMinutes >= 0) {
                                        try {
                                            val rem = android.content.ContentValues().apply {
                                                put(android.provider.CalendarContract.Reminders.EVENT_ID, eventId)
                                                put(android.provider.CalendarContract.Reminders.MINUTES, reminderMinutes)
                                                put(android.provider.CalendarContract.Reminders.METHOD, android.provider.CalendarContract.Reminders.METHOD_ALERT)
                                            }
                                            context.contentResolver.insert(android.provider.CalendarContract.Reminders.CONTENT_URI, rem)
                                        } catch (_: Exception) {}
                                    }
                                    result = "✅ 添加成功！已添加到系统日历"; return@Button
                                }
                            } catch (_: Exception) { continue }
                        }
                        result = "❌ 未找到可用日历（请先打开系统日历 App 初始化）"; return@Button
                    }
                    val startMillis = date.atTime(java.time.LocalTime.of(hour, minute)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val endMillis = startMillis + 3600000
                    val values = android.content.ContentValues().apply {
                        put(android.provider.CalendarContract.Events.DTSTART, startMillis)
                        put(android.provider.CalendarContract.Events.DTEND, endMillis)
                        put(android.provider.CalendarContract.Events.TITLE, title.trim())
                        put(android.provider.CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    }
                    val uri = context.contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, values)
                    if (uri != null) {
                        val eventId = uri.lastPathSegment?.toLong()
                        if (eventId != null && reminderMinutes >= 0) {
                            try {
                                val rem = android.content.ContentValues().apply {
                                    put(android.provider.CalendarContract.Reminders.EVENT_ID, eventId)
                                    put(android.provider.CalendarContract.Reminders.MINUTES, reminderMinutes)
                                    put(android.provider.CalendarContract.Reminders.METHOD, android.provider.CalendarContract.Reminders.METHOD_ALERT)
                                }
                                context.contentResolver.insert(android.provider.CalendarContract.Reminders.CONTENT_URI, rem)
                            } catch (_: Exception) {}
                        }
                        result = "✅ 添加成功！已添加到系统日历"
                    } else { result = "❌ 添加失败" }
                } catch (e: Exception) { result = "❌ 出错了：${e.message}" }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = title.isNotBlank()) { Text("添加到日历") }
            Spacer(Modifier.height(8.dp))
            if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.labelMedium, color = if (result.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text("删除事件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = deleteTitle, onValueChange = { deleteTitle = it }, placeholder = { Text("输入标题查找删除") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (deleteTitle.isBlank()) { deleteResult = "请输入标题"; return@Button }
                deleteResult = ""
                try {
                    val cursor = context.contentResolver.query(android.provider.CalendarContract.Events.CONTENT_URI, arrayOf("_id", "title"), "title = ?", arrayOf(deleteTitle.trim()), null)
                    var count = 0
                    cursor?.use { while (it.moveToNext()) {
                        val eventId = it.getLong(0)
                        context.contentResolver.delete(android.provider.CalendarContract.Reminders.CONTENT_URI, "event_id = ?", arrayOf(eventId.toString()))
                        context.contentResolver.delete(android.provider.CalendarContract.Events.CONTENT_URI, "_id = ?", arrayOf(eventId.toString()))
                        count++
                    } }
                    deleteResult = if (count > 0) "✅ 已删除 $count 个事件" else "❌ 未找到匹配的事件"
                } catch (e: Exception) { deleteResult = "❌ 出错了：${e.message}" }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = deleteTitle.isNotBlank()) { Text("删除匹配事件") }
            if (deleteResult.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(deleteResult, style = MaterialTheme.typography.labelMedium, color = if (deleteResult.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
        if (showDatePicker) {
            val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86400000L)
            DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showDatePicker = false }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }) { DatePicker(state = state) }
        }
    }
}

// ============ 设置页 ============

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val s = remember { mutableStateOf(SettingsRepository.load()) }
    var apiUrl by remember { mutableStateOf(s.value.apiUrl) }
    var apiKey by remember { mutableStateOf(s.value.apiKey) }
    var model by remember { mutableStateOf(s.value.model) }
    var saved by remember { mutableStateOf(false) }
    var showCalendarTest by remember { mutableStateOf(false) }
    var autoSync by remember { mutableStateOf(s.value.autoSyncCalendar) }
    var defaultRemind by remember { mutableStateOf(s.value.defaultReminderMinutes) }
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val calendarPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // ──── API 设置 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" API 设置 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(16.dp))
        Text("API 地址", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = apiUrl, onValueChange = { apiUrl = it; saved = false },
            placeholder = { Text("https://api.deepseek.com/chat/completions") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(16.dp))
        Text("API Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; saved = false },
            placeholder = { Text("sk-...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(16.dp))
        Text("模型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = model, onValueChange = { model = it; saved = false },
            placeholder = { Text("deepseek-v4-flash") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(24.dp))

        // ──── 显示偏好 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 显示偏好 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(16.dp))
        var mergeOverdue by remember { mutableStateOf(s.value.showOverdueInline) }
        var longChat by remember { mutableStateOf(s.value.longPressChat) }
        Row(Modifier.fillMaxWidth().clickable { mergeOverdue = !mergeOverdue }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("过期任务分区", style = MaterialTheme.typography.bodyMedium)
                Text("关闭后过期任务混排在列表底部（正常→过期→已完成）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = mergeOverdue, onCheckedChange = { mergeOverdue = it })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clickable { longChat = !longChat }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("长按打开 AI 对话框", style = MaterialTheme.typography.bodyMedium)
                Text("关闭后短按打开 AI 对话框，长按打开新建任务窗口", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = longChat, onCheckedChange = { longChat = it })
        }
        Spacer(Modifier.height(12.dp))
        var showToken by remember { mutableStateOf(s.value.showTokenUsage) }
        Row(Modifier.fillMaxWidth().clickable { showToken = !showToken }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("显示 Token 用量", style = MaterialTheme.typography.bodyMedium)
                Text("开启后在头部显示今日 AI 调用 Token 数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = showToken, onCheckedChange = { showToken = it })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clickable { autoSync = !autoSync }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("自动同步日历", style = MaterialTheme.typography.bodyMedium)
                Text("有截止日期的任务自动写入系统日历", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = autoSync, onCheckedChange = {
                autoSync = it
                if (it) {
                    val check = androidx.core.content.ContextCompat.checkSelfPermission(settingsContext, android.Manifest.permission.WRITE_CALENDAR)
                    if (check != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        calendarPermLauncher.launch(android.Manifest.permission.WRITE_CALENDAR)
                    }
                }
            })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("默认提醒时间", style = MaterialTheme.typography.bodyMedium)
                Text("提前 ${defaultRemind} 分钟提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { defaultRemind = (defaultRemind + 5).coerceAtMost(120) }, shape = RoundedCornerShape(8.dp)) { Text("+5", fontSize = 12.sp) }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { defaultRemind = (defaultRemind - 5).coerceAtLeast(0) }, shape = RoundedCornerShape(8.dp)) { Text("-5", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(24.dp))

        // ──── 数据统计 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 数据统计 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(12.dp))
        val total = com.example.aitodoapp.data.TokenRepository.getTotalTokens()
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column {
                Text("总 Token 用量", style = MaterialTheme.typography.bodyMedium)
                Text("输入 ${total.prompt} · 输出 ${total.completion} · 共 ${total.prompt + total.completion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // ──── 测试 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 测试 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showCalendarTest = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("测试日历写入") }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = { SettingsRepository.save(SettingsRepository.Settings(apiUrl.trim(), apiKey.trim(), model.trim(), mergeOverdue, longChat, showToken, autoSync, defaultRemind)); saved = true },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("保存") }
        if (showCalendarTest) CalendarTestDialog(onDismiss = { showCalendarTest = false })
        if (saved) { Spacer(Modifier.height(8.dp)); Text("✓ 已保存", color = MaterialTheme.colorScheme.primary) }
    }
}

// ============ 任务页 ============

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskScreen(tasks: List<Task>, allTags: List<Tag>, onComplete: (String) -> Unit, onAddTask: (String, Priority, LocalDate?, List<String>, String, List<LocalDate>, String?) -> Unit, onDelete: (String) -> Unit, onUpdateTask: (String, String, String, Priority, LocalDate?, List<String>, List<LocalDate>, Boolean, String?, List<String>) -> Unit, modifier: Modifier, isArchive: Boolean, selectedDay: DayFilter = DayFilter.ALL, onSelectDay: (DayFilter) -> Unit = {}, overdueTasks: List<Task> = emptyList(), showOverdueSection: Boolean = true, longPressChat: Boolean = true, showTokenUsage: Boolean = false, onUpdateSettings: (SettingsRepository.Settings) -> Unit = {}, onTagAction: (action: String, tagName: String) -> Unit = { _, _ -> }, onArchiveToggle: (taskId: String, archive: Boolean) -> Unit = { _, _ -> }) {
    val today = LocalDate.now(); var showInput by remember { mutableStateOf(false) }; var chatMode by remember { mutableStateOf(false) }; var chatInput by remember { mutableStateOf("") }; var editTarget by remember { mutableStateOf<Task?>(null) }; var aiReply by remember { mutableStateOf("") }; var aiLoading by remember { mutableStateOf(false) }; var aiStatus by remember { mutableStateOf("") }; var aiDoneMessage by remember { mutableStateOf("") }; var pendingRetryInput by remember { mutableStateOf("") }; var chatFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // 使用 Activity 的 lifecycleScope，切后台时协程不被取消
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = lifecycleOwner.lifecycleScope
    val placeholders = remember { listOf("记个事...", "粘贴长文本或输入...", "告诉 AI 做什么...", "输入任务...", "试试自然语言...", "说你想做的事...") }
    val currentPlaceholder by remember(chatMode) { mutableStateOf(if (chatMode) placeholders.random() else "") }
    // AI 轮播状态消息
    val aiStatusMessages = remember { listOf("🤔 思考中...", "🧠 分析中...", "📡 接收中...", "⚡ 处理中...", "🎯 定位中...", "✨ 优化中...", "🔍 检索中...", "💡 构思中...") }
    LaunchedEffect(aiLoading) {
        if (aiLoading) {
            var i = 0
            while (true) {
                aiStatus = aiStatusMessages[i % aiStatusMessages.size]
                kotlinx.coroutines.delay(2200)
                i++
            }
        } else {
            aiStatus = ""
        }
    }
    // 完成消息 5 秒后自动消失
    LaunchedEffect(aiDoneMessage) {
        if (aiDoneMessage.isNotEmpty()) {
            kotlinx.coroutines.delay(5000)
            aiDoneMessage = ""
        }
    }

    // 切后台网络失败后，回到前台自动重试
    val retryLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(retryLifecycle, pendingRetryInput) {
        if (pendingRetryInput.isNotEmpty()) {
            retryLifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                val input = pendingRetryInput
                pendingRetryInput = ""
                // Re-send the request
                aiLoading = true; aiReply = ""; aiStatus = "🔄 自动重试..."
                kotlinx.coroutines.delay(500) // slight delay to ensure network is ready
                val aiTaskList = (tasks + overdueTasks).distinctBy { it.id }
                val taskDescriptions = aiTaskList.map { t ->
                    val prefix = when {
                        t.isCompleted -> "[已完成] "
                        overdueTasks.any { it.id == t.id } -> "[过期] "
                        else -> ""
                    }
                    prefix + formatTaskForAi(t, today)
                }
                val tagNames = allTags.map { it.name }
                val result = com.example.aitodoapp.data.AiService.processMessage(input, taskDescriptions, tagNames)
                com.example.aitodoapp.data.TokenRepository.recordUsage(result.promptTokens, result.completionTokens)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (result.text.startsWith("网络请求失败")) {
                        aiReply = "⚠️ 网络不可用，请稍后手动重试（输入已保留）"
                        aiLoading = false; aiStatus = ""; return@withContext
                    }
                    aiReply = result.text
                    var created = 0; var completed = 0; var deleted = 0; var updated = 0; var settingsChanged = 0; var tagged = 0; var archived = 0; var unarchived = 0
                    for (action in result.actions) {
                        when (action) {
                            is com.example.aitodoapp.data.AiAction.CreateTask -> {
                                onAddTask(action.title, action.priority, action.deadline, action.tags, action.content, action.plannedDates, action.deadlineTime); created++
                            }
                            is com.example.aitodoapp.data.AiAction.CompleteTask -> {
                                findBestMatch(aiTaskList, action.toMatchCriteria())?.let { onComplete(it.id) }; completed++
                            }
                            is com.example.aitodoapp.data.AiAction.DeleteTask -> {
                                findBestMatch(aiTaskList, action.toMatchCriteria())?.let { onDelete(it.id) }; deleted++
                            }
                            is com.example.aitodoapp.data.AiAction.UpdateTask -> {
                                val task = findBestMatch(aiTaskList, action.toMatchCriteria())
                                if (task != null && (action.newTitle != null || action.priority != null || action.deadline != null || action.tags != null || action.plannedDates != null || action.deadlineTime != null || action.plannedTimes != null)) {
                                    onUpdateTask(task.id, action.newTitle ?: task.title, task.content, action.priority ?: task.priority, action.deadline ?: task.deadline, action.tags ?: task.tags, action.plannedDates ?: task.plannedDates, action.priority != null && action.priority != task.priority, action.deadlineTime, action.plannedTimes ?: emptyList()); updated++
                                }
                            }
                            is com.example.aitodoapp.data.AiAction.CompletedTasks -> {
                                val done = aiTaskList.filter { it.isCompleted }
                                aiReply = "已完成的任务（${done.size}个）：\n" + done.joinToString("\n") { "- ${it.title}" }
                            }
                            is com.example.aitodoapp.data.AiAction.UpdateSettings -> {
                                val cur = SettingsRepository.load()
                                val updated = cur.copy(
                                    apiUrl = action.apiUrl ?: cur.apiUrl,
                                    apiKey = action.apiKey ?: cur.apiKey,
                                    model = action.model ?: cur.model,
                                    showOverdueInline = action.showOverdueInline ?: cur.showOverdueInline,
                                    longPressChat = action.longPressChat ?: cur.longPressChat,
                                    showTokenUsage = action.showTokenUsage ?: cur.showTokenUsage,
                                    autoSyncCalendar = action.autoSyncCalendar ?: cur.autoSyncCalendar,
                                    defaultReminderMinutes = action.defaultReminderMinutes ?: cur.defaultReminderMinutes
                                )
                                SettingsRepository.save(updated); onUpdateSettings(updated); settingsChanged++
                            }
                            is com.example.aitodoapp.data.AiAction.ManageTag -> {
                                onTagAction(action.action, action.tagName); tagged++
                            }
                            is com.example.aitodoapp.data.AiAction.ArchiveTask -> {
                                findBestMatch(aiTaskList, action.toMatchCriteria())?.let { onArchiveToggle(it.id, true) }; archived++
                            }
                            is com.example.aitodoapp.data.AiAction.UnarchiveTask -> {
                                findBestMatch(aiTaskList, action.toMatchCriteria())?.let { onArchiveToggle(it.id, false) }; unarchived++
                            }
                        }
                    }
                    val parts = mutableListOf<String>()
                    val details = mutableListOf<String>()
                    if (created > 0) { parts.add("📝添加了${created}个任务"); val a = result.actions.firstOrNull { it is com.example.aitodoapp.data.AiAction.CreateTask } as? com.example.aitodoapp.data.AiAction.CreateTask; if (a != null) { var d = a.title; if (a.deadline != null) d += " 截止${a.deadline?.format(java.time.format.DateTimeFormatter.ofPattern("M月d日")) ?: ""}"; if (a.deadlineTime != null) d += " ${a.deadlineTime}"; details.add(d) } }
                    if (completed > 0) { parts.add("✅完成了${completed}个任务"); details.add(result.actions.filterIsInstance<com.example.aitodoapp.data.AiAction.CompleteTask>().firstOrNull()?.title ?: "") }
                    if (deleted > 0) { parts.add("🗑️删除了${deleted}个任务"); details.add(result.actions.filterIsInstance<com.example.aitodoapp.data.AiAction.DeleteTask>().firstOrNull()?.title ?: "") }
                    if (updated > 0) { parts.add("✏️修改了${updated}个任务"); details.add(result.actions.filterIsInstance<com.example.aitodoapp.data.AiAction.UpdateTask>().firstOrNull()?.let { "修改 ${it.title}" } ?: "") }
                    if (settingsChanged > 0) parts.add("⚙️已更新设置")
                    if (tagged > 0) parts.add("🏷已更新标签")
                    if (archived > 0) parts.add("📦已归档${archived}个")
                    if (unarchived > 0) parts.add("📤已恢复${unarchived}个")
                    if (parts.isNotEmpty()) {
                        aiDoneMessage = parts.joinToString(" ")
                        val notifBody = details.filter { it.isNotBlank() }.joinToString("\n")
                        com.example.aitodoapp.data.NotificationHelper.show(context, "AI 代办", if (notifBody.isNotBlank()) notifBody else parts.joinToString(" "))
                    }
                    aiLoading = false; aiStatus = ""
                }
            }
        }
    }

    // AI 加载安全兜底：超过 40 秒自动重置
    LaunchedEffect(aiLoading) {
        if (aiLoading) {
            kotlinx.coroutines.delay(40000)
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
                if (showTokenUsage) {
                    val todayTk = com.example.aitodoapp.data.TokenRepository.getTodayTokens()
                    if (todayTk.prompt + todayTk.completion > 0) {
                        Text("⬆${todayTk.prompt} ⬇${todayTk.completion}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                    }
                }
            }
            // AI 处理进度 / 完成反馈（头部显示，退出聊天窗口也能看到）
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
            if (!isArchive) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayFilter.entries.forEach { filter ->
                        val selected = selectedDay == filter
                        Surface(
                            onClick = { onSelectDay(filter) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(filter.label(today), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            if (!isArchive && selectedDay == DayFilter.ALL) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("全部任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text("(${tasks.size})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val inlineOverdue = !isArchive && selectedDay == DayFilter.TODAY && overdueTasks.isNotEmpty() && !showOverdueSection
            val displayTasks = if (inlineOverdue) {
                tasks.filter { !it.isCompleted } + overdueTasks + tasks.filter { it.isCompleted }
            } else tasks
            if (displayTasks.isEmpty() && overdueTasks.isEmpty()) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(if (isArchive) "还没有归档的任务" else if (selectedDay == DayFilter.OVERDUE) "没有过期任务" else if (selectedDay == DayFilter.TODAY) "今天没有任务" else "还没有任务", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else {
                Column(Modifier.weight(1f)) {
                    LazyColumn(Modifier.weight(1f)) { items(displayTasks, key = { it.id }) { task -> TaskItem(task, isArchive, { onComplete(task.id) }, { editTarget = task }); HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) } }
                    // 过期任务折叠栏（仅设置开启时显示）
                    if (!isArchive && selectedDay == DayFilter.TODAY && overdueTasks.isNotEmpty() && showOverdueSection) {
                        var showOverdue by remember { mutableStateOf(true) }
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                                Text(" 过期任务 (${overdueTasks.size}) ", color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.clickable { showOverdue = !showOverdue })
                                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                            }
                            if (showOverdue) {
                                LazyColumn(Modifier.heightIn(max = 200.dp)) { items(overdueTasks, key = { it.id }) { task -> TaskItem(task, false, { onComplete(task.id) }, { editTarget = task }); HorizontalDivider(Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)) } }
                            }
                        }
                    }
                }
            }
        }
        if (!isArchive) Box(Modifier.align(Alignment.BottomEnd).padding(28.dp).size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).combinedClickable(
            onClick = { if (longPressChat) showInput = true else { chatMode = true; chatInput = "" } },
            onLongClick = { if (longPressChat) { chatMode = true; chatInput = "" } else showInput = true }
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
                        Spacer(Modifier.width(8.dp))
                        Text(aiStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            aiLoading = true; aiReply = ""; aiStatus = "🔄 正在连接 AI..."
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val aiTaskList = (tasks + overdueTasks).distinctBy { it.id }
                                    val taskDescriptions = aiTaskList.map { t ->
                                        val prefix = when {
                                            t.isCompleted -> "[已完成] "
                                            overdueTasks.any { it.id == t.id } -> "[过期] "
                                            else -> ""
                                        }
                                        prefix + formatTaskForAi(t, today)
                                    }
                                    val tagNames = allTags.map { it.name }
                                    val result = AiService.processMessage(t, taskDescriptions, tagNames)
                                    com.example.aitodoapp.data.TokenRepository.recordUsage(result.promptTokens, result.completionTokens)
                                    scope.launch(Dispatchers.Main) {
                                        if (result.text.startsWith("网络请求失败")) {
                                            pendingRetryInput = t
                                            aiReply = "⚠️ 网络不可用，回到前台自动重试..."
                                            aiLoading = false; aiStatus = ""; return@launch
                                        }
                                        aiReply = result.text
                                        var created = 0; var completed = 0; var deleted = 0; var updated = 0; var settingsChanged = 0; var tagged = 0; var archived = 0; var unarchived = 0
                                        for (action in result.actions) {
                                            when (action) {
                                                is com.example.aitodoapp.data.AiAction.CreateTask -> { onAddTask(action.title, action.priority, action.deadline, action.tags, action.content, action.plannedDates, action.deadlineTime); created++ }
                                                is com.example.aitodoapp.data.AiAction.CompleteTask -> { findBestMatch(aiTaskList, action.toMatchCriteria())?.let { onComplete(it.id) }; completed++ }
                                                is com.example.aitodoapp.data.AiAction.DeleteTask -> { findBestMatch(aiTaskList, action.toMatchCriteria())?.let { onDelete(it.id) }; deleted++ }
                                                is com.example.aitodoapp.data.AiAction.UpdateTask -> {
                                                    val task = findBestMatch(aiTaskList, action.toMatchCriteria())
                                                    if (task != null && (action.newTitle != null || action.priority != null || action.deadline != null || action.tags != null || action.plannedDates != null || action.deadlineTime != null || action.plannedTimes != null)) {
                                                        onUpdateTask(
                                                            task.id,
                                                            action.newTitle ?: task.title,
                                                            task.content,
                                                            action.priority ?: task.priority,
                                                            action.deadline ?: task.deadline,
                                                            action.tags ?: task.tags,
                                                            action.plannedDates ?: task.plannedDates,
                                                            action.priority != null && action.priority != task.priority,
                                                            action.deadlineTime,
                                                            action.plannedTimes ?: emptyList()
                                                        ); updated++
                                                    }
                                                }
                                                is com.example.aitodoapp.data.AiAction.CompletedTasks -> {
                                                    val done = aiTaskList.filter { it.isCompleted }
                                                    aiReply = "已完成的任务（${done.size}个）：\n" + done.joinToString("\n") { "- ${it.title}" }
                                                }
                                                is com.example.aitodoapp.data.AiAction.UpdateSettings -> {
                                                    val cur = SettingsRepository.load()
                                                    val upd = cur.copy(
                                                        apiUrl = action.apiUrl ?: cur.apiUrl,
                                                        apiKey = action.apiKey ?: cur.apiKey,
                                                        model = action.model ?: cur.model,
                                                        showOverdueInline = action.showOverdueInline ?: cur.showOverdueInline,
                                                        longPressChat = action.longPressChat ?: cur.longPressChat,
                                                        showTokenUsage = action.showTokenUsage ?: cur.showTokenUsage,
                                                        autoSyncCalendar = action.autoSyncCalendar ?: cur.autoSyncCalendar,
                                                        defaultReminderMinutes = action.defaultReminderMinutes ?: cur.defaultReminderMinutes
                                                    )
                                                    SettingsRepository.save(upd); onUpdateSettings(upd); settingsChanged++
                                                }
                                                is com.example.aitodoapp.data.AiAction.ManageTag -> { onTagAction(action.action, action.tagName); tagged++ }
                                                is com.example.aitodoapp.data.AiAction.ArchiveTask -> { findBestMatch(aiTaskList, action.toMatchCriteria())?.let { onArchiveToggle(it.id, true) }; archived++ }
                                                is com.example.aitodoapp.data.AiAction.UnarchiveTask -> { findBestMatch(aiTaskList, action.toMatchCriteria())?.let { onArchiveToggle(it.id, false) }; unarchived++ }
                                            }
                                        }
                                        val parts = mutableListOf<String>()
                                        val details = mutableListOf<String>()
                                        if (created > 0) { parts.add("📝添加了${created}个任务"); val a = result.actions.firstOrNull { it is com.example.aitodoapp.data.AiAction.CreateTask } as? com.example.aitodoapp.data.AiAction.CreateTask; if (a != null) { var d = a.title; if (a.deadline != null) d += " 截止${a.deadline?.format(java.time.format.DateTimeFormatter.ofPattern("M月d日")) ?: ""}"; if (a.deadlineTime != null) d += " ${a.deadlineTime}"; details.add(d) } }
                                        if (completed > 0) { parts.add("✅完成了${completed}个任务"); details.add(result.actions.filterIsInstance<com.example.aitodoapp.data.AiAction.CompleteTask>().firstOrNull()?.title ?: "") }
                                        if (deleted > 0) { parts.add("🗑️删除了${deleted}个任务"); details.add(result.actions.filterIsInstance<com.example.aitodoapp.data.AiAction.DeleteTask>().firstOrNull()?.title ?: "") }
                                        if (updated > 0) { parts.add("✏️修改了${updated}个任务"); details.add(result.actions.filterIsInstance<com.example.aitodoapp.data.AiAction.UpdateTask>().firstOrNull()?.let { "修改 ${it.title}" } ?: "") }
                                        if (settingsChanged > 0) parts.add("⚙️已更新设置")
                                        if (tagged > 0) parts.add("🏷已更新标签")
                                        if (archived > 0) parts.add("📦已归档${archived}个")
                                        if (unarchived > 0) parts.add("📤已恢复${unarchived}个")
                                        if (parts.isNotEmpty()) {
                                            aiDoneMessage = parts.joinToString(" ")
                                            val notifBody = details.filter { it.isNotBlank() }.joinToString("\n")
                                            com.example.aitodoapp.data.NotificationHelper.show(context, "AI 代办", if (notifBody.isNotBlank()) notifBody else parts.joinToString(" "))
                                        }
                                        aiLoading = false; aiStatus = ""
                                        chatInput = ""
                                    }
                                } catch (_: kotlinx.coroutines.CancellationException) {
                                    // 切后台时协程被取消，不显示错误
                                    aiLoading = false; aiStatus = ""
                                } catch (e: Exception) {
                                    scope.launch(Dispatchers.Main) {
                                        aiReply = "出错了：${e.message}"
                                        aiLoading = false; aiStatus = ""
                                    }
                                }
                            }
                        }
                    }, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(52.dp), enabled = !aiLoading) { Text(if (aiLoading) "..." else "发送") }
                }
            }
        }
        if (showInput) AddTaskDialog(allTags, { showInput = false }) { t, p, d, tags, c, pl, dt -> onAddTask(t, p, d, tags, c, pl, dt); showInput = false }
        if (editTarget != null) EditTaskDialog(editTarget!!, allTags, { editTarget = null }, { id, ti, c, p, d, tags, pl, locked, dt, pt -> onUpdateTask(id, ti, c, p, d, tags, pl, locked, dt, pt); editTarget = null }, { onDelete(editTarget!!.id); editTarget = null })
    }
}

// ============ 标签管理 ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagerScreen(allTags: List<Tag>, allTasks: List<Task>, onCreate: (String) -> Unit, onPromote: (String) -> Unit, onDelete: (String) -> Unit, modifier: Modifier) {
    var newTagName by remember { mutableStateOf("") }; var selectedTag by remember { mutableStateOf("") }
    val formalTags = allTags.filter { !it.isTemporary }; val tempTags = allTags.filter { it.isTemporary }
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text("标签管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("创建标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newTagName, onValueChange = { newTagName = it }, placeholder = { Text("输入标签名") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.width(8.dp)); Button({ onCreate(newTagName.trim()); newTagName = "" }, shape = RoundedCornerShape(12.dp)) { Text("创建") }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { formalTags.forEach { val cnt = allTasks.count { t -> t.tags.contains(it.name) }; TagChip(it.name + " (" + cnt.toString() + ")", false, onDelete = { onDelete(it.name) }, onClick = { selectedTag = it.name }) }; if (formalTags.isEmpty()) Text("还没有正式标签", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("临时标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Text("(${tempTags.size})", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(4.dp)); Text("添加任务时自动收录，点击箭头转为正式标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (tempTags.isEmpty()) Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) { Text("没有临时标签", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) { items(tempTags) { tag -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${tag.name}", modifier = Modifier.weight(1f))
            OutlinedButton({ onPromote(tag.name) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(32.dp)) { Text("→ 转为正式", fontSize = 12.sp) }
            Spacer(Modifier.width(8.dp)); Text("✕", color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).padding(4.dp).clickable { onDelete(tag.name) }) } } }
    }
        if (selectedTag.isNotEmpty()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { selectedTag = "" }.padding(32.dp), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp)) {
                    Text("🏷 " + selectedTag, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    val taggedTasks = allTasks.filter { it.tags.contains(selectedTag) }
                    if (taggedTasks.isEmpty()) {
                        Text("没有任务使用此标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("共 " + taggedTasks.size.toString() + " 个任务", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                            taggedTasks.forEach { task ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (task.isCompleted) "✅" else "⬜", fontSize = 14.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None)
                                        Text(if (task.deadline != null) java.time.format.DateTimeFormatter.ofPattern("M月d日").format(task.deadline) else "无截止", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { selectedTag = "" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("关闭") }
                }
            }
        }
}

// ============ 标签组件 ============

@Composable
fun TagChip(name: String, isTemporary: Boolean, onDelete: () -> Unit, onClick: () -> Unit = {}) {
    val bg = if (isTemporary) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
    val fg = if (isTemporary) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onPrimaryContainer
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (isTemporary) "📌 " else "#", fontSize = 12.sp, color = fg); Text(name, style = MaterialTheme.typography.labelMedium, color = fg, modifier = Modifier.clickable(onClick = onClick))
        Spacer(Modifier.width(6.dp)); Text("✕", fontSize = 12.sp, color = fg.copy(alpha = 0.5f), modifier = Modifier.clickable(onClick = onDelete))
    }
}

// ============ 添加任务弹窗 ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(allTags: List<Tag>, onDismiss: () -> Unit, onConfirm: (String, Priority, LocalDate?, List<String>, String, List<LocalDate>, String?) -> Unit) {
    var title by remember { mutableStateOf("") }; var content by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(Priority.P3) }; var hasDeadline by remember { mutableStateOf(false) }
    var deadlineDate by remember { mutableStateOf(LocalDate.now()) }; var showPriorityMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var hasPlan by remember { mutableStateOf(false) }; var plannedDates by remember { mutableStateOf(listOf<LocalDate>()) }; var showPlanPicker by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(listOf<String>()) }; var tagInput by remember { mutableStateOf("") }; var showTagSuggest by remember { mutableStateOf(false) }
    val suggestions = allTags.map { it.name }.filter { it !in selectedTags }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { onDismiss() })
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("新建任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("任务标题") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = content, onValueChange = { content = it }, placeholder = { Text("详细描述、注意事项等") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("优先级", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                Box { OutlinedButton({ showPriorityMenu = true }, shape = RoundedCornerShape(10.dp)) { Text("${selectedPriority.emoji} ${selectedPriority.label}", fontSize = 14.sp) }
                    DropdownMenu(showPriorityMenu, { showPriorityMenu = false }) { Priority.entries.forEach { DropdownMenuItem(text = { Text("${it.emoji} ${it.label}") }, onClick = { selectedPriority = it; showPriorityMenu = false }) } } } }
            Spacer(Modifier.height(12.dp))
            Text("标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(4.dp))
            if (selectedTags.isNotEmpty()) { FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(6.dp)) { selectedTags.forEach { TagChip(it, false, onDelete = { selectedTags = selectedTags.filter { t -> t != it } }) } }; Spacer(Modifier.height(6.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = tagInput, onValueChange = { tagInput = it; showTagSuggest = true }, placeholder = { Text(if (suggestions.isEmpty()) "输入新标签" else "输入或选择标签") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(8.dp)); Button({ val n = tagInput.trim(); if (n.isNotBlank() && n !in selectedTags) { selectedTags = selectedTags + n; tagInput = "" } }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(52.dp)) { Text("+") }
            }
            if (showTagSuggest && tagInput.isNotBlank() && suggestions.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                suggestions.filter { it.contains(tagInput, ignoreCase = true) }.take(5).forEach { s -> Text("+ $s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { selectedTags = selectedTags + s; tagInput = "" }) }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("已有标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                    suggestions.take(12).forEach { s ->
                        Text("#$s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { selectedTags = selectedTags + s; tagInput = "" })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasDeadline, { hasDeadline = it }); Spacer(Modifier.width(4.dp)); Text("设置截止日期") }
            if (hasDeadline) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { showDatePicker = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("📅 ${deadlineDate.format(DateTimeFormatter.ofPattern("M月d日  EEEE"))}", fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasPlan, { hasPlan = it; if (!it) plannedDates = emptyList() }); Spacer(Modifier.width(4.dp)); Text("设计划时间") }
            if (hasPlan) {
                Spacer(Modifier.height(6.dp))
                if (plannedDates.isNotEmpty()) FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                    plannedDates.sorted().forEach { d -> Row(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.format(DateTimeFormatter.ofPattern("M/d")), fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(4.dp)); Text("✕", fontSize = 10.sp, modifier = Modifier.clickable { plannedDates = plannedDates.filter { it != d } })
                    } }
                }
                OutlinedButton(onClick = { showPlanPicker = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text("+ 选择日期", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("取消") }
                Button(onClick = { if (title.isNotBlank()) onConfirm(title.trim(), selectedPriority, if (hasDeadline) deadlineDate else null, selectedTags, content.trim(), if (hasPlan) plannedDates else emptyList(), null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = title.isNotBlank()) { Text("添加") }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = deadlineDate.toEpochDay() * 86400000L)
            DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { deadlineDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                    showDatePicker = false
                }) { Text("确定") }
            }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
            ) { DatePicker(state = datePickerState) }
        }
        if (showPlanPicker) {
            val planPickerState = rememberDatePickerState()
            DatePickerDialog(onDismissRequest = { showPlanPicker = false }, confirmButton = {
                TextButton(onClick = {
                    planPickerState.selectedDateMillis?.let { val d = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate(); if (d !in plannedDates) plannedDates = plannedDates + d }
                    showPlanPicker = false
                }) { Text("添加") }
            }, dismissButton = { TextButton(onClick = { showPlanPicker = false }) { Text("取消") } }
            ) { DatePicker(state = planPickerState) }
        }
    }
    }
}

// ============ 编辑任务弹窗 ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTaskDialog(task: Task, allTags: List<Tag>, onDismiss: () -> Unit, onSave: (String, String, String, Priority, LocalDate?, List<String>, List<LocalDate>, Boolean, String?, List<String>) -> Unit, onDelete: () -> Unit) {
    var title by remember { mutableStateOf(task.title) }; var content by remember { mutableStateOf(task.content) }
    var selectedPriority by remember { mutableStateOf(task.priority) }; var hasDeadline by remember { mutableStateOf(task.deadline != null) }
    var deadlineDate by remember { mutableStateOf(task.deadline ?: LocalDate.now()) }; var showPriorityMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dh by remember { mutableStateOf(if (task.deadlineTime != null) task.deadlineTime!!.substringBefore(":").toIntOrNull() ?: 10 else 10) }
    var dm by remember { mutableStateOf(if (task.deadlineTime != null) task.deadlineTime!!.substringAfter(":").toIntOrNull() ?: 0 else 0) }
    var ph by remember { mutableStateOf(if (task.plannedTimes.isNotEmpty()) task.plannedTimes.first().substringBefore(":").toIntOrNull() ?: 10 else 10) }
    var pm by remember { mutableStateOf(if (task.plannedTimes.isNotEmpty()) task.plannedTimes.first().substringAfter(":").toIntOrNull() ?: 0 else 0) }
    var hasPlan by remember { mutableStateOf(task.plannedDates.isNotEmpty()) }
    var plannedDates by remember { mutableStateOf(task.plannedDates) }; var showPlanPicker by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(task.tags) }; var tagInput by remember { mutableStateOf("") }; var showTagSuggest by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val suggestions = allTags.map { it.name }.filter { it !in selectedTags }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { onDismiss() })
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("编辑任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("任务标题") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = content, onValueChange = { content = it }, placeholder = { Text("详细描述、注意事项等") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("优先级", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                Box { OutlinedButton({ showPriorityMenu = true }, shape = RoundedCornerShape(10.dp)) { Text("${selectedPriority.emoji} ${selectedPriority.label}", fontSize = 14.sp) }
                    DropdownMenu(showPriorityMenu, { showPriorityMenu = false }) { Priority.entries.forEach { DropdownMenuItem(text = { Text("${it.emoji} ${it.label}") }, onClick = { selectedPriority = it; showPriorityMenu = false }) } } } }
            Spacer(Modifier.height(12.dp))
            Text("标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(4.dp))
            if (selectedTags.isNotEmpty()) { FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(6.dp)) { selectedTags.forEach { TagChip(it, false, onDelete = { selectedTags = selectedTags.filter { t -> t != it } }) } }; Spacer(Modifier.height(6.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = tagInput, onValueChange = { tagInput = it; showTagSuggest = true }, placeholder = { Text(if (suggestions.isEmpty()) "输入新标签" else "输入或选择标签") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(8.dp)); Button({ val n = tagInput.trim(); if (n.isNotBlank() && n !in selectedTags) { selectedTags = selectedTags + n; tagInput = "" } }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(52.dp)) { Text("+") }
            }
            if (showTagSuggest && tagInput.isNotBlank() && suggestions.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                suggestions.filter { it.contains(tagInput, true) }.take(5).forEach { Text("+ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { selectedTags = selectedTags + it; tagInput = "" }) }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("已有标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                    suggestions.take(12).forEach { s ->
                        Text("#$s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { selectedTags = selectedTags + s; tagInput = "" })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasDeadline, { hasDeadline = it }); Spacer(Modifier.width(4.dp)); Text("设置截止日期") }
            if (hasDeadline) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { showDatePicker = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("📅 ${deadlineDate.format(DateTimeFormatter.ofPattern("M月d日  EEEE"))}", fontSize = 15.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { dh = (dh + 1) % 24 }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Text("🕐 ${dh.toString().padStart(2,'0')}:${dm.toString().padStart(2,'0')}", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { dm = (dm + 5) % 60 }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("分钟 +5", fontSize = 12.sp) }
                    OutlinedButton(onClick = { dm = (dm - 5 + 60) % 60 }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) { Text("分钟 -5", fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(12.dp))
            // 计划日期
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasPlan, { hasPlan = it; if (!it) plannedDates = emptyList() }); Spacer(Modifier.width(4.dp)); Text("设计划时间") }
            if (hasPlan) {
                Spacer(Modifier.height(6.dp))
                if (plannedDates.isNotEmpty()) FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                    plannedDates.sorted().forEach { d -> Row(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.format(DateTimeFormatter.ofPattern("M/d")), fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(4.dp)); Text("✕", fontSize = 10.sp, modifier = Modifier.clickable { plannedDates = plannedDates.filter { it != d } })
                    } }
                }
                OutlinedButton(onClick = { showPlanPicker = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text("+ 选择日期", fontSize = 13.sp) }
                if (hasPlan && plannedDates.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("时间：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ph = (ph + 1) % 24 }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Text("🕐 ${ph.toString().padStart(2,'0')}:${pm.toString().padStart(2,'0')}", fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = { pm = (pm + 5) % 60 }, shape = RoundedCornerShape(8.dp)) { Text("+5", fontSize = 12.sp) }
                        OutlinedButton(onClick = { pm = (pm - 5 + 60) % 60 }, shape = RoundedCornerShape(8.dp)) { Text("-5", fontSize = 12.sp) }
                        OutlinedButton(onClick = {
                            val times = listOf("08:00","09:00","10:00","12:00","14:00","15:00","16:00","18:00","20:00","22:00")
                            val current = "${ph.toString().padStart(2,'0')}:${pm.toString().padStart(2,'0')}"
                            val idx = times.indexOf(current)
                            val next = if (idx >= 0 && idx < times.size - 1) times[idx + 1] else times[0]
                            ph = next.substringBefore(":").toInt(); pm = next.substringAfter(":").toInt()
                        }, shape = RoundedCornerShape(8.dp)) { Text("常用", fontSize = 12.sp) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("取消") }
                Button(onClick = { if (title.isNotBlank()) onSave(task.id, title.trim(), content.trim(), selectedPriority, if (hasDeadline) deadlineDate else null, selectedTags, if (hasPlan) plannedDates else emptyList(), selectedPriority != task.priority, if (hasDeadline) "${dh.toString().padStart(2,'0')}:${dm.toString().padStart(2,'0')}" else null, if (hasPlan && plannedDates.isNotEmpty()) listOf("${ph.toString().padStart(2,'0')}:${pm.toString().padStart(2,'0')}") else emptyList()) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = title.isNotBlank()) { Text("保存") }
            }
            Spacer(Modifier.height(12.dp))
            if (deleteConfirm) { Text("确定要删除？", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick = { deleteConfirm = false }, shape = RoundedCornerShape(8.dp)) { Text("取消") }; Button(onClick = onDelete, shape = RoundedCornerShape(8.dp)) { Text("确认删除", color = MaterialTheme.colorScheme.error) } }
            } else { TextButton(onClick = { deleteConfirm = true }) { Text("删除任务", color = MaterialTheme.colorScheme.error) } }
            Spacer(Modifier.height(4.dp))
        }
        // 日历选择器
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = deadlineDate.toEpochDay() * 86400000L)
            DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { deadlineDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                    showDatePicker = false
                }) { Text("确定") }
            }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
            ) { DatePicker(state = datePickerState) }
        }
        // 计划日期选择器
        if (showPlanPicker) {
            val planPickerState = rememberDatePickerState()
            DatePickerDialog(onDismissRequest = { showPlanPicker = false }, confirmButton = {
                TextButton(onClick = {
                    planPickerState.selectedDateMillis?.let { val d = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate(); if (d !in plannedDates) plannedDates = plannedDates + d }
                    showPlanPicker = false
                }) { Text("添加") }
            }, dismissButton = { TextButton(onClick = { showPlanPicker = false }) { Text("取消") } }
            ) { DatePicker(state = planPickerState) }
        }
    }
    }
}

// ============ 日期顶部 ============

// ============ 单条任务 ============

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TaskItem(task: Task, isArchive: Boolean, onComplete: () -> Unit, onDelete: () -> Unit) {
    val today = LocalDate.now(); val isOverdue = task.deadline != null && task.deadline < today && !task.isCompleted; val isDueToday = task.deadline == today && !task.isCompleted; val isDone = task.isCompleted || task.isArchived; val planOverdue = task.plannedDates.isNotEmpty() && task.plannedDates.all { it < today } && !task.isCompleted
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onDelete).padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)).background(if (isDone) Color(0xFFBDBDBD) else task.priority.color))
            Spacer(Modifier.width(12.dp))
            if (!isArchive) { Checkbox(task.isCompleted, { onComplete() }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50), uncheckedColor = task.priority.color.copy(alpha = 0.5f))); Spacer(Modifier.width(4.dp)) }
            else { Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF4CAF50).copy(alpha = 0.8f)), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = if (!isDone && (isDueToday || isOverdue)) FontWeight.SemiBold else FontWeight.Normal, textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None, color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!isDone) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("${task.priority.emoji} ${task.priority.label}", style = MaterialTheme.typography.labelSmall, color = task.priority.color) }
                        if (task.deadline != null) { Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            val dt = when { isOverdue -> "过期${today.toEpochDay() - task.deadline.toEpochDay()}天"; isDueToday -> "今天截止"; else -> task.deadline.format(DateTimeFormatter.ofPattern("M月d日")) }
                            val dc = when { isOverdue -> MaterialTheme.colorScheme.error; isDueToday -> Color(0xFFE65100); else -> MaterialTheme.colorScheme.onSurfaceVariant }
                            Text(dt, style = MaterialTheme.typography.labelSmall, color = dc) }
                        task.tags.forEach { Text("· #$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                        if (task.plannedDates.isNotEmpty()) { val pd = task.plannedDates.sorted(); Text("· 📅 ${pd.first().format(DateTimeFormatter.ofPattern("M/d"))}" + if (pd.size > 1) "等${pd.size}天" else "", style = MaterialTheme.typography.labelSmall, color = if (planOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                        if (planOverdue) Text("· 计划逾期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (isArchive) { Spacer(Modifier.height(4.dp)); TextButton(onComplete) { Text("取消归档", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) } }
            }
        }
    }
}

// ============ 预览 ============

@Preview(showBackground = true, showSystemUi = true, heightDp = 700)
@Composable
fun AppMainPreview() { AiTodoAppTheme { AppMain() } }
