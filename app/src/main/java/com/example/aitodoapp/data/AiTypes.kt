package com.example.aitodoapp.data

import com.example.aitodoapp.Priority

data class AiResult(val text: String, val actions: List<AiAction> = emptyList(), val promptTokens: Int = 0, val completionTokens: Int = 0, val error: String? = null)

sealed class AiAction {
    data class CreateTask(val title: String, val content: String = "", val priority: Priority, val deadline: java.time.LocalDate?, val tags: List<String>, val plannedDates: List<java.time.LocalDate> = emptyList(), val deadlineTime: String? = null, val estimatedMinutes: Int? = null) : AiAction()
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
    data class UpdateSettings(
        val apiUrl: String? = null, val apiKey: String? = null, val model: String? = null,
        val showOverdueInline: Boolean? = null, val longPressChat: Boolean? = null,
        val showTokenUsage: Boolean? = null, val autoSyncCalendar: Boolean? = null,
        val defaultReminderMinutes: Int? = null
    ) : AiAction()
    data class ManageTag(val action: String, val tagName: String) : AiAction()
    data class ArchiveTask(val title: String, val deadline: java.time.LocalDate? = null, val deadlineTime: String? = null, val tags: List<String>? = null, val content: String? = null, val plannedDate: java.time.LocalDate? = null, val completedAt: java.time.LocalDate? = null) : AiAction()
    data class UnarchiveTask(val title: String, val deadline: java.time.LocalDate? = null, val deadlineTime: String? = null, val tags: List<String>? = null, val content: String? = null, val plannedDate: java.time.LocalDate? = null) : AiAction()
}
