package com.example.aitodoapp.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.aitodoapp.MainActivity
import com.example.aitodoapp.model.ReportEntry
import com.example.aitodoapp.Task
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * WorkManager 后台任务：生成播报。
 * 即使 App 被系统杀后台也能执行（OPPO 等也适用）。
 */
class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isMorning = inputData.getBoolean("is_morning", true)
        val retentionDays = inputData.getInt("retention_days", 30)

        return try {
            // 加载任务数据
            TaskRepository.init(applicationContext)
            SettingsRepository.init(applicationContext)
            TokenRepository.init(applicationContext)
            ReportRepository.init(applicationContext)
            NotificationHelper.createChannel(applicationContext)
            val today = LocalDate.now()

            val tasks = TaskRepository.load<Task>("tasks.json")
            val allTags = TaskRepository.load<com.example.aitodoapp.Tag>("tags.json")

            // 构建任务描述（与 TaskScreen 保持一致）
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

            // 生成播报
            val result = AiService.generateDailyReport(descs, tagNames, isMorning)
            if (result.text.startsWith("网络请求失败") || result.text.startsWith("请先")) {
                return Result.retry()
            }

            // 保存
            val entry = ReportEntry(result.text, isMorning, today.toString())
            ReportRepository.addReport(entry, retentionDays)

            // 发通知（可点击跳转）
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
        /** 调度一个延迟播报任务 */
        fun schedule(context: Context, isMorning: Boolean, delayMinutes: Long = 1) {
            val request = OneTimeWorkRequestBuilder<ReportWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(workDataOf("is_morning" to isMorning))
                .addTag("report_generation")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
