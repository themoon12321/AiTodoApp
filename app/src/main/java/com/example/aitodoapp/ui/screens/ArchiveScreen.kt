package com.example.aitodoapp.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitodoapp.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ArchiveScreen(
    tasks: List<Task>,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    // 按 completedAt 日期分组（取不到 completedAt 的用 createdAt）
    val grouped = remember(tasks) {
        tasks.groupBy { task ->
            task.completedAt ?: task.createdAt
        }.entries.sortedByDescending { it.key }.map { (date, taskList) ->
            date to taskList.sortedBy { it.title }
        }
    }

    val totalArchived = tasks.size

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("📦 归档 ($totalArchived)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("按完成日期分组，点击展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        grouped.forEach { (date, taskList) ->
            // 日期分组头
            item(key = "header_$date") {
                DateGroupHeader(date, taskList.size)
            }
            // 该日期下的任务
            taskList.forEach { task ->
                item(key = task.id) {
                    ArchivedTaskItem(task, onUnarchive, onDelete)
                }
            }
            // 分组间分隔
            item { Spacer(Modifier.height(4.dp)) }
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

@Composable
private fun DateGroupHeader(date: LocalDate, count: Int) {
    var expanded by remember { mutableStateOf(true) }
    val today = LocalDate.now()
    val diff = today.toEpochDay() - date.toEpochDay()
    val label = when {
        diff == 0L -> "今天"
        diff == 1L -> "昨天"
        diff <= 7L -> "${diff}天前"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日  EEEE"))
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (expanded) "▼" else "▶",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "📅 $label",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        Text(
            "$count 项",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun ArchivedTaskItem(
    task: Task,
    onUnarchive: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 勾选标记
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))

        // 任务信息
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task.tags.isNotEmpty()) {
                Text(
                    task.tags.joinToString(" · ") { "#$it" },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
        }

        // 操作按钮
        TextButton(
            onClick = { onUnarchive(task.id) },
            modifier = Modifier.height(32.dp)
        ) {
            Text("恢复", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(4.dp))
        if (showDeleteConfirm) {
            TextButton(
                onClick = { onDelete(task.id); showDeleteConfirm = false },
                modifier = Modifier.height(32.dp)
            ) {
                Text("确认删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = { showDeleteConfirm = false },
                modifier = Modifier.height(32.dp)
            ) {
                Text("取消", fontSize = 11.sp)
            }
        } else {
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.height(32.dp)
            ) {
                Text("删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
    HorizontalDivider(
        Modifier.padding(start = 40.dp, end = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    )
}
