package com.example.aitodoapp.data

import android.content.Context
import com.example.aitodoapp.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 前台服务通知文案决策。把「显示什么」的逻辑集中在这里，Service 只负责渲染。
 *
 * 决策树（按优先级从高到低）：
 * 1. 深夜（22:00-7:00）→ 静态"AI 代办运行中"，不显示任务（避免半夜制造焦虑）
 * 2. 有过期任务 → 主标题最急的过期任务，副标题 [GagPool.overdue]
 * 3. 有截止 ≤1h 的任务 → 主标题该任务，副标题 [GagPool.urgent]
 * 4. 有任务（不急）→ 主标题下一个该做的，副标题 [GagPool.idle] 或 [GagPool.encourage]（刚完成时）
 * 5. 无任务 → 主标题"全搞定啦"，副标题 [GagPool.celebrate]
 *
 * 「下一个该做的」排序：过期 > 今日截止 > 有截止 > 有计划日期 > 其他，同级按优先级。
 */
object NotificationContent {

    /** 深夜时段：22:00 ~ 次日 7:00，不显示任务提醒 */
    private const val QUIET_START = 22
    private const val QUIET_END = 7

    data class Content(val title: String, val subtext: String)

    fun build(context: Context, justCompleted: Boolean = false): Content {
        val now = LocalTime.now()
        // 1. 深夜静态
        if (isQuietHours(now)) {
            return Content("AI 代办", "后台运行中")
        }

        val tasks = TaskRepository.load<Task>("tasks.json")
        val today = LocalDate.now()
        val active = tasks.filter { !it.isArchived && !it.isDeleted }
        val pending = active.filter { !it.isCompleted }

        // 5. 全清空
        if (pending.isEmpty()) {
            return Content("今天全搞定啦！🎉", GagPool.pick(GagPool.celebrate))
        }

        // 选「下一个该做的」
        val next = pickNextTask(pending, today) ?: return Content("今天全搞定啦！🎉", GagPool.pick(GagPool.celebrate))

        // 2. 有过期任务
        val isOverdue = next.deadline != null && next.deadline < today
        if (isOverdue) {
            val days = ChronoUnit.DAYS.between(next.deadline, today)
            val title = "⚠️ ${next.title} · 过期${days}天"
            return Content(title, GagPool.pick(GagPool.overdue))
        }

        // 3. 今日截止且 ≤1h（有截止时间才算）
        val hoursLeft = hoursUntilDeadline(next, today, now)
        if (hoursLeft != null && hoursLeft <= 1.0) {
            val title = if (next.deadlineTime != null) "${next.title} · ${next.deadlineTime}截止"
                        else "${next.title} · 今天截止"
            return Content(title, GagPool.pick(GagPool.urgent))
        }

        // 4. 普通任务
        val timeInfo = when {
            next.deadline == today && next.deadlineTime != null -> " · ${next.deadlineTime}"
            next.deadline == today -> " · 今天截止"
            next.deadline != null -> " · ${next.deadline.monthValue}/${next.deadline.dayOfMonth}截止"
            next.plannedDates.isNotEmpty() -> " · 今日计划"
            else -> ""
        }
        val title = "下一个：${next.title}${timeInfo}"
        val subtext = if (justCompleted) GagPool.pick(GagPool.encourage) else GagPool.pick(GagPool.idle)
        return Content(title, subtext)
    }

    /** 是否处于深夜静默时段 */
    private fun isQuietHours(now: LocalTime): Boolean {
        return now.hour >= QUIET_START || now.hour < QUIET_END
    }

    /**
     * 从待办里挑「下一个该做的」。优先级：
     * 过期 > 今日截止 > 有截止日期 > 有计划日期 > 其他；同级按 priority 序号（P0 最前）。
     */
    private fun pickNextTask(pending: List<Task>, today: LocalDate): Task? {
        if (pending.isEmpty()) return null
        return pending.sortedWith(
            compareBy<Task> { task ->
                when {
                    task.deadline != null && task.deadline < today -> 0   // 过期最急
                    task.deadline == today -> 1                          // 今日截止
                    task.deadline != null -> 2                           // 未来截止
                    task.plannedDates.isNotEmpty() -> 3                  // 有计划日期
                    else -> 4                                             // 无日期
                }
            }.thenBy { it.priority.ordinal }
        ).first()
    }

    /** 计算距截止还剩多少小时（需要 deadlineTime 才精确，否则返回 null） */
    private fun hoursUntilDeadline(task: Task, today: LocalDate, now: LocalTime): Double? {
        if (task.deadline != today || task.deadlineTime == null) return null
        return try {
            val target = LocalTime.parse(task.deadlineTime)
            ChronoUnit.MINUTES.between(now, target).toDouble() / 60.0
        } catch (_: Exception) { null }
    }
}
