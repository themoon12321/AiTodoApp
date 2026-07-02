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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.aitodoapp.data.SettingsRepository
import com.example.aitodoapp.data.TokenRepository
import com.example.aitodoapp.ui.components.CalendarTestDialog

// ============ 设置页 ============

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
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
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.height(24.dp))
        Button(onClick = { SettingsRepository.save(SettingsRepository.Settings(apiUrl.trim(), apiKey.trim(), model.trim(), mergeOverdue, longChat, showToken, autoSync, defaultRemind)); saved = true },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("保存") }
        if (showCalendarTest) CalendarTestDialog(onDismiss = { showCalendarTest = false })
        if (saved) { Spacer(Modifier.height(8.dp)); Text("✓ 已保存", color = MaterialTheme.colorScheme.primary) }
    }
}
