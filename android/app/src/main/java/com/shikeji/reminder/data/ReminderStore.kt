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
import org.json.JSONArray
import org.json.JSONObject

/**
 * 提醒状态持久化 + WorkManager 调度。
 * 提醒清单为动态列表（可增删改），JSON 存于 SharedPreferences；
 * 每个提醒另存：下次触发时间戳（n_id）、待确认标记（u_id，通知已触发等待用户确认）。
 * 调度采用「一次性延迟任务链」：触发后 Worker 负责排下一轮，保证提醒自持；
 * 修改间隔后按新间隔立即重新计时（与小程序行为一致）。
 */
object ReminderStore {
    private lateinit var prefs: SharedPreferences

    var reminders by mutableStateOf(listOf<HealthReminder>())
        private set

    // UI 订阅用：任何写入都会递增，触发 Compose 重组
    var revision by mutableStateOf(0)
        private set

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("shikeji_reminders", Context.MODE_PRIVATE)
            reminders = loadList()
        }
    }

    fun reminderById(id: String): HealthReminder? = reminders.firstOrNull { it.id == id }

    fun nextTriggerAt(id: String): Long = prefs.getLong("n_$id", 0L)

    fun isUnacknowledged(id: String): Boolean = prefs.getBoolean("u_$id", false)

    /** 应用启动时调用：补齐缺失状态并重新武装任务（覆盖强杀/重启后的场景） */
    fun ensureScheduled(context: Context) {
        init(context)
        val now = System.currentTimeMillis()
        reminders.forEach { reminder ->
            if (nextTriggerAt(reminder.id) == 0L) {
                setNextTrigger(reminder.id, now + reminder.intervalMinutes * 60_000L)
            }
        }
        reminders.forEach { schedule(context, it.id) }
        bump()
    }

    /** 调整间隔：按新间隔立即重新计时并重新调度 */
    fun setInterval(context: Context, id: String, minutes: Int) {
        val minutes = minutes.coerceIn(1, 180)
        reminders = reminders.map { if (it.id == id) it.copy(intervalMinutes = minutes) else it }
        saveList()
        setNextTrigger(id, System.currentTimeMillis() + minutes * 60_000L)
        prefs.edit().putBoolean("u_$id", false).apply()
        schedule(context, id)
        bump()
    }

    /** 修改标题/描述（每次输入即保存） */
    fun updateText(id: String, title: String, description: String) {
        reminders = reminders.map {
            if (it.id == id) it.copy(title = title.ifBlank { "未命名提醒" }, description = description) else it
        }
        saveList()
        bump()
    }

    /** 新增提醒：默认 30 分钟，立即进入调度 */
    fun addReminder(context: Context) {
        val reminder = HealthReminder(
            id = "r_" + java.lang.Long.toString(System.currentTimeMillis(), 36),
            title = "新提醒",
            intervalMinutes = 30,
            description = "提醒内容"
        )
        reminders = reminders + reminder
        saveList()
        setNextTrigger(reminder.id, System.currentTimeMillis() + 30 * 60_000L)
        schedule(context, reminder.id)
        bump()
    }

    /** 删除提醒：取消后台任务并清理状态，至少保留一个 */
    fun removeReminder(context: Context, id: String) {
        if (reminders.size <= 1) return
        reminders = reminders.filter { it.id != id }
        saveList()
        prefs.edit().remove("n_$id").remove("u_$id").apply()
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$id")
        bump()
    }

    /** 用户在应用内确认完成：+10 分由 UI 层处理，这里只清除待确认标记 */
    fun acknowledge(id: String) {
        prefs.edit().putBoolean("u_$id", false).apply()
        bump()
    }

    /** 通知触发后由 Worker 调用：排下一轮（是否通知/标记待确认由 Worker 按静默状态决定） */
    fun onReminderFired(context: Context, id: String) {
        val reminder = reminderById(id) ?: return
        setNextTrigger(id, System.currentTimeMillis() + reminder.intervalMinutes * 60_000L)
        schedule(context, id)
        bump()
    }

    fun markUnacknowledged(id: String) {
        prefs.edit().putBoolean("u_$id", true).apply()
        bump()
    }

    private fun schedule(context: Context, id: String) {
        val delayMs = (nextTriggerAt(id) - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("id" to id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder_$id",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun setNextTrigger(id: String, timestamp: Long) {
        prefs.edit().putLong("n_$id", timestamp).apply()
    }

    private fun loadList(): List<HealthReminder> {
        val raw = prefs.getString("list", null) ?: return DefaultReminders.ALL
        return try {
            val array = JSONArray(raw)
            val loaded = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                HealthReminder(
                    id = item.getString("id"),
                    title = item.getString("title"),
                    intervalMinutes = item.optInt("interval", 30),
                    description = item.optString("desc", "")
                )
            }
            loaded.ifEmpty { DefaultReminders.ALL }
        } catch (e: Exception) {
            DefaultReminders.ALL
        }
    }

    private fun saveList() {
        val array = JSONArray()
        reminders.forEach { reminder ->
            array.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("title", reminder.title)
                    .put("desc", reminder.description)
                    .put("interval", reminder.intervalMinutes)
            )
        }
        prefs.edit().putString("list", array.toString()).apply()
    }

    private fun bump() {
        revision = revision + 1
    }
}
