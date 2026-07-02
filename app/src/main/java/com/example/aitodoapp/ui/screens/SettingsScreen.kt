package com.example.aitodoapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.aitodoapp.data.ReportRepository
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.data.TokenRepository
import com.example.aitodoapp.ui.components.CalendarTestDialog

// ============ 设置页 ============

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onTestReport: ((Boolean) -> Unit)? = null, onScheduleReport: ((Boolean) -> Unit)? = null, onScheduleDaily: ((isMorning: Boolean, hour: Int, minute: Int) -> Unit)? = null, onRestoreTask: ((String) -> Unit)? = null, onPermanentDelete: ((String) -> Unit)? = null) {
    val s = remember { mutableStateOf(SettingsRepository.load()) }
    var apiUrl by remember { mutableStateOf(s.value.apiUrl) }
    var apiKey by remember { mutableStateOf(s.value.apiKey) }
    var model by remember { mutableStateOf(s.value.model) }
    var saved by remember { mutableStateOf(false) }
    var showCalendarTest by remember { mutableStateOf(false) }
    var autoSync by remember { mutableStateOf(s.value.autoSyncCalendar) }
    var defaultRemind by remember { mutableStateOf(s.value.defaultReminderMinutes) }
    val settingsContext = LocalContext.current
    val calendarPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // ──── API 设置 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" API 设置 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(16.dp))
        Text("API 地址", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = apiUrl, onValueChange = { apiUrl = it; saved = false },
            placeholder = { Text("https://api.deepseek.com/chat/completions") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(16.dp))
        Text("API Key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; saved = false },
            placeholder = { Text("sk-...") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(16.dp))
        Text("模型", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = model, onValueChange = { model = it; saved = false },
            placeholder = { Text("deepseek-v4-flash") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(24.dp))

        // ──── 显示偏好 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 显示偏好 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(16.dp))
        var mergeOverdue by remember { mutableStateOf(s.value.showOverdueInline) }
        var longChat by remember { mutableStateOf(s.value.longPressChat) }
        Row(Modifier.fillMaxWidth().clickable { mergeOverdue = !mergeOverdue }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("过期任务分区", style = MaterialTheme.typography.bodyMedium)
                Text("关闭后过期任务混排在列表底部（正常→过期→已完成）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = mergeOverdue, onCheckedChange = { mergeOverdue = it })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clickable { longChat = !longChat }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("长按打开 AI 对话框", style = MaterialTheme.typography.bodyMedium)
                Text("关闭后短按打开 AI 对话框，长按打开新建任务窗口", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = longChat, onCheckedChange = { longChat = it })
        }
        Spacer(Modifier.height(12.dp))
        var showToken by remember { mutableStateOf(s.value.showTokenUsage) }
        Row(Modifier.fillMaxWidth().clickable { showToken = !showToken }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("显示 Token 用量", style = MaterialTheme.typography.bodyMedium)
                Text("开启后在头部显示今日 AI 调用 Token 数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = showToken, onCheckedChange = { showToken = it })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clickable { autoSync = !autoSync }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("自动同步日历", style = MaterialTheme.typography.bodyMedium)
                Text("有截止日期的任务自动写入系统日历", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = autoSync, onCheckedChange = {
                autoSync = it
                if (it) {
                    val check = ContextCompat.checkSelfPermission(settingsContext, android.Manifest.permission.WRITE_CALENDAR)
                    if (check != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        calendarPermLauncher.launch(android.Manifest.permission.WRITE_CALENDAR)
                    }
                }
            })
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("默认提醒时间", style = MaterialTheme.typography.bodyMedium)
                Text("提前 ${defaultRemind} 分钟提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { defaultRemind = (defaultRemind + 5).coerceAtMost(120) }, shape = RoundedCornerShape(8.dp)) { Text("+5", fontSize = 12.sp) }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(onClick = { defaultRemind = (defaultRemind - 5).coerceAtLeast(0) }, shape = RoundedCornerShape(8.dp)) { Text("-5", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(24.dp))

        // ──── 早晚间播报 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 早晚间播报 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(16.dp))
        var reportEnabled by remember { mutableStateOf(s.value.reportEnabled) }
        Row(Modifier.fillMaxWidth().clickable { reportEnabled = !reportEnabled }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("启用播报", style = MaterialTheme.typography.bodyMedium)
                Text("AI 每日生成早晚间代办报告", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = reportEnabled, onCheckedChange = { reportEnabled = it })
        }
        if (reportEnabled) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("早间播报", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedTextField(value = s.value.morningReportTime, onValueChange = { v ->
                    val clean = v.replace(":", "")
                    val formatted = if (clean.length == 4) "${clean.take(2)}:${clean.drop(2)}" else v
                    if (formatted.matches(Regex("^\\d{2}:\\d{2}$"))) {
                        val h = formatted.take(2).toIntOrNull() ?: return@OutlinedTextField
                        val m = formatted.drop(3).toIntOrNull() ?: return@OutlinedTextField
                        if (h in 0..23 && m in 0..59) s.value = s.value.copy(morningReportTime = formatted)
                    } else if (v.length <= 5) {
                        s.value = s.value.copy(morningReportTime = v)
                    }
                }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(100.dp), shape = RoundedCornerShape(8.dp), placeholder = { Text("07:00", fontSize = 14.sp) })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("晚间播报", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedTextField(value = s.value.eveningReportTime, onValueChange = { v ->
                    val clean = v.replace(":", "")
                    val formatted = if (clean.length == 4) "${clean.take(2)}:${clean.drop(2)}" else v
                    if (formatted.matches(Regex("^\\d{2}:\\d{2}$"))) {
                        val h = formatted.take(2).toIntOrNull() ?: return@OutlinedTextField
                        val m = formatted.drop(3).toIntOrNull() ?: return@OutlinedTextField
                        if (h in 0..23 && m in 0..59) s.value = s.value.copy(eveningReportTime = formatted)
                    } else if (v.length <= 5) {
                        s.value = s.value.copy(eveningReportTime = v)
                    }
                }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(100.dp), shape = RoundedCornerShape(8.dp), placeholder = { Text("21:00", fontSize = 14.sp) })
            }
        }
        Spacer(Modifier.height(24.dp))

        // ──── 数据统计 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 数据统计 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(12.dp))
        val total = TokenRepository.getTotalTokens()
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column {
                Text("总 Token 用量", style = MaterialTheme.typography.bodyMedium)
                Text("输入 ${total.prompt} · 输出 ${total.completion} · 共 ${total.prompt + total.completion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // ──── 测试 ────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 测试 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { showCalendarTest = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("测试日历写入") }
        Spacer(Modifier.height(8.dp))
        if (onTestReport != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onTestReport(true) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("🌅 测试早间播报", fontSize = 13.sp) }
                Button(onClick = { onTestReport(false) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("🌙 测试晚间播报", fontSize = 13.sp) }
            }
        }
        if (onScheduleReport != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onScheduleReport(true) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("⏰ 1分钟后·早间", fontSize = 12.sp) }
                Button(onClick = { onScheduleReport(false) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("⏰ 1分钟后·晚间", fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { ReportRepository.clearAll() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("🗑️ 清除播报记录", fontSize = 13.sp) }
        if (onRestoreTask != null) {
            Spacer(Modifier.height(8.dp))
            var showTrash by remember { mutableStateOf(false) }
            Button(onClick = { showTrash = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("📦 最近删除", fontSize = 13.sp) }
            if (showTrash) {
                val trashTasks = remember { com.example.aitodoapp.data.TaskRepository.load<com.example.aitodoapp.Task>("tasks.json").filter { it.isDeleted } }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showTrash = false },
                    title = { Text("最近删除（保留30天）", fontWeight = FontWeight.Bold) },
                    text = {
                        if (trashTasks.isEmpty()) {
                            Text("暂无删除的任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column {
                                trashTasks.forEach { task ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Text(task.title, modifier = Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        TextButton(onClick = { onRestoreTask?.invoke(task.id); showTrash = false }) { Text("恢复", fontSize = 11.sp) }
                                        TextButton(onClick = { onPermanentDelete?.invoke(task.id); showTrash = false }) { Text("删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showTrash = false }) { Text("关闭") } }
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            val newSettings = SettingsRepository.Settings(apiUrl.trim(), apiKey.trim(), model.trim(), mergeOverdue, longChat, showToken, autoSync, defaultRemind, s.value.defaultDurationMinutes, reportEnabled, s.value.morningReportTime, s.value.eveningReportTime)
            SettingsRepository.save(newSettings)
            if (reportEnabled && onScheduleDaily != null) {
                val mh = s.value.morningReportTime.substringBefore(":").toIntOrNull() ?: 7
                val mm = s.value.morningReportTime.substringAfter(":").toIntOrNull() ?: 0
                val eh = s.value.eveningReportTime.substringBefore(":").toIntOrNull() ?: 21
                val em = s.value.eveningReportTime.substringAfter(":").toIntOrNull() ?: 0
                onScheduleDaily(true, mh, mm)
                onScheduleDaily(false, eh, em)
            }
            saved = true
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("保存") }
        if (showCalendarTest) CalendarTestDialog(onDismiss = { showCalendarTest = false })
        if (saved) { Spacer(Modifier.height(8.dp)); Text("✓ 已保存", color = MaterialTheme.colorScheme.primary) }
    }
}

// ──── 时间调整辅助函数 ────
private fun timeAddHour(t: String): String {
    val h = (t.substringBefore(":").toIntOrNull() ?: 0).let { if (it + 1 >= 24) 0 else it + 1 }
    val m = t.substringAfter(":").toIntOrNull() ?: 0
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
private fun timeSubHour(t: String): String {
    val h = (t.substringBefore(":").toIntOrNull() ?: 0).let { if (it - 1 < 0) 23 else it - 1 }
    val m = t.substringAfter(":").toIntOrNull() ?: 0
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
private fun timeAddMin(t: String): String {
    val h = t.substringBefore(":").toIntOrNull() ?: 0
    val m = (t.substringAfter(":").toIntOrNull() ?: 0).let { if (it + 5 >= 60) 0 else it + 5 }
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
private fun timeSubMin(t: String): String {
    val h = t.substringBefore(":").toIntOrNull() ?: 0
    val m = (t.substringAfter(":").toIntOrNull() ?: 0).let { if (it - 5 < 0) 55 else it - 5 }
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
}
