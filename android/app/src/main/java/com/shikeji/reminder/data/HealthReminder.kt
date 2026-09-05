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
