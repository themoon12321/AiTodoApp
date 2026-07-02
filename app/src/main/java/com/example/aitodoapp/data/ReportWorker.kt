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
import java.util.concurrent.TimeUnit

class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isMorning = inputData.getBoolean("is_morning", true)
        val retentionDays = inputData.getInt("retention_days", 30)

        return try {
            TaskRepository.init(applicationContext)
            SettingsRepository.init(applicationContext)
            TokenRepository.init(applicationContext)
            ReportRepository.init(applicationContext)
            NotificationHelper.createChannel(applicationContext)
            val today = LocalDate.now()

            val tasks = TaskRepository.load<Task>("tasks.json")
            val allTags = TaskRepository.load<com.example.aitodoapp.Tag>("tags.json")
            val descs = tasks.map { t ->
                val p = when {
                    t.isCompleted -> "[已完成] "
                    t.deadline != null && t.deadline < today && !t.isCompleted -> "[过期] "
                    else -> ""
                }
                p + "${t.title} | ${t.priority.emoji}${t.priority.label}" +
                    if (t.deadline != null) " | 截止:${t.deadline}" else "" +
                    if (t.tags.isNotEmpty()) " | 🏷${t.tags.joinToString(",")}" else ""
            }
            val tagNames = allTags.map { it.name }

            val result = AiService.generateDailyReport(descs, tagNames, isMorning)
            if (result.text.startsWith("网络请求失败") || result.text.startsWith("请先")) {
                return Result.retry()
            }

            val entry = ReportEntry(result.text, isMorning, today.toString())
            ReportRepository.addReport(entry, retentionDays)

            val pi = NotificationHelper.reportPendingIntent(applicationContext)
            NotificationHelper.showWithIntent(applicationContext,
                if (isMorning) "🌅 早间播报已送达" else "🌙 晚间播报已送达",
                "点击查看今日播报详情", pi)

            // 执行成功后，调度明天的同一时间
            val settings = SettingsRepository.load()
            val timeStr = if (isMorning) settings.morningReportTime else settings.eveningReportTime
            val hour = timeStr.substringBefore(":").toIntOrNull() ?: if (isMorning) 7 else 21
            val minute = timeStr.substringAfter(":").toIntOrNull() ?: 0
            scheduleNext(applicationContext, isMorning, hour, minute)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        /** 一次性延迟测试 */
        fun schedule(context: Context, isMorning: Boolean, delayMinutes: Long = 1) {
            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf("is_morning" to isMorning))
                .addTag("report_test")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /** 调度每日定时播报（计算到下次时间的毫秒数） */
        fun scheduleDaily(context: Context, isMorning: Boolean, hour: Int, minute: Int) {
            val now = LocalDateTime.now()
            val targetToday = now.with(LocalTime.of(hour, minute))
            val target = if (targetToday.isAfter(now)) targetToday else targetToday.plusDays(1)
            val delayMs = Duration.between(now, target).toMillis()

            val tag = if (isMorning) "daily_morning" else "daily_evening"
            // 取消旧任务，用新任务替换
            WorkManager.getInstance(context).cancelUniqueWork(tag)

            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("is_morning" to isMorning))
                .addTag(tag)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
        }

        /** 执行完成后调度明天同一时间 */
        private fun scheduleNext(context: Context, isMorning: Boolean, hour: Int, minute: Int) {
            val tomorrow = LocalDateTime.now().plusDays(1).with(LocalTime.of(hour, minute))
            val delayMs = Duration.between(LocalDateTime.now(), tomorrow).toMillis()

            val tag = if (isMorning) "daily_morning" else "daily_evening"
            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("is_morning" to isMorning))
                .addTag(tag)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(tag, ExistingWorkPolicy.REPLACE, request)
        }

        /** 取消所有定时播报 */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("daily_morning")
            WorkManager.getInstance(context).cancelUniqueWork("daily_evening")
        }
    }
}
