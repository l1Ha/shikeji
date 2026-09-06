package com.shikeji.reminder.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.shikeji.reminder.MainActivity
import com.shikeji.reminder.data.ReminderStore

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val id = inputData.getString("id")
        val title = inputData.getString("title") ?: "健康提醒"
        val content = inputData.getString("content") ?: "该休息一下啦"
        val interval = inputData.getInt("interval", 30)

        // 先排下一轮再发通知，保证提醒链自持（用户不打开应用也会持续提醒）
        if (id != null) {
            ReminderStore.onReminderFired(applicationContext, id, interval)
        }

        sendNotification(title, content)
        return Result.success()
    }

    private fun sendNotification(title: String, content: String) {
        val channelId = "health_reminders"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "健康提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openApp = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
