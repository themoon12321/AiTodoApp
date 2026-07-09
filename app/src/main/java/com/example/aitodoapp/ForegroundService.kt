package com.example.aitodoapp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.aitodoapp.data.NotificationHelper

/**
 * 前台 Service，保持 App 进程存活，让 WorkManager 的日报定时任务在国产 ROM 下能准时触发。
 * 通知栏会有一条常驻"AI 代办正在运行"提示，不可滑动清除。
 *
 * 启停由设置页的「后台保活」开关控制（[com.example.aitodoapp.data.SettingsRepository.Settings.keepAliveEnabled]）。
 */
class ForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AI 代办")
            .setContentText("后台运行中，确保定时播报正常")
            .setOngoing(true)  // 不可滑动清除
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        // Android 14+（API 34）要求 startForeground 显式声明 foregroundServiceType，
        // 且必须与 AndroidManifest 中声明的类型一致（specialUse）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
        return START_STICKY  // 被系统杀掉后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户从最近任务划掉 App 时，系统可能顺带杀掉本服务；START_STICKY 会让系统尝试重建。
        super.onTaskRemoved(rootIntent)
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
    }
}
