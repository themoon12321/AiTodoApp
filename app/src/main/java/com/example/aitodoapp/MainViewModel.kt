package com.example.aitodoapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.aitodoapp.data.CalendarSyncHelper
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    // ===== 状态 =====
    var tasks by mutableStateOf(TaskRepository.load<Task>("tasks.json"))
        private set
    var allTags by mutableStateOf(TaskRepository.load<Tag>("tags.json"))
        private set
    var settings by mutableStateOf(SettingsRepository.load())
        private set
    var selectedDay by mutableStateOf(DayFilter.TODAY)
    var tab by mutableStateOf(0)

    val today: LocalDate get() = LocalDate.now()

    // ===== 派生属性 =====
    val allActive get() = tasks.filter { !it.isArchived && !it.isDeleted }

    val overdueTasks get() = allActive.filter {
        !it.isCompleted && (
            (it.deadline != null && it.deadline < today) ||
            (it.plannedDates.isNotEmpty() && it.plannedDates.all { d -> d < today })
        )
    }

    val activeTasks get() = when (selectedDay) {
        DayFilter.OVERDUE -> allActive.filter { it.deadline != null && it.deadline < today && !it.isCompleted }
        DayFilter.ALL -> allActive
        DayFilter.TODAY -> allActive.filter {
            val todayRelevant = it.plannedDates.any { d -> d == today } || it.deadline == today ||
                (it.plannedDates.isEmpty() && it.createdAt <= today && (it.deadline == null || it.deadline >= today))
            todayRelevant || (it.isCompleted && it.completedAt == today)
        }
        else -> {
            val d = selectedDay.date(today) ?: today
            allActive.filter {
                it.plannedDates.any { p -> p == d } ||
                (it.plannedDates.isEmpty() && it.createdAt <= d && (it.deadline == null || it.deadline >= d))
            }
        }
    }

    val archivedTasks get() = tasks.filter { it.isArchived && !it.isDeleted }
    val deletedTasks get() = tasks.filter { it.isDeleted }.sortedByDescending { it.deletedAt ?: it.createdAt }

    // ===== 内部方法 =====

    private fun autoPriority(task: Task): Priority {
        if (task.isCompleted || task.isArchived || task.priorityLocked) return task.priority
        val daysTillDeadline = task.deadline?.let { ChronoUnit.DAYS.between(today, it) }
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

    private fun saveAll() {
        TaskRepository.save("tasks.json", tasks)
        TaskRepository.save("tags.json", allTags)
    }

    // ===== 初始化（需在 LaunchedEffect 中调用一次） =====
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        initialized = true
        var changed = false
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
                if (t.completedAt != null && t.completedAt < today && !t.isArchived) {
                    changed = true; nt = nt.copy(isArchived = true)
                }
                val ap = autoPriority(nt)
                if (ap != nt.priority && !nt.isArchived && !nt.isCompleted) {
                    changed = true; nt = nt.copy(priority = ap)
                }
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
                        val eid = withContext(Dispatchers.IO) {
                            CalendarSyncHelper.createEvent(context, task.title, syncDate, settings.defaultReminderMinutes,
                                time = task.deadlineTime?.let { try { LocalTime.parse(it) } catch (_: Exception) { null } },
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

    // ===== 操作 =====

    fun refreshSettings() {
        settings = SettingsRepository.load()
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
                        time = updated.deadlineTime?.let { try { LocalTime.parse(it) } catch (_: Exception) { null } },
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
        tasks = tasks.map { if (it.id == id) it.copy(isDeleted = true, deletedAt = today) else it }
        saveAll()
    }

    fun archiveTask(id: String, completedDate: LocalDate? = null) {
        tasks = tasks.map { if (it.id == id) it.copy(isArchived = true, isCompleted = true, completedAt = completedDate ?: today) else it }
        saveAll()
    }

    fun unarchiveTask(id: String) {
        tasks = tasks.map { if (it.id == id) it.copy(isArchived = false, isCompleted = false) else it }
        saveAll()
    }

    fun addTag(n: String): String {
        val t = n.trim()
        if (t.isBlank() || allTags.any { it.name == t }) return t
        allTags = allTags + Tag(t, true)
        saveAll()
        return t
    }

    fun createTag(n: String) {
        val t = n.trim()
        if (t.isNotBlank() && allTags.none { it.name == t }) { allTags = allTags + Tag(t, false); saveAll() }
    }

    fun promoteTag(n: String) {
        allTags = allTags.map { if (it.name == n) it.copy(isTemporary = false) else it }
        saveAll()
    }

    fun deleteTag(n: String) {
        allTags = allTags.filter { it.name != n }
        tasks = tasks.map { it.copy(tags = it.tags.filter { t -> t != n }) }
        saveAll()
    }

    fun addTask(title: String, pri: Priority, dl: LocalDate?, tags: List<String>, content: String = "", planned: List<LocalDate> = emptyList(), deadlineTime: String? = null, estimatedMinutes: Int? = null) {
        tags.forEach { addTag(it) }
        val newTask = Task(title = title, content = content, priority = pri, tags = tags, deadline = dl, plannedDates = planned, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes)
        tasks = listOf(newTask) + tasks
        if (settings.autoSyncCalendar && (dl != null || planned.isNotEmpty())) {
            val syncDate = dl ?: planned.first()
            try {
                val eid = CalendarSyncHelper.createEvent(context, title, syncDate, settings.defaultReminderMinutes,
                    time = newTask.deadlineTime?.let { try { LocalTime.parse(it) } catch (_: Exception) { null } },
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
                        time = deadlineTime?.let { try { LocalTime.parse(it) } catch (_: Exception) { null } },
                        durationMinutes = tasks.find { it.id == id }?.estimatedMinutes ?: settings.defaultDurationMinutes)
                    tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes, plannedDates = planned, plannedTimes = plannedTimes, calendarEventId = eid) else it }
                    saveAll(); return
                } catch (_: Exception) {}
            }
        }
        tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes, plannedDates = planned, plannedTimes = plannedTimes) else it }
        saveAll()
    }

    fun restoreTask(id: String) {
        tasks = tasks.map { if (it.id == id) it.copy(isDeleted = false, deletedAt = null) else it }
        saveAll()
    }

    fun permanentDelete(id: String) {
        tasks = tasks.filter { it.id != id }
        saveAll()
    }
}
