package com.example.aitodoapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.aitodoapp.data.ActionLog
import com.example.aitodoapp.data.ActionLogRepository
import com.example.aitodoapp.data.CalendarSyncHelper
import com.example.aitodoapp.data.LogType
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

    val activeTasks get() = sortTasks(when (selectedDay) {
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
    })

    val archivedTasks get() = tasks.filter { it.isArchived && !it.isDeleted }
    val deletedTasks get() = tasks.filter { it.isDeleted }.sortedByDescending { it.deletedAt ?: it.createdAt }

    // ===== 排序方法 =====

    private fun sortTasks(list: List<Task>): List<Task> {
        return when (settings.taskSortOrder) {
            "deadline" -> list.sortedBy { it.deadline?.toEpochDay() ?: Long.MAX_VALUE }
            "priority" -> list.sortedBy { it.priority.ordinal }
            "created" -> list.sortedByDescending { it.createdAt.toEpochDay() }
            "manual" -> {
                val ids = tasks.map { it.id }.withIndex().associate { (i, id) -> id to i }
                list.filter { !it.isCompleted }.sortedBy { ids[it.id] ?: 0 } +
                list.filter { it.isCompleted }.sortedBy { ids[it.id] ?: 0 }
            }
            else -> list
        }
    }

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

    /** 任务有变化时刷新前台服务通知（保活开着才有用，没开则静默跳过） */
    private fun notifyRefresh(justCompleted: Boolean = false) {
        try { ForegroundService.refresh(context, justCompleted) } catch (_: Exception) {}
    }

    /** 记录操作日志。source: AI/MANUAL/SYSTEM */
    private fun log(type: String, source: String, summary: String, detail: String = "") {
        try { ActionLogRepository.add(ActionLog(type = type, source = source, summary = summary, detail = detail)) } catch (_: Exception) {}
    }

    // ===== 初始化（需在 LaunchedEffect 中调用一次） =====
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        initialized = true
        var changed = false
        if (tasks.isEmpty()) {
            // 首次启动无默认任务
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
            // 首次启动无默认标签
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
                                durationMinutes = if (task.deadline != null) 0 else (task.estimatedMinutes ?: settings.defaultDurationMinutes))
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
        // 记录启动初始化日志
        val initDetail = buildString {
            append("任务${tasks.size}个，活跃${allActive.size}个，标签${allTags.size}个")
            if (changed) append("；已自动归档/调整优先级")
            if (hasOldDeleted) append("；已清理过期回收站")
        }
        log(LogType.SYSTEM, "SYSTEM", "App启动初始化", initDetail)
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
        if (wasCompleted && settings.autoSyncCalendar) {
            val updated = tasks.find { it.id == id }
            if (updated != null && (updated.deadline != null || updated.plannedDates.isNotEmpty())) {
                val sd = updated.deadline ?: updated.plannedDates.first()
                try {
                    val eid = CalendarSyncHelper.createEvent(context, updated.title, sd, settings.defaultReminderMinutes,
                        time = updated.deadlineTime?.let { try { LocalTime.parse(it) } catch (_: Exception) { null } },
                        durationMinutes = if (updated.deadline != null) 0 else (updated.estimatedMinutes ?: settings.defaultDurationMinutes))
                    if (eid != null) tasks = tasks.map { if (it.id == id) it.copy(calendarEventId = eid) else it }
                } catch (_: Exception) {}
            }
        }
        saveAll()
        notifyRefresh(justCompleted = !wasCompleted)
        log(if (!wasCompleted) LogType.COMPLETE else LogType.UPDATE, "MANUAL",
            if (!wasCompleted) "完成任务：${t?.title ?: id}" else "取消完成：${t?.title ?: id}")
    }

    fun deleteTask(id: String) {
        val t = tasks.find { it.id == id }
        if (t?.calendarEventId != null) CalendarSyncHelper.deleteEvent(context, t.calendarEventId!!)
        tasks = tasks.map { if (it.id == id) it.copy(isDeleted = true, deletedAt = today) else it }
        saveAll()
        notifyRefresh()
        log(LogType.DELETE, "MANUAL", "删除任务：${t?.title ?: id}")
    }

    fun archiveTask(id: String, completedDate: LocalDate? = null) {
        val t = tasks.find { it.id == id }
        tasks = tasks.map { if (it.id == id) it.copy(isArchived = true, isCompleted = true, completedAt = completedDate ?: today) else it }
        saveAll()
        notifyRefresh()
        log(LogType.ARCHIVE, "MANUAL", "归档任务：${t?.title ?: id}")
    }

    fun unarchiveTask(id: String) {
        val t = tasks.find { it.id == id }
        tasks = tasks.map { if (it.id == id) it.copy(isArchived = false, isCompleted = false) else it }
        saveAll()
        notifyRefresh()
        log(LogType.UNARCHIVE, "MANUAL", "取消归档：${t?.title ?: id}")
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
        if (t.isNotBlank() && allTags.none { it.name == t }) { allTags = allTags + Tag(t, false); saveAll(); log(LogType.TAG, "MANUAL", "创建标签：$t") }
    }

    fun promoteTag(n: String) {
        allTags = allTags.map { if (it.name == n) it.copy(isTemporary = false) else it }
        saveAll()
        log(LogType.TAG, "MANUAL", "标签转正：$n")
    }

    fun deleteTag(n: String) {
        allTags = allTags.filter { it.name != n }
        tasks = tasks.map { it.copy(tags = it.tags.filter { t -> t != n }) }
        saveAll()
        log(LogType.TAG, "MANUAL", "删除标签：$n")
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
                    durationMinutes = if (dl != null) 0 else (newTask.estimatedMinutes ?: settings.defaultDurationMinutes))
                if (eid != null) tasks = tasks.map { if (it.id == newTask.id) it.copy(calendarEventId = eid) else it }
            } catch (_: Exception) {}
        }
        saveAll()
        notifyRefresh()
        val dlInfo = if (dl != null) " 截止${dl}" + (deadlineTime?.let { " $it" } ?: "") else ""
        val plInfo = if (planned.isNotEmpty()) " 计划${planned.size}天" else ""
        log(LogType.CREATE, "MANUAL", "创建任务：$title | ${pri.emoji}${pri.label}$dlInfo$plInfo")
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
                        durationMinutes = 0)
                    tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes, plannedDates = planned, plannedTimes = plannedTimes, calendarEventId = eid) else it }
                    saveAll(); notifyRefresh(); log(LogType.UPDATE, "MANUAL", "修改任务：$title"); return
                } catch (_: Exception) {}
            }
        }
        tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes, plannedDates = planned, plannedTimes = plannedTimes) else it }
        saveAll()
        notifyRefresh()
        log(LogType.UPDATE, "MANUAL", "修改任务：$title")
    }

    fun restoreTask(id: String) {
        val t = tasks.find { it.id == id }
        tasks = tasks.map { if (it.id == id) it.copy(isDeleted = false, deletedAt = null) else it }
        saveAll()
        notifyRefresh()
        log(LogType.RESTORE, "MANUAL", "恢复任务：${t?.title ?: id}")
    }

    fun permanentDelete(id: String) {
        val t = tasks.find { it.id == id }
        tasks = tasks.filter { it.id != id }
        saveAll()
        notifyRefresh()
        log(LogType.DELETE, "MANUAL", "永久删除：${t?.title ?: id}")
    }
}
