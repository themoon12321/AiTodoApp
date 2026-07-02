package com.example.aitodoapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.example.aitodoapp.ui.components.TagChip
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.Priority
import com.example.aitodoapp.Tag
import com.example.aitodoapp.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditTaskDialog(
    task: Task,
    allTags: List<Tag>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Priority, LocalDate?, List<String>, List<LocalDate>, Boolean, String?, List<String>, Int?) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(task.title) }; var content by remember { mutableStateOf(task.content) }
    var selectedPriority by remember { mutableStateOf(task.priority) }; var hasDeadline by remember { mutableStateOf(task.deadline != null) }
    var deadlineDate by remember { mutableStateOf(task.deadline ?: LocalDate.now()) }; var showPriorityMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var deadlineTimeStr by remember { mutableStateOf(task.deadlineTime ?: "") }
    var estimatedMinutesStr by remember { mutableStateOf(task.estimatedMinutes?.toString() ?: "") }
    var plannedTimeStr by remember { mutableStateOf(task.plannedTimes.firstOrNull() ?: "") }
    var pm by remember { mutableStateOf(if (task.plannedTimes.isNotEmpty()) task.plannedTimes.first().substringAfter(":").toIntOrNull() ?: 0 else 0) }
    var hasPlan by remember { mutableStateOf(task.plannedDates.isNotEmpty()) }
    var plannedDates by remember { mutableStateOf(task.plannedDates) }; var showPlanPicker by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(task.tags) }; var tagInput by remember { mutableStateOf("") }; var showTagSuggest by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val suggestions = allTags.map { it.name }.filter { it !in selectedTags }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { onDismiss() })
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("编辑任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("任务标题") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = content, onValueChange = { content = it }, placeholder = { Text("详细描述、注意事项等") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("优先级", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                Box { OutlinedButton({ showPriorityMenu = true }, shape = RoundedCornerShape(10.dp)) { Text("${selectedPriority.emoji} ${selectedPriority.label}", fontSize = 14.sp) }
                    DropdownMenu(showPriorityMenu, { showPriorityMenu = false }) { Priority.entries.forEach { DropdownMenuItem(text = { Text("${it.emoji} ${it.label}") }, onClick = { selectedPriority = it; showPriorityMenu = false }) } } } }
            Spacer(Modifier.height(12.dp))
            Text("标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(4.dp))
            if (selectedTags.isNotEmpty()) { FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(6.dp)) { selectedTags.forEach { TagChip(it, false, onDelete = { selectedTags = selectedTags.filter { t -> t != it } }) } }; Spacer(Modifier.height(6.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = tagInput, onValueChange = { tagInput = it; showTagSuggest = true }, placeholder = { Text(if (suggestions.isEmpty()) "输入新标签" else "输入或选择标签") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.width(8.dp)); Button({ val n = tagInput.trim(); if (n.isNotBlank() && n !in selectedTags) { selectedTags = selectedTags + n; tagInput = "" } }, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(52.dp)) { Text("+") }
            }
            if (showTagSuggest && tagInput.isNotBlank() && suggestions.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                suggestions.filter { it.contains(tagInput, true) }.take(5).forEach { Text("+ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { selectedTags = selectedTags + it; tagInput = "" }) }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("已有标签", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                    suggestions.take(12).forEach { s ->
                        Text("#$s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { selectedTags = selectedTags + s; tagInput = "" })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasDeadline, { hasDeadline = it }); Spacer(Modifier.width(4.dp)); Text("设置截止日期") }
            if (hasDeadline) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { showDatePicker = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("📅 ${deadlineDate.format(DateTimeFormatter.ofPattern("M月d日  EEEE"))}", fontSize = 15.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("截止时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = deadlineTimeStr, onValueChange = { v ->
                        val clean = v.replace(":", "")
                        val formatted = if (clean.length == 4) "${clean.take(2)}:${clean.drop(2)}" else v
                        if (formatted.matches(Regex("^\\d{2}:\\d{2}$"))) {
                            val h = formatted.take(2).toIntOrNull() ?: return@OutlinedTextField; val m = formatted.drop(3).toIntOrNull() ?: return@OutlinedTextField
                            if (h in 0..23 && m in 0..59) deadlineTimeStr = formatted
                        } else if (v.length <= 5) deadlineTimeStr = v
                    }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(100.dp), shape = RoundedCornerShape(8.dp), placeholder = { Text("HH:mm", fontSize = 14.sp) })
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("任务时长", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = estimatedMinutesStr, onValueChange = { v ->
                        if (v.all { it.isDigit() } && v.length <= 4) estimatedMinutesStr = v
                        else if (v.isEmpty()) estimatedMinutesStr = ""
                    }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(80.dp), shape = RoundedCornerShape(8.dp), placeholder = { Text("60", fontSize = 14.sp) })
                    Text(" 分钟", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            // 计划日期
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasPlan, { hasPlan = it; if (!it) plannedDates = emptyList() }); Spacer(Modifier.width(4.dp)); Text("设计划时间") }
            if (hasPlan) {
                Spacer(Modifier.height(6.dp))
                if (plannedDates.isNotEmpty()) FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(6.dp), Arrangement.spacedBy(4.dp)) {
                    plannedDates.sorted().forEach { d -> Row(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.format(DateTimeFormatter.ofPattern("M/d")), fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(4.dp)); Text("✕", fontSize = 10.sp, modifier = Modifier.clickable { plannedDates = plannedDates.filter { it != d } })
                    } }
                }
                OutlinedButton(onClick = { showPlanPicker = true }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) { Text("+ 选择日期", fontSize = 13.sp) }
                if (hasPlan && plannedDates.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("计划时间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = plannedTimeStr, onValueChange = { v ->
                            val clean = v.replace(":", "")
                            val formatted = if (clean.length == 4) "${clean.take(2)}:${clean.drop(2)}" else v
                            if (formatted.matches(Regex("^\\d{2}:\\d{2}$"))) {
                                val h = formatted.take(2).toIntOrNull() ?: return@OutlinedTextField; val m = formatted.drop(3).toIntOrNull() ?: return@OutlinedTextField
                                if (h in 0..23 && m in 0..59) plannedTimeStr = formatted
                            } else if (v.length <= 5) plannedTimeStr = v
                        }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(100.dp), shape = RoundedCornerShape(8.dp), placeholder = { Text("HH:mm", fontSize = 14.sp) })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("取消") }
                Button(onClick = { if (title.isNotBlank()) onSave(task.id, title.trim(), content.trim(), selectedPriority, if (hasDeadline) deadlineDate else null, selectedTags, if (hasPlan) plannedDates else emptyList(), selectedPriority != task.priority, if (hasDeadline && deadlineTimeStr.matches(Regex("^\\d{2}:\\d{2}$"))) deadlineTimeStr else null, if (hasPlan && plannedDates.isNotEmpty() && plannedTimeStr.matches(Regex("^\\d{2}:\\d{2}$"))) listOf(plannedTimeStr) else emptyList(), estimatedMinutesStr.toIntOrNull()) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = title.isNotBlank()) { Text("保存") }
            }
            Spacer(Modifier.height(12.dp))
            if (deleteConfirm) { Text("确定要删除？", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedButton(onClick = { deleteConfirm = false }, shape = RoundedCornerShape(8.dp)) { Text("取消") }; Button(onClick = onDelete, shape = RoundedCornerShape(8.dp)) { Text("确认删除", color = MaterialTheme.colorScheme.error) } }
            } else { TextButton(onClick = { deleteConfirm = true }) { Text("删除任务", color = MaterialTheme.colorScheme.error) } }
            Spacer(Modifier.height(4.dp))
        }
        // 日历选择器
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = deadlineDate.toEpochDay() * 86400000L)
            DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { deadlineDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
                    showDatePicker = false
                }) { Text("确定") }
            }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
            ) { DatePicker(state = datePickerState) }
        }
        // 计划日期选择器
        if (showPlanPicker) {
            val planPickerState = rememberDatePickerState()
            DatePickerDialog(onDismissRequest = { showPlanPicker = false }, confirmButton = {
                TextButton(onClick = {
                    planPickerState.selectedDateMillis?.let { val d = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate(); if (d !in plannedDates) plannedDates = plannedDates + d }
                    showPlanPicker = false
                }) { Text("添加") }
            }, dismissButton = { TextButton(onClick = { showPlanPicker = false }) { Text("取消") } }
            ) { DatePicker(state = planPickerState) }
        }
    }
}
}
}
