package com.shikeji.reminder.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shikeji.reminder.worker.ReminderWorker
import java.util.concurrent.TimeUnit

/**
 * 提醒状态持久化 + WorkManager 调度。
 * 每个提醒包含：间隔分钟数、下次触发时间戳、待确认标记（通知已触发、等待用户在应用内确认）。
 * 调度采用「一次性延迟任务链」：触发后 Worker 负责排下一轮，保证提醒自持；
 * 修改间隔后按新间隔立即重新计时（与小程序行为一致）。
 */
object ReminderStore {
    private lateinit var prefs: SharedPreferences

    // UI 订阅用：任何写入都会递增，触发 Compose 重组
    var revision by mutableStateOf(0)
        private set

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("shikeji_reminders", Context.MODE_PRIVATE)
        }
    }

    private fun ensureInit(context: Context) = init(context)

    fun intervalMinutes(id: String): Int = prefs.getInt("i_$id", 30)

    fun nextTriggerAt(id: String): Long = prefs.getLong("n_$id", 0L)

    fun isUnacknowledged(id: String): Boolean = prefs.getBoolean("u_$id", false)

    /** 应用启动时调用：补齐缺失状态并重新武装任务（覆盖强杀/清除后的场景） */
    fun ensureScheduled(context: Context) {
        ensureInit(context)
        DefaultReminders.ALL.forEach { reminder ->
            if (nextTriggerAt(reminder.id) == 0L) {
                setNextTrigger(reminder.id, System.currentTimeMillis() + reminder.intervalMinutes * 60_000L)
            }
            schedule(context, reminder)
        }
        bump()
    }

    /** 调整间隔：按新间隔立即重新计时并重新调度 */
    fun setInterval(context: Context, id: String, minutes: Int) {
        ensureInit(context)
        prefs.edit().putInt("i_$id", minutes).apply()
        setNextTrigger(id, System.currentTimeMillis() + minutes * 60_000L)
        prefs.edit().putBoolean("u_$id", false).apply()
        DefaultReminders.ALL.firstOrNull { it.id == id }?.let { schedule(context, it) }
        bump()
    }

    /** 用户在应用内确认完成：+10 分由 UI 层处理，这里只重置待确认标记 */
    fun acknowledge(id: String) {
        prefs.edit().putBoolean("u_$id", false).apply()
        bump()
    }

    /** 通知触发后由 Worker 调用：排下一轮并标记待确认 */
    fun onReminderFired(context: Context, id: String, intervalMinutes: Int) {
        ensureInit(context)
        setNextTrigger(id, System.currentTimeMillis() + intervalMinutes * 60_000L)
        prefs.edit().putBoolean("u_$id", true).apply()
        DefaultReminders.ALL.firstOrNull { it.id == id }?.let { schedule(context, it) }
        bump()
    }

    private fun setNextTrigger(id: String, timestamp: Long) {
        prefs.edit().putLong("n_$id", timestamp).apply()
    }

    private fun schedule(context: Context, reminder: HealthReminder) {
        val interval = intervalMinutes(reminder.id)
        val delayMs = (nextTriggerAt(reminder.id) - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "id" to reminder.id,
                    "title" to reminder.title,
                    "content" to reminder.description,
                    "interval" to interval
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder_${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun bump() {
        revision = revision + 1
    }
}
