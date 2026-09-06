package com.shikeji.reminder.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 健康分持久化：今日分每日清零，累计完成次数一直保留。
 * 用 Compose state 暴露，UI 直接订阅。
 */
object HealthStore {
    private lateinit var prefs: SharedPreferences

    var score by mutableStateOf(0)
        private set
    var total by mutableStateOf(0)
        private set

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("shikeji_health", Context.MODE_PRIVATE)
            rolloverIfNeeded()
        }
    }

    /** 完成行为加分；当天首次完成时保持"今日分"语义 */
    fun addScore(points: Int) {
        rolloverIfNeeded()
        score = score + points
        prefs.edit()
            .putInt("score", score)
            .putInt("total", total + 1)
            .apply()
        total = total + 1
    }

    private fun rolloverIfNeeded() {
        val today = dayFormat.format(Date())
        if (prefs.getString("date", null) != today) {
            prefs.edit().putString("date", today).putInt("score", 0).apply()
            score = 0
        } else {
            score = prefs.getInt("score", 0)
        }
        total = prefs.getInt("total", 0)
    }
}
