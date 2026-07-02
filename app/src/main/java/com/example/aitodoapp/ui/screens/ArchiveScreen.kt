package com.example.aitodoapp.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.Task
import java.time.format.DateTimeFormatter

@Composable
fun ArchiveScreen(
    tasks: List<Task>,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    // 按 completedAt 日期分组
    val grouped = remember(tasks) {
        tasks.groupBy { it.completedAt ?: it.createdAt }
            .entries.sortedByDescending { it.key }
            .map { it.key to it.value.sortedBy { it.title } }
    }
    val totalArchived = tasks.size
    // 每个日期组的展开状态
    val expandedMap = remember { mutableStateOf<Set<String>>(emptySet()) }
    // 待确认删除的任务 ID
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("📦 归档 ($totalArchived)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
        }

        grouped.forEach { (date, taskList) ->
            val dateKey = date.toString()

            // 日期头 — 点击折叠/展开
            item(key = "h_$dateKey") {
                val isExpanded = dateKey in (expandedMap.value)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedMap.value = if (isExpanded) expandedMap.value - dateKey
                            else expandedMap.value + dateKey
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isExpanded) "▼" else "▶", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${taskList.size}项", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }

            // 该日期下的任务（仅展开时显示）
            if (dateKey in (expandedMap.value)) {
                items(taskList, key = { it.id }) { task ->
                    val isConfirming = confirmDeleteId == task.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onUnarchive(task.id) }, modifier = Modifier.height(28.dp)) {
                            Text("↩", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(2.dp))
                        if (isConfirming) {
                            TextButton(onClick = { onDelete(task.id); confirmDeleteId = null }, modifier = Modifier.height(28.dp)) {
                                Text("确认删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { confirmDeleteId = null }, modifier = Modifier.height(28.dp)) {
                                Text("取消", fontSize = 11.sp)
                            }
                        } else {
                            TextButton(onClick = { confirmDeleteId = task.id }, modifier = Modifier.height(28.dp)) {
                                Text("✕", fontSize = 13.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Text("还没有归档的任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
