package com.example.aitodoapp.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.aitodoapp.ForegroundService
import java.util.concurrent.TimeUnit

/**
 * 定时刷新前台服务通知文案。15 分钟周期检查一次。
 *
 * 目的：捕获「截止倒计时」这类需要时间推移才触发的变化——
 * 任务增删改已经会即时刷新（[ForegroundService.refresh]），但「距离截止还有 1 小时」
 * 这种状态是随时间自然到达的，没有用户操作触发，需要定时检查。
 *
 * 取舍：15 分钟是 WorkManager 周期任务的最小间隔，意味着倒计时提醒可能偏差最多 15 分钟。
 * 对「差几分钟可接受」的场景够用。要精确到分钟级需用 AlarmManager，成本高、耗电多，不值得。
 *
 * 只在保活开启时调度（开关控制），关闭时取消，避免无谓的后台唤醒。
 */
class NotificationRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Worker 由系统在后台唤醒，可能是 App 进程被杀后重建，Repository.dataDir 可能为 null，
            // 必须重新 init，否则 TaskRepository.load 返回空列表 → 通知错误显示"全清空"
            TaskRepository.init(applicationContext)
            SettingsRepository.init(applicationContext)
            NotificationHelper.createChannel(applicationContext)
            ForegroundService.refresh(applicationContext, justCompleted = false)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "notif_refresh"
        private const val INTERVAL_MIN = 15L

        /** 启动 15 分钟周期的通知刷新（保活开启时调用） */
        fun start(context: Context) {
            val request = PeriodicWorkRequestBuilder<NotificationRefreshWorker>(INTERVAL_MIN, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** 停止周期刷新（保活关闭时调用） */
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
