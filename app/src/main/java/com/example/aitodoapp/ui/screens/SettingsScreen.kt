package com.example.aitodoapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.aitodoapp.data.AppLogger
import com.example.aitodoapp.data.ReportRepository
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.data.TaskRepository
import com.example.aitodoapp.data.TokenRepository
import com.example.aitodoapp.ui.components.CalendarTestDialog

// ============ 设置页 ============

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onTestReport: ((Boolean) -> Unit)? = null, onScheduleReport: ((Boolean) -> Unit)? = null, onRestoreTask: ((String) -> Unit)? = null, onPermanentDelete: ((String, String) -> Unit)? = null) {
    val s = remember { mutableStateOf(SettingsRepository.load()) }
    var apiUrl by remember { mutableStateOf(s.value.apiUrl) }
    var apiKey by remember { mutableStateOf(s.value.apiKey) }
    var model by remember { mutableStateOf(s.value.model) }
    var saved by remember { mutableStateOf(false) }
    var showCalendarTest by remember { mutableStateOf(false) }
    var showLogScreen by remember { mutableStateOf(false) }
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
        val sortLabels = mapOf("manual" to "不排序", "deadline" to "截止时间", "priority" to "优先级", "created" to "创建时间")
        var sortOrder by remember { mutableStateOf(s.value.taskSortOrder) }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("任务排序", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                val keys = listOf("manual", "deadline", "priority", "created")
                val idx = keys.indexOf(sortOrder)
                sortOrder = keys[(idx + 1) % keys.size]
            }, shape = RoundedCornerShape(8.dp)) { Text(sortLabels[sortOrder] ?: "不排序", fontSize = 13.sp) }
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
            Text("默认提醒", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedTextField(value = defaultRemind.toString(), onValueChange = { v ->
                val n = v.filter { it.isDigit() }.take(4).toIntOrNull()
                if (n != null && n in 0..1440) defaultRemind = n; else if (v.isEmpty()) defaultRemind = 0
            }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(80.dp), shape = RoundedCornerShape(8.dp), placeholder = { Text("30", fontSize = 14.sp) })
            Spacer(Modifier.width(4.dp))
            Text("分钟", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("默认时长", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedTextField(value = s.value.defaultDurationMinutes.toString(), onValueChange = { v ->
                val n = v.filter { it.isDigit() }.take(4).toIntOrNull()
                if (n != null && n in 1..1440) s.value = s.value.copy(defaultDurationMinutes = n); else if (v.isEmpty()) s.value = s.value.copy(defaultDurationMinutes = 1)
            }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(80.dp), shape = RoundedCornerShape(8.dp), placeholder = { Text("60", fontSize = 14.sp) })
            Spacer(Modifier.width(4.dp))
            Text("分钟", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Spacer(Modifier.height(8.dp))
        var keepAlive by remember { mutableStateOf(s.value.keepAliveEnabled) }
        Row(Modifier.fillMaxWidth().clickable { keepAlive = !keepAlive }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("后台保活", style = MaterialTheme.typography.bodyMedium)
                Text("开启前台服务常驻通知栏，防止 App 被杀导致播报不响（国产 ROM 仍需在系统设置里允许后台运行）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = keepAlive, onCheckedChange = {
                keepAlive = it
                // 即时启停：保活状态需要立即生效，不能等"保存"按钮
                if (it) {
                    com.example.aitodoapp.ForegroundService.start(settingsContext)
                    com.example.aitodoapp.data.NotificationRefreshWorker.start(settingsContext)
                } else {
                    com.example.aitodoapp.ForegroundService.stop(settingsContext)
                    com.example.aitodoapp.data.NotificationRefreshWorker.stop(settingsContext)
                }
            })
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
        // ──── 测试（可折叠） ────
        var testExpanded by remember { mutableStateOf(false) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clickable { testExpanded = !testExpanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (testExpanded) "▼" else "▶", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 测试 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        AnimatedVisibility(visible = testExpanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                // 迁移状态 + 按钮
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("数据文件结构：", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val isMigrated = TaskRepository.fileExists("archived_tasks.json")
                    Text(if (isMigrated) "✅ 已迁移" else "⏳ 旧版单文件", fontSize = 13.sp, color = if (isMigrated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = {
                    try {
                        val oldAll = TaskRepository.load<com.example.aitodoapp.Task>("tasks.json")
                        val hasArchived = oldAll.any { it.isArchived || it.isDeleted }
                        if (hasArchived || !TaskRepository.fileExists("archived_tasks.json")) {
                            val active = oldAll.filter { !it.isArchived && !it.isDeleted }
                            val archived = oldAll.filter { it.isArchived && !it.isDeleted }
                            val deleted = oldAll.filter { it.isDeleted }
                            TaskRepository.save("tasks.json", active)
                            TaskRepository.save("archived_tasks.json", archived)
                            TaskRepository.save("deleted_tasks.json", deleted)
                            val msg = "活跃${active.size} 归档${archived.size} 回收站${deleted.size}"
                            android.widget.Toast.makeText(settingsContext, "迁移完成：$msg", android.widget.Toast.LENGTH_LONG).show()
                            AppLogger.system("数据手动迁移完成", msg)
                        } else {
                            android.widget.Toast.makeText(settingsContext, "已是最新格式，无需迁移", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(settingsContext, "迁移失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        AppLogger.system("数据迁移失败", e.message ?: "")
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("🔄 执行数据迁移", fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showLogScreen = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("📋 查看操作日志", fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    showCalendarTest = true
                    AppLogger.testOperation("日历写入", "打开测试弹窗")
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("测试日历写入") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    com.example.aitodoapp.ForegroundService.refresh(settingsContext, justCompleted = false)
                    val t = com.example.aitodoapp.data.TaskRepository.load<com.example.aitodoapp.Task>("tasks.json")
                    val pending = t.count { !it.isCompleted }
                    val hour = java.time.LocalTime.now().hour
                    val quiet = hour >= 4 && hour < 6
                    val status = if (quiet) "静默时段" else "正常显示"
                    android.widget.Toast.makeText(settingsContext,
                        "已触发刷新 | 总${t.size} 待办${pending} | $status",
                        android.widget.Toast.LENGTH_LONG).show()
                    AppLogger.testOperation("刷新通知", "总${t.size} 待办${pending} $status")
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("🔔 刷新通知（测试）", fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
                if (onTestReport != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onTestReport(true); AppLogger.testOperation("早间播报", "手动触发") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("🌅 测试早间播报", fontSize = 13.sp) }
                        Button(onClick = { onTestReport(false); AppLogger.testOperation("晚间播报", "手动触发") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("🌙 测试晚间播报", fontSize = 13.sp) }
                    }
                }
                if (onScheduleReport != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onScheduleReport(true); AppLogger.testOperation("调度早间播报", "1分钟后") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("⏰ 1分钟后·早间", fontSize = 12.sp) }
                        Button(onClick = { onScheduleReport(false); AppLogger.testOperation("调度晚间播报", "1分钟后") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("⏰ 1分钟后·晚间", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val count = com.example.aitodoapp.data.ReportWorker.cleanupLegacy(settingsContext)
                    val msg = if (count > 0) "已清理 ${count} 个旧版定时任务" else "未发现旧版残留任务"
                    android.widget.Toast.makeText(settingsContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                    AppLogger.testOperation("清理旧版播报残留", if (count > 0) "清理${count}个" else "无残留")
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("🧹 清理旧版播报残留任务", fontSize = 13.sp) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    ReportRepository.clearAll()
                    AppLogger.reportCleared()
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("🗑️ 清除播报记录", fontSize = 13.sp) }
                if (onRestoreTask != null) {
                    Spacer(Modifier.height(8.dp))
                    var showTrash by remember { mutableStateOf(false) }
                    Button(onClick = { showTrash = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("📦 最近删除", fontSize = 13.sp) }
                    if (showTrash) {
                        val trashTasks = com.example.aitodoapp.data.TaskRepository.load<com.example.aitodoapp.Task>("deleted_tasks.json")
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
                                                TextButton(onClick = { onRestoreTask?.invoke(task.id) }) { Text("恢复", fontSize = 11.sp) }
                                                TextButton(onClick = { onPermanentDelete?.invoke(task.id, task.title) }) { Text("删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        }
                                        if (trashTasks.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Button(onClick = { trashTasks.forEach { onPermanentDelete?.invoke(it.id, it.title) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("一键全部删除", fontSize = 13.sp) }
                                        }
                                    }
                                }
                            },
                            confirmButton = { TextButton(onClick = { showTrash = false }) { Text("关闭") } }
                        )
                    }
                }
            }
        }

        // ──── 本地文件 ────
        var showFileDialog by remember { mutableStateOf(false) }
        var fileDialogContent by remember { mutableStateOf("") }
        var fileDialogTitle by remember { mutableStateOf("") }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(" 本地文件 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(12.dp))
        val localFiles = listOf(
            "tasks.json" to "📋 活跃任务",
            "archived_tasks.json" to "📦 归档任务",
            "deleted_tasks.json" to "🗑️ 回收站",
            "tags.json" to "🏷️ 标签",
            "settings.json" to "⚙️ 设置",
            "token.json" to "🔢 Token 用量",
            "reports.json" to "📊 播报记录",
            "action_logs.json" to "📜 操作日志"
        )
        localFiles.forEach { (file, label) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable {
                    val f = java.io.File(settingsContext.filesDir, file)
                    fileDialogTitle = "$label ($file)"
                    fileDialogContent = if (f.exists()) {
                        try {
                            val text = f.readText()
                            if (text.isBlank()) "（空文件）" else text
                        } catch (e: Exception) { "读取失败：${e.message}" }
                    } else "（文件不存在）"
                    showFileDialog = true
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val f = java.io.File(settingsContext.filesDir, file)
                    Text(
                        if (f.exists()) "✅ ${f.length()}B" else "❌ 不存在",
                        fontSize = 12.sp,
                        color = if (f.exists()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                    )
                }
                // 分享按钮 → FileProvider 直接分享 filesDir 中的原始文件
                Text(" ↗", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        val f = java.io.File(settingsContext.filesDir, file)
                        if (f.exists()) {
                            try {
                                val uri = androidx.core.content.FileProvider.getUriForFile(settingsContext, "${settingsContext.packageName}.fileprovider", f)
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                settingsContext.startActivity(android.content.Intent.createChooser(intent, "分享 $file"))
                            } catch (_: Exception) {}
                        } else {
                            android.widget.Toast.makeText(settingsContext, "文件不存在", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }.padding(start = 8.dp, end = 4.dp))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        }
        if (showFileDialog) {
            Dialog(onDismissRequest = { showFileDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                androidx.compose.material3.Surface(Modifier.fillMaxWidth(0.95f), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(fileDialogTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text(settingsContext.filesDir.absolutePath, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val fileScroll = rememberScrollState()
                        Column(Modifier.weight(1f).verticalScroll(fileScroll)) {
                            Text(fileDialogContent, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                try {
                                    val fileName = fileDialogTitle.substringAfter("(").substringBefore(")")
                                    val src = java.io.File(settingsContext.filesDir, fileName)
                                    if (src.exists()) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(settingsContext, "${settingsContext.packageName}.fileprovider", src)
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        settingsContext.startActivity(android.content.Intent.createChooser(intent, "分享"))
                                    }
                                } catch (_: Exception) {}
                            }) { Text("分享") }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = {
                                val clipboard = settingsContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(fileDialogTitle, fileDialogContent))
                                android.widget.Toast.makeText(settingsContext, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                            }) { Text("复制") }
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { showFileDialog = false }) { Text("关闭") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            val old = s.value
            val newSettings = SettingsRepository.Settings(apiUrl.trim(), apiKey.trim(), model.trim(), mergeOverdue, longChat, showToken, autoSync, defaultRemind, s.value.defaultDurationMinutes, sortOrder, reportEnabled, s.value.morningReportTime, s.value.eveningReportTime, keepAlive)
            SettingsRepository.save(newSettings)
            // 日志：记录哪些设置项发生了变化
            val changedFields = mutableListOf<String>()
            if (apiUrl.trim() != old.apiUrl) changedFields.add("API 地址")
            if (apiKey.trim() != old.apiKey) changedFields.add("API Key")
            if (model.trim() != old.model) changedFields.add("模型")
            if (mergeOverdue != old.showOverdueInline) changedFields.add("过期分区")
            if (longChat != old.longPressChat) changedFields.add("长按对话")
            if (showToken != old.showTokenUsage) changedFields.add("Token 显示")
            if (autoSync != old.autoSyncCalendar) changedFields.add("日历同步")
            if (defaultRemind != old.defaultReminderMinutes) changedFields.add("提醒时间")
            if (s.value.defaultDurationMinutes != old.defaultDurationMinutes) changedFields.add("默认时长")
            if (sortOrder != old.taskSortOrder) changedFields.add("任务排序")
            if (reportEnabled != old.reportEnabled) changedFields.add("播报开关")
            if (s.value.morningReportTime != old.morningReportTime) changedFields.add("早间播报时间")
            if (s.value.eveningReportTime != old.eveningReportTime) changedFields.add("晚间播报时间")
            if (keepAlive != old.keepAliveEnabled) changedFields.add("后台保活")
            if (changedFields.isNotEmpty()) {
                AppLogger.settingsChanged(changedFields)
            }
            // ⚠️ save 必须在 scheduleNext 之前：scheduleNext 内部会 load 文件取时间，
            // 调换顺序会导致读到的还是旧时间，播报时间改了但不生效。
            if (reportEnabled) {
                com.example.aitodoapp.data.ReportWorker.cancel(settingsContext, true)
                com.example.aitodoapp.data.ReportWorker.cancel(settingsContext, false)
                com.example.aitodoapp.data.ReportWorker.scheduleNext(settingsContext, true)
                com.example.aitodoapp.data.ReportWorker.scheduleNext(settingsContext, false)
                AppLogger.reportScheduled(true); AppLogger.reportScheduled(false)
            } else {
                com.example.aitodoapp.data.ReportWorker.cancel(settingsContext, true)
                com.example.aitodoapp.data.ReportWorker.cancel(settingsContext, false)
                AppLogger.reportCancelled(true); AppLogger.reportCancelled(false)
            }
            saved = true
        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("保存") }
        if (showCalendarTest) CalendarTestDialog(onDismiss = { showCalendarTest = false })
        if (saved) { Spacer(Modifier.height(8.dp)); Text("✓ 已保存", color = MaterialTheme.colorScheme.primary) }
    }
    // 日志页用全屏 Dialog，脱离 SettingsScreen 的可滚动 Column，避免 fillMaxSize 在 verticalScroll 内崩溃
    if (showLogScreen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showLogScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                com.example.aitodoapp.ui.screens.LogScreen(onDismiss = { showLogScreen = false })
            }
        }
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
