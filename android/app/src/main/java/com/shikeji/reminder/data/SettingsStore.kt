package com.shikeji.reminder.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.Locale

/**
 * 应用设置：提醒生效时间窗（支持跨天）、勿扰模式、通知震动。
 * 静默判断逻辑与小程序一致：不在生效窗口内或处于勿扰时段（22:00-次日 8:00）则不弹通知。
 */
object SettingsStore {
    private lateinit var prefs: SharedPreferences

    var startTime by mutableStateOf("09:00")
        private set
    var endTime by mutableStateOf("17:00")
        private set
    var dnd by mutableStateOf(true)
        private set
    var vibration by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("shikeji_settings", Context.MODE_PRIVATE)
            startTime = prefs.getString("start", "09:00") ?: "09:00"
            endTime = prefs.getString("end", "17:00") ?: "17:00"
            dnd = prefs.getBoolean("dnd", true)
            vibration = prefs.getBoolean("vibration", true)
        }
    }

    fun updateStartTime(value: String) {
        startTime = value
        prefs.edit().putString("start", value).apply()
    }

    fun updateEndTime(value: String) {
        endTime = value
        prefs.edit().putString("end", value).apply()
    }

    fun updateDnd(value: Boolean) {
        dnd = value
        prefs.edit().putBoolean("dnd", value).apply()
    }

    fun updateVibration(value: Boolean) {
        vibration = value
        prefs.edit().putBoolean("vibration", value).apply()
    }

    /** 当前是否处于静默时段：不在生效窗口内，或勿扰模式开启且处于 22:00-次日 8:00 */
    fun isSilentNow(): Boolean {
        val cal = Calendar.getInstance()
        val current = String.format(
            Locale.CHINA, "%02d:%02d",
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val inWindow = if (startTime <= endTime) {
            current >= startTime && current <= endTime
        } else {
            current >= startTime || current <= endTime
        }
        val dndBlocked = dnd && (hour >= 22 || hour < 8)
        return !inWindow || dndBlocked
    }
}
