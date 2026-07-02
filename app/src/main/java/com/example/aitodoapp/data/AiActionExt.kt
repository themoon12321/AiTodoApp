package com.example.aitodoapp.data

import com.example.aitodoapp.MatchCriteria

/**
 * AiAction → MatchCriteria 转换扩展函数。
 * 用于将 AI 返回的动作指令转为多字段匹配条件。
 */
internal fun AiAction.CompleteTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = deadline, deadlineTime = deadlineTime,
    tags = tags, content = content, plannedDate = plannedDate
)
internal fun AiAction.DeleteTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = deadline, deadlineTime = deadlineTime,
    tags = tags, content = content, plannedDate = plannedDate
)
internal fun AiAction.UpdateTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = matchDeadline, deadlineTime = matchDeadlineTime,
    tags = matchTags, content = matchContent, plannedDate = matchPlannedDate
)
internal fun AiAction.ArchiveTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = deadline, deadlineTime = deadlineTime,
    tags = tags, content = content, plannedDate = plannedDate
)
internal fun AiAction.UnarchiveTask.toMatchCriteria() = MatchCriteria(
    keyword = title, deadline = deadline, deadlineTime = deadlineTime,
    tags = tags, content = content, plannedDate = plannedDate
)
