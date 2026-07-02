package com.example.aitodoapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.example.aitodoapp.ui.components.TagChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.Priority
import com.example.aitodoapp.Tag
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ============ 添加任务弹窗 ============

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(allTags: List<Tag>, onDismiss: () -> Unit, onConfirm: (String, Priority, LocalDate?, List<String>, String, List<LocalDate>, String?) -> Unit) {
    var title by remember { mutableStateOf("") }; var content by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(Priority.P3) }; var hasDeadline by remember { mutableStateOf(false) }
    var deadlineDate by remember { mutableStateOf(LocalDate.now()) }; var showPriorityMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var hasPlan by remember { mutableStateOf(false) }; var plannedDates by remember { mutableStateOf(listOf<LocalDate>()) }; var showPlanPicker by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(listOf<String>()) }; var tagInput by remember { mutableStateOf("") }; var showTagSuggest by remember { mutableStateOf(false) }
    val suggestions = allTags.map { it.name }.filter { it !in selectedTags }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable { onDismiss() })
        Box(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("新建任务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                suggestions.filter { it.contains(tagInput, ignoreCase = true) }.take(5).forEach { s -> Text("+ $s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp).clickable { selectedTags = selectedTags + s; tagInput = "" }) }
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
            }
            Spacer(Modifier.height(12.dp))
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
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("取消") }
                Button(onClick = { if (title.isNotBlank()) onConfirm(title.trim(), selectedPriority, if (hasDeadline) deadlineDate else null, selectedTags, content.trim(), if (hasPlan) plannedDates else emptyList(), null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), enabled = title.isNotBlank()) { Text("添加") }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = deadlineDate.toEpochDay() * 86400000L)
            DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { deadlineDate = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                    showDatePicker = false
                }) { Text("确定") }
            }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
            ) { DatePicker(state = datePickerState) }
        }
        if (showPlanPicker) {
            val planPickerState = rememberDatePickerState()
            DatePickerDialog(onDismissRequest = { showPlanPicker = false }, confirmButton = {
                TextButton(onClick = {
                    planPickerState.selectedDateMillis?.let { val d = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate(); if (d !in plannedDates) plannedDates = plannedDates + d }
                    showPlanPicker = false
                }) { Text("添加") }
            }, dismissButton = { TextButton(onClick = { showPlanPicker = false }) { Text("取消") } }
            ) { DatePicker(state = planPickerState) }
        }
    }
    }
}
