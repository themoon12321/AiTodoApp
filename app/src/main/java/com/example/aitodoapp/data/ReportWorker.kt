package com.example.aitodoapp.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aitodoapp.model.ReportEntry
import com.example.aitodoapp.Task
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isMorning = inputData.getBoolean("is_morning", true)
        val retentionDays = inputData.getInt("retention_days", 30)

        val wmResult = try {
            TaskRepository.init(applicationContext)
            SettingsRepository.init(applicationContext)
            TokenRepository.init(applicationContext)
            ReportRepository.init(applicationContext)
            ActionLogRepository.init(applicationContext)
            NotificationHelper.createChannel(applicationContext)
            val today = LocalDate.now()

            val allTasks = TaskRepository.load<Task>("tasks.json")
            val allTags = TaskRepository.load<com.example.aitodoapp.Tag>("tags.json")
            // 过滤已归档/已删除，避免播报中出现不应显示的任务
            val tasks = allTasks.filter { !it.isArchived && !it.isDeleted }

            fun taskLine(t: Task): String {
                val parts = mutableListOf<String>()
                parts.add(t.title)
                // 同时输出 emoji 和文本优先级名 + 序号，帮助 AI 准确识别颜色
                parts.add("${t.priority.emoji}${t.priority.label}(${t.priority.name})")
                if (t.deadline != null) parts.add("截止:${t.deadline.format(DateTimeFormatter.ofPattern("M/d"))}")
                if (t.tags.isNotEmpty()) parts.add("🏷${t.tags.joinToString(",")}")
                if (t.plannedDates.isNotEmpty()) {
                    val pdStr = t.plannedDates.sorted().joinToString(",") { it.format(DateTimeFormatter.ofPattern("M/d")) }
                    parts.add("计划:$pdStr")
                }
                if (t.isCompleted) parts.add("[已完成]")
                return parts.joinToString(" | ")
            }

            // 预分类（代码计算，不依赖 AI 判断）
            val dayOfWeek = today.dayOfWeek.value // Mon=1 .. Sun=7
            val monday = today.minusDays((dayOfWeek - 1).toLong())
            val sunday = monday.plusDays(6)
            val nextMonday = sunday.plusDays(1)
            val nextSunday = nextMonday.plusDays(6)

            val pending = tasks.filter { !it.isCompleted }
            val todayTasks = pending.filter { t ->
                t.deadline == today || t.plannedDates.contains(today)
            }
            val thisWeekTasks = pending.filter { t ->
                t.deadline != null
                    && !t.deadline.isBefore(monday)
                    && !t.deadline.isAfter(sunday)
                    && t.deadline != today
            }
            val overdueTasks = pending.filter { t ->
                t.deadline != null && t.deadline < today
            }
            // 周四及以后显示下周预览
            val showNextWeek = dayOfWeek >= 4
            val nextWeekTasks = if (showNextWeek) {
                pending.filter { t ->
                    t.deadline != null
                        && !t.deadline.isBefore(nextMonday)
                        && !t.deadline.isAfter(nextSunday)
                }
            } else emptyList()

            val sectionLines = mutableListOf<String>()
            sectionLines.add("📋 今日任务")
            if (todayTasks.isEmpty()) sectionLines.add("（暂无今日任务安排）")
            else todayTasks.forEach { sectionLines.add("- ${taskLine(it)}") }

            sectionLines.add("")
            sectionLines.add("📋 本周剩余任务概览（明天～${sunday.monthValue}/${sunday.dayOfMonth}）")
            if (thisWeekTasks.isEmpty()) sectionLines.add("（暂无本周剩余任务）")
            else thisWeekTasks.forEach { sectionLines.add("- ${taskLine(it)}") }

            if (overdueTasks.isNotEmpty()) {
                sectionLines.add("")
                sectionLines.add("⚠️ 过期任务")
                overdueTasks.forEach { t ->
                    val days = java.time.temporal.ChronoUnit.DAYS.between(t.deadline, today)
                    sectionLines.add("- ${taskLine(t)} · 已过期${days}天")
                }
            }

            if (nextWeekTasks.isNotEmpty()) {
                sectionLines.add("")
                sectionLines.add("📅 下周任务预览（${nextMonday.monthValue}/${nextMonday.dayOfMonth}～${nextSunday.monthValue}/${nextSunday.dayOfMonth}）")
                nextWeekTasks.forEach { sectionLines.add("- ${taskLine(it)}") }
            }

            val tagNames = allTags.map { it.name }
            val genResult = AiService.generateDailyReport(sectionLines, tagNames, isMorning)
            if (genResult.error != null) {
                // 配置类错误（Key错/欠费/地址错）重试也没用，直接结束；
                // 瞬时错误（网络/超时/限流/5xx）交给 WorkManager 重试。
                val noRetry = genResult.error.startsWith("API Key") || genResult.error.contains("401") ||
                    genResult.error.contains("402") || genResult.error.contains("404") ||
                    genResult.error.contains("422")
                if (noRetry) Result.success() else Result.retry()
            } else {
                val entry = ReportEntry(genResult.text, isMorning, today.toString())
                ReportRepository.addReport(entry, retentionDays)
                ActionLogRepository.add(ActionLog(type = LogType.REPORT, source = "SYSTEM",
                    summary = "${if (isMorning) "早间" else "晚间"}播报已生成", detail = genResult.text.take(200)))

                val pi = NotificationHelper.reportPendingIntent(applicationContext)
                NotificationHelper.showWithIntent(applicationContext,
                    if (isMorning) "🌅 早间播报已送达" else "🌙 晚间播报已送达",
                    "点击查看今日播报详情", pi)

                Result.success()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }

        // 链式调度下一次播报（只有执行成功才排）
        if (wmResult == Result.success()) {
            scheduleNext(applicationContext, isMorning)
        }

        return wmResult
    }

    companion object {
        /** 一次性延迟测试（保留原有行为） */
        fun schedule(context: Context, isMorning: Boolean, delayMinutes: Long = 1) {
            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf("is_morning" to isMorning))
                .addTag("report_test")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * 链式调度下一次播报。用 OneTimeWorkRequest 取代旧版 PeriodicWorkRequest：
         * - 执行完自动排下一次，不会残留
         * - 任何时候系统中最多 1 个 OneTimeWorkRequest 在排队
         * - 用户改时间/关播报后旧的自动被 REPLACE 取消
         */
        fun scheduleNext(context: Context, isMorning: Boolean) {
            val settings = SettingsRepository.load()
            if (!settings.reportEnabled) return

            val (hour, minute) = if (isMorning) {
                val h = settings.morningReportTime.substringBefore(":").toIntOrNull() ?: 7
                val m = settings.morningReportTime.substringAfter(":").toIntOrNull() ?: 0
                h to m
            } else {
                val h = settings.eveningReportTime.substringBefore(":").toIntOrNull() ?: 21
                val m = settings.eveningReportTime.substringAfter(":").toIntOrNull() ?: 0
                h to m
            }

            val delayMs = msUntilNext(hour, minute)
            val tag = if (isMorning) "report_morning" else "report_evening"

            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("is_morning" to isMorning))
                .addTag(tag)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                tag, ExistingWorkPolicy.REPLACE, request
            )
        }

        /** 取消指定时段的播报链 */
        fun cancel(context: Context, isMorning: Boolean) {
            val tag = if (isMorning) "report_morning" else "report_evening"
            WorkManager.getInstance(context).cancelUniqueWork(tag)
        }

        /** 取消所有播报 */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("report_morning")
            WorkManager.getInstance(context).cancelUniqueWork("report_evening")
        }

        /** 清理旧版 PeriodicWorkRequest 残留（唯一名 daily_morning / daily_evening），返回实际清理数 */
        fun cleanupLegacy(context: Context): Int {
            val wm = WorkManager.getInstance(context)
            var count = 0
            for (name in listOf("daily_morning", "daily_evening")) {
                val infos = try { wm.getWorkInfosForUniqueWork(name).get() } catch (_: Exception) { emptyList() }
                if (infos.isNotEmpty()) count++
                wm.cancelUniqueWork(name)
            }
            return count
        }

        /** 计算从现在到下一个目标时刻（hh:mm）的毫秒数。若今天已过该时刻，则算到明天。 */
        private fun msUntilNext(hour: Int, minute: Int): Long {
            val now = LocalDateTime.now()
            val targetToday = now.with(LocalTime.of(hour, minute))
            val target = if (targetToday.isAfter(now)) targetToday else targetToday.plusDays(1)
            return Duration.between(now, target).toMillis()
        }
    }
}
