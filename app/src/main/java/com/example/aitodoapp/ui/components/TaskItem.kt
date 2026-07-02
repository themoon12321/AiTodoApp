package com.example.aitodoapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.Priority
import com.example.aitodoapp.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TaskItem(task: Task, isArchive: Boolean, onComplete: () -> Unit, onDelete: () -> Unit) {
    val today = LocalDate.now()
    val isOverdue = task.deadline != null && task.deadline < today && !task.isCompleted
    val isDueToday = task.deadline == today && !task.isCompleted
    val isDone = task.isCompleted || task.isArchived
    val planOverdue = task.plannedDates.isNotEmpty() && task.plannedDates.all { it < today } && !task.isCompleted
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onDelete).padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)).background(if (isDone) Color(0xFFBDBDBD) else task.priority.color))
            Spacer(Modifier.width(12.dp))
            if (!isArchive) {
                Checkbox(task.isCompleted, { onComplete() }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50), uncheckedColor = task.priority.color.copy(alpha = 0.5f)))
                Spacer(Modifier.width(4.dp))
            } else {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFF4CAF50).copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(task.title,
                    fontWeight = if (!isDone && (isDueToday || isOverdue)) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!isDone) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${task.priority.emoji} ${task.priority.label}", style = MaterialTheme.typography.labelSmall, color = task.priority.color)
                        }
                        if (task.deadline != null) {
                            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            val dt = when {
                                isOverdue -> "过期${today.toEpochDay() - task.deadline.toEpochDay()}天"
                                isDueToday -> "今天截止"
                                else -> task.deadline.format(DateTimeFormatter.ofPattern("M月d日"))
                            }
                            val dc = when {
                                isOverdue -> MaterialTheme.colorScheme.error
                                isDueToday -> Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(dt, style = MaterialTheme.typography.labelSmall, color = dc)
                        }
                        task.tags.forEach { Text("· #$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) }
                        if (task.plannedDates.isNotEmpty()) {
                            val pd = task.plannedDates.sorted()
                            Text("· 📅 ${pd.first().format(DateTimeFormatter.ofPattern("M/d"))}" + if (pd.size > 1) "等${pd.size}天" else "",
                                style = MaterialTheme.typography.labelSmall, color = if (planOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        if (planOverdue) Text("· 计划逾期", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (isArchive) { Spacer(Modifier.height(4.dp)); TextButton(onComplete) { Text("取消归档", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) } }
            }
        }
    }
}
