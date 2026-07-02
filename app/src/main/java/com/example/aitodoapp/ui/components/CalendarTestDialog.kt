package com.example.aitodoapp.ui.components

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

// ============ 日历测试弹窗 ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTestDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now()) }
    var hour by remember { mutableStateOf(10) }
    var minute by remember { mutableStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf(-1) }
    var deleteTitle by remember { mutableStateOf("") }
    var deleteResult by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) result = "✅ 权限已授予，请再次点击添加"
        else result = "❌ 权限被拒绝"
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { onDismiss() }.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("测试日历写入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("日程标题") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDatePicker = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Text("📅 " + date.format(java.time.format.DateTimeFormatter.ofPattern("M月d日")), fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { hour = (hour + 1) % 24 }, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Text("🕐 ${hour.toString().padStart(2,'0')}:${minute.toString().padStart(2,'0')}", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("分钟：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { minute = (minute + 10) % 60 }, shape = RoundedCornerShape(8.dp)) { Text("+10", fontSize = 12.sp) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = { minute = (minute + 30) % 60 }, shape = RoundedCornerShape(8.dp)) { Text("+30", fontSize = 12.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("提醒：", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val reminders = listOf(-1 to "无", 0 to "准时", 10 to "10分", 30 to "30分", 60 to "1小时")
                reminders.forEach { (min, label) ->
                    OutlinedButton(onClick = { reminderMinutes = min }, shape = RoundedCornerShape(8.dp),
                        colors = if (reminderMinutes == min) androidx.compose.material3.ButtonDefaults.filledTonalButtonColors() else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()) {
                        Text(label, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (title.isBlank()) { result = "请输入标题"; return@Button }
                val check = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
                if (check != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    launcher.launch(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR))
                    return@Button
                }
                try {
                    val projection = arrayOf("_id", "account_name", "calendar_displayName")
                    var cursor = context.contentResolver.query(android.provider.CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
                    var calendarId = -1L
                    cursor?.use { while (it.moveToNext()) { calendarId = it.getLong(0); if (calendarId > 0) break } }
                    if (calendarId <= 0) {
                        // 直接尝试用常用日历 ID 写入用户事件
                        val startMillis = date.atTime(java.time.LocalTime.of(hour, minute)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val endMillis = startMillis + 3600000
                        for (id in 1L..5L) {
                            val v = android.content.ContentValues().apply {
                                put(android.provider.CalendarContract.Events.DTSTART, startMillis)
                                put(android.provider.CalendarContract.Events.DTEND, endMillis)
                                put(android.provider.CalendarContract.Events.TITLE, title.trim())
                                put(android.provider.CalendarContract.Events.CALENDAR_ID, id)
                                put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                            }
                            try {
                                val uri = context.contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, v)
                                if (uri != null) {
                                    val eventId = uri.lastPathSegment?.toLong()
                                    if (eventId != null && reminderMinutes >= 0) {
                                        try {
                                            val rem = android.content.ContentValues().apply {
                                                put(android.provider.CalendarContract.Reminders.EVENT_ID, eventId)
                                                put(android.provider.CalendarContract.Reminders.MINUTES, reminderMinutes)
                                                put(android.provider.CalendarContract.Reminders.METHOD, android.provider.CalendarContract.Reminders.METHOD_ALERT)
                                            }
                                            context.contentResolver.insert(android.provider.CalendarContract.Reminders.CONTENT_URI, rem)
                                        } catch (_: Exception) {}
                                    }
                                    result = "✅ 添加成功！已添加到系统日历"; return@Button
                                }
                            } catch (_: Exception) { continue }
                        }
                        result = "❌ 未找到可用日历（请先打开系统日历 App 初始化）"; return@Button
                    }
                    val startMillis = date.atTime(java.time.LocalTime.of(hour, minute)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val endMillis = startMillis + 3600000
                    val values = android.content.ContentValues().apply {
                        put(android.provider.CalendarContract.Events.DTSTART, startMillis)
                        put(android.provider.CalendarContract.Events.DTEND, endMillis)
                        put(android.provider.CalendarContract.Events.TITLE, title.trim())
                        put(android.provider.CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    }
                    val uri = context.contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, values)
                    if (uri != null) {
                        val eventId = uri.lastPathSegment?.toLong()
                        if (eventId != null && reminderMinutes >= 0) {
                            try {
                                val rem = android.content.ContentValues().apply {
                                    put(android.provider.CalendarContract.Reminders.EVENT_ID, eventId)
                                    put(android.provider.CalendarContract.Reminders.MINUTES, reminderMinutes)
                                    put(android.provider.CalendarContract.Reminders.METHOD, android.provider.CalendarContract.Reminders.METHOD_ALERT)
                                }
                                context.contentResolver.insert(android.provider.CalendarContract.Reminders.CONTENT_URI, rem)
                            } catch (_: Exception) {}
                        }
                        result = "✅ 添加成功！已添加到系统日历"
                    } else { result = "❌ 添加失败" }
                } catch (e: Exception) { result = "❌ 出错了：${e.message}" }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = title.isNotBlank()) { Text("添加到日历") }
            Spacer(Modifier.height(8.dp))
            if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.labelMedium, color = if (result.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text("删除事件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = deleteTitle, onValueChange = { deleteTitle = it }, placeholder = { Text("输入标题查找删除") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (deleteTitle.isBlank()) { deleteResult = "请输入标题"; return@Button }
                deleteResult = ""
                try {
                    val cursor = context.contentResolver.query(android.provider.CalendarContract.Events.CONTENT_URI, arrayOf("_id", "title"), "title = ?", arrayOf(deleteTitle.trim()), null)
                    var count = 0
                    cursor?.use { while (it.moveToNext()) {
                        val eventId = it.getLong(0)
                        context.contentResolver.delete(android.provider.CalendarContract.Reminders.CONTENT_URI, "event_id = ?", arrayOf(eventId.toString()))
                        context.contentResolver.delete(android.provider.CalendarContract.Events.CONTENT_URI, "_id = ?", arrayOf(eventId.toString()))
                        count++
                    } }
                    deleteResult = if (count > 0) "✅ 已删除 $count 个事件" else "❌ 未找到匹配的事件"
                } catch (e: Exception) { deleteResult = "❌ 出错了：${e.message}" }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), enabled = deleteTitle.isNotBlank()) { Text("删除匹配事件") }
            if (deleteResult.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(deleteResult, style = MaterialTheme.typography.labelMedium, color = if (deleteResult.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
        if (showDatePicker) {
            val state = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86400000L)
            DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }; showDatePicker = false }) { Text("确定") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }) { DatePicker(state = state) }
        }
    }
}
