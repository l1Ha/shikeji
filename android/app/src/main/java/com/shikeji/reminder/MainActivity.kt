package com.shikeji.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shikeji.reminder.data.HealthStore
import com.shikeji.reminder.data.HealthReminder
import com.shikeji.reminder.data.ReminderStore
import com.shikeji.reminder.data.SettingsStore
import com.shikeji.reminder.update.UpdateChecker
import com.shikeji.reminder.ui.theme.Teal200
import com.shikeji.reminder.ui.theme.Typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShikejiTheme {
                MainApp()
            }
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
    ) { /* 用户拒绝后不打扰，应用内提醒仍可用 */ }

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
        // 启动时自动检查新版本（有新版会自动弹出更新弹窗）
        UpdateChecker.check(context)
    }

    // 更新包下载中：轮询系统 DownloadManager 进度
    LaunchedEffect(UpdateChecker.downloading) {
        while (UpdateChecker.downloading) {
            delay(500)
            UpdateChecker.pollProgress(context)
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

    if (UpdateChecker.dialogVisible) {
        UpdateDialog(onDismiss = { UpdateChecker.dialogVisible = false })
    }
}

private val QUOTES = listOf(
    "深呼吸，感受当下的宁静。",
    "喝口水吧，让身体焕发活力。",
    "抬起头，看看远方的风景。",
    "每一分努力都值得被温柔对待。",
    "你的健康，是对家人最大的爱。"
)

@Composable
fun MainScreen(onStartBreathing: () -> Unit) {
    // 每秒刷新一次，驱动所有倒计时
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

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
        items(ReminderStore.reminders) { reminder ->
            ReminderCard(reminder, now)
        }
        item {
            Text(
                "提醒按设定周期自动触发通知，触发后回到应用点击「去完成」可领取健康分。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun HealthScoreCard() {
    val quotes = QUOTES
    var quoteIndex by remember { mutableStateOf((quotes.indices).random()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Teal200)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).clickable {
                quoteIndex = (quotes.indices).filter { it != quoteIndex }.random()
            }
        ) {
            Text("今日健康分", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${HealthStore.score} 分 · 累计完成 ${HealthStore.total} 次",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Text(
                "“${quotes[quoteIndex]}”",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "点击换一句",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
            )
        }
    }
}

@Composable
fun ReminderCard(reminder: HealthReminder, now: Long) {
    // 订阅 store 版本号，完成/调整间隔后立即刷新
    val revision = ReminderStore.revision
    val nextTrigger = remember(reminder.id, revision) { ReminderStore.nextTriggerAt(reminder.id) }
    val unacknowledged = remember(reminder.id, revision) { ReminderStore.isUnacknowledged(reminder.id) }

    val remainingSec = ((nextTrigger - now) / 1000L).coerceAtLeast(0L)
    val countdown = "%02d:%02d".format(remainingSec / 60, remainingSec % 60)
    val context = LocalContext.current

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
                Text(
                    "每 ${reminder.intervalMinutes} 分钟 · 下次 $countdown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Button(
                onClick = {
                    if (unacknowledged) {
                        HealthStore.addScore(10)
                        ReminderStore.acknowledge(reminder.id)
                        Toast.makeText(context, "太棒了！+10 分", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = unacknowledged,
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00b894),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(if (unacknowledged) "去完成 +10分" else "等待中", fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("我的提醒清单", style = MaterialTheme.typography.headlineMedium)
        Text(
            "标题、描述、间隔修改后自动保存并立即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ReminderStore.reminders.forEach { reminder ->
            ReminderEditCard(reminder)
        }
        OutlinedButton(
            onClick = { ReminderStore.addReminder(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ 添加新提醒")
        }

        Text(
            "提醒生效时间",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        TimeSettingRow("开始时间", SettingsStore.startTime) { SettingsStore.updateStartTime(it) }
        TimeSettingRow("结束时间", SettingsStore.endTime) { SettingsStore.updateEndTime(it) }
        if (SettingsStore.startTime > SettingsStore.endTime) {
            Text(
                "跨天模式：提醒将持续到次日结束时间",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFe17055)
            )
        }

        SwitchRow("勿扰模式（22:00 - 次日 8:00 自动静默）", SettingsStore.dnd) {
            SettingsStore.updateDnd(it)
        }
        SwitchRow("通知震动", SettingsStore.vibration) {
            SettingsStore.updateVibration(it)
        }

        Text(
            "关于与更新",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "当前版本 v" + UpdateChecker.currentVersionName(context),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            UpdateChecker.checkMessage ?: "应用启动时会自动检查新版本",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { UpdateChecker.check(context) },
                        enabled = !UpdateChecker.checking
                    ) {
                        Text(if (UpdateChecker.checking) "检查中…" else "检查更新")
                    }
                }
                val pendingUpdate = UpdateChecker.latest
                if (pendingUpdate != null) {
                    TextButton(onClick = { UpdateChecker.dialogVisible = true }) {
                        Text("发现新版本 ${pendingUpdate.version}，去更新", color = Color(0xFF00b894))
                    }
                }
            }
        }

        Text(
            "提示：部分厂商的后台管控较严，建议在系统设置中允许本应用「自启动」并将省电策略设为「无限制」，提醒会更准时。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditCard(reminder: HealthReminder) {
    val context = LocalContext.current
    val revision = ReminderStore.revision
    var title by remember(reminder.id) { mutableStateOf(reminder.title) }
    var description by remember(reminder.id) { mutableStateOf(reminder.description) }
    var sliderValue by remember(reminder.id) {
        mutableStateOf(reminder.intervalMinutes.toFloat())
    }
    val nextTrigger = remember(reminder.id, revision) { ReminderStore.nextTriggerAt(reminder.id) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    ReminderStore.updateText(reminder.id, it, description)
                },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    ReminderStore.updateText(reminder.id, title, it)
                },
                label = { Text("提醒描述") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "间隔 ${sliderValue.toInt()} 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF00b894),
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        ReminderStore.removeReminder(context, reminder.id)
                        Toast.makeText(context, "已删除「${reminder.title}」", Toast.LENGTH_SHORT).show()
                    },
                    enabled = ReminderStore.reminders.size > 1
                ) {
                    Text("删除", color = if (ReminderStore.reminders.size > 1) Color(0xFFd63031) else Color.Gray)
                }
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    val minutes = sliderValue.toInt()
                    ReminderStore.setInterval(context, reminder.id, minutes)
                    Toast.makeText(context, "已按 $minutes 分钟重新计时", Toast.LENGTH_SHORT).show()
                },
                valueRange = 1f..180f
            )
            Text(
                "下次提醒 " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                    .format(java.util.Date(nextTrigger)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSettingRow(label: String, value: String, onChange: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                color = Color(0xFF0984e3),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.clickable { showDialog = true }
            )
        }
    }

    if (showDialog) {
        val initialHour = value.substringBefore(":").toIntOrNull() ?: 9
        val initialMinute = value.substringAfter(":").toIntOrNull() ?: 0
        val timeState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange("%02d:%02d".format(timeState.hour, timeState.minute))
                    showDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("取消") }
            },
            text = { TimePicker(state = timeState) }
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF00b894))
            )
        }
    }
}

