package com.example.aitodoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitodoapp.data.AiService
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
        // 状态栏深色字体（WindowInsetsControllerCompat 自动处理 API 版本）
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        TaskRepository.init(applicationContext)
        SettingsRepository.init(applicationContext)
        TokenRepository.init(applicationContext)
        ReportRepository.init(applicationContext)
        NotificationHelper.createChannel(applicationContext)
        // 恢复保活状态：系统重启或 App 被完全杀过后重新打开时，按上次设置决定是否拉起前台服务
        if (SettingsRepository.load().keepAliveEnabled) {
            try { ForegroundService.start(applicationContext) } catch (_: Exception) {}
        }
        if (intent?.getBooleanExtra("open_report", false) == true) {
            openReportTrigger = true
            intent.removeExtra("open_report")
        }
        setContent { AiTodoAppTheme { AppMain(openReportTrigger = openReportTrigger, onClearReportTrigger = { openReportTrigger = false }) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_report", false)) {
            openReportTrigger = true
            intent.removeExtra("open_report")
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent?.getBooleanExtra("open_report", false) == true) {
            openReportTrigger = true
            intent?.removeExtra("open_report")
        }
    }

}

// ============ 主导航 ============

@Composable
fun AppMain(viewModel: MainViewModel = viewModel(), openReportTrigger: Boolean = false, onClearReportTrigger: () -> Unit = {}) {
    val today = viewModel.today
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.initialize() }
    LaunchedEffect(viewModel.tab) { viewModel.refreshSettings() }

    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = viewModel.tab == 0, onClick = { viewModel.tab = 0 }, icon = { Text("📋", fontSize = 16.sp) }, label = { Text("任务 (${viewModel.activeTasks.size})") })
            NavigationBarItem(selected = viewModel.tab == 1, onClick = { viewModel.tab = 1 }, icon = { Text("📦", fontSize = 16.sp) }, label = { Text("归档 (${viewModel.archivedTasks.size})") })
            NavigationBarItem(selected = viewModel.tab == 2, onClick = { viewModel.tab = 2 }, icon = { Text("🏷", fontSize = 16.sp) }, label = { Text("标签") })
            NavigationBarItem(selected = viewModel.tab == 3, onClick = { viewModel.tab = 3 }, icon = { Text("⚙️", fontSize = 16.sp) }, label = { Text("设置") })
        }
    }) { innerPadding ->
        when (viewModel.tab) {
            0 -> TaskScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
                openReportTrigger = openReportTrigger,
                onClearReportTrigger = onClearReportTrigger
            )
            1 -> ArchiveScreen(viewModel.archivedTasks, { viewModel.unarchiveTask(it) }, { viewModel.deleteTask(it) })
            2 -> TagManagerScreen(viewModel.allTags, viewModel.allActive, { viewModel.createTag(it) }, { viewModel.promoteTag(it) }, { viewModel.deleteTag(it) }, Modifier.padding(innerPadding))
            3 -> SettingsScreen(Modifier.padding(innerPadding), onTestReport = { isMorning ->
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val aiTasks = (viewModel.tasks + viewModel.overdueTasks).distinctBy { it.id }
                            val descs = aiTasks.map { t ->
                                val p = when { t.isCompleted -> "[已完成] "; viewModel.overdueTasks.any { o -> o.id == t.id } -> "[过期] "; else -> "" }
                                p + formatTaskForAi(t, today)
                            }
                            val tags = viewModel.allTags.map { it.name }
                            val result = AiService.generateDailyReport(descs, tags, isMorning)
                            if (result.error == null) {
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
                }, onRestoreTask = { id -> viewModel.restoreTask(id) },
                onPermanentDelete = { id -> viewModel.permanentDelete(id) })
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, heightDp = 700)
@Composable
fun AppMainPreview() { AiTodoAppTheme { AppMain() } }
