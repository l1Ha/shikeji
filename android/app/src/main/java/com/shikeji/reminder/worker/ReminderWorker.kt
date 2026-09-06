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
import com.shikeji.reminder.data.SettingsStore

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.success()
        val reminder = ReminderStore.reminderById(id) ?: return Result.success() // 提醒已被删除

        SettingsStore.init(applicationContext)

        // 先排下一轮再决定是否通知，保证提醒链自持（用户不打开应用也会持续提醒）
        ReminderStore.onReminderFired(applicationContext, id)

        // 静默时段（非生效窗口 / 勿扰时段）：不打扰，也不标记待确认
        if (!SettingsStore.isSilentNow()) {
            ReminderStore.markUnacknowledged(id)
            sendNotification(reminder.title, reminder.description)
        }
        return Result.success()
    }

    private fun sendNotification(title: String, content: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 震动开关用两个渠道实现：Android 8+ 渠道一旦创建，震动行为以渠道为准
        val vibrationOn = SettingsStore.vibration
        val channelId = if (vibrationOn) "health_reminders" else "health_reminders_quiet"
        val pattern = longArrayOf(0, 300, 200, 300)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibChannel = NotificationChannel("health_reminders", "健康提醒（震动）", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = pattern
            }
            val quietChannel = NotificationChannel("health_reminders_quiet", "健康提醒（静音）", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(vibChannel)
            notificationManager.createNotificationChannel(quietChannel)
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
            .setVibrate(if (vibrationOn) pattern else longArrayOf(0L))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