// ---------------- 应用内更新 ----------------

@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = UpdateChecker.latest ?: return

    AlertDialog(
        onDismissRequest = { if (!UpdateChecker.downloading) onDismiss() },
        title = { Text("发现新版本 ${info.version}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    info.notes.ifBlank { "性能优化与问题修复。" },
                    style = MaterialTheme.typography.bodySmall
                )
                when {
                    UpdateChecker.downloading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "下载中 ${UpdateChecker.downloadProgress}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = UpdateChecker.downloadProgress / 100f,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    UpdateChecker.downloadedApk != null -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "下载完成，点击「安装」开始更新。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00b894),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        UpdateChecker.downloadedApk != null -> UpdateChecker.install(context)
                        !UpdateChecker.downloading -> UpdateChecker.startDownload(context)
                    }
                },
                enabled = !UpdateChecker.downloading
            ) {
                Text(
                    when {
                        UpdateChecker.downloadedApk != null -> "安装"
                        UpdateChecker.downloading -> "下载中…"
                        else -> "立即更新"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (UpdateChecker.downloading) "后台下载" else "暂不更新")
            }
        }
    )
}

// ---------------- 呼吸冥想 ----------------

data class BreathMode(
    val name: String,
    val inhale: Int,
    val hold: Int,
    val exhale: Int,
    val custom: Boolean = false
)

private val BREATH_MODES = listOf(
    BreathMode("4-7-8 助眠", 4, 7, 8),
    BreathMode("等比呼吸", 4, 4, 4),
    BreathMode("快速冷静", 2, 0, 4),
    BreathMode("自定义", 4, 4, 6, custom = true)
)

