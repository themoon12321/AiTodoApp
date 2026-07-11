package com.example.aitodoapp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.aitodoapp.data.NotificationContent
import com.example.aitodoapp.data.NotificationHelper

/**
 * 前台 Service，保持 App 进程存活，让 WorkManager 的日报定时任务在国产 ROM 下能准时触发。
 * 通知栏显示「下一个该做的事」+ 趣味副标题，让常驻通知有信息价值而非纯负担。
 *
 * 启停由设置页的「后台保活」开关控制（[com.example.aitodoapp.data.SettingsRepository.Settings.keepAliveEnabled]）。
 * 通知文案由 [NotificationContent] 决策，任务变化时调 [refresh] 刷新。
 */
class ForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service 可能被系统单独重建（App 进程被杀但 Service 由 START_STICKY 拉起），
        // 此时 Repository.dataDir 可能为 null，必须先 init
        com.example.aitodoapp.data.TaskRepository.init(applicationContext)
        com.example.aitodoapp.data.NotificationHelper.createChannel(applicationContext)
        val justCompleted = intent?.getBooleanExtra("just_completed", false) ?: false
        val content = NotificationContent.build(this, justCompleted)
        Log.d("ForegroundService", "onStartCommand: justCompleted=$justCompleted, title='${content.title}', subtext='${content.subtext}'")
        startForegroundWith(content)
        return START_STICKY  // 被系统杀掉后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundWith(content: NotificationContent.Content) {
        startForeground(NOTIF_ID, buildNotification(this, content))
    }

    companion object {
        private const val NOTIF_ID = 2

        /** 构建前台服务通知（供 startForeground 和 refresh 共用，保证一致性） */
        private fun buildNotification(context: Context, content: NotificationContent.Content): android.app.Notification {
            return NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(content.title)
                .setContentText(content.subtext)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()
        }

        /** 启动前台保活服务 */
        fun start(context: Context) {
            val intent = Intent(context, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            com.example.aitodoapp.data.AppLogger.foregroundServiceStarted()
        }

        /** 停止前台保活服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundService::class.java))
            com.example.aitodoapp.data.AppLogger.foregroundServiceStopped()
        }

        /**
         * 刷新通知文案。任务增删改完成时调用，让通知栏的「下一个任务」保持准确。
         *
         * 直接用 NotificationManager.notify 更新已存在的通知，不重新走 startForeground——
         * 因为 startForeground 在服务已前台运行时，部分 Android 版本/国产 ROM 不会刷新通知 UI。
         * notify 带 same ID 是更新通知的标准做法，行为可靠。
         * 服务未运行时静默跳过（开关关着时不打扰）。
         */
        fun refresh(context: Context, justCompleted: Boolean = false) {
            try {
                com.example.aitodoapp.data.TaskRepository.init(context)
                val content = NotificationContent.build(context, justCompleted)
                Log.d("ForegroundService", "refresh: justCompleted=$justCompleted, title='${content.title}', subtext='${content.subtext}'")
                NotificationManagerCompat.from(context).notify(NOTIF_ID, buildNotification(context, content))
            } catch (e: Exception) {
                Log.e("ForegroundService", "refresh failed", e)
            }
        }
    }
}
