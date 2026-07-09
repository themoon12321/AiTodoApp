package com.example.aitodoapp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
        // 启动时按当前任务状态生成通知；refresh 时带 just_completed 标志
        val justCompleted = intent?.getBooleanExtra("just_completed", false) ?: false
        val content = NotificationContent.build(this, justCompleted)
        startForegroundWith(content)
        return START_STICKY  // 被系统杀掉后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundWith(content: NotificationContent.Content) {
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(content.title)
            .setContentText(content.subtext)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val NOTIF_ID = 2

        /** 启动前台保活服务 */
        fun start(context: Context) {
            val intent = Intent(context, ForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止前台保活服务 */
        fun stop(context: Context) {
            context.stopService(Intent(context, ForegroundService::class.java))
        }

        /**
         * 刷新通知文案。任务增删改完成时调用，让通知栏的「下一个任务」保持准确。
         * 服务未运行时静默跳过（开关关着时不打扰）。
         */
        fun refresh(context: Context, justCompleted: Boolean = false) {
            val intent = Intent(context, ForegroundService::class.java)
            intent.putExtra("just_completed", justCompleted)
            // 服务已在运行：重新走 onStartCommand 刷新通知；不在运行则忽略（不主动拉起保活）
            try { context.startService(intent) } catch (_: Exception) {}
        }
    }
}
