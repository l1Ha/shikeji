package com.shikeji.reminder

import android.app.Application
import com.shikeji.reminder.data.HealthStore
import com.shikeji.reminder.data.ReminderStore
import com.shikeji.reminder.data.SettingsStore

class ShikejiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        HealthStore.init(this)
        SettingsStore.init(this)
        // 应用启动即武装提醒任务：补齐状态、恢复被强杀/重启打断的调度链
        ReminderStore.ensureScheduled(this)
    }
}
