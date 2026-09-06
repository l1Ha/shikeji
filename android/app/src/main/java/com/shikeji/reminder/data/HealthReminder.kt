package com.shikeji.reminder.data

import java.util.UUID

data class HealthReminder(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val intervalMinutes: Int,
    val description: String,
    val iconType: String = "default", // activity, water, eye
    val isEnabled: Boolean = true,
    val lastCompletedTimestamp: Long = 0L
) {
    fun getNextReminderTimestamp(): Long {
        if (lastCompletedTimestamp == 0L) return System.currentTimeMillis() + intervalMinutes * 60 * 1000
        return lastCompletedTimestamp + intervalMinutes * 60 * 1000
    }
}

/**
 * 默认提醒清单，UI 展示与 WorkManager 调度共用同一份数据。
 * id 需稳定（不使用随机 UUID），保证调度任务名唯一且重启后 KEEP 策略能命中。
 */
object DefaultReminders {
    val ALL = listOf(
        HealthReminder(
            id = "activity",
            title = "起身活动",
            intervalMinutes = 45,
            description = "站起来伸个腰，走动2分钟",
            iconType = "activity"
        ),
        HealthReminder(
            id = "water",
            title = "喝水提醒",
            intervalMinutes = 60,
            description = "给身体充个电，喝杯温水吧",
            iconType = "water"
        ),
        HealthReminder(
            id = "eye",
            title = "远眺放松",
            intervalMinutes = 30,
            description = "凝视远处绿色或远方，放松睫状肌",
            iconType = "eye"
        )
    )
}
