package com.example.aitodoapp.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
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
                val parts = mutableListOf<String>()
                val p = when {
                    t.isCompleted -> "[已完成]"
                    t.deadline != null && t.deadline < today && !t.isCompleted -> "[过期]"
                    else -> null
                }
                if (p != null) parts.add(p)
                parts.add(t.title)
                parts.add("${t.priority.emoji}${t.priority.label}")
                if (t.deadline != null) parts.add("截止:${t.deadline}")
                if (t.tags.isNotEmpty()) parts.add("🏷${t.tags.joinToString(",")}")
                parts.joinToString(" | ")
            }
            val tagNames = allTags.map { it.name }

            val result = AiService.generateDailyReport(descs, tagNames, isMorning)
            if (result.error != null) {
                // 配置类错误（Key错/欠费/地址错）重试也没用，直接成功结束周期；
                // 瞬时错误（网络/超时/限流/5xx）交给 WorkManager 重试。
                val noRetry = result.error.startsWith("API Key") || result.error.contains("401") ||
                    result.error.contains("402") || result.error.contains("404") ||
                    result.error.contains("422")
                return if (noRetry) Result.success() else Result.retry()
            }

            val entry = ReportEntry(result.text, isMorning, today.toString())
            ReportRepository.addReport(entry, retentionDays)

            val pi = NotificationHelper.reportPendingIntent(applicationContext)
            NotificationHelper.showWithIntent(applicationContext,
                if (isMorning) "🌅 早间播报已送达" else "🌙 晚间播报已送达",
                "点击查看今日播报详情", pi)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_INTERVAL_MIN = 24 * 60L  // 24 小时周期

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
         * 调度每日定时播报。改用 PeriodicWorkRequest：
         * 系统自动维护周期调度，不再依赖"执行成功后才排下一次"的链式续约，
         * 即使某次因进程被杀/网络失败没执行，下次周期仍会由系统重新拉起，杜绝永久断链。
         *
         * 取舍：PeriodicWork 最小周期 15 分钟，执行时刻会因 Doze/省电策略漂移几分钟到几十分钟，
         * 对"每日早晚一次"的场景可接受。初始延迟算到最近一个目标时刻。
         */
        fun scheduleDaily(context: Context, isMorning: Boolean, hour: Int, minute: Int) {
            val delayMs = msUntilNext(hour, minute)
            val tag = if (isMorning) "daily_morning" else "daily_evening"

            val request = PeriodicWorkRequestBuilder<ReportWorker>(PERIODIC_INTERVAL_MIN, TimeUnit.MINUTES)
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("is_morning" to isMorning))
                .addTag(tag)
                .build()
            // KEEP：若已存在同 tag 的周期任务，保留原调度，避免每次保存设置都重置周期时钟。
            // 改用 REPLACE 仅在用户明确改时间时才需要，由调用方通过 updateSchedule 触发。
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(tag, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** 取消指定时段的定时播报 */
        fun cancel(context: Context, isMorning: Boolean) {
            val tag = if (isMorning) "daily_morning" else "daily_evening"
            WorkManager.getInstance(context).cancelUniqueWork(tag)
        }

        /** 取消所有定时播报 */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("daily_morning")
            WorkManager.getInstance(context).cancelUniqueWork("daily_evening")
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
