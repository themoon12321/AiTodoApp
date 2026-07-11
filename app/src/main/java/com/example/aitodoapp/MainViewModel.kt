package com.example.aitodoapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.aitodoapp.data.AppLogger
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
    var tasks by mutableStateOf(emptyList<Task>())
        private set
    var archivedTasks by mutableStateOf(emptyList<Task>())
        private set
    var deletedTasks by mutableStateOf(emptyList<Task>())
        private set
    var allTags by mutableStateOf(TaskRepository.load<Tag>("tags.json"))
        private set
    var settings by mutableStateOf(SettingsRepository.load())
        private set
    var selectedDay by mutableStateOf(DayFilter.TODAY)
    var tab by mutableStateOf(0)

    val today: LocalDate get() = LocalDate.now()

    // ===== 派生属性 =====
    val allActive get() = tasks

    val overdueTasks get() = tasks.filter {
        !it.isCompleted && (
            (it.deadline != null && it.deadline < today) ||
            (it.plannedDates.isNotEmpty() && it.plannedDates.all { d -> d < today })
        )
    }

    val activeTasks get() = sortTasks(when (selectedDay) {
        DayFilter.OVERDUE -> tasks.filter { it.deadline != null && it.deadline < today && !it.isCompleted }
        DayFilter.ALL -> tasks
        DayFilter.TODAY -> tasks.filter {
            val todayRelevant = it.plannedDates.any { d -> d == today } || it.deadline == today ||
                (it.plannedDates.isEmpty() && it.createdAt <= today && (it.deadline == null || it.deadline >= today))
            todayRelevant || (it.isCompleted && it.completedAt == today)
        }
        else -> {
            val d = selectedDay.date(today) ?: today
            tasks.filter {
                it.plannedDates.any { p -> p == d } ||
                (it.plannedDates.isEmpty() && it.createdAt <= d && (it.deadline == null || it.deadline >= d))
            }
        }
    })

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

    // ===== 批处理（批量操作时合并多次 saveAll 为一次） =====

    private var batchDepth = 0
    private var batchDirty = false

    /** 进入批处理模式：后续 saveAll() 调用被合并，直到 endBatch() 才真正写入 */
    fun beginBatch() {
        batchDepth++
    }

    /** 退出批处理模式：真正写入一次文件（如有改动） */
    fun endBatch() {
        batchDepth--
        if (batchDepth < 0) batchDepth = 0     // 防重复调用导致负值
        if (batchDepth == 0 && batchDirty) {
            saveAll()
            batchDirty = false
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
        if (batchDepth > 0) { batchDirty = true; return }   // 批次中 → 延迟写入
        TaskRepository.save("tasks.json", tasks)
        TaskRepository.save("tags.json", allTags)
        TaskRepository.save("archived_tasks.json", archivedTasks)
        TaskRepository.save("deleted_tasks.json", deletedTasks)
    }

    /** 任务有变化时刷新前台服务通知（保活开着才有用，没开则静默跳过） */
    private fun notifyRefresh(justCompleted: Boolean = false) {
        try { ForegroundService.refresh(context, justCompleted) } catch (_: Exception) {}
    }

    /** 记录操作日志。source: AI/MANUAL/SYSTEM，traceId 用于关联同一次 AI 对话的完整链路 */
    private fun log(type: String, source: String, summary: String, detail: String = "", traceId: String = "") {
        AppLogger.add(type, source, summary, detail, traceId)
    }

    // ===== 初始化（需在 LaunchedEffect 中调用一次） =====
    private var initialized = false

    /** 迁移旧版单文件数据到新版三文件结构 */
    private fun migrateIfNeeded(): Boolean {
        if (TaskRepository.fileExists("archived_tasks.json")) return false
        val oldAll = TaskRepository.load<Task>("tasks.json")
        if (oldAll.isEmpty()) {
            // 空数据→直接写个空 archived+deleted 文件，标记迁移完成
            TaskRepository.save("archived_tasks.json", emptyList<Task>())
            TaskRepository.save("deleted_tasks.json", emptyList<Task>())
            tasks = emptyList()
            archivedTasks = emptyList()
            deletedTasks = emptyList()
            return true
        }
        tasks = oldAll.filter { !it.isArchived && !it.isDeleted }
        archivedTasks = oldAll.filter { it.isArchived && !it.isDeleted }
        deletedTasks = oldAll.filter { it.isDeleted }
        saveAll()
        return true
    }

    suspend fun initialize() {
        if (initialized) return
        initialized = true
        var changed = false
        val migrated = migrateIfNeeded()
        if (!migrated) {
            // 已迁移过 → 正常从各自文件加载
            tasks = TaskRepository.load<Task>("tasks.json")
            archivedTasks = TaskRepository.load<Task>("archived_tasks.json")
            deletedTasks = TaskRepository.load<Task>("deleted_tasks.json")
        } else if (tasks.isEmpty() && archivedTasks.isEmpty() && deletedTasks.isEmpty()) {
            // 空迁移完成（无数据）
        } else {
            changed = true
        }

        if (checkAutoArchive()) changed = true

        if (changed) saveAll()

        // 日历同步
        if (settings.autoSyncCalendar) {
            val toSync = tasks.filter { !it.isCompleted && it.calendarEventId == null && (it.deadline != null || it.plannedDates.isNotEmpty()) }
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
        val hasOldDeleted = deletedTasks.any { it.deletedAt != null && it.deletedAt!! < cutoff }
        if (hasOldDeleted) {
            deletedTasks = deletedTasks.filter { !(it.deletedAt != null && it.deletedAt!! < cutoff) }
            saveAll()
        }

        // 记录启动初始化日志
        val initDetail = buildString {
            append("任务${tasks.size}个，归档${archivedTasks.size}个，标签${allTags.size}个")
            if (migrated) append("；已迁移到新文件结构")
            if (hasOldDeleted) append("；已清理过期回收站")
        }
        log(LogType.SYSTEM, "SYSTEM", "App启动初始化", initDetail)
    }

    /** 检查自动归档 + 自动调整优先级。返回 true 表示有数据变更。可供定时器调用 */
    fun checkAutoArchive(): Boolean {
        var changed = false
        // 自动归档：昨天及之前完成的任务 → 移入 archivedTasks
        val toArchive = tasks.filter { it.completedAt != null && it.completedAt < today }
        if (toArchive.isNotEmpty()) {
            tasks = tasks.filter { it.id !in toArchive.map { it.id } }
            archivedTasks = toArchive.map { it.copy(isArchived = true) } + archivedTasks
            changed = true
        }
        // 自动调整未归档任务的优先级
        tasks = tasks.map { t ->
            if (t.isCompleted) t
            else {
                val ap = autoPriority(t)
                if (ap != t.priority && !t.priorityLocked) { changed = true; t.copy(priority = ap) }
                else t
            }
        }
        if (changed) saveAll()
        return changed
    }

    // ===== 操作 =====

    fun refreshSettings() {
        settings = SettingsRepository.load()
    }

    fun completeTask(id: String, traceId: String = "") {
        val t = tasks.find { it.id == id }
        val wasCompleted = t?.isCompleted == true
        if (t != null && !t.isCompleted && t.calendarEventId != null) {
            CalendarSyncHelper.deleteEvent(context, t.calendarEventId!!, t.title)
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
        val src = if (traceId.isNotBlank()) "AI" else "MANUAL"
        log(if (!wasCompleted) LogType.COMPLETE else LogType.UPDATE, src,
            if (!wasCompleted) "完成任务：${t?.title ?: id}" else "取消完成：${t?.title ?: id}", traceId = traceId)
    }

    fun deleteTask(id: String, traceId: String = "") {
        val t = tasks.find { it.id == id } ?: archivedTasks.find { it.id == id } ?: return
        if (t.calendarEventId != null) CalendarSyncHelper.deleteEvent(context, t.calendarEventId!!, t.title)
        val deleted = t.copy(isDeleted = true, deletedAt = today)
        tasks = tasks.filter { it.id != id }
        archivedTasks = archivedTasks.filter { it.id != id }
        deletedTasks = listOf(deleted) + deletedTasks
        saveAll()
        notifyRefresh()
        log(LogType.DELETE, if (traceId.isNotBlank()) "AI" else "MANUAL", "删除任务：${t.title}", traceId = traceId)
    }

    fun archiveTask(id: String, completedDate: LocalDate? = null, traceId: String = "") {
        val t = tasks.find { it.id == id } ?: return
        val archived = t.copy(isArchived = true, isCompleted = true, completedAt = completedDate ?: today)
        tasks = tasks.filter { it.id != id }
        archivedTasks = listOf(archived) + archivedTasks
        saveAll()
        notifyRefresh()
        log(LogType.ARCHIVE, if (traceId.isNotBlank()) "AI" else "MANUAL", "归档任务：${t.title}", traceId = traceId)
    }

    fun unarchiveTask(id: String, traceId: String = "") {
        val t = archivedTasks.find { it.id == id } ?: return
        val restored = t.copy(isArchived = false, isCompleted = false)
        archivedTasks = archivedTasks.filter { it.id != id }
        tasks = listOf(restored) + tasks
        saveAll()
        notifyRefresh()
        log(LogType.UNARCHIVE, if (traceId.isNotBlank()) "AI" else "MANUAL", "取消归档：${t.title}", traceId = traceId)
    }

    fun addTag(n: String): String {
        val t = n.trim()
        if (t.isBlank() || allTags.any { it.name == t }) return t
        allTags = allTags + Tag(t, true)
        saveAll()
        return t
    }

    fun createTag(n: String, traceId: String = "") {
        val t = n.trim()
        if (t.isNotBlank() && allTags.none { it.name == t }) { allTags = allTags + Tag(t, false); saveAll(); log(LogType.TAG, if (traceId.isNotBlank()) "AI" else "MANUAL", "创建标签：$t", traceId = traceId) }
    }

    fun promoteTag(n: String, traceId: String = "") {
        allTags = allTags.map { if (it.name == n) it.copy(isTemporary = false) else it }
        saveAll()
        log(LogType.TAG, if (traceId.isNotBlank()) "AI" else "MANUAL", "标签转正：$n", traceId = traceId)
    }

    fun deleteTag(n: String, traceId: String = "") {
        allTags = allTags.filter { it.name != n }
        tasks = tasks.map { it.copy(tags = it.tags.filter { t -> t != n }) }
        saveAll()
        log(LogType.TAG, if (traceId.isNotBlank()) "AI" else "MANUAL", "删除标签：$n", traceId = traceId)
    }

    fun addTask(title: String, pri: Priority, dl: LocalDate?, tags: List<String>, content: String = "", planned: List<LocalDate> = emptyList(), deadlineTime: String? = null, estimatedMinutes: Int? = null, traceId: String = "") {
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
        val src = if (traceId.isNotBlank()) "AI" else "MANUAL"
        log(LogType.CREATE, src, "创建任务：$title | ${pri.emoji}${pri.label}$dlInfo$plInfo", traceId = traceId)
    }

    fun updateTask(id: String, title: String, content: String, pri: Priority, dl: LocalDate?, tags: List<String>, planned: List<LocalDate> = emptyList(), lockPriority: Boolean = false, deadlineTime: String? = null, plannedTimes: List<String> = emptyList(), estimatedMinutes: Int? = null, traceId: String = "") {
        tags.forEach { addTag(it) }
        val old = tasks.find { it.id == id }
        if (old?.deadline != dl || old?.deadlineTime != deadlineTime) {
            if (old?.calendarEventId != null) CalendarSyncHelper.deleteEvent(context, old.calendarEventId!!, old.title)
            if (settings.autoSyncCalendar && dl != null) {
                try {
                    val eid = CalendarSyncHelper.createEvent(context, title, dl, settings.defaultReminderMinutes,
                        time = deadlineTime?.let { try { LocalTime.parse(it) } catch (_: Exception) { null } },
                        durationMinutes = 0)
                    tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes, plannedDates = planned, plannedTimes = plannedTimes, calendarEventId = eid) else it }
                    saveAll(); notifyRefresh(); log(LogType.UPDATE, if (traceId.isNotBlank()) "AI" else "MANUAL", "修改任务：$title", traceId = traceId); return
                } catch (_: Exception) {}
            }
        }
        tasks = tasks.map { if (it.id == id) it.copy(title = title, content = content, priority = pri, priorityLocked = lockPriority || it.priorityLocked, tags = tags, deadline = dl, deadlineTime = deadlineTime, estimatedMinutes = estimatedMinutes, plannedDates = planned, plannedTimes = plannedTimes) else it }
        saveAll()
        notifyRefresh()
        log(LogType.UPDATE, if (traceId.isNotBlank()) "AI" else "MANUAL", "修改任务：$title", traceId = traceId)
    }

    fun restoreTask(id: String, traceId: String = "") {
        val t = deletedTasks.find { it.id == id } ?: return
        val restored = t.copy(isDeleted = false, deletedAt = null)
        deletedTasks = deletedTasks.filter { it.id != id }
        tasks = listOf(restored) + tasks
        saveAll()
        notifyRefresh()
        log(LogType.RESTORE, if (traceId.isNotBlank()) "AI" else "MANUAL", "恢复任务：${t.title}", traceId = traceId)
    }

    fun permanentDelete(id: String, traceId: String = "", title: String = "") {
        val t = deletedTasks.find { it.id == id }
        deletedTasks = deletedTasks.filter { it.id != id }
        saveAll()
        notifyRefresh()
        val logTitle = title.ifBlank { t?.title ?: id }
        log(LogType.DELETE, if (traceId.isNotBlank()) "AI" else "MANUAL", "永久删除：$logTitle", traceId = traceId)
    }
}
