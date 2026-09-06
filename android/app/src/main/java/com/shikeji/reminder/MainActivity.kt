package com.shikeji.reminder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shikeji.reminder.data.DefaultReminders
import com.shikeji.reminder.data.HealthReminder
import com.shikeji.reminder.ui.theme.Teal200
import com.shikeji.reminder.ui.theme.Typography
import com.shikeji.reminder.worker.ReminderWorker
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleDefaultReminders()
        setContent {
            ShikejiTheme {
                MainApp()
            }
        }
    }

    /**
     * 用 WorkManager 周期任务调度默认提醒。
     * - KEEP 策略：重复启动不会重复排队；WorkManager 自带开机后自动恢复
     * - 兼容性：周期任务最小间隔 15 分钟，系统为省电可能合并执行，属正常行为
     */
    private fun scheduleDefaultReminders() {
        val workManager = WorkManager.getInstance(this)
        DefaultReminders.ALL.forEach { reminder ->
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(reminder.intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(
                    workDataOf(
                        "title" to reminder.title,
                        "content" to reminder.description
                    )
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                "reminder_${reminder.id}",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

@Composable
fun ShikejiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal200,
            secondary = Teal200,
            tertiary = Teal200
        ),
        typography = Typography,
        content = content
    )
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户拒绝后不打扰，仅应用内提醒仍可用 */ }

    LaunchedEffect(Unit) {
        // 兼容性：Android 13+ 通知是运行时权限，未授权时通知不会展示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var currentTab by remember { mutableStateOf(0) }
    var showBreathing by remember { mutableStateOf(false) }

    if (showBreathing) {
        BreathingScreen(onBack = { showBreathing = false })
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "主页") },
                        label = { Text("提醒") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                        label = { Text("设置") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                if (currentTab == 0) {
                    MainScreen(onStartBreathing = { showBreathing = true })
                } else {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen(onStartBreathing: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { HealthScoreCard() }
        item {
            Button(
                onClick = onStartBreathing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00cec9))
            ) {
                Text("开始1分钟呼吸冥想")
            }
        }
        items(DefaultReminders.ALL) { reminder ->
            ReminderCard(reminder)
        }
    }
}

@Composable
fun HealthScoreCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Teal200)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("今日健康分", color = Color.White)
            Text("80", style = MaterialTheme.typography.displayMedium, color = Color.White)
            Text("“深呼吸，感受当下的宁静。”", color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ReminderCard(reminder: HealthReminder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(reminder.description, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                val minutes = reminder.intervalMinutes
                Text("%02d:00".format(minutes), style = MaterialTheme.typography.titleLarge, color = Teal200)
                Button(onClick = {}, modifier = Modifier.height(30.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                    Text("已调度", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("时刻计 设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))
        Text("提醒生效时间: 09:00 - 17:00")
        Spacer(modifier = Modifier.height(8.dp))
        Text("提醒已在启动时自动调度，无需额外设置", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun BreathingScreen(onBack: () -> Unit) {
    var isInhaling by remember { mutableStateOf(true) }
    val scale by animateFloatAsState(
        targetValue = if (isInhaling) 2f else 1f,
        animationSpec = tween(durationMillis = 4000, easing = LinearEasing),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        while (true) {
            isInhaling = true
            delay(4000)
            isInhaling = false
            delay(4000)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF00cec9)).clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .scale(scale)
                    .background(Color.White.copy(alpha = 0.5f))
            )
            Spacer(modifier = Modifier.height(100.dp))
            Text(if (isInhaling) "吸气..." else "呼气...", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text("点击任意位置返回", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 20.dp))
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)
