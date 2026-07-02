package com.example.aitodoapp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.aitodoapp.data.NotificationHelper

/**
 * 前台 Service，保持 App 进程存活。
 * 通知栏会有一条"AI 代办正在运行"的提示，防止被 OPPO 杀后台。
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

        startForeground(2, notification)
        return START_STICKY  // 被系统杀掉后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