private enum class BreathPhase { IDLE, INHALE, HOLD, EXHALE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("shikeji_breath", Context.MODE_PRIVATE) }

    var modeIndex by remember {
        mutableStateOf(prefs.getInt("mode", 1).coerceIn(0, BREATH_MODES.lastIndex))
    }
    var customInhale by remember { mutableStateOf(prefs.getInt("c_inhale", 4).coerceIn(1, 10)) }
    var customHold by remember { mutableStateOf(prefs.getInt("c_hold", 4).coerceIn(0, 10)) }
    var customExhale by remember { mutableStateOf(prefs.getInt("c_exhale", 6).coerceIn(1, 10)) }

    val mode = BREATH_MODES[modeIndex]
    val effective = if (mode.custom) mode.copy(inhale = customInhale, hold = customHold, exhale = customExhale) else mode

    var isRunning by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(BreathPhase.IDLE) }
    var phaseEndAt by remember { mutableStateOf(0L) }
    var phaseDurationMs by remember { mutableStateOf(4000) }
    var sessionEndAt by remember { mutableStateOf(0L) }
    var sessionLeft by remember { mutableStateOf(60) }
    var phaseLeft by remember { mutableStateOf(0) }

    val scale by animateFloatAsState(
        targetValue = if (phase == BreathPhase.INHALE || phase == BreathPhase.HOLD) 2f else 1f,
        animationSpec = tween(durationMillis = phaseDurationMs, easing = LinearEasing),
        label = "breathScale"
    )

    // 呼吸阶段驱动：吸气 -> (屏息) -> 呼气 循环，直到会话结束或手动停止
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (isActive) {
            phaseDurationMs = effective.inhale * 1000
            phase = BreathPhase.INHALE
            phaseEndAt = System.currentTimeMillis() + effective.inhale * 1000L
            delay(effective.inhale * 1000L)
            if (!isActive) return@LaunchedEffect

            if (effective.hold > 0) {
                phaseDurationMs = effective.hold * 1000
                phase = BreathPhase.HOLD
                phaseEndAt = System.currentTimeMillis() + effective.hold * 1000L
                delay(effective.hold * 1000L)
                if (!isActive) return@LaunchedEffect
            }

            phaseDurationMs = effective.exhale * 1000
            phase = BreathPhase.EXHALE
            phaseEndAt = System.currentTimeMillis() + effective.exhale * 1000L
            delay(effective.exhale * 1000L)
            if (!isActive) return@LaunchedEffect
        }
    }

    // 会话与阶段倒计时（250ms 刷新一次足够流畅），自然完成时奖励 5 分
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        while (isActive) {
            delay(250)
            val nowMs = System.currentTimeMillis()
            phaseLeft = (((phaseEndAt - nowMs) + 999) / 1000L).toInt().coerceAtLeast(0)
            sessionLeft = (((sessionEndAt - nowMs) + 999) / 1000L).toInt().coerceAtLeast(0)
            if (nowMs >= sessionEndAt) {
                isRunning = false
                phase = BreathPhase.IDLE
                phaseLeft = 0
                HealthStore.addScore(5)
                Toast.makeText(context, "呼吸完成！+5 分", Toast.LENGTH_SHORT).show()
                break
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00cec9))
            .clickable(enabled = !isRunning) { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack, enabled = !isRunning) {
                    Text("返回", color = Color.White.copy(alpha = if (isRunning) 0.3f else 0.9f))
                }
                Text(
                    "1 分钟呼吸冥想",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 节奏选择：练习中隐藏
            if (!isRunning) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    BREATH_MODES.forEachIndexed { index, m ->
                        FilterChip(
                            selected = modeIndex == index,
                            onClick = {
                                modeIndex = index
                                prefs.edit().putInt("mode", index).apply()
                            },
                            label = { Text(m.name, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                labelColor = Color.White,
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color(0xFF00cec9)
                            )
                        )
                    }
                }
                if (mode.custom) {
                    CustomBreathEditor(
                        inhale = customInhale,
                        hold = customHold,
                        exhale = customExhale,
                        onChange = { field, value ->
                            when (field) {
                                "inhale" -> { customInhale = value; prefs.edit().putInt("c_inhale", value).apply() }
                                "hold" -> { customHold = value; prefs.edit().putInt("c_hold", value).apply() }
                                "exhale" -> { customExhale = value; prefs.edit().putInt("c_exhale", value).apply() }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 呼吸圆圈 + 状态
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.55f))
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val statusText = when (phase) {
                        BreathPhase.INHALE -> "吸气..."
                        BreathPhase.HOLD -> "屏息..."
                        BreathPhase.EXHALE -> "呼气..."
                        BreathPhase.IDLE -> if (isRunning) "" else "准备好了吗？"
                    }
                    Text(statusText, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    if (isRunning && phaseLeft > 0) {
                        Text("${phaseLeft}s", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "%02d".format(sessionLeft),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 40.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (isRunning) {
                        isRunning = false
                        phase = BreathPhase.IDLE
                        phaseLeft = 0
                        sessionLeft = 60
                    } else {
                        sessionLeft = 60
                        sessionEndAt = System.currentTimeMillis() + 60_000L
                        isRunning = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF00cec9)),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(bottom = 48.dp).padding(horizontal = 40.dp)
            ) {
                Text(if (isRunning) "结束" else "开始", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CustomBreathEditor(
    inhale: Int,
    hold: Int,
    exhale: Int,
    onChange: (String, Int) -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            CustomSliderRow("吸气 ${inhale}s", inhale, 1, 10) { onChange("inhale", it) }
            CustomSliderRow("屏息 ${hold}s", hold, 0, 10) { onChange("hold", it) }
            CustomSliderRow("呼气 ${exhale}s", exhale, 1, 10) { onChange("exhale", it) }
            Text(
                "一轮共 ${inhale + hold + exhale} 秒 · 自动保存",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CustomSliderRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(84.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = max - min - 1,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
